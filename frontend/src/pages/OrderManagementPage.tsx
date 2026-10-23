import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  BarChart3,
  Box,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  CircleUserRound,
  Crown,
  Download,
  Eye,
  Headphones,
  Home,
  PackageCheck,
  Printer,
  Search,
  Settings,
  ShoppingBag,
  Store,
  TicketPercent,
  Truck,
  WalletCards,
} from "lucide-react";
import MerchantBell from "../components/merchant/MerchantBell";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import MerchantOrderDrawer from "../components/merchant/MerchantOrderDrawer";
import LanguageSwitcher from "../components/LanguageSwitcher";
import MerchantShopMenu from "../components/MerchantShopMenu";
import { LoadError, SkeletonTable } from "../components/feedback/Skeleton";
import { formatBaht } from "../components/cart/cartFormat";
import {
  FULFILLMENT_STATUSES,
  PAYMENT_STATUSES,
  formatBangkokParts,
  orderErrorMessage,
  paymentTone,
  periodRange,
  statusTone,
  type PeriodKey,
} from "../components/merchant/orderFormat";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { usePermissions } from "../hooks/usePermissions";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { merchantOrderApi, type OrderPage } from "../services/api";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/order-management.css";

const menu = [
  [Home, "หน้าหลัก", "/merchant/dashboard"],
  [Box, "จัดการสินค้า", "/merchant/products"],
  [ShoppingBag, "คำสั่งซื้อ", "/merchant/orders"],
  [CircleUserRound, "ลูกค้า", "/merchant/customers"],
  [TicketPercent, "โปรโมชั่น/คูปอง", "/merchant/promotions"],
  [BarChart3, "รายงาน", "/merchant/reports"],
  [WalletCards, "การเงิน", "/merchant/finance"],
  [Truck, "การจัดส่ง", "/merchant/shipping"],
  [Store, "ร้านของฉัน", "/merchant/store"],
  [Settings, "ตั้งค่า", "/merchant/settings"],
] as const;

const TABS = [
  "all", "AWAITING", "PENDING_PAYMENT", "PAID_AWAITING_MERCHANT_CONFIRM", "MERCHANT_CONFIRMED",
  "DISPATCHED", "COMPLETED", "CANCELLED", "REFUND_REQUESTED",
];
const SIZES = [10, 20, 50];
const KPI_STATUSES = [undefined, "PENDING_PAYMENT", "MERCHANT_CONFIRMED", "COMPLETED"] as const;

function Brand() {
  return (
    <a className="merchant-brand" href="/">
      <span>
        <ShoppingBag />
        <i>✓</i>
      </span>
      <div>
        <b>
          ThaiShop<span>Fun</span>
        </b>
        <small>Shop Local · Support Thai · Smile Together</small>
      </div>
    </a>
  );
}

function pageNumbers(page: number, pages: number) {
  const first = Math.max(0, Math.min(page - 2, pages - 5));
  const last = Math.min(pages - 1, first + 4);
  const numbers: number[] = [];
  for (let n = first; n <= last; n += 1) numbers.push(n);
  return numbers;
}

export default function OrderManagementPage() {
  const { t } = useTranslation("merchant");
  const { localeTag } = usePublicTranslation();
  const { user, loading } = useAuth();
  const { shopId } = useActiveShop();
  const { canSeeMenuItem } = usePermissions();
  const [query, setQuery] = useState("");
  const [q, setQ] = useState("");
  const [tab, setTab] = useState("all");
  const [payment, setPayment] = useState("");
  const [fulfillment, setFulfillment] = useState("");
  const [period, setPeriod] = useState<PeriodKey>("all");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [data, setData] = useState<OrderPage | null>(null);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [kpis, setKpis] = useState<(number | null)[]>([null, null, null, null]);
  const [openId, setOpenId] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const [tone, setTone] = useState<"ok" | "fail">("ok");
  const toastTimer = useRef<number | undefined>(undefined);
  const qRef = useRef("");
  const unsupported = t("orderManagement.notSupported");
  const money = (value: number | string | null | undefined) => formatBaht(value, localeTag);

  const notify = useCallback((text: string, nextTone: "ok" | "fail" = "ok") => {
    setTone(nextTone);
    setNotice(text);
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setNotice(""), 2400);
  }, []);
  useEffect(() => () => window.clearTimeout(toastTimer.current), []);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      const next = query.trim();
      if (next === qRef.current) return;
      qRef.current = next;
      setQ(next);
      setPage(0);
    }, 300);
    return () => window.clearTimeout(handle);
  }, [query]);

  useEffect(() => {
    if (shopId == null) return;
    let alive = true;
    setFetching(true);
    setError(null);
    const range = periodRange(period);
    merchantOrderApi
      .list(shopId, {
        status: tab !== "all" && tab !== "AWAITING" ? tab : undefined,
        acceptance: tab === "AWAITING" ? "AWAITING" : undefined,
        paymentStatus: payment || undefined,
        fulfillmentStatus: fulfillment || undefined,
        from: range.from,
        to: range.to,
        q: q || undefined,
        page,
        size,
        sort: "placedAt",
        direction: "desc",
      })
      .then((result) => { if (alive) setData(result); })
      .catch((caught) => { if (alive) setError(orderErrorMessage(t, caught)); })
      .finally(() => { if (alive) setFetching(false); });
    return () => { alive = false; };
  }, [shopId, tab, payment, fulfillment, period, q, page, size, reload, t]);

  useEffect(() => {
    if (shopId == null) return;
    let alive = true;
    Promise.all(
      KPI_STATUSES.map((status) =>
        merchantOrderApi.list(shopId, { status, size: 1 }).then((r) => r.totalItems).catch(() => null),
      ),
    ).then((values) => { if (alive) setKpis(values); });
    return () => { alive = false; };
  }, [shopId, reload]);

  const handleChanged = useCallback(() => setReload((n) => n + 1), []);

  if (loading)
    return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if (!user) {
    window.location.replace("/login?next=/merchant/orders");
    return null;
  }
  if (hasPlatformAccess(user) && !hasMerchantAccess(user)) {
    window.location.replace("/admin");
    return null;
  }
  if (!hasMerchantAccess(user)) {
    window.location.replace("/");
    return null;
  }

  const items = data?.items ?? [];
  const total = data?.totalItems ?? 0;
  const pages = Math.max(1, data?.totalPages ?? 1);
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, page * size + items.length);
  const reset = () => {
    setQuery("");
    qRef.current = "";
    setQ("");
    setPayment("");
    setFulfillment("");
    setPeriod("all");
    setTab("all");
    setPage(0);
  };
  const changeTab = (next: string) => {
    setTab(next);
    setPage(0);
  };
  const kpiValue = (index: number) => (kpis[index] == null ? "—" : kpis[index]!.toLocaleString(localeTag));
  const tabLabel = (key: string) =>
    key === "all" ? t("orderManagement.tabs.all") : key === "AWAITING" ? t("orderManagement.tabs.awaiting") : t(`orderManagement.status.${key}`);
  const empty = !fetching && !error && items.length === 0;

  return (
    <main className="merchant-shell om-shell">
      <header className="merchant-topbar">
        <Brand />
        <MerchantTopbarSearch />
        <LanguageSwitcher /> <MerchantBell />
        <MerchantShopMenu />
      </header>
      <aside className="merchant-sidebar pm-sidebar om-sidebar">
        <nav>
          {menu.filter(canSeeMenuItem).map(([Icon, label, href]) => (
            <a className={label === "คำสั่งซื้อ" ? "active" : ""} href={href} key={label}>
              <Icon />
              {label}
            </a>
          ))}
        </nav>
        <section className="upgrade-card">
          <Crown />
          <h3>ยกระดับร้านค้าของคุณ</h3>
          <p>
            ใช้ฟีเจอร์ครบชุด เพิ่มยอดขาย
            <br />
            ได้มากกว่าเดิม
          </p>
          <button onClick={() => notify("กำลังเปิดรายละเอียดแพ็กเกจ")}>
            ดูแพ็กเกจ
          </button>
        </section>
        <div className="merchant-support">
          <a href="/help">
            <CircleHelp />
            ศูนย์ช่วยเหลือ
          </a>
          <button onClick={() => notify("กำลังติดต่อทีมงาน")}>
            <Headphones />
            ติดต่อทีมงาน
          </button>
        </div>
      </aside>
      <section className="pm-content om-content">
        <header className="pm-heading om-heading">
          <div>
            <h1>{t("orderManagement.title")}</h1>
            <p>{t("orderManagement.subtitle")}</p>
          </div>
          <div className="pm-actions">
            <button disabled title={unsupported}>
              <Download />
              {t("orderManagement.export")}
            </button>
            <button disabled title={unsupported} className="primary">
              <Printer />
              {t("orderManagement.printLabels")}
            </button>
          </div>
        </header>
        <section className="pm-kpis om-kpis">
          <article className="all">
            <span>
              <ShoppingBag />
            </span>
            <div>
              <small>{t("orderManagement.kpi.all")}</small>
              <b>{kpiValue(0)}</b>
              <em>{t("orderManagement.kpi.allHint")}</em>
            </div>
          </article>
          <article className="low">
            <span>฿</span>
            <div>
              <small>{t("orderManagement.status.PENDING_PAYMENT")}</small>
              <b>{kpiValue(1)}</b>
              <em>{t("orderManagement.kpi.pendingHint")}</em>
            </div>
          </article>
          <article className="empty">
            <span>
              <Truck />
            </span>
            <div>
              <small>{t("orderManagement.status.MERCHANT_CONFIRMED")}</small>
              <b>{kpiValue(2)}</b>
              <em>{t("orderManagement.kpi.confirmedHint")}</em>
            </div>
          </article>
          <article className="ready">
            <span>
              <PackageCheck />
            </span>
            <div>
              <small>{t("orderManagement.status.COMPLETED")}</small>
              <b>{kpiValue(3)}</b>
              <em>{t("orderManagement.kpi.completedHint")}</em>
            </div>
          </article>
        </section>
        <section className="pm-table-card om-card">
          <div className="om-tabs" role="tablist">
            {TABS.map((key) => (
              <button
                key={key}
                role="tab"
                aria-selected={tab === key}
                className={tab === key ? "active" : ""}
                onClick={() => changeTab(key)}
              >
                {tabLabel(key)}
              </button>
            ))}
          </div>
          <form className="om-filters" onSubmit={(e) => { e.preventDefault(); qRef.current = query.trim(); setQ(qRef.current); setPage(0); setReload((n) => n + 1); }}>
            <label className="om-search">
              <Search />
              <input
                aria-label={t("orderManagement.filters.searchLabel")}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder={t("orderManagement.filters.searchPlaceholder")}
              />
            </label>
            <label>
              {t("orderManagement.filters.payment")}
              <select
                aria-label={t("orderManagement.filters.payment")}
                value={payment}
                onChange={(e) => { setPayment(e.target.value); setPage(0); }}
              >
                <option value="">{t("orderManagement.filters.all")}</option>
                {PAYMENT_STATUSES.map((s) => (
                  <option key={s} value={s}>{t(`orderManagement.paymentStatus.${s}`)}</option>
                ))}
              </select>
            </label>
            <label>
              {t("orderManagement.filters.fulfillment")}
              <select
                aria-label={t("orderManagement.filters.fulfillment")}
                value={fulfillment}
                onChange={(e) => { setFulfillment(e.target.value); setPage(0); }}
              >
                <option value="">{t("orderManagement.filters.all")}</option>
                {FULFILLMENT_STATUSES.map((s) => (
                  <option key={s} value={s}>{t(`orderManagement.fulfillmentStatus.${s}`)}</option>
                ))}
              </select>
            </label>
            <label>
              {t("orderManagement.filters.period")}
              <select
                aria-label={t("orderManagement.filters.period")}
                value={period}
                onChange={(e) => { setPeriod(e.target.value as PeriodKey); setPage(0); }}
              >
                <option value="all">{t("orderManagement.filters.periods.all")}</option>
                <option value="7d">{t("orderManagement.filters.periods.7d")}</option>
                <option value="30d">{t("orderManagement.filters.periods.30d")}</option>
                <option value="month">{t("orderManagement.filters.periods.month")}</option>
              </select>
            </label>
            <div>
              <button type="button" onClick={reset}>
                {t("orderManagement.filters.reset")}
              </button>
              <button className="primary">{t("orderManagement.filters.search")}</button>
            </div>
          </form>
          <div className="om-table-wrap" aria-busy={fetching}>
            {error ? (
              <LoadError message={error} onRetry={() => setReload((n) => n + 1)} retryLabel={t("shopSettings.retry")} />
            ) : fetching && items.length === 0 ? (
              <SkeletonTable rows={6} cols={8} />
            ) : (
              <table className={fetching ? "is-refetching" : undefined}>
                <thead>
                  <tr>
                    <th>
                      <input aria-label={t("orderManagement.columns.selectAll")} type="checkbox" disabled title={unsupported} />
                    </th>
                    <th>{t("orderManagement.columns.order")}</th>
                    <th>{t("orderManagement.columns.customer")}</th>
                    <th>{t("orderManagement.columns.items")}</th>
                    <th>{t("orderManagement.columns.total")}</th>
                    <th>{t("orderManagement.columns.payment")}</th>
                    <th>{t("orderManagement.columns.status")}</th>
                    <th>{t("orderManagement.columns.placedAt")}</th>
                    <th>{t("orderManagement.columns.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((o) => {
                    const hidden = o.awaitingAcceptance;
                    const parts = formatBangkokParts(o.placedAt, localeTag);
                    return (
                      <tr key={o.id}>
                        <td>
                          <input aria-label={o.orderNumber} type="checkbox" disabled title={unsupported} />
                        </td>
                        <td>
                          <b className="num">{o.orderNumber}</b>
                          {hidden && <span className="om-badge warn">{t("orderManagement.awaitingBadge")}</span>}
                          {!hidden && o.paymentMethod === "COD" && <span className="om-badge cod">COD</span>}
                        </td>
                        <td>—</td>
                        <td>
                          {hidden || o.itemCount == null ? "—" : (
                            <div className="om-product">
                              <p>
                                <b>{t("orderManagement.itemsCount", { count: o.itemCount })}</b>
                                {o.totalQuantity != null && <small>{t("orderManagement.quantityCount", { count: o.totalQuantity })}</small>}
                              </p>
                            </div>
                          )}
                        </td>
                        <td>{hidden ? "—" : <strong className="num">{money(o.grandTotal)}</strong>}</td>
                        <td>
                          {hidden ? "—" : (
                            <span className={`om-pill ${paymentTone(o.paymentStatus)}`}>
                              {t(`orderManagement.paymentStatus.${o.paymentStatus}`, { defaultValue: o.paymentStatus })}
                            </span>
                          )}
                        </td>
                        <td>
                          {hidden ? "—" : (
                            <span className={`om-pill ${statusTone(o.status)}`}>
                              {t(`orderManagement.status.${o.status}`, { defaultValue: o.status })}
                            </span>
                          )}
                        </td>
                        <td>
                          {parts ? parts.map((x) => <span key={x}>{x}</span>) : "—"}
                        </td>
                        <td>
                          <div className="om-row-actions">
                            <button
                              aria-label={t("orderManagement.viewOrder", { number: o.orderNumber })}
                              onClick={() => setOpenId(o.id)}
                            >
                              <Eye />
                            </button>
                            <button disabled title={unsupported} aria-label={t("orderManagement.printOrder", { number: o.orderNumber })}>
                              <Printer />
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
            {empty && <div className="om-empty">{t("orderManagement.empty")}</div>}
          </div>
          <div className="pm-pagination">
            <span>{t("orderManagement.pagination.range", { from, to, total })}</span>
            <label>
              {t("orderManagement.pagination.show")}{" "}
              <select
                aria-label={t("orderManagement.pagination.perPage")}
                value={size}
                onChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}
              >
                {SIZES.map((n) => <option key={n} value={n}>{n}</option>)}
              </select>{" "}
              {t("orderManagement.pagination.rows")}
            </label>
            <nav>
              <button aria-label={t("orderManagement.pagination.first")} disabled={page === 0} onClick={() => setPage(0)}>«</button>
              <button aria-label={t("orderManagement.pagination.prev")} disabled={page === 0} onClick={() => setPage(page - 1)}>
                <ChevronLeft />
              </button>
              {pageNumbers(page, pages).map((n) => (
                <button key={n} className={n === page ? "active" : ""} onClick={() => setPage(n)}>{n + 1}</button>
              ))}
              {pageNumbers(page, pages).slice(-1)[0] < pages - 1 && <span>...</span>}
              <button aria-label={t("orderManagement.pagination.next")} disabled={page >= pages - 1} onClick={() => setPage(page + 1)}>
                <ChevronRight />
              </button>
            </nav>
          </div>
        </section>
      </section>
      {openId && shopId != null && (
        <MerchantOrderDrawer
          shopId={shopId}
          orderId={openId}
          onClose={() => setOpenId(null)}
          onChanged={handleChanged}
          notify={notify}
        />
      )}
      {notice && (
        <div className={`merchant-toast${tone === "fail" ? " om-toast-fail" : ""}`} role="status">
          {notice}
        </div>
      )}
    </main>
  );
}
