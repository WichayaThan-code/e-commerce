import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  BarChart3,
  CalendarDays,
  Download,
  FileText,
  Lightbulb,
  Lock,
  Package,
  PieChart,
  Receipt,
  Repeat,
  ShoppingBag,
  ShoppingCart,
  TicketPercent,
  Truck,
  UserPlus,
  UsersRound,
  WalletCards,
} from "lucide-react";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import MerchantTopbarActions from "../components/merchant/MerchantTopbarActions";
import MerchantPanelEmpty from "../components/merchant/MerchantPanelEmpty";
import DateRangePicker from "../components/form/DateRangePicker";
import { localeTag } from "../i18n";
import { useAuth } from "../contexts/AuthContext";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { hasPlatformAccess, hasMerchantAccess, hasShopPermission } from "../services/permissions";
import { usePermissions } from "../hooks/usePermissions";
import { numberOf } from "../components/merchant/orderFormat";
import {
  authorizedBlob,
  mediaUrl,
  merchantReportsApi,
  type MerchantDashboardRange,
  type MerchantReportOverview,
  type MerchantReportPeriod,
  type MerchantReportTab,
} from "../services/api";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/reports.css";
import MerchantSidebar from "../components/merchant/MerchantSidebar";
import MerchantBrand from "../components/merchant/MerchantBrand";

type RangeKey = MerchantDashboardRange | "custom";
/** [key, i18n short label, i18n long label] — same presets as the dashboard date filter. */
const RANGES: [RangeKey, string, string][] = [
  ["today", "today", "todayFull"],
  ["7d", "d7", "d7Full"],
  ["30d", "d30", "d30Full"],
  ["1y", "y1", "y1Full"],
  ["custom", "custom", "customFull"],
];

/** Tabs keep their Thai labels (via i18n) but are addressed by key. */
const TABS: MerchantReportTab[] = ["overview", "sales", "products", "orders", "customers", "promotions", "fees", "shipping"];

type PanelKey = "chart" | "category" | "channel" | "products" | "customers" | "fee" | "feeRate" | "advice"
  | "promoSummary" | "promoTypes" | "promoTop" | "shipSummary" | "shipCarriers" | "shipOps";
/** Every tab reuses the same panels (same grids); a tab shows the subset that is relevant for its topic. */
const TAB_PANELS: Record<MerchantReportTab, PanelKey[]> = {
  overview: ["chart", "category", "channel", "products", "customers", "fee", "feeRate", "advice"],
  sales: ["chart", "category", "channel", "advice"],
  products: ["category", "products"],
  orders: ["chart", "channel", "advice"],
  customers: ["customers", "advice"],
  promotions: ["promoSummary", "promoTypes", "promoTop", "advice"],
  fees: ["chart", "fee", "feeRate"],
  shipping: ["shipSummary", "shipCarriers", "shipOps", "advice"],
};

const CHANNEL_GLYPH: Record<string, string> = { STOREFRONT: "◎", LINE_OA: "LINE", SHOPEE: "S", TIKTOK: "♪", LAZADA: "L", MANUAL: "M", OMS_IMPORT: "O", UNKNOWN: "…" };

const bkkDate = (d: Date) => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Bangkok" }).format(d);
const ymd = (v: string) => new Date(`${v}T00:00:00Z`);
const num = (v: unknown) => numberOf(v as number);

/** Round the chart max up to a "nice" number so the 5 grid lines read well. */
function niceMax(v: number) {
  if (v <= 0) return 5000;
  const step = Math.pow(10, Math.floor(Math.log10(v / 5)));
  for (const m of [1, 2, 2.5, 5, 10]) if (m * step * 5 >= v) return m * step * 5;
  return Math.ceil(v / 5) * 5;
}

type LoadState = { status: "idle" | "loading" | "ready" | "error"; data: MerchantReportOverview | null };

/** Top-5 thumbnail: merchant image URLs need auth (blob), others go through mediaUrl; missing/broken -> neutral placeholder. */
function ProductThumb({ path, label }: { path?: string | null; label: string }) {
  const [src, setSrc] = useState("");
  const [broken, setBroken] = useState(false);
  useEffect(() => {
    setBroken(false);
    if (!path) { setSrc(""); return; }
    if (!path.startsWith("/api/merchant/")) { setSrc(mediaUrl(path)); return; }
    let active = true;
    let objectUrl = "";
    setSrc("");
    authorizedBlob(path)
      .then((blob) => { if (!active) return; objectUrl = URL.createObjectURL(blob); setSrc(objectUrl); })
      .catch(() => { if (active) setBroken(true); });
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [path]);
  if (!src || broken) return <span className="product-thumb placeholder" title={label} aria-label={label}><Package /></span>;
  return <span className="product-thumb"><img src={src} alt="" onError={() => setBroken(true)} /></span>;
}

export default function ReportsPage() {
  const { t, i18n } = useTranslation("merchant");
  const { user, loading } = useAuth();
  const { ready, shopId } = useActiveShop();
  const { canInShop, deniedTitle } = usePermissions();
  const canExport = canInShop("REPORT_EXPORT");
  const canRead = !!user && hasShopPermission(user, "REPORT_READ", shopId);
  const [activeTab, setActiveTab] = useState<MerchantReportTab>("overview");
  const [range, setRange] = useState<RangeKey>("30d");
  const [customFrom, setCustomFrom] = useState(() => bkkDate(new Date(Date.now() - 6 * 86400000)));
  const [customTo, setCustomTo] = useState(() => bkkDate(new Date()));
  const [notice, setNotice] = useState("");
  const [state, setState] = useState<LoadState>({ status: "idle", data: null });
  const [reloadTick, setReloadTick] = useState(0);
  const [hover, setHover] = useState<number | null>(null);
  const [exporting, setExporting] = useState(false);
  const requestId = useRef(0);

  const customValid = !!customFrom && !!customTo && customFrom <= customTo;
  const periodKey = range === "custom"
    ? (customValid ? JSON.stringify({ from: customFrom, to: customTo }) : "")
    : JSON.stringify({ range });

  // One overview request powers every tab. A new period/shop/retry aborts the previous request; stale answers are ignored.
  useEffect(() => {
    if (!ready) return;
    setHover(null);
    if (shopId == null || !canRead || !periodKey) {
      requestId.current += 1;
      setState({ status: "idle", data: null });
      return;
    }
    const id = ++requestId.current;
    const controller = new AbortController();
    setState({ status: "loading", data: null });
    merchantReportsApi.overview(shopId, JSON.parse(periodKey) as MerchantReportPeriod, controller.signal)
      .then((data) => { if (requestId.current === id) setState({ status: "ready", data }); })
      .catch(() => { if (!controller.signal.aborted && requestId.current === id) setState({ status: "error", data: null }); });
    return () => controller.abort();
  }, [ready, shopId, canRead, periodKey, reloadTick]);

  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if (!user) { window.location.replace("/login?next=/merchant/reports"); return null; }
  if ((hasPlatformAccess(user) && !hasMerchantAccess(user))) { window.location.replace("/admin"); return null; }
  if (!hasMerchantAccess(user)) { window.location.replace("/"); return null; }

  const loc = localeTag();
  const lang = (i18n.language || "th").toLowerCase();
  const data = state.data;
  const panels = TAB_PANELS[activeTab];
  const show = (panel: PanelKey) => panels.includes(panel);
  const count = (...keys: PanelKey[]) => keys.filter(show).length;

  const notify = (message: string) => { setNotice(message); window.setTimeout(() => setNotice(""), 1900); };
  const baht = (v: unknown) => "฿ " + num(v).toLocaleString(loc, { maximumFractionDigits: 2 });
  const shortBaht = (v: number) => (v === 0 ? "0" : "฿ " + v.toLocaleString(loc, { maximumFractionDigits: 0 }));
  const int = (v: unknown) => num(v).toLocaleString(loc);
  const dec = (v: unknown, digits = 1) => num(v).toLocaleString(loc, { maximumFractionDigits: digits });
  const dayFmt = new Intl.DateTimeFormat(loc, { timeZone: "UTC", day: "numeric", month: "short" });
  const fullFmt = new Intl.DateTimeFormat(loc, { timeZone: "UTC", day: "numeric", month: "short", year: "numeric" });
  const monthFmt = new Intl.DateTimeFormat(loc, { timeZone: "UTC", month: "short", year: "2-digit" });
  const bucketLabel = (start: string, bucket: string) => (bucket === "month" ? monthFmt.format(ymd(start)) : dayFmt.format(ymd(start)));
  const bucketTitle = (start: string, end: string, bucket: string) =>
    bucket === "day" || start === end ? fullFmt.format(ymd(start)) : `${dayFmt.format(ymd(start))} – ${fullFmt.format(ymd(end))}`;

  const rangeLong = t(`reports.range.${RANGES.find((r) => r[0] === range)?.[2] ?? "todayFull"}`);
  const channelName = (code: string) => t(`reports.channels.names.${code}`, { defaultValue: t("reports.channels.names.UNKNOWN") });
  const categoryName = (c: MerchantReportOverview["categories"][number]) => {
    if (c.key === "UNCATEGORIZED") return t("reports.categories.uncategorized");
    if (c.key === "OTHER") return t("reports.categories.other");
    const pick = lang.startsWith("zh") ? c.nameZh : lang.startsWith("en") ? c.nameEn : c.nameTh;
    return pick || c.nameTh || c.nameEn || c.key;
  };

  /** Growth chip: null (no previous base) -> em dash; red only for a real negative change. */
  const growth = (value: number | string | null | undefined, tag: "i" | "strong" = "i") => {
    const Tag = tag;
    if (value == null) return <Tag className="growth none" title={t("reports.vsPrevious")}>{t("reports.growthNone")}</Tag>;
    const v = num(value);
    const text = Math.abs(v) > 999 ? "999+" : dec(Math.abs(v));
    return <Tag className={`growth ${v < 0 ? "down" : v > 0 ? "up" : "none"}`} title={t("reports.vsPrevious")}>{v > 0 ? "↑ " : v < 0 ? "↓ " : ""}{text}%</Tag>;
  };

  const empty = (title: string, hint?: string, icon: ReactNode = <BarChart3 />) => (
    <MerchantPanelEmpty icon={icon} title={title} hint={hint} />
  );
  /** Panel body gate: no permission / loading / error are explicit states, never zeros. */
  const gate = (render: (d: MerchantReportOverview) => ReactNode): ReactNode => {
    if (!canRead) return empty(t("reports.noPermission"), t("reports.noPermissionHint"), <Lock />);
    if (state.status === "loading") return empty(t("reports.loading"), t("reports.loadingHint"), <BarChart3 />);
    if (state.status !== "ready" || !data) {
      return empty(
        state.status === "error" ? t("reports.error.title") : "—",
        state.status === "error" ? t("reports.error.hint") : undefined,
        <BarChart3 />,
      );
    }
    return render(data);
  };
  const kpi = (render: (d: MerchantReportOverview) => ReactNode) => (!canRead ? "—" : state.status === "loading" ? "…" : data ? render(data) : "—");

  const viewAll = (to: string, permission: string) =>
    canInShop(permission)
      ? <Link className="report-view-all" to={to}>{t("reports.viewAll")}</Link>
      : <button type="button" disabled title={t("reports.viewAllDenied")}>{t("reports.viewAll")}</button>;

  const download = async () => {
    if (!periodKey || shopId == null) { notify(t("reports.export.needRange")); return; }
    const exportShopId = shopId;
    const exportPeriod = JSON.parse(periodKey) as MerchantReportPeriod;
    const exportTab = activeTab;
    setExporting(true);
    try {
      await merchantReportsApi.exportXlsx(exportShopId, exportPeriod, exportTab);
      if (shopId !== exportShopId || periodKey !== JSON.stringify(exportPeriod)) {
        notify(t("reports.export.stale"));
        return;
      }
      notify(t("reports.export.done"));
    } catch {
      notify(t("reports.export.failed"));
    } finally {
      setExporting(false);
    }
  };

  const adviceText = (d: MerchantReportOverview): [string, string | null] => {
    const text = (flag: string) => {
      switch (flag) {
        case "GMV_UP":
        case "GMV_DOWN": return t(`reports.advice.${flag}`, { pct: dec(Math.abs(num(d.kpis.gmvGrowthPct))) });
        case "REFUNDS_PRESENT": return t("reports.advice.REFUNDS_PRESENT", { amount: baht(d.kpis.refundedAmount) });
        default: return t(`reports.advice.${flag}`);
      }
    };
    const flags = d.adviceFlags;
    if (flags.length === 0) return ["", null];
    if (flags[0] === "NO_SALES") return [text("NO_SALES"), t("reports.advice.NO_SALES_TIP")];
    return [text(flags[0]), flags[1] ? text(flags[1]) : null];
  };

  const salesChart = (d: MerchantReportOverview) => {
    const series = d.series;
    const n = series.length;
    const gmv = series.map((p) => num(p.gmv));
    const income = series.map((p) => num(p.estimatedNet));
    const fee = series.map((p) => num(p.estimatedFee));
    const max = niceMax(Math.max(0, ...gmv));
    const total = gmv.reduce((a, b) => a + b, 0);
    if (total === 0) return empty(t("reports.chart.noSales"), t("reports.chart.emptyHint"), <BarChart3 />);
    const every = n > 16 ? Math.ceil(n / 8) : 1;
    const barW = n <= 8 ? 12 : n <= 14 ? 9 : n <= 20 ? 6 : 4;
    const peak = gmv.indexOf(Math.max(...gmv));
    const tipIndex = hover != null && hover < n ? hover : peak;
    const tip = tipIndex >= 0 ? series[tipIndex] : null;
    const h = (v: number) => `${Math.max(0, Math.round((v / max) * 150))}px`;
    return (
      <div className="sales-graph">
        <div className="graph-y">{[5, 4, 3, 2, 1, 0].map((k) => <span key={k}>{shortBaht((max / 5) * k)}</span>)}</div>
        <div className="graph-plot" onMouseLeave={() => setHover(null)}>
          {series.map((p, i) => (
            <div
              className={`graph-day${tipIndex === i ? " is-active" : ""}`}
              key={p.start}
              style={{ width: `${Math.min(12, 92 / n)}%` }}
              onMouseEnter={() => setHover(i)}
              title={`${bucketTitle(p.start, p.end, d.bucket)} · ${baht(gmv[i])} · ${t("reports.chart.tipOrders", { count: p.orders })}`}
            >
              <div>
                <i className="bar-gmv" style={{ height: h(gmv[i]), width: barW }} />
                <i className="bar-income" style={{ height: h(income[i]), width: barW }} />
                <i className="bar-fee" style={{ height: fee[i] > 0 ? `${Math.max(3, Math.round((fee[i] / max) * 150))}px` : "0px", width: barW }} />
              </div>
              <small style={i % every === 0 ? undefined : { visibility: "hidden" }}>{bucketLabel(p.start, d.bucket)}</small>
            </div>
          ))}
          {tip ? (
            <div className="report-tip">
              <b>{bucketTitle(tip.start, tip.end, d.bucket)}</b>
              <span><i className="dot-gmv" />{t("reports.chart.tipGmv")}<strong>{baht(tip.gmv)}</strong></span>
              <span><i className="dot-income" />{t("reports.chart.tipIncome")}<strong>{baht(tip.estimatedNet)}</strong></span>
              <span><i className="dot-fee" />{t("reports.chart.tipFee")}<strong>{baht(tip.estimatedFee)}</strong></span>
              <span className="orders">{t("reports.chart.tipOrders", { count: tip.orders })}</span>
            </div>
          ) : null}
        </div>
      </div>
    );
  };

  const alertText = !periodKey
    ? (customFrom && customTo ? t("reports.range.invalidRange") : t("reports.range.pickDates"))
    : shopId == null && ready ? t("reports.noShop")
    : !canRead ? t("reports.noPermission")
    : "";

  return <main className="merchant-shell pm-shell report-shell">
    <header className="merchant-topbar"><MerchantBrand /><MerchantTopbarSearch /><MerchantTopbarActions /></header>
    <MerchantSidebar active="reports"/>

    <section className="pm-content report-content">
      <header className="pm-heading report-heading">
        <div><h1><BarChart3 />{t("reports.title")}</h1><p>{t("reports.subtitle")}</p></div>
        <div className="report-actions">
          <div className="merchant-range">
            <div className="merchant-range-seg" role="tablist" aria-label={t("reports.range.label")}>
              <CalendarDays />
              {RANGES.map(([key, short]) => (
                <button key={key} type="button" role="tab" aria-selected={range === key} className={range === key ? "active" : ""} onClick={() => setRange(key)}>{t(`reports.range.${short}`)}</button>
              ))}
            </div>
            {range === "custom" ? (
              <DateRangePicker
                className="merchant-range-custom"
                label={t("orderManagement.filters.dateRange")}
                from={customFrom}
                to={customTo}
                clearLabel={t("orderManagement.filters.clear")}
                applyLabel={t("orderManagement.filters.apply")}
                presetsLabel={{
                  d7: t("orderManagement.filters.presets.d7"),
                  d30: t("orderManagement.filters.presets.d30"),
                  month: t("orderManagement.filters.presets.month"),
                }}
                onChange={({ from, to }) => { setCustomFrom(from); setCustomTo(to); }}
              />
            ) : <small>{rangeLong} · {t("reports.range.thaiTime")}</small>}
          </div>
          <button type="button" disabled={!canExport || exporting || !periodKey} title={deniedTitle(canExport)} className="primary" onClick={() => void download()}>
            <Download />{exporting ? t("reports.export.busy") : t("reports.export.button")}
          </button>
        </div>
      </header>

      <nav className="report-tabs" aria-label={t("reports.tabsLabel")}>
        {TABS.map((tab) => <button key={tab} type="button" className={activeTab === tab ? "active" : ""} aria-current={activeTab === tab ? "page" : undefined} onClick={() => setActiveTab(tab)}>{t(`reports.tabs.${tab}`)}</button>)}
      </nav>

      {(state.status === "error" || alertText) && (
        <div className="report-alert" role="alert">
          <span>{state.status === "error" ? t("reports.error.generic") : alertText}</span>
          {state.status === "error" && <button type="button" onClick={() => setReloadTick((n) => n + 1)}>{t("reports.error.retry")}</button>}
        </div>
      )}

      <section className="report-kpis" aria-busy={state.status === "loading"}>
        <article className="gmv"><span><ShoppingCart /></span><div><small>{t("reports.kpi.gmv")}</small><b>{kpi((d) => <>{baht(d.kpis.gmv)} {growth(d.kpis.gmvGrowthPct)}</>)}</b><em>{t("reports.kpi.gmvNote")}</em></div></article>
        <article className="income"><span><WalletCards /></span><div><small>{t("reports.kpi.income")}</small><b>{kpi((d) => <>{baht(d.kpis.estimatedNet)} {growth(d.kpis.estimatedNetGrowthPct)}</>)}</b><em>{t("reports.kpi.incomeNote")}</em></div></article>
        <article className="fee"><span>●</span><div><small>{t("reports.kpi.fee")}</small><b>{kpi((d) => <>{baht(d.kpis.estimatedFee)} {growth(d.kpis.estimatedFeeGrowthPct)}</>)}</b><em>{t("reports.kpi.feeNote")}</em></div></article>
        <article className="orders"><span><FileText /></span><div><small>{t("reports.kpi.orders")}</small><b>{kpi((d) => <>{int(d.kpis.paidOrderCount)} {growth(d.kpis.paidOrderGrowthPct)}</>)}</b><em>{data && canRead ? t("reports.kpi.ordersNote", { count: data.kpis.allOrderCount }) : t("reports.vsPrevious")}</em></div></article>
        <article className="customer"><span><UsersRound /></span><div><small>{t("reports.kpi.customers")}</small><b>{kpi((d) => <>{int(d.customerSummary.uniqueCustomers)} {growth(d.customerSummary.uniqueGrowthPct)}</>)}</b><em>{data && canRead ? t("reports.kpi.customersNote", { count: data.customerSummary.newCustomers }) : t("reports.vsPrevious")}</em></div></article>
      </section>

      {count("chart", "category", "promoSummary", "shipSummary") > 0 && (
        <section className="report-primary-grid" data-count={count("chart", "category", "promoSummary", "shipSummary")}>
          {show("chart") && (
            <article className="report-panel sales-chart">
              <header><h2>{t("reports.chart.title")}</h2></header>
              <div className="report-legend">
                <span><i className="dot-gmv" />{t("reports.chart.legendGmv")}</span>
                <span><i className="dot-income" />{t("reports.chart.legendIncome")}</span>
                <span><i className="dot-fee" />{t("reports.chart.legendFee")}</span>
              </div>
              {gate(salesChart)}
            </article>
          )}
          {show("category") && <article className="report-panel category-panel"><header><h2>{t("reports.categories.title")}</h2>{viewAll("/merchant/products", "PRODUCT_READ")}</header>{gate((d) => {
            if (d.categories.length === 0) return empty(t("reports.categories.empty"), t("reports.categories.emptyHint"), <PieChart />);
            let from = 0;
            const stops = d.categories.map((c, i) => { const to = from + num(c.sharePercent); const s = `var(--rc-${i}) ${from}% ${to}%`; from = to; return s; });
            const background = d.categories.length === 1 ? "var(--rc-0)" : `conic-gradient(${stops.join(",")}${from < 100 ? `,var(--rc-rest) ${from}% 100%` : ""})`;
            return <div className="category-body"><div className="report-donut" style={{ background }}><b>{baht(d.kpis.itemSales)}<small>{t("reports.categories.centerLabel")}</small></b></div><ul>{d.categories.map((c, i) => <li key={c.key} title={`${baht(c.itemSales)} · ${int(c.quantity)}`}><i className={`color-${i}`} /><span>{categoryName(c)}</span><b>{dec(c.sharePercent)}%</b></li>)}</ul></div>;
          })}</article>}
          {show("promoSummary") && <article className="report-panel promo-chart"><header><h2 title={t("reports.promoPanel.note")}>{t("reports.promoPanel.title")}</h2>{viewAll("/merchant/promotions", "PROMOTION_READ")}</header>{gate((d) => {
            const p = d.promotionSummary;
            if (p.redemptionCount === 0 && p.paidOrdersWithCoupon === 0) {
              return empty(t("reports.promoPanel.empty"), t("reports.promoPanel.emptyHint"), <TicketPercent />);
            }
            const attach = Math.min(100, Math.max(0, num(p.attachRatePct)));
            const barMax = Math.max(p.redemptionCount, p.paidOrdersWithCoupon, 1);
            const tiles: [ReactNode, string, string, number | null, string][] = [
              [<Receipt />, t("reports.promoPanel.redemptions"), int(p.redemptionCount), p.redemptionGrowthPct, "tone-blue"],
              [<WalletCards />, t("reports.promoPanel.discount"), baht(p.discountTotal), p.discountGrowthPct, "tone-amber"],
              [<ShoppingCart />, t("reports.promoPanel.couponOrders"), int(p.paidOrdersWithCoupon), p.couponOrderGrowthPct, "tone-mint"],
              [<BarChart3 />, t("reports.promoPanel.attachRate"), `${dec(p.attachRatePct)}%`, p.attachRateGrowthPct, "tone-violet"],
            ];
            return (
              <div className="promo-chart-body">
                <div className="promo-chart-kpis">
                  {tiles.map(([icon, label, value, change, tone]) => (
                    <div className={`promo-kpi ${tone}`} key={label}>
                      <i>{icon}</i>
                      <div>
                        <small>{label}</small>
                        <b>{value} {growth(change)}</b>
                      </div>
                    </div>
                  ))}
                </div>
                <div className="promo-chart-viz">
                  <div className="promo-attach" style={{ background: `conic-gradient(var(--rp-blue) 0 ${attach}%, var(--rp-track) ${attach}% 100%)` }}>
                    <b>{dec(p.attachRatePct)}%<small>{t("reports.promoPanel.attachRate")}</small></b>
                  </div>
                  <ul className="promo-bars">
                    <li>
                      <span>{t("reports.promoPanel.redemptions")}</span>
                      <em><i className="bar-blue" style={{ width: `${Math.max(6, (p.redemptionCount / barMax) * 100)}%` }} /></em>
                      <strong>{int(p.redemptionCount)}</strong>
                    </li>
                    <li>
                      <span>{t("reports.promoPanel.couponOrders")}</span>
                      <em><i className="bar-mint" style={{ width: `${Math.max(6, (p.paidOrdersWithCoupon / barMax) * 100)}%` }} /></em>
                      <strong>{int(p.paidOrdersWithCoupon)}</strong>
                    </li>
                    <li className="promo-bars-discount">
                      <span>{t("reports.promoPanel.discount")}</span>
                      <strong>{baht(p.discountTotal)}</strong>
                    </li>
                  </ul>
                </div>
              </div>
            );
          })}</article>}
          {show("shipSummary") && <article className="report-panel customer-summary ship-summary"><header><h2 title={t("reports.shipPanel.note")}>{t("reports.shipPanel.title")}</h2>{viewAll("/merchant/shipping", "SHIPMENT_READ")}</header>{gate((d) => {
            const s = d.shippingSummary;
            if (s.paidOrderCount === 0) return empty(t("reports.shipPanel.empty"), t("reports.shipPanel.emptyHint"), <Truck />);
            const rows: [ReactNode, string, string, number | null][] = [
              [<WalletCards />, t("reports.shipPanel.feeCollected"), baht(s.shippingFeeCollected), s.shippingFeeGrowthPct],
              [<Package />, t("reports.shipPanel.freeOrders"), int(s.freeShippingOrderCount), s.freeShippingGrowthPct],
              [<Receipt />, t("reports.shipPanel.avgFee"), baht(s.averageShippingFee), s.averageShippingFeeGrowthPct],
            ];
            return <>{rows.map(([icon, label, value, change]) => <p key={label}><i>{icon}</i><span>{label}</span><b>{value}</b>{growth(change, "strong")}</p>)}</>;
          })}</article>}
      </section>
      )}

      {count("channel", "products", "customers", "promoTypes", "promoTop", "shipCarriers", "shipOps") > 0 && (
        <section className="report-secondary-grid" data-count={count("channel", "products", "customers", "promoTypes", "promoTop", "shipCarriers", "shipOps")}>
          {show("channel") && <article className="report-panel channel-panel"><header><h2>{t("reports.channels.title")}</h2>{viewAll("/merchant/orders", "ORDER_READ")}</header>{gate((d) => d.channels.length === 0
            ? empty(t("reports.channels.empty"), t("reports.channels.emptyHint"), <ShoppingBag />)
            : <div>{d.channels.slice(0, 6).map((c) => <p key={c.channel} title={`${int(c.orders)}`}><i className={`ch-${c.channel.toLowerCase()}`}>{CHANNEL_GLYPH[c.channel] ?? "…"}</i><span>{channelName(c.channel)}</span><b>{baht(c.gmv)}</b><em><i style={{ width: `${Math.min(100, num(c.sharePercent))}%` }} /></em><strong>{dec(c.sharePercent, 0)}%</strong></p>)}</div>)}</article>}
          {show("products") && <article className="report-panel top-products"><header><h2>{t("reports.products.title")}</h2>{viewAll("/merchant/products", "PRODUCT_READ")}</header>{gate((d) => d.topProducts.length === 0
            ? empty(t("reports.products.empty"), t("reports.products.emptyHint"), <Package />)
            : <><div className="product-head"><span>{t("reports.products.rank")}</span><span>{t("reports.products.product")}</span><span>{t("reports.products.quantity")}</span><span>{t("reports.products.sales")}</span></div>{d.topProducts.map((p, i) => <div className="product-row" key={p.productId ?? p.name}><i>{i + 1}</i><ProductThumb path={p.imageUrl} label={t("reports.placeholderProduct")} /><b title={p.name}>{p.name}</b><em>{int(p.quantity)}</em><strong>{baht(p.itemSales)}</strong></div>)}</>)}</article>}
          {show("customers") && <article className="report-panel customer-summary"><header><h2 title={t("reports.customersPanel.note")}>{t("reports.customersPanel.title")}</h2>{viewAll("/merchant/customers", "CUSTOMER_READ")}</header>{gate((d) => {
            const c = d.customerSummary;
            if (c.uniqueCustomers === 0) return empty(t("reports.customersPanel.empty"), t("reports.customersPanel.emptyHint"), <UsersRound />);
            const rows: [ReactNode, string, string, number | null][] = [
              [<UsersRound />, t("reports.customersPanel.total"), int(c.uniqueCustomers), c.uniqueGrowthPct],
              [<UserPlus />, t("reports.customersPanel.fresh"), int(c.newCustomers), c.newGrowthPct],
              [<Repeat />, t("reports.customersPanel.returning"), int(c.returningCustomers), c.returningGrowthPct],
              [<ShoppingBag />, t("reports.customersPanel.ordersPerCustomer"), dec(c.averageOrdersPerCustomer, 2), c.averageOrdersGrowthPct],
              [<Receipt />, t("reports.customersPanel.aov"), baht(c.averageOrderValue), c.averageOrderValueGrowthPct],
            ];
            return <>{rows.map(([icon, label, value, change]) => <p key={label}><i>{icon}</i><span>{label}</span><b>{value}</b>{growth(change, "strong")}</p>)}</>;
          })}</article>}
          {show("promoTypes") && <article className="report-panel category-panel promo-types"><header><h2>{t("reports.promoTypes.title")}</h2></header>{gate((d) => {
            if (d.promoTypes.length === 0) return empty(t("reports.promoTypes.empty"), t("reports.promoTypes.emptyHint"), <TicketPercent />);
            let from = 0;
            const stops = d.promoTypes.map((c, i) => { const to = from + num(c.sharePercent); const s = `var(--rc-${i}) ${from}% ${to}%`; from = to; return s; });
            const background = d.promoTypes.length === 1 ? "var(--rc-0)" : `conic-gradient(${stops.join(",")}${from < 100 ? `,var(--rc-rest) ${from}% 100%` : ""})`;
            const total = d.promoTypes.reduce((a, c) => a + num(c.discountTotal), 0);
            return <div className="category-body"><div className="report-donut" style={{ background }}><b>{baht(total)}<small>{t("reports.promoTypes.centerLabel")}</small></b></div><ul>{d.promoTypes.map((c, i) => <li key={c.promoType} title={`${baht(c.discountTotal)} · ${int(c.redemptions)}`}><i className={`color-${i}`} /><span>{t(`reports.promoTypes.names.${c.promoType}`, { defaultValue: c.promoType })}</span><b>{dec(c.sharePercent)}%</b></li>)}</ul></div>;
          })}</article>}
          {show("promoTop") && <article className="report-panel top-products"><header><h2>{t("reports.promoTop.title")}</h2>{viewAll("/merchant/promotions", "PROMOTION_READ")}</header>{gate((d) => d.topCoupons.length === 0
            ? empty(t("reports.promoTop.empty"), t("reports.promoTop.emptyHint"), <TicketPercent />)
            : <><div className="product-head"><span>{t("reports.products.rank")}</span><span>{t("reports.promoTop.code")}</span><span>{t("reports.promoTop.uses")}</span><span>{t("reports.promoTop.discount")}</span></div>{d.topCoupons.map((c, i) => <div className="product-row" key={`${c.code}-${i}`}><i>{i + 1}</i><span className="promo-code-chip" title={t(`reports.promoTypes.names.${c.promoType}`, { defaultValue: c.promoType })}>{c.promoType.slice(0, 1)}</span><b title={c.code}>{c.code}</b><em>{int(c.redemptions)}</em><strong>{baht(c.discountTotal)}</strong></div>)}</>)}</article>}
          {show("shipCarriers") && <article className="report-panel channel-panel"><header><h2>{t("reports.carriers.title")}</h2></header>{gate((d) => d.carriers.length === 0
            ? empty(t("reports.carriers.empty"), t("reports.carriers.emptyHint"), <Truck />)
            : <div>{d.carriers.slice(0, 6).map((c) => <p key={c.carrier} title={`${int(c.orders)}`}><i className={`ch-${c.carrier.toLowerCase()}`}>{c.carrier.slice(0, 2)}</i><span>{c.carrier === "UNKNOWN" ? t("reports.channels.names.UNKNOWN") : c.carrier}</span><b>{baht(c.shippingFee)}</b><em><i style={{ width: `${Math.min(100, num(c.sharePercent))}%` }} /></em><strong>{dec(c.sharePercent, 0)}%</strong></p>)}</div>)}</article>}
          {show("shipOps") && <article className="report-panel customer-summary"><header><h2 title={t("reports.shipOps.note")}>{t("reports.shipOps.title")}</h2>{viewAll("/merchant/shipping", "SHIPMENT_READ")}</header>{gate((d) => {
            const o = d.shipmentOps;
            if (o.dispatchedCount + o.deliveredCount + o.failedCount + o.returnedCount === 0) return empty(t("reports.shipOps.empty"), t("reports.shipOps.emptyHint"), <Package />);
            const rows: [ReactNode, string, string, number | null][] = [
              [<Package />, t("reports.shipOps.dispatched"), int(o.dispatchedCount), o.dispatchedGrowthPct],
              [<ShoppingBag />, t("reports.shipOps.delivered"), int(o.deliveredCount), o.deliveredGrowthPct],
              [<FileText />, t("reports.shipOps.failed"), int(o.failedCount), o.failedGrowthPct],
              [<Repeat />, t("reports.shipOps.returned"), int(o.returnedCount), o.returnedGrowthPct],
            ];
            return <>{rows.map(([icon, label, value, change]) => <p key={label}><i>{icon}</i><span>{label}</span><b>{value}</b>{growth(change, "strong")}</p>)}</>;
          })}</article>}
      </section>
      )}

      {count("fee", "feeRate", "advice") > 0 && (
        <section className="report-bottom" data-count={count("fee", "feeRate", "advice")}>
          {show("fee") && <article><h2>{t("reports.fee.title")}</h2><div><span className="coin-stack">●</span><p>{t("reports.fee.total")}<b>{kpi((d) => baht(d.kpis.estimatedFee))}</b></p>{data && canRead ? growth(data.kpis.estimatedFeeGrowthPct, "strong") : null}</div></article>}
          {show("feeRate") && <article className="fee-rate"><div className="mini-donut" style={{ background: `conic-gradient(var(--rp-blue) 0 ${num(data?.feeEstimateRate ?? 0.05) * 100}%, var(--rp-track) ${num(data?.feeEstimateRate ?? 0.05) * 100}%)` }} /><p>{t("reports.fee.rateTitle")}<b>{dec(num(data?.feeEstimateRate ?? 0.05) * 100, 0)}%</b></p><span title={t("reports.fee.note")}>{t("reports.fee.rateOf")}</span></article>}
          {show("advice") && <article className="report-advice"><Lightbulb /><h2>{t("reports.advice.title")}</h2>{(() => {
            if (!canRead) return <p>{t("reports.noPermission")}</p>;
            if (state.status === "loading") return <p>{t("reports.advice.loading")}</p>;
            if (!data) return <p>{t("reports.advice.unavailable")}</p>;
            const [first, second] = adviceText(data);
            return <p>{first}{second ? <><br />{second}</> : null}</p>;
          })()}{activeTab === "shipping"
            ? (canInShop("SHIPMENT_READ")
              ? <Link className="report-advice-link" to="/merchant/shipping" aria-label={t("reports.advice.openShipping")} title={t("reports.advice.openShipping")}>›</Link>
              : <button type="button" aria-label={t("reports.advice.openShipping")} disabled title={t("reports.advice.openShippingDenied")}>›</button>)
            : (canInShop("PROMOTION_READ")
              ? <Link className="report-advice-link" to="/merchant/promotions" aria-label={t("reports.advice.open")} title={t("reports.advice.open")}>›</Link>
              : <button type="button" aria-label={t("reports.advice.open")} disabled title={t("reports.advice.openDenied")}>›</button>)}</article>}
        </section>
      )}
    </section>{notice && <div className="merchant-toast" role="status">{notice}</div>}
  </main>;
}
