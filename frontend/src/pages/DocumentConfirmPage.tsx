import { useEffect, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  Building2,
  Check,
  CheckCircle2,
  Headphones,
  Home,
  IdCard,
  Lightbulb,
  ShieldCheck,
  Store,
  UploadCloud,
  UserRound,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { useLanguage } from "../contexts/LanguageContext";
import MerchantOnboardingSidebar from "../components/merchant/MerchantOnboardingSidebar";
import MerchantHeader from "../components/MerchantHeader";
import { merchantApi, type DocumentType } from "../services/api";
import { showApiFailure } from "../services/apiErrors";
import { documentFileError, focusFirstInvalid, validateDocuments } from "../services/merchantApplicationValidation";
import i18n from "../i18n";
import { SHOP_IMAGE_ACCEPT } from "../services/shopImage";
import { readMerchantDraft, rememberDocumentName, writeMerchantDraft } from "../services/merchantDraft";
import "../styles/doc-confirm.css";
import "../styles/form-fields.css";

const docFlow = [
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
    number: "3",
    title: "เอกสารยืนยัน",
    text: "ยืนยันตัวตน และเอกสารธุรกิจ",
    state: "active",
  },
  {
    number: "4",
    title: "ตรวจสอบและส่ง",
    text: "ตรวจสอบข้อมูลก่อนส่ง",
    state: "",
  },
];

const topSteps = [
  ["✓", "ข้อมูลร้านค้า", "done"],
  ["✓", "ข้อมูลเจ้าของร้าน", "done"],
  ["3", "เอกสารยืนยัน", "active"],
  ["4", "ตรวจสอบและส่ง", ""],
];

type DocRowProps = {
  kind: "id" | "home" | "business";
  type: DocumentType;
  title: string;
  description: string;
  uploadText: string;
  exampleTitle: string;
  existingName?: string;
  error?: string;
};
function DocumentRow({
  kind,
  type,
  title,
  description,
  uploadText,
  exampleTitle,
  existingName,
  error,
}: DocRowProps) {
  const { user } = useAuth();
  const Icon = kind === "id" ? IdCard : kind === "home" ? Home : Building2;
  const [file, setFile] = useState(existingName || readMerchantDraft(user?.id).documentNames?.[type] || "");
  const [uploading, setUploading] = useState(false);
  useEffect(() => {
    if (existingName) setFile(existingName);
  }, [existingName]);
  const select = async (selected?: File) => {
    if (!selected) return;
    const fileError = documentFileError(selected);
    if (fileError) {
      setFile(fileError);
      return;
    }
    setUploading(true);
    setFile("กำลังอัปโหลด...");
    try {
      await merchantApi.uploadDocument(type, selected);
      setFile(selected.name);
      rememberDocumentName(user?.id, type, selected.name);
    } catch (error) {
      showApiFailure(error, (text) => setFile(text));
    } finally {
      setUploading(false);
    }
  };
  return (
    <section className="document-row" data-field={type}>
      <div className="document-row-title">
        <Icon />
        <div>
          <h3>{title}</h3>
          <p>{description}</p>
        </div>
      </div>
      <div className="document-row-body">
        <label
          className={file ? "selected" : ""}
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault();
            void select(e.dataTransfer.files[0]);
          }}
        >
          <UploadCloud />
          <b>{file || uploadText}</b>
          <span>{existingName ? "ใช้เอกสารที่มีอยู่ในระบบแล้ว ไม่ต้องอัปโหลดใหม่" : "ไฟล์ JPG, PNG, WEBP หรือ PDF ขนาดไม่เกิน 5 MB"}</span>
          <i>
            {uploading ? "กำลังอัปโหลด" : file ? "เปลี่ยนไฟล์" : "เลือกไฟล์"}
          </i>
          <input
            disabled={uploading}
            type="file"
            accept={`${SHOP_IMAGE_ACCEPT},application/pdf`}
            onChange={(e) => void select(e.target.files?.[0])}
          />
        </label>
        <div className={`document-example ${kind}`}>
          <p>{exampleTitle}</p>
          {kind === "id" ? (
            <div className="id-card-art">
              <span>บัตรประจำตัวประชาชน</span>
              <i />
              <UserRound />
              <small>
                ชื่อ นาย สมชาย ใจดี
                <br />
                เลขประจำตัว 1 2345 67890 12 3
              </small>
            </div>
          ) : kind === "home" ? (
            <div className="home-book-art">
              <b>ทะเบียนบ้าน</b>
              <span>กรมการปกครอง</span>
              <i>ตราครุฑ</i>
            </div>
          ) : (
            <div className="certificate-art">
              <b>หนังสือรับรองบริษัท</b>
              <i>ตราครุฑ</i>
              <span />
              <span />
              <span />
            </div>
          )}
        </div>
      </div>
      {error ? <p className="form-alert" role="alert">{error}</p> : null}
    </section>
  );
}

export default function DocumentConfirmPage() {
  const { user } = useAuth();
  const { onboarding: ob } = useLanguage();
  const d = ob.documents;
  const o = ob.owner;
  const [confirmed, setConfirmed] = useState(() => readMerchantDraft(user?.id).documentsConfirmed ?? true);
  const [message, setMessage] = useState("");
  const [docErrors, setDocErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [existingDocs, setExistingDocs] = useState<Partial<Record<DocumentType, string>>>({});
  useEffect(() => {
    if (!user) return;
    let active = true;
    merchantApi.getDraft().then((draft) => {
      if (!active) return;
      const names: Partial<Record<DocumentType, string>> = {};
      for (const doc of draft.documents) {
        names[doc.type] = doc.originalFilename;
        rememberDocumentName(user.id, doc.type, doc.originalFilename);
      }
      setExistingDocs(names);
    }).catch(() => undefined);
    return () => { active = false; };
  }, [user]);
  useEffect(() => {
    if (!user) return;
    writeMerchantDraft(user.id, { ...readMerchantDraft(user.id), documentsConfirmed: confirmed });
  }, [confirmed]);
  const next = async () => {
    if (submitting) return;
    if (!user) {
      window.location.assign("/login?next=/merchant/documents");
      return;
    }
    if (!confirmed) {
      setMessage(i18n.t("validation.stepDocuments", { ns: "merchant" }));
      return;
    }
    const local = readMerchantDraft(user.id);
    const known = new Set<string>([
      ...Object.keys(local.documentNames || {}),
      ...Object.keys(existingDocs),
    ]);
    const localErrors = validateDocuments(local.owner?.ownerType, [...known]);
    if (Object.keys(localErrors).length > 0) {
      setDocErrors(localErrors);
      setMessage(i18n.t("validation.stepDocuments", { ns: "merchant" }));
      focusFirstInvalid(localErrors);
      return;
    }
    setSubmitting(true);
    setMessage(i18n.t("validation.checking", { ns: "merchant" }));
    try {
      const draft = await merchantApi.getDraft();
      const types = draft.documents.map((item) => item.type);
      const serverErrors = validateDocuments(local.owner?.ownerType, types);
      if (Object.keys(serverErrors).length > 0) {
        setDocErrors(serverErrors);
        setMessage(i18n.t("validation.stepDocuments", { ns: "merchant" }));
        focusFirstInvalid(serverErrors);
        return;
      }
      window.location.assign("/merchant/review");
    } catch (error) {
      showApiFailure(error, setMessage);
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <main className="doc-page">
      <MerchantHeader />
      <div className="doc-layout">
        <MerchantOnboardingSidebar copy={ob} activeStep={2} />
        <section className="doc-center">
          <div className="doc-card">
            <div className="doc-title">
              <h1>{d.pageTitle}</h1>
              <p>{d.pageLead}</p>
            </div>
            <div className="doc-top-steps">
              {ob.steps.map((step, index) => {
                const state = index < 2 ? "done" : index === 2 ? "active" : "";
                const number = index < 2 ? "✓" : String(index + 1);
                return (
                  <div className={state} key={step.title}>
                    <span>{number}</span>
                    <small>{step.title}</small>
                  </div>
                );
              })}
            </div>
            <DocumentRow type="IDENTITY" kind={d.rows[0].kind} title={d.rows[0].title} description={d.rows[0].description} uploadText={d.rows[0].uploadText} exampleTitle={d.rows[0].exampleTitle} existingName={existingDocs.IDENTITY} error={docErrors.IDENTITY} />
            <DocumentRow type="ADDRESS" kind={d.rows[1].kind} title={d.rows[1].title} description={d.rows[1].description} uploadText={d.rows[1].uploadText} exampleTitle={d.rows[1].exampleTitle} existingName={existingDocs.ADDRESS} error={docErrors.ADDRESS} />
            <DocumentRow type="BUSINESS" kind={d.rows[2].kind} title={d.rows[2].title} description={d.rows[2].description} uploadText={d.rows[2].uploadText} exampleTitle={d.rows[2].exampleTitle} existingName={existingDocs.BUSINESS} error={docErrors.BUSINESS} />
            <label className="doc-confirm-check">
              <input
                type="checkbox"
                checked={confirmed}
                onChange={(e) => setConfirmed(e.target.checked)}
              />
              <span>
                <Check />
              </span>
              <p>
                {d.confirmText}
              </p>
            </label>
            <div className="doc-form-actions">
              <a href="/merchant/owner-info">
                <ArrowLeft />
                {ob.common.back}
              </a>
              <button className="login-submit" type="button" onClick={() => void next()} disabled={submitting}>
                {submitting ? i18n.t("validation.checking", { ns: "merchant" }) : d.next} <ArrowRight />
              </button>
            </div>
            {message && <p className="form-alert" role="alert">{message}</p>}
          </div>
        </section>
        <aside className="doc-notes">
          <section className="doc-safe">
            <div>
              <ShieldCheck />
              <CheckCircle2 />
            </div>
            <h3>{o.safeTitle}</h3>
            <ul>{o.safeBullets.map((line) => <li key={line}>{line}</li>)}</ul>
          </section>
          <section className="doc-tips">
            <h3>
              <Lightbulb />
              คำแนะนำการอัปโหลด
            </h3>
            <ul>
              <li>ใช้ภาพที่ชัดเจน ไม่เบลอ</li>
              <li>เห็นข้อมูลครบถ้วนทั้งฉบับ</li>
              <li>ไฟล์มีขนาดไม่เกิน 5 MB</li>
              <li>รองรับไฟล์ JPG, PNG, PDF</li>
              <li>เอกสารต้องยังไม่หมดอายุ</li>
            </ul>
          </section>
          <section className="doc-question">
            <Headphones />
            <div>
              <h3>พบปัญหา?</h3>
              <p>
                ทีมงานพร้อมช่วยเหลือคุณ
                <br />
                ทุกขั้นตอน
              </p>
            </div>
            <a href="#contact">{ob.common.contact}</a>
          </section>
          <div className="doc-shop-art">
            <span>🏪</span>
            <i>🪴</i>
            <b>{ob.common.quoteEn.split("\n").map((line, i) => <span key={i}>{line}{i === 0 && <br />}</span>)}</b>
          </div>
        </aside>
      </div>
    </main>
  );
}
