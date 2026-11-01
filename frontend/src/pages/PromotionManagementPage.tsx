import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  BarChart3,
  CalendarDays,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Copy,
  Ellipsis,
  Gift,
  Lightbulb,
  Megaphone,
  Pause,
  Pencil,
  Play,
  Trash2,
  Plus,
  Search,
  ShoppingBag,
  Tag,
  TicketPercent,
  Truck,
  UsersRound,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { usePermissions } from "../hooks/usePermissions";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/promotion-management.css";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import MerchantTopbarActions from "../components/merchant/MerchantTopbarActions";

import ConfirmDialog from "../components/form/ConfirmDialog";
import { LoadError, SkeletonBusy, SkeletonTable } from "../components/feedback/Skeleton";
import PromotionFormDialog, { draftFrom, emptyDraft, type PromotionDraft } from "../components/merchant/PromotionFormDialog";
import { benefitText, formatBaht, formatThaiDate, statusBadge, TYPE_CLASS, TYPE_LABEL } from "../components/merchant/promotionFormat";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { useDelayedLoading } from "../hooks/useDelayedLoading";
import { ApiError, promotionApi, type Promotion, type PromotionList, type PromotionStatus, type PromotionType, type PromotionWrite } from "../services/api";
import MerchantSidebar from "../components/merchant/MerchantSidebar";


function Brand() {
  return (
    <a className="merchant-brand" href="/">
      <span><ShoppingBag /><i>✓</i></span>
      <div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div>
    </a>
  );
}

type Tab = "ทั้งหมด" | "คูปองส่วนลด" | "ส่งฟรี" | "หยุดชั่วคราว" | "หมดอายุแล้ว";
const TABS: Tab[] = ["ทั้งหมด", "คูปองส่วนลด", "ส่งฟรี", "หยุดชั่วคราว", "หมดอายุแล้ว"];
const TAB_FILTER: Record<Tab, { types?: PromotionType[]; status?: PromotionStatus }> = {
  "ทั้งหมด": {},
  "คูปองส่วนลด": { types: ["PERCENT", "FIXED_AMOUNT"] },
  "ส่งฟรี": { types: ["FREE_SHIPPING"] },
  "หยุดชั่วคราว": { status: "PAUSED" },
  "หมดอายุแล้ว": { status: "EXPIRED" },
};
const STATUS_OPTIONS: [string, PromotionStatus | ""][] = [["ทั้งหมด", ""], ["ใช้งานอยู่", "ACTIVE"], ["ยังไม่เริ่ม", "UPCOMING"], ["หยุดชั่วคราว", "PAUSED"], ["หมดอายุ", "EXPIRED"]];
const TYPE_OPTIONS: [string, PromotionType | ""][] = [["ทั้งหมด", ""], ["ส่วนลด %", "PERCENT"], ["ส่วนลดบาท", "FIXED_AMOUNT"], ["ส่งฟรี", "FREE_SHIPPING"]];
const IDEAS: [string, Partial<PromotionDraft>][] = [
  ["ส่วนลดต้อนรับลูกค้าใหม่", { type: "PERCENT", value: "10", usageLimitPerBuyer: "1" }],
  ["คูปองวันเกิด", { type: "PERCENT", value: "20" }],
  ["ส่งฟรีเมื่อซื้อครบตามกำหนด", { type: "FREE_SHIPPING", minOrderAmount: "500" }],
  ["ซื้อครบ X ลด Y", { type: "FIXED_AMOUNT", value: "100", minOrderAmount: "1000" }],
  ["แฟลชเซลล์ช่วงเทศกาล", { type: "PERCENT", value: "30" }],
  ["โปรเฉพาะสมาชิก", { type: "PERCENT", value: "15", showOnStorefront: false }],
];
const EMPTY: PromotionList = { items: [], page: 0, size: 10, total: 0, totalPages: 0, summary: { total: 0, active: 0, upcoming: 0, paused: 0, expired: 0, percent: 0, fixedAmount: 0, freeShipping: 0, usageCount: 0, discountTotal: 0 } };

type IconKind = "ticket" | "truck" | "bag" | "gift" | "tag" | "cart";
function iconOf(promotion: Promotion): IconKind {
  if (promotion.type === "FREE_SHIPPING") return "truck";
  if (promotion.status === "EXPIRED") return "bag";
  if (promotion.type === "FIXED_AMOUNT") return "cart";
  return "ticket";
}

function PromoIcon({ kind }: { kind: IconKind }) {
  const Icon = kind === "truck" ? Truck : kind === "gift" ? Gift : kind === "tag" ? Tag : kind === "cart" ? ShoppingBag : kind === "bag" ? ShoppingBag : TicketPercent;
  return <span className={`promo-kind ${kind}`}><Icon /></span>;
}

function conditionLines(promotion: Promotion): string[] {
  const lines: string[] = [];
  if (promotion.maxDiscountAmount != null) lines.push(`สูงสุด ${formatBaht(promotion.maxDiscountAmount)} บาท`);
  lines.push(promotion.minOrderAmount && promotion.minOrderAmount > 0 ? `ขั้นต่ำ ${formatBaht(promotion.minOrderAmount)} บาท` : "ไม่มีขั้นต่ำ");
  return lines;
}

function errorMessage(error: unknown) {
  return error instanceof Error && error.message ? error.message : "เกิดข้อผิดพลาด กรุณาลองใหม่";
}

export default function PromotionManagementPage() {
  const { user, loading } = useAuth();
  const { shopId } = useActiveShop();
  const { canInShop, deniedTitle } = usePermissions();
  const canManage = canInShop("PROMOTION_MANAGE");
  const [data, setData] = useState<PromotionList>(EMPTY);
  const [listLoading, setListLoading] = useState(true);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [reload, setReload] = useState(0);
  const [query, setQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [status, setStatus] = useState<PromotionStatus | "">("");
  const [type, setType] = useState<PromotionType | "">("");
  const [tab, setTab] = useState<Tab>("ทั้งหมด");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [selected, setSelected] = useState<string[]>([]);
  const [notice, setNotice] = useState("");
  const [form, setForm] = useState<{ mode: "create" | "edit"; draft: PromotionDraft; target?: Promotion } | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<Promotion | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [menuFor, setMenuFor] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const noticeTimer = useRef<number | undefined>(undefined);

  const notify = useCallback((message: string) => {
    setNotice(message);
    window.clearTimeout(noticeTimer.current);
    noticeTimer.current = window.setTimeout(() => setNotice(""), 2400);
  }, []);
  useEffect(() => () => window.clearTimeout(noticeTimer.current), []);

  // search box: debounce into the server query
  useEffect(() => {
    const id = window.setTimeout(() => { setAppliedQuery(query.trim()); setPage(0); }, 300);
    return () => window.clearTimeout(id);
  }, [query]);

  // tab + selects combine (tab narrows; a select outside the tab yields an empty list without a request)
  const effective = useMemo(() => {
    const tabFilter = TAB_FILTER[tab];
    let types = tabFilter.types ?? (type ? [type] : undefined);
    if (tabFilter.types && type) types = tabFilter.types.includes(type) ? [type] : [];
    let effectiveStatus: PromotionStatus | "" | null = tabFilter.status ?? status;
    if (tabFilter.status && status && status !== tabFilter.status) effectiveStatus = null;
    return { types, status: effectiveStatus };
  }, [tab, type, status]);

  useEffect(() => {
    if (shopId == null) return;
    if (effective.status === null || (effective.types && effective.types.length === 0)) {
      setData((current) => ({ ...current, items: [], total: 0, totalPages: 0, page: 0 }));
      setListLoading(false);
      setLoadError(null);
      return;
    }
    let cancelled = false;
    setListLoading(true);
    setLoadError(null);
    promotionApi.list(shopId, { q: appliedQuery, status: effective.status, type: effective.types, page, size })
      .then((result) => {
        if (cancelled) return;
        if (result.totalPages > 0 && page >= result.totalPages) { setPage(result.totalPages - 1); return; }
        setData(result);
        setSelected((ids) => ids.filter((id) => result.items.some((item) => item.id === id)));
      })
      .catch((error) => { if (!cancelled) setLoadError(error); })
      .finally(() => { if (!cancelled) setListLoading(false); });
    return () => { cancelled = true; };
  }, [shopId, appliedQuery, effective, page, size, reload]);

  const { showSkeleton, markHasData, isRefetching } = useDelayedLoading(listLoading, 150, shopId ?? "none");
  useEffect(() => { if (!listLoading && !loadError) markHasData(); }, [listLoading, loadError, markHasData]);

  useEffect(() => {
    if (!menuFor) return;
    const close = (event: MouseEvent | KeyboardEvent) => {
      if (event instanceof KeyboardEvent ? event.key === "Escape" : !(event.target as HTMLElement).closest(".promo-row-menu, .promo-row-actions")) setMenuFor(null);
    };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", close);
    return () => { document.removeEventListener("mousedown", close); document.removeEventListener("keydown", close); };
  }, [menuFor]);

  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if (!user) { window.location.replace("/login?next=/merchant/promotions"); return null; }
  if ((hasPlatformAccess(user) && !hasMerchantAccess(user))) { window.location.replace("/admin"); return null; }
  if (!hasMerchantAccess(user)) { window.location.replace("/"); return null; }

  const visible = data.items;
  const summary = data.summary;
  const tabCount: Record<Tab, number> = {
    "ทั้งหมด": summary.total,
    "คูปองส่วนลด": summary.percent + summary.fixedAmount,
    "ส่งฟรี": summary.freeShipping,
    "หยุดชั่วคราว": summary.paused,
    "หมดอายุแล้ว": summary.expired,
  };
  const filtered = !!(appliedQuery || status || type || tab !== "ทั้งหมด");
  const reset = () => { setQuery(""); setAppliedQuery(""); setStatus(""); setType(""); setTab("ทั้งหมด"); setPage(0); };
  const toggleAll = () => setSelected(selected.length === visible.length ? [] : visible.map((item) => item.id));
  const refresh = () => setReload((value) => value + 1);
  const forbidden = loadError instanceof ApiError && (loadError.status === 403 || loadError.status === 404);
  const from = data.total === 0 ? 0 : data.page * data.size + 1;
  const to = Math.min(data.total, data.page * data.size + visible.length);
  const pageCount = Math.max(1, data.totalPages);
  const pageButtons = Array.from({ length: Math.min(5, pageCount) }, (_, index) => Math.min(Math.max(0, page - 2), Math.max(0, pageCount - 5)) + index);

  const openCreate = (partial: Partial<PromotionDraft> = {}) => { if (canManage) setForm({ mode: "create", draft: emptyDraft(partial) }); };
  const submitForm = async (payload: PromotionWrite) => {
    if (shopId == null || !form) return;
    if (form.mode === "edit" && form.target) {
      const saved = await promotionApi.update(shopId, form.target.id, payload);
      notify(`บันทึก ${saved.code ?? saved.name} แล้ว`);
    } else {
      const saved = await promotionApi.create(shopId, payload);
      notify(`สร้างคูปอง ${saved.code ?? saved.name} แล้ว`);
      setPage(0);
    }
    setForm(null);
    refresh();
  };
  const toggleActive = async (promotion: Promotion) => {
    if (shopId == null || busyId) return;
    setMenuFor(null);
    setBusyId(promotion.id);
    try {
      const saved = promotion.active ? await promotionApi.pause(shopId, promotion.id) : await promotionApi.resume(shopId, promotion.id);
      notify(saved.active ? `เปิดใช้งาน ${saved.code} แล้ว` : `หยุด ${saved.code} ชั่วคราวแล้ว`);
      refresh();
    } catch (error) {
      notify(errorMessage(error));
    } finally {
      setBusyId(null);
    }
  };
  const copyCode = async (promotion: Promotion) => {
    setMenuFor(null);
    if (!promotion.code) return;
    try { await navigator.clipboard.writeText(promotion.code); notify(`คัดลอกรหัส ${promotion.code} แล้ว`); }
    catch { notify(`รหัสคูปอง: ${promotion.code}`); }
  };
  const runDelete = async () => {
    if (shopId == null || !confirmDelete || deleting) return;
    setDeleting(true);
    try {
      await promotionApi.remove(shopId, confirmDelete.id);
      notify(`ลบ ${confirmDelete.code ?? confirmDelete.name} แล้ว`);
      setConfirmDelete(null);
      refresh();
    } catch (error) {
      notify(errorMessage(error));
      if (error instanceof ApiError && error.code === "PROMOTION_IN_USE") setConfirmDelete(null);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <main className="merchant-shell pm-shell promo-shell">
      <header className="merchant-topbar">
        <Brand />
        <MerchantTopbarSearch />
        <MerchantTopbarActions />
      </header>

      <MerchantSidebar active="promotions"/>

      <section className="pm-content promo-content">
        <header className="pm-heading promo-heading">
          <div><h1><Tag />โปรโมชั่น / คูปอง</h1><p>สร้างและจัดการโปรโมชั่น เพื่อกระตุ้นยอดขายให้ร้านของคุณ</p></div>
          <div className="pm-actions"><button onClick={() => notify("ประวัติการใช้คูปองจะแสดงเมื่อเปิดใช้คูปองตอนชำระเงิน")}><TicketPercent />ดูประวัติการใช้คูปอง</button><button disabled={!canManage} title={deniedTitle(canManage)} className="primary" onClick={() => openCreate()}><Plus />สร้างโปรโมชั่น / คูปอง</button></div>
        </header>

        <section className="pm-kpis promo-kpis">
          <article className="total"><span><TicketPercent /></span><div><small>โปรโมชั่นทั้งหมด</small><b className="num">{summary.total.toLocaleString()}</b><em>ยังไม่เริ่ม <span className="num">{summary.upcoming.toLocaleString()}</span> · หมดอายุ <span className="num">{summary.expired.toLocaleString()}</span></em></div></article>
          <article className="active"><span><TicketPercent /></span><div><small>คูปองที่ใช้งานอยู่</small><b className="num">{summary.active.toLocaleString()}</b><em>หยุดชั่วคราว <span className="num">{summary.paused.toLocaleString()}</span></em></div></article>
          <article className="uses"><span><UsersRound /></span><div><small>จำนวนการใช้คูปอง</small><b className="num">{summary.usageCount.toLocaleString()}</b><em>นับจากคำสั่งซื้อที่ใช้คูปอง</em></div></article>
          <article className="value"><span><BarChart3 /></span><div><small>มูลค่าลดรวม</small><b className="num">฿ {formatBaht(summary.discountTotal)}</b><em>รวมทุกโปรโมชั่น</em></div></article>
        </section>

        <div className="promo-layout">
          <section className="promo-table-card">
            <div className="promo-tabs-search">
              <div className="promo-tabs">{TABS.map((label) => <button key={label} className={tab === label ? "active" : ""} onClick={() => { setTab(label); setPage(0); }}>{label} (<span className="num">{tabCount[label].toLocaleString()}</span>)</button>)}</div>
              <label><Search /><input aria-label="ค้นหาโปรโมชั่น" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="ค้นหาชื่อโปรโมชั่น หรือรหัสคูปอง..." /></label>
            </div>
            <form className="promo-filters" onSubmit={(event) => { event.preventDefault(); setAppliedQuery(query.trim()); setPage(0); refresh(); }}>
              <label>สถานะ<select aria-label="สถานะโปรโมชั่น" value={status} onChange={(event) => { setStatus(event.target.value as PromotionStatus | ""); setPage(0); }}>{STATUS_OPTIONS.map(([label, value]) => <option key={label} value={value}>{label}</option>)}</select></label>
              <label>ประเภท<select aria-label="ประเภทโปรโมชั่น" value={type} onChange={(event) => { setType(event.target.value as PromotionType | ""); setPage(0); }}>{TYPE_OPTIONS.map(([label, value]) => <option key={label} value={value}>{label}</option>)}</select></label>
              <label>ช่องทาง<select aria-label="ช่องทางโปรโมชั่น" value="ทั้งหมด" disabled title="คูปองใช้ได้ทุกช่องทาง (ยังไม่รองรับการแยกช่องทาง)" onChange={() => undefined}><option>ทั้งหมด</option><option>เว็บไซต์</option><option>LINE OA</option></select></label>
              <label>ช่วงวันที่<span className="promo-date"><CalendarDays />ทั้งหมด<ChevronDown /></span></label>
              <div><button type="button" onClick={reset}>รีเซ็ต</button><button className="primary">ค้นหา</button></div>
            </form>
            {showSkeleton && listLoading && visible.length === 0 ? (
              <div className="promo-table-wrap" aria-busy="true" aria-label="กำลังโหลดโปรโมชั่น"><SkeletonTable rows={6} cols={8} /></div>
            ) : loadError ? (
              <div className="promo-table-wrap"><LoadError message={forbidden ? "คุณไม่มีสิทธิ์ดูโปรโมชั่นของร้านนี้" : errorMessage(loadError)} onRetry={refresh} retryLabel="ลองใหม่" /></div>
            ) : (
              <SkeletonBusy refetching={isRefetching} busy={isRefetching}>
                <div className="promo-table-wrap">
                  <table>
                    <thead><tr><th><input type="checkbox" aria-label="เลือกโปรโมชั่นทั้งหมด" checked={visible.length > 0 && selected.length === visible.length} onChange={toggleAll} /></th><th>ชื่อโปรโมชั่น / คูปอง</th><th>ประเภท</th><th>ส่วนลด / เงื่อนไข</th><th>ช่วงเวลา</th><th>การใช้งาน</th><th>สถานะ</th><th>จัดการ</th></tr></thead>
                    <tbody>{visible.map((promotion) => {
                      const limit = promotion.usageLimitTotal;
                      const percent = limit ? Math.min(100, Math.round((promotion.usedCount / limit) * 100)) : 0;
                      const badge = statusBadge(promotion);
                      const label = promotion.code ?? promotion.name;
                      return <tr key={promotion.id}>
                        <td><input type="checkbox" aria-label={`เลือก ${label}`} checked={selected.includes(promotion.id)} onChange={() => setSelected((items) => items.includes(promotion.id) ? items.filter((id) => id !== promotion.id) : [...items, promotion.id])} /></td>
                        <td><div className="promo-name"><PromoIcon kind={iconOf(promotion)} /><p><b className="num">{promotion.code ?? "-"}</b><small>{promotion.name}</small></p></div></td>
                        <td><span className={`promo-type ${TYPE_CLASS[promotion.type]}`}>{TYPE_LABEL[promotion.type]}</span></td>
                        <td><b className="num">{benefitText(promotion)}</b>{conditionLines(promotion).map((line) => <small className="num" key={line}>{line}</small>)}</td>
                        <td><span className="num">{formatThaiDate(promotion.startsAt)} -</span><span className="num">{formatThaiDate(promotion.endsAt)}</span></td>
                        <td><b className="num">{promotion.usedCount.toLocaleString()} / {limit ? limit.toLocaleString() : "ไม่จำกัด"} ครั้ง</b>{limit ? <><div className="promo-progress"><i style={{ width: `${percent}%` }} /></div><small className="num">{percent}%</small></> : <small>{promotion.showOnStorefront ? "แสดงบนหน้าร้าน" : "ไม่แสดงบนหน้าร้าน"}</small>}</td>
                        <td><span className={`promo-status ${badge.className}`}>{badge.label}</span></td>
                        <td><div className="promo-row-actions">
                          <button disabled={!canManage} title={deniedTitle(canManage)} aria-label={`แก้ไข ${label}`} onClick={() => setForm({ mode: "edit", draft: draftFrom(promotion), target: promotion })}><Pencil /></button>
                          <button disabled={!canManage} title={deniedTitle(canManage)} aria-label={`ทำสำเนา ${label}`} onClick={() => setForm({ mode: "create", draft: draftFrom(promotion, true) })}><Copy /></button>
                          <button disabled={!canManage || busyId === promotion.id} title={deniedTitle(canManage)} aria-label={`ตัวเลือก ${label}`} aria-haspopup="menu" aria-expanded={menuFor === promotion.id} onClick={() => setMenuFor((current) => current === promotion.id ? null : promotion.id)}><Ellipsis /></button>
                          {menuFor === promotion.id && <div className="promo-row-menu" role="menu">
                            {promotion.status !== "EXPIRED" && <button role="menuitem" onClick={() => void toggleActive(promotion)}>{promotion.active ? <><Pause />หยุดชั่วคราว</> : <><Play />เปิดใช้งานอีกครั้ง</>}</button>}
                            <button role="menuitem" onClick={() => void copyCode(promotion)}><Copy />คัดลอกรหัส</button>
                            <button role="menuitem" className="danger" onClick={() => { setMenuFor(null); setConfirmDelete(promotion); }}><Trash2 />ลบ</button>
                          </div>}
                        </div></td>
                      </tr>;
                    })}</tbody>
                  </table>
                  {visible.length === 0 && !listLoading && <div className="promo-empty">{filtered ? "ไม่พบโปรโมชั่นที่ตรงกับตัวกรอง" : "ยังไม่มีโปรโมชั่น — กด \"สร้างโปรโมชั่น / คูปอง\" เพื่อเริ่ม"}</div>}
                </div>
              </SkeletonBusy>
            )}
            <div className="promo-pagination"><span>แสดง <span className="num">{from.toLocaleString()} - {to.toLocaleString()}</span> จาก <span className="num">{data.total.toLocaleString()}</span> รายการ</span><label>แสดง<select aria-label="จำนวนรายการ" value={size} onChange={(event) => { setSize(Number(event.target.value)); setPage(0); }}><option value={10}>10</option><option value={20}>20</option></select>รายการ</label><nav aria-label="หน้าโปรโมชั่น"><button aria-label="ก่อนหน้า" disabled={page <= 0} onClick={() => setPage((value) => Math.max(0, value - 1))}><ChevronLeft /></button>{pageButtons.map((index) => <button key={index} className={index === page ? "active num" : "num"} aria-current={index === page ? "page" : undefined} onClick={() => setPage(index)}>{index + 1}</button>)}<button aria-label="ถัดไป" disabled={page >= pageCount - 1} onClick={() => setPage((value) => Math.min(pageCount - 1, value + 1))}><ChevronRight /></button></nav></div>
          </section>

          <aside className="promo-aside">
            <section className="promo-guide"><h2><Megaphone />สร้างโปรโมชั่นง่ายๆ</h2>{["เลือกประเภทโปรโมชั่น", "ตั้งค่าส่วนลดและเงื่อนไข", "กำหนดช่วงเวลา", "กำหนดจำนวนสิทธิ์ (ถ้ามี)", "เผยแพร่ และเริ่มใช้งานได้ทันที"].map((text) => <p key={text}><b>✓</b>{text}</p>)}</section>
            <section className="promo-ideas"><h2><Lightbulb />ไอเดียโปรโมชั่น</h2>{IDEAS.map(([text, preset]) => <button key={text} disabled={!canManage} title={deniedTitle(canManage)} onClick={() => openCreate({ name: text, ...preset })}><span>♨</span>{text}<ChevronRight /></button>)}</section>
            <section className="promo-cta"><Gift /><h2>โปรโมชั่นดี ๆ<br />เพิ่มยอดขายให้ร้าน</h2><button disabled={!canManage} title={deniedTitle(canManage)} onClick={() => openCreate()}>เริ่มสร้างเลยวันนี้!</button></section>
          </aside>
        </div>
      </section>
      {form && <PromotionFormDialog mode={form.mode} initial={form.draft} usedCount={form.target?.usedCount ?? 0} onSubmit={submitForm} onCancel={() => setForm(null)} />}
      {confirmDelete && <ConfirmDialog
        title="ลบโปรโมชั่นนี้?"
        message={`ลบ ${confirmDelete.code ?? confirmDelete.name} ถาวร — ถ้าคูปองนี้เคยถูกใช้ในคำสั่งซื้อแล้วจะลบไม่ได้ ให้ใช้ "หยุดชั่วคราว" แทน`}
        confirmLabel={deleting ? "กำลังลบ..." : "ลบ"}
        cancelLabel="ยกเลิก"
        danger
        confirmDisabled={deleting}
        onConfirm={() => void runDelete()}
        onCancel={() => { if (!deleting) setConfirmDelete(null); }}
      />}
      {notice && <div className="merchant-toast" role="status">{notice}</div>}
    </main>
  );
}
