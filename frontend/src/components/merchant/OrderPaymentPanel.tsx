import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Banknote, CircleAlert, FileCheck2 } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import { Skeleton } from "../feedback/Skeleton";
import { formatBaht } from "../cart/cartFormat";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import {
  ApiError,
  merchantPaymentApi,
  type OrderDetail,
  type PaymentProofView,
  type PaymentView,
} from "../../services/api";
import SlipPreview from "./SlipPreview";
import {
  bangkokNowInput,
  formatBangkokDateTime,
  isStaleOrderError,
  numberOf,
  orderErrorMessage,
  paymentTone,
} from "./orderFormat";

type Props = {
  shopId: number;
  order: OrderDetail;
  canUpdate: boolean;
  deniedTitle?: string;
  /** bump to open the COD collect dialog (drawer action COLLECT_COD) */
  codRequest: number;
  /** transitions of the order contain COLLECT_COD */
  canCollectCod: boolean;
  onOrderChanged: (order: OrderDetail) => void;
  onStale: () => void;
  notify: (text: string, tone?: "ok" | "fail") => void;
};

type Dialog =
  | { kind: "confirm"; payment: PaymentView; proof?: PaymentProofView }
  | { kind: "reject"; payment: PaymentView }
  | { kind: "cod" }
  | null;

const MAX_FILE = 5 * 1024 * 1024;

export default function OrderPaymentPanel({ shopId, order, canUpdate, deniedTitle, codRequest, canCollectCod, onOrderChanged, onStale, notify }: Props) {
  const { t } = useTranslation("merchant");
  const { localeTag } = usePublicTranslation();
  const money = (value: number | string | null | undefined) => formatBaht(value, localeTag);
  const [payments, setPayments] = useState<PaymentView[] | null>(null);
  const [failed, setFailed] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [busy, setBusy] = useState(false);
  const [reason, setReason] = useState("");
  const [acceptMismatch, setAcceptMismatch] = useState(false);
  const [mismatchNeeded, setMismatchNeeded] = useState(false);
  const [amount, setAmount] = useState("");
  const [collectedAt, setCollectedAt] = useState("");
  const [reference, setReference] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const isCod = order.paymentMethod === "COD";

  const load = useCallback(() => {
    let alive = true;
    setFailed(null);
    merchantPaymentApi
      .list(shopId, order.id)
      .then((rows) => { if (alive) setPayments(rows); })
      .catch((caught) => { if (alive) setFailed(orderErrorMessage(t, caught)); });
    return () => { alive = false; };
  }, [shopId, order.id, t]);

  useEffect(() => load(), [load, order.version, order.paymentStatus, order.status]);

  const openCod = useCallback(() => {
    setReason("");
    setAcceptMismatch(false);
    setMismatchNeeded(false);
    setAmount(String(numberOf(order.grandTotal)));
    setCollectedAt("");
    setReference("");
    setFile(null);
    setErrors({});
    setFormError("");
    setDialog({ kind: "cod" });
  }, [order.grandTotal]);

  const lastCodRequest = useRef(codRequest);
  useEffect(() => {
    if (codRequest === lastCodRequest.current) return;
    lastCodRequest.current = codRequest;
    if (canUpdate && canCollectCod) openCod();
  }, [codRequest, canUpdate, canCollectCod, openCod]);

  const resetForm = () => {
    setReason("");
    setAcceptMismatch(false);
    setMismatchNeeded(false);
    setErrors({});
    setFormError("");
  };
  const close = () => { if (!busy) setDialog(null); };

  const applyFailure = (caught: unknown) => {
    if (caught instanceof ApiError && caught.code === "PAYMENT_AMOUNT_MISMATCH") {
      setMismatchNeeded(true);
      setFormError(caught.message);
      return;
    }
    if (caught instanceof ApiError && caught.fieldErrors.length > 0) {
      const next: Record<string, string> = {};
      for (const item of caught.fieldErrors) if (!next[item.field]) next[item.field] = item.message;
      setErrors(next);
      setFormError("");
      return;
    }
    if (isStaleOrderError(caught)) {
      setDialog(null);
      notify(orderErrorMessage(t, caught), "fail");
      onStale();
      return;
    }
    setFormError(orderErrorMessage(t, caught));
  };

  const submit = async (run: () => Promise<{ changed: boolean; order: OrderDetail }>, doneKey: string) => {
    setBusy(true);
    setErrors({});
    setFormError("");
    try {
      const result = await run();
      setDialog(null);
      onOrderChanged(result.order);
      load();
      notify(t(result.changed ? doneKey : "orderManagement.drawer.toast.unchanged"));
    } catch (caught) {
      applyFailure(caught);
    } finally {
      setBusy(false);
    }
  };

  const confirmSlip = (payment: PaymentView) => {
    const needsReason = acceptMismatch;
    if (needsReason && !reason.trim()) { setErrors({ reason: t("orderManagement.payment.reasonRequired") }); return; }
    void submit(
      () => merchantPaymentApi.confirm(shopId, order.id, payment.id, {
        reason: reason.trim() || undefined,
        acceptAmountMismatch: acceptMismatch || undefined,
        expectedVersion: order.version,
      }),
      "orderManagement.payment.toast.confirmed",
    );
  };

  const rejectSlip = (payment: PaymentView) => {
    if (!reason.trim()) { setErrors({ reason: t("orderManagement.payment.reasonRequired") }); return; }
    void submit(
      () => merchantPaymentApi.reject(shopId, order.id, payment.id, { reason: reason.trim(), expectedVersion: order.version }),
      "orderManagement.payment.toast.rejected",
    );
  };

  const collectCod = () => {
    const value = Number(amount.replace(/,/g, ""));
    const next: Record<string, string> = {};
    if (!Number.isFinite(value) || value <= 0) next.amount = t("orderManagement.payment.amountInvalid");
    const mismatch = Number.isFinite(value) && Math.round(value * 100) !== Math.round(numberOf(order.grandTotal) * 100);
    const accept = (mismatch || mismatchNeeded) && acceptMismatch;
    if ((mismatch || mismatchNeeded) && !acceptMismatch) next.amount = next.amount || t("orderManagement.payment.mismatchNeedsAccept");
    if (accept && !reason.trim()) next.reason = t("orderManagement.payment.reasonRequired");
    if (reference.trim() && (reference.trim().length < 4 || reference.trim().length > 120)) next.reference = t("orderManagement.payment.referenceInvalid");
    if (file && file.size > MAX_FILE) next.file = t("orderManagement.payment.fileTooLarge");
    setErrors(next);
    if (Object.keys(next).length) return;
    void submit(
      () => merchantPaymentApi.collectCod(shopId, order.id, {
        amount: value.toFixed(2),
        collectedAt: collectedAt || undefined,
        reference: reference.trim() || undefined,
        reason: reason.trim() || undefined,
        acceptAmountMismatch: accept || undefined,
        expectedVersion: order.version,
      }, file),
      "orderManagement.payment.toast.codCollected",
    );
  };

  const sorted = (payments ?? []).slice().sort((a, b) => b.attemptNo - a.attemptNo);
  const codValue = Number(amount.replace(/,/g, ""));
  const codMismatch = dialog?.kind === "cod" && (mismatchNeeded || (Number.isFinite(codValue) && Math.round(codValue * 100) !== Math.round(numberOf(order.grandTotal) * 100)));
  const fieldError = (name: string) => (errors[name] ? <small className="om-field-error" role="alert">{errors[name]}</small> : null);

  return (
    <section className="om-section om-payment" aria-label={t("orderManagement.payment.title")}>
      <header className="om-section-head">
        <h3><Banknote /> {t("orderManagement.payment.title")}</h3>
        {canCollectCod && (
          <button type="button" className="om-btn primary" disabled={!canUpdate} title={deniedTitle} onClick={openCod}>
            {t("orderManagement.payment.collectCod")}
          </button>
        )}
      </header>

      {isCod && <p className="om-note"><CircleAlert /> {t("orderManagement.payment.codHint")}</p>}
      {failed && <p className="om-error" role="alert">{failed}</p>}
      {!failed && payments === null && <div className="om-skel"><Skeleton height={16} /><Skeleton height={64} radius={8} /></div>}
      {payments && sorted.length === 0 && !failed && <p className="om-muted">{t("orderManagement.payment.empty")}</p>}

      {sorted.map((payment) => {
        const pendingProof = (payment.proofs ?? []).find((p) => p.status === "PENDING_REVIEW");
        const canDecide = payment.status === "PENDING" && !isCod && !!pendingProof;
        return (
          <article className="om-payment-card" key={payment.id}>
            <div className="om-payment-top">
              <b>{t(`orderManagement.payment.provider.${payment.provider}`, { defaultValue: payment.provider })}</b>
              <span className={`om-pill ${paymentTone(payment.status)}`}>{t(`orderManagement.payment.status.${payment.status}`, { defaultValue: payment.status })}</span>
              <strong className="num">{money(payment.amount)}</strong>
            </div>
            <dl className="om-kv">
              <div><dt>{t("orderManagement.payment.attempt")}</dt><dd>#{payment.attemptNo}</dd></div>
              <div><dt>{t("orderManagement.payment.createdAt")}</dt><dd>{formatBangkokDateTime(payment.createdAt, localeTag)}</dd></div>
              {payment.capturedAt && <div><dt>{t("orderManagement.payment.capturedAt")}</dt><dd>{formatBangkokDateTime(payment.capturedAt, localeTag)}</dd></div>}
              {payment.failureCode && <div><dt>{t("orderManagement.payment.failure")}</dt><dd>{payment.failureCode}</dd></div>}
            </dl>

            {(payment.proofs ?? []).map((proof) => (
              <div className="om-proof" key={proof.id}>
                <div className="om-proof-file">
                  {proof.fileUrl
                    ? <SlipPreview path={proof.fileUrl} contentType={proof.contentType} alt={t("orderManagement.payment.slipAlt")} />
                    : <span className="om-muted">{t("orderManagement.payment.noFile")}</span>}
                </div>
                <dl className="om-kv">
                  <div>
                    <dt>{t("orderManagement.payment.proofType")}</dt>
                    <dd>{t(`orderManagement.payment.proofTypes.${proof.proofType || "SLIP"}`, { defaultValue: proof.proofType || "SLIP" })}</dd>
                  </div>
                  <div>
                    <dt>{t("orderManagement.payment.proofStatus")}</dt>
                    <dd>{t(`orderManagement.payment.proofStatuses.${proof.status}`, { defaultValue: proof.status })}</dd>
                  </div>
                  {proof.amountDeclared != null && (
                    <div>
                      <dt>{t("orderManagement.payment.amountDeclared")}</dt>
                      <dd>
                        {money(proof.amountDeclared)}
                        {proof.amountMismatch && <span className="om-badge warn">{t("orderManagement.payment.mismatch")}</span>}
                      </dd>
                    </div>
                  )}
                  {proof.referenceNo && <div><dt>{t("orderManagement.payment.reference")}</dt><dd className="num">{proof.referenceNo}</dd></div>}
                  {proof.bankName && <div><dt>{t("orderManagement.payment.bank")}</dt><dd>{proof.bankName}</dd></div>}
                  {proof.transferredAt && <div><dt>{t("orderManagement.payment.transferredAt")}</dt><dd>{formatBangkokDateTime(proof.transferredAt, localeTag)}</dd></div>}
                  {proof.uploadedAt && <div><dt>{t("orderManagement.payment.uploadedAt")}</dt><dd>{formatBangkokDateTime(proof.uploadedAt, localeTag)}</dd></div>}
                  {proof.reviewedAt && <div><dt>{t("orderManagement.payment.reviewedAt")}</dt><dd>{formatBangkokDateTime(proof.reviewedAt, localeTag)}</dd></div>}
                  {proof.rejectReason && <div><dt>{t("orderManagement.payment.rejectReason")}</dt><dd>{proof.rejectReason}</dd></div>}
                </dl>
              </div>
            ))}

            {canDecide && (
              <div className="om-btn-row">
                <button
                  type="button" className="om-btn primary" disabled={!canUpdate} title={deniedTitle}
                  onClick={() => { resetForm(); setAcceptMismatch(false); setDialog({ kind: "confirm", payment, proof: pendingProof }); }}
                >
                  <FileCheck2 /> {t("orderManagement.payment.confirmSlip")}
                </button>
                <button
                  type="button" className="om-btn danger" disabled={!canUpdate} title={deniedTitle}
                  onClick={() => { resetForm(); setDialog({ kind: "reject", payment }); }}
                >
                  {t("orderManagement.payment.rejectSlip")}
                </button>
              </div>
            )}

            {(payment.history ?? []).length > 0 && (
              <details className="om-history">
                <summary>{t("orderManagement.payment.history")}</summary>
                <ul>
                  {(payment.history ?? []).map((entry, index) => (
                    <li key={entry.id ?? index}>
                      <b>{entry.event}</b>
                      {entry.toStatus && <span> → {entry.toStatus}</span>}
                      <small>{formatBangkokDateTime(entry.at, localeTag)}</small>
                      {entry.reason && <em>{entry.reason}</em>}
                    </li>
                  ))}
                </ul>
              </details>
            )}
          </article>
        );
      })}

      {dialog?.kind === "confirm" && (() => {
        const proof = dialog.proof;
        const mismatch = !!proof?.amountMismatch || mismatchNeeded;
        return (
          <ConfirmDialog
            title={t("orderManagement.payment.confirmTitle")}
            message={t("orderManagement.payment.confirmMessage", { amount: money(proof?.amountDeclared ?? dialog.payment.amount) })}
            confirmLabel={t("orderManagement.payment.confirmSlip")}
            cancelLabel={t("orderManagement.drawer.cancel")}
            confirmDisabled={busy || (mismatch && (!acceptMismatch || !reason.trim()))}
            onCancel={close}
            onConfirm={() => confirmSlip(dialog.payment)}
          >
            <div className="om-dialog-fields">
              {mismatch && (
                <div className="om-warn" role="alert">
                  <b>{t("orderManagement.payment.mismatchTitle")}</b>
                  <span>{t("orderManagement.payment.mismatchBody", { declared: money(proof?.amountDeclared), total: money(order.grandTotal) })}</span>
                  <label className="om-check">
                    <input type="checkbox" checked={acceptMismatch} onChange={(e) => setAcceptMismatch(e.target.checked)} />
                    {t("orderManagement.payment.acceptMismatch")}
                  </label>
                </div>
              )}
              <label className="om-field">
                <span>{t(mismatch ? "orderManagement.payment.reasonRequiredLabel" : "orderManagement.payment.noteOptional")}</span>
                <textarea rows={2} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
                {fieldError("reason")}
              </label>
              {formError && !mismatchNeeded && <p className="om-error" role="alert">{formError}</p>}
            </div>
          </ConfirmDialog>
        );
      })()}

      {dialog?.kind === "reject" && (
        <ConfirmDialog
          title={t("orderManagement.payment.rejectTitle")}
          message={t("orderManagement.payment.rejectMessage")}
          confirmLabel={t("orderManagement.payment.rejectSlip")}
          cancelLabel={t("orderManagement.drawer.cancel")}
          danger
          confirmDisabled={busy || !reason.trim()}
          onCancel={close}
          onConfirm={() => rejectSlip(dialog.payment)}
        >
          <div className="om-dialog-fields">
            <label className="om-field">
              <span>{t("orderManagement.payment.reasonRequiredLabel")}</span>
              <textarea rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
              {fieldError("reason")}
            </label>
            {formError && <p className="om-error" role="alert">{formError}</p>}
          </div>
        </ConfirmDialog>
      )}

      {dialog?.kind === "cod" && (
        <ConfirmDialog
          title={t("orderManagement.payment.codTitle")}
          message={t("orderManagement.payment.codMessage", { total: money(order.grandTotal) })}
          confirmLabel={t("orderManagement.payment.collectCod")}
          cancelLabel={t("orderManagement.drawer.cancel")}
          confirmDisabled={busy}
          onCancel={close}
          onConfirm={collectCod}
        >
          <div className="om-dialog-fields">
            <label className="om-field">
              <span>{t("orderManagement.payment.codAmount")}</span>
              <input inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
              {fieldError("amount")}
            </label>
            <label className="om-field">
              <span>{t("orderManagement.payment.codCollectedAt")}</span>
              <input type="datetime-local" max={bangkokNowInput()} value={collectedAt} onChange={(e) => setCollectedAt(e.target.value)} />
              {fieldError("collectedAt")}
            </label>
            <label className="om-field">
              <span>{t("orderManagement.payment.codReference")}</span>
              <input maxLength={120} value={reference} onChange={(e) => setReference(e.target.value)} />
              {fieldError("reference")}
            </label>
            <label className="om-field">
              <span>{t("orderManagement.payment.codEvidence")}</span>
              <input type="file" accept="image/jpeg,image/png,image/webp,application/pdf" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
              {fieldError("file")}
            </label>
            {codMismatch && (
              <div className="om-warn" role="alert">
                <b>{t("orderManagement.payment.mismatchTitle")}</b>
                <span>{formError || t("orderManagement.payment.codMismatchBody", { total: money(order.grandTotal) })}</span>
                <label className="om-check">
                  <input type="checkbox" checked={acceptMismatch} onChange={(e) => setAcceptMismatch(e.target.checked)} />
                  {t("orderManagement.payment.acceptMismatch")}
                </label>
                <label className="om-field">
                  <span>{t("orderManagement.payment.reasonRequiredLabel")}</span>
                  <textarea rows={2} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
                  {fieldError("reason")}
                </label>
              </div>
            )}
            {formError && !codMismatch && <p className="om-error" role="alert">{formError}</p>}
          </div>
        </ConfirmDialog>
      )}
    </section>
  );
}
