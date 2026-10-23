import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import {
  AlertTriangle, ChevronLeft, ChevronRight, Package, Search, Store, Truck, Wallet,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { useCart } from "../contexts/CartContext";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { Skeleton } from "../components/feedback/Skeleton";
import { formatBaht } from "../components/cart/cartFormat";
import { buyerOrderApi, type OrderPage, type OrderSummary } from "../services/api";
import "../styles/cart.css";
import "../styles/checkout.css";
import "../styles/orders.css";

const PAGE_SIZE = 10;

/** Shopee/Lazada-style buckets → one backend status (API filters a single status). */
const ORDER_TABS: { value: string; status: string; tone: string }[] = [
  { value: "", status: "", tone: "all" },
  { value: "to-pay", status: "PENDING_PAYMENT", tone: "pay" },
  { value: "to-ship", status: "READY_TO_PACK", tone: "prepare" },
  { value: "shipping", status: "DISPATCHED", tone: "ship" },
  { value: "delivered", status: "DELIVERED", tone: "deliver" },
  { value: "completed", status: "COMPLETED", tone: "done" },
  { value: "cancelled", status: "CANCELLED", tone: "cancel" },
  { value: "refund", status: "REFUND_REQUESTED", tone: "refund" },
];

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

function primaryAction(order: OrderSummary, tp: (key: string, o?: Record<string, unknown>) => string) {
  if (order.status === "PENDING_PAYMENT") {
    return order.paymentMethod === "COD"
      ? tp("orders.list.actionCod")
      : tp("orders.list.actionPay");
  }
  if (order.status === "DISPATCHED" || order.status === "DELIVERED") return tp("orders.list.actionTrack");
  if (order.status === "COMPLETED") return tp("orders.list.actionBuyAgain");
  return tp("orders.viewDetail");
}

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
  const [searchDraft, setSearchDraft] = useState(params.get("q") ?? "");

  const legacyStatus = params.get("status") ?? "";
  const tabFromLegacy = ORDER_TABS.find((row) => row.status && row.status === legacyStatus)?.value;
  const tabValue = params.get("tab") ?? tabFromLegacy ?? "";
  const tab = ORDER_TABS.find((row) => row.value === tabValue) ?? ORDER_TABS[0];
  const status = tab.status || (ORDER_TABS.some((row) => row.status === legacyStatus) ? "" : legacyStatus);
  const page = Math.max(0, Number(params.get("page") || 0) || 0);
  const q = (params.get("q") ?? "").trim();
  const money = (value: unknown) => formatBaht(value as number, localeTag);

  useEffect(() => {
    setSearchDraft(q);
  }, [q]);

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
    buyerOrderApi.list({ status: status || undefined, page, size: PAGE_SIZE, q: q || undefined })
      .then((data) => { if (alive) setPageData(data); })
      .catch(() => { if (alive) { setFailed(true); setPageData(null); } })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [authLoading, user?.id, status, page, q, cart.access, reload]);

  const totalItems = pageData?.totalItems ?? 0;
  const pageCount = Math.max(1, Math.ceil(totalItems / PAGE_SIZE));
  /** 1-based page index for UI (matches /shop/:slug catalog pagination). */
  const displayPage = page + 1;

  const tabs = useMemo(() => ORDER_TABS.map((row) => ({
    ...row,
    label: row.value === ""
      ? tp("orders.all")
      : tp(`orders.list.tabs.${row.value}`),
  })), [tp]);

  const setFilter = (nextTab: string, nextPage = 0, nextQ = q) => {
    const next = new URLSearchParams();
    if (nextTab) next.set("tab", nextTab);
    if (nextQ.trim()) next.set("q", nextQ.trim());
    if (nextPage > 0) next.set("page", String(nextPage));
    setParams(next, { replace: true });
  };

  const onSearch = (event: FormEvent) => {
    event.preventDefault();
    setFilter(tab.value, 0, searchDraft);
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
      <div className="cart-width orders-shell">
        <section className="orders-panel">
          <div className="orders-panel-head">
            <div className="orders-panel-title">
              <h1>{tp("orders.title")}</h1>
              {!loading && !failed && pageData ? (
                <span className="orders-panel-count">
                  {tp("orders.list.found")} <b className="num">{totalItems.toLocaleString(localeTag)}</b>
                </span>
              ) : null}
            </div>
            <p className="orders-panel-sub">{tp("orders.list.subtitle")}</p>
            <form className="orders-search" onSubmit={onSearch} role="search">
              <Search size={18} aria-hidden="true" />
              <input
                value={searchDraft}
                onChange={(event) => setSearchDraft(event.target.value)}
                placeholder={tp("orders.list.searchPlaceholder")}
                aria-label={tp("orders.list.searchPlaceholder")}
              />
              <button type="submit">{tp("orders.list.search")}</button>
            </form>
          </div>

          <div className="orders-tabs" role="tablist" aria-label={tp("orders.title")}>
            {tabs.map((row) => (
              <button
                key={row.value || "all"}
                type="button"
                role="tab"
                aria-selected={tab.value === row.value}
                className={`is-${row.tone}`}
                onClick={() => setFilter(row.value, 0, q)}
              >
                {row.label}
              </button>
            ))}
          </div>

          <div className="orders-panel-body">
            {loading || cart.access === "loading" || authLoading ? (
              <div className="orders-list" aria-busy="true">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="orders-card">
                    <div style={{ padding: 16, display: "grid", gap: 10 }}>
                      <Skeleton height={18} width="40%" />
                      <Skeleton height={56} />
                      <Skeleton height={16} width="50%" />
                    </div>
                  </div>
                ))}
              </div>
            ) : failed ? (
              <div className="cart-empty feedback-error orders-empty-panel" role="alert">
                <AlertTriangle />
                <h2>{tp("orders.loadError")}</h2>
                <button type="button" onClick={() => setReload((n) => n + 1)}>{tp("cart.retry")}</button>
              </div>
            ) : !pageData?.items.length ? (
              <div className="cart-empty orders-empty-panel" role="status">
                <Package />
                <h2>{q ? tp("orders.list.searchEmpty") : tp("orders.empty")}</h2>
                <p>{q ? tp("orders.list.searchEmptyHint") : tp("orders.list.emptyHint")}</p>
                {q ? (
                  <button type="button" className="orders-empty-clear" onClick={() => setFilter(tab.value, 0, "")}>
                    {tp("orders.list.clearSearch")}
                  </button>
                ) : (
                  <Link to="/">{tp("cart.backToShopping")}</Link>
                )}
              </div>
            ) : (
              <>
                <div className="orders-list">
                  {pageData.items.map((order) => {
                    const tone = statusTone(order.status);
                    const cta = primaryAction(order, tp);
                    return (
                      <article key={order.id} className={`orders-card is-${tone}`}>
                        <div className="orders-card-top">
                          <Link className="orders-shop-link" to={`/account/orders/${order.id}`}>
                            <Store size={16} aria-hidden="true" />
                            <span>{order.shopName}</span>
                            <ChevronRight size={16} aria-hidden="true" />
                          </Link>
                          <span className={`orders-status is-${tone}`}>
                            {tp(`checkout.status.${order.status}`, { defaultValue: order.status })}
                          </span>
                        </div>

                        <Link className="orders-card-body" to={`/account/orders/${order.id}`}>
                          <span className="orders-card-thumb" aria-hidden="true">
                            {tone === "ship" || tone === "deliver" ? <Truck size={26} /> : tone === "pay" ? <Wallet size={26} /> : <Package size={26} />}
                          </span>
                          <div className="orders-card-copy">
                            <p className="checkout-order-number num">{order.orderNumber}</p>
                            <p className="orders-card-meta">
                              {order.placedAt
                                ? new Date(order.placedAt).toLocaleString(localeTag, { dateStyle: "medium", timeStyle: "short" })
                                : null}
                              {order.itemCount != null || order.totalQuantity != null ? (
                                <span>· {tp("orders.items", { count: order.totalQuantity ?? order.itemCount ?? 0 })}</span>
                              ) : null}
                            </p>
                            <div className="orders-card-badges">
                              {order.paymentMethod === "COD" ? (
                                <span className="orders-badge is-cod">{tp("orders.paymentsHistory.cod")}</span>
                              ) : order.paymentMethod ? (
                                <span className="orders-badge">{tp("orders.paymentsHistory.bankTransfer")}</span>
                              ) : null}
                              {order.paymentStatus ? (
                                <span className="orders-badge is-muted">
                                  {tp(`orders.list.paymentStatus.${order.paymentStatus}`, { defaultValue: order.paymentStatus })}
                                </span>
                              ) : null}
                            </div>
                          </div>
                          <div className="orders-card-price">
                            <small>{tp("orders.total")}</small>
                            <b className="num">{money(order.grandTotal)}</b>
                          </div>
                        </Link>

                        <footer className="orders-card-foot">
                          <span className="orders-card-hint">
                            {order.status === "PENDING_PAYMENT" && order.paymentMethod !== "COD"
                              ? tp("orders.list.hintPay")
                              : order.status === "DISPATCHED"
                                ? tp("orders.list.hintShip")
                                : tp("orders.list.hintDetail")}
                          </span>
                          <Link className="orders-card-cta" to={`/account/orders/${order.id}`}>
                            {cta}
                          </Link>
                        </footer>
                      </article>
                    );
                  })}
                </div>

                {totalItems > PAGE_SIZE ? (
                  <nav className="orders-pagination" aria-label={tp("orders.list.pageAria")}>
                    <button
                      type="button"
                      disabled={displayPage <= 1}
                      onClick={() => setFilter(tab.value, Math.max(0, page - 1), q)}
                      aria-label={tp("shops.pagePrev")}
                    >
                      <ChevronLeft />
                    </button>
                    {Array.from({ length: pageCount }, (_, index) => index + 1).map((number) => (
                      <button
                        type="button"
                        className={`num${displayPage === number ? " current" : ""}`}
                        key={number}
                        onClick={() => setFilter(tab.value, number - 1, q)}
                      >
                        {number}
                      </button>
                    ))}
                    <button
                      type="button"
                      disabled={displayPage >= pageCount}
                      onClick={() => setFilter(tab.value, Math.min(pageCount - 1, page + 1), q)}
                      aria-label={tp("shops.pageNext")}
                    >
                      <ChevronRight />
                    </button>
                  </nav>
                ) : null}
              </>
            )}
          </div>
        </section>
      </div>
    </main>
  );
}
