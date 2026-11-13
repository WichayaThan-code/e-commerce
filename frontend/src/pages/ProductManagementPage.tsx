import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { SkeletonTable, SkeletonBusy, LoadError } from "../components/feedback/Skeleton";
import { useDelayedLoading } from "../hooks/useDelayedLoading";
import { Link, useSearchParams } from "react-router-dom";
import { Archive, Download, Ellipsis, ExternalLink, Eye, Link2, LoaderCircle, Pencil, Plus, QrCode, RotateCcw, Search, Upload, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useImportActivity } from "../contexts/ImportActivityContext";
import ConfirmDialog from "../components/form/ConfirmDialog";
import MerchantShell from "../components/merchant/MerchantShell";
import MerchantPagination from "../components/merchant/MerchantPagination";
import MerchantCheck from "../components/merchant/MerchantCheck";
import AuthImage from "../components/merchant/AuthImage";
import SearchableSelect from "../components/SearchableSelect";
import ProductQrModal from "../components/shop/ProductQrModal";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { useAuth } from "../contexts/AuthContext";
import { usePermissions } from "../hooks/usePermissions";
import { labelForCategory } from "../services/categories";
import { catalogMessage, formatMoney, formatWhen } from "../services/catalogText";
import { hasMerchantAccess, hasPlatformAccess } from "../services/permissions";
import { ApiError, catalogApi, merchantPlanUsageApi, productApi, type MerchantPlanUsage, type Product, type ProductBrand, type ProductCategory, type ProductSort, type ProductStatus, type StockFilter } from "../services/api";
import { canOpenStorefront, copyText, productPageUrl, productShareLink } from "../utils/productLinks";
import type { Locale } from "../i18n";
import "../styles/order-management.css";

const tabs = [
  ["", "all"],
  ["PUBLISHED", "published"],
  ["DRAFT", "draft"],
  ["ARCHIVED", "archived"],
] as const;

function stockBadge(product: Product) {
  if (product.totalStock != null) {
    if (product.totalStock <= 0) return "out" as const;
    if ((product.lowStockCount || 0) > 0) return "low" as const;
    return "in" as const;
  }
  const rows = product.skus.filter((sku) => sku.active);
  const available = rows.reduce((sum, sku) => sum + sku.available, 0);
  if (available <= 0) return "out" as const;
  if (rows.some((sku) => {
    if (sku.available <= 0) return false;
    const threshold = sku.lowStockThreshold != null
      ? Number(sku.lowStockThreshold)
      : (sku.safetyStock != null && Number(sku.safetyStock) > 0 ? Number(sku.safetyStock) : 5);
    return sku.available < threshold;
  })) return "low" as const;
  return "in" as const;
}

function stockTotal(product: Product) {
  if (product.totalStock != null) return product.totalStock;
  return product.skus.filter((sku) => sku.active).reduce((sum, sku) => sum + sku.available, 0);
}

function priceLabel(product: Product, locale: Locale, format: (value: number | string | null | undefined, locale: Locale) => string) {
  if (product.productType === "VARIABLE" && product.minPrice != null && product.maxPrice != null) {
    if (Number(product.minPrice) === Number(product.maxPrice)) return format(product.minPrice, locale);
    return `${format(product.minPrice, locale)} – ${format(product.maxPrice, locale)}`;
  }
  return format(product.basePrice ?? product.skus[0]?.price, locale);
}

export default function ProductManagementPage() {
  const { t, i18n } = useTranslation("merchant");
  const locale = (i18n.language || "th") as Locale;
  const { user, loading: authLoading } = useAuth();
  const { canInShop, deniedTitle } = usePermissions();
  const canCreate = canInShop("PRODUCT_CREATE");
  const canUpdate = canInShop("PRODUCT_UPDATE");
  const canArchive = canInShop("PRODUCT_ARCHIVE");
  const { shopId, shops } = useActiveShop();
  const shop = shops.find((item) => item.shopId === shopId);
  const shopSlug = shop?.slug?.trim() || "";
  const [params, setParams] = useSearchParams();
  const q = params.get("q") || "";
  const status = (params.get("status") || "") as "" | ProductStatus;
  const categoryId = params.get("categoryId") || "";
  const brand = params.get("brand") || "";
  const stock = (params.get("stock") || "") as "" | StockFilter;
  const sort = (params.get("sort") || "newest") as ProductSort;
  const page = Math.max(0, Number(params.get("page") || "1") - 1);
  const size = Number(params.get("size") || "20");
  const [query, setQuery] = useState(q);
  const [items, setItems] = useState<Product[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState({ all: 0, published: 0, draft: 0, archived: 0 });
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [brands, setBrands] = useState<ProductBrand[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [notice, setNotice] = useState("");
  const [tone, setTone] = useState<"ok" | "fail" | "warning">("ok");
  const [busy, setBusy] = useState(false);
  const [confirmIds, setConfirmIds] = useState<string[] | null>(null);
  const [reload, setReload] = useState(0);
  const [transfer, setTransfer] = useState<"export" | "import" | null>(null);
  const { busy: importBusy, finishedTick, startImport, job: importJob } = useImportActivity();
  const importRef = useRef<HTMLInputElement>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [qrProduct, setQrProduct] = useState<Product | null>(null);
  const [menuFor, setMenuFor] = useState<string | null>(null);
  const [menuPos, setMenuPos] = useState<{ top: number; left: number } | null>(null);
  const menuAnchorRef = useRef<HTMLButtonElement | null>(null);
  const menuPanelRef = useRef<HTMLDivElement | null>(null);
  const [planUsage, setPlanUsage] = useState<MerchantPlanUsage | null>(null);

  useEffect(() => { setQuery(q); }, [q]);
  useEffect(() => {
    if (shopId == null) {
      setPlanUsage(null);
      return;
    }
    let cancelled = false;
    merchantPlanUsageApi.get(shopId)
      .then((row) => { if (!cancelled) setPlanUsage(row); })
      .catch(() => { if (!cancelled) setPlanUsage(null); });
    return () => { cancelled = true; };
  }, [shopId, reload]);
  useEffect(() => {
    if (finishedTick === 0) return;
    setReload((value) => value + 1);
    setImportOpen(true);
  }, [finishedTick]);
  useEffect(() => {
    const handle = window.setTimeout(() => {
      if (query === q) return;
      setParams((current) => {
        const next = new URLSearchParams(current);
        if (query.trim()) next.set("q", query.trim());
        else next.delete("q");
        next.delete("page");
        return next;
      }, { replace: true });
    }, 300);
    return () => window.clearTimeout(handle);
  }, [query, q, setParams]);

  useEffect(() => {
    catalogApi.categories().then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    if (shopId == null) return;
    productApi.brands(shopId).then(setBrands).catch(() => setBrands([]));
  }, [shopId, reload]);

  useEffect(() => {
    if (shopId == null) return;
    let active = true;
    setLoading(true);
    setError(null);
    const shared = { q, categoryId: categoryId ? Number(categoryId) : undefined, brand: brand || undefined, stock: stock || undefined };
    Promise.all([
      productApi.list(shopId, { ...shared, status: status || undefined, sort, page, size }),
      productApi.list(shopId, { ...shared, size: 1 }),
      productApi.list(shopId, { ...shared, status: "PUBLISHED", size: 1 }),
      productApi.list(shopId, { ...shared, status: "DRAFT", size: 1 }),
      productApi.list(shopId, { ...shared, status: "ARCHIVED", size: 1 }),
    ]).then(([list, all, published, draft, archived]) => {
      if (!active) return;
      setItems(list.items);
      setTotal(list.total);
      setCounts({ all: all.total, published: published.total, draft: draft.total, archived: archived.total });
      setSelected([]);
    }).catch((caught) => {
      if (active) setError(caught);
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [shopId, q, status, categoryId, brand, stock, sort, page, size, reload]);

  const patch = (updates: Record<string, string | null>) => {
    setParams((current) => {
      const next = new URLSearchParams(current);
      Object.entries(updates).forEach(([key, value]) => {
        if (!value) next.delete(key);
        else next.set(key, value);
      });
      if (!("page" in updates)) next.delete("page");
      return next;
    });
  };
  const applySearch = () => {
    patch({ q: query.trim() || null });
  };
  const resetFilters = () => {
    setQuery("");
    setParams((current) => {
      const next = new URLSearchParams();
      const keepStatus = current.get("status");
      const keepSize = current.get("size");
      if (keepStatus) next.set("status", keepStatus);
      if (keepSize) next.set("size", keepSize);
      return next;
    }, { replace: true });
  };
  const canExport = shopId != null && total > 0 && !loading;
  const exportTitle = shopId == null
    ? deniedTitle(false)
    : total <= 0
      ? t("catalog.exportEmptyTitle")
      : undefined;
  const exportProducts = async () => {
    if (shopId == null || transfer || !canExport) return;
    setTransfer("export");
    try {
      await productApi.exportExcel(shopId, {
        q: q || undefined,
        status: status || undefined,
        categoryId: categoryId ? Number(categoryId) : undefined,
        brand: brand || undefined,
        stock: stock || undefined,
        sort,
        lang: locale.startsWith("zh") ? "zh" : locale.startsWith("en") ? "en" : "th",
      });
      toast(t("catalog.exportDone"));
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === "EXPORT_EMPTY") {
        toast(t("catalog.exportEmpty"), "warning");
      } else if (caught instanceof ApiError && caught.code === "EXPORT_TOO_LARGE") {
        toast(t("catalog.exportTooLarge", { max: "10000" }), "fail");
      } else if (
        !(caught instanceof ApiError)
        || caught.code === "NETWORK_ERROR"
        || caught.status === 0
      ) {
        toast(t("catalog.exportNetwork"), "fail");
      } else if (caught.status === 403 || caught.code === "FORBIDDEN" || caught.code === "PERMISSION_DENIED") {
        toast(deniedTitle(false) || t("catalog.exportFailed"), "fail");
      } else {
        toast(t("catalog.exportFailed"), "fail");
      }
    } finally {
      setTransfer(null);
    }
  };
  const importProducts = async (file?: File) => {
    if (!file || transfer || importBusy) return;
    try {
      await startImport(file);
    } finally {
      if (importRef.current) importRef.current.value = "";
    }
  };
  const toast = (text: string, nextTone: "ok" | "fail" | "warning" = "ok") => {
    setTone(nextTone);
    setNotice(text);
    window.setTimeout(() => setNotice(""), 2200);
  };
  const placeMenu = (anchor: HTMLElement) => {
    const rect = anchor.getBoundingClientRect();
    const width = Math.min(220, window.innerWidth - 16);
    const left = Math.min(Math.max(8, rect.right - width), window.innerWidth - width - 8);
    const height = menuPanelRef.current?.offsetHeight ?? 260;
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

  const openRowMenu = (productId: string, anchor: HTMLButtonElement) => {
    if (menuFor === productId) {
      closeMenu();
      return;
    }
    menuAnchorRef.current = anchor;
    placeMenu(anchor);
    setMenuFor(productId);
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

  const productMetric = planUsage?.metrics.find((metric) => metric.code === "PRODUCTS");
  const publishLimit = productMetric?.limit.kind === "FINITE" ? productMetric.limit.value : null;
  const publishedUsed = productMetric?.used ?? counts.published;
  const draftCount = productMetric?.breakdown?.draft ?? counts.draft;
  const publishRemaining = publishLimit == null ? null : Math.max(0, publishLimit - publishedUsed);
  const publishQuotaBlocked = publishLimit != null && (productMetric?.status === "FULL" || productMetric?.status === "OVER" || publishedUsed >= publishLimit);
  const publishQuotaWarn = !publishQuotaBlocked
    && productMetric?.usedRatio != null
    && productMetric.usedRatio >= 0.8;
  const publishQuotaTitle = publishQuotaBlocked ? t("catalog.publishQuota.blocked") : undefined;

  const rowActions = (product: Product) => {
    const open = menuFor === product.id;
    return (
      <div className="pm-row-actions">
        {canUpdate
          ? <Link aria-label={t("catalog.actions.edit")} title={t("catalog.actions.edit")} to={`/merchant/products/${product.id}/edit`}><Pencil /></Link>
          : <button type="button" aria-label={t("catalog.actions.edit")} disabled title={deniedTitle(false)}><Pencil /></button>}
        <div className="pm-share-wrap">
          <button
            type="button"
            aria-label={t("catalog.actions.more")}
            title={t("catalog.actions.more")}
            aria-haspopup="menu"
            aria-expanded={open}
            onClick={(event) => openRowMenu(product.id, event.currentTarget)}
          >
            <Ellipsis />
          </button>
        </div>
      </div>
    );
  };

  const menuProduct = menuFor ? items.find((item) => item.id === menuFor) : null;
  const menuStorefrontOk = menuProduct ? canOpenStorefront(menuProduct.status, shopSlug, menuProduct.slug) : false;
  const menuStorefrontTip = menuStorefrontOk ? undefined : t("catalog.actions.notPublished");
  const runMany = async (ids: string[], action: "publish" | "archive" | "unarchive") => {
    if (shopId == null) return;
    if (action === "publish") {
      const toPublish = ids.filter((id) => items.find((item) => item.id === id)?.status !== "PUBLISHED");
      if (publishRemaining != null && toPublish.length > publishRemaining) {
        toast(t("catalog.publishQuota.bulkOver", { selected: toPublish.length, remaining: publishRemaining }), "fail");
        return;
      }
    }
    setBusy(true);
    let ok = 0;
    let fail = 0;
    let first: unknown = null;
    for (const id of ids) {
      try {
        await productApi[action](shopId, id);
        ok += 1;
      } catch (caught) {
        fail += 1;
        first = first || caught;
        // Do not continue publishing after quota failure — avoid silent partial success.
        if (action === "publish" && caught instanceof ApiError && caught.code === "PRODUCT_QUOTA_EXCEEDED") break;
      }
    }
    setBusy(false);
    setConfirmIds(null);
    setReload((value) => value + 1);
    if (fail && ok) toast(t("catalog.toast.bulk", { ok, fail }), "fail");
    else if (fail) toast(catalogMessage(t, first), "fail");
    else toast(t(action === "publish" ? "catalog.toast.published" : action === "unarchive" ? "catalog.toast.unarchived" : "catalog.toast.archived"));
  };

  const pages = Math.max(1, Math.ceil(total / size));
  const filtered = Boolean(q || categoryId || brand || stock || status);
  const hasChipFilters = Boolean(q || categoryId || brand || stock);
  const pageSelectedCount = items.filter((item) => selected.includes(item.id)).length;
  const allPageSelected = items.length > 0 && pageSelectedCount === items.length;
  const somePageSelected = pageSelectedCount > 0 && !allPageSelected;
  const toggleAllPage = () => {
    setSelected(allPageSelected ? [] : items.map((item) => item.id));
  };
  const toggleOne = (id: string) => {
    setSelected((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));
  };
  const lang = locale.startsWith("en") ? "en" : locale.startsWith("zh") ? "zh" : "th";
  const categoryName = (id?: number | null) => {
    const match = categories.find((category) => category.id === id);
    return match ? labelForCategory(match, lang) : "—";
  };
  const stockChoices = [
    ["", t("catalog.stockAll")],
    ["in", t("catalog.stockIn")],
    ["low", t("catalog.stockLow")],
    ["out", t("catalog.stockOut")],
  ] as const;
  const sortChoices = [
    ["newest", t("catalog.sortNewest")],
    ["updated", t("catalog.sortUpdated")],
    ["oldest", t("catalog.sortOldest")],
    ["name", t("catalog.sortName")],
    ["price_asc", t("catalog.sortPriceAsc")],
    ["price_desc", t("catalog.sortPriceDesc")],
    ["stock", t("catalog.sortStock")],
  ] as const;
  const selectedCategory = categories.find((category) => String(category.id) === categoryId);
  const stockChipLabel = stockChoices.find(([value]) => value === stock)?.[1] || "";
  const clearChipLabel = (label: string) => t("catalog.filters.clearChip", { label });
  const { showSkeleton, markHasData, isRefetching } = useDelayedLoading(loading, 150, shopId ?? "none");

  useEffect(() => {
    if (!loading && !error) markHasData();
  }, [loading, error, markHasData]);

  if (authLoading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if (!user) {
    window.location.replace("/login?next=/merchant/products");
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

  if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
    return (
      <MerchantShell>
        <section className="pm-state">
          <h1>{t(error.status === 403 ? "catalog.forbiddenTitle" : "catalog.missingTitle")}</h1>
          <p>{t(error.status === 403 ? "catalog.forbiddenBody" : "catalog.missingBody")}</p>
        </section>
      </MerchantShell>
    );
  }

  return (
    <MerchantShell notice={notice} tone={tone}>
      <header className="pm-heading">
        <div>
          <h1>{t("catalog.title")}</h1>
          <p>{t("catalog.lead")}</p>
        </div>
        <div className="pm-actions">
          <button
            type="button"
            className="primary"
            disabled={shopId == null}
            onClick={() => toast(t("catalog.importUnavailable"), "warning")}
          >
            <Upload />
            {t("catalog.import")}
          </button>
          <input
            ref={importRef}
            className="pm-file"
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            onChange={(event) => void importProducts(event.target.files?.[0])}
          />
          {canCreate ? (
            <Link className="primary" to="/merchant/products/new"><Plus />{t("catalog.add")}</Link>
          ) : (
            <button type="button" className="primary" disabled title={deniedTitle(false)}><Plus />{t("catalog.add")}</button>
          )}
        </div>
      </header>
      {publishLimit != null || productMetric?.limit.kind === "UNLIMITED" ? (
        <p className="pm-plan-quota" aria-live="polite">
          {publishLimit != null
            ? t("catalog.publishQuota.counter", { used: publishedUsed, limit: publishLimit })
            : t("catalog.publishQuota.counterUnlimited", { used: publishedUsed })}
          {" · "}
          {t("catalog.publishQuota.draftNote", { draft: draftCount })}
        </p>
      ) : null}
      {publishQuotaBlocked || publishQuotaWarn ? (
        <p className="form-alert" role="alert">
          {t(publishQuotaBlocked ? "catalog.publishQuota.fullAlert" : "catalog.publishQuota.warnAlert")}{" "}
          <Link to="/merchant/settings?tab=plan">{t("catalog.publishQuota.link")}</Link>
        </p>
      ) : null}
      <div className="pm-tabs" role="tablist">
        {tabs.map(([value, key]) => (
          <button key={key} type="button" role="tab" aria-selected={status === value} className={status === value ? "active" : ""} onClick={() => patch({ status: value || null })}>
            {t(`catalog.tabs.${key}`)}
            <i>{counts[key === "all" ? "all" : key === "published" ? "published" : key === "draft" ? "draft" : "archived"]}</i>
          </button>
        ))}
      </div>
      <section className="pm-table-card">
        <form
          className="om-filters"
          onSubmit={(event) => {
            event.preventDefault();
            applySearch();
          }}
        >
          <div className="om-filters-main pm-filters-main">
            <div className="om-filter-search">
              <span className="form-label">{t("catalog.searchLabel")}</span>
              <label className="om-search">
                <Search aria-hidden="true" />
                <input
                  aria-label={t("catalog.searchLabel")}
                  placeholder={t("catalog.searchPh")}
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                />
              </label>
            </div>
            <SearchableSelect
              label={t("catalog.category")}
              placeholder={t("catalog.categoryAll")}
              options={[t("catalog.categoryAll"), ...categories.map((category) => labelForCategory(category, lang))]}
              value={selectedCategory ? labelForCategory(selectedCategory, lang) : t("catalog.categoryAll")}
              onChange={(picked) => {
                const match = categories.find((category) => labelForCategory(category, lang) === picked);
                patch({ categoryId: match ? String(match.id) : null });
              }}
            />
            <SearchableSelect
              label={t("catalog.brand")}
              placeholder={t("catalog.brandAll")}
              options={[t("catalog.brandAll"), ...brands.map((item) => item.name)]}
              value={brand || t("catalog.brandAll")}
              onChange={(picked) => patch({ brand: brands.some((item) => item.name === picked) ? picked : null })}
            />
            <SearchableSelect
              label={t("catalog.stock")}
              options={stockChoices.map(([, label]) => label)}
              value={stockChoices.find(([value]) => value === stock)?.[1] || t("catalog.stockAll")}
              onChange={(picked) => patch({ stock: stockChoices.find(([, label]) => label === picked)?.[0] || null })}
            />
            <SearchableSelect
              label={t("catalog.sort")}
              options={sortChoices.map(([, label]) => label)}
              value={sortChoices.find(([value]) => value === sort)?.[1] || t("catalog.sortNewest")}
              onChange={(picked) => patch({ sort: sortChoices.find(([, label]) => label === picked)?.[0] || "newest" })}
            />
            <div className="om-filters-actions">
              <button type="button" onClick={resetFilters}>
                {t("catalog.filters.reset")}
              </button>
              <button type="submit" className="primary">
                {t("catalog.filters.search")}
              </button>
            </div>
          </div>
        </form>
        <div className="pm-table-tools">
          <div className="pm-table-tools-chips" role="list" aria-label={t("catalog.filters.active")}>
            {q ? (
              <div className="om-filter-chip" role="listitem">
                <span>{t("catalog.searchLabel")}: {q}</span>
                <button
                  type="button"
                  aria-label={clearChipLabel(t("catalog.searchLabel"))}
                  onClick={() => {
                    setQuery("");
                    patch({ q: null });
                  }}
                >
                  <X size={14} aria-hidden="true" />
                </button>
              </div>
            ) : null}
            {selectedCategory ? (
              <div className="om-filter-chip" role="listitem">
                <span>{t("catalog.category")}: {labelForCategory(selectedCategory, lang)}</span>
                <button
                  type="button"
                  aria-label={clearChipLabel(t("catalog.category"))}
                  onClick={() => patch({ categoryId: null })}
                >
                  <X size={14} aria-hidden="true" />
                </button>
              </div>
            ) : null}
            {brand ? (
              <div className="om-filter-chip" role="listitem">
                <span>{t("catalog.brand")}: {brand}</span>
                <button
                  type="button"
                  aria-label={clearChipLabel(t("catalog.brand"))}
                  onClick={() => patch({ brand: null })}
                >
                  <X size={14} aria-hidden="true" />
                </button>
              </div>
            ) : null}
            {stock ? (
              <div className="om-filter-chip" role="listitem">
                <span>{t("catalog.stock")}: {stockChipLabel}</span>
                <button
                  type="button"
                  aria-label={clearChipLabel(t("catalog.stock"))}
                  onClick={() => patch({ stock: null })}
                >
                  <X size={14} aria-hidden="true" />
                </button>
              </div>
            ) : null}
            {hasChipFilters ? (
              <button type="button" className="om-clear-all" onClick={resetFilters}>
                {t("catalog.filters.clearAll")}
              </button>
            ) : null}
          </div>
          <button
            type="button"
            className="pm-export-btn"
            disabled={!canExport || transfer != null || importBusy}
            title={exportTitle || t("catalog.exportHint")}
            onClick={() => void exportProducts()}
          >
            <Download aria-hidden="true" />
            <span>{transfer === "export" ? t("catalog.exporting") : t("catalog.export")}</span>
          </button>
        </div>
        {showSkeleton && loading && items.length === 0 ? (
          <div aria-busy="true" aria-label={t("catalog.loading")}><SkeletonTable rows={6} cols={9} /></div>
        ) : error ? (
          <LoadError
            message={catalogMessage(t, error)}
            onRetry={() => setReload((value) => value + 1)}
            retryLabel={t("catalog.actions.retry")}
          />
        ) : items.length === 0 ? (
          <div className="pm-state">
            <h2>{t(filtered ? "catalog.empty.filterTitle" : "catalog.empty.noneTitle")}</h2>
            <p>{t(filtered ? "catalog.empty.filterBody" : "catalog.empty.noneBody")}</p>
            {filtered ? <button type="button" onClick={() => setParams({})}>{t("catalog.actions.clear")}</button> : canCreate ? (
              <Link className="primary" to="/merchant/products/new">{t("catalog.actions.addFirst")}</Link>
            ) : (
              <button type="button" className="primary" disabled title={deniedTitle(false)}>{t("catalog.actions.addFirst")}</button>
            )}
          </div>
        ) : (
          <SkeletonBusy refetching={isRefetching} busy={isRefetching}>
            <div className="pm-table-wrap pm-detail-table">
              <table>
                <thead>
                  <tr>
                    <th className="pm-col-check">
                      <MerchantCheck
                        checked={allPageSelected}
                        indeterminate={somePageSelected}
                        label={t("catalog.selectAll")}
                        onChange={toggleAllPage}
                      />
                    </th>
                    <th className="pm-col-fk pm-col-product">{t("catalog.columns.product")}</th>
                    <th className="pm-col-fk pm-col-sku">{t("catalog.columns.sku")}</th>
                    <th className="pm-col-fk pm-col-category">{t("catalog.columns.category")}</th>
                    <th className="pm-col-price">{t("catalog.columns.price")}</th>
                    <th className="pm-col-stock">{t("catalog.columns.stock")}</th>
                    <th className="pm-col-status">{t("catalog.columns.status")}</th>
                    <th className="pm-col-updated">{t("catalog.columns.updated")}</th>
                    <th className="pm-col-actions">{t("catalog.columns.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((product) => {
                    const badge = stockBadge(product);
                    const image = product.images.find((item) => item.primary) || product.images[0];
                    const available = stockTotal(product);
                    const isSelected = selected.includes(product.id);
                    const variantText = product.productType === "VARIABLE"
                      ? t("catalog.variantLine", { count: product.variantCount ?? product.skus.filter((sku) => sku.active).length, stock: available })
                      : product.skus.length > 1 ? product.skus.map((sku) => sku.skuCode).join(", ") : product.skus[0]?.skuCode || "—";
                    return (
                      <tr key={product.id} className={isSelected ? "is-selected" : undefined}>
                        <td className="pm-col-check">
                          <MerchantCheck
                            checked={isSelected}
                            label={product.nameTh}
                            onChange={() => toggleOne(product.id)}
                          />
                        </td>
                        <td className="pm-col-fk pm-col-product">
                          <div className="pm-product">
                            <span className="pm-thumb"><AuthImage path={image?.url} alt="" /></span>
                            <p><b title={product.nameTh}>{product.nameTh}</b><small title={product.nameEn || product.slug}>{product.nameEn || product.slug}</small></p>
                          </div>
                        </td>
                        <td className="pm-col-fk pm-col-sku pm-clip num" title={variantText}>{variantText}</td>
                        <td className="pm-col-fk pm-col-category pm-clip" title={categoryName(product.categoryId)}>{categoryName(product.categoryId)}</td>
                        <td className="pm-col-price"><b className="num">{priceLabel(product, locale, formatMoney)}</b></td>
                        <td className="pm-col-stock">
                          <div className="pm-stock">
                            <b className={`num ${badge === "out" ? "stock-zero" : badge === "low" ? "stock-low" : "stock-ok"}`}>{available}</b>
                            {badge !== "in" ? <span className={badge === "out" ? "pm-status empty" : "pm-status low"}>{t(`catalog.badges.${badge}`)}</span> : null}
                          </div>
                        </td>
                        <td className="pm-col-status pm-state-cell"><span className={product.status === "PUBLISHED" ? "pm-status ready" : product.status === "ARCHIVED" ? "pm-status empty" : "pm-status low"}>{t(`catalog.status.${product.status}`)}</span></td>
                        <td className="pm-col-updated pm-clip" title={formatWhen(product.modifiedAt, locale)}>{formatWhen(product.modifiedAt, locale)}</td>
                        <td className="pm-col-actions">{rowActions(product)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="pm-cards">
              {items.length > 0 ? (
                <div className="pm-cards-select-all">
                  <MerchantCheck
                    checked={allPageSelected}
                    indeterminate={somePageSelected}
                    label={t("catalog.selectAll")}
                    onChange={toggleAllPage}
                  />
                  <button type="button" className="pm-cards-select-label" onClick={toggleAllPage}>
                    {allPageSelected ? t("catalog.clearSelection") : t("catalog.selectAll")}
                    {pageSelectedCount > 0 ? <i className="num">{pageSelectedCount}</i> : null}
                  </button>
                </div>
              ) : null}
              {items.map((product) => {
                const badge = stockBadge(product);
                const image = product.images.find((item) => item.primary) || product.images[0];
                const available = stockTotal(product);
                const isSelected = selected.includes(product.id);
                const variantText = product.productType === "VARIABLE"
                  ? t("catalog.variantLine", { count: product.variantCount ?? product.skus.filter((sku) => sku.active).length, stock: available })
                  : product.skus.map((sku) => sku.skuCode).join(", ") || "—";
                return (
                  <article key={product.id} className={isSelected ? "is-selected" : undefined}>
                    <div className="pm-card-media">
                      <MerchantCheck
                        checked={isSelected}
                        label={product.nameTh}
                        onChange={() => toggleOne(product.id)}
                      />
                      <AuthImage path={image?.url} alt="" />
                    </div>
                    <div>
                      <b>{product.nameTh}</b>
                      <small className="num">{variantText}</small>
                      <small>{categoryName(product.categoryId)}</small>
                      <span><span className="num">{priceLabel(product, locale, formatMoney)}</span> · <span className="num">{available}</span></span>
                      <em className={badge === "out" ? "pm-status empty" : badge === "low" ? "pm-status low" : "pm-status ready"}>{badge === "in" ? t(`catalog.status.${product.status}`) : t(`catalog.badges.${badge}`)}</em>
                      <small>{formatWhen(product.modifiedAt, locale)}</small>
                      {rowActions(product)}
                    </div>
                  </article>
                );
              })}
            </div>
          </SkeletonBusy>
        )}
        <MerchantPagination
          page={page}
          pageCount={pages}
          size={size}
          total={total}
          onPageChange={(next) => patch({ page: String(next + 1) })}
          onSizeChange={(next) => patch({ size: String(next), page: "1" })}
        />
      </section>
      {selected.length > 0 ? (
        <div className="pm-bulk-bar" role="region" aria-label={t("catalog.bulkBarLabel")}>
          <div className="pm-bulk-count">
            <span className="pm-bulk-badge num" aria-hidden="true">{selected.length}</span>
            <b>{t("catalog.selected", { count: selected.length })}</b>
          </div>
          <div className="pm-bulk-actions">
            <button
              type="button"
              className="primary"
              disabled={busy || !canUpdate || publishQuotaBlocked}
              title={publishQuotaTitle ?? deniedTitle(canUpdate)}
              onClick={() => void runMany(selected, "publish")}
            >
              <Upload aria-hidden="true" />
              <span>{t("catalog.bulkPublish")}</span>
            </button>
            <button
              type="button"
              disabled={busy || !canArchive}
              title={deniedTitle(canArchive)}
              onClick={() => setConfirmIds(selected)}
            >
              <Archive aria-hidden="true" />
              <span>{t("catalog.bulkArchive")}</span>
            </button>
            <button type="button" className="ghost" disabled={busy} onClick={() => setSelected([])}>
              <X aria-hidden="true" />
              <span>{t("catalog.clearSelection")}</span>
            </button>
          </div>
        </div>
      ) : null}
      {importOpen && importJob && (
        <ConfirmDialog
          title={t("catalog.importResult.title")}
          message={t("catalog.importResult.summary", { created: importJob.created, skuCreated: importJob.skuCreated ?? 0, failed: importJob.failed })}
          cancelLabel={t("catalog.actions.cancel")}
          confirmLabel={t("catalog.actions.close")}
          onCancel={() => setImportOpen(false)}
          onConfirm={() => setImportOpen(false)}
        >
          {importJob.errors.length > 0 ? (
            <div className="pm-import-table">
              <table>
                <thead>
                  <tr>
                    <th>{t("catalog.importResult.row")}</th>
                    <th>{t("catalog.importResult.productKey")}</th>
                    <th>{t("catalog.importResult.skuCode")}</th>
                    <th>{t("catalog.importResult.message")}</th>
                  </tr>
                </thead>
                <tbody>
                  {importJob.errors.map((item, index) => (
                    <tr key={`${item.row}-${index}`}>
                      <td>{item.row}</td>
                      <td>{item.productKey || "—"}</td>
                      <td>{item.skuCode || "—"}</td>
                      <td>{item.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </ConfirmDialog>
      )}
      {confirmIds && (
        <ConfirmDialog
          title={confirmIds.length > 1 ? t("catalog.archiveBulk", { count: confirmIds.length }) : t("catalog.archiveTitle")}
          message={t("catalog.archiveBody")}
          cancelLabel={t("catalog.actions.cancel")}
          confirmLabel={<>{busy ? <LoaderCircle className="pm-spin" /> : null}{t("catalog.actions.confirm")}</>}
          confirmDisabled={busy || !canArchive}
          confirmTitle={deniedTitle(canArchive)}
          onCancel={() => setConfirmIds(null)}
          onConfirm={() => void runMany(confirmIds, "archive")}
        />
      )}
      {qrProduct && shopSlug ? (
        <ProductQrModal
          productUrl={productPageUrl(shopSlug, qrProduct.slug)}
          name={qrProduct.nameTh}
          onClose={() => setQrProduct(null)}
        />
      ) : null}
      {menuProduct && menuPos
        ? createPortal(
            <div
              ref={menuPanelRef}
              className="pm-row-menu is-portaled"
              role="menu"
              style={{ top: menuPos.top, left: menuPos.left }}
            >
              <Link role="menuitem" to={`/merchant/products/${menuProduct.id}`} onClick={closeMenu}>
                <Eye />{t("catalog.actions.view")}
              </Link>
              <button
                type="button"
                role="menuitem"
                disabled={!menuStorefrontOk}
                title={menuStorefrontTip}
                onClick={() => {
                  if (!menuStorefrontOk) return;
                  closeMenu();
                  window.open(productPageUrl(shopSlug, menuProduct.slug), "_blank", "noopener,noreferrer");
                }}
              >
                <ExternalLink />{t("catalog.actions.viewStorefront")}
              </button>
              <button
                type="button"
                role="menuitem"
                disabled={!menuStorefrontOk}
                title={menuStorefrontTip}
                onClick={() => {
                  if (!menuStorefrontOk) return;
                  closeMenu();
                  void copyText(productShareLink(shopSlug, menuProduct.slug)).then((ok) => {
                    toast(ok ? t("catalog.toast.linkCopied") : catalogMessage(t, null), ok ? "ok" : "fail");
                  });
                }}
              >
                <Link2 />{t("catalog.actions.copyLink")}
              </button>
              <button
                type="button"
                role="menuitem"
                disabled={!menuStorefrontOk}
                title={menuStorefrontTip}
                onClick={() => {
                  if (!menuStorefrontOk) return;
                  closeMenu();
                  setQrProduct(menuProduct);
                }}
              >
                <QrCode />{t("catalog.actions.qrCode")}
              </button>
              {menuProduct.status === "ARCHIVED" ? (
                <button
                  type="button"
                  role="menuitem"
                  disabled={busy || !canArchive}
                  title={deniedTitle(canArchive) ?? t("catalog.actions.unarchive")}
                  onClick={() => { closeMenu(); void runMany([menuProduct.id], "unarchive"); }}
                >
                  <RotateCcw />{t("catalog.actions.unarchive")}
                </button>
              ) : (
                <>
                  {menuProduct.status !== "PUBLISHED" ? (
                    <button
                      type="button"
                      role="menuitem"
                      disabled={busy || !canUpdate || publishQuotaBlocked}
                      title={publishQuotaTitle ?? deniedTitle(canUpdate)}
                      onClick={() => { closeMenu(); void runMany([menuProduct.id], "publish"); }}
                    >
                      <Upload />{t("catalog.actions.publish")}
                    </button>
                  ) : null}
                  <button
                    type="button"
                    role="menuitem"
                    className="danger"
                    disabled={busy || !canArchive}
                    title={deniedTitle(canArchive)}
                    onClick={() => { closeMenu(); setConfirmIds([menuProduct.id]); }}
                  >
                    <Archive />{t("catalog.actions.archive")}
                  </button>
                </>
              )}
            </div>,
            document.body,
          )
        : null}
    </MerchantShell>
  );
}

