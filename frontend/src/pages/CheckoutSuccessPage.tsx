import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { AlertTriangle, CheckCircle2, Copy, Landmark, MapPin, QrCode, Store, Truck, Upload } from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { Skeleton } from "../components/feedback/Skeleton";
import { formatBaht } from "../components/cart/cartFormat";
import { buyerOrderApi, type CheckoutResult, type OrderDetail, type OrderPaymentInfo } from "../services/api";
import { estimateDelivery, type DeliveryEstimate } from "../services/checkoutExtensions";
import "../styles/cart.css";
import "../styles/checkout.css";

type Row = { order: OrderDetail; payment: OrderPaymentInfo | null };
const UUID = /^[0-9a-f-]{36}$/i;

function formatEtaDate(isoDate: string, localeTag: string) {
  const parsed = new Date(`${isoDate}T12:00:00`);
  if (Number.isNaN(parsed.getTime())) return isoDate;
  return parsed.toLocaleDateString(localeTag, { day: "numeric", month: "short", year: "numeric" });
}

function OrderDeliveryEta({ order }: { order: OrderDetail }) {
  const { tp, localeTag } = usePublicTranslation();
  const [eta, setEta] = useState<DeliveryEstimate | null>(null);

  useEffect(() => {
    let alive = true;
    setEta(null);
    void estimateDelivery(order).then((result) => {
      if (alive) setEta(result);
    });
    return () => { alive = false; };
  }, [order.id, order.placedAt, order.shipping?.carrierCode, order.shipment?.carrierCode]);

  if (!eta) {
    return (
      <p className="checkout-meta checkout-success-eta" aria-busy="true">
        <span>{tp("checkout.success.eta")}</span>
        <Skeleton height={16} width="40%" />
      </p>
    );
  }

  const range = eta.minDate === eta.maxDate
    ? formatEtaDate(eta.minDate, localeTag)
    : `${formatEtaDate(eta.minDate, localeTag)} – ${formatEtaDate(eta.maxDate, localeTag)}`;

  return (
    <p className="checkout-meta checkout-success-eta">
      <span>{tp("checkout.success.eta")}</span>
      <b>
        <span className="num">{range}</span>
        {eta.source === "LOCAL" ? (
          <small className="checkout-success-eta-local">{tp("checkout.success.etaLocal")}</small>
        ) : null}
      </b>
    </p>
  );
}

export default function CheckoutSuccessPage() {
  const { user, loading: authLoading } = useAuth();
  const { tp, localeTag } = usePublicTranslation();
  const [params] = useSearchParams();
  const location = useLocation();
  const [rows, setRows] = useState<Row[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0);
  const [copied, setCopied] = useState("");
  const copyTimer = useRef<number | null>(null);
  const ids = (params.get("orders") ?? "").split(",").filter((id) => UUID.test(id)).slice(0, 20);
  const idKey = ids.join(",");
  const money = (value: unknown) => formatBaht(value as number, localeTag);

  useEffect(() => {
    if (authLoading || !user) return;
    if (!ids.length) { setRows([]); return; }
    let alive = true;
    setFailed(false);
    const passed = (location.state as { result?: CheckoutResult } | null)?.result?.orders ?? [];
    Promise.all(ids.map(async (id) => {
      const order = passed.find((item) => item.id === id) ?? await buyerOrderApi.get(id);
      const payment = await buyerOrderApi.paymentInfo(id).catch(() => null);
      return { order, payment };
    }))
      .then((loaded) => { if (alive) setRows(loaded); })
      .catch(() => { if (alive) setFailed(true); });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authLoading, user?.id, idKey, reload]);

  useEffect(() => () => {
    if (copyTimer.current != null) window.clearTimeout(copyTimer.current);
  }, []);

  const copyText = async (key: string, value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(key);
      if (copyTimer.current != null) window.clearTimeout(copyTimer.current);
      copyTimer.current = window.setTimeout(() => setCopied(""), 1600);
    } catch { /* clipboard unavailable */ }
  };

  const total = (rows ?? []).reduce((sum, row) => sum + Number(row.order.grandTotal || 0), 0);
  const allCod = (rows ?? []).length > 0 && (rows ?? []).every(({ order, payment }) =>
    (payment?.paymentMethod ?? order.paymentMethod) === "COD");
  const primaryPay = (rows ?? []).find(({ order, payment }) =>
    (payment?.paymentMethod ?? order.paymentMethod) !== "COD" && (payment?.canSubmitSlip || order.status === "PENDING_PAYMENT"));

  return <main className="cart-page checkout-page">
    <div className="cart-heading cart-width"><Link to="/">← {tp("cart.backToShopping")}</Link><ol aria-label={tp("checkout.steps.label")}><li><span>1</span> {tp("checkout.steps.cart")}</li><li><span>2</span> {tp("checkout.steps.checkout")}</li><li aria-current="step"><span>3</span> {tp("checkout.steps.done")}</li></ol></div>
    <section className="cart-width checkout-success">
      {failed ? <div className="cart-empty feedback-error" role="alert"><AlertTriangle/><h2>{tp("checkout.success.loadError")}</h2><button type="button" onClick={() => setReload((value) => value + 1)}>{tp("cart.retry")}</button></div>
      : rows == null ? <div className="checkout-card" aria-busy="true"><Skeleton height={28} width="50%"/><Skeleton height={18}/><Skeleton height={18}/><Skeleton height={120}/></div>
      : !rows.length ? <div className="cart-empty"><AlertTriangle/><h2>{tp("checkout.success.missing")}</h2><Link to="/cart">{tp("checkout.backToCart")}</Link></div>
      : <>
        <div className="checkout-success-head"><CheckCircle2/><div><h1>{tp("checkout.success.title")}</h1><p>{tp("checkout.success.subtitle", { count: rows.length })}</p></div><b className="num">{money(total)}</b></div>

        {!allCod && primaryPay ? (
          <div className="checkout-success-cta-banner">
            <div>
              <b>{tp("checkout.success.nextPayTitle")}</b>
              <p>{tp("checkout.success.nextPayBody")}</p>
            </div>
            <Link className="checkout-success-cta" to={`/account/orders/${primaryPay.order.id}`}>
              <Upload size={16} /> {tp("checkout.success.payAndSlip")}
            </Link>
          </div>
        ) : null}

        {rows.map(({ order, payment }) => {
          const method = payment?.paymentMethod ?? order.paymentMethod;
          const isCod = method === "COD";
          const codFee = payment?.codFee ?? order.codFee;
          const accounts = (payment?.accounts ?? []).filter((account) => account.active);
          const canSlip = Boolean(payment?.canSubmitSlip);
          const address = order.shippingAddress;
          const addressLine = address
            ? [address.addressLine1, address.addressLine2, address.subdistrict, address.district, address.province]
              .filter(Boolean).join(", ")
            : "";
          return <article key={order.id} className="checkout-card checkout-order">
            <div className="checkout-order-top">
              <span className="cart-shop-head"><Store size={16}/>{order.shopName}</span>
              <span className="checkout-order-number-row">
                <span className="checkout-order-number num">{tp("checkout.success.orderNumber")} {order.orderNumber}</span>
                <button
                  type="button"
                  className="checkout-copy-btn"
                  onClick={() => void copyText(`order-${order.id}`, order.orderNumber)}
                >
                  <Copy size={14} /> {copied === `order-${order.id}` ? tp("checkout.success.copied") : tp("checkout.success.copyOrder")}
                </button>
              </span>
            </div>
            <p className="checkout-meta"><span>{tp("checkout.success.amountDue")}</span><b className="num checkout-success-amount">{money(payment?.amountDue ?? order.grandTotal)}</b></p>
            <p className="checkout-meta"><span>{tp("checkout.success.status")}</span><b>{tp(`checkout.status.${order.status}`, { defaultValue: order.status })}</b></p>

            {address ? (
              <div className="checkout-success-ship">
                <p className="checkout-meta">
                  <MapPin size={14} aria-hidden="true" />
                  <span>{tp("checkout.success.shipTo")}</span>
                  <b>{address.recipientName} · <span className="num">{address.phone}</span></b>
                </p>
                <p className="checkout-meta muted">
                  <span>{addressLine}{address.postalCode ? <> · <span className="num">{address.postalCode}</span></> : null}</span>
                </p>
                {order.shipping?.methodName ? (
                  <p className="checkout-meta">
                    <Truck size={14} aria-hidden="true" />
                    <span>{tp("checkout.success.shippingMethod")}</span>
                    <b>{order.shipping.methodName}</b>
                  </p>
                ) : null}
              </div>
            ) : null}

            <OrderDeliveryEta order={order} />

            {isCod ? (
              <p className="checkout-meta">
                <span>{tp("checkout.success.codPay")}</span>
                <b className="num">
                  {tp("checkout.success.codAmount", {
                    total: formatBaht(payment?.amountDue ?? order.grandTotal, localeTag),
                    fee: formatBaht(codFee, localeTag),
                  })}
                </b>
              </p>
            ) : accounts.length ? <ul className="checkout-accounts">
              {accounts.map((account, index) => {
                const value = account.promptPayId || account.accountNumber || "";
                const copyKey = `acc-${order.id}-${index}`;
                return <li key={`${account.provider}-${index}`}>
                  {account.provider === "PROMPTPAY" ? <QrCode size={16}/> : <Landmark size={16}/>}
                  <span>{account.displayName || account.bankName || tp(`checkout.provider.${account.provider}`, { defaultValue: account.provider })}</span>
                  <b className="num">{value}</b>
                  {account.accountName ? <small>{account.accountName}</small> : null}
                  {value ? (
                    <button type="button" className="checkout-copy-btn" onClick={() => void copyText(copyKey, value)}>
                      <Copy size={14} /> {copied === copyKey ? tp("checkout.success.copied") : tp("checkout.success.copyAccount")}
                    </button>
                  ) : null}
                </li>;
              })}
            </ul> : <p className="checkout-meta muted">{tp("checkout.success.noAccounts")}</p>}

            <div className="checkout-success-order-actions">
              {canSlip || (!isCod && order.status === "PENDING_PAYMENT") ? (
                <Link className="checkout-success-cta" to={`/account/orders/${order.id}`}>
                  <Upload size={16} /> {tp("checkout.success.payAndSlip")}
                </Link>
              ) : (
                <Link className="checkout-secondary" to={`/account/orders/${order.id}`}>{tp("orders.viewDetail")}</Link>
              )}
            </div>
          </article>;
        })}
        {!allCod ? <p className="checkout-success-note">{tp("checkout.success.payNote")}</p> : <p className="checkout-success-note">{tp("checkout.success.codNote")}</p>}
        <div className="checkout-success-actions">
          <Link className="checkout-secondary" to="/account/orders">{tp("orders.title")}</Link>
          <Link className="checkout-secondary" to="/">{tp("cart.backToShopping")}</Link>
        </div>
      </>}
    </section>
  </main>;
}
