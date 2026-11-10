import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import ConfigurationSummary from "../components/cart/ConfigurationSummary";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import {
  CheckCircle2, ChevronRight, Copy, Landmark, MapPin,
  Package, QrCode, SearchX, Store, Truck, Wallet,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { useCart } from "../contexts/CartContext";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import EmptyState from "../components/feedback/EmptyState";
import { Skeleton } from "../components/feedback/Skeleton";
import { formatBaht } from "../components/cart/cartFormat";
import {
  ApiError,
  authorizedBlob,
  buyerOrderApi,
  mediaUrl,
  moneyValue,
  type OrderDetail,
  type OrderPaymentInfo,
  type OrderTimelineEntry,
} from "../services/api";
import OrderShipmentCard, { shouldShowShipmentCard } from "../components/orders/OrderShipmentCard";
import OrderPaymentsCard from "../components/orders/OrderPaymentsCard";
import OrderRefundCard from "../components/orders/OrderRefundCard";
import "../styles/cart.css";
import "../styles/checkout.css";
import "../styles/orders.css";
import "../styles/shop-page.css";

const UUID = /^[0-9a-f-]{36}$/i;
const MAX_SLIP = 5 * 1024 * 1024;
const SLIP_ACCEPT = "image/jpeg,image/png,image/webp,application/pdf";

function when(value: string | null | undefined, localeTag: string) {
  if (!value) return "";
  return new Date(value).toLocaleString(localeTag, { dateStyle: "medium", timeStyle: "short" });
}

function statusTone(status: string | null | undefined) {
  switch (status) {
    case "PENDING_PAYMENT": return "pay";
    case "PAID_AWAITING_MERCHANT_CONFIRM":
    case "MERCHANT_CONFIRMED":
    case "READY_TO_PACK":
    case "PACKING":
    case "PACKED": return "prepare";
    case "DISPATCHED": return "ship";
    case "DELIVERED": return "deliver";
    case "COMPLETED": return "done";
    case "CANCELLED": return "cancel";
    case "REFUND_REQUESTED":
    case "REFUNDED": return "refund";
    default: return "all";
  }
}

function statusHint(
  order: OrderDetail,
  tp: (key: string, o?: Record<string, unknown>) => string,
) {
  if (order.status === "PENDING_PAYMENT") {
    return order.paymentMethod === "COD"
      ? tp("orders.detail.hintCod")
      : tp("orders.detail.hintPay");
  }
  if (order.status === "DISPATCHED") return tp("orders.detail.hintShip");
  if (order.status === "DELIVERED") return tp("orders.detail.hintDelivered");
  if (order.status === "COMPLETED") return tp("orders.detail.hintDone");
  if (order.status === "CANCELLED") return tp("orders.detail.hintCancel");
  if (order.status === "REFUND_REQUESTED" || order.status === "REFUNDED") {
    return tp("orders.detail.hintRefund");
  }
  return tp("orders.detail.hintPrepare");
}

export default function MyOrderDetailPage() {
  const { orderId = "" } = useParams();
  const { user, loading: authLoading } = useAuth();
  const cart = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const { tp, localeTag } = usePublicTranslation();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [payment, setPayment] = useState<OrderPaymentInfo | null>(null);
  const [timeline, setTimeline] = useState<OrderTimelineEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0);
  const [copied, setCopied] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");
  const [cancelling, setCancelling] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [actionError, setActionError] = useState("");
  const [slipBusy, setSlipBusy] = useState(false);
  const [slipError, setSlipError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [slipFile, setSlipFile] = useState<File | null>(null);
  const [slipAmount, setSlipAmount] = useState("");
  const [slipAt, setSlipAt] = useState("");
  const [slipRef, setSlipRef] = useState("");
  const [slipBank, setSlipBank] = useState("");
  const [slipProvider, setSlipProvider] = useState("BANK_TRANSFER");
  const payRef = useRef<HTMLElement | null>(null);
  const blobUrls = useRef<string[]>([]);
  const money = (value: unknown) => formatBaht(value as number, localeTag);
  const idOk = UUID.test(orderId);

  useEffect(() => {
    if (authLoading) return;
    if (!user) {
      navigate(`/login?next=${encodeURIComponent(location.pathname + location.search)}`, { replace: true });
    }
  }, [authLoading, user, navigate, location.pathname, location.search]);

  useEffect(() => () => {
    blobUrls.current.forEach((url) => URL.revokeObjectURL(url));
    blobUrls.current = [];
  }, []);

  useEffect(() => {
    if (authLoading || !user || cart.access === "blocked" || cart.access === "loading" || !idOk) {
      if (!idOk) { setMissing(true); setLoading(false); }
      return;
    }
    let alive = true;
    setLoading(true);
    setFailed(false);
    setMissing(false);
    setActionError("");
    buyerOrderApi.get(orderId)
      .then(async (detail) => {
        if (!alive) return;
        setOrder(detail);
        const needPay = detail.status === "PENDING_PAYMENT" || detail.paymentStatus !== "UNPAID";
        const [tl, pay] = await Promise.all([
          buyerOrderApi.timeline(orderId).catch(() => [] as OrderTimelineEntry[]),
          needPay ? buyerOrderApi.paymentInfo(orderId).catch(() => null) : Promise.resolve(null),
        ]);
        if (!alive) return;
        setTimeline(tl);
        setPayment(pay);
        if (pay?.amountDue != null) setSlipAmount(String(moneyValue(pay.amountDue)));
      })
      .catch((error) => {
        if (!alive) return;
        if (error instanceof ApiError && (error.status === 404 || error.code === "ORDER_NOT_FOUND")) setMissing(true);
        else setFailed(true);
        setOrder(null);
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [authLoading, user?.id, orderId, idOk, cart.access, reload]);

  const transitions = order?.allowedTransitions ?? [];
  const canCancel = transitions.includes("CANCELLED");
  const canComplete = transitions.includes("COMPLETED");
  const needPayNow = order?.status === "PENDING_PAYMENT" && order.paymentMethod !== "COD";
  const showShipment = order ? shouldShowShipmentCard(order) : false;
  const tone = statusTone(order?.status);
  const proofs = useMemo(
    () => (payment?.payments ?? []).flatMap((row) => (row.proofs ?? []).map((proof) => ({ ...proof, provider: row.provider }))),
    [payment],
  );

  const openSlip = async (fileUrl: string) => {
    try {
      const blob = await authorizedBlob(fileUrl);
      const url = URL.createObjectURL(blob);
      blobUrls.current.push(url);
      window.open(url, "_blank", "noopener,noreferrer");
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : tp("orders.loadError"));
    }
  };

  const copyText = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch { /* clipboard unavailable */ }
  };

  const refreshAfterAction = () => setReload((n) => n + 1);

  const onCancel = async () => {
    const reason = cancelReason.trim();
    if (reason.length < 1 || reason.length > 500) {
      setActionError(tp("orders.cancelReason"));
      return;
    }
    setCancelling(true);
    setActionError("");
    try {
      await buyerOrderApi.cancel(orderId, reason);
      setCancelOpen(false);
      setCancelReason("");
      refreshAfterAction();
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : tp("orders.loadError"));
    } finally {
      setCancelling(false);
    }
  };

  const onConfirmReceived = async () => {
    if (!window.confirm(tp("orders.confirmReceivedAsk"))) return;
    setConfirming(true);
    setActionError("");
    try {
      await buyerOrderApi.confirmReceived(orderId);
      refreshAfterAction();
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : tp("orders.loadError"));
    } finally {
      setConfirming(false);
    }
  };

  const onSubmitSlip = async (event: FormEvent) => {
    event.preventDefault();
    if (slipBusy || !payment?.canSubmitSlip) return;
    setSlipError("");
    setFieldErrors({});
    if (!slipFile) {
      setFieldErrors({ file: tp("checkout.errors.required") });
      return;
    }
    if (slipFile.size > MAX_SLIP) {
      setSlipError(tp("orders.slipForm.tooLarge"));
      return;
    }
    const form = new FormData();
    form.append("file", slipFile);
    form.append("amount", slipAmount.trim());
    form.append("transferredAt", slipAt);
    form.append("reference", slipRef.trim());
    if (slipBank.trim()) form.append("bankName", slipBank.trim());
    form.append("provider", slipProvider);
    setSlipBusy(true);
    try {
      await buyerOrderApi.submitSlip(orderId, form);
      setSlipFile(null);
      setSlipRef("");
      setSlipBank("");
      refreshAfterAction();
    } catch (error) {
      if (error instanceof ApiError && (error.code === "VALIDATION_ERROR" || error.code === "VALIDATION_FAILED")) {
        const mapped: Record<string, string> = {};
        error.fieldErrors.forEach((field) => { mapped[field.field] = field.message; });
        setFieldErrors(mapped);
        setSlipError(error.message);
      } else if (error instanceof ApiError) {
        setSlipError(error.message);
      } else {
        setSlipError(tp("orders.loadError"));
      }
    } finally {
      setSlipBusy(false);
    }
  };

  if (cart.access === "blocked") {
    return (
      <main className="cart-page orders-page">
        <div className="cart-width">
          <EmptyState icon={Package} title={tp("orders.blocked")} action={{ label: tp("cart.backToShopping"), to: "/" }} />
        </div>
      </main>
    );
  }

  if (missing) {
    return (
      <main className="cart-page orders-page">
        <div className="cart-width">
          <EmptyState icon={SearchX} title={tp("orders.notFound")} action={{ label: tp("orders.back"), to: "/account/orders" }} />
        </div>
      </main>
    );
  }

  const address = order?.shippingAddress;
  const addressLine = address
    ? [address.addressLine1, address.addressLine2, address.subdistrict, address.district, address.province, address.postalCode]
      .filter(Boolean).join(", ")
    : "";

  return (
    <main className="cart-page orders-page orders-detail">
      <div className="cart-width orders-detail-shell">
        <Link className="orders-detail-back" to="/account/orders">
          ← {tp("orders.back")}
        </Link>

        {loading || authLoading || cart.access === "loading" ? (
          <div className="orders-detail-stack" aria-busy="true">
            <div className="orders-detail-card">
              <Skeleton height={28} width="40%" />
              <Skeleton height={18} />
              <Skeleton height={56} />
            </div>
            <div className="orders-detail-card">
              <Skeleton height={120} />
            </div>
          </div>
        ) : failed || !order ? (
          <EmptyState
            variant="error"
            title={tp("orders.loadError")}
            action={{ label: tp("cart.retry"), onClick: () => setReload((n) => n + 1) }}
          />
        ) : (
          <div className="orders-detail-stack">
            {/* 1. Status + next action */}
            <section className={`orders-detail-hero is-${tone}`}>
              <div className="orders-detail-hero-top">
                <div className="orders-detail-shop">
                  <Store size={16} aria-hidden="true" />
                  <span>{order.shopName}</span>
                </div>
                <span className={`orders-status is-${tone}`}>
                  {tp(`checkout.status.${order.status}`, { defaultValue: order.status })}
                </span>
              </div>

              <div className="orders-detail-hero-main">
                <div>
                  <p className="orders-detail-label">{tp("orders.detail.orderNo")}</p>
                  <p className="orders-detail-number num">
                    {order.orderNumber}
                    <button
                      type="button"
                      className="orders-detail-icon-btn"
                      aria-label={tp("orders.copy")}
                      onClick={() => void copyText(order.orderNumber)}
                    >
                      <Copy size={14} />
                      {copied ? tp("orders.copied") : null}
                    </button>
                  </p>
                  {order.placedAt ? (
                    <p className="orders-detail-sub">{tp("orders.placedAt")}: {when(order.placedAt, localeTag)}</p>
                  ) : null}
                </div>
                <div className="orders-detail-hero-total">
                  <small>{tp("orders.total")}</small>
                  <b className="num">{money(order.grandTotal)}</b>
                </div>
              </div>

              <p className="orders-detail-hint">{statusHint(order, tp)}</p>

              {(canComplete || needPayNow || canCancel) ? (
                <div className="orders-detail-cta">
                  {needPayNow ? (
                    <button
                      type="button"
                      className="orders-detail-cta-primary is-pay"
                      onClick={() => payRef.current?.scrollIntoView({ behavior: "smooth", block: "start" })}
                    >
                      <Wallet size={16} /> {tp("orders.list.actionPay")}
                    </button>
                  ) : null}
                  {canComplete ? (
                    <button
                      type="button"
                      className="orders-detail-cta-primary"
                      disabled={confirming}
                      onClick={() => void onConfirmReceived()}
                    >
                      <CheckCircle2 size={16} /> {tp("orders.confirmReceived")}
                    </button>
                  ) : null}
                  {canCancel ? (
                    <button
                      type="button"
                      className="orders-detail-cta-ghost"
                      disabled={cancelling}
                      onClick={() => setCancelOpen(true)}
                    >
                      {tp("orders.cancel")}
                    </button>
                  ) : null}
                </div>
              ) : null}

              {cancelOpen ? (
                <div className="orders-cancel-box">
                  <label>
                    <span>{tp("orders.cancelReason")}</span>
                    <textarea
                      value={cancelReason}
                      onChange={(event) => setCancelReason(event.target.value)}
                      maxLength={500}
                      required
                    />
                  </label>
                  <div className="orders-actions">
                    <button type="button" disabled={cancelling} onClick={() => void onCancel()}>
                      {cancelling ? tp("orders.cancelling") : tp("orders.cancelConfirm")}
                    </button>
                    <button type="button" className="is-danger" disabled={cancelling} onClick={() => setCancelOpen(false)}>
                      {tp("cart.dismiss")}
                    </button>
                  </div>
                </div>
              ) : null}
              {actionError ? <p className="orders-form-error" role="alert">{actionError}</p> : null}
            </section>

            {/* 2. Pay now (highest urgency when unpaid) */}
            {payment && (needPayNow || payment.canSubmitSlip || proofs.length > 0 || order.status === "PENDING_PAYMENT") ? (
              <section
                ref={payRef}
                className={`orders-detail-card orders-detail-pay ${needPayNow ? "is-urgent" : ""}`}
              >
                <div className="orders-detail-card-head">
                  <Wallet size={18} aria-hidden="true" />
                  <div>
                    <h2>{tp("orders.payment")}</h2>
                    <p>
                      {order.paymentMethod === "COD"
                        ? tp("orders.paymentsHistory.cod")
                        : tp("orders.paymentsHistory.bankTransfer")}
                      {order.paymentStatus
                        ? ` · ${tp(`orders.list.paymentStatus.${order.paymentStatus}`, { defaultValue: order.paymentStatus })}`
                        : ""}
                    </p>
                  </div>
                </div>

                <div className="orders-detail-due">
                  <span>{tp("orders.amountDue")}</span>
                  <b className="num">{money(payment.amountDue)}</b>
                </div>

                {payment.accounts?.length ? (
                  <ul className="checkout-accounts orders-detail-accounts">
                    {payment.accounts.filter((account) => account.active).map((account, index) => (
                      <li key={`${account.provider}-${index}`}>
                        {account.provider === "PROMPTPAY" ? <QrCode size={16} /> : <Landmark size={16} />}
                        <span>{account.displayName || account.bankName || tp(`checkout.provider.${account.provider}`, { defaultValue: account.provider })}</span>
                        <b className="num">{account.promptPayId || account.accountNumber}</b>
                        {account.accountName ? <small>{account.accountName}</small> : null}
                      </li>
                    ))}
                  </ul>
                ) : needPayNow ? (
                  <p className="checkout-meta muted">{tp("checkout.success.noAccounts")}</p>
                ) : null}

                {proofs.length > 0 ? (
                  <div className="orders-detail-block">
                    <h3>{tp("orders.slips")}</h3>
                    <ul className="orders-proofs">
                      {proofs.map((proof) => (
                        <li key={proof.id}>
                          <span className="orders-status">{proof.status}</span>
                          {proof.referenceNo ? <span className="num">{proof.referenceNo}</span> : null}
                          {proof.amountDeclared != null ? <b className="num">{money(proof.amountDeclared)}</b> : null}
                          {proof.rejectReason ? <small>{proof.rejectReason}</small> : null}
                          {proof.fileUrl?.startsWith("/api/") ? (
                            <button type="button" className="checkout-secondary" onClick={() => void openSlip(proof.fileUrl!)}>
                              {tp("orders.viewSlip")}
                            </button>
                          ) : null}
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}

                {payment.canSubmitSlip ? (
                  <form className="orders-slip-form" onSubmit={(event) => void onSubmitSlip(event)}>
                    <h3>{tp("orders.slipForm.title")}</h3>
                    <label className={fieldErrors.file ? "has-error" : ""}>
                      <span>{tp("orders.slipForm.file")}</span>
                      <input
                        type="file"
                        accept={SLIP_ACCEPT}
                        onChange={(event) => {
                          const file = event.target.files?.[0] ?? null;
                          setSlipFile(file);
                          if (file && file.size > MAX_SLIP) setSlipError(tp("orders.slipForm.tooLarge"));
                          else setSlipError("");
                        }}
                      />
                      {fieldErrors.file ? <small role="alert">{fieldErrors.file}</small> : null}
                    </label>
                    <label className={fieldErrors.amount ? "has-error" : ""}>
                      <span>{tp("orders.slipForm.amount")}</span>
                      <input className="num" value={slipAmount} onChange={(event) => setSlipAmount(event.target.value)} inputMode="decimal" required />
                      {fieldErrors.amount ? <small role="alert">{fieldErrors.amount}</small> : null}
                    </label>
                    <label className={fieldErrors.transferredAt ? "has-error" : ""}>
                      <span>{tp("orders.slipForm.transferredAt")}</span>
                      <input type="datetime-local" value={slipAt} onChange={(event) => setSlipAt(event.target.value)} required />
                      {fieldErrors.transferredAt ? <small role="alert">{fieldErrors.transferredAt}</small> : null}
                    </label>
                    <label className={fieldErrors.reference ? "has-error" : ""}>
                      <span>{tp("orders.slipForm.reference")}</span>
                      <input className="num" value={slipRef} onChange={(event) => setSlipRef(event.target.value)} minLength={4} maxLength={120} required />
                      {fieldErrors.reference ? <small role="alert">{fieldErrors.reference}</small> : null}
                    </label>
                    <label className={fieldErrors.bankName ? "has-error" : ""}>
                      <span>{tp("orders.slipForm.bankName")}</span>
                      <input value={slipBank} onChange={(event) => setSlipBank(event.target.value)} />
                      {fieldErrors.bankName ? <small role="alert">{fieldErrors.bankName}</small> : null}
                    </label>
                    <label className={fieldErrors.provider ? "has-error" : ""}>
                      <span>{tp("orders.slipForm.provider")}</span>
                      <select value={slipProvider} onChange={(event) => setSlipProvider(event.target.value)}>
                        <option value="BANK_TRANSFER">{tp("checkout.provider.BANK_TRANSFER")}</option>
                        <option value="PROMPTPAY">{tp("checkout.provider.PROMPTPAY")}</option>
                      </select>
                      {fieldErrors.provider ? <small role="alert">{fieldErrors.provider}</small> : null}
                    </label>
                    {slipError ? <p className="orders-form-error" role="alert">{slipError}</p> : null}
                    <button type="submit" disabled={slipBusy}>
                      {slipBusy ? tp("orders.slipForm.submitting") : tp("orders.slipForm.submit")}
                    </button>
                  </form>
                ) : null}
              </section>
            ) : null}

            {/* 3. Tracking */}
            {showShipment ? <OrderShipmentCard order={order} /> : null}

            {/* 4. Products + money summary */}
            <section className="orders-detail-card">
              <div className="orders-detail-card-head">
                <Package size={18} aria-hidden="true" />
                <div>
                  <h2>{tp("orders.products")}</h2>
                  <p>{tp("orders.items", { count: order.items?.reduce((n, i) => n + i.quantity, 0) ?? 0 })}</p>
                </div>
              </div>
              <div className="orders-detail-lines">
                {(order.items ?? []).map((item) => (
                  <div key={item.id} className="orders-line">
                    {item.imageUrl
                      ? <img src={mediaUrl(item.imageUrl)} alt="" width={56} height={56} />
                      : <span className="orders-line-ph" aria-hidden="true" />}
                    <div>
                      <h3>{item.productName}</h3>
                      {item.optionLabel ? <small>{item.optionLabel}</small> : null}
                      <ConfigurationSummary configuration={item.configuration} localeTag={localeTag} />
                      <small className="num">{money(item.unitPrice)} × {item.quantity}</small>
                    </div>
                    <div className="orders-line-total">
                      <b className="num">{money(item.lineTotal)}</b>
                    </div>
                  </div>
                ))}
              </div>
              <div className="orders-detail-totals">
                <p><span>{tp("orders.subtotal")}</span><b className="num">{money(order.subtotal)}</b></p>
                {moneyValue(order.discountTotal) > 0 ? (
                  <p>
                    <span>{tp("orders.discount")}{order.couponCode ? ` (${order.couponCode})` : ""}</span>
                    <b className="num">−{money(order.discountTotal)}</b>
                  </p>
                ) : null}
                <p><span>{tp("orders.shippingFee")}</span><b className="num">{money(order.shippingFee)}</b></p>
                {order.codFee != null && moneyValue(order.codFee) > 0 ? (
                  <p><span>{tp("checkout.payment.codFee")}</span><b className="num">{money(order.codFee)}</b></p>
                ) : null}
                <p className="is-grand"><span>{tp("orders.total")}</span><b className="num">{money(order.grandTotal)}</b></p>
              </div>
            </section>

            {/* 5. Address + shipping method */}
            {(address || order.buyerNote || order.shipping) ? (
              <section className="orders-detail-card">
                <div className="orders-detail-card-head">
                  <MapPin size={18} aria-hidden="true" />
                  <div>
                    <h2>{tp("orders.address")}</h2>
                    {order.shipping?.methodName ? (
                      <p><Truck size={12} /> {order.shipping.methodName}</p>
                    ) : (
                      <p>{tp("checkout.shippingDefault")}</p>
                    )}
                  </div>
                </div>
                {address ? (
                  <div className="orders-detail-address">
                    <p className="orders-detail-recipient">
                      <b>{address.recipientName}</b>
                      <span className="num">{address.phone}</span>
                    </p>
                    <p>{addressLine}</p>
                  </div>
                ) : null}
                {order.buyerNote ? (
                  <p className="orders-detail-note">
                    <span>{tp("orders.note")}</span>
                    <b>{order.buyerNote}</b>
                  </p>
                ) : null}
              </section>
            ) : null}

            {/* 6. Payment history (reference) */}
            <OrderPaymentsCard
              order={order}
              paymentMethod={payment?.paymentMethod ?? order.paymentMethod}
              codFee={payment?.codFee ?? order.codFee}
            />

            {/* 7. Refund */}
            <OrderRefundCard order={order} onChanged={refreshAfterAction} />

            {/* 8. Timeline */}
            {timeline.length > 0 ? (
              <section className="orders-detail-card orders-detail-timeline">
                <div className="orders-detail-card-head">
                  <ChevronRight size={18} aria-hidden="true" />
                  <div>
                    <h2>{tp("orders.timeline")}</h2>
                    <p>{tp("orders.detail.timelineHint")}</p>
                  </div>
                </div>
                <ol className="orders-timeline">
                  {[...timeline].sort((a, b) => b.at.localeCompare(a.at)).map((entry) => (
                    <li key={entry.id}>
                      <time dateTime={entry.at}>{when(entry.at, localeTag)}</time>
                      <b>
                        {entry.event === "ORDER_PLACED"
                          ? tp("orders.placed")
                          : tp(`checkout.status.${entry.toStatus ?? ""}`, { defaultValue: entry.toStatus ?? entry.event })}
                      </b>
                      {entry.reason ? <small>{entry.reason}</small> : null}
                    </li>
                  ))}
                </ol>
              </section>
            ) : null}

            {/* 9. Low-priority meta */}
            {(order.cancelReason || order.termsAcceptedAt) ? (
              <section className="orders-detail-meta">
                {order.cancelReason ? (
                  <p><span>{tp("orders.cancelReason")}</span><b>{order.cancelReason}</b></p>
                ) : null}
                {order.termsAcceptedAt ? (
                  <p><span>{tp("orders.termsAcceptedAt")}</span><b>{when(order.termsAcceptedAt, localeTag)}</b></p>
                ) : null}
              </section>
            ) : null}
          </div>
        )}
      </div>
    </main>
  );
}
