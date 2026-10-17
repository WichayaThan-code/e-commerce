import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Archive, ChevronFirst, ChevronLast, ChevronLeft, ChevronRight, Download, Eye, LoaderCircle, Pencil, Plus, RotateCcw, Search, Upload } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useImportActivity } from "../contexts/ImportActivityContext";
import ConfirmDialog from "../components/form/ConfirmDialog";
import MerchantShell from "../components/merchant/MerchantShell";
import AuthImage from "../components/merchant/AuthImage";
import SearchableSelect from "../components/SearchableSelect";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { useAuth } from "../contexts/AuthContext";
import { usePermissions } from "../hooks/usePermissions";
import { labelForCategory } from "../services/categories";
import { catalogMessage, formatMoney, formatWhen } from "../services/catalogText";
import { hasMerchantAccess, hasPlatformAccess } from "../services/permissions";
import { ApiError, catalogApi, productApi, type Product, type ProductBrand, type ProductCategory, type ProductSort, type ProductStatus, type StockFilter } from "../services/api";
import type { Locale } from "../i18n";

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
  if (rows.some((sku) => sku.lowStockThreshold != null && sku.available > 0 && sku.available <= sku.lowStockThreshold)) return "low" as const;
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
  const canImport = canInShop("PRODUCT_IMPORT");
  const canCreate = canInShop("PRODUCT_CREATE");
  const canUpdate = canInShop("PRODUCT_UPDATE");
  const canArchive = canInShop("PRODUCT_ARCHIVE");
  const { shopId } = useActiveShop();
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
  const [tone, setTone] = useState<"ok" | "fail">("ok");
  const [busy, setBusy] = useState(false);
  const [confirmIds, setConfirmIds] = useState<string[] | null>(null);
  const [reload, setReload] = useState(0);
  const [transfer, setTransfer] = useState<"export" | "import" | "template" | null>(null);
  const { busy: importBusy, finishedTick, startImport, job: importJob } = useImportActivity();
  const importRef = useRef<HTMLInputElement>(null);
  const [importOpen, setImportOpen] = useState(false);

  useEffect(() => { setQuery(q); }, [q]);
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
  const downloadTemplate = async () => {
    if (shopId == null || transfer) return;
    setTransfer("template");
    try {
      await productApi.downloadTemplate(shopId);
    } catch (caught) {
      toast(catalogMessage(t, caught), "fail");
    } finally {
      setTransfer(null);
    }
  };
  const exportProducts = async () => {
    if (shopId == null || transfer) return;
    setTransfer("export");
    try {
      await productApi.exportExcel(shopId);
      toast(t("catalog.exportDone"));
    } catch (caught) {
      toast(catalogMessage(t, caught), "fail");
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
  const toast = (text: string, nextTone: "ok" | "fail" = "ok") => {
    setTone(nextTone);
    setNotice(text);
    window.setTimeout(() => setNotice(""), 2200);
  };
  const runMany = async (ids: string[], action: "publish" | "archive" | "unarchive") => {
    if (shopId == null) return;
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
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  const filtered = Boolean(q || categoryId || brand || stock || status);
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
          <button type="button" className="primary" disabled={shopId == null || transfer != null || importBusy} onClick={() => void exportProducts()}>
            {transfer === "export" ? <LoaderCircle className="pm-spin" /> : <Download />}
            {t("catalog.export")}
          </button>
          <button type="button" className="primary" title={deniedTitle(canImport)} disabled={shopId == null || transfer != null || importBusy || !canImport} onClick={() => void downloadTemplate()}>
            {transfer === "template" ? <LoaderCircle className="pm-spin" /> : <Download />}
            {t("catalog.downloadTemplate")}
          </button>
          <button type="button" className="primary" title={deniedTitle(canImport) ?? t("catalog.importHint")} disabled={shopId == null || transfer != null || importBusy || !canImport} onClick={() => importRef.current?.click()}>
            {importBusy ? <LoaderCircle className="pm-spin" /> : <Upload />}
            {t("catalog.import")}
          </button>
          <input
            ref={importRef}
            className="pm-file"
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            onChange={(event) => void importProducts(event.target.files?.[0])}
          />
          {canCreate ? <Link className="primary" to="/merchant/products/new"><Plus />{t("catalog.add")}</Link> : <button type="button" className="primary" disabled title={deniedTitle(false)}><Plus />{t("catalog.add")}</button>}
        </div>
      </header>
      <div className="pm-tabs" role="tablist">
        {tabs.map(([value, key]) => (
          <button key={key} type="button" role="tab" aria-selected={status === value} className={status === value ? "active" : ""} onClick={() => patch({ status: value || null })}>
            {t(`catalog.tabs.${key}`)}
            <i>{counts[key === "all" ? "all" : key === "published" ? "published" : key === "draft" ? "draft" : "archived"]}</i>
          </button>
        ))}
      </div>
      <section className="pm-table-card">
        <form className="pm-filters pm-filters-brand" onSubmit={(event) => event.preventDefault()}>
          <label className="pm-search">
            <Search />
            <input aria-label={t("catalog.searchPh")} placeholder={t("catalog.searchPh")} value={query} onChange={(event) => setQuery(event.target.value)} />
          </label>
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
        </form>
        {selected.length > 0 && (
          <div className="pm-bulk">
            <span>{t("catalog.selected", { count: selected.length })}</span>
            <button type="button" disabled={busy || !canUpdate} title={deniedTitle(canUpdate)} onClick={() => void runMany(selected, "publish")}><Upload />{t("catalog.bulkPublish")}</button>
            <button type="button" disabled={busy || !canArchive} title={deniedTitle(canArchive)} onClick={() => setConfirmIds(selected)}><Archive />{t("catalog.bulkArchive")}</button>
          </div>
        )}
        {loading ? (
          <div className="pm-skeleton-list" aria-busy="true" aria-label={t("catalog.loading")}>
            {Array.from({ length: 6 }, (_, index) => <span key={index} />)}
          </div>
        ) : error ? (
          <div className="pm-state">
            <p>{catalogMessage(t, error)}</p>
            <button type="button" onClick={() => setReload((value) => value + 1)}>{t("catalog.actions.retry")}</button>
          </div>
        ) : items.length === 0 ? (
          <div className="pm-state">
            <h2>{t(filtered ? "catalog.empty.filterTitle" : "catalog.empty.noneTitle")}</h2>
            <p>{t(filtered ? "catalog.empty.filterBody" : "catalog.empty.noneBody")}</p>
            {filtered ? <button type="button" onClick={() => setParams({})}>{t("catalog.actions.clear")}</button> : canCreate ? <Link className="primary" to="/merchant/products/new">{t("catalog.actions.addFirst")}</Link> : <button type="button" className="primary" disabled title={deniedTitle(false)}>{t("catalog.actions.addFirst")}</button>}
          </div>
        ) : (
          <>
            <div className="pm-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th><input aria-label={t("catalog.columns.product")} type="checkbox" checked={selected.length === items.length} onChange={() => setSelected(selected.length === items.length ? [] : items.map((item) => item.id))} /></th>
                    <th>{t("catalog.columns.product")}</th>
                    <th>{t("catalog.columns.sku")}</th>
                    <th>{t("catalog.columns.category")}</th>
                    <th>{t("catalog.columns.price")}</th>
                    <th>{t("catalog.columns.stock")}</th>
                    <th>{t("catalog.columns.status")}</th>
                    <th>{t("catalog.columns.updated")}</th>
                    <th>{t("catalog.columns.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((product) => {
                    const badge = stockBadge(product);
                    const image = product.images.find((item) => item.primary) || product.images[0];
                    const available = stockTotal(product);
                    const variantText = product.productType === "VARIABLE"
                      ? t("catalog.variantLine", { count: product.variantCount ?? product.skus.filter((sku) => sku.active).length, stock: available })
                      : product.skus.length > 1 ? product.skus.map((sku) => sku.skuCode).join(", ") : product.skus[0]?.skuCode || "—";
                    return (
                      <tr key={product.id}>
                        <td><input aria-label={product.nameTh} type="checkbox" checked={selected.includes(product.id)} onChange={() => setSelected((current) => current.includes(product.id) ? current.filter((id) => id !== product.id) : [...current, product.id])} /></td>
                        <td>
                          <div className="pm-product">
                            <span className="pm-thumb"><AuthImage path={image?.url} alt="" /></span>
                            <p><b title={product.nameTh}>{product.nameTh}</b><small title={product.nameEn || product.slug}>{product.nameEn || product.slug}</small></p>
                          </div>
                        </td>
                        <td className="pm-clip" title={variantText}>{variantText}</td>
                        <td className="pm-clip">{categoryName(product.categoryId)}</td>
                        <td><b>{priceLabel(product, locale, formatMoney)}</b></td>
                        <td>
                          <div className="pm-stock">
                            <b className={badge === "out" ? "stock-zero" : badge === "low" ? "stock-low" : "stock-ok"}>{available}</b>
                            {badge !== "in" ? <span className={badge === "out" ? "pm-status empty" : "pm-status low"}>{t(`catalog.badges.${badge}`)}</span> : null}
                          </div>
                        </td>
                        <td className="pm-state-cell"><span className={product.status === "PUBLISHED" ? "pm-status ready" : product.status === "ARCHIVED" ? "pm-status empty" : "pm-status low"}>{t(`catalog.status.${product.status}`)}</span></td>
                        <td className="pm-clip" title={formatWhen(product.modifiedAt, locale)}>{formatWhen(product.modifiedAt, locale)}</td>
                        <td>
                          <div className="pm-row-actions">
                            <Link aria-label={t("catalog.actions.view")} to={`/merchant/products/${product.id}`}><Eye /></Link>
                            {canUpdate ? <Link aria-label={t("catalog.actions.edit")} to={`/merchant/products/${product.id}/edit`}><Pencil /></Link> : <button type="button" aria-label={t("catalog.actions.edit")} disabled title={deniedTitle(false)}><Pencil /></button>}
                            {product.status === "ARCHIVED"
                              ? <button type="button" aria-label={t("catalog.actions.unarchive")} title={deniedTitle(canArchive) ?? t("catalog.actions.unarchive")} disabled={busy || !canArchive} onClick={() => void runMany([product.id], "unarchive")}><RotateCcw /></button>
                              : <>
                                  {product.status !== "PUBLISHED" ? <button type="button" aria-label={t("catalog.actions.publish")} title={deniedTitle(canUpdate)} disabled={busy || !canUpdate} onClick={() => void runMany([product.id], "publish")}><Upload /></button> : null}
                                  <button type="button" className="delete" aria-label={t("catalog.actions.archive")} title={deniedTitle(canArchive)} disabled={busy || !canArchive} onClick={() => setConfirmIds([product.id])}><Archive /></button>
                                </>}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="pm-cards">
              {items.map((product) => {
                const badge = stockBadge(product);
                const image = product.images.find((item) => item.primary) || product.images[0];
                const available = stockTotal(product);
                const variantText = product.productType === "VARIABLE"
                  ? t("catalog.variantLine", { count: product.variantCount ?? product.skus.filter((sku) => sku.active).length, stock: available })
                  : product.skus.map((sku) => sku.skuCode).join(", ") || "—";
                return (
                  <article key={product.id}>
                    <AuthImage path={image?.url} alt="" />
                    <div>
                      <b>{product.nameTh}</b>
                      <small>{variantText}</small>
                      <small>{categoryName(product.categoryId)}</small>
                      <span>{priceLabel(product, locale, formatMoney)} · {available}</span>
                      <em className={badge === "out" ? "pm-status empty" : badge === "low" ? "pm-status low" : "pm-status ready"}>{badge === "in" ? t(`catalog.status.${product.status}`) : t(`catalog.badges.${badge}`)}</em>
                      <small>{formatWhen(product.modifiedAt, locale)}</small>
                      <div className="pm-row-actions">
                        <Link aria-label={t("catalog.actions.view")} to={`/merchant/products/${product.id}`}><Eye /></Link>
                        {canUpdate ? <Link aria-label={t("catalog.actions.edit")} to={`/merchant/products/${product.id}/edit`}><Pencil /></Link> : <button type="button" aria-label={t("catalog.actions.edit")} disabled title={deniedTitle(false)}><Pencil /></button>}
                        {product.status === "ARCHIVED"
                          ? <button type="button" aria-label={t("catalog.actions.unarchive")} title={deniedTitle(canArchive) ?? t("catalog.actions.unarchive")} disabled={busy || !canArchive} onClick={() => void runMany([product.id], "unarchive")}><RotateCcw /></button>
                          : <>
                              {product.status !== "PUBLISHED" ? <button type="button" title={deniedTitle(canUpdate)} disabled={busy || !canUpdate} onClick={() => void runMany([product.id], "publish")}><Upload /></button> : null}
                              <button type="button" className="delete" title={deniedTitle(canArchive)} disabled={busy || !canArchive} onClick={() => setConfirmIds([product.id])}><Archive /></button>
                            </>}
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          </>
        )}
        <div className="pm-pagination">
          <span>{t("catalog.showing", { from, to, total })}</span>
          <div className="pm-page-size">
            <span>{t("catalog.perPage")}</span>
            <SearchableSelect compact label={t("catalog.perPage")} options={["10", "20", "50"]} value={String(size)} onChange={(picked) => patch({ size: picked, page: "1" })} />
          </div>
          <nav>
            <button type="button" aria-label={t("catalog.first")} disabled={page <= 0} onClick={() => patch({ page: "1" })}><ChevronFirst /></button>
            <button type="button" aria-label={t("catalog.prev")} disabled={page <= 0} onClick={() => patch({ page: String(page) })}><ChevronLeft /></button>
            {Array.from({ length: Math.min(5, pages) }, (_, index) => {
              const start = Math.max(0, Math.min(page - 2, pages - Math.min(5, pages)));
              const number = start + index;
              return (
                <button key={number} type="button" className={number === page ? "active" : ""} aria-current={number === page ? "page" : undefined} onClick={() => patch({ page: String(number + 1) })}>
                  {number + 1}
                </button>
              );
            })}
            <button type="button" aria-label={t("catalog.next")} disabled={page + 1 >= pages} onClick={() => patch({ page: String(page + 2) })}><ChevronRight /></button>
            <button type="button" aria-label={t("catalog.last")} disabled={page + 1 >= pages} onClick={() => patch({ page: String(pages) })}><ChevronLast /></button>
          </nav>
        </div>
      </section>
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
    </MerchantShell>
  );
}

