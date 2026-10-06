<<<<<<< HEAD
import { useEffect, useState } from "react";
=======
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
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
<<<<<<< HEAD
  Search,
  Send,
  ShieldCheck,
  ShoppingBag,
=======
  LoaderCircle,
  Send,
  ShieldCheck,
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
  Store,
  UserRound,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
<<<<<<< HEAD
import { ApiError, merchantApi, type MerchantApplication } from "../services/api";
import "../styles/doc-confirm.css";
import "../styles/verify-send.css";

const reviewFlow = [
  {
    number: "✓",
    title: "ข้อมูลร้านค้า",
    text: "ชื่อร้าน ประเภทสินค้า",
    state: "done",
  },
  {
    number: "✓",
    title: "ข้อมูลเจ้าของร้าน",
    text: "ข้อมูลส่วนตัว / ข้อมูลบริษัท",
    state: "done",
  },
  {
    number: "✓",
    title: "เอกสารยืนยัน",
    text: "ยืนยันตัวตน และเอกสารธุรกิจ",
    state: "done",
  },
  {
    number: "4",
    title: "ตรวจสอบและส่ง",
    text: "ตรวจสอบข้อมูลก่อนส่ง",
    state: "active",
  },
];

function VerifyBrand() {
  return (
    <a className="brand doc-brand" href="/">
      <span className="brand-mark">
        <ShoppingBag />
        <i>✓</i>
      </span>
      <span>
        <b>
          ThaiShop<span>Fun</span>
        </b>
        <small>Shop Local · Support Thai · Smile Together</small>
      </span>
    </a>
  );
}
=======
import { useLanguage } from "../contexts/LanguageContext";
import MerchantOnboardingSidebar from "../components/merchant/MerchantOnboardingSidebar";
import MerchantHeader from "../components/MerchantHeader";
import { ApiError, merchantApi, type MerchantApplication, type MerchantOwnerPayload, type MerchantShopPayload } from "../services/api";
import { clearMerchantDraft, readMerchantDraft } from "../services/merchantDraft";
import "../styles/doc-confirm.css";
import "../styles/verify-send.css";
import "../styles/form-fields.css";
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb

function ReviewSection({
  icon: Icon,
  title,
<<<<<<< HEAD
  status = "ครบถ้วน",
  edit,
=======
  status,
  edit,
  locked = false,
  labels,
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
  children,
}: {
  icon: typeof Store;
  title: string;
  status?: string;
<<<<<<< HEAD
  edit: string;
=======
  edit?: string;
  locked?: boolean;
  labels: { complete: string; edit: string; locked: string };
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
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
<<<<<<< HEAD
            {status}
=======
            {status ?? labels.complete}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
          </span>
        </div>
        {children}
      </div>
<<<<<<< HEAD
      <a href={edit}>แก้ไข</a>
=======
      {locked ? <span className="review-locked">{labels.locked}</span> : edit ? <a href={edit}>{labels.edit}</a> : null}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    </section>
  );
}

export default function VerifySendPage() {
<<<<<<< HEAD
  const { user } = useAuth();
=======
  const navigate = useNavigate();
  const redirectTimer = useRef<number | null>(null);
  const { user } = useAuth();
  const { onboarding: ob } = useLanguage();
  const rv = ob.review;
  const soc = ob.social;
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
  const [application, setApplication] = useState<MerchantApplication | null>(
    null,
  );
  const [confirmed, setConfirmed] = useState(true);
  const [submitting, setSubmitting] = useState(false);
<<<<<<< HEAD
  const [message, setMessage] = useState("กำลังโหลดข้อมูลใบสมัคร...");
=======
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [applyResult, setApplyResult] = useState<"success" | "error" | null>(null);
  const [message, setMessage] = useState(rv.loading);
  useEffect(() => () => {
    if (redirectTimer.current) window.clearTimeout(redirectTimer.current);
  }, []);
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
  useEffect(() => {
    merchantApi
      .getDraft()
      .then((data) => {
        setApplication(data);
        setMessage("");
      })
      .catch((error) =>
<<<<<<< HEAD
        setMessage(
          error instanceof ApiError ? error.message : "ไม่สามารถโหลดใบสมัครได้",
        ),
      );
  }, []);
  const submit = async () => {
    if (!confirmed) return;
    setSubmitting(true);
    setMessage("กำลังส่งใบสมัคร...");
    try {
      const result = await merchantApi.submit();
      setApplication(result);
      setMessage("");
    } catch (error) {
      setMessage(
        error instanceof ApiError ? error.message : "ส่งใบสมัครไม่สำเร็จ",
=======
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
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
      );
      setSubmitting(false);
    }
  };
  const submitted = application?.status === "SUBMITTED";
  const hasDoc = (type: string) =>
    application?.documents.some((document) => document.type === type);
  return (
    <main className="doc-page verify-page">
<<<<<<< HEAD
      <header className="doc-header">
        <VerifyBrand />
        <nav>
          <a href="/">หน้าหลัก</a>
          <a href="/#recommended">สินค้า</a>
          <a href="/#shops">ร้านค้า</a>
          <a href="/merchant/register">สมัครเป็นร้านค้า</a>
          <a href="/help">ช่วยเหลือ</a>
        </nav>
        <div className="doc-actions">
          <button aria-label="ค้นหา">
            <Search />
          </button>
          <label>
            <span className="mini-flag" />
            <select defaultValue="th">
              <option value="th">ไทย</option>
              <option value="en">English</option>
            </select>
          </label>
          <a href={user ? "/dashboard" : "/login"}>
            {user ? "บัญชีของฉัน" : "เข้าสู่ระบบ"}
          </a>
        </div>
      </header>
      <div className="doc-layout verify-layout">
        <aside className="doc-sidebar">
          <a className="doc-back-home" href="/">
            <ArrowLeft />
            กลับสู่หน้าหลัก
          </a>
          <h2>สมัครเป็นร้านค้า</h2>
          <p>
            สร้างร้านค้าของคุณกับ ThaiShopFun
            <br />
            ง่ายๆ ในไม่กี่ขั้นตอน
          </p>
          <div className="doc-flow">
            {reviewFlow.map((step) => (
              <div className={step.state} key={step.title}>
                <span>{step.number}</span>
                <p>
                  <b>{step.title}</b>
                  <small>{step.text}</small>
                </p>
              </div>
            ))}
          </div>
          <div className="doc-help">
            <Headphones />
            <p>
              <b>ต้องการความช่วยเหลือ?</b>
              <span>ทีมงานพร้อมดูแลคุณ</span>
              <a href="#contact">ติดต่อทีมงาน</a>
            </p>
          </div>
          <blockquote>
            “ร้านค้าไทย
            <br />
            เติบโตไปด้วยกัน” <i>♡</i>
            <span />
          </blockquote>
          <div className="parcel-art">
            <i>📦</i>
            <i>📦</i>
            <i>📦</i>
          </div>
        </aside>
        <section className="doc-center">
          <div className="verify-card">
            <header>
              <h1>ตรวจสอบข้อมูลและส่ง</h1>
              <p>
                กรุณาตรวจสอบข้อมูลทั้งหมดให้ถูกต้องก่อนส่ง
                เพื่อให้การอนุมัติรวดเร็วยิ่งขึ้น
              </p>
            </header>
            <ReviewSection
              icon={Store}
              title="ข้อมูลร้านค้า"
              edit="/merchant/register"
            >
              <dl>
                <dt>ชื่อร้านค้า (ไทย)</dt>
                <dd>{application?.shopName || "—"}</dd>
                <dt>ชื่อร้านค้า (English)</dt>
                <dd>{application?.shopNameEn || "—"}</dd>
                <dt>ประเภทการขาย</dt>
                <dd>
                  {application?.saleType === "RESELLER"
                    ? "ตัวแทนจำหน่าย / รีเซลเลอร์"
                    : application?.saleType === "OWNER"
                      ? "ขายสินค้าของตัวเอง (เจ้าของสินค้า)"
                      : "—"}
                </dd>
                <dt>หมวดหมู่สินค้า</dt>
                <dd>{application?.category || "—"}</dd>
                <dt>รายละเอียดร้านค้า</dt>
                <dd>{application?.description || "—"}</dd>
                <dt>โลโก้ร้านค้า</dt>
                <dd>
                  {hasDoc("LOGO") ? (
                    <span className="review-logo">
                      Uploaded<small>SHOP LOGO</small>
                    </span>
                  ) : (
                    "—"
=======
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
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
                  )}
                </dd>
              </dl>
            </ReviewSection>
            <ReviewSection
<<<<<<< HEAD
              icon={UserRound}
              title="ข้อมูลเจ้าของร้าน"
              edit="/merchant/owner-info"
            >
              <dl>
                <dt>ชื่อ–นามสกุล</dt>
                <dd>
                  {application
                    ? `${application.firstName || ""} ${application.lastName || ""}`
                    : "—"}
                </dd>
                <dt>เบอร์โทรศัพท์</dt>
                <dd>{application?.phone || "—"}</dd>
                <dt>อีเมล</dt>
                <dd>{application?.email || "—"}</dd>
                <dt>ที่อยู่ตามบัตรประชาชน</dt>
                <dd>
                  {application
                    ? `${application.houseNo || ""} หมู่ ${application.villageNo || "-"} ${application.subdistrict || ""} ${application.district || ""} ${application.province || ""} ${application.postalCode || ""}`
                    : "—"}
=======
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
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
                </dd>
              </dl>
            </ReviewSection>
            <ReviewSection
              icon={FileCheck2}
<<<<<<< HEAD
              title="เอกสารยืนยัน"
              status={
                hasDoc("IDENTITY") && hasDoc("ADDRESS")
                  ? "อัปโหลดครบถ้วน"
                  : "ยังไม่ครบ"
              }
              edit="/merchant/documents"
            >
              <dl>
                <dt>บัตรประชาชน</dt>
                <dd>
                  {hasDoc("IDENTITY") ? (
                    <>
                      <CheckCircle2 />
                      อัปโหลดแล้ว
                    </>
                  ) : (
                    "—"
                  )}
                </dd>
                <dt>ทะเบียนบ้าน</dt>
                <dd>
                  {hasDoc("ADDRESS") ? (
                    <>
                      <CheckCircle2 />
                      อัปโหลดแล้ว
                    </>
                  ) : (
                    "—"
                  )}
                </dd>
                <dt>หนังสือรับรองบริษัท</dt>
                <dd>
                  {hasDoc("BUSINESS") ? (
                    <>
                      <CheckCircle2 />
                      อัปโหลดแล้ว
                    </>
                  ) : (
                    "–"
                  )}
                </dd>
              </dl>
            </ReviewSection>
            <ReviewSection
              icon={MapPin}
              title="ที่อยู่ร้านค้า"
              edit="/merchant/register"
            >
              <p className="review-address">
                {application?.shopAddress || "—"}
                <br />
                {application?.shopProvince || ""}{" "}
                {application?.shopPostalCode || ""}
              </p>
            </ReviewSection>
            <ReviewSection
              icon={Landmark}
              title="ข้อมูลรับเงิน"
              edit="/merchant/owner-info"
            >
              <dl>
                <dt>ธนาคาร</dt>
                <dd>{application?.bankName || "—"}</dd>
                <dt>เลขบัญชี</dt>
                <dd>{application?.bankAccountMasked || "—"}</dd>
                <dt>ชื่อบัญชี</dt>
                <dd>{application?.bankAccountName || "—"}</dd>
              </dl>
            </ReviewSection>
            <label className="verify-confirm">
              <input
                type="checkbox"
                checked={confirmed}
                onChange={(e) => setConfirmed(e.target.checked)}
              />
              <span>
                <Check />
              </span>
              <div>
                <b>ข้าพเจ้ายืนยันว่าข้อมูลทั้งหมดถูกต้องและเป็นความจริง</b>
                <p>
                  ข้าพเจ้ายอมรับ <a href="#terms">ข้อกำหนดการใช้งาน</a>{" "}
                  สำหรับผู้ขาย <a href="#privacy">นโยบายความเป็นส่วนตัว</a> และ
                  <a href="#service">เงื่อนไขการให้บริการ</a>ของ ThaiShopFun
                  <br />
                  และยินยอมให้ทางบริษัทตรวจสอบข้อมูลและเอกสารที่เกี่ยวข้อง
                </p>
=======
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
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
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
<<<<<<< HEAD
                ย้อนกลับ
              </a>
              <button
                onClick={() => void submit()}
                disabled={!confirmed || submitted || submitting || !application}
              >
                <Send />
                {submitted
                  ? "ส่งใบสมัครแล้ว"
                  : submitting
                    ? "กำลังส่ง..."
                    : "ส่งสมัครเป็นร้านค้า"}
=======
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
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
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
<<<<<<< HEAD
=======
                  {application?.shopPath && (
                    <a className="shop-entry-link" href={application.shopPath}>
                      เข้าดูร้าน {application.shopPath}
                    </a>
                  )}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
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
<<<<<<< HEAD
    </main>
  );
}
=======
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
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
