import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  Download,
  Eye,
  PackageCheck,
  Search,
  ShoppingBag,
  SlidersHorizontal,
  Tags,
  Truck,
  UserRound,
  X,
} from "lucide-react";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import MerchantTopbarActions from "../components/merchant/MerchantTopbarActions";
import MerchantOrderDrawer from "../components/merchant/MerchantOrderDrawer";
import MerchantPagination from "../components/merchant/MerchantPagination";
import OrderBulkBar, { type BulkRunPayload, type BulkSelectionItem } from "../components/merchant/OrderBulkBar";
import OrderBulkResults, { type BulkResultRow } from "../components/merchant/OrderBulkResults";
import OrderTagChips from "../components/merchant/OrderTagChips";
import OrderTagManager from "../components/merchant/OrderTagManager";
import { LoadError, SkeletonTable } from "../components/feedback/Skeleton";
import DateRangePicker from "../components/form/DateRangePicker";
import SearchableSelect from "../components/SearchableSelect";
import { labelOf, uniqueOptions, valueOf } from "../components/merchant/selectOptions";
import { formatBaht } from "../components/cart/cartFormat";
import {
  FULFILLMENT_STATUSES,
  PAYMENT_STATUSES,
  formatBangkokParts,
  orderErrorMessage,
  paymentTone,
  statusTone,
} from "../components/merchant/orderFormat";
import { FEATURES } from "../config/features";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { usePermissions } from "../hooks/usePermissions";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { ApiError, carrierApi, merchantOrderApi, type Carrier, type OrderPage, type OrderSummary } from "../services/api";
import {
  bulkAction,
  bulkTags,
  createTag,
  listTags,
  setOrderTags,
  type OrderTag,
} from "../services/merchantOrderExtensions";
import "../styles/form-fields.css";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/order-management.css";
import MerchantSidebar from "../components/merchant/MerchantSidebar";
import MerchantBrand from "../components/merchant/MerchantBrand";

const BULK_MAX = 50;


const TABS = [
  "all", "AWAITING", "PENDING_PAYMENT", "PAID_AWAITING_MERCHANT_CONFIRM", "MERCHANT_CONFIRMED",
  "DISPATCHED", "COMPLETED", "CANCELLED", "REFUND_REQUESTED",
];
const SIZES = [10, 20, 50];
const KPI_STATUSES = [undefined, "PENDING_PAYMENT", "MERCHANT_CONFIRMED", "COMPLETED"] as const;

export default function OrderManagementPage() {
  const { t } = useTranslation("merchant");
  const { localeTag } = usePublicTranslation();
  const { user, loading } = useAuth();
  const { shopId } = useActiveShop();
  const { canInShop, deniedTitle } = usePermissions();
  const canReadCustomers = canInShop("CUSTOMER_READ");
  const canExportOrders = canInShop("ORDER_EXPORT");
  const [exporting, setExporting] = useState(false);
  const navigate = useNavigate();
  const { orderId: pathOrderId } = useParams<{ orderId?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [query, setQuery] = useState(() => searchParams.get("q")?.trim() || "");
  const [q, setQ] = useState(() => searchParams.get("q")?.trim() || "");
  const [tab, setTab] = useState("all");
  const [payment, setPayment] = useState("");
  const [fulfillment, setFulfillment] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [customerFilter, setCustomerFilter] = useState<{ key: string; name: string } | null>(() => {
    const key = searchParams.get("customer")?.trim() || "";
    if (!key) return null;
    const name = (searchParams.get("customerName") || "").trim().slice(0, 60);
    return { key, name };
  });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [data, setData] = useState<OrderPage | null>(null);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [kpis, setKpis] = useState<(number | null)[]>([null, null, null, null]);
  const openId = (pathOrderId?.trim() || searchParams.get("order")?.trim() || null);
  const drawerTriggerRef = useRef<HTMLElement | null>(null);
  const [notice, setNotice] = useState("");
  const [tone, setTone] = useState<"ok" | "fail">("ok");
  const toastTimer = useRef<number | undefined>(undefined);
  const qRef = useRef(searchParams.get("q")?.trim() || "");
  const money = (value: number | string | null | undefined) => formatBaht(value, localeTag);
  const [paymentMethod, setPaymentMethod] = useState("");
  const [carrierCode, setCarrierCode] = useState("");
  const [hasTracking, setHasTracking] = useState("");
  const [tagFilter, setTagFilter] = useState("");
  const [carriers, setCarriers] = useState<Carrier[]>([]);
  const [carriersLoading, setCarriersLoading] = useState(true);
  const [shopTags, setShopTags] = useState<OrderTag[]>([]);
  const [tagsLoading, setTagsLoading] = useState(FEATURES.orderTagsApi);
  const [tagManagerOpen, setTagManagerOpen] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(() => {
    try {
      return localStorage.getItem("tsf.orders.filtersAdvancedOpen") === "true";
    } catch {
      return false;
    }
  });
  const [selected, setSelected] = useState<Map<string, BulkSelectionItem>>(new Map());
  const [bulkRunning, setBulkRunning] = useState(false);
  const [bulkProgress, setBulkProgress] = useState<{ current: number; total: number } | null>(null);
  const [bulkResults, setBulkResults] = useState<BulkResultRow[] | null>(null);
  const bulkStopRef = useRef(false);
  // Idempotency-Key reused for retries of the same action + selection until a server response is shown
  const bulkIdemRef = useRef<{ fingerprint: string; key: string } | null>(null);

  const notify = useCallback((text: string, nextTone: "ok" | "fail" = "ok") => {
    setTone(nextTone);
    setNotice(text);
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setNotice(""), 2400);
  }, []);
  useEffect(() => () => window.clearTimeout(toastTimer.current), []);

  /** Open order drawer via `?order=` without clearing other list filters; deep link `/merchant/orders/:id` also works. */
  const openOrder = useCallback((id: string, trigger?: HTMLElement | null) => {
    drawerTriggerRef.current = trigger ?? (document.activeElement instanceof HTMLElement ? document.activeElement : null);
    if (pathOrderId) {
      const next = new URLSearchParams(searchParams);
      next.set("order", id);
      navigate({ pathname: "/merchant/orders", search: next.toString() ? `?${next}` : "" }, { replace: true });
      return;
    }
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (next.get("order") === id) return current;
      next.set("order", id);
      return next;
    }, { replace: true });
  }, [navigate, pathOrderId, searchParams, setSearchParams]);

  const closeOrder = useCallback(() => {
    const restore = drawerTriggerRef.current;
    drawerTriggerRef.current = null;
    if (pathOrderId) {
      const next = new URLSearchParams(searchParams);
      next.delete("order");
      navigate({ pathname: "/merchant/orders", search: next.toString() ? `?${next}` : "" }, { replace: true });
    } else {
      setSearchParams((current) => {
        if (!current.get("order")) return current;
        const next = new URLSearchParams(current);
        next.delete("order");
        return next;
      }, { replace: true });
    }
    window.requestAnimationFrame(() => {
      if (restore?.isConnected) restore.focus();
    });
  }, [navigate, pathOrderId, searchParams, setSearchParams]);

  useEffect(() => {
    if (!pathOrderId) return;
    const next = new URLSearchParams(window.location.search);
    next.set("order", pathOrderId);
    navigate({ pathname: "/merchant/orders", search: `?${next}` }, { replace: true });
  }, [navigate, pathOrderId]);

  useEffect(() => {
    const fromUrl = searchParams.get("q")?.trim() || "";
    if (fromUrl === qRef.current) return;
    qRef.current = fromUrl;
    setQuery(fromUrl);
    setQ(fromUrl);
    setPage(0);
  }, [searchParams]);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      const next = query.trim();
      if (next === qRef.current) return;
      qRef.current = next;
      setQ(next);
      setPage(0);
      setSearchParams((current) => {
        const nextParams = new URLSearchParams(current);
        if (next) nextParams.set("q", next);
        else nextParams.delete("q");
        return nextParams;
      }, { replace: true });
    }, 300);
    return () => window.clearTimeout(handle);
  }, [query, setSearchParams]);

  useEffect(() => {
    if (shopId == null) return;
    let alive = true;
    carrierApi.list().then((rows) => { if (alive) setCarriers(rows.filter((c) => c.active)); }).catch(() => undefined)
      .finally(() => { if (alive) setCarriersLoading(false); });
    if (FEATURES.orderTagsApi) {
      listTags(shopId).then((rows) => { if (alive) setShopTags(rows); }).catch(() => undefined)
        .finally(() => { if (alive) setTagsLoading(false); });
    }
    return () => { alive = false; };
  }, [shopId, reload]);

  useEffect(() => {
    setSelected(new Map());
  }, [tab, payment, fulfillment, dateFrom, dateTo, q, page, size, paymentMethod, carrierCode, hasTracking, tagFilter, customerFilter?.key]);

  const clearCustomerFilter = useCallback(() => {
    setCustomerFilter(null);
    setPage(0);
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.delete("customer");
      next.delete("customerName");
      return next;
    }, { replace: true });
  }, [setSearchParams]);

  const applyCustomerFilter = useCallback((key: string, name: string) => {
    const trimmedKey = key.trim();
    if (!trimmedKey) return;
    if (customerFilter?.key === trimmedKey) return;
    const trimmedName = name.trim().slice(0, 60);
    setCustomerFilter({ key: trimmedKey, name: trimmedName });
    setPage(0);
    setSearchParams((params) => {
      const next = new URLSearchParams(params);
      next.set("customer", trimmedKey);
      if (trimmedName) next.set("customerName", trimmedName);
      else next.delete("customerName");
      return next;
    });
  }, [customerFilter?.key, setSearchParams]);

  useEffect(() => {
    const key = searchParams.get("customer")?.trim() || "";
    const name = (searchParams.get("customerName") || "").trim().slice(0, 60);
    setCustomerFilter((current) => {
      if (!key) return current == null ? current : null;
      if (current?.key === key && (current.name || "") === name) return current;
      return { key, name };
    });
  }, [searchParams]);

  useEffect(() => {
    if (shopId == null) return;
    let alive = true;
    setFetching(true);
    setError(null);
    const params: Record<string, string | number | boolean | null | undefined> = {
      status: tab !== "all" && tab !== "AWAITING" ? tab : undefined,
      acceptance: tab === "AWAITING" ? "AWAITING" : undefined,
      paymentStatus: payment || undefined,
      fulfillmentStatus: fulfillment || undefined,
      from: dateFrom || undefined,
      to: dateTo || undefined,
      q: q || undefined,
      page,
      size,
      sort: "placedAt",
      direction: "desc",
    };
    if (customerFilter?.key) params.customer = customerFilter.key;
    if (FEATURES.orderFiltersApi) {
      if (paymentMethod) params.paymentMethod = paymentMethod;
      if (carrierCode) params.carrierCode = carrierCode;
      if (hasTracking === "true" || hasTracking === "false") params.hasTracking = hasTracking === "true";
      if (FEATURES.orderTagsApi && tagFilter) params.tag = tagFilter;
    }
    merchantOrderApi
      .list(shopId, params)
      .then((result) => { if (alive) setData(result); })
      .catch((caught) => {
        if (!alive) return;
        setError(orderErrorMessage(t, caught));
        if (caught instanceof ApiError && caught.code === "CUSTOMER_KEY_INVALID") clearCustomerFilter();
      })
      .finally(() => { if (alive) setFetching(false); });
    return () => { alive = false; };
  }, [shopId, tab, payment, fulfillment, dateFrom, dateTo, q, page, size, reload, t, paymentMethod, carrierCode, hasTracking, tagFilter, customerFilter?.key, clearCustomerFilter]);

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

  const selectionOf = (order: OrderSummary): BulkSelectionItem => ({
    version: order.version,
    orderNumber: order.orderNumber,
    status: order.status,
    awaitingAcceptance: order.awaitingAcceptance,
  });

  const toggleSelect = (order: OrderSummary) => {
    setSelected((current) => {
      const next = new Map(current);
      if (next.has(order.id)) {
        next.delete(order.id);
        return next;
      }
      if (next.size >= BULK_MAX) {
        notify(t("orderManagement.errors.BULK_TOO_MANY"), "fail");
        return current;
      }
      next.set(order.id, selectionOf(order));
      return next;
    });
  };

  const runBulk = async (payload: BulkRunPayload) => {
    if (!shopId || selected.size === 0 || bulkRunning) return;
    const entries = [...selected.entries()];
    bulkStopRef.current = false;
    setBulkRunning(true);
    setBulkProgress({ current: 0, total: entries.length });
    const results: BulkResultRow[] = [];
    const fingerprint = JSON.stringify([
      payload,
      entries.map(([orderId, row]) => [orderId, row.version]).sort((a, b) => String(a[0]).localeCompare(String(b[0]))),
    ]);
    const idemKey = () => {
      if (bulkIdemRef.current?.fingerprint !== fingerprint) {
        bulkIdemRef.current = { fingerprint, key: crypto.randomUUID() };
      }
      return bulkIdemRef.current.key;
    };
    let serverResponded = false;

    const pushFail = (orderId: string, orderNumber: string, code: string | null, message?: string | null) => {
      results.push({ orderId, orderNumber, ok: false, code, message: message ?? null, newVersion: null });
    };

    if (FEATURES.bulkOrdersApi && payload.kind !== "tags") {
      const idem = idemKey();
      try {
        const body = {
          items: entries.map(([orderId, row]) => ({ orderId, expectedVersion: row.version })),
          reason: payload.kind === "cancel" ? payload.reason : undefined,
          status: payload.kind === "process" ? payload.status : undefined,
          carrierCode: payload.kind === "ship" ? payload.carrierCode : undefined,
          trackingNumbers: payload.kind === "ship" ? payload.trackingNumbers : undefined,
        };
        const response = await bulkAction(shopId, payload.kind, body, idem);
        serverResponded = true;
        response.results.forEach((row) => {
          const meta = selected.get(row.orderId);
          results.push({ ...row, orderNumber: meta?.orderNumber ?? row.orderId });
        });
      } catch (caught) {
        entries.forEach(([orderId, row]) => {
          pushFail(orderId, row.orderNumber, caught instanceof ApiError ? caught.code : "REQUEST_FAILED", orderErrorMessage(t, caught));
        });
      }
    } else if (FEATURES.bulkOrdersApi && FEATURES.orderTagsApi && payload.kind === "tags") {
      const idem = idemKey();
      try {
        const response = await bulkTags(shopId, {
          items: entries.map(([orderId, row]) => ({ orderId, expectedVersion: row.version })),
          addTagIds: payload.addTagIds,
          removeTagIds: payload.removeTagIds,
        }, idem);
        serverResponded = true;
        response.results.forEach((row) => {
          const meta = selected.get(row.orderId);
          results.push({ ...row, orderNumber: meta?.orderNumber ?? row.orderId });
        });
      } catch (caught) {
        entries.forEach(([orderId, row]) => {
          pushFail(orderId, row.orderNumber, caught instanceof ApiError ? caught.code : "REQUEST_FAILED", orderErrorMessage(t, caught));
        });
      }
    } else {
      for (let index = 0; index < entries.length; index += 1) {
        if (bulkStopRef.current) break;
        const [orderId, row] = entries[index];
        setBulkProgress({ current: index + 1, total: entries.length });
        try {
          if (payload.kind === "tags") {
            if (!FEATURES.orderTagsApi) {
              pushFail(orderId, row.orderNumber, "FEATURE_DISABLED");
            } else {
              const current = await merchantOrderApi.get(shopId, orderId);
              const existing = (current.tags ?? []).map((tag) => tag.id);
              const nextIds = [...new Set([...existing, ...payload.addTagIds])].filter((id) => !payload.removeTagIds.includes(id));
              const result = await setOrderTags(shopId, orderId, { tagIds: nextIds, expectedVersion: row.version });
              results.push({ orderId, orderNumber: row.orderNumber, ok: true, code: null, newVersion: result.version });
            }
          } else {
            const endpoint = payload.kind;
            const extra: Record<string, string> = {};
            if (payload.kind === "process") extra.status = payload.status;
            if (payload.kind === "ship") {
              extra.carrierCode = payload.carrierCode;
              extra.trackingNumber = payload.trackingNumbers[orderId] ?? "";
            }
            if (payload.kind === "cancel") extra.reason = payload.reason;
            const result = await merchantOrderApi.act(shopId, orderId, endpoint, {
              expectedVersion: row.version,
              ...extra,
            });
            results.push({
              orderId,
              orderNumber: row.orderNumber,
              ok: true,
              code: null,
              newVersion: result.order?.version ?? null,
            });
          }
        } catch (caught) {
          pushFail(orderId, row.orderNumber, caught instanceof ApiError ? caught.code : "REQUEST_FAILED", orderErrorMessage(t, caught));
        }
      }
    }

    setBulkRunning(false);
    setBulkProgress(null);
    // keep the key after a network / 5xx failure so "retry" replays instead of re-running; drop it once results arrived
    if (serverResponded) bulkIdemRef.current = null;
    setBulkResults(results);
    const okIds = new Set(results.filter((row) => row.ok).map((row) => row.orderId));
    setSelected((current) => {
      const next = new Map(current);
      okIds.forEach((id) => next.delete(id));
      return next;
    });
    setReload((n) => n + 1);
  };

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

  const rawItems = data?.items ?? [];
  const items = !FEATURES.orderFiltersApi && paymentMethod
    ? rawItems.filter((row) => row.paymentMethod === paymentMethod)
    : rawItems;
  const total = data?.totalItems ?? 0;
  const pages = Math.max(1, data?.totalPages ?? 1);
  const pageSelectedCount = items.filter((row) => selected.has(row.id)).length;
  const allPageSelected = items.length > 0 && pageSelectedCount === items.length;
  const clearFilters = () => {
    setQuery("");
    qRef.current = "";
    setQ("");
    setPayment("");
    setFulfillment("");
    setDateFrom("");
    setDateTo("");
    setPaymentMethod("");
    setCarrierCode("");
    setHasTracking("");
    setTagFilter("");
    setPage(0);
    clearCustomerFilter();
  };
  const reset = () => {
    clearFilters();
    setTab("all");
  };
  const toggleAdvanced = () => {
    setAdvancedOpen((prev) => {
      const next = !prev;
      try {
        localStorage.setItem("tsf.orders.filtersAdvancedOpen", String(next));
      } catch {
        /* ignore quota / private mode */
      }
      return next;
    });
  };
  const advancedCount = [
    paymentMethod,
    FEATURES.orderFiltersApi ? carrierCode : "",
    FEATURES.orderFiltersApi ? hasTracking : "",
    FEATURES.orderTagsApi ? tagFilter : "",
  ].filter(Boolean).length;
  const showAdvanced = advancedOpen || advancedCount > 0;
  const hasActiveFilters = Boolean(
    q || dateFrom || dateTo || payment || fulfillment || paymentMethod
      || (FEATURES.orderFiltersApi && (carrierCode || hasTracking))
      || (FEATURES.orderTagsApi && tagFilter)
      || customerFilter,
  );
  const allLabel = t("orderManagement.filters.all");
  const carrierOptions = uniqueOptions([
    { value: "", label: allLabel },
    ...carriers.map((carrier) => ({ value: carrier.code, label: carrier.nameTh || carrier.code })),
  ]);
  const trackingOptions = [
    { value: "", label: allLabel },
    { value: "true", label: t("orderManagement.filters.trackingYes") },
    { value: "false", label: t("orderManagement.filters.trackingNo") },
  ];
  const tagOptions = uniqueOptions([
    { value: "", label: allLabel },
    ...shopTags.map((tag) => ({ value: tag.id, label: tag.name })),
  ]);
  const carrierLabel = (carrierCode && labelOf(carrierOptions, carrierCode)) || carrierCode;
  const tagLabel = (tagFilter && labelOf(tagOptions, tagFilter)) || tagFilter;
  const paymentMethodLabel = paymentMethod === "BANK_TRANSFER"
    ? t("orderManagement.payment.provider.BANK_TRANSFER")
    : paymentMethod === "COD"
      ? t("orderManagement.payment.provider.COD")
      : paymentMethod;
  const trackingLabel = hasTracking === "true"
    ? t("orderManagement.filters.trackingYes")
    : hasTracking === "false"
      ? t("orderManagement.filters.trackingNo")
      : hasTracking;
  const dateChipValue = dateFrom && dateTo
    ? t("orderManagement.filters.chipDate", { from: dateFrom, to: dateTo })
    : dateFrom || dateTo;
  const clearChipLabel = (label: string) => t("orderManagement.filters.clearChip", { label });
  const changeTab = (next: string) => {
    setTab(next);
    setPage(0);
  };
  const kpiValue = (index: number) => (kpis[index] == null ? "—" : kpis[index]!.toLocaleString(localeTag));
  const tabLabel = (key: string) =>
    key === "all" ? t("orderManagement.tabs.all") : key === "AWAITING" ? t("orderManagement.tabs.awaiting") : t(`orderManagement.status.${key}`);
  const empty = !fetching && !error && items.length === 0;

  const exportOrders = async () => {
    if (shopId == null || exporting || !canExportOrders) return;
    const exportShopId = shopId;
    const params: Record<string, string | number | boolean | null | undefined> = {
      status: tab !== "all" && tab !== "AWAITING" ? tab : undefined,
      acceptance: tab === "AWAITING" ? "AWAITING" : undefined,
      paymentStatus: payment || undefined,
      fulfillmentStatus: fulfillment || undefined,
      from: dateFrom || undefined,
      to: dateTo || undefined,
      q: q || undefined,
      sort: "placedAt",
      direction: "desc",
    };
    if (customerFilter?.key) params.customer = customerFilter.key;
    if (FEATURES.orderFiltersApi) {
      if (paymentMethod) params.paymentMethod = paymentMethod;
      if (carrierCode) params.carrierCode = carrierCode;
      if (hasTracking === "true" || hasTracking === "false") params.hasTracking = hasTracking === "true";
      if (FEATURES.orderTagsApi && tagFilter) params.tag = tagFilter;
    }
    setExporting(true);
    try {
      await merchantOrderApi.exportXlsx(exportShopId, params);
      if (shopId !== exportShopId) {
        notify(t("orderManagement.exportStale"), "fail");
        return;
      }
      notify(t("orderManagement.exportDone"));
    } catch (caught) {
      notify(orderErrorMessage(t, caught), "fail");
    } finally {
      setExporting(false);
    }
  };

  return (
    <main className="merchant-shell om-shell">
      <header className="merchant-topbar">
        <MerchantBrand />
        <MerchantTopbarSearch />
        <MerchantTopbarActions />
      </header>
      <MerchantSidebar active="orders"/>
      <section className="pm-content om-content">
        <header className="pm-heading om-heading">
          <div>
            <h1>{t("orderManagement.title")}</h1>
            <p>{t("orderManagement.subtitle")}</p>
          </div>
          <div className="pm-actions">
            {FEATURES.orderTagsApi ? (
              <button type="button" onClick={() => setTagManagerOpen(true)}>
                <Tags />
                {t("orderManagement.tags.manage")}
              </button>
            ) : null}
            <button
              type="button"
              disabled={!canExportOrders || exporting || shopId == null}
              title={canExportOrders ? t("orderManagement.exportHint") : deniedTitle(canExportOrders)}
              onClick={() => void exportOrders()}
            >
              <Download />
              {exporting ? t("orderManagement.exporting") : t("orderManagement.export")}
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
            <div className="om-filters-main">
              <div className="om-filter-search">
                <span className="form-label">{t("orderManagement.filters.searchLabel")}</span>
                <label className="om-search">
                  <Search aria-hidden />
                  <input
                    aria-label={t("orderManagement.filters.searchLabel")}
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder={t("orderManagement.filters.searchPlaceholder")}
                  />
                </label>
              </div>
              <DateRangePicker
                className="om-filter-range"
                label={t("orderManagement.filters.dateRange")}
                from={dateFrom}
                to={dateTo}
                clearLabel={t("orderManagement.filters.clear")}
                applyLabel={t("orderManagement.filters.apply")}
                presetsLabel={{
                  d7: t("orderManagement.filters.presets.d7"),
                  d30: t("orderManagement.filters.presets.d30"),
                  month: t("orderManagement.filters.presets.month"),
                }}
                onChange={({ from, to }) => {
                  setDateFrom(from);
                  setDateTo(to);
                  setPage(0);
                }}
              />
              <SearchableSelect
                label={t("orderManagement.filters.payment")}
                options={[t("orderManagement.filters.all"), ...PAYMENT_STATUSES.map((s) => t(`orderManagement.paymentStatus.${s}`))]}
                value={payment ? t(`orderManagement.paymentStatus.${payment}`) : t("orderManagement.filters.all")}
                onChange={(picked) => {
                  const next = PAYMENT_STATUSES.find((s) => t(`orderManagement.paymentStatus.${s}`) === picked) || "";
                  setPayment(next);
                  setPage(0);
                }}
              />
              <SearchableSelect
                label={t("orderManagement.filters.fulfillment")}
                options={[t("orderManagement.filters.all"), ...FULFILLMENT_STATUSES.map((s) => t(`orderManagement.fulfillmentStatus.${s}`))]}
                value={fulfillment ? t(`orderManagement.fulfillmentStatus.${fulfillment}`) : t("orderManagement.filters.all")}
                onChange={(picked) => {
                  const next = FULFILLMENT_STATUSES.find((s) => t(`orderManagement.fulfillmentStatus.${s}`) === picked) || "";
                  setFulfillment(next);
                  setPage(0);
                }}
              />
              <button
                type="button"
                className="om-filters-more"
                aria-expanded={showAdvanced}
                aria-controls="om-filters-advanced"
                onClick={toggleAdvanced}
              >
                <SlidersHorizontal aria-hidden />
                {advancedCount > 0
                  ? t("orderManagement.filters.moreFiltersCount", { count: advancedCount })
                  : t("orderManagement.filters.moreFilters")}
              </button>
              <div className="om-filters-actions">
                <button type="button" onClick={reset}>
                  {t("orderManagement.filters.reset")}
                </button>
                <button type="submit" className="primary">{t("orderManagement.filters.search")}</button>
              </div>
            </div>
            <div
              id="om-filters-advanced"
              className="om-filters-advanced"
              hidden={!showAdvanced}
            >
              <SearchableSelect
                label={FEATURES.orderFiltersApi
                  ? t("orderManagement.filters.paymentMethod")
                  : `${t("orderManagement.filters.paymentMethod")} (${t("orderManagement.filters.localOnly")})`}
                options={[
                  t("orderManagement.filters.all"),
                  t("orderManagement.payment.provider.BANK_TRANSFER"),
                  t("orderManagement.payment.provider.COD"),
                ]}
                value={
                  paymentMethod === "BANK_TRANSFER"
                    ? t("orderManagement.payment.provider.BANK_TRANSFER")
                    : paymentMethod === "COD"
                      ? t("orderManagement.payment.provider.COD")
                      : t("orderManagement.filters.all")
                }
                onChange={(picked) => {
                  const next =
                    picked === t("orderManagement.payment.provider.BANK_TRANSFER")
                      ? "BANK_TRANSFER"
                      : picked === t("orderManagement.payment.provider.COD")
                        ? "COD"
                        : "";
                  setPaymentMethod(next);
                  setPage(0);
                }}
              />
              {FEATURES.orderFiltersApi ? (
                <>
                  <SearchableSelect
                    label={t("orderManagement.filters.carrier")}
                    options={carrierOptions.map((opt) => opt.label)}
                    value={labelOf(carrierOptions, carrierCode, allLabel)}
                    loading={carriersLoading}
                    onChange={(picked) => { setCarrierCode(valueOf(carrierOptions, picked)); setPage(0); }}
                  />
                  <SearchableSelect
                    label={t("orderManagement.filters.hasTracking")}
                    options={trackingOptions.map((opt) => opt.label)}
                    value={labelOf(trackingOptions, hasTracking, allLabel)}
                    onChange={(picked) => { setHasTracking(valueOf(trackingOptions, picked)); setPage(0); }}
                  />
                </>
              ) : null}
              {FEATURES.orderTagsApi ? (
                <SearchableSelect
                  label={t("orderManagement.filters.tag")}
                  options={tagOptions.map((opt) => opt.label)}
                  value={!tagsLoading && shopTags.length === 0 ? "" : labelOf(tagOptions, tagFilter, allLabel)}
                  placeholder={t("orderManagement.filters.noTags")}
                  loading={tagsLoading}
                  disabled={!tagsLoading && shopTags.length === 0}
                  onChange={(picked) => { setTagFilter(valueOf(tagOptions, picked)); setPage(0); }}
                />
              ) : null}
            </div>
          </form>
          {hasActiveFilters ? (
            <div className="om-active-filters" role="list">
              {q ? (
                <div className="om-filter-chip" role="listitem">
                  <span>{t("orderManagement.filters.searchLabel")}: {q}</span>
                  <button
                    type="button"
                    aria-label={clearChipLabel(t("orderManagement.filters.searchLabel"))}
                    onClick={() => { setQuery(""); qRef.current = ""; setQ(""); setPage(0); }}
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                </div>
              ) : null}
              {dateFrom || dateTo ? (
                <div className="om-filter-chip" role="listitem">
                  <span>{t("orderManagement.filters.dateRange")}: {dateChipValue}</span>
                  <button
                    type="button"
                    aria-label={clearChipLabel(t("orderManagement.filters.dateRange"))}
                    onClick={() => { setDateFrom(""); setDateTo(""); setPage(0); }}
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                </div>
              ) : null}
              {payment ? (
                <div className="om-filter-chip" role="listitem">
                  <span>{t("orderManagement.filters.payment")}: {t(`orderManagement.paymentStatus.${payment}`)}</span>
                  <button
                    type="button"
                    aria-label={clearChipLabel(t("orderManagement.filters.payment"))}
                    onClick={() => { setPayment(""); setPage(0); }}
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                </div>
              ) : null}
              {fulfillment ? (
                <div className="om-filter-chip" role="listitem">
                  <span>{t("orderManagement.filters.fulfillment")}: {t(`orderManagement.fulfillmentStatus.${fulfillment}`)}</span>
                  <button
                    type="button"
                    aria-label={clearChipLabel(t("orderManagement.filters.fulfillment"))}
                    onClick={() => { setFulfillment(""); setPage(0); }}
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                </div>
              ) : null}
              {paymentMethod ? (
                <div className="om-filter-chip" role="listitem">
                  <span>{t("orderManagement.filters.paymentMethod")}: {paymentMethodLabel}</span>
                  <button
                    type="button"
                    aria-label={clearChipLabel(t("orderManagement.filters.paymentMethod"))}
                    onClick={() => { setPaymentMethod(""); setPage(0); }}
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                </div>
              ) : null}
              {FEATURES.orderFiltersApi && carrierCode ? (
                <div className="om-filter-chip" role="listitem">
                  <span>{t("orderManagement.filters.carrier")}: {carrierLabel}</span>
                  <button
                    type="button"
                    aria-label={clearChipLabel(t("orderManagement.filters.carrier"))}
                    onClick={() => { setCarrierCode(""); setPage(0); }}
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                </div>
              ) : null}
              {FEATURES.orderFiltersApi && hasTracking ? (
                <div className="om-filter-chip" role="listitem">
                  <span>{t("orderManagement.filters.hasTracking")}: {trackingLabel}</span>
                  <button
                    type="button"
                    aria-label={clearChipLabel(t("orderManagement.filters.hasTracking"))}
                    onClick={() => { setHasTracking(""); setPage(0); }}
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                </div>
              ) : null}
              {FEATURES.orderTagsApi && tagFilter ? (
                <div className="om-filter-chip" role="listitem">
                  <span>{t("orderManagement.filters.tag")}: {tagLabel}</span>
                  <button
                    type="button"
                    aria-label={clearChipLabel(t("orderManagement.filters.tag"))}
                    onClick={() => { setTagFilter(""); setPage(0); }}
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                </div>
              ) : null}
              {customerFilter ? (
                <div className="om-filter-chip" role="listitem">
                  <span>
                    {t("orderManagement.filters.customerChip", {
                      name: customerFilter.name || t("orderManagement.filters.customerUnknown"),
                    })}
                  </span>
                  <button
                    type="button"
                    aria-label={t("orderManagement.filters.clearCustomer")}
                    onClick={clearCustomerFilter}
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                </div>
              ) : null}
              <button type="button" className="om-clear-all" onClick={clearFilters}>
                {t("orderManagement.filters.clearAll")}
              </button>
            </div>
          ) : null}
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
                      <input
                        aria-label={t("orderManagement.columns.selectAll")}
                        type="checkbox"
                        checked={allPageSelected}
                        onChange={() => {
                          setSelected((current) => {
                            const next = new Map(current);
                            if (allPageSelected) {
                              items.forEach((row) => next.delete(row.id));
                              return next;
                            }
                            for (const row of items) {
                              if (next.has(row.id)) continue;
                              if (next.size >= BULK_MAX) {
                                notify(t("orderManagement.errors.BULK_TOO_MANY"), "fail");
                                break;
                              }
                              next.set(row.id, selectionOf(row));
                            }
                            return next;
                          });
                        }}
                      />
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
                    const isOpen = openId === o.id;
                    return (
                      <tr
                        key={o.id}
                        className={isOpen ? "selected" : undefined}
                        data-order-id={o.id}
                        onClick={() => openOrder(o.id)}
                      >
                        <td onClick={(event) => event.stopPropagation()}>
                          <input
                            aria-label={o.orderNumber}
                            type="checkbox"
                            checked={selected.has(o.id)}
                            disabled={!selected.has(o.id) && selected.size >= BULK_MAX}
                            onChange={() => toggleSelect(o)}
                          />
                        </td>
                        <td>
                          <b className="num">{o.orderNumber}</b>
                          {hidden && <span className="om-badge warn">{t("orderManagement.awaitingBadge")}</span>}
                          {!hidden && o.paymentMethod === "COD" && <span className="om-badge cod">COD</span>}
                          {FEATURES.orderTagsApi && !hidden ? <OrderTagChips tags={o.tags} /> : null}
                        </td>
                        <td onClick={(event) => event.stopPropagation()}>
                          {hidden ? (
                            <span className="om-muted">{t("orderManagement.customer.awaiting")}</span>
                          ) : o.customerKey ? (
                            <div className="om-customer-cell">
                              <button
                                type="button"
                                className="om-customer-link"
                                title={t("orderManagement.customer.filterBy", {
                                  name: o.customerName || t("orderManagement.filters.customerUnknown"),
                                })}
                                onClick={() =>
                                  applyCustomerFilter(
                                    o.customerKey!,
                                    o.customerName || t("orderManagement.filters.customerUnknown"),
                                  )
                                }
                              >
                                {o.customerName || "—"}
                              </button>
                              {canReadCustomers ? (
                                <Link
                                  to={`/merchant/customers?selected=${encodeURIComponent(o.customerKey)}`}
                                  aria-label={t("orderManagement.customer.openProfile")}
                                >
                                  <UserRound size={16} aria-hidden="true" />
                                </Link>
                              ) : null}
                            </div>
                          ) : (
                            "—"
                          )}
                        </td>
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
                        <td onClick={(event) => event.stopPropagation()}>
                          <div className="om-row-actions">
                            <button
                              type="button"
                              aria-label={t("orderManagement.viewOrder", { number: o.orderNumber })}
                              aria-haspopup="dialog"
                              aria-expanded={isOpen}
                              onClick={(event) => openOrder(o.id, event.currentTarget)}
                            >
                              <Eye aria-hidden="true" />
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
          <MerchantPagination
            page={page}
            pageCount={pages}
            size={size}
            total={total}
            sizeOptions={SIZES}
            onPageChange={setPage}
            onSizeChange={(next) => { setSize(next); setPage(0); }}
          />
        </section>
      </section>
      <OrderBulkBar
        selected={selected}
        carriers={carriers}
        tags={shopTags}
        progress={bulkProgress}
        running={bulkRunning}
        onCancelRun={() => { bulkStopRef.current = true; }}
        onClear={() => setSelected(new Map())}
        onRun={(payload) => void runBulk(payload)}
      />
      {openId && shopId != null && (
        <MerchantOrderDrawer
          shopId={shopId}
          orderId={openId}
          onClose={closeOrder}
          onChanged={handleChanged}
          notify={notify}
          shopTags={shopTags}
          onCreateShopTag={async (name, color) => {
            const created = await createTag(shopId, { name, color });
            setShopTags((current) => [...current, created]);
            return created;
          }}
        />
      )}
      {FEATURES.orderTagsApi && shopId != null ? (
        <OrderTagManager
          shopId={shopId}
          open={tagManagerOpen}
          onClose={() => setTagManagerOpen(false)}
          onChanged={setShopTags}
          notify={notify}
        />
      ) : null}
      {bulkResults ? (
        <OrderBulkResults
          rows={bulkResults}
          onClose={() => setBulkResults(null)}
          onSelectFailed={(orderIds) => {
            setSelected((current) => {
              const next = new Map<string, BulkSelectionItem>();
              orderIds.forEach((id) => {
                const existing = current.get(id);
                if (existing) { next.set(id, existing); return; }
                const fromPage = items.find((row) => row.id === id);
                if (fromPage) { next.set(id, selectionOf(fromPage)); return; }
                const fromResults = bulkResults.find((row) => row.orderId === id);
                if (fromResults) {
                  next.set(id, {
                    version: fromResults.newVersion ?? 0,
                    orderNumber: fromResults.orderNumber,
                    status: "",
                    awaitingAcceptance: false,
                  });
                }
              });
              return next;
            });
            setBulkResults(null);
          }}
        />
      ) : null}
      {notice && (
        <div className={`merchant-toast${tone === "fail" ? " om-toast-fail" : ""}`} role="status">
          {notice}
        </div>
      )}
    </main>
  );
}
