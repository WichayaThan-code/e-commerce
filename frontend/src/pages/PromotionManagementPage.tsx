import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  BarChart3,
  ChevronRight,
  ClipboardCopy,
  CopyPlus,
  Download,
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
  X,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { usePermissions } from "../hooks/usePermissions";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/promotion-management.css";
import "../styles/form-fields.css";
import "../styles/order-management.css";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import MerchantTopbarActions from "../components/merchant/MerchantTopbarActions";
import MerchantBrand from "../components/merchant/MerchantBrand";
import MerchantPagination from "../components/merchant/MerchantPagination";
import SearchableSelect from "../components/SearchableSelect";

import ConfirmDialog from "../components/form/ConfirmDialog";
import { LoadError, SkeletonBusy, SkeletonTable } from "../components/feedback/Skeleton";
import PromotionFormDialog, { draftFrom, emptyDraft, type PromotionDraft } from "../components/merchant/PromotionFormDialog";
import { benefitText, formatBaht, formatThaiDate, statusBadge, TYPE_CLASS, TYPE_LABEL } from "../components/merchant/promotionFormat";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { useDelayedLoading } from "../hooks/useDelayedLoading";
import { ApiError, promotionApi, type Promotion, type PromotionList, type PromotionStatus, type PromotionType, type PromotionWrite } from "../services/api";
import MerchantSidebar from "../components/merchant/MerchantSidebar";


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
  const [notice, setNotice] = useState("");
  const [form, setForm] = useState<{ mode: "create" | "edit"; draft: PromotionDraft; target?: Promotion } | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<Promotion | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [menuFor, setMenuFor] = useState<string | null>(null);
  const [menuPos, setMenuPos] = useState<{ top: number; left: number } | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const noticeTimer = useRef<number | undefined>(undefined);
  const menuAnchorRef = useRef<HTMLButtonElement | null>(null);
  const menuPanelRef = useRef<HTMLDivElement | null>(null);

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
      })
      .catch((error) => { if (!cancelled) setLoadError(error); })
      .finally(() => { if (!cancelled) setListLoading(false); });
    return () => { cancelled = true; };
  }, [shopId, appliedQuery, effective, page, size, reload]);

  const { showSkeleton, markHasData, isRefetching } = useDelayedLoading(listLoading, 150, shopId ?? "none");
  useEffect(() => { if (!listLoading && !loadError) markHasData(); }, [listLoading, loadError, markHasData]);

  const placeMenu = (anchor: HTMLElement) => {
    const rect = anchor.getBoundingClientRect();
    const width = Math.min(220, window.innerWidth - 16);
    const left = Math.min(Math.max(8, rect.right - width), window.innerWidth - width - 8);
    const height = menuPanelRef.current?.offsetHeight ?? 160;
    const below = rect.bottom + 4;
    const top = below + height > window.innerHeight - 8
      ? Math.max(8, rect.top - height - 4)
      : below;
    setMenuPos({ top, left });
  };

  const closeMenu = () => {
    setMenuFor(null);
    setMenuPos(null);
    menuAnchorRef.current = null;
  };

  const openRowMenu = (promotionId: string, anchor: HTMLButtonElement) => {
    if (menuFor === promotionId) {
      closeMenu();
      return;
    }
    menuAnchorRef.current = anchor;
    placeMenu(anchor);
    setMenuFor(promotionId);
  };

  useLayoutEffect(() => {
    if (!menuFor || !menuAnchorRef.current) return;
    const anchor = menuAnchorRef.current;
    const place = () => placeMenu(anchor);
    place();
    const raf = window.requestAnimationFrame(place);
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, true);
    return () => {
      window.cancelAnimationFrame(raf);
      window.removeEventListener("resize", place);
      window.removeEventListener("scroll", place, true);
    };
  }, [menuFor]);

  useEffect(() => {
    if (!menuFor) return;
    const onPointer = (event: PointerEvent) => {
      const target = event.target as Node;
      if (menuAnchorRef.current?.contains(target) || menuPanelRef.current?.contains(target)) return;
      closeMenu();
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") closeMenu();
    };
    window.addEventListener("pointerdown", onPointer);
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("pointerdown", onPointer);
      window.removeEventListener("keydown", onKey);
    };
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
  const clearChipFilters = () => { setQuery(""); setAppliedQuery(""); setStatus(""); setType(""); setPage(0); };
  const hasChipFilters = Boolean(appliedQuery || status || type);
  const statusChipLabel = STATUS_OPTIONS.find(([, value]) => value === status)?.[0] || "";
  const typeChipLabel = TYPE_OPTIONS.find(([, value]) => value === type)?.[0] || "";
  const refresh = () => setReload((value) => value + 1);
  const menuPromotion = menuFor ? visible.find((item) => item.id === menuFor) : null;
  const forbidden = loadError instanceof ApiError && (loadError.status === 403 || loadError.status === 404);
  const pageCount = Math.max(1, data.totalPages);
  const canExport = shopId != null && data.total > 0 && !listLoading
    && effective.status !== null
    && !(effective.types && effective.types.length === 0);
  const exportTitle = shopId == null
    ? "เลือกร้านก่อนส่งออก"
    : !canExport
      ? "ไม่มีโปรโมชั่นตามตัวกรองนี้"
      : "ส่งออก Excel ตามตัวกรองปัจจุบัน";
  const exportPromotions = async () => {
    if (shopId == null || exporting || !canExport) return;
    setExporting(true);
    try {
      await promotionApi.exportExcel(shopId, {
        q: appliedQuery || undefined,
        status: effective.status || undefined,
        type: effective.types,
        lang: "th",
      });
      notify("ส่งออก Excel แล้ว");
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === "EXPORT_EMPTY") {
        notify("ไม่มีโปรโมชั่นตามตัวกรองนี้");
      } else if (caught instanceof ApiError && caught.code === "EXPORT_TOO_LARGE") {
        notify("ข้อมูลมากเกินไป — ลดตัวกรองแล้วลองใหม่");
      } else if (caught instanceof ApiError && (caught.status === 403 || caught.code === "FORBIDDEN" || caught.code === "PERMISSION_DENIED")) {
        notify(deniedTitle(false) || "ส่งออกไม่สำเร็จ");
      } else {
        notify(errorMessage(caught));
      }
    } finally {
      setExporting(false);
    }
  };

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
    closeMenu();
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
    closeMenu();
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
        <MerchantBrand />
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
            <div className="pm-tabs" role="tablist">
              {TABS.map((label) => (
                <button
                  key={label}
                  type="button"
                  role="tab"
                  aria-selected={tab === label}
                  className={tab === label ? "active" : ""}
                  onClick={() => { setTab(label); setPage(0); }}
                >
                  {label}
                  <i className="num">{tabCount[label].toLocaleString()}</i>
                </button>
              ))}
            </div>
            <form
              className="om-filters"
              onSubmit={(event) => {
                event.preventDefault();
                setAppliedQuery(query.trim());
                setPage(0);
                refresh();
              }}
            >
              <div className="om-filters-main">
                <div className="om-filter-search">
                  <span className="form-label">ค้นหาโปรโมชั่น</span>
                  <label className="om-search">
                    <Search aria-hidden />
                    <input
                      aria-label="ค้นหาโปรโมชั่น"
                      value={query}
                      onChange={(event) => setQuery(event.target.value)}
                      placeholder="ค้นหาชื่อโปรโมชั่น หรือรหัสคูปอง..."
                    />
                  </label>
                </div>
                <SearchableSelect
                  label="สถานะ"
                  options={STATUS_OPTIONS.map(([label]) => label)}
                  value={STATUS_OPTIONS.find(([, value]) => value === status)?.[0] || "ทั้งหมด"}
                  onChange={(picked) => {
                    setStatus((STATUS_OPTIONS.find(([label]) => label === picked)?.[1] ?? "") as PromotionStatus | "");
                    setPage(0);
                  }}
                />
                <SearchableSelect
                  label="ประเภท"
                  options={TYPE_OPTIONS.map(([label]) => label)}
                  value={TYPE_OPTIONS.find(([, value]) => value === type)?.[0] || "ทั้งหมด"}
                  onChange={(picked) => {
                    setType((TYPE_OPTIONS.find(([label]) => label === picked)?.[1] ?? "") as PromotionType | "");
                    setPage(0);
                  }}
                />
                <div title="คูปองใช้ได้ทุกช่องทาง (ยังไม่รองรับการแยกช่องทาง)">
                  <SearchableSelect
                    label="ช่องทาง"
                    options={["ทั้งหมด"]}
                    value="ทั้งหมด"
                    disabled
                    onChange={() => undefined}
                  />
                </div>
                <div className="om-filters-actions">
                  <button type="button" onClick={reset}>รีเซ็ต</button>
                  <button type="submit" className="primary">ค้นหา</button>
                </div>
              </div>
            </form>
            <div className="pm-table-tools">
              <div className="pm-table-tools-chips" role="list" aria-label="ตัวกรองที่ใช้อยู่">
                {appliedQuery ? (
                  <div className="om-filter-chip" role="listitem">
                    <span>ค้นหาโปรโมชั่น: {appliedQuery}</span>
                    <button
                      type="button"
                      aria-label="ล้างตัวกรอง ค้นหาโปรโมชั่น"
                      onClick={() => { setQuery(""); setAppliedQuery(""); setPage(0); }}
                    >
                      <X size={14} aria-hidden="true" />
                    </button>
                  </div>
                ) : null}
                {status ? (
                  <div className="om-filter-chip" role="listitem">
                    <span>สถานะ: {statusChipLabel}</span>
                    <button
                      type="button"
                      aria-label="ล้างตัวกรอง สถานะ"
                      onClick={() => { setStatus(""); setPage(0); }}
                    >
                      <X size={14} aria-hidden="true" />
                    </button>
                  </div>
                ) : null}
                {type ? (
                  <div className="om-filter-chip" role="listitem">
                    <span>ประเภท: {typeChipLabel}</span>
                    <button
                      type="button"
                      aria-label="ล้างตัวกรอง ประเภท"
                      onClick={() => { setType(""); setPage(0); }}
                    >
                      <X size={14} aria-hidden="true" />
                    </button>
                  </div>
                ) : null}
                {hasChipFilters ? (
                  <button type="button" className="om-clear-all" onClick={clearChipFilters}>
                    ล้างทั้งหมด
                  </button>
                ) : null}
              </div>
              <button
                type="button"
                className="pm-export-btn"
                disabled={!canExport || exporting}
                title={exportTitle}
                onClick={() => void exportPromotions()}
              >
                <Download aria-hidden="true" />
                <span>{exporting ? "กำลังส่งออก..." : "ส่งออก Excel"}</span>
              </button>
            </div>
            {showSkeleton && listLoading && visible.length === 0 ? (
              <div className="promo-table-wrap" aria-busy="true" aria-label="กำลังโหลดโปรโมชั่น"><SkeletonTable rows={6} cols={8} /></div>
            ) : loadError ? (
              <div className="promo-table-wrap"><LoadError message={forbidden ? "คุณไม่มีสิทธิ์ดูโปรโมชั่นของร้านนี้" : errorMessage(loadError)} onRetry={refresh} retryLabel="ลองใหม่" /></div>
            ) : (
              <SkeletonBusy refetching={isRefetching} busy={isRefetching}>
                <div className="promo-table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th className="col-index">#</th>
                        <th className="col-name">ชื่อโปรโมชั่น / คูปอง</th>
                        <th className="col-type">ประเภท</th>
                        <th className="col-benefit">ส่วนลด / เงื่อนไข</th>
                        <th className="col-period">ช่วงเวลา</th>
                        <th className="col-usage">การใช้งาน</th>
                        <th className="col-status">สถานะ</th>
                        <th className="col-actions">จัดการ</th>
                      </tr>
                    </thead>
                    <tbody>{visible.map((promotion, index) => {
                      const limit = promotion.usageLimitTotal;
                      const percent = limit ? Math.min(100, Math.round((promotion.usedCount / limit) * 100)) : 0;
                      const badge = statusBadge(promotion);
                      const label = promotion.code ?? promotion.name;
                      const benefit = benefitText(promotion);
                      const conditions = conditionLines(promotion);
                      const starts = formatThaiDate(promotion.startsAt);
                      const ends = formatThaiDate(promotion.endsAt);
                      const usageLabel = limit
                        ? `${promotion.usedCount.toLocaleString()} / ${limit.toLocaleString()} ครั้ง`
                        : `${promotion.usedCount.toLocaleString()} / ไม่จำกัด ครั้ง`;
                      return <tr key={promotion.id}>
                        <td className="col-index num">{page * size + index + 1}</td>
                        <td className="col-name">
                          <div className="promo-name">
                            <PromoIcon kind={iconOf(promotion)} />
                            <p>
                              <b className="num" title={promotion.code || undefined}>{promotion.code ?? "—"}</b>
                              <small title={promotion.name}>{promotion.name}</small>
                            </p>
                          </div>
                        </td>
                        <td className="col-type"><span className={`promo-type ${TYPE_CLASS[promotion.type]}`}>{TYPE_LABEL[promotion.type]}</span></td>
                        <td className="col-benefit">
                          <div className="promo-cell-stack">
                            <b className="num" title={benefit}>{benefit}</b>
                            {conditions.map((line) => <small className="num" key={line} title={line}>{line}</small>)}
                          </div>
                        </td>
                        <td className="col-period">
                          <div className="promo-cell-stack" title={`${starts} – ${ends}`}>
                            <span className="num">{starts}</span>
                            <small className="num">ถึง {ends}</small>
                          </div>
                        </td>
                        <td className="col-usage">
                          <div className="promo-usage">
                            <b className="num" title={usageLabel}>{usageLabel}</b>
                            {limit ? (
                              <div className="promo-usage-meter">
                                <div className="promo-progress" aria-hidden="true"><i style={{ width: `${percent}%` }} /></div>
                                <small className="num">{percent}%</small>
                              </div>
                            ) : (
                              <small>{promotion.showOnStorefront ? "แสดงบนหน้าร้าน" : "ไม่แสดงบนหน้าร้าน"}</small>
                            )}
                          </div>
                        </td>
                        <td className="col-status"><span className={`promo-status ${badge.className}`}>{badge.label}</span></td>
                        <td className="col-actions pm-col-actions">
                          <div className="pm-row-actions">
                            <button
                              type="button"
                              disabled={!canManage}
                              title={deniedTitle(canManage)}
                              aria-label={`แก้ไข ${label}`}
                              onClick={() => setForm({ mode: "edit", draft: draftFrom(promotion), target: promotion })}
                            >
                              <Pencil />
                            </button>
                            <div className="pm-share-wrap">
                              <button
                                type="button"
                                disabled={!canManage || busyId === promotion.id}
                                title={deniedTitle(canManage)}
                                aria-label={`ตัวเลือก ${label}`}
                                aria-haspopup="menu"
                                aria-expanded={menuFor === promotion.id}
                                onClick={(event) => openRowMenu(promotion.id, event.currentTarget)}
                              >
                                <Ellipsis />
                              </button>
                            </div>
                          </div>
                        </td>
                      </tr>;
                    })}</tbody>
                  </table>
                  {visible.length === 0 && !listLoading && <div className="promo-empty">{filtered ? "ไม่พบโปรโมชั่นที่ตรงกับตัวกรอง" : "ยังไม่มีโปรโมชั่น — กด \"สร้างโปรโมชั่น / คูปอง\" เพื่อเริ่ม"}</div>}
                </div>
              </SkeletonBusy>
            )}
            <MerchantPagination
              page={page}
              pageCount={pageCount}
              size={size}
              total={data.total}
              onPageChange={setPage}
              onSizeChange={(next) => { setSize(next); setPage(0); }}
            />
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
      {menuPromotion && menuPos
        ? createPortal(
            <div
              ref={menuPanelRef}
              className="pm-row-menu is-portaled"
              role="menu"
              style={{ top: menuPos.top, left: menuPos.left }}
            >
              {menuPromotion.status !== "EXPIRED" ? (
                <button type="button" role="menuitem" onClick={() => void toggleActive(menuPromotion)}>
                  {menuPromotion.active ? <><Pause />หยุดชั่วคราว</> : <><Play />เปิดใช้งานอีกครั้ง</>}
                </button>
              ) : null}
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  closeMenu();
                  setForm({ mode: "create", draft: draftFrom(menuPromotion, true) });
                }}
              >
                <CopyPlus />ทำสำเนา
              </button>
              <button type="button" role="menuitem" onClick={() => void copyCode(menuPromotion)}>
                <ClipboardCopy />คัดลอกรหัส
              </button>
              <button
                type="button"
                role="menuitem"
                className="danger"
                onClick={() => {
                  closeMenu();
                  setConfirmDelete(menuPromotion);
                }}
              >
                <Trash2 />ลบ
              </button>
            </div>,
            document.body,
          )
        : null}
      {notice && <div className="merchant-toast" role="status">{notice}</div>}
    </main>
  );
}
