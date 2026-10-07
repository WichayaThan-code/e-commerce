import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Archive, ChevronFirst, ChevronLast, ChevronLeft, ChevronRight, Download, Eye, LoaderCircle, Pencil, Plus, RotateCcw, Search, Upload } from "lucide-react";
import { useTranslation } from "react-i18next";
import ImportProgressDialog, { importJobKey } from "../components/merchant/ImportProgressDialog";
import MerchantShell from "../components/merchant/MerchantShell";
import AuthImage from "../components/merchant/AuthImage";
import SearchableSelect from "../components/SearchableSelect";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { labelForCategory } from "../services/categories";
import { catalogMessage, formatMoney, formatWhen } from "../services/catalogText";
import { ApiError, catalogApi, productApi, type Product, type ProductCategory, type ProductSort, type ProductStatus, type StockFilter } from "../services/api";
import type { Locale } from "../i18n";

const tabs = [
  ["", "all"],
  ["PUBLISHED", "published"],
  ["DRAFT", "draft"],
  ["ARCHIVED", "archived"],
] as const;

function stockBadge(product: Product) {
  const rows = product.skus.filter((sku) => sku.active);
  const available = rows.reduce((sum, sku) => sum + sku.available, 0);
  if (available <= 0) return "out" as const;
  if (rows.some((sku) => sku.lowStockThreshold != null && sku.available > 0 && sku.available <= sku.lowStockThreshold)) return "low" as const;
  return "in" as const;
}

export default function ProductManagementPage() {
  const { t, i18n } = useTranslation("merchant");
  const locale = (i18n.language || "th") as Locale;
  const { shopId } = useActiveShop();
  const [params, setParams] = useSearchParams();
  const q = params.get("q") || "";
  const status = (params.get("status") || "") as "" | ProductStatus;
  const categoryId = params.get("categoryId") || "";
  const stock = (params.get("stock") || "") as "" | StockFilter;
  const sort = (params.get("sort") || "newest") as ProductSort;
  const page = Math.max(0, Number(params.get("page") || "1") - 1);
  const size = Number(params.get("size") || "20");
  const [query, setQuery] = useState(q);
  const [items, setItems] = useState<Product[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState({ all: 0, published: 0, draft: 0, archived: 0 });
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [notice, setNotice] = useState("");
  const [tone, setTone] = useState<"ok" | "fail">("ok");
  const [busy, setBusy] = useState(false);
  const [confirmIds, setConfirmIds] = useState<string[] | null>(null);
  const [reload, setReload] = useState(0);
  const [transfer, setTransfer] = useState<"export" | "import" | null>(null);
  const [importBusy, setImportBusy] = useState(false);
  const [uploadPercent, setUploadPercent] = useState<number | null>(null);
  const [importJobId, setImportJobId] = useState<string | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const importRef = useRef<HTMLInputElement>(null);

  useEffect(() => { setQuery(q); }, [q]);
  useEffect(() => {
    if (shopId == null) return;
    const saved = sessionStorage.getItem(importJobKey(shopId));
    if (!saved) return;
    setImportJobId(saved);
    setImportBusy(true);
  }, [shopId]);
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
    let active = true;
    setLoading(true);
    setError(null);
    const shared = { q, categoryId: categoryId ? Number(categoryId) : undefined, stock: stock || undefined };
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
  }, [shopId, q, status, categoryId, stock, sort, page, size, reload]);

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
    if (shopId == null || !file || transfer || importBusy) return;
    setImportError(null);
    setImportJobId(null);
    setUploadPercent(0);
    setImportBusy(true);
    try {
      const accepted = await productApi.importExcel(shopId, file, setUploadPercent);
      sessionStorage.setItem(importJobKey(shopId), accepted.jobId);
      setUploadPercent(null);
      setImportJobId(accepted.jobId);
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 409 && caught.code === "IMPORT_RUNNING" && caught.jobId) {
        sessionStorage.setItem(importJobKey(shopId), caught.jobId);
        setUploadPercent(null);
        setImportJobId(caught.jobId);
        return;
      }
      setUploadPercent(null);
      setImportError(catalogMessage(t, caught));
      setImportBusy(false);
    } finally {
      if (importRef.current) importRef.current.value = "";
    }
  };
  const closeImport = () => {
    if (shopId != null) sessionStorage.removeItem(importJobKey(shopId));
    setImportJobId(null);
    setImportError(null);
    setUploadPercent(null);
    setImportBusy(false);
    setReload((value) => value + 1);
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
  const filtered = Boolean(q || categoryId || stock || status);
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
    ["name", t("catalog.sortName")],
    ["price_asc", t("catalog.sortPriceAsc")],
    ["price_desc", t("catalog.sortPriceDesc")],
    ["stock", t("catalog.sortStock")],
  ] as const;
  const selectedCategory = categories.find((category) => String(category.id) === categoryId);

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
          <button type="button" className="primary" title={t("catalog.importHint")} disabled={shopId == null || transfer != null || importBusy} onClick={() => importRef.current?.click()}>
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
          <Link className="primary" to="/merchant/products/new"><Plus />{t("catalog.add")}</Link>
        </div>
      </header>
      {shopId != null && (uploadPercent != null || importJobId || importError) ? (
        <ImportProgressDialog
          shopId={shopId}
          uploadPercent={uploadPercent}
          jobId={importJobId}
          uploadError={importError}
          onBusy={setImportBusy}
          onClose={closeImport}
        />
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
        <form className="pm-filters" onSubmit={(event) => event.preventDefault()}>
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
            <button type="button" disabled={busy} onClick={() => void runMany(selected, "publish")}><Upload />{t("catalog.bulkPublish")}</button>
            <button type="button" disabled={busy} onClick={() => setConfirmIds(selected)}><Archive />{t("catalog.bulkArchive")}</button>
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
            {filtered ? <button type="button" onClick={() => setParams({})}>{t("catalog.actions.clear")}</button> : <Link className="primary" to="/merchant/products/new">{t("catalog.actions.addFirst")}</Link>}
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
                    const available = product.skus.reduce((sum, sku) => sum + sku.available, 0);
                    return (
                      <tr key={product.id}>
                        <td><input aria-label={product.nameTh} type="checkbox" checked={selected.includes(product.id)} onChange={() => setSelected((current) => current.includes(product.id) ? current.filter((id) => id !== product.id) : [...current, product.id])} /></td>
                        <td>
                          <div className="pm-product">
                            <span className="pm-thumb"><AuthImage path={image?.url} alt="" /></span>
                            <p><b title={product.nameTh}>{product.nameTh}</b><small title={product.nameEn || product.slug}>{product.nameEn || product.slug}</small></p>
                          </div>
                        </td>
                        <td className="pm-clip" title={product.skus.map((sku) => sku.skuCode).join(", ")}>{product.skus.length > 1 ? product.skus.map((sku) => sku.skuCode).join(", ") : product.skus[0]?.skuCode || "—"}</td>
                        <td className="pm-clip">{categoryName(product.categoryId)}</td>
                        <td><b>{formatMoney(product.basePrice ?? product.skus[0]?.price, locale)}</b></td>
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
                            <Link aria-label={t("catalog.actions.edit")} to={`/merchant/products/${product.id}/edit`}><Pencil /></Link>
                            {product.status === "ARCHIVED"
                              ? <button type="button" aria-label={t("catalog.actions.unarchive")} title={t("catalog.actions.unarchive")} disabled={busy} onClick={() => void runMany([product.id], "unarchive")}><RotateCcw /></button>
                              : <>
                                  {product.status !== "PUBLISHED" ? <button type="button" aria-label={t("catalog.actions.publish")} disabled={busy} onClick={() => void runMany([product.id], "publish")}><Upload /></button> : null}
                                  <button type="button" className="delete" aria-label={t("catalog.actions.archive")} disabled={busy} onClick={() => setConfirmIds([product.id])}><Archive /></button>
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
                const available = product.skus.reduce((sum, sku) => sum + sku.available, 0);
                return (
                  <article key={product.id}>
                    <AuthImage path={image?.url} alt="" />
                    <div>
                      <b>{product.nameTh}</b>
                      <small>{product.skus.map((sku) => sku.skuCode).join(", ") || "—"}</small>
                      <small>{categoryName(product.categoryId)}</small>
                      <span>{formatMoney(product.basePrice ?? product.skus[0]?.price, locale)} · {available}</span>
                      <em className={badge === "out" ? "pm-status empty" : badge === "low" ? "pm-status low" : "pm-status ready"}>{badge === "in" ? t(`catalog.status.${product.status}`) : t(`catalog.badges.${badge}`)}</em>
                      <small>{formatWhen(product.modifiedAt, locale)}</small>
                      <div className="pm-row-actions">
                        <Link aria-label={t("catalog.actions.view")} to={`/merchant/products/${product.id}`}><Eye /></Link>
                        <Link aria-label={t("catalog.actions.edit")} to={`/merchant/products/${product.id}/edit`}><Pencil /></Link>
                        {product.status === "ARCHIVED"
                          ? <button type="button" aria-label={t("catalog.actions.unarchive")} title={t("catalog.actions.unarchive")} disabled={busy} onClick={() => void runMany([product.id], "unarchive")}><RotateCcw /></button>
                          : <>
                              {product.status !== "PUBLISHED" ? <button type="button" disabled={busy} onClick={() => void runMany([product.id], "publish")}><Upload /></button> : null}
                              <button type="button" className="delete" disabled={busy} onClick={() => setConfirmIds([product.id])}><Archive /></button>
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
      {confirmIds && (
        <div className="pm-dialog" role="dialog" aria-modal="true" aria-labelledby="archive-title">
          <section>
            <h2 id="archive-title">{confirmIds.length > 1 ? t("catalog.archiveBulk", { count: confirmIds.length }) : t("catalog.archiveTitle")}</h2>
            <p>{t("catalog.archiveBody")}</p>
            <div>
              <button type="button" onClick={() => setConfirmIds(null)}>{t("catalog.actions.cancel")}</button>
              <button type="button" className="primary" disabled={busy} onClick={() => void runMany(confirmIds, "archive")}>{busy ? <LoaderCircle className="pm-spin" /> : null}{t("catalog.actions.confirm")}</button>
            </div>
          </section>
        </div>
      )}
    </MerchantShell>
  );
}
