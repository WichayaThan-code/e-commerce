import { useEffect, useRef, useState } from "react";
import ConfirmDialog from "../form/ConfirmDialog";
import { Skeleton } from "../feedback/Skeleton";
import { formatBaht } from "../cart/cartFormat";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import {
  ApiError,
  authorizedBlob,
  buyerOrderApi,
  moneyValue,
  type OrderDetail,
  type RefundSummary,
  type RefundView,
} from "../../services/api";

const LOAD_STATUSES = new Set(["REFUND_REQUESTED", "REFUNDED", "CANCELLED"]);

function when(value: string | null | undefined, localeTag: string) {
  if (!value) return "";
  return new Date(value).toLocaleString(localeTag, { dateStyle: "medium", timeStyle: "short" });
}

type Props = {
  order: OrderDetail;
  onChanged: () => void;
};

type Dialog =
  | { kind: "request" }
  | { kind: "dispute"; refund: RefundView }
  | null;

export default function OrderRefundCard({ order, onChanged }: Props) {
  const { tp, localeTag } = usePublicTranslation();
  const money = (value: unknown) => formatBaht(value as number, localeTag);
  const canRequest = (order.allowedTransitions ?? []).includes("REFUND_REQUESTED");
  const shouldLoad = LOAD_STATUSES.has(order.status)
    || moneyValue(order.refundedAmount) > 0
    || canRequest;

  const [summary, setSummary] = useState<RefundSummary | null | undefined>(null);
  const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [busy, setBusy] = useState(false);
  const [reason, setReason] = useState("");
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [formError, setFormError] = useState("");
  const [actionError, setActionError] = useState("");
  const blobUrls = useRef<string[]>([]);

  useEffect(() => () => {
    blobUrls.current.forEach((url) => URL.revokeObjectURL(url));
    blobUrls.current = [];
  }, []);

  useEffect(() => {
    if (!shouldLoad) {
      setSummary(null);
      return;
    }
    const needList = LOAD_STATUSES.has(order.status) || moneyValue(order.refundedAmount) > 0;
    if (!needList && canRequest) {
      setSummary(null);
      return;
    }
    let alive = true;
    setFailed(false);
    setSummary(undefined);
    buyerOrderApi.refunds(order.id)
      .then((value) => { if (alive) setSummary(value); })
      .catch(() => { if (alive) setFailed(true); });
    return () => { alive = false; };
  }, [order.id, order.status, order.refundedAmount, order.version, reload, shouldLoad, canRequest]);

  if (!shouldLoad) return null;

  const refundable = summary?.refundableAmount ?? order.refundableAmount;
  const openFile = async (fileUrl: string) => {
    try {
      const blob = await authorizedBlob(fileUrl);
      const url = URL.createObjectURL(blob);
      blobUrls.current.push(url);
      window.open(url, "_blank", "noopener,noreferrer");
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : tp("orders.loadError"));
    }
  };

  const closeDialog = () => {
    if (busy) return;
    setDialog(null);
    setReason("");
    setAmount("");
    setNote("");
    setFormError("");
  };

  const submitRequest = async () => {
    const text = reason.trim();
    if (text.length < 1) {
      setFormError(tp("orders.refund.reasonRequired"));
      return;
    }
    let amountNum: number | undefined;
    if (amount.trim()) {
      amountNum = Number(amount.trim());
      if (!Number.isFinite(amountNum) || amountNum <= 0) {
        setFormError(tp("orders.refund.amountInvalid"));
        return;
      }
      if (refundable != null && amountNum > moneyValue(refundable)) {
        setFormError(tp("orders.refund.amountTooHigh"));
        return;
      }
    }
    setBusy(true);
    setFormError("");
    try {
      await buyerOrderApi.refundRequest(order.id, {
        reason: text,
        ...(amountNum != null ? { amount: amountNum } : {}),
      });
      closeDialog();
      onChanged();
      setReload((n) => n + 1);
    } catch (error) {
      if (error instanceof ApiError && error.code === "ORDER_VERSION_CONFLICT") {
        setFormError(error.message);
        onChanged();
      } else if (error instanceof ApiError) {
        setFormError(error.message);
      } else {
        setFormError(tp("orders.loadError"));
      }
    } finally {
      setBusy(false);
    }
  };

  const confirmReceived = async (refund: RefundView) => {
    setBusy(true);
    setActionError("");
    try {
      await buyerOrderApi.confirmRefundReceived(order.id, refund.id);
      onChanged();
      setReload((n) => n + 1);
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : tp("orders.loadError"));
    } finally {
      setBusy(false);
    }
  };

  const submitDispute = async () => {
    if (!dialog || dialog.kind !== "dispute") return;
    const text = note.trim();
    if (text.length < 1) {
      setFormError(tp("orders.refund.noteRequired"));
      return;
    }
    setBusy(true);
    setFormError("");
    try {
      await buyerOrderApi.disputeRefund(order.id, dialog.refund.id, text);
      closeDialog();
      onChanged();
      setReload((n) => n + 1);
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : tp("orders.loadError"));
    } finally {
      setBusy(false);
    }
  };

  const needsAck = (refund: RefundView) =>
    Boolean(refund.proofFileUrl) && !refund.buyerAckStatus;

  return (
    <article className="checkout-card orders-refund-card">
      <h2>{tp("orders.refund.title")}</h2>
      {canRequest ? (
        <div className="orders-actions">
          <button type="button" onClick={() => { setFormError(""); setDialog({ kind: "request" }); }}>
            {tp("orders.refund.request")}
          </button>
        </div>
      ) : null}

      {summary === undefined && !failed ? (
        <div aria-busy="true">
          <Skeleton height={18} />
          <Skeleton height={48} />
        </div>
      ) : failed ? (
        <div className="orders-card-state" role="alert">
          <p>{tp("orders.refund.loadError")}</p>
          <button type="button" onClick={() => setReload((n) => n + 1)}>{tp("cart.retry")}</button>
        </div>
      ) : summary && summary.refunds.length > 0 ? (
        <>
          <p className="checkout-meta">
            <span>{tp("orders.refund.refundable")}</span>
            <b className="num">{money(summary.refundableAmount)}</b>
          </p>
          <ul className="orders-refund-list">
            {summary.refunds.map((refund) => (
              <li key={refund.id}>
                <div className="orders-payment-attempt-head">
                  <span className="orders-status">{tp(`orders.refund.status.${refund.status}`, { defaultValue: refund.status })}</span>
                  <b className="num">{money(refund.amount)}</b>
                  {refund.requestedAt ? <time dateTime={refund.requestedAt}>{when(refund.requestedAt, localeTag)}</time> : null}
                </div>
                {refund.reason ? <p className="checkout-meta"><span>{tp("orders.refund.reason")}</span><b>{refund.reason}</b></p> : null}
                {refund.rejectReason ? <p className="checkout-meta"><span>{tp("orders.refund.rejectReason")}</span><b>{refund.rejectReason}</b></p> : null}
                {refund.proofReference ? <p className="checkout-meta"><span>{tp("orders.refund.proofRef")}</span><b className="num">{refund.proofReference}</b></p> : null}
                {refund.proofTransferredAt ? <p className="checkout-meta"><span>{tp("orders.refund.proofAt")}</span><b>{when(refund.proofTransferredAt, localeTag)}</b></p> : null}
                {refund.proofFileUrl?.startsWith("/api/") ? (
                  <button type="button" className="checkout-secondary" onClick={() => void openFile(refund.proofFileUrl!)}>
                    {tp("orders.refund.viewProof")}
                  </button>
                ) : null}
                {needsAck(refund) ? (
                  <div className="orders-actions">
                    <button type="button" disabled={busy} onClick={() => void confirmReceived(refund)}>
                      {tp("orders.refund.confirmReceived")}
                    </button>
                    <button
                      type="button"
                      className="is-danger"
                      disabled={busy}
                      onClick={() => { setNote(""); setFormError(""); setDialog({ kind: "dispute", refund }); }}
                    >
                      {tp("orders.refund.dispute")}
                    </button>
                  </div>
                ) : null}
                {refund.buyerAckStatus ? (
                  <p className="checkout-meta">
                    <span>{tp("orders.refund.ackStatus")}</span>
                    <b>{tp(`orders.refund.ack.${refund.buyerAckStatus}`, { defaultValue: refund.buyerAckStatus })}</b>
                  </p>
                ) : null}
              </li>
            ))}
          </ul>
        </>
      ) : canRequest ? (
        <p className="checkout-meta muted">{tp("orders.refund.empty")}</p>
      ) : null}

      {actionError ? <p className="orders-form-error" role="alert">{actionError}</p> : null}

      {dialog?.kind === "request" ? (
        <ConfirmDialog
          title={tp("orders.refund.request")}
          message={tp("orders.refund.requestAsk")}
          confirmLabel={tp("orders.refund.submit")}
          cancelLabel={tp("cart.dismiss")}
          danger
          confirmDisabled={busy}
          onCancel={closeDialog}
          onConfirm={() => void submitRequest()}
        >
          <div className="orders-dialog-fields">
            <label>
              <span>{tp("orders.refund.reason")}</span>
              <textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={1000} required />
            </label>
            <label>
              <span>{tp("orders.refund.amountOptional")}</span>
              <input
                className="num"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                inputMode="decimal"
                placeholder={refundable != null ? money(refundable) : undefined}
              />
            </label>
            {formError ? <p className="orders-form-error" role="alert">{formError}</p> : null}
          </div>
        </ConfirmDialog>
      ) : null}

      {dialog?.kind === "dispute" ? (
        <ConfirmDialog
          title={tp("orders.refund.dispute")}
          message={tp("orders.refund.disputeAsk")}
          confirmLabel={tp("orders.refund.disputeSubmit")}
          cancelLabel={tp("cart.dismiss")}
          danger
          confirmDisabled={busy}
          onCancel={closeDialog}
          onConfirm={() => void submitDispute()}
        >
          <div className="orders-dialog-fields">
            <label>
              <span>{tp("orders.refund.note")}</span>
              <textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={1000} required />
            </label>
            {formError ? <p className="orders-form-error" role="alert">{formError}</p> : null}
          </div>
        </ConfirmDialog>
      ) : null}
    </article>
  );
}
