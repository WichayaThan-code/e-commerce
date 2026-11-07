import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  BarChart3,
  CalendarDays,
  ChevronDown,
  ShoppingBag,
  ShoppingCart,
  UsersRound,
  WalletCards,
} from "lucide-react";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import MerchantTopbarActions from "../components/merchant/MerchantTopbarActions";
import DateRangePicker from "../components/form/DateRangePicker";
import DashboardWelcome from "../components/dashboard/DashboardWelcome";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess, hasShopPermission } from "../services/permissions";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { merchantApi, merchantDashboardApi, merchantOrderApi, productApi, authorizedBlob, mediaUrl, type MerchantDashboardInsights, type MerchantDashboardRange, type OrderSummary } from "../services/api";
import { numberOf } from "../components/merchant/orderFormat";
import { usePermissions } from "../hooks/usePermissions";
import { dashboardGreetingName, shopInitials, shopStatusChip } from "../utils/dashboardGreeting";
import "../styles/merchant-dashboard.css";
import MerchantSidebar from "../components/merchant/MerchantSidebar";
import MerchantBrand from "../components/merchant/MerchantBrand";


/** A12: platform fee is not stored per order yet, so fee/net are an estimate. */
const FEE_RATE = 0.05;
const TH_MONTH = new Intl.DateTimeFormat("th-TH", { timeZone: "UTC", month: "short" });
const TH_DAY = new Intl.DateTimeFormat("th-TH", { timeZone: "UTC", day: "numeric", month: "short" });
const TH_FULL = new Intl.DateTimeFormat("th-TH", { timeZone: "UTC", day: "numeric", month: "short", year: "numeric" });
const ymd = (v: string) => new Date(`${v}T00:00:00Z`);
function bucketLabel(start: string, bucket: string) {
  const d = ymd(start);
  return bucket === "month" ? `${TH_MONTH.format(d)} ${String(d.getUTCFullYear() + 543).slice(-2)}` : TH_DAY.format(d);
}
function bucketTitle(start: string, end: string, bucket: string) {
  if (bucket === "day" || start === end) return TH_FULL.format(ymd(start));
  return `${TH_DAY.format(ymd(start))} – ${TH_FULL.format(ymd(end))}`;
}
/** Round the chart max up to a "nice" number so the 5 grid lines read well. */
function niceMax(v: number) {
  if (v <= 0) return 5000;
  const step = Math.pow(10, Math.floor(Math.log10(v / 5)));
  for (const m of [1, 2, 2.5, 5, 10]) if (m * step * 5 >= v) return m * step * 5;
  return Math.ceil(v / 5) * 5;
}
const shortBaht = (v: number) => v === 0 ? "0" : "฿ " + v.toLocaleString("th-TH", { maximumFractionDigits: 0 });
const EmptyNote = ({ children }: { children: React.ReactNode }) => (
  <p className="merchant-empty-note" style={{ margin: 0, padding: "28px 16px", textAlign: "center", color: "#6881a6", fontSize: 13 }}>{children}</p>
);

const STATUS_VIEW: Record<string, [string, string]> = {
  PENDING_PAYMENT: ["รอชำระเงิน", "รอชำระเงิน"],
  PAID_AWAITING_MERCHANT_CONFIRM: ["ชำระแล้ว รอยืนยัน", "ชำระแล้ว"],
  MERCHANT_CONFIRMED: ["ยืนยันแล้ว", "รอจัดส่ง"],
  READY_TO_PACK: ["รอแพ็ก", "รอจัดส่ง"],
  PACKING: ["กำลังแพ็ก", "รอจัดส่ง"],
  PACKED: ["แพ็กแล้ว รอจัดส่ง", "รอจัดส่ง"],
  DISPATCHED: ["กำลังจัดส่ง", "จัดส่งแล้ว"],
  DELIVERED: ["จัดส่งแล้ว", "จัดส่งแล้ว"],
  COMPLETED: ["สำเร็จ", "จัดส่งแล้ว"],
  CANCELLED: ["ยกเลิก", "ยกเลิก"],
  REFUNDED: ["คืนเงินแล้ว", "ยกเลิก"],
  REFUND_REQUESTED: ["ขอคืนเงิน", "ยกเลิก"],
};
function statusView(o: OrderSummary): [string, string] {
  if (o.awaitingAcceptance) return ["รอยืนยันออเดอร์", "รอชำระเงิน"];
  return STATUS_VIEW[o.status] ?? [o.status, "รอชำระเงิน"];
}
const baht = (v: unknown) => "฿ " + numberOf(v as number).toLocaleString("th-TH", { maximumFractionDigits: 2 });
const thaiDateTime = (v?: string | null) => v ? new Intl.DateTimeFormat("th-TH", { timeZone: "Asia/Bangkok", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date(v)) : "-";
const bkkDate = (d: Date) => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Bangkok" }).format(d);
type RangeKey = MerchantDashboardRange | "custom";
const RANGES: [RangeKey, string, string][] = [
  ["today", "วันนี้", "วันนี้"],
  ["7d", "7 วัน", "7 วันล่าสุด"],
  ["30d", "30 วัน", "30 วันล่าสุด"],
  ["1y", "1 ปี", "1 ปีล่าสุด"],
  ["custom", "กำหนดเอง", "ช่วงที่เลือก"],
];
/** % change vs previous same-length period; null when there is no base to compare. */
function pctChange(cur: number | null, prev: number | null): number | null {
  if (cur == null || prev == null || prev === 0) return null;
  return Math.round(((cur - prev) / prev) * 1000) / 10;
}
function Trend({ cur, prev }: { cur: number | null; prev: number | null }) {
  const p = pctChange(cur, prev);
  if (p == null) return null;
  return <i style={p < 0 ? { color: "#f32657" } : undefined} title="เทียบกับช่วงก่อนหน้า">{p > 0 ? "▲" : p < 0 ? "▼" : ""} {Math.abs(p) > 999 ? "999+" : Math.abs(p).toLocaleString("th-TH")}%</i>;
}
type DashData = { ordersToday: number | null; salesToday: number | null; prevOrders: number | null; prevSales: number | null; pending: number | null; lowStock: number | null; outOfStock: number | null; latest: OrderSummary[] | null };

/** Stable colors from the original channel card (donut + legend). */
const PAYMENT_METHOD_COLORS: Record<string, string> = {
  PROMPTPAY: "#159df5",
  BANK_TRANSFER: "#12c98b",
  COD: "#9458ee",
  OMISE: "#ffae2f",
  "2C2P": "#f72b59",
};
const PAYMENT_METHOD_OTHER_COLOR = "#ff8a00";

function paymentMethodLabel(method: string): string {
  switch (method.trim().toUpperCase()) {
    case "PROMPTPAY": return "พร้อมเพย์";
    case "BANK_TRANSFER": return "โอนผ่านธนาคาร";
    case "COD": return "เก็บเงินปลายทาง";
    case "OMISE": return "บัตรเครดิต (Omise)";
    case "2C2P": return "บัตร/ช่องทาง 2C2P";
    default: return "อื่น ๆ";
  }
}

function paymentMethodColor(method: string): string {
  return PAYMENT_METHOD_COLORS[method.trim().toUpperCase()] ?? PAYMENT_METHOD_OTHER_COLOR;
}

type PaymentShareRow = { key: string; label: string; color: string; sales: number; pct: number };

/** Merge unknown providers into one "อื่น ๆ" row; % rounded to integers with the last row making 100. */
function paymentShareRows(
  methods: MerchantDashboardInsights["paymentMethods"] | undefined,
  totalSales: number,
): PaymentShareRow[] {
  if (!methods?.length || totalSales <= 0) return [];
  const known: PaymentShareRow[] = [];
  let otherSales = 0;
  for (const row of methods) {
    const code = (row.method || "").trim().toUpperCase();
    const sales = numberOf(row.sales as number);
    if (!code || !(code in PAYMENT_METHOD_COLORS)) {
      otherSales += sales;
      continue;
    }
    known.push({ key: code, label: paymentMethodLabel(code), color: paymentMethodColor(code), sales, pct: 0 });
  }
  const rows = otherSales > 0
    ? [...known, { key: "OTHER", label: "อื่น ๆ", color: PAYMENT_METHOD_OTHER_COLOR, sales: otherSales, pct: 0 }]
    : known;
  if (rows.length === 0) return [];
  let allocated = 0;
  return rows.map((row, index) => {
    const pct = index === rows.length - 1
      ? Math.max(0, 100 - allocated)
      : Math.round((row.sales / totalSales) * 100);
    if (index < rows.length - 1) allocated += pct;
    return { ...row, pct };
  });
}

function paymentDonutBackground(rows: PaymentShareRow[]): string {
  if (rows.length === 0) return "#e8eef5";
  if (rows.length === 1) return rows[0].color;
  let from = 0;
  const stops: string[] = [];
  for (const row of rows) {
    const to = from + row.pct;
    stops.push(`${row.color} ${from}% ${to}%`);
    from = to;
  }
  if (from < 100) stops.push(`#e8eef5 ${from}% 100%`);
  return `conic-gradient(${stops.join(",")})`;
}

function customerInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return Array.from(parts[0]).slice(0, 2).join("").toUpperCase();
  return (Array.from(parts[0])[0] + Array.from(parts[1])[0]).toUpperCase();
}
const EMPTY: DashData = { ordersToday: null, salesToday: null, prevOrders: null, prevSales: null, pending: null, lowStock: null, outOfStock: null, latest: null };

function MerchantPanel({
  title,
  action,
  sample,
  to,
  children,
  className = "",
}: {
  title: string;
  action?: string;
  sample?: boolean;
  to?: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={`merchant-panel ${className}`}>
      <header>
        <h2>{title}{sample && <small style={{ marginLeft: 8, fontSize: 12, fontWeight: 600, color: "#8a94a6", background: "#eef2f7", borderRadius: 999, padding: "2px 8px", verticalAlign: "middle" }}>ตัวอย่าง</small>}</h2>
        {action && !sample && (to ? <Link to={to}><button type="button">{action} →</button></Link> : <button>{action} →</button>)}
      </header>
      {children}
    </section>
  );
}

export default function MerchantDashboardPage() {
  const { t } = useTranslation("merchant");
  const { user, loading } = useAuth();
  const [notice, setNotice] = useState("");
  const { shops, shopId, ready } = useActiveShop();
  const [hover, setHover] = useState<number | null>(null);
  const [ins, setIns] = useState<MerchantDashboardInsights | null>(null);
  const [data, setData] = useState<DashData>(EMPTY);
  const [dashLoading, setDashLoading] = useState(true);
  const [dashError, setDashError] = useState("");
  const [range, setRange] = useState<RangeKey>("today");
  const [customFrom, setCustomFrom] = useState(() => bkkDate(new Date(Date.now() - 6 * 86400000)));
  const [customTo, setCustomTo] = useState(() => bkkDate(new Date()));
  const [logoUrl, setLogoUrl] = useState<string | null>(null);
  const [logoFailed, setLogoFailed] = useState(false);
  const customValid = !!customFrom && !!customTo && customFrom <= customTo;
  const period = range === "custom" ? (customValid ? { from: customFrom, to: customTo } : null) : { range };
  const periodKey = period ? JSON.stringify(period) : "";
  const canOrders = !!user && hasShopPermission(user, "ORDER_READ", shopId);
  const canProducts = !!user && hasShopPermission(user, "PRODUCT_READ", shopId);
  const canCustomers = !!user && hasShopPermission(user, "CUSTOMER_READ", shopId);
  const canCreate = !!user && hasShopPermission(user, "PRODUCT_CREATE", shopId);
  useEffect(() => {
    if (shopId == null) {
      setLogoUrl(null);
      setLogoFailed(false);
      return;
    }
    let alive = true;
    setLogoFailed(false);
    merchantApi.getSettings(shopId)
      .then((settings) => {
        if (!alive) return;
        setLogoUrl(settings.logoUrl?.trim() || null);
      })
      .catch(() => {
        if (alive) setLogoUrl(null);
      });
    return () => { alive = false; };
  }, [shopId]);
  useEffect(() => {
    if (!ready) return;
    if (shopId == null) { setDashLoading(false); return; }
    if (range === "custom" && !customValid) {
      setData(d => ({ ...d, ordersToday: null, salesToday: null, prevOrders: null, prevSales: null }));
      setIns(null);
      setDashLoading(false);
      setDashError(!customFrom || !customTo
        ? "เลือกช่วงวันที่เพื่อดูภาพรวม"
        : "ช่วงวันที่ไม่ถูกต้อง: วันเริ่มต้องไม่หลังวันสิ้นสุด");
      return;
    }
    let alive = true;
    setDashLoading(true); setDashError(""); setData(EMPTY); setIns(null); setHover(null);
    const tasks: Promise<unknown>[] = [];
    const next: DashData = { ...EMPTY };
    if (canOrders) {
      if (period) tasks.push(merchantDashboardApi.summary(shopId, period).then(s => { next.ordersToday = s.orderCount; next.salesToday = numberOf(s.sales as number); next.prevOrders = s.previousOrderCount; next.prevSales = numberOf(s.previousSales as number); next.pending = s.pendingOrders; }));
      if (period) tasks.push(merchantDashboardApi.insights(shopId, period).then(r => { if (alive) setIns(r); }));
      tasks.push(merchantOrderApi.list(shopId, { size: 5, sort: "placedAt", direction: "desc" }).then(p => { next.latest = p.items; }));
    }
    if (canProducts) {
      tasks.push(productApi.list(shopId, { stock: "low", size: 1 }).then(p => { next.lowStock = p.total; }));
      tasks.push(productApi.list(shopId, { stock: "out", size: 1 }).then(p => { next.outOfStock = p.total; }));
    }
    Promise.allSettled(tasks).then(rs => {
      if (!alive) return;
      setData(next);
      if (rs.some(r => r.status === "rejected")) setDashError("โหลดข้อมูลบางส่วนไม่สำเร็จ กรุณาลองใหม่อีกครั้ง");
      setDashLoading(false);
    });
    return () => { alive = false; };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, shopId, canOrders, canProducts, periodKey]);
  if (loading)
    return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if (!user) {
    window.location.replace("/login");
    return null;
  }
  if ((hasPlatformAccess(user) && !hasMerchantAccess(user))) {
    window.location.replace("/admin");
    return null;
  }
  if (!hasMerchantAccess(user)) {
    window.location.replace("/dashboard");
    return null;
  }
  const greetingName = dashboardGreetingName(user);
  const activeShop = shops.find(sh => sh.shopId === shopId);
  const shopName = activeShop?.shopName ?? "";
  const shopSlug = activeShop?.slug?.trim() || "";
  const statusChip = shopStatusChip(activeShop?.status);
  const todayLabel = new Intl.DateTimeFormat("th-TH", { timeZone: "Asia/Bangkok", day: "numeric", month: "long", year: "numeric" }).format(new Date());
  const rangeLabel = RANGES.find(r => r[0] === range)?.[2] ?? "วันนี้";
  const kpi = (v: number | null, allowed: boolean) => !allowed ? "—" : dashLoading ? "…" : v == null ? "—" : v.toLocaleString("th-TH");
  const notify = (text: string) => {
    setNotice(text);
    window.setTimeout(() => setNotice(""), 1800);
  };
  const fmt = (n: number) => n.toLocaleString("th-TH");
  const welcomeActions = [
    canOrders && data.pending != null && data.pending > 0
      ? { label: `รอยืนยัน ${fmt(data.pending)} ออเดอร์`, count: data.pending, to: "/merchant/orders", tone: "warn" as const }
      : null,
    canProducts && data.lowStock != null && data.lowStock > 0
      ? { label: `สินค้าใกล้หมด ${fmt(data.lowStock)}`, count: data.lowStock, to: "/merchant/products?stock=low", tone: "info" as const }
      : null,
    canProducts && data.outOfStock != null && data.outOfStock > 0
      ? { label: `สินค้าหมด ${fmt(data.outOfStock)}`, count: data.outOfStock, to: "/merchant/products?stock=out", tone: "danger" as const }
      : null,
  ].filter((item): item is NonNullable<typeof item> => item != null);
  return (
    <main className="merchant-shell">
      <header className="merchant-topbar">
        <MerchantBrand />
        <MerchantTopbarSearch />
        <MerchantTopbarActions />
      </header>
      <MerchantSidebar active="dashboard"/>
      <div className="merchant-content">
        <section className="merchant-welcome">
          <div>
            <DashboardWelcome
              greetingName={greetingName}
              loading={dashLoading}
              actions={welcomeActions}
              contextNode={shopName ? (
                <>
                  <span className="dash-welcome-avatar" aria-hidden={logoUrl && !logoFailed ? undefined : true}>
                    {logoUrl && !logoFailed ? (
                      <img
                        src={mediaUrl(logoUrl)}
                        alt={`โลโก้ ${shopName}`}
                        onError={() => setLogoFailed(true)}
                      />
                    ) : (
                      shopInitials(shopName)
                    )}
                  </span>
                  <span className="dash-welcome-shop">{shopName}</span>
                  {statusChip ? <span className={`dash-welcome-status ${statusChip.tone}`}>{statusChip.label}</span> : null}
                  {shopSlug ? (
                    <a
                      className="dash-welcome-storefront"
                      href={`/shop/${encodeURIComponent(shopSlug)}`}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      ดูหน้าร้าน ↗
                    </a>
                  ) : null}
                </>
              ) : null}
            />
            {dashError && <p role="alert" style={{ color: "#f32657" }}>{dashError}</p>}
            {ready && shopId == null && <p role="alert">ยังไม่มีร้านค้าที่เลือก</p>}
          </div>
          <div className="merchant-range">
            <div className="merchant-range-seg" role="tablist" aria-label="ช่วงเวลา">
              <CalendarDays />
              {RANGES.map(([key, label]) => (
                <button key={key} type="button" role="tab" aria-selected={range === key} className={range === key ? "active" : ""} onClick={() => setRange(key)}>{label}</button>
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
                onChange={({ from, to }) => {
                  setCustomFrom(from);
                  setCustomTo(to);
                }}
              />
            ) : <small>{range === "today" ? `วันนี้ · ${todayLabel}` : rangeLabel} · เวลาไทย</small>}
          </div>
        </section>
        <section className="merchant-kpis">
          <article className="purple">
            <span>
              <ShoppingCart />
            </span>
            <div>
              <small>คำสั่งซื้อ{range === "today" ? "วันนี้" : ` · ${rangeLabel}`}</small>
              <b>
                {kpi(data.ordersToday, canOrders)}{!dashLoading && canOrders && <Trend cur={data.ordersToday} prev={data.prevOrders} />}
              </b>
              <em>เวลาไทย</em>
            </div>
          </article>
          <article className="green">
            <span>
              <BarChart3 />
            </span>
            <div>
              <small>ยอดขาย{range === "today" ? "วันนี้" : ` · ${rangeLabel}`} (GMV)</small>
              <b>
                {!canOrders ? "—" : dashLoading ? "…" : data.salesToday == null ? "—" : baht(data.salesToday)}{!dashLoading && canOrders && <Trend cur={data.salesToday} prev={data.prevSales} />}
              </b>
              <em>ชำระแล้ว · เวลาไทย</em>
            </div>
          </article>
          <article className="blue">
            <span>
              <WalletCards />
            </span>
            <div>
              <small>ออเดอร์รอดำเนินการ</small>
              <b>
                {kpi(data.pending, canOrders)}
              </b>
              <em>รอยืนยัน – แพ็กแล้ว · ปัจจุบัน</em>
            </div>
          </article>
          <article className="pink">
            <span>
              <ShoppingBag />
            </span>
            <div>
              <small>สินค้าใกล้หมด</small>
              <b>
                {kpi(data.lowStock, canProducts)}
              </b>
              <em>ถึงเกณฑ์แจ้งเตือน</em>
            </div>
          </article>
          <article className="orange">
            <span>
              <UsersRound />
            </span>
            <div>
              <small>สินค้าหมดสต็อก</small>
              <b>
                {kpi(data.outOfStock, canProducts)}
              </b>
              <em>ไม่มีสต็อกพร้อมขาย</em>
            </div>
          </article>
        </section>
        <section className="merchant-grid top">
          <MerchantPanel title="ยอดขายและรายได้" action="ดูรายงานทั้งหมด" to="/merchant/reports">
            <div className="merchant-legend">
              <span>
                <i />
                ยอดขาย (GMV)
              </span>
              <span>
                <i />
                รายได้หลังหักค่าธรรมเนียม (ประมาณการ)
              </span>
              <span>
                <i />
                ค่าธรรมเนียม 5% (ประมาณการ)
              </span>
            </div>
            {!canOrders ? <EmptyNote>ไม่มีสิทธิ์ดูยอดขาย</EmptyNote>
              : dashLoading && !ins ? <EmptyNote>กำลังโหลด…</EmptyNote>
              : !ins ? <EmptyNote>ไม่มีข้อมูลยอดขาย</EmptyNote>
              : (() => {
                const series = ins.series;
                const values = series.map(p => numberOf(p.sales as number));
                const max = niceMax(Math.max(0, ...values));
                const total = values.reduce((x, y) => x + y, 0);
                const n = series.length;
                const every = n > 16 ? Math.ceil(n / 8) : 1;
                const barW = n <= 8 ? 13 : n <= 14 ? 9 : n <= 20 ? 6 : 4;
                const peak = values.indexOf(Math.max(...values));
                const tipIndex = hover ?? (total > 0 ? peak : -1);
                const tip = tipIndex >= 0 ? series[tipIndex] : null;
                const tipSales = tip ? numberOf(tip.sales as number) : 0;
                return (
                  <div className="merchant-chart">
                    <div className="merchant-y">
                      {[5, 4, 3, 2, 1, 0].map(k => <span key={k}>{shortBaht((max / 5) * k)}</span>)}
                    </div>
                    <div className="merchant-plot" onMouseLeave={() => setHover(null)}>
                      {series.map((p, index) => {
                        const v = values[index];
                        const h = (x: number) => `${Math.round((x / max) * 126)}px`;
                        return (
                          <div className="merchant-day" key={p.start} style={{ width: `${Math.min(12, 92 / n)}%` }}
                            onMouseEnter={() => setHover(index)} title={`${bucketTitle(p.start, p.end, ins.bucket)} · ${baht(v)} · ${p.orders} ออเดอร์`}>
                            <div>
                              <i style={{ height: h(v), width: barW }} />
                              <i style={{ height: h(v * (1 - FEE_RATE)), width: barW }} />
                              <i style={{ height: v > 0 ? `${Math.max(Math.round((v * FEE_RATE / max) * 126), 2)}px` : "0px", width: barW }} />
                            </div>
                            <small style={index % every === 0 ? undefined : { visibility: "hidden" }}>{bucketLabel(p.start, ins.bucket)}</small>
                          </div>
                        );
                      })}
                      {total === 0 ? (
                        <div className="chart-tip" style={{ left: "50%", right: "auto", top: "35%", transform: "translateX(-50%)", textAlign: "center" }}>
                          <b>ยังไม่มียอดขายที่ชำระแล้วในช่วงนี้</b>
                        </div>
                      ) : tip ? (
                        <div className="chart-tip" style={{ pointerEvents: "none" }}>
                          <b>{bucketTitle(tip.start, tip.end, ins.bucket)}</b>
                          <span>
                            <i />
                            ยอดขาย <strong>{baht(tipSales)}</strong>
                          </span>
                          <span>
                            <i />
                            รายได้ (ประมาณการ) <strong>{baht(Math.round(tipSales * (1 - FEE_RATE) * 100) / 100)}</strong>
                          </span>
                          <span>
                            <i />
                            ค่าธรรมเนียม 5% (ประมาณการ) <strong>{baht(Math.round(tipSales * FEE_RATE * 100) / 100)}</strong>
                          </span>
                        </div>
                      ) : null}
                    </div>
                  </div>
                );
              })()}
          </MerchantPanel>
          <MerchantPanel title="สัดส่วนวิธีชำระเงิน">
            {!canOrders ? <EmptyNote>ไม่มีสิทธิ์ดูยอดขาย</EmptyNote>
              : dashLoading && !ins ? <EmptyNote>กำลังโหลด…</EmptyNote>
              : !ins ? <EmptyNote>ไม่มีข้อมูลยอดขาย</EmptyNote>
              : (() => {
                const totalSales = numberOf(ins.sales as number);
                const rows = paymentShareRows(ins.paymentMethods, totalSales);
                if (rows.length === 0 || totalSales <= 0) {
                  return <EmptyNote>ยังไม่มียอดขายในช่วงนี้</EmptyNote>;
                }
                return (
                  <div className="merchant-channel">
                    <div className="merchant-donut" style={{ background: paymentDonutBackground(rows) }}>
                      <b>
                        {baht(totalSales)}<small>ยอดขายรวม</small>
                      </b>
                    </div>
                    <ul>
                      {rows.map((row) => (
                        <li key={row.key} title={baht(row.sales)}>
                          <i style={{ background: row.color }} />
                          {row.label} <b>{row.pct}%</b>
                        </li>
                      ))}
                    </ul>
                  </div>
                );
              })()}
          </MerchantPanel>
        </section>
        <section className="merchant-grid bottom">
          <MerchantPanel title="สินค้าขายดี (Top 5)" action="ดูสินค้าทั้งหมด" to={canProducts ? "/merchant/products" : undefined}>
            {!canOrders ? <EmptyNote>ไม่มีสิทธิ์ดูยอดขาย</EmptyNote>
              : dashLoading && !ins ? <EmptyNote>กำลังโหลด…</EmptyNote>
              : !ins || ins.topProducts.length === 0 ? <EmptyNote>ยังไม่มีสินค้าที่ขายได้ในช่วงนี้</EmptyNote>
              : (
            <ol className="best-products">
              {ins.topProducts.map((p, index) => (
                <li key={p.productId ?? p.name}>
                  <i>{index + 1}</i>
                  <span style={{ overflow: "hidden", height: 42, display: "grid", placeItems: "center" }}>
                    <TopProductThumb path={p.imageUrl} />
                  </span>
                  <p style={{ minWidth: 0 }}>
                    <b style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }} title={p.name}>{p.name}</b>
                    <small>ขายแล้ว {p.quantity.toLocaleString("th-TH")} ชิ้น</small>
                  </p>
                  <strong>{baht(p.sales)}</strong>
                </li>
              ))}
            </ol>)}
          </MerchantPanel>
          <MerchantPanel title="คำสั่งซื้อล่าสุด" action="ดูทั้งหมด" to={canOrders ? "/merchant/orders" : undefined}>
            <div className="recent-orders" aria-busy={dashLoading}>
              {!canOrders ? <p>ไม่มีสิทธิ์ดูคำสั่งซื้อ</p>
                : dashLoading ? [0, 1, 2].map(i => <div key={i} style={{ opacity: 0.45 }}><b>กำลังโหลด…</b><span /><strong /><em /><small /></div>)
                : !data.latest?.length ? <p>ยังไม่มีคำสั่งซื้อ</p>
                : data.latest.map((order) => { const [label, cls] = statusView(order); return (
                <div key={order.id}>
                  <b style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }} title={order.orderNumber}>#{order.orderNumber}</b>
                  <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{order.customerName || "-"}</span>
                  <strong title={order.awaitingAcceptance ? "แสดงยอดหลังร้านยืนยันออเดอร์" : undefined}>{order.awaitingAcceptance ? "—" : baht(order.grandTotal)}</strong>
                  <em className={`status-${cls}`}>{label}</em>
                  <small>{thaiDateTime(order.placedAt)}</small>
                </div>); })}
            </div>
          </MerchantPanel>
          <MerchantPanel title="ลูกค้าซื้อมากที่สุด (Top 5)" action="ดูลูกค้าทั้งหมด" to={canCustomers ? "/merchant/customers" : undefined}>
            {!canOrders ? <EmptyNote>ไม่มีสิทธิ์ดูยอดขาย</EmptyNote>
              : dashLoading && !ins ? <EmptyNote>กำลังโหลด…</EmptyNote>
              : !ins || !ins.topCustomers?.length ? <EmptyNote>ยังไม่มีลูกค้าซื้อในช่วงนี้</EmptyNote>
              : (
            <ol className="best-products best-customers">
              {ins.topCustomers.map((c, index) => {
                const share = numberOf(c.sharePercent as number);
                return (
                  <li key={c.customerKey}>
                    <i>{index + 1}</i>
                    <span className="customer-initials" aria-hidden="true">{customerInitials(c.displayName)}</span>
                    <p style={{ minWidth: 0 }}>
                      <b style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }} title={c.displayName}>{c.displayName}</b>
                      <small>{c.orderCount.toLocaleString("th-TH")} ออเดอร์</small>
                    </p>
                    <div className="customer-metric">
                      <strong>{baht(c.totalAmount)}</strong>
                      <em title={`${share.toLocaleString("th-TH", { maximumFractionDigits: 1 })}% ของยอดขายช่วงนี้`}>
                        <i style={{ background: `linear-gradient(to right, #159df5 0 ${Math.min(100, Math.max(0, share))}%, #d7e6f5 ${Math.min(100, Math.max(0, share))}% 100%)` }} />
                        <b>{share.toLocaleString("th-TH", { maximumFractionDigits: 1 })}%</b>
                      </em>
                    </div>
                  </li>
                );
              })}
            </ol>)}
          </MerchantPanel>
        </section>
        <section className="merchant-cta">
          <div className="cta-store">
            🏪<i>🪴</i>
          </div>
          <div>
            <h2>ทางลัด (Quick Actions)</h2>
            <p>เพิ่มสินค้าใหม่ หรือจัดการคำสั่งซื้อของร้านได้ทันที</p>
          </div>
          {canCreate && <Link to="/merchant/products/new"><button type="button">เพิ่มสินค้า →</button></Link>}
          {canOrders && <Link to="/merchant/orders"><button type="button" className="outline">เปิดออเดอร์</button></Link>}
        </section>
      </div>
      {notice && (
        <div className="merchant-toast" role="status">
          {notice}
        </div>
      )}
    </main>
  );
}

/** Top-5 thumbnail: merchant image URLs need auth (blob), others go through mediaUrl; missing or broken -> 📦. */
function TopProductThumb({ path }: { path?: string | null }) {
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
      .then(blob => { if (!active) return; objectUrl = URL.createObjectURL(blob); setSrc(objectUrl); })
      .catch(() => { if (active) setBroken(true); });
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [path]);
  if (!src || broken) return <>📦</>;
  return <img src={src} alt="" style={{ width: 42, height: 42, objectFit: "cover" }} onError={() => setBroken(true)} />;
}
