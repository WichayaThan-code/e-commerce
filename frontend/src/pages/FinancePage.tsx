import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  BarChart3,
  Building2,
  CalendarDays,
  Download,
  FileText,
  Landmark,
  Lock,
  ShoppingBag,
  TrendingUp,
  WalletCards,
} from "lucide-react";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import MerchantTopbarActions from "../components/merchant/MerchantTopbarActions";
import MerchantPagination from "../components/merchant/MerchantPagination";
import MerchantPanelEmpty from "../components/merchant/MerchantPanelEmpty";
import DateRangePicker from "../components/form/DateRangePicker";
import { localeTag } from "../i18n";
import { useAuth } from "../contexts/AuthContext";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { hasPlatformAccess, hasMerchantAccess, hasShopPermission } from "../services/permissions";
import { usePermissions } from "../hooks/usePermissions";
import { numberOf } from "../components/merchant/orderFormat";
import {
  merchantOrderApi,
  merchantReportsApi,
  merchantFinanceApi,
  type MerchantDashboardRange,
  type MerchantReportOverview,
  type MerchantReportPeriod,
  type OrderPage,
  type OrderSummary,
} from "../services/api";
import FinanceWithdrawPanel from "../components/merchant/FinanceWithdrawPanel";
import FinanceInvoicePanel, { type InvoiceExportFilters } from "../components/merchant/FinanceInvoicePanel";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/finance.css";
import MerchantSidebar from "../components/merchant/MerchantSidebar";
import MerchantBrand from "../components/merchant/MerchantBrand";

type RangeKey = MerchantDashboardRange | "custom";
type FinanceTab = "overview" | "ledger" | "fees" | "transfers" | "invoices";
type PanelKey =
  | "kpis" | "chart" | "balance" | "transactions" | "transferSummary" | "documents"
  | "feeDetail" | "transfers" | "invoices";

const RANGES: [RangeKey, string, string][] = [
  ["today", "today", "todayFull"],
  ["7d", "d7", "d7Full"],
  ["30d", "d30", "d30Full"],
  ["1y", "y1", "y1Full"],
  ["custom", "custom", "customFull"],
];

const TABS: FinanceTab[] = ["overview", "ledger", "fees", "transfers", "invoices"];

const TAB_PANELS: Record<FinanceTab, PanelKey[]> = {
  overview: ["kpis", "chart", "balance", "transactions", "transferSummary", "documents"],
  ledger: ["kpis", "transactions"],
  fees: ["kpis", "chart", "feeDetail"],
  transfers: ["transfers"],
  invoices: ["invoices"],
};

const FEE_RATE = 0.05;
const LEDGER_SIZES = [10, 20, 50];
const bkkDate = (d: Date) => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Bangkok" }).format(d);
const num = (v: unknown) => numberOf(v as number);

function niceMax(v: number) {
  if (v <= 0) return 5000;
  const step = Math.pow(10, Math.floor(Math.log10(v / 5)));
  for (const m of [1, 2, 2.5, 5, 10]) if (m * step * 5 >= v) return m * step * 5;
  return Math.ceil(v / 5) * 5;
}

type ReportState = { status: "idle" | "loading" | "ready" | "error"; data: MerchantReportOverview | null };
type LedgerState = { status: "idle" | "loading" | "ready" | "error"; page: OrderPage | null };

export default function FinancePage() {
  const { t } = useTranslation("merchant");
  const { user, loading } = useAuth();
  const { ready, shopId } = useActiveShop();
  const { canInShop, deniedTitle } = usePermissions();
  const canExport = canInShop("REPORT_EXPORT");
  const canFinanceExport = canInShop("FINANCE_EXPORT") || canExport;
  const canFinanceRead = canInShop("FINANCE_READ") || canInShop("REPORT_READ");
  const canWithdraw = canInShop("FINANCE_WITHDRAW");
  const canInvoiceManage = canInShop("FINANCE_INVOICE_MANAGE");
  const canRead = !!user && hasShopPermission(user, "REPORT_READ", shopId);
  const canOrders = canInShop("ORDER_READ");

  const [tab, setTab] = useState<FinanceTab>("overview");
  const [range, setRange] = useState<RangeKey>("30d");
  const [customFrom, setCustomFrom] = useState(() => bkkDate(new Date(Date.now() - 6 * 86400000)));
  const [customTo, setCustomTo] = useState(() => bkkDate(new Date()));
  const [notice, setNotice] = useState("");
  const [report, setReport] = useState<ReportState>({ status: "idle", data: null });
  const [ledger, setLedger] = useState<LedgerState>({ status: "idle", page: null });
  const [ledgerPage, setLedgerPage] = useState(0);
  const [ledgerSize, setLedgerSize] = useState(10);
  const [reloadTick, setReloadTick] = useState(0);
  const [exporting, setExporting] = useState(false);
  const reportReq = useRef(0);
  const ledgerReq = useRef(0);
  const invoiceExportFiltersRef = useRef<InvoiceExportFilters>({});

  const customValid = !!customFrom && !!customTo && customFrom <= customTo;
  const periodKey = range === "custom"
    ? (customValid ? JSON.stringify({ from: customFrom, to: customTo }) : "")
    : JSON.stringify({ range });
  const period = periodKey ? JSON.parse(periodKey) as MerchantReportPeriod : null;
  /** Order list API accepts only from/to (YYYY-MM-DD), so presets are expanded in Asia/Bangkok. */
  const orderDates = (() => {
    if (!period) return null;
    if (period.from && period.to) return { from: period.from, to: period.to };
    const today = bkkDate(new Date());
    const end = new Date(`${today}T12:00:00+07:00`);
    const start = new Date(end);
    if (period.range === "today") { /* same day */ }
    else if (period.range === "7d") start.setDate(start.getDate() - 6);
    else if (period.range === "30d") start.setDate(start.getDate() - 29);
    else if (period.range === "1y") start.setFullYear(start.getFullYear() - 1);
    else return null;
    return { from: bkkDate(start), to: today };
  })();

  useEffect(() => {
    if (!ready) return;
    if (shopId == null || !canRead || !periodKey) {
      reportReq.current += 1;
      setReport({ status: "idle", data: null });
      return;
    }
    const id = ++reportReq.current;
    const controller = new AbortController();
    setReport({ status: "loading", data: null });
    merchantReportsApi.overview(shopId, JSON.parse(periodKey) as MerchantReportPeriod, controller.signal)
      .then((data) => { if (reportReq.current === id) setReport({ status: "ready", data }); })
      .catch(() => { if (!controller.signal.aborted && reportReq.current === id) setReport({ status: "error", data: null }); });
    return () => controller.abort();
  }, [ready, shopId, canRead, periodKey, reloadTick]);

  useEffect(() => {
    if (!ready) return;
    const needLedger = tab === "overview" || tab === "ledger";
    if (!needLedger || shopId == null || !canOrders || !orderDates) {
      ledgerReq.current += 1;
      setLedger({ status: "idle", page: null });
      return;
    }
    const id = ++ledgerReq.current;
    const controller = new AbortController();
    setLedger({ status: "loading", page: null });
    merchantOrderApi.list(shopId, {
      page: ledgerPage,
      size: ledgerSize,
      sort: "placedAt",
      direction: "desc",
      paymentStatus: "PAID",
      from: orderDates.from,
      to: orderDates.to,
    })
      .then((page) => { if (ledgerReq.current === id) setLedger({ status: "ready", page }); })
      .catch(() => { if (!controller.signal.aborted && ledgerReq.current === id) setLedger({ status: "error", page: null }); });
    return () => controller.abort();
  }, [ready, shopId, canOrders, orderDates?.from, orderDates?.to, tab, ledgerPage, ledgerSize, reloadTick]);

  useEffect(() => { setLedgerPage(0); }, [periodKey, tab, ledgerSize]);
  useEffect(() => { setLedgerSize(tab === "overview" ? 5 : 10); }, [tab]);

  if (loading) return <main className="auth-loading">{t("finance.authLoading")}</main>;
  if (!user) { window.location.replace("/login?next=/merchant/finance"); return null; }
  if ((hasPlatformAccess(user) && !hasMerchantAccess(user))) { window.location.replace("/admin"); return null; }
  if (!hasMerchantAccess(user)) { window.location.replace("/"); return null; }

  const loc = localeTag();
  const data = report.data;
  const panels = TAB_PANELS[tab];
  const show = (panel: PanelKey) => panels.includes(panel);
  const notify = (text: string) => { setNotice(text); window.setTimeout(() => setNotice(""), 1900); };

  const baht = (v: unknown) => new Intl.NumberFormat(loc, { style: "currency", currency: "THB", maximumFractionDigits: 2 }).format(num(v));
  const shortBaht = (v: number) => {
    if (v >= 1_000_000) return `฿${(v / 1_000_000).toLocaleString(loc, { maximumFractionDigits: 1 })}M`;
    if (v >= 1000) return `฿${(v / 1000).toLocaleString(loc, { maximumFractionDigits: 1 })}k`;
    return `฿${v.toLocaleString(loc, { maximumFractionDigits: 0 })}`;
  };
  const dec = (v: unknown, digits = 1) => num(v).toLocaleString(loc, { maximumFractionDigits: digits, minimumFractionDigits: digits });
  const growth = (value: number | null | undefined) => {
    if (value == null) return <i className="growth none">{t("finance.growthNone")}</i>;
    const v = num(value);
    const text = Math.abs(v) > 999 ? "999+" : dec(Math.abs(v));
    return <i className={v < 0 ? "growth down" : v > 0 ? "growth up" : "growth none"}>{v > 0 ? "↑ " : v < 0 ? "↓ " : ""}{text}%</i>;
  };
  const rangeLong = t(`finance.range.${RANGES.find(([k]) => k === range)?.[2] ?? "d30Full"}`);
  const when = (iso?: string | null) => {
    if (!iso) return "—";
    const d = new Date(iso);
    return new Intl.DateTimeFormat(loc, { timeZone: "Asia/Bangkok", day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }).format(d);
  };
  const methodLabel = (m?: string | null) => {
    if (!m) return t("finance.ledger.paymentFallback");
    return t(`finance.ledger.methods.${m}`, { defaultValue: m });
  };
  const orderKind = (o: OrderSummary) => {
    if (o.status === "REFUNDED" || o.paymentStatus === "REFUNDED") return t("finance.ledger.refund");
    return t("finance.ledger.payment", { method: methodLabel(o.paymentMethod) });
  };
  const feeOf = (gmv: number) => Math.round(gmv * FEE_RATE * 100) / 100;
  const empty = (title: string, hint?: string, icon: ReactNode = <BarChart3 />) => (
    <MerchantPanelEmpty icon={icon} title={title} hint={hint} />
  );

  const kpi = (render: (d: MerchantReportOverview) => ReactNode) => {
    if (!canRead) return "—";
    if (report.status === "loading") return "…";
    if (report.status !== "ready" || !data) return "—";
    return render(data);
  };

  const download = async () => {
    if (shopId == null) { notify(t("finance.export.needRange")); return; }
    if (tab === "transfers") {
      if (!canFinanceExport) { notify(t("finance.noPermission")); return; }
      setExporting(true);
      try {
        await merchantFinanceApi.exportWithdrawalsCsv(shopId, { dateField: "requestedAt" });
        notify(t("finance.payout.exportDone"));
      } catch {
        notify(t("finance.payout.exportFailed"));
      } finally {
        setExporting(false);
      }
      return;
    }
    if (tab === "invoices") {
      if (!canFinanceExport) { notify(t("finance.noPermission")); return; }
      setExporting(true);
      try {
        const filters = invoiceExportFiltersRef.current;
        await merchantFinanceApi.exportInvoicesCsv(shopId, {
          docType: filters.docType,
          status: filters.status,
          q: filters.q,
          from: filters.from,
          to: filters.to,
          dateField: "createdAt",
        });
        notify(t("finance.invoices.exportDone"));
      } catch {
        notify(t("finance.invoices.exportFailed"));
      } finally {
        setExporting(false);
      }
      return;
    }
    if (!periodKey) { notify(t("finance.export.needRange")); return; }
    const exportShopId = shopId;
    const exportPeriod = JSON.parse(periodKey) as MerchantReportPeriod;
    const exportTab = tab === "fees" ? "fees" : tab === "ledger" ? "orders" : "overview";
    setExporting(true);
    try {
      await merchantReportsApi.exportXlsx(exportShopId, exportPeriod, exportTab);
      if (shopId !== exportShopId || periodKey !== JSON.stringify(exportPeriod)) {
        notify(t("finance.export.stale"));
        return;
      }
      notify(t("finance.export.done"));
    } catch {
      notify(t("finance.export.failed"));
    } finally {
      setExporting(false);
    }
  };

  const alertText = !periodKey
    ? (customFrom && customTo ? t("finance.range.invalidRange") : t("finance.range.pickDates"))
    : shopId == null && ready ? t("finance.noShop")
    : !canRead ? t("finance.noPermission")
    : "";

  const seriesChart = (d: MerchantReportOverview) => {
    const series = d.series;
    const n = series.length;
    const gmv = series.map((p) => num(p.gmv));
    const income = series.map((p) => num(p.estimatedNet));
    const fee = series.map((p) => num(p.estimatedFee));
    const max = niceMax(Math.max(0, ...gmv));
    const every = n > 16 ? Math.ceil(n / 8) : 1;
    const barW = n <= 8 ? 12 : n <= 14 ? 9 : n <= 20 ? 6 : 4;
    const h = (v: number) => `${Math.max(0, Math.round((v / max) * 150))}px`;
    const label = (start: string) => {
      const day = new Date(`${start}T00:00:00+07:00`);
      return new Intl.DateTimeFormat(loc, { timeZone: "Asia/Bangkok", day: "numeric", month: "short" }).format(day);
    };
    return (
      <div className="finance-graph">
        <div className="f-y">{[5, 4, 3, 2, 1, 0].map((k) => <span key={k}>{shortBaht((max / 5) * k)}</span>)}</div>
        <div className="f-plot">
          {series.map((p, i) => (
            <div className="f-day" key={p.start} style={{ width: `${Math.min(12, 92 / n)}%` }} title={`${baht(gmv[i])}`}>
              <div>
                <i style={{ height: h(gmv[i]), width: barW }} />
                <i style={{ height: h(income[i]), width: barW }} />
                <i style={{ height: fee[i] > 0 ? `${Math.max(3, Math.round((fee[i] / max) * 150))}px` : "0px", width: barW }} />
              </div>
              <small style={i % every === 0 ? undefined : { visibility: "hidden" }}>{label(p.start)}</small>
            </div>
          ))}
        </div>
      </div>
    );
  };

  const transactionRows = () => {
    if (!canOrders) return empty(t("finance.ledger.noPermission"), t("finance.ledger.noPermissionHint"), <Lock />);
    if (ledger.status === "loading") return empty(t("finance.loading"), t("finance.loadingHint"), <BarChart3 />);
    if (ledger.status === "error") return (
      <div className="finance-alert">
        <span>{t("finance.error.ledger")}</span>
        <button type="button" onClick={() => setReloadTick((n) => n + 1)}>{t("finance.error.retry")}</button>
      </div>
    );
    const page = ledger.page;
    if (!page || page.items.length === 0) return empty(t("finance.ledger.empty"), t("finance.ledger.emptyHint"), <FileText />);
    return (
      <>
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>{t("finance.ledger.date")}</th>
              <th>{t("finance.ledger.item")}</th>
              <th>{t("finance.ledger.order")}</th>
              <th>{t("finance.ledger.sales")}</th>
              <th>{t("finance.ledger.fee")}</th>
              <th>{t("finance.ledger.net")}</th>
              <th>{t("finance.ledger.status")}</th>
              <th>{t("finance.ledger.proof")}</th>
            </tr>
          </thead>
          <tbody>
            {page.items.map((o, index) => {
              const gmv = num(o.grandTotal);
              const refunded = o.status === "REFUNDED" || o.paymentStatus === "REFUNDED";
              const sign = refunded ? -1 : 1;
              const fee = feeOf(Math.abs(gmv));
              const net = Math.abs(gmv) - fee;
              const cls = refunded ? "negative" : "";
              return (
                <tr key={o.id}>
                  <td>{ledgerPage * page.size + index + 1}</td>
                  <td><span>{when(o.placedAt)}</span></td>
                  <td>{orderKind(o)}</td>
                  <td>{o.orderNumber}</td>
                  <td className={cls}>{baht(sign * Math.abs(gmv))}</td>
                  <td className={cls}>{baht(sign * fee)}</td>
                  <td className={cls}>{baht(sign * net)}</td>
                  <td><span className={`finance-status ${refunded ? "refund" : "success"}`}>{refunded ? t("finance.ledger.statusRefunded") : t("finance.ledger.statusPaid")}</span></td>
                  <td>
                    {canOrders
                      ? <Link className="finance-proof" to={`/merchant/orders/${o.id}`} aria-label={t("finance.ledger.openOrder", { number: o.orderNumber })}><FileText /></Link>
                      : <button type="button" disabled title={t("finance.viewAllDenied")}><FileText /></button>}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <MerchantPagination
          className="finance-pagination"
          page={ledgerPage}
          pageCount={Math.max(1, page.totalPages)}
          size={ledgerSize}
          total={page.totalItems}
          sizeOptions={LEDGER_SIZES}
          onPageChange={setLedgerPage}
          onSizeChange={(next) => { setLedgerSize(next); setLedgerPage(0); }}
        />
        <p className="finance-ledger-note">{t("finance.ledger.feeNote")}</p>
      </>
    );
  };

  return (
    <main className="merchant-shell pm-shell finance-shell">
      <header className="merchant-topbar"><MerchantBrand /><MerchantTopbarSearch /><MerchantTopbarActions /></header>
      <MerchantSidebar active="finance" />

    <section className="pm-content finance-content">
        <header className="pm-heading finance-heading">
          <div>
            <h1><WalletCards />{t("finance.title")}</h1>
            <p>{t("finance.subtitle")}</p>
          </div>
          <div className="finance-actions">
            <div className="merchant-range">
              <div className="merchant-range-seg" role="tablist" aria-label={t("finance.range.label")}>
                <CalendarDays />
                {RANGES.map(([key, short]) => (
                  <button key={key} type="button" role="tab" aria-selected={range === key} className={range === key ? "active" : ""} onClick={() => setRange(key)}>
                    {t(`finance.range.${short}`)}
                  </button>
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
              ) : <small>{rangeLong} · {t("finance.range.thaiTime")}</small>}
            </div>
            <button
              type="button"
              disabled={(tab === "transfers" || tab === "invoices") ? (!canFinanceExport || exporting) : (!canExport || exporting || !periodKey)}
              title={(tab === "transfers" || tab === "invoices")
                ? (canFinanceExport
                  ? (tab === "invoices" ? t("finance.invoices.export") : t("finance.payout.export"))
                  : deniedTitle(canFinanceExport))
                : (canExport ? t("finance.export.xlsxNote") : deniedTitle(canExport))}
              className="primary"
              onClick={() => void download()}
            >
              <Download />{exporting
                ? t("finance.export.busy")
                : tab === "transfers" ? t("finance.payout.export")
                : tab === "invoices" ? t("finance.invoices.export")
                : t("finance.export.button")}
            </button>
          </div>
        </header>

        <nav className="finance-tabs" aria-label={t("finance.tabsLabel")}>
          {TABS.map((key) => (
            <button key={key} type="button" className={tab === key ? "active" : ""} aria-current={tab === key ? "page" : undefined} onClick={() => setTab(key)}>
              {t(`finance.tabs.${key}`)}
            </button>
          ))}
        </nav>

        {(report.status === "error" || alertText) && (
          <div className="finance-alert" role="alert">
            <span>{report.status === "error" ? t("finance.error.generic") : alertText}</span>
            {report.status === "error" && <button type="button" onClick={() => setReloadTick((n) => n + 1)}>{t("finance.error.retry")}</button>}
          </div>
        )}

        {show("kpis") && (
          <section className="finance-kpis" aria-busy={report.status === "loading"}>
            <article className="sales"><span><TrendingUp /></span><div><small>{t("finance.kpi.gmv")}</small><b>{kpi((d) => <>{baht(d.kpis.gmv)} {growth(d.kpis.gmvGrowthPct)}</>)}</b><em>{t("finance.kpi.gmvNote")}</em></div></article>
            <article className="income"><span><WalletCards /></span><div><small>{t("finance.kpi.income")}</small><b>{kpi((d) => <>{baht(d.kpis.estimatedNet)} {growth(d.kpis.estimatedNetGrowthPct)}</>)}</b><em>{t("finance.kpi.incomeNote")}</em></div></article>
            <article className="fee"><span>●</span><div><small>{t("finance.kpi.fee")}</small><b>{kpi((d) => <>{baht(d.kpis.estimatedFee)} {growth(d.kpis.estimatedFeeGrowthPct)}</>)}</b><em>{t("finance.kpi.feeNote")}</em></div></article>
            <article className="pending"><span><Building2 /></span><div><small>{t("finance.kpi.refunds")}</small><b>{kpi((d) => <>{baht(d.kpis.refundedAmount)} {growth(d.kpis.refundedGrowthPct)}</>)}</b><em>{t("finance.kpi.refundsNote")}</em></div></article>
          </section>
        )}

        {show("transfers") && shopId != null && canFinanceRead && (
          <FinanceWithdrawPanel
            shopId={shopId}
            canWithdraw={canWithdraw}
            canExport={canFinanceExport}
            localeTag={loc}
            deniedTitle={deniedTitle}
            notify={notify}
            mode="transfers"
          />
        )}
        {show("transfers") && !canFinanceRead && (
          <article className="finance-panel finance-unavailable">
            <header><h2>{t("finance.tabs.transfers")}</h2></header>
            {empty(t("finance.noPermission"), t("finance.payout.needFinanceRead"), <Lock />)}
          </article>
        )}
        {show("invoices") && shopId != null && canFinanceRead && (
          <FinanceInvoicePanel
            shopId={shopId}
            canManage={canInvoiceManage}
            localeTag={loc}
            deniedTitle={deniedTitle}
            notify={notify}
            exportFiltersRef={invoiceExportFiltersRef}
          />
        )}
        {show("invoices") && !canFinanceRead && (
          <article className="finance-panel finance-unavailable">
            <header><h2>{t("finance.tabs.invoices")}</h2></header>
            {empty(t("finance.noPermission"), t("finance.payout.needFinanceRead"), <Lock />)}
          </article>
        )}

        {(show("chart") || show("feeDetail")) && (
          <div className="finance-top-grid" data-single={!show("feeDetail") ? "1" : undefined}>
            {show("chart") && (
              <article className="finance-panel finance-chart">
                <header>
                  <h2>{t("finance.chart.title")}</h2>
                  <div className="finance-legend">
                    <span><i />{t("finance.chart.legendGmv")}</span>
                    <span><i />{t("finance.chart.legendIncome")}</span>
                    <span><i />{t("finance.chart.legendFee")}</span>
                  </div>
                  {canInShop("REPORT_READ")
                    ? <Link to="/merchant/reports">{t("finance.chart.openReports")}</Link>
                    : <button type="button" disabled title={t("finance.viewAllDenied")}>{t("finance.chart.openReports")}</button>}
                </header>
                {!canRead ? empty(t("finance.noPermission"), t("finance.noPermissionHint"), <Lock />)
                  : report.status === "loading" ? empty(t("finance.loading"), t("finance.loadingHint"), <BarChart3 />)
                  : report.status !== "ready" || !data ? empty(
                    report.status === "error" ? t("finance.error.title") : "—",
                    report.status === "error" ? t("finance.error.hint") : undefined,
                    <BarChart3 />,
                  )
                  : num(data.kpis.gmv) <= 0 ? empty(t("finance.chart.empty"), t("finance.chart.emptyHint"), <TrendingUp />)
                  : seriesChart(data)}
              </article>
            )}
            {show("feeDetail") && (
              <aside className="finance-panel finance-rail fee-detail">
                <header><h2>{t("finance.feeDetail.title")}</h2></header>
                <div className="finance-rail-hero">
                  <small>{t("finance.feeDetail.total")}</small>
                  <b>{kpi((d) => baht(d.kpis.estimatedFee))}</b>
                  <em>{t("finance.feeDetail.rate")}</em>
                </div>
                <div className="finance-rail-metric">
                  <span><WalletCards /></span>
                  <p>
                    <small>{t("finance.feeDetail.net")}</small>
                    <strong>{kpi((d) => baht(d.kpis.estimatedNet))}</strong>
                    <em>{t("finance.feeDetail.netNote")}</em>
                  </p>
                </div>
                <p className="finance-ledger-note">{t("finance.feeDetail.disclaimer")}</p>
              </aside>
            )}
          </div>
        )}

        {(show("transactions") || show("balance") || show("transferSummary") || show("documents")) && (
          <div className="finance-bottom-grid" data-single={!show("balance") && !show("transferSummary") && !show("documents") ? "1" : undefined}>
            {show("transactions") && (
              <article className={`finance-panel transaction-card${tab === "ledger" ? " is-ledger" : ""}`}>
                <header>
                  <h2>{tab === "ledger" ? t("finance.ledger.titleFull") : t("finance.ledger.title")}</h2>
                  {canOrders
                    ? <Link to="/merchant/orders">{t("finance.viewAll")}</Link>
                    : <button type="button" disabled title={t("finance.viewAllDenied")}>{t("finance.viewAll")}</button>}
                </header>
                {transactionRows()}
              </article>
            )}
            {(show("balance") || show("transferSummary") || show("documents")) && (
              <aside className="finance-panel finance-rail">
                {show("balance") && (
                  <>
                    <section className="finance-rail-block">
                      <header><h2>{t("finance.balance.title")}</h2></header>
                      <div className="finance-rail-hero">
                        <small>{t("finance.balance.estimatedNet")}</small>
                        <b>{kpi((d) => baht(d.kpis.estimatedNet))}</b>
                        <em>{t("finance.balance.estimatedNetNote")}</em>
                      </div>
                    </section>
                    {shopId != null && canFinanceRead ? (
                      <FinanceWithdrawPanel
                        shopId={shopId}
                        canWithdraw={canWithdraw}
                        canExport={canFinanceExport}
                        localeTag={loc}
                        deniedTitle={deniedTitle}
                        notify={notify}
                        mode="balance"
                      />
                    ) : null}
                  </>
                )}
                {(show("transferSummary") || show("documents")) && (
                  <section className="finance-rail-block finance-rail-soon">
                    <header><h2>{t("finance.rail.soonTitle")}</h2></header>
                    <ul>
                      {show("transferSummary") && (
                        <li>
                          <i><Landmark /></i>
                          <div>
                            <b>{t("finance.transfers.title")}</b>
                            <small>{t("finance.payout.goTransfers")}</small>
                          </div>
                          <button type="button" onClick={() => setTab("transfers")}>{t("finance.payout.openTransfers")}</button>
                        </li>
                      )}
                      {show("documents") && (
                        <li>
                          <i><FileText /></i>
                          <div>
                            <b>{t("finance.documents.title")}</b>
                            <small>{t("finance.invoices.openHint")}</small>
                          </div>
                          <button type="button" onClick={() => setTab("invoices")}>{t("finance.invoices.openTab")}</button>
                        </li>
                      )}
                    </ul>
                  </section>
                )}
              </aside>
            )}
          </div>
        )}
      </section>
      {notice && <div className="merchant-toast" role="status">{notice}</div>}
  </main>
  );
}
