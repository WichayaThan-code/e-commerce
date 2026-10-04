import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ArrowLeft,
  Check,
  CheckCircle2,
  Clock3,
  FileCheck2,
  Headphones,
  Lightbulb,
  Landmark,
  MapPin,
  LoaderCircle,
  Send,
  ShieldCheck,
  Store,
  UserRound,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { useLanguage } from "../contexts/LanguageContext";
import MerchantOnboardingSidebar from "../components/merchant/MerchantOnboardingSidebar";
import MerchantHeader from "../components/MerchantHeader";
import { ApiError, merchantApi, type MerchantApplication, type MerchantOwnerPayload, type MerchantShopPayload } from "../services/api";
import { clearMerchantDraft, readMerchantDraft } from "../services/merchantDraft";
import "../styles/doc-confirm.css";
import "../styles/verify-send.css";
import "../styles/form-fields.css";

function ReviewSection({
  icon: Icon,
  title,
  status,
  edit,
  locked = false,
  labels,
  children,
}: {
  icon: typeof Store;
  title: string;
  status?: string;
  edit?: string;
  locked?: boolean;
  labels: { complete: string; edit: string; locked: string };
  children: React.ReactNode;
}) {
  return (
    <section className="review-section">
      <div className="review-section-icon">
        <Icon />
      </div>
      <div className="review-section-content">
        <div className="review-section-head">
          <h2>{title}</h2>
          <span>
            <CheckCircle2 />
            {status ?? labels.complete}
          </span>
        </div>
        {children}
      </div>
      {locked ? <span className="review-locked">{labels.locked}</span> : edit ? <a href={edit}>{labels.edit}</a> : null}
    </section>
  );
}

export default function VerifySendPage() {
  const navigate = useNavigate();
  const redirectTimer = useRef<number | null>(null);
  const { user } = useAuth();
  const { onboarding: ob } = useLanguage();
  const rv = ob.review;
  const soc = ob.social;
  const [application, setApplication] = useState<MerchantApplication | null>(
    null,
  );
  const [confirmed, setConfirmed] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [applyResult, setApplyResult] = useState<"success" | "error" | null>(null);
  const [message, setMessage] = useState(rv.loading);
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
      .catch((error) =>
        setMessage(error instanceof ApiError ? error.message : rv.loadFail),
      );
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
  const submit = async () => {
    if (submitting || submitted) return;
    if (!confirmed) return;
    setApplyResult(null);
    setSubmitting(true);
    if (!user) {
      window.location.assign("/login?next=/merchant/review");
      return;
    }
    const draft = readMerchantDraft(user.id);
    const ownerDraft = draft.owner;
    if (
      !draft.shopName ||
      !draft.address ||
      !draft.category ||
      !draft.province ||
      !draft.district ||
      !draft.subdistrict ||
      !draft.postalCode ||
      (!ownerDraft && !draft.ownerId && !application?.ownerId)
    ) {
      setApplyResult("error");
      setMessage(rv.errIncomplete);
      setSubmitting(false);
      return;
    }
    const shop: MerchantShopPayload = {
      shopName: draft.shopName,
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
      province: draft.province,
      district: draft.district,
      subdistrict: draft.subdistrict,
      postalCode: draft.postalCode,
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
      }
      const result = await merchantApi.submit();
      setApplication(result);
      setApplyResult("success");
      clearMerchantDraft(user.id);
      setMessage(rv.success);
      setSubmitting(false);
      redirectTimer.current = window.setTimeout(() => navigate("/"), 1600);
    } catch (error) {
      setApplyResult("error");
      setMessage(
        error instanceof ApiError
          ? error.code === "SHOP_EXISTS"
            ? rv.errShopExists
            : error.code === "CITIZEN_ID_TAKEN"
              ? rv.errCitizenTaken
              : error.message
          : rv.errSubmit,
      );
      setSubmitting(false);
    }
  };
  const submitted = application?.status === "SUBMITTED";
  const hasDoc = (type: string) =>
    application?.documents.some((document) => document.type === type);
  return (
    <main className="doc-page verify-page">
      <MerchantHeader />
      <div className="doc-layout verify-layout">
        <MerchantOnboardingSidebar copy={ob} activeStep={3} />
        <section className="doc-center">
          <div className="verify-card">
            <header>
              <h1>{rv.pageTitle}</h1>
              <p>{rv.pageLead}</p>
            </header>
            <ReviewSection
              icon={Store}
              title={rv.sectionShop}
              edit="/merchant/register"
              locked={submitted}
              status={rv.complete}
              labels={rv}
            >
              <dl>
                <dt>{rv.shopNameTh}</dt>
                <dd>{shown(application?.shopName, localDraft.shopName)}</dd>
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
              labels={rv}
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
            <ReviewSection icon={UserRound} title={rv.sectionOwner} edit={localDraft.ownerId ? `/merchant/owner-info?ownerId=${localDraft.ownerId}` : "/merchant/owner-info"} locked={submitted} labels={rv}>
              <dl>
                <dt>{rv.ownerName}</dt>
                <dd>
                  {application?.firstName || owner
                    ? `${application?.firstName || owner?.firstName || ""} ${application?.lastName || owner?.lastName || ""}`.trim()
                    : rv.empty}
                </dd>
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
              status={localDraft.ownerId || (hasDoc("IDENTITY") && hasDoc("ADDRESS")) ? rv.uploaded : rv.incomplete}
              edit="/merchant/documents"
              locked={submitted}
              labels={rv}
            >
              <dl>
                <dt>{rv.idDoc}</dt>
                <dd>{localDraft.ownerId || hasDoc("IDENTITY") ? (<><CheckCircle2 />{rv.uploadedOk}</>) : rv.empty}</dd>
                <dt>{rv.homeDoc}</dt>
                <dd>{localDraft.ownerId || hasDoc("ADDRESS") ? (<><CheckCircle2 />{rv.uploadedOk}</>) : rv.empty}</dd>
                <dt>{rv.businessDoc}</dt>
                <dd>{hasDoc("BUSINESS") ? (<><CheckCircle2 />{rv.uploadedOk}</>) : rv.empty}</dd>
              </dl>
            </ReviewSection>
            <ReviewSection icon={MapPin} title={rv.sectionAddress} edit="/merchant/register" locked={submitted} labels={rv}>
              <p className="review-address">
                {application?.shopAddress || localDraft.address || rv.empty}
                <br />
                {application?.shopProvince || localDraft.province || ""}{" "}
                {application?.shopPostalCode || localDraft.postalCode || ""}
              </p>
            </ReviewSection>
            <ReviewSection icon={Landmark} title={rv.sectionPayout} edit={localDraft.ownerId ? `/merchant/owner-info?ownerId=${localDraft.ownerId}` : "/merchant/owner-info"} locked={submitted} labels={rv}>
              <dl>
                <dt>{rv.bank}</dt>
                <dd>{shown(application?.bankName, owner?.bankName)}</dd>
                <dt>{rv.accountNo}</dt>
                <dd>{application?.bankAccountMasked || owner?.bankAccountNumber || rv.empty}</dd>
                <dt>{rv.accountName}</dt>
                <dd>{shown(application?.bankAccountName, owner?.bankAccountName)}</dd>
              </dl>
            </ReviewSection>
            <label className="verify-confirm">
              <input type="checkbox" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} />
              <span><Check /></span>
              <div>
                <b>{rv.confirmTitle}</b>
                <p>{rv.confirmBody}</p>
              </div>
            </label>
            {message && (
              <p className="doc-message" role="status">
                {message}
              </p>
            )}
            <div className="verify-actions">
              <a href="/merchant/documents">
                <ArrowLeft />
                {ob.common.back}
              </a>
              <button
                className="login-submit"
                type="button"
                onClick={() => setConfirmOpen(true)}
                disabled={!confirmed || submitted || submitting || !application}
              >
                {submitting ? <LoaderCircle className="login-spinner" size={18} /> : <Send />}
                {submitted ? rv.submitted : submitting ? rv.submitting : rv.submit}
              </button>
            </div>
            {submitted && (
              <div className="verify-success">
                <CheckCircle2 />
                <span>
                  <b>ส่งใบสมัครสำเร็จ</b>
                  <small>
                    เลขอ้างอิง {application?.referenceNo} ·
                    ทีมงานจะตรวจสอบและแจ้งผลภายใน 1–3 วันทำการ
                  </small>
                  {application?.shopPath && (
                    <a className="shop-entry-link" href={application.shopPath}>
                      เข้าดูร้าน {application.shopPath}
                    </a>
                  )}
                </span>
              </div>
            )}
          </div>
        </section>
        <aside className="doc-notes verify-notes">
          <section className="after-submit">
            <div>
              <ShieldCheck />
              <CheckCircle2 />
            </div>
            <h3>หลังจากส่งใบสมัคร</h3>
            <ol>
              <li>
                <span>1</span>
                <b>
                  ทีมงานตรวจสอบข้อมูล<small>(ภายใน 1–3 วันทำการ)</small>
                </b>
              </li>
              <li>
                <span>2</span>
                <b>
                  แจ้งผลการอนุมัติ<small>ทางอีเมลและ SMS</small>
                </b>
              </li>
              <li>
                <span>3</span>
                <b>
                  เริ่มใช้งานร้านค้าของคุณ<small>ได้ทันที</small>
                </b>
              </li>
            </ol>
          </section>
          <section className="approval-tips">
            <h3>
              <Clock3 />
              เคล็ดลับให้อនុมัติไวขึ้น
            </h3>
            <ul>
              <li>ข้อมูลตรงกับเอกสารราชการ</li>
              <li>เอกสารชัดเจน ไม่เบลอ</li>
              <li>ไฟล์ไม่เกิน 5 MB</li>
              <li>ครบถ้วนทุกข้อมูล</li>
              <li>เบอร์โทรและอีเมลใช้งานได้จริง</li>
            </ul>
          </section>
          <section className="doc-question">
            <Headphones />
            <div>
              <h3>มีคำถาม?</h3>
              <p>
                ทีมงานพร้อมช่วยเหลือคุณ
                <br />
                ทุกขั้นตอน
              </p>
            </div>
            <a href="#contact">ติดต่อทีมงาน</a>
          </section>
          <div className="verify-handwrite">
            Good Shops
            <br />
            Brighter Thailand
            <i />
          </div>
        </aside>
      </div>
      {confirmOpen && (
        <div className="confirm-dialog-backdrop" onClick={() => { if (!submitting && applyResult !== "success") { setConfirmOpen(false); setApplyResult(null); } }}>
          <div
            className="confirm-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="confirm-apply-title"
            onClick={(event) => event.stopPropagation()}
          >
            {submitting ? (
              <div className="apply-status" role="status">
                <span className="apply-spinner" />
                <h2 id="confirm-apply-title">กำลังส่งใบสมัคร</h2>
                <p>กรุณารอสักครู่ ระบบกำลังบันทึกข้อมูลร้านค้า</p>
              </div>
            ) : applyResult === "success" ? (
              <div className="apply-status ok" role="status">
                <CheckCircle2 />
                <h2 id="confirm-apply-title">สมัครร้านค้าสำเร็จ</h2>
                <p>กำลังพาคุณไปหน้าแรก</p>
              </div>
            ) : applyResult === "error" ? (
              <div className="apply-status bad" role="alert">
                <h2 id="confirm-apply-title">สมัครร้านค้าไม่สำเร็จ</h2>
                <p>{message}</p>
                <button type="button" onClick={() => { setConfirmOpen(false); setApplyResult(null); }}>ปิด</button>
              </div>
            ) : (
              <>
                <h2 id="confirm-apply-title">ยืนยันการสมัครร้านค้า</h2>
                <p>หากกดยืนยัน ระบบจะส่งใบสมัครทันที และจะไม่สามารถแก้ไขข้อมูลได้อีก</p>
                <div>
                  <button type="button" onClick={() => setConfirmOpen(false)}>ยกเลิก</button>
                  <button type="button" onClick={() => void submit()}>ยืนยัน</button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
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
