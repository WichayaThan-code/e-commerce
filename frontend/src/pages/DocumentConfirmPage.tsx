import { useEffect, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  Building2,
  Check,
  Home,
  IdCard,
  UploadCloud,
  UserRound,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { useLanguage } from "../contexts/LanguageContext";
import MerchantOnboardingLayout from "../components/merchant/MerchantOnboardingLayout";
import OnboardingAside from "../components/merchant/OnboardingAside";
import RegisterStepper from "../components/merchant/RegisterStepper";
import MerchantHeader from "../components/MerchantHeader";
import DocumentFilePreview from "../components/merchant/DocumentFilePreview";
import { merchantApi, type DocumentType } from "../services/api";
import { showApiFailure } from "../services/apiErrors";
import FieldError from "../components/form/FieldError";
import { documentFileError, focusFirstInvalid, validateDocuments } from "../services/merchantApplicationValidation";
import i18n from "../i18n";
import { SHOP_IMAGE_ACCEPT } from "../services/shopImage";
import { readMerchantDraft, rememberDocumentName, writeMerchantDraft } from "../services/merchantDraft";
import "../styles/doc-confirm.css";
import "../styles/form-fields.css";
import "../styles/form-invalid.css";

type DocRowProps = {
  kind: "id" | "home" | "business";
  type: DocumentType;
  title: string;
  description: string;
  uploadText: string;
  exampleTitle: string;
  existingName?: string;
  error?: string;
  onClearError?: (type: DocumentType) => void;
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
  onClearError,
}: DocRowProps) {
  const { user } = useAuth();
  const Icon = kind === "id" ? IdCard : kind === "home" ? Home : Building2;
  const [fileName, setFileName] = useState(existingName || readMerchantDraft(user?.id).documentNames?.[type] || "");
  const [uploading, setUploading] = useState(false);
  const [previewFile, setPreviewFile] = useState<File | null>(null);
  const [rowMessage, setRowMessage] = useState("");
  const errorId = `field-error-doc-${type}`;
  useEffect(() => {
    if (existingName) setFileName(existingName);
  }, [existingName]);
  const select = async (selected?: File) => {
    if (!selected) return;
    const fileError = documentFileError(selected);
    if (fileError) {
      setRowMessage(fileError);
      return;
    }
    setUploading(true);
    setRowMessage("");
    try {
      await merchantApi.uploadDocument(type, selected);
      setFileName(selected.name);
      setPreviewFile(selected);
      rememberDocumentName(user?.id, type, selected.name);
      onClearError?.(type);
    } catch (err) {
      showApiFailure(err, setRowMessage);
    } finally {
      setUploading(false);
    }
  };
  const displayError = error || rowMessage;
  const exampleArt =
    kind === "id" ? (
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
    );
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
          className={`${fileName && !displayError ? "selected" : ""}${displayError ? " is-invalid" : ""}`.trim()}
          aria-invalid={Boolean(displayError)}
          aria-describedby={displayError ? errorId : undefined}
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault();
            void select(e.dataTransfer.files[0]);
          }}
        >
          <UploadCloud />
          <b>{fileName || uploadText}</b>
          <span>{existingName && !previewFile ? "ใช้เอกสารที่มีอยู่ในระบบแล้ว ไม่ต้องอัปโหลดใหม่" : "ไฟล์ JPG, PNG, WEBP หรือ PDF ขนาดไม่เกิน 5 MB"}</span>
          <i>
            {uploading ? "กำลังอัปโหลด" : fileName ? "เปลี่ยนไฟล์" : "เลือกไฟล์"}
          </i>
          <input
            disabled={uploading}
            type="file"
            accept={`${SHOP_IMAGE_ACCEPT},application/pdf`}
            onChange={(e) => void select(e.target.files?.[0])}
          />
        </label>
        <DocumentFilePreview
          type={type}
          kind={kind}
          exampleTitle={exampleTitle}
          fileName={fileName}
          uploading={uploading}
          previewFile={previewFile}
          example={exampleArt}
        />
      </div>
      <FieldError text={displayError} id={errorId} />
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
    if (localErrors.PORTRAIT) {
      setDocErrors(localErrors);
      setMessage(i18n.t("validation.stepDocuments", { ns: "merchant" }));
      window.location.assign("/merchant/owner-info");
      return;
    }
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
      if (serverErrors.PORTRAIT) {
        setDocErrors(serverErrors);
        setMessage(i18n.t("validation.stepDocuments", { ns: "merchant" }));
        window.location.assign("/merchant/owner-info");
        return;
      }
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
    <main className="doc-page onboard-page">
      <MerchantHeader />
      <MerchantOnboardingLayout
        aside={(
          <OnboardingAside
            safeSummaryLabel={ob.common.asideSafeSummary}
            safe={{ title: o.safeTitle, items: [...o.safeBullets] }}
            tips={{ title: d.tipsTitle, items: [...d.tipsBullets] }}
            help={{
              title: d.helpTitle,
              lead: d.helpLead,
              contactLabel: ob.common.contact,
            }}
            trustLabel={ob.common.trustSslPdpa}
          />
        )}
      >
          <div className="doc-card">
            <div className="doc-title">
              <h1>{d.pageTitle}</h1>
              <p>{d.pageLead}</p>
            </div>
            <RegisterStepper currentStep={3} />
            <DocumentRow type="IDENTITY" kind={d.rows[0].kind} title={d.rows[0].title} description={d.rows[0].description} uploadText={d.rows[0].uploadText} exampleTitle={d.rows[0].exampleTitle} existingName={existingDocs.IDENTITY} error={docErrors.IDENTITY} onClearError={(key) => setDocErrors((prev) => { const next = { ...prev }; delete next[key]; return next; })} />
            <DocumentRow type="ADDRESS" kind={d.rows[1].kind} title={d.rows[1].title} description={d.rows[1].description} uploadText={d.rows[1].uploadText} exampleTitle={d.rows[1].exampleTitle} existingName={existingDocs.ADDRESS} error={docErrors.ADDRESS} onClearError={(key) => setDocErrors((prev) => { const next = { ...prev }; delete next[key]; return next; })} />
            <DocumentRow type="BUSINESS" kind={d.rows[2].kind} title={d.rows[2].title} description={d.rows[2].description} uploadText={d.rows[2].uploadText} exampleTitle={d.rows[2].exampleTitle} existingName={existingDocs.BUSINESS} error={docErrors.BUSINESS} onClearError={(key) => setDocErrors((prev) => { const next = { ...prev }; delete next[key]; return next; })} />
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
      </MerchantOnboardingLayout>
    </main>
  );
}
