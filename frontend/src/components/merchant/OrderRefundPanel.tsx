import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { FileUp, RotateCcw } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import { Skeleton } from "../feedback/Skeleton";
import { formatBaht } from "../cart/cartFormat";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import {
  ApiError,
  merchantOrderApi,
  merchantPaymentApi,
  type OrderDetail,
  type RefundSummary,
  type RefundView,
} from "../../services/api";
import SlipPreview from "./SlipPreview";
import { OrderDateTimeField, shipNowInput } from "./ShipDateTimeFields";
import { formatBangkokDateTime, isStaleOrderError, orderErrorMessage } from "./orderFormat";

export type RefundDialogRequest = { kind: "approve" | "reject"; nonce: number } | null;

type Props = {
  shopId: number;
  order: OrderDetail;
  canRefund: boolean;
  deniedTitle?: string;
  /** drawer actions APPROVE_REFUND / REJECT_REFUND open the same dialogs */
  openRequest: RefundDialogRequest;
  onOrderChanged: (order: OrderDetail) => void;
  onStale: () => void;
  notify: (text: string, tone?: "ok" | "fail") => void;
};

type Dialog =
  | { kind: "approve"; refund?: RefundView }
  | { kind: "reject"; refund?: RefundView }
  | { kind: "proof"; refund: RefundView }
  | null;

const MAX_FILE = 5 * 1024 * 1024;
const REFUND_STATUSES = new Set(["REFUND_REQUESTED", "REFUNDED", "CANCELLED"]);
const REJECT_REASON_KEYS = [
  "NO_RETURN_RECEIVED",
  "RETURN_DAMAGED",
  "NO_ISSUE_AS_CLAIMED",
  "INSUFFICIENT_EVIDENCE",
  "OUTSIDE_POLICY",
  "OTHER",
] as const;
type RejectReasonKey = (typeof REJECT_REASON_KEYS)[number];

export function showsRefundPanel(order: OrderDetail) {
  return REFUND_STATUSES.has(order.status)
    || order.paymentStatus === "REFUNDED"
    || order.paymentStatus === "PARTIALLY_REFUNDED"
    || !!order.statusBeforeRefund;
}

export default function OrderRefundPanel({ shopId, order, canRefund, deniedTitle, openRequest, onOrderChanged, onStale, notify }: Props) {
  const { t } = useTranslation("merchant");
  const { localeTag } = usePublicTranslation();
  const money = (value: number | string | null | undefined) => formatBaht(value, localeTag);
  const [summary, setSummary] = useState<RefundSummary | null>(null);
  const [failed, setFailed] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [busy, setBusy] = useState(false);
  const [restock, setRestock] = useState(true);
  const [rejectReasonKey, setRejectReasonKey] = useState<RejectReasonKey | "">("");
  const [rejectReasonOther, setRejectReasonOther] = useState("");
  const [reference, setReference] = useState("");
  const [transferredAt, setTransferredAt] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const summaryRef = useRef<RefundSummary | null>(null);
  summaryRef.current = summary;

  const load = useCallback(() => {
    let alive = true;
    setFailed(null);
    merchantPaymentApi
      .refunds(shopId, order.id)
      .then((value) => { if (alive) setSummary(value); })
      .catch((caught) => { if (alive) setFailed(orderErrorMessage(t, caught)); });
    return () => { alive = false; };
  }, [shopId, order.id, t]);

  useEffect(() => load(), [load, order.version, order.status, order.paymentStatus]);

  const reset = () => {
    setRestock(true);
    setRejectReasonKey("");
    setRejectReasonOther("");
    setReference("");
    setTransferredAt(shipNowInput());
    setFile(null);
    setErrors({});
    setFormError("");
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const resolvedRejectReason = () => {
    if (!rejectReasonKey) return "";
    if (rejectReasonKey === "OTHER") return rejectReasonOther.trim();
    return t(`orderManagement.refund.rejectReasons.${rejectReasonKey}`);
  };

  const lastNonce = useRef(0);
  useEffect(() => {
    if (!openRequest || openRequest.nonce === lastNonce.current) return;
    lastNonce.current = openRequest.nonce;
    if (!canRefund) return;
    const open = summaryRef.current?.refunds.find((r) => r.status === "REQUESTED");
    reset();
    setDialog({ kind: openRequest.kind, refund: open });
  }, [openRequest, canRefund]);

  const close = () => { if (!busy) setDialog(null); };

  const run = async (call: () => Promise<{ changed: boolean; order: OrderDetail }>, doneKey: string) => {
    setBusy(true);
    setErrors({});
    setFormError("");
    try {
      const result = await call();
      setDialog(null);
      onOrderChanged(result.order);
      load();
      notify(t(result.changed ? doneKey : "orderManagement.drawer.toast.unchanged"));
    } catch (caught) {
      if (isStaleOrderError(caught)) {
        setDialog(null);
        notify(orderErrorMessage(t, caught), "fail");
        onStale();
      } else if (caught instanceof ApiError && caught.fieldErrors.length > 0) {
        const next: Record<string, string> = {};
        for (const item of caught.fieldErrors) if (!next[item.field]) next[item.field] = item.message;
        setErrors(next);
      } else {
        setFormError(orderErrorMessage(t, caught));
      }
    } finally {
      setBusy(false);
    }
  };

  const approve = (refund?: RefundView) => run(
    () => (refund
      ? merchantPaymentApi.approveRefund(shopId, order.id, refund.id, { restock })
      : merchantOrderApi.approveRefund(shopId, order.id, { restock })),
    "orderManagement.refund.toast.approved",
  );

  const reject = (refund?: RefundView) => {
    if (!rejectReasonKey) {
      setErrors({ reason: t("orderManagement.refund.reasonRequired") });
      return;
    }
    const text = resolvedRejectReason();
    if (rejectReasonKey === "OTHER" && !text) {
      setErrors({ reason: t("orderManagement.refund.reasonOtherRequired") });
      return;
    }
    if (!text || text.length > 500) {
      setErrors({ reason: t("orderManagement.refund.reasonRequired") });
      return;
    }
    return run(
      () => (refund
        ? merchantPaymentApi.rejectRefund(shopId, order.id, refund.id, { reason: text })
        : merchantOrderApi.rejectRefund(shopId, order.id, { reason: text })),
      "orderManagement.refund.toast.rejected",
    );
  };

  const uploadProof = (refund: RefundView) => {
    const next: Record<string, string> = {};
    if (!file) next.file = t("orderManagement.refund.fileRequired");
    else if (file.size > MAX_FILE) next.file = t("orderManagement.payment.fileTooLarge");
    setErrors(next);
    if (Object.keys(next).length || !file) return;
    const form = new FormData();
    form.set("file", file);
    if (reference.trim()) form.set("reference", reference.trim());
    if (transferredAt) form.set("transferredAt", transferredAt);
    return run(() => merchantPaymentApi.uploadRefundProof(shopId, order.id, refund.id, form), "orderManagement.refund.toast.proofUploaded");
  };

  const refunds = (summary?.refunds ?? []).slice().sort((a, b) => String(b.requestedAt ?? "").localeCompare(String(a.requestedAt ?? "")));
  if (summary && refunds.length === 0 && !failed && order.status !== "REFUND_REQUESTED") return null;
  const fieldError = (name: string) => (errors[name] ? <small className="om-field-error" role="alert">{errors[name]}</small> : null);

  return (
    <section className="om-section om-refund" aria-label={t("orderManagement.refund.title")}>
      <header className="om-section-head">
        <h3><RotateCcw /> {t("orderManagement.refund.title")}</h3>
      </header>
      {failed && <p className="om-error" role="alert">{failed}</p>}
      {!failed && !summary && <div className="om-skel"><Skeleton height={16} /><Skeleton height={64} radius={8} /></div>}
      {summary && (
        <dl className="om-kv om-kv-money">
          <div><dt>{t("orderManagement.refund.paid")}</dt><dd className="num">{money(summary.paidAmount)}</dd></div>
          <div><dt>{t("orderManagement.refund.refunded")}</dt><dd className="num">{money(summary.refundedAmount)}</dd></div>
          <div><dt>{t("orderManagement.refund.refundable")}</dt><dd className="num">{money(summary.refundableAmount)}</dd></div>
        </dl>
      )}

      {refunds.map((refund) => (
        <article className="om-payment-card" key={refund.id}>
          <div className="om-payment-top">
            <b>{t("orderManagement.refund.requestOf", { amount: money(refund.amount) })}</b>
            <span className={`om-pill ${refund.status === "COMPLETED" ? "paid" : refund.status === "REJECTED" ? "cancelled" : "pending"}`}>
              {t(`orderManagement.refund.status.${refund.status}`, { defaultValue: refund.status })}
            </span>
          </div>
          <dl className="om-kv">
            {refund.reason && <div><dt>{t("orderManagement.refund.reason")}</dt><dd>{refund.reason}</dd></div>}
            {refund.requesterType && <div><dt>{t("orderManagement.refund.requester")}</dt><dd>{t(`orderManagement.refund.requesters.${refund.requesterType}`, { defaultValue: refund.requesterType })}</dd></div>}
            <div><dt>{t("orderManagement.refund.requestedAt")}</dt><dd>{formatBangkokDateTime(refund.requestedAt, localeTag)}</dd></div>
            {refund.approvedAt && <div><dt>{t("orderManagement.refund.approvedAt")}</dt><dd>{formatBangkokDateTime(refund.approvedAt, localeTag)}</dd></div>}
            {refund.rejectedAt && <div><dt>{t("orderManagement.refund.rejectedAt")}</dt><dd>{formatBangkokDateTime(refund.rejectedAt, localeTag)}</dd></div>}
            {refund.rejectReason && <div><dt>{t("orderManagement.refund.rejectReason")}</dt><dd>{refund.rejectReason}</dd></div>}
            {refund.restock != null && <div><dt>{t("orderManagement.refund.restock")}</dt><dd>{t(refund.restock ? "orderManagement.refund.yes" : "orderManagement.refund.no")}</dd></div>}
            {refund.proofReference && <div><dt>{t("orderManagement.refund.proofReference")}</dt><dd className="num">{refund.proofReference}</dd></div>}
            {refund.proofTransferredAt && <div><dt>{t("orderManagement.refund.proofTransferredAt")}</dt><dd>{formatBangkokDateTime(refund.proofTransferredAt, localeTag)}</dd></div>}
            {refund.completedAt && <div><dt>{t("orderManagement.refund.completedAt")}</dt><dd>{formatBangkokDateTime(refund.completedAt, localeTag)}</dd></div>}
            {refund.buyerAckStatus && <div><dt>{t("orderManagement.refund.buyerAck")}</dt><dd>{refund.buyerAckStatus}{refund.buyerAckNote ? ` — ${refund.buyerAckNote}` : ""}</dd></div>}
          </dl>
          {refund.proofFileUrl && (
            <div className="om-proof-file">
              <SlipPreview path={refund.proofFileUrl} alt={t("orderManagement.refund.proofAlt")} />
            </div>
          )}
          {refund.status === "APPROVED" && (
            <div className="om-btn-row">
              <button type="button" className="om-btn primary" disabled={!canRefund} title={deniedTitle} onClick={() => { reset(); setDialog({ kind: "proof", refund }); }}>
                {t("orderManagement.refund.uploadProof")}
              </button>
            </div>
          )}
        </article>
      ))}

      {dialog?.kind === "approve" && (
        <ConfirmDialog
          className="om-refund-approve"
          title={t("orderManagement.refund.approveTitle")}
          message={t("orderManagement.refund.approveMessage")}
          confirmLabel={t("orderManagement.refund.approve")}
          cancelLabel={t("orderManagement.drawer.cancel")}
          confirmDisabled={busy}
          onCancel={close}
          onConfirm={() => void approve(dialog.refund)}
        >
          <div className="om-dialog-fields om-refund-approve-fields">
            <div className="om-refund-approve-amount">
              <span>{t("orderManagement.refund.approveAmountLabel")}</span>
              <b className="num">{money(dialog.refund?.amount ?? summary?.refundableAmount)}</b>
            </div>
            {dialog.refund?.reason ? (
              <div className="om-refund-approve-reason">
                <span>{t("orderManagement.refund.approveBuyerReason")}</span>
                <p>{dialog.refund.reason}</p>
              </div>
            ) : null}
            <p className="om-refund-approve-next">{t("orderManagement.refund.approveNextSteps")}</p>
            <label className={`om-refund-approve-restock${restock ? " is-on" : ""}`}>
              <input type="checkbox" checked={restock} disabled={busy} onChange={(e) => setRestock(e.target.checked)} />
              <i aria-hidden="true" />
              <span>
                <b>{t("orderManagement.refund.restockLabel")}</b>
                <small>{t("orderManagement.refund.restockHint")}</small>
              </span>
            </label>
            {formError && <p className="om-error" role="alert">{formError}</p>}
          </div>
        </ConfirmDialog>
      )}

      {dialog?.kind === "reject" && (
        <ConfirmDialog
          className="om-refund-reject"
          title={t("orderManagement.refund.rejectTitle")}
          message={t("orderManagement.refund.rejectMessage")}
          confirmLabel={t("orderManagement.refund.reject")}
          cancelLabel={t("orderManagement.drawer.cancel")}
          danger
          confirmDisabled={
            busy
            || !rejectReasonKey
            || (rejectReasonKey === "OTHER" && !rejectReasonOther.trim())
          }
          onCancel={close}
          onConfirm={() => void reject(dialog.refund)}
        >
          <div className="om-dialog-fields om-refund-reject-fields">
            <div className={`om-field${errors.reason ? " invalid" : ""}`}>
              <span>{t("orderManagement.refund.reasonLabel")}</span>
              <div className="om-reason-list" role="radiogroup" aria-label={t("orderManagement.refund.reasonLabel")}>
                {REJECT_REASON_KEYS.map((key) => {
                  const selected = rejectReasonKey === key;
                  return (
                    <label key={key} className={`om-reason-option${selected ? " is-selected" : ""}`}>
                      <input
                        type="radio"
                        name="refund-reject-reason"
                        value={key}
                        checked={selected}
                        disabled={busy}
                        onChange={() => {
                          setRejectReasonKey(key);
                          setErrors({});
                          setFormError("");
                          if (key !== "OTHER") setRejectReasonOther("");
                        }}
                      />
                      <i aria-hidden="true" />
                      <span>{t(`orderManagement.refund.rejectReasons.${key}`)}</span>
                    </label>
                  );
                })}
              </div>
              {rejectReasonKey === "OTHER" ? (
                <textarea
                  className="om-reason-other"
                  rows={3}
                  maxLength={500}
                  value={rejectReasonOther}
                  disabled={busy}
                  placeholder={t("orderManagement.refund.reasonOtherPlaceholder")}
                  aria-label={t("orderManagement.refund.reasonOtherPlaceholder")}
                  onChange={(e) => {
                    setRejectReasonOther(e.target.value);
                    setErrors({});
                  }}
                />
              ) : null}
              {fieldError("reason")}
            </div>
            {formError && <p className="om-error" role="alert">{formError}</p>}
          </div>
        </ConfirmDialog>
      )}

      {dialog?.kind === "proof" && (
        <ConfirmDialog
          className="om-refund-proof"
          title={t("orderManagement.refund.proofTitle")}
          message={t("orderManagement.refund.proofMessage", { amount: money(dialog.refund.amount) })}
          confirmLabel={t("orderManagement.refund.uploadProof")}
          cancelLabel={t("orderManagement.drawer.cancel")}
          confirmDisabled={busy}
          onCancel={close}
          onConfirm={() => void uploadProof(dialog.refund)}
        >
          <div className="om-dialog-fields om-refund-proof-fields">
            <div className={`om-field om-refund-file${errors.file ? " invalid" : ""}`}>
              <span>{t("orderManagement.refund.proofFile")}</span>
              <input
                ref={fileInputRef}
                className="om-refund-file-input"
                type="file"
                accept="image/jpeg,image/png,image/webp,application/pdf"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              />
              <button
                type="button"
                className={`om-refund-file-drop${file ? " has-file" : ""}`}
                onClick={() => fileInputRef.current?.click()}
              >
                <span className="om-refund-file-icon" aria-hidden="true"><FileUp /></span>
                <span className="om-refund-file-copy">
                  <b>{file ? file.name : t("orderManagement.refund.proofFilePick")}</b>
                  <small>{file ? t("orderManagement.refund.proofFileChange") : t("orderManagement.refund.proofFileHint")}</small>
                </span>
              </button>
              {fieldError("file")}
            </div>
            <label className={`om-field${errors.reference ? " invalid" : ""}`}>
              <span>{t("orderManagement.refund.proofReference")}</span>
              <input
                maxLength={120}
                value={reference}
                placeholder={t("orderManagement.refund.proofReference")}
                onChange={(e) => setReference(e.target.value)}
              />
              {fieldError("reference")}
            </label>
            <OrderDateTimeField
              value={transferredAt}
              localeTag={localeTag}
              title={t("orderManagement.refund.proofTransferredAt")}
              hint={t("orderManagement.refund.proofDateTimeHint")}
              presetsLabel={t("orderManagement.drawer.shippedAtPresets")}
              nowLabel={t("orderManagement.drawer.shippedAtNow")}
              dateLabel={t("orderManagement.drawer.shippedAtDate")}
              timeLabel={t("orderManagement.drawer.shippedAtTime")}
              hourLabel={t("orderManagement.drawer.shippedAtHour")}
              minuteLabel={t("orderManagement.drawer.shippedAtMinute")}
              applyLabel={t("orderManagement.drawer.shippedAtApply")}
              selectedLabel={t("orderManagement.drawer.shippedAtSelected")}
              datePlaceholder={t("orderManagement.refund.proofDate")}
              error={errors.transferredAt}
              periodLabels={{
                night: t("orderManagement.drawer.shippedAtPeriod.night"),
                morning: t("orderManagement.drawer.shippedAtPeriod.morning"),
                afternoon: t("orderManagement.drawer.shippedAtPeriod.afternoon"),
                evening: t("orderManagement.drawer.shippedAtPeriod.evening"),
              }}
              presetLabels={{
                "0900": t("orderManagement.drawer.shippedAtPreset.0900"),
                "1200": t("orderManagement.drawer.shippedAtPreset.1200"),
                "1500": t("orderManagement.drawer.shippedAtPreset.1500"),
                "1800": t("orderManagement.drawer.shippedAtPreset.1800"),
              }}
              onChange={setTransferredAt}
            />
            {formError && <p className="om-error" role="alert">{formError}</p>}
          </div>
        </ConfirmDialog>
      )}
    </section>
  );
}
