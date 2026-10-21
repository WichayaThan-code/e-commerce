import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { AlertTriangle, Package, Store } from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { useCart } from "../contexts/CartContext";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { Skeleton } from "../components/feedback/Skeleton";
import { formatBaht } from "../components/cart/cartFormat";
import { buyerOrderApi, type OrderPage } from "../services/api";
import "../styles/cart.css";
import "../styles/checkout.css";
import "../styles/orders.css";

const ORDER_STATUSES = [
  "PENDING_PAYMENT", "PAID_AWAITING_MERCHANT_CONFIRM", "MERCHANT_CONFIRMED",
  "READY_TO_PACK", "PACKING", "PACKED", "DISPATCHED", "DELIVERED",
  "COMPLETED", "CANCELLED", "REFUNDED", "REFUND_REQUESTED",
] as const;

export default function MyOrdersPage() {
  const { user, loading: authLoading } = useAuth();
  const cart = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const { tp, localeTag } = usePublicTranslation();
  const [pageData, setPageData] = useState<OrderPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0);

  const rawStatus = params.get("status") ?? "";
  const status = (ORDER_STATUSES as readonly string[]).includes(rawStatus) ? rawStatus : "";
  const page = Math.max(0, Number(params.get("page") || 0) || 0);
  const money = (value: unknown) => formatBaht(value as number, localeTag);

  useEffect(() => {
    if (authLoading) return;
    if (!user) {
      navigate(`/login?next=${encodeURIComponent("/account/orders" + location.search)}`, { replace: true });
    }
  }, [authLoading, user, navigate, location.search]);

  useEffect(() => {
    if (authLoading || !user || cart.access === "blocked" || cart.access === "loading") return;
    let alive = true;
    setLoading(true);
    setFailed(false);
    buyerOrderApi.list({ status: status || undefined, page, size: 10 })
      .then((data) => { if (alive) setPageData(data); })
      .catch(() => { if (alive) { setFailed(true); setPageData(null); } })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [authLoading, user?.id, status, page, cart.access, reload]);

  const tabs = useMemo(() => [
    { value: "", label: tp("orders.all") },
    ...ORDER_STATUSES.map((code) => ({
      value: code,
      label: tp(`checkout.status.${code}`, { defaultValue: code }),
    })),
  ], [tp]);

  const setFilter = (nextStatus: string, nextPage = 0) => {
    const next = new URLSearchParams();
    if (nextStatus) next.set("status", nextStatus);
    if (nextPage > 0) next.set("page", String(nextPage));
    setParams(next, { replace: true });
  };

  if (cart.access === "blocked") {
    return (
      <main className="cart-page orders-page">
        <div className="cart-width">
          <div className="cart-empty" role="status">
            <Package />
            <h2>{tp("orders.blocked")}</h2>
            <Link to="/">{tp("cart.backToShopping")}</Link>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="cart-page orders-page">
      <div className="cart-heading cart-width">
        <h1>{tp("orders.title")}</h1>
      </div>
      <section className="cart-width">
        <div className="orders-tabs" role="tablist" aria-label={tp("orders.title")}>
          {tabs.map((tab) => (
            <button
              key={tab.value || "all"}
              type="button"
              role="tab"
              aria-selected={status === tab.value}
              onClick={() => setFilter(tab.value, 0)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {loading || cart.access === "loading" || authLoading ? (
          <div className="orders-list" aria-busy="true">
            {[0, 1, 2].map((i) => (
              <div key={i} className="checkout-card">
                <Skeleton height={20} width="40%" />
                <Skeleton height={16} width="55%" />
                <Skeleton height={16} width="30%" />
              </div>
            ))}
          </div>
        ) : failed ? (
          <div className="cart-empty feedback-error" role="alert">
            <AlertTriangle />
            <h2>{tp("orders.loadError")}</h2>
            <button type="button" onClick={() => setReload((n) => n + 1)}>{tp("cart.retry")}</button>
          </div>
        ) : !pageData?.items.length ? (
          <div className="cart-empty" role="status">
            <Package />
            <h2>{tp("orders.empty")}</h2>
            <Link to="/">{tp("cart.backToShopping")}</Link>
          </div>
        ) : (
          <>
            <div className="orders-list">
              {pageData.items.map((order) => (
                <Link key={order.id} className="checkout-card orders-card-link" to={`/account/orders/${order.id}`}>
                  <div className="orders-card-top">
                    <span className="cart-shop-head"><Store size={16} />{order.shopName}</span>
                    <span className="orders-status">{tp(`checkout.status.${order.status}`, { defaultValue: order.status })}</span>
                  </div>
                  <p className="checkout-order-number num">{order.orderNumber}</p>
                  {order.placedAt ? (
                    <p className="checkout-meta">
                      <span>{tp("orders.placedAt")}</span>
                      <b>{new Date(order.placedAt).toLocaleString(localeTag, { dateStyle: "medium", timeStyle: "short" })}</b>
                    </p>
                  ) : null}
                  <div className="orders-meta-row">
                    <span>{tp("orders.items", { count: order.totalQuantity ?? 0 })}</span>
                    <b className="num">{money(order.grandTotal)}</b>
                  </div>
                </Link>
              ))}
            </div>
            {pageData.totalPages > 1 ? (
              <div className="orders-pager">
                <button
                  type="button"
                  className="checkout-secondary"
                  disabled={page <= 0}
                  onClick={() => setFilter(status, page - 1)}
                >
                  {tp("orders.prev")}
                </button>
                <span>{tp("orders.page", { current: page + 1, total: pageData.totalPages })}</span>
                <button
                  type="button"
                  className="checkout-secondary"
                  disabled={page + 1 >= pageData.totalPages}
                  onClick={() => setFilter(status, page + 1)}
                >
                  {tp("orders.next")}
                </button>
              </div>
            ) : null}
          </>
        )}
      </section>
    </main>
  );
}
