import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Archive, ExternalLink, LoaderCircle, Pencil, Upload } from "lucide-react";
import { useTranslation } from "react-i18next";
import MerchantShell from "../components/merchant/MerchantShell";
import AuthImage from "../components/merchant/AuthImage";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { catalogMessage, formatMoney, formatWhen } from "../services/catalogText";
import { ApiError, productApi, type LedgerEntry, type Product } from "../services/api";

export default function ProductDetailPage() {
  const { productId = "" } = useParams();
  const { t, i18n } = useTranslation("merchant");
  const { shopId, shops } = useActiveShop();
  const shop = shops.find((item) => item.shopId === shopId);
  const [product, setProduct] = useState<Product | null>(null);
  const [ledger, setLedger] = useState<LedgerEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState("");
  const [tone, setTone] = useState<"ok" | "fail">("ok");
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [activeImage, setActiveImage] = useState(0);
  const [adjust, setAdjust] = useState({ skuId: "", entryType: "RECEIVE" as "RECEIVE" | "ADJUST", quantityDelta: "1", reason: "" });

  const load = (quiet = false) => {
    if (shopId == null) return;
    if (!quiet) setLoading(true);
    setError(null);
    Promise.all([productApi.get(shopId, productId), productApi.ledger(shopId, productId)])
      .then(([item, history]) => {
        setProduct(item);
        setLedger(history);
        setAdjust((current) => ({ ...current, skuId: current.skuId || item.skus[0]?.id || "" }));
      })
      .catch(setError)
      .finally(() => {
        setLoading(false);
        setBusy(false);
      });
  };

  useEffect(() => { load(); }, [shopId, productId]);

  const toast = (text: string, next: "ok" | "fail" = "ok") => {
    setTone(next);
    setNotice(text);
    window.setTimeout(() => setNotice(""), 2200);
  };
  const act = async (action: "publish" | "archive" | "unarchive") => {
    if (shopId == null || !product) return;
    setBusy(true);
    try {
      const saved = await productApi[action](shopId, product.id);
      setProduct(saved);
      setConfirm(false);
      toast(t(action === "publish" ? "catalog.toast.published" : action === "unarchive" ? "catalog.toast.unarchived" : "catalog.toast.archived"));
    } catch (caught) {
      toast(catalogMessage(t, caught), "fail");
    } finally {
      setBusy(false);
    }
  };
  const submitStock = async () => {
    if (shopId == null || !product) return;
    const sku = product.skus.find((item) => item.id === adjust.skuId);
    const quantityDelta = Number(adjust.quantityDelta);
    if (!sku || !Number.isInteger(quantityDelta) || quantityDelta === 0) {
      toast(t("catalog.errors.INVALID_ADJUSTMENT"), "fail");
      return;
    }
    if (adjust.entryType === "ADJUST" && !adjust.reason.trim()) {
      toast(t("catalog.errors.STOCK_REASON_REQUIRED"), "fail");
      return;
    }
    setBusy(true);
    try {
      await productApi.adjustStock(shopId, sku.id, {
        entryType: adjust.entryType,
        quantityDelta,
        reason: adjust.reason.trim() || undefined,
        version: sku.version,
      });
      toast(t("catalog.toast.stock"));
      load(true);
    } catch (caught) {
      toast(catalogMessage(t, caught), "fail");
      setBusy(false);
    }
  };

  if (loading) {
    return <MerchantShell><div className="pm-skeleton-list" aria-label={t("catalog.loading")}>{Array.from({ length: 4 }, (_, index) => <span key={index} />)}</div></MerchantShell>;
  }
  if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
    return (
      <MerchantShell>
        <section className="pm-state">
          <h1>{t(error.status === 403 ? "catalog.forbiddenTitle" : "catalog.missingTitle")}</h1>
          <p>{catalogMessage(t, error)}</p>
          <Link to="/merchant/products">{t("catalog.actions.back")}</Link>
        </section>
      </MerchantShell>
    );
  }
  if (error || !product) {
    return (
      <MerchantShell>
        <section className="pm-state">
          <p>{catalogMessage(t, error)}</p>
          <button type="button" onClick={() => load()}>{t("catalog.actions.retry")}</button>
        </section>
      </MerchantShell>
    );
  }

  const image = product.images[activeImage] || product.images[0];
  const locale = i18n.language;
  const stockTotal = product.skus.reduce((sum, sku) => sum + sku.available, 0);
  const names = { th: product.nameTh, en: product.nameEn, zh: product.nameZh };
  const descriptions = { th: product.descriptionTh, en: product.descriptionEn, zh: product.descriptionZh };
  return (
    <MerchantShell notice={notice} tone={tone}>
      <header className="pm-heading">
        <div>
          <h1>{product.nameTh}</h1>
          <p><span className={product.status === "PUBLISHED" ? "pm-status ready" : product.status === "ARCHIVED" ? "pm-status empty" : "pm-status low"}>{t(`catalog.status.${product.status}`)}</span></p>
        </div>
        <div className="pm-actions">
          {product.status === "PUBLISHED" && shop ? <a href={`/shop/${shop.slug}/product/${product.slug}`}><ExternalLink />{t("catalog.actions.viewShop")}</a> : null}
          <Link to={`/merchant/products/${product.id}/edit`}><Pencil />{t("catalog.actions.edit")}</Link>
          {product.status === "ARCHIVED"
            ? <button type="button" disabled={busy} onClick={() => void act("unarchive")}>{t("catalog.actions.unarchive")}</button>
            : <>
                {product.status !== "PUBLISHED" ? <button type="button" className="primary" disabled={busy} onClick={() => void act("publish")}><Upload />{t("catalog.actions.publish")}</button> : null}
                <button type="button" disabled={busy} onClick={() => setConfirm(true)}><Archive />{t("catalog.actions.archive")}</button>
              </>}
        </div>
      </header>
      <div className="pm-detail">
        <div className="pm-detail-grid">
          <section className="pm-gallery">
            <h2>{t("catalog.detail.gallery")}</h2>
            <div className="pm-hero-frame">
              {image ? <AuthImage className="pm-hero" path={image.url} alt={product.nameTh} /> : <div className="pm-hero pm-hero-empty">{t("catalog.detail.noImage")}</div>}
            </div>
            {product.images.length > 1 ? (
              <div className="pm-thumbs">
                {product.images.map((item, index) => (
                  <button type="button" key={item.id} className={index === activeImage ? "active" : ""} aria-pressed={index === activeImage} onClick={() => setActiveImage(index)}>
                    <AuthImage path={item.url} alt="" />
                  </button>
                ))}
              </div>
            ) : null}
          </section>
          <section className="pm-summary">
            <h2>{t("catalog.form.general")}</h2>
            <dl className="pm-facts">
              <div><dt>{t("catalog.form.price")}</dt><dd>{formatMoney(product.basePrice, locale)}{product.compareAtPrice ? <s>{formatMoney(product.compareAtPrice, locale)}</s> : null}</dd></div>
              <div><dt>{t("catalog.columns.stock")}</dt><dd>{stockTotal}</dd></div>
              <div><dt>{t("catalog.form.type")}</dt><dd>{t(product.productType === "VARIABLE" ? "catalog.form.variable" : "catalog.form.simple")}</dd></div>
              <div><dt>{t("catalog.form.brand")}</dt><dd>{product.brand || "—"}</dd></div>
              <div><dt>{t("catalog.form.weight")}</dt><dd>{product.weightGrams ?? "—"}</dd></div>
              <div><dt>{t("catalog.columns.updated")}</dt><dd>{product.modifiedAt ? formatWhen(product.modifiedAt, locale) : "—"}</dd></div>
            </dl>
            <div className="pm-langs">
              {(["th", "en", "zh"] as const).map((lang) => (
                <article key={lang}>
                  <b>{t(`catalog.form.lang${lang === "th" ? "Th" : lang === "en" ? "En" : "Zh"}`)}</b>
                  <p>{names[lang] || "—"}</p>
                  {descriptions[lang] ? <p>{descriptions[lang]}</p> : null}
                </article>
              ))}
            </div>
          </section>
        </div>
        <section>
          <h2>{t("catalog.detail.skus")}</h2>
          <div className="pm-table-scroll">
            <table>
              <thead><tr><th>{t("catalog.form.sku")}</th><th>{t("catalog.form.options")}</th><th>{t("catalog.form.price")}</th><th>{t("catalog.columns.stock")}</th></tr></thead>
              <tbody>
                {product.skus.map((sku) => (
                  <tr key={sku.id} className={sku.id === adjust.skuId ? "is-selected" : ""}>
                    <td>{sku.skuCode}</td>
                    <td>{sku.optionLabel || "—"}</td>
                    <td>{formatMoney(sku.price, locale)}</td>
                    <td>{sku.available}{sku.available <= 0 ? <span className="pm-status empty">{t("catalog.badges.out")}</span> : sku.lowStockThreshold != null && sku.available <= sku.lowStockThreshold ? <span className="pm-status low">{t("catalog.badges.low")}</span> : null}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <form className="pm-adjust" onSubmit={(event) => { event.preventDefault(); void submitStock(); }}>
            <label>{t("catalog.form.sku")}
              <select value={adjust.skuId} onChange={(event) => setAdjust({ ...adjust, skuId: event.target.value })}>
                {product.skus.map((sku) => <option key={sku.id} value={sku.id}>{sku.skuCode}</option>)}
              </select>
            </label>
            <label>{t("catalog.detail.entry")}
              <select value={adjust.entryType} onChange={(event) => setAdjust({ ...adjust, entryType: event.target.value as "RECEIVE" | "ADJUST" })}>
                <option value="RECEIVE">{t("catalog.detail.receive")}</option>
                <option value="ADJUST">{t("catalog.detail.adjust")}</option>
              </select>
            </label>
            <label>{t("catalog.detail.delta")}
              <input inputMode="numeric" value={adjust.quantityDelta} onChange={(event) => setAdjust({ ...adjust, quantityDelta: event.target.value })} />
            </label>
            {adjust.entryType === "ADJUST" ? (
              <label>{t("catalog.detail.reason")}
                <input placeholder={t("catalog.detail.reasonPh")} value={adjust.reason} onChange={(event) => setAdjust({ ...adjust, reason: event.target.value })} />
              </label>
            ) : null}
            <button type="submit" className="primary" disabled={busy}>{busy ? <LoaderCircle className="pm-spin" /> : null}{t("catalog.actions.adjust")}</button>
          </form>
        </section>
        <section>
          <h2>{t("catalog.detail.ledger")}</h2>
          {ledger.length === 0 ? <p className="pm-empty-note">{t("catalog.detail.emptyLedger")}</p> : (
            <div className="pm-table-scroll">
              <table>
                <thead><tr><th>{t("catalog.detail.when")}</th><th>{t("catalog.form.sku")}</th><th>{t("catalog.detail.entry")}</th><th>{t("catalog.detail.delta")}</th><th>{t("catalog.detail.before")}</th><th>{t("catalog.detail.after")}</th><th>{t("catalog.detail.reason")}</th></tr></thead>
                <tbody>
                  {ledger.map((entry) => (
                    <tr key={entry.id}>
                      <td>{formatWhen(entry.createdAt, locale)}</td>
                      <td>{entry.skuCode}</td>
                      <td>{entry.entryType === "RECEIVE" ? t("catalog.detail.receive") : t("catalog.detail.adjust")}</td>
                      <td className={entry.quantityDelta < 0 ? "is-down" : "is-up"}>{entry.quantityDelta > 0 ? `+${entry.quantityDelta}` : entry.quantityDelta}</td>
                      <td>{entry.onHandBefore}</td>
                      <td>{entry.onHandAfter}</td>
                      <td>{entry.reason || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
      {confirm && (
        <div className="pm-dialog" role="dialog" aria-modal="true">
          <section>
            <h2>{t("catalog.archiveTitle")}</h2>
            <p>{t("catalog.archiveBody")}</p>
            <div>
              <button type="button" onClick={() => setConfirm(false)}>{t("catalog.actions.cancel")}</button>
              <button type="button" className="primary" disabled={busy} onClick={() => void act("archive")}>{t("catalog.actions.confirm")}</button>
            </div>
          </section>
        </div>
      )}
    </MerchantShell>
  );
}
