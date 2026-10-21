import { useEffect, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { AlertTriangle, CheckCircle2, Landmark, QrCode, Store } from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { Skeleton } from "../components/feedback/Skeleton";
import { formatBaht } from "../components/cart/cartFormat";
import { buyerOrderApi, type CheckoutResult, type OrderDetail, type OrderPaymentInfo } from "../services/api";
import "../styles/cart.css";
import "../styles/checkout.css";

type Row = { order: OrderDetail; payment: OrderPaymentInfo | null };
const UUID = /^[0-9a-f-]{36}$/i;

export default function CheckoutSuccessPage() {
  const { user, loading: authLoading } = useAuth();
  const { tp, localeTag } = usePublicTranslation();
  const [params] = useSearchParams();
  const location = useLocation();
  const [rows, setRows] = useState<Row[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0);
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

  const total = (rows ?? []).reduce((sum, row) => sum + Number(row.order.grandTotal || 0), 0);

  return <main className="cart-page checkout-page">
    <div className="cart-heading cart-width"><Link to="/">← {tp("cart.backToShopping")}</Link><ol aria-label={tp("checkout.steps.label")}><li><span>1</span> {tp("checkout.steps.cart")}</li><li><span>2</span> {tp("checkout.steps.checkout")}</li><li aria-current="step"><span>3</span> {tp("checkout.steps.done")}</li></ol></div>
    <section className="cart-width checkout-success">
      {failed ? <div className="cart-empty feedback-error" role="alert"><AlertTriangle/><h2>{tp("checkout.success.loadError")}</h2><button type="button" onClick={() => setReload((value) => value + 1)}>{tp("cart.retry")}</button></div>
      : rows == null ? <div className="checkout-card" aria-busy="true"><Skeleton height={28} width="50%"/><Skeleton height={18}/><Skeleton height={18}/><Skeleton height={120}/></div>
      : !rows.length ? <div className="cart-empty"><AlertTriangle/><h2>{tp("checkout.success.missing")}</h2><Link to="/cart">{tp("checkout.backToCart")}</Link></div>
      : <>
        <header className="checkout-success-head"><CheckCircle2/><div><h1>{tp("checkout.success.title")}</h1><p>{tp("checkout.success.subtitle", { count: rows.length })}</p></div><b className="num">{money(total)}</b></header>
        {rows.map(({ order, payment }) => <article key={order.id} className="checkout-card checkout-order">
          <header><span className="cart-shop-head"><Store size={16}/>{order.shopName}</span><span className="checkout-order-number num">{tp("checkout.success.orderNumber")} {order.orderNumber}</span></header>
          <p className="checkout-meta"><span>{tp("checkout.success.amountDue")}</span><b className="num">{money(payment?.amountDue ?? order.grandTotal)}</b></p>
          <p className="checkout-meta"><span>{tp("checkout.success.status")}</span><b>{tp(`checkout.status.${order.status}`, { defaultValue: order.status })}</b></p>
          {payment?.accounts?.length ? <ul className="checkout-accounts">
            {payment.accounts.filter((account) => account.active).map((account, index) => <li key={`${account.provider}-${index}`}>
              {account.provider === "PROMPTPAY" ? <QrCode size={16}/> : <Landmark size={16}/>}
              <span>{account.displayName || account.bankName || tp(`checkout.provider.${account.provider}`, { defaultValue: account.provider })}</span>
              <b className="num">{account.promptPayId || account.accountNumber}</b>
              {account.accountName ? <small>{account.accountName}</small> : null}
            </li>)}
          </ul> : <p className="checkout-meta muted">{tp("checkout.success.noAccounts")}</p>}
          <p className="checkout-meta"><Link to={`/account/orders/${order.id}`}>{tp("orders.viewDetail")}</Link></p>
        </article>)}
        <p className="checkout-success-note">{tp("checkout.success.payNote")}</p>
        <div className="checkout-success-actions">
          <Link className="checkout-secondary" to="/account/orders">{tp("orders.title")}</Link>
          <Link className="checkout-secondary" to="/">{tp("cart.backToShopping")}</Link>
        </div>
      </>}
    </section>
  </main>;
}
