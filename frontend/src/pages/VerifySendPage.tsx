import { useEffect, useState } from "react";
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
  Search,
  Send,
  ShieldCheck,
  ShoppingBag,
  Store,
  UserRound,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
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

function ReviewSection({
  icon: Icon,
  title,
  status = "ครบถ้วน",
  edit,
  children,
}: {
  icon: typeof Store;
  title: string;
  status?: string;
  edit: string;
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
            {status}
          </span>
        </div>
        {children}
      </div>
      <a href={edit}>แก้ไข</a>
    </section>
  );
}

export default function VerifySendPage() {
  const { user } = useAuth();
  const [application, setApplication] = useState<MerchantApplication | null>(
    null,
  );
  const [confirmed, setConfirmed] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState("กำลังโหลดข้อมูลใบสมัคร...");
  useEffect(() => {
    merchantApi
      .getDraft()
      .then((data) => {
        setApplication(data);
        setMessage("");
      })
      .catch((error) =>
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
      );
      setSubmitting(false);
    }
  };
  const submitted = application?.status === "SUBMITTED";
  const hasDoc = (type: string) =>
    application?.documents.some((document) => document.type === type);
  return (
    <main className="doc-page verify-page">
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
                  )}
                </dd>
              </dl>
            </ReviewSection>
            <ReviewSection
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
                </dd>
              </dl>
            </ReviewSection>
            <ReviewSection
              icon={FileCheck2}
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
    </main>
  );
}
