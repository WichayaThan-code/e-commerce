import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ArrowLeft,
  Check,
  CheckCircle2,
  CircleAlert,
  FileCheck2,
  Landmark,
  LoaderCircle,
  MapPin,
  Send,
  Store,
  UserRound,
  XCircle,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { useLanguage } from "../contexts/LanguageContext";
import MerchantOnboardingLayout from "../components/merchant/MerchantOnboardingLayout";
import OnboardingAside from "../components/merchant/OnboardingAside";
import RegisterStepper from "../components/merchant/RegisterStepper";
import MerchantHeader from "../components/MerchantHeader";
import ConfirmDialog from "../components/form/ConfirmDialog";
import FieldError from "../components/form/FieldError";
import { ApiError, merchantApi, type MerchantApplication, type MerchantOwnerPayload, type MerchantShopPayload } from "../services/api";
import { describeApiError, showApiFailure } from "../services/apiErrors";
import { clearMerchantDraft, readMerchantDraft } from "../services/merchantDraft";
import { focusFirstInvalid, linkedOwnerPayload, reviewGap } from "../services/merchantApplicationValidation";
import i18n from "../i18n";
import "../styles/doc-confirm.css";
import "../styles/verify-send.css";
import "../styles/form-fields.css";
import "../styles/form-invalid.css";
import "../styles/product-management.css";

type ReviewSectionKey = "shop" | "social" | "owner" | "docs" | "address" | "payout";

function ReviewSection({
  icon: Icon,
  title,
  status,
  edit,
  locked = false,
  needsFix = false,
  labels,
  onEdit,
  children,
}: {
  icon: typeof Store;
  title: string;
  status?: string;
  edit?: string;
  locked?: boolean;
  needsFix?: boolean;
  labels: { complete: string; edit: string; locked: string; needsFix: string };
  onEdit?: () => void;
  children: React.ReactNode;
}) {
  return (
    <section className={`review-section${needsFix ? " is-needs-fix" : ""}`}>
      <div className="review-section-icon">
        <Icon />
      </div>
      <div className="review-section-content">
        <div className="review-section-head">
          <h2>{title}</h2>
          <span className={needsFix ? "is-needs-fix" : undefined}>
            {needsFix ? <CircleAlert aria-hidden="true" /> : <CheckCircle2 aria-hidden="true" />}
            {needsFix ? labels.needsFix : (status ?? labels.complete)}
          </span>
        </div>
        {children}
      </div>
      {locked ? (
        <span className="review-locked">{labels.locked}</span>
      ) : edit ? (
        <a
          href={edit}
          onClick={() => onEdit?.()}
        >
          {labels.edit}
        </a>
      ) : null}
    </section>
  );
}

function sectionLabel(section: ReviewSectionKey | null) {
  if (!section) return "";
  const key =
    section === "shop" ? "sectionShop"
      : section === "social" ? "sectionSocial"
        : section === "owner" ? "sectionOwner"
          : section === "docs" ? "sectionDocs"
            : section === "address" ? "sectionAddress"
              : "sectionPayout";
  return i18n.t(`review.${key}`, { ns: "merchant" });
}

function fieldSection(field: string): ReviewSectionKey | null {
  if (field === "shopName" || field === "shopNameEn" || field === "category" || field === "description" || field === "saleType") return "shop";
  if (field === "website" || field === "lineId" || field === "facebookUrl" || field === "instagramUrl" || field === "tiktokUrl") return "social";
  if (field === "address" || field === "province" || field === "district" || field === "subdistrict" || field === "postalCode") return "address";
  if (field === "houseNo" || field === "villageNo" || field === "building" || field === "alley" || field === "road") return "address";
  if (field === "bankAccountNumber" || field === "bankAccountName" || field === "bankName") return "payout";
  if (field === "citizenId" || field === "phone" || field === "email" || field === "firstName" || field === "lastName" || field === "title" || field === "birthDate" || field === "nationality") return "owner";
  return null;
}

function resolveErrorTarget(error: unknown): { href: string; section: ReviewSectionKey | null } {
  if (!(error instanceof ApiError)) return { href: "", section: null };
  const code = error.code;
  const apiSection = typeof error.details.section === "string" ? error.details.section : "";
  const field = error.fieldErrors[0]?.field || "";
  const mapped = fieldSection(field);

  if (code === "SHOP_EXISTS" || mapped === "shop") {
    return { href: "/merchant/register", section: "shop" };
  }
  if (mapped === "social") {
    return { href: "/merchant/register", section: "social" };
  }
  if (mapped === "address") {
    // Owner address fields live on owner-info; shop address on register.
    if (field === "houseNo" || field === "villageNo" || field === "building" || field === "alley" || field === "road") {
      return { href: "/merchant/owner-info", section: "address" };
    }
    return { href: "/merchant/register", section: "address" };
  }
  if (code === "CITIZEN_ID_TAKEN" || code === "OWNER_EXISTS" || mapped === "owner" || apiSection === "owner") {
    return { href: "/merchant/owner-info", section: "owner" };
  }
  if (mapped === "payout" || apiSection === "bank") {
    return { href: "/merchant/owner-info", section: "payout" };
  }
  if (code === "FILE_TOO_LARGE" || code === "INVALID_FILE" || apiSection === "documents") {
    return { href: "/merchant/documents", section: "docs" };
  }
  if (code === "VALUE_TOO_LONG" || code === "VALIDATION_FAILED") {
    if (apiSection === "shop") return { href: "/merchant/register", section: "shop" };
    if (apiSection === "owner" || apiSection === "bank" || apiSection === "address") {
      return { href: "/merchant/owner-info", section: apiSection === "bank" ? "payout" : apiSection === "address" ? "address" : "owner" };
    }
  }
  if (code === "APPLICATION_INCOMPLETE") {
    return { href: "", section: null };
  }
  return { href: "", section: null };
}

function hrefToSection(href: string): ReviewSectionKey | null {
  if (href.includes("/merchant/documents")) return "docs";
  if (href.includes("/merchant/owner-info")) return "owner";
  if (href.includes("/merchant/register")) return "shop";
  return null;
}

export default function VerifySendPage() {
  const navigate = useNavigate();
  const redirectTimer = useRef<number | null>(null);
  const submittingRef = useRef(false);
  const { user } = useAuth();
  const { onboarding: ob } = useLanguage();
  const rv = ob.review;
  const soc = ob.social;
  const [application, setApplication] = useState<MerchantApplication | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const [confirmError, setConfirmError] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [applyResult, setApplyResult] = useState<"success" | "error" | null>(null);
  const [message, setMessage] = useState(rv.loading);
  const [stepHref, setStepHref] = useState("");
  const [errorSection, setErrorSection] = useState<ReviewSectionKey | null>(null);

  const clearSubmitError = () => {
    setMessage("");
    setStepHref("");
    setErrorSection(null);
    setApplyResult(null);
  };

  useEffect(() => () => {
    if (redirectTimer.current) window.clearTimeout(redirectTimer.current);
  }, []);

  useEffect(() => {
    merchantApi
      .getDraft()
      .then((data) => {
        setApplication(data);
        setMessage("");
      })
      .catch((error) => showApiFailure(error, setMessage));
  }, []);

  const [localDraft] = useState(() => readMerchantDraft(user?.id));
  const owner = localDraft.owner;
  const shown = (saved?: string | null, drafted?: string) => saved || drafted || rv.empty;
  const saleType =
    application?.saleType ||
    (localDraft.saleType === "reseller"
      ? "RESELLER"
      : localDraft.saleType === "owner"
        ? "OWNER"
        : undefined);
  const uploadedTypes = useMemo(() => ([
    ...new Set([
      ...Object.keys(localDraft.documentNames || {}),
      ...(application?.documents.map((document) => document.type) || []),
    ]),
  ]), [application?.documents, localDraft.documentNames]);
  const existingOwner = Boolean(application?.existingOwner);
  const ownerReusedLabel = i18n.t("review.ownerReused", { ns: "merchant" });
  const docsGap = application
    ? reviewGap({ ...localDraft, ownerId: localDraft.ownerId || application.ownerId || undefined }, uploadedTypes)
    : null;

  useEffect(() => {
    if (!application || !docsGap) return;
    setMessage(docsGap.message);
    setStepHref(docsGap.href);
    setErrorSection(hrefToSection(docsGap.href));
  }, [application, docsGap?.href, docsGap?.message]);

  const shopName = shown(application?.shopName, localDraft.shopName);
  const shopCategory = shown(application?.category, localDraft.category);
  const shopAddressSummary = [
    application?.shopAddress || localDraft.address,
    application?.shopSubdistrict || localDraft.subdistrict,
    application?.shopDistrict || localDraft.district,
    application?.shopProvince || localDraft.province,
  ].filter(Boolean).join(" ") || rv.empty;
  const ownerName = application?.firstName || owner
    ? `${application?.firstName || owner?.firstName || ""} ${application?.lastName || owner?.lastName || ""}`.trim()
    : rv.empty;
  const ownerEditHref = localDraft.ownerId ? `/merchant/owner-info?ownerId=${localDraft.ownerId}` : "/merchant/owner-info";
  const sectionLabels = { ...rv, needsFix: i18n.t("review.needsFix", { ns: "merchant" }) };
  const showBanner = Boolean(message) && !(confirmOpen && (submitting || applyResult));

  const submit = async () => {
    if (submittingRef.current || submitted) return;
    if (!confirmed) {
      setConfirmError(true);
      focusFirstInvalid({ verifyConfirm: i18n.t("review.confirmRequired", { ns: "merchant" }) });
      return;
    }
    submittingRef.current = true;
    setApplyResult(null);
    setSubmitting(true);
    if (!user) {
      submittingRef.current = false;
      window.location.assign("/login?next=/merchant/review");
      return;
    }
    const draft = readMerchantDraft(user.id);
    const ownerDraft = draft.owner;
    const uploaded = [
      ...Object.keys(draft.documentNames || {}),
      ...(application?.documents.map((document) => document.type) || []),
    ];
    const gap = reviewGap({ ...draft, ownerId: draft.ownerId || application?.ownerId || undefined }, uploaded);
    if (gap) {
      setApplyResult("error");
      setMessage(gap.message);
      setStepHref(gap.href);
      setErrorSection(hrefToSection(gap.href));
      submittingRef.current = false;
      setSubmitting(false);
      return;
    }
    setStepHref("");
    setErrorSection(null);
    const shop: MerchantShopPayload = {
      shopName: draft.shopName || "",
      shopNameEn: draft.shopNameEnglish || "",
      description: draft.description || "",
      category: draft.category || "",
      saleType: draft.saleType === "reseller" ? "RESELLER" : "OWNER",
      website: draft.website || "",
      lineId: draft.lineId || "",
      facebookUrl: draft.facebookUrl || "",
      instagramUrl: draft.instagramUrl || "",
      tiktokUrl: draft.tiktokUrl || "",
      address: draft.address || "",
      province: draft.province || "",
      district: draft.district || "",
      subdistrict: draft.subdistrict || "",
      postalCode: draft.postalCode || "",
    };
    try {
      if (draft.logoBase64 && !application?.documents.some((document) => document.type === "LOGO")) {
        await merchantApi.uploadDocument("LOGO", await logoFile(draft.logoBase64));
      }
      await merchantApi.saveShop(shop);
      if (ownerDraft && !draft.ownerId) {
        const ownerPayload: MerchantOwnerPayload = {
          ownerType: ownerDraft.ownerType === "company" ? "COMPANY" : "PERSON",
          title: ownerDraft.title,
          firstName: ownerDraft.firstName,
          lastName: ownerDraft.lastName,
          citizenId: ownerDraft.citizenId,
          birthDate: ownerDraft.birthDate,
          nationality: ownerDraft.nationality,
          phone: ownerDraft.phone,
          email: ownerDraft.email,
          houseNo: ownerDraft.houseNo,
          villageNo: ownerDraft.villageNo || "",
          building: ownerDraft.building || "",
          alley: ownerDraft.alley || "",
          road: ownerDraft.road || "",
          province: ownerDraft.province,
          district: ownerDraft.district,
          subdistrict: ownerDraft.subdistrict,
          postalCode: ownerDraft.postalCode,
          bankName: ownerDraft.bankName,
          bankAccountNumber: ownerDraft.bankAccountNumber,
          bankAccountName: ownerDraft.bankAccountName,
        };
        await merchantApi.saveOwner(ownerPayload);
      } else if (ownerDraft && draft.ownerId) {
        // Second shop of an existing owner: save the (possibly edited) owner + this shop's payout to this application only.
        await merchantApi.saveLinkedOwner(linkedOwnerPayload(ownerDraft));
      }
      const result = await merchantApi.submit();
      setApplication(result);
      setApplyResult("success");
      clearMerchantDraft(user.id);
      setMessage(rv.success);
      submittingRef.current = false;
      setSubmitting(false);
      if (redirectTimer.current) window.clearTimeout(redirectTimer.current);
      redirectTimer.current = window.setTimeout(() => navigate("/"), 5000);
    } catch (error) {
      const described = describeApiError(error);
      const target = resolveErrorTarget(error);
      let href = target.href;
      let section = target.section;
      if (!href && error instanceof ApiError) {
        const field = error.fieldErrors[0]?.field || "";
        const mapped = fieldSection(field);
        if (mapped === "shop" || mapped === "social" || (mapped === "address" && field === "address")) {
          href = "/merchant/register";
          section = mapped === "social" ? "social" : mapped === "address" ? "address" : "shop";
        } else if (mapped === "owner" || mapped === "payout" || mapped === "address") {
          href = ownerEditHref;
          section = mapped;
        }
      }
      const fieldLines = error instanceof ApiError && error.fieldErrors.length > 0
        ? error.fieldErrors.map((item) => item.message).filter(Boolean)
        : [];
      const banner = fieldLines.length > 1
        ? `${described.message}\n${fieldLines.map((line) => `• ${line}`).join("\n")}`
        : fieldLines.length === 1
          ? fieldLines[0]
          : described.message;
      setApplyResult("error");
      setStepHref(href);
      setErrorSection(section);
      setMessage(banner);
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  const closeConfirm = () => {
    if (submitting) return;
    setConfirmOpen(false);
    if (applyResult !== "error") setApplyResult(null);
  };

  const finishSuccess = () => {
    if (redirectTimer.current) {
      window.clearTimeout(redirectTimer.current);
      redirectTimer.current = null;
    }
    setConfirmOpen(false);
    navigate("/");
  };

  const closeError = () => {
    setConfirmOpen(false);
  };

  const goFix = () => {
    const href = stepHref || ownerEditHref;
    clearSubmitError();
    setConfirmOpen(false);
    window.location.assign(href);
  };

  const submitted = application?.status === "SUBMITTED";
  const hasDoc = (type: string) =>
    application?.documents.some((document) => document.type === type);

  return (
    <main className="doc-page verify-page onboard-page">
      <MerchantHeader />
      <MerchantOnboardingLayout
        aside={(
          <OnboardingAside
            safeSummaryLabel={ob.common.asideSafeSummary}
            safe={{ title: ob.owner.safeTitle, items: [...ob.owner.safeBullets] }}
            tips={{ title: rv.tipsTitle, items: [...rv.tipsBullets] }}
            help={{
              title: rv.helpTitle,
              lead: rv.helpLead,
              contactLabel: ob.common.contact,
            }}
            trustLabel={ob.common.trustSslPdpa}
          />
        )}
      >
          <div className="verify-card">
            <header>
              <h1>{rv.pageTitle}</h1>
              <p>{rv.pageLead}</p>
            </header>
            <RegisterStepper currentStep={4} />
            <ReviewSection
              icon={Store}
              title={rv.sectionShop}
              edit="/merchant/register"
              locked={submitted}
              status={rv.complete}
              needsFix={errorSection === "shop"}
              labels={sectionLabels}
              onEdit={clearSubmitError}
            >
              <dl>
                <dt>{rv.shopNameTh}</dt>
                <dd>{shopName}</dd>
                <dt>{rv.shopNameEn}</dt>
                <dd>{shown(application?.shopNameEn, localDraft.shopNameEnglish)}</dd>
                <dt>{rv.saleType}</dt>
                <dd>
                  {saleType === "RESELLER"
                    ? rv.saleReseller
                    : saleType === "OWNER"
                      ? rv.saleOwner
                      : rv.empty}
                </dd>
                <dt>{rv.category}</dt>
                <dd>{shown(application?.category, localDraft.category)}</dd>
                <dt>{rv.description}</dt>
                <dd>{shown(application?.description, localDraft.description)}</dd>
                <dt>{rv.logo}</dt>
                <dd>
                  {localDraft.logoBase64 ? (
                    <img className="review-logo-image" src={localDraft.logoBase64} alt={rv.logoAlt} />
                  ) : (
                    rv.empty
                  )}
                </dd>
              </dl>
            </ReviewSection>
            <ReviewSection
              icon={Store}
              title={rv.sectionSocial}
              edit="/merchant/register"
              locked={submitted}
              status={rv.complete}
              needsFix={errorSection === "social"}
              labels={sectionLabels}
              onEdit={clearSubmitError}
            >
              <dl>
                <dt>{soc.website}</dt>
                <dd>{shown(application?.website, localDraft.website)}</dd>
                <dt>{soc.line}</dt>
                <dd>{shown(application?.lineId, localDraft.lineId)}</dd>
                <dt>{soc.facebook}</dt>
                <dd>{shown(application?.facebookUrl, localDraft.facebookUrl)}</dd>
                <dt>{soc.instagram}</dt>
                <dd>{shown(application?.instagramUrl, localDraft.instagramUrl)}</dd>
                <dt>{soc.tiktok}</dt>
                <dd>{shown(application?.tiktokUrl, localDraft.tiktokUrl)}</dd>
              </dl>
            </ReviewSection>
            <ReviewSection
              icon={UserRound}
              title={rv.sectionOwner}
              edit={ownerEditHref}
              locked={submitted}
              status={existingOwner ? ownerReusedLabel : undefined}
              needsFix={errorSection === "owner"}
              labels={sectionLabels}
              onEdit={clearSubmitError}
            >
              <dl>
                <dt>{rv.ownerName}</dt>
                <dd>{ownerName || rv.empty}</dd>
                <dt>{rv.ownerPhone}</dt>
                <dd>{shown(application?.phone, owner?.phone)}</dd>
                <dt>{rv.ownerEmail}</dt>
                <dd>{shown(application?.email, owner?.email)}</dd>
                <dt>{rv.ownerIdAddress}</dt>
                <dd>
                  {application?.houseNo || owner
                    ? `${application?.houseNo || owner?.houseNo || ""} หมู่ ${application?.villageNo || owner?.villageNo || "-"} ${application?.subdistrict || owner?.subdistrict || ""} ${application?.district || owner?.district || ""} ${application?.province || owner?.province || ""} ${application?.postalCode || owner?.postalCode || ""}`
                    : rv.empty}
                </dd>
              </dl>
            </ReviewSection>
            <ReviewSection
              icon={FileCheck2}
              title={rv.sectionDocs}
              status={(hasDoc("IDENTITY") && hasDoc("ADDRESS")) ? rv.uploaded : rv.incomplete}
              edit="/merchant/documents"
              locked={submitted}
              needsFix={errorSection === "docs"}
              labels={sectionLabels}
              onEdit={clearSubmitError}
            >
              <dl>
                <dt>{rv.idDoc}</dt>
                <dd>{hasDoc("IDENTITY") ? (<><CheckCircle2 aria-hidden="true" />{rv.uploadedOk}</>) : rv.empty}</dd>
                <dt>{rv.homeDoc}</dt>
                <dd>{hasDoc("ADDRESS") ? (<><CheckCircle2 aria-hidden="true" />{rv.uploadedOk}</>) : rv.empty}</dd>
                <dt>{rv.businessDoc}</dt>
                <dd>{hasDoc("BUSINESS") ? (<><CheckCircle2 aria-hidden="true" />{rv.uploadedOk}</>) : rv.empty}</dd>
              </dl>
            </ReviewSection>
            <ReviewSection
              icon={MapPin}
              title={rv.sectionAddress}
              edit="/merchant/register"
              locked={submitted}
              needsFix={errorSection === "address"}
              labels={sectionLabels}
              onEdit={clearSubmitError}
            >
              <p className="review-address">
                {application?.shopAddress || localDraft.address || rv.empty}
                <br />
                {application?.shopProvince || localDraft.province || ""}{" "}
                {application?.shopPostalCode || localDraft.postalCode || ""}
              </p>
            </ReviewSection>
            <ReviewSection
              icon={Landmark}
              title={rv.sectionPayout}
              edit={ownerEditHref}
              locked={submitted}
              needsFix={errorSection === "payout"}
              labels={sectionLabels}
              onEdit={clearSubmitError}
            >
              <dl>
                <dt>{rv.bank}</dt>
                <dd>{shown(application?.bankName, owner?.bankName)}</dd>
                <dt>{rv.accountNo}</dt>
                <dd>{application?.bankAccountMasked || owner?.bankAccountNumber || rv.empty}</dd>
                <dt>{rv.accountName}</dt>
                <dd>{shown(application?.bankAccountName, owner?.bankAccountName)}</dd>
              </dl>
            </ReviewSection>
            <label
              className={`verify-confirm${confirmed ? " is-checked" : ""}${confirmError ? " is-invalid" : ""}`}
              data-field="verifyConfirm"
            >
              <input
                id="verify-confirm-check"
                name="verifyConfirm"
                type="checkbox"
                checked={confirmed}
                aria-invalid={confirmError || undefined}
                aria-describedby={`verify-confirm-body${confirmError ? " field-error-verify-confirm" : ""}`}
                onChange={(e) => {
                  const next = e.target.checked;
                  setConfirmed(next);
                  if (next) setConfirmError(false);
                }}
              />
              <span aria-hidden="true">{confirmed ? <Check /> : null}</span>
              <div>
                <b>{rv.confirmTitle}</b>
                <p id="verify-confirm-body">{rv.confirmBody}</p>
              </div>
            </label>
            <FieldError
              text={confirmError ? i18n.t("review.confirmRequired", { ns: "merchant" }) : undefined}
              id="field-error-verify-confirm"
            />
            {showBanner ? (
              <p className={stepHref || applyResult === "error" || errorSection ? "form-alert" : "doc-message"} role={stepHref || applyResult === "error" || errorSection ? "alert" : "status"}>
                {(stepHref || applyResult === "error" || errorSection) ? <CircleAlert aria-hidden="true" /> : null}
                <span>
                  {message}
                  {stepHref ? (
                    <>
                      {" "}
                      <a href={stepHref} onClick={clearSubmitError}>
                        {i18n.t("review.goFixSection", {
                          ns: "merchant",
                          section: sectionLabel(errorSection) || i18n.t("validation.goFix", { ns: "merchant" }),
                        })}
                      </a>
                    </>
                  ) : null}
                </span>
              </p>
            ) : null}
            <div className="verify-actions">
              <a href="/merchant/documents">
                <ArrowLeft aria-hidden="true" />
                {ob.common.back}
              </a>
              <button
                className="login-submit"
                type="button"
                onClick={() => {
                  if (!confirmed) {
                    setConfirmError(true);
                    focusFirstInvalid({ verifyConfirm: i18n.t("review.confirmRequired", { ns: "merchant" }) });
                    return;
                  }
                  if (!user) {
                    window.location.assign("/login?next=/merchant/review");
                    return;
                  }
                  const draft = readMerchantDraft(user.id);
                  const uploaded = [
                    ...Object.keys(draft.documentNames || {}),
                    ...(application?.documents.map((document) => document.type) || []),
                  ];
                  const gap = reviewGap({ ...draft, ownerId: draft.ownerId || application?.ownerId || undefined }, uploaded);
                  if (gap) {
                    setMessage(gap.message);
                    setStepHref(gap.href);
                    setErrorSection(hrefToSection(gap.href));
                    setApplyResult("error");
                    return;
                  }
                  clearSubmitError();
                  setConfirmOpen(true);
                }}
                disabled={!confirmed || submitted || submitting || !application || Boolean(docsGap)}
              >
                {submitting ? <LoaderCircle className="login-spinner" size={18} aria-hidden="true" /> : <Send aria-hidden="true" />}
                {submitted ? rv.submitted : submitting ? rv.submitting : rv.submit}
              </button>
            </div>
            {submitted && (
              <div className="verify-success">
                <CheckCircle2 aria-hidden="true" />
                <span>
                  <b>{rv.success}</b>
                  <small>
                    {i18n.t("review.successRef", { ns: "merchant", ref: application?.referenceNo || "—" })}
                  </small>
                  {application?.shopPath && (
                    <a className="shop-entry-link" href={application.shopPath}>
                      {i18n.t("review.visitShop", { ns: "merchant", path: application.shopPath })}
                    </a>
                  )}
                </span>
              </div>
            )}
          </div>
      </MerchantOnboardingLayout>

      {confirmOpen && applyResult === "success" ? (
        <ConfirmDialog
          icon={<CheckCircle2 />}
          tone="success"
          title={i18n.t("review.success", { ns: "merchant" })}
          message={i18n.t("review.successRedirect", { ns: "merchant" })}
          cancelLabel={i18n.t("review.dialogClose", { ns: "merchant" })}
          confirmLabel={i18n.t("review.dialogClose", { ns: "merchant" })}
          hideCancel
          focusPrimary
          onCancel={finishSuccess}
          onConfirm={finishSuccess}
        />
      ) : null}

      {confirmOpen && applyResult === "error" ? (
        <ConfirmDialog
          icon={<XCircle />}
          tone="danger"
          title={i18n.t("review.dialogFail", { ns: "merchant" })}
          message={message || i18n.t("review.errSubmit", { ns: "merchant" })}
          cancelLabel={i18n.t("review.dialogClose", { ns: "merchant" })}
          confirmLabel={
            stepHref
              ? i18n.t("review.goFixSection", { ns: "merchant", section: sectionLabel(errorSection) })
              : i18n.t("review.dialogClose", { ns: "merchant" })
          }
          hideCancel={!stepHref}
          focusPrimary
          onCancel={closeError}
          onConfirm={() => {
            if (stepHref) goFix();
            else closeError();
          }}
        />
      ) : null}

      {confirmOpen && applyResult !== "success" && applyResult !== "error" ? (
        <ConfirmDialog
          icon={submitting ? <LoaderCircle className="pm-spin" /> : <Send />}
          title={submitting ? i18n.t("review.dialogSending", { ns: "merchant" }) : i18n.t("review.dialogTitle", { ns: "merchant" })}
          message={submitting ? i18n.t("review.dialogSendingHint", { ns: "merchant" }) : i18n.t("review.dialogBody", { ns: "merchant" })}
          cancelLabel={i18n.t("review.dialogCancel", { ns: "merchant" })}
          confirmLabel={submitting ? i18n.t("review.dialogSending", { ns: "merchant" }) : i18n.t("review.dialogConfirm", { ns: "merchant" })}
          confirmIcon={submitting ? <LoaderCircle className="pm-spin" aria-hidden="true" /> : undefined}
          focusPrimary
          confirmDisabled={submitting}
          cancelDisabled={submitting}
          escapeDisabled={submitting}
          onCancel={closeConfirm}
          onConfirm={() => void submit()}
        >
          {submitting ? null : (
            <div className="pm-dialog-summary">
              <div>
                <span>{i18n.t("review.summaryShop", { ns: "merchant" })}</span>
                <b>{shopName}</b>
              </div>
              <div>
                <span>{i18n.t("review.summaryCategory", { ns: "merchant" })}</span>
                <b>{shopCategory}</b>
              </div>
              <div>
                <span>{i18n.t("review.summaryAddress", { ns: "merchant" })}</span>
                <b>{shopAddressSummary}</b>
              </div>
              <div>
                <span>{i18n.t("review.summaryOwner", { ns: "merchant" })}</span>
                <b>{ownerName || rv.empty}</b>
              </div>
            </div>
          )}
        </ConfirmDialog>
      ) : null}
    </main>
  );
}

async function logoFile(dataUrl: string) {
  const response = await fetch(dataUrl);
  const blob = await response.blob();
  const header = dataUrl.slice(0, 48);
  const type = header.includes("image/webp") || blob.type === "image/webp" ? "image/webp"
    : header.includes("image/jpeg") || header.includes("image/jpg") || blob.type === "image/jpeg" ? "image/jpeg"
    : "image/png";
  const name = type === "image/jpeg" ? "logo.jpg" : type === "image/webp" ? "logo.webp" : "logo.png";
  return new File([blob], name, { type });
}
