import { useEffect, useRef, useState } from "react";
import { Skeleton } from "../feedback/Skeleton";
import { formatBaht } from "../cart/cartFormat";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import {
  ApiError,
  authorizedBlob,
  buyerOrderApi,
  moneyValue,
  type OrderDetail,
  type PaymentView,
} from "../../services/api";

function when(value: string | null | undefined, localeTag: string) {
  if (!value) return "";
  return new Date(value).toLocaleString(localeTag, { dateStyle: "medium", timeStyle: "short" });
}

type Props = {
  order: OrderDetail;
  paymentMethod?: string | null;
  codFee?: number | string | null;
};

export default function OrderPaymentsCard({ order, paymentMethod, codFee }: Props) {
  const { tp, localeTag } = usePublicTranslation();
  const [payments, setPayments] = useState<PaymentView[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0);
  const [actionError, setActionError] = useState("");
  const blobUrls = useRef<string[]>([]);
  const money = (value: unknown) => formatBaht(value as number, localeTag);
  const method = paymentMethod ?? order.paymentMethod;
  const fee = codFee ?? order.codFee;
  const isCod = method === "COD";

  useEffect(() => () => {
    blobUrls.current.forEach((url) => URL.revokeObjectURL(url));
    blobUrls.current = [];
  }, []);

  useEffect(() => {
    let alive = true;
    setFailed(false);
    setPayments(null);
    buyerOrderApi.payments(order.id)
      .then((rows) => { if (alive) setPayments(rows); })
      .catch(() => { if (alive) setFailed(true); });
    return () => { alive = false; };
  }, [order.id, order.status, order.paymentStatus, reload]);

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

  return (
    <article className="checkout-card orders-payments-card">
      <h2>{tp("orders.paymentsHistory.title")}</h2>
      <p className="checkout-meta">
        <span>{tp("orders.paymentsHistory.method")}</span>
        <b>
          {isCod
            ? tp("orders.paymentsHistory.cod")
            : tp("orders.paymentsHistory.bankTransfer")}
        </b>
      </p>
      {isCod ? (
        <p className="checkout-meta">
          <span>{tp("orders.paymentsHistory.codPay")}</span>
          <b className="num">
            {tp("orders.paymentsHistory.codAmount", {
              total: formatBaht(order.grandTotal, localeTag),
              fee: formatBaht(fee, localeTag),
            })}
          </b>
        </p>
      ) : null}
      {!isCod && fee != null && moneyValue(fee) > 0 ? (
        <p className="checkout-meta">
          <span>{tp("checkout.payment.codFee")}</span>
          <b className="num">{money(fee)}</b>
        </p>
      ) : null}

      {payments == null && !failed ? (
        <div aria-busy="true">
          <Skeleton height={18} />
          <Skeleton height={48} />
        </div>
      ) : failed ? (
        <div className="orders-card-state" role="alert">
          <p>{tp("orders.paymentsHistory.loadError")}</p>
          <button type="button" onClick={() => setReload((n) => n + 1)}>{tp("cart.retry")}</button>
        </div>
      ) : !payments?.length ? (
        <p className="checkout-meta muted">{tp("orders.paymentsHistory.empty")}</p>
      ) : (
        <ul className="orders-payment-attempts">
          {payments.map((row) => (
            <li key={row.id}>
              <div className="orders-payment-attempt-head">
                <span className="orders-status">{row.status}</span>
                <b className="num">{money(row.amount)}</b>
                {row.createdAt ? <time dateTime={row.createdAt}>{when(row.createdAt, localeTag)}</time> : null}
              </div>
              {(row.proofs ?? []).length > 0 ? (
                <ul className="orders-proofs">
                  {(row.proofs ?? []).map((proof) => (
                    <li key={proof.id}>
                      <span className="orders-status">{proof.status}</span>
                      {proof.referenceNo ? <span className="num">{proof.referenceNo}</span> : null}
                      {proof.transferredAt ? <small>{when(proof.transferredAt, localeTag)}</small> : null}
                      {proof.rejectReason ? <small>{proof.rejectReason}</small> : null}
                      {proof.fileUrl?.startsWith("/api/") ? (
                        <button type="button" className="checkout-secondary" onClick={() => void openFile(proof.fileUrl!)}>
                          {tp("orders.viewSlip")}
                        </button>
                      ) : null}
                    </li>
                  ))}
                </ul>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {actionError ? <p className="orders-form-error" role="alert">{actionError}</p> : null}
    </article>
  );
}
