import { useEffect, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Check, ExternalLink, Package, Search, Truck } from "lucide-react";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import { ApiError, mediaUrl, moneyValue, publicOrderApi, type PublicOrderStatus } from "../services/api";
import "../styles/order-tracking.css";

/** AB3: public order tracking (no login). Order number + shipping phone or account e-mail; the API returns a masked view. */
const STEPS = ["payment", "preparing", "shipping", "delivered"] as const;
const PREPARING = ["PAID_AWAITING_MERCHANT_CONFIRM", "MERCHANT_CONFIRMED", "READY_TO_PACK", "PACKING", "PACKED"];
const STOPPED = ["CANCELLED", "REFUNDED", "REFUND_REQUESTED"];

function stepOf(order: PublicOrderStatus) {
  if (order.status === "DELIVERED" || order.status === "COMPLETED") return 4;
  if (order.status === "DISPATCHED") return 2;
  if (PREPARING.includes(order.status)) return 1;
  return 0;
}

function safeLink(url?: string | null) {
  return url && /^https?:\/\//i.test(url) ? url : null;
}

export default function OrderTrackingPage() {
  const { tp, localeTag } = usePublicTranslation();
  useDocumentMeta({ title: tp("tracking.title") });
  const [params] = useSearchParams();
  const [orderNumber, setOrderNumber] = useState(params.get("no") ?? "");
  const [contact, setContact] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [order, setOrder] = useState<PublicOrderStatus | null>(null);

  useEffect(() => { const no = params.get("no"); if (no) setOrderNumber(no); }, [params]);

  const date = (value?: string | null) => value
    ? new Intl.DateTimeFormat(localeTag, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
    : "";
  const money = (value: unknown) => new Intl.NumberFormat(localeTag, { style: "currency", currency: order?.currency || "THB" })
    .format(moneyValue(value as number));

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy) return;
    if (!orderNumber.trim() || !contact.trim()) { setError(tp("tracking.required")); return; }
    setBusy(true);
    setError(null);
    try {
      setOrder(await publicOrderApi.lookup(orderNumber.trim(), contact.trim()));
    } catch (failure) {
      setOrder(null);
      const status = failure instanceof ApiError ? failure.status : 0;
      setError(status === 429 ? tp("tracking.tooMany") : status === 404 ? tp("tracking.notFound") : tp("tracking.failed"));
    } finally {
      setBusy(false);
    }
  };

  const step = order ? stepOf(order) : 0;
  const stopped = order ? STOPPED.includes(order.status) : false;
  const tracking = order?.shipment;
  const link = safeLink(tracking?.trackingUrl);

  return (
    <section className="page-width order-track">
      <h1><Package /> {tp("tracking.title")}</h1>
      <p className="order-track-lead">{tp("tracking.lead")}</p>
      <form className="order-track-form" onSubmit={(event) => void submit(event)}>
        <label>
          <span>{tp("tracking.orderNumber")}</span>
          <input value={orderNumber} onChange={(event) => setOrderNumber(event.target.value)} placeholder="TSF-20260101-ABC123" maxLength={40} autoComplete="off" />
        </label>
        <label>
          <span>{tp("tracking.contact")}</span>
          <input value={contact} onChange={(event) => setContact(event.target.value)} placeholder={tp("tracking.contactPlaceholder")} maxLength={255} autoComplete="off" />
        </label>
        <button type="submit" disabled={busy}><Search size={16} /> {busy ? tp("tracking.searching") : tp("tracking.search")}</button>
        {error ? <p className="order-track-error" role="alert">{error}</p> : null}
      </form>

      {order ? (
        <article className="order-track-result">
          <header>
            <div>
              <b>{order.orderNumber}</b>
              <small>{order.shopName} · {date(order.placedAt)}</small>
            </div>
            <span className="order-track-status">{tp(`tracking.status.${order.status}`, { defaultValue: order.status })}</span>
          </header>

          {stopped ? <p className="order-track-stopped">{tp(`tracking.status.${order.status}`, { defaultValue: order.status })}</p> : (
            <ol className="order-track-steps">
              {STEPS.map((key, index) => (
                <li key={key} className={index < step ? "done" : index === step ? "current" : ""}>
                  <span>{index < step ? <Check size={14} /> : index + 1}</span>
                  <b>{tp(`tracking.steps.${key}`)}</b>
                </li>
              ))}
            </ol>
          )}

          <dl className="order-track-meta">
            <div><dt>{tp("tracking.payment")}</dt><dd>{tp(`tracking.paymentStatus.${order.paymentStatus}`, { defaultValue: order.paymentStatus })}</dd></div>
            <div><dt>{tp("tracking.total")}</dt><dd>{money(order.grandTotal)}</dd></div>
            {order.recipient ? <div><dt>{tp("tracking.recipient")}</dt><dd>{[order.recipient.name, order.recipient.phone, order.recipient.province, order.recipient.postalCode].filter(Boolean).join(" · ")}</dd></div> : null}
          </dl>

          <section className="order-track-shipment">
            <h2><Truck size={18} /> {tp("tracking.shipment")}</h2>
            {tracking ? <>
              <p>
                {tracking.carrierName || tracking.carrierCode || "-"}
                {tracking.trackingNumber ? <> · <b>{tracking.trackingNumber}</b></> : null}
                {link ? <> · <a href={link} target="_blank" rel="noopener noreferrer">{tp("tracking.openCarrier")} <ExternalLink size={12} /></a></> : null}
              </p>
              {tracking.events?.length ? (
                <ul className="order-track-events">
                  {[...tracking.events].sort((a, b) => b.eventAt.localeCompare(a.eventAt)).map((event) => (
                    <li key={event.id}>
                      <time>{date(event.eventAt)}</time>
                      <span>{tp(`tracking.shipmentStatus.${event.status}`, { defaultValue: event.status || event.eventType })}{event.location ? ` · ${event.location}` : ""}</span>
                    </li>
                  ))}
                </ul>
              ) : null}
            </> : <p>{tp("tracking.noShipment")}</p>}
          </section>

          {order.items?.length ? (
            <ul className="order-track-items">
              {order.items.map((item, index) => (
                <li key={index}>
                  {item.imageUrl ? <img src={mediaUrl(item.imageUrl)} alt="" /> : <span className="order-track-noimg" />}
                  <div><b>{item.productName}</b>{item.optionLabel ? <small>{item.optionLabel}</small> : null}</div>
                  <span>×{item.quantity}</span>
                  <span>{money(item.lineTotal)}</span>
                </li>
              ))}
            </ul>
          ) : null}
          <p className="order-track-foot">{tp("tracking.loginHint")} <Link to="/account/orders">{tp("orders.title")}</Link></p>
        </article>
      ) : null}
    </section>
  );
}
