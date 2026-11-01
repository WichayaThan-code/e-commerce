import { useEffect, useState } from "react";
import { SkeletonForm, SkeletonTable, SkeletonBusy, LoadError } from "../components/feedback/Skeleton";
import { useDelayedLoading } from "../hooks/useDelayedLoading";
import { Link, useParams } from "react-router-dom";
import { Archive, ExternalLink, Link2, LoaderCircle, Pencil, QrCode, Upload } from "lucide-react";
import { useTranslation } from "react-i18next";
import ConfirmDialog from "../components/form/ConfirmDialog";
import SearchableSelect from "../components/SearchableSelect";
import MerchantShell from "../components/merchant/MerchantShell";
import AuthImage from "../components/merchant/AuthImage";
import ProductQrModal from "../components/shop/ProductQrModal";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { catalogMessage, formatMoney, formatWhen } from "../services/catalogText";
import { ApiError, productApi, type LedgerEntry, type Product, type ProductSku } from "../services/api";
import { canOpenStorefront, copyText, productPageUrl, productShareLink } from "../utils/productLinks";

type StockMovement = "RECEIVE" | "ISSUE" | "ADJUST" | "RESERVE" | "RELEASE";

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
  const [qrOpen, setQrOpen] = useState(false);
  const [adjust, setAdjust] = useState({ skuId: "", entryType: "RECEIVE" as StockMovement, quantityDelta: "1", reason: "" });
  const shopSlug = shop?.slug?.trim() || "";

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

  const { showSkeleton, markHasData, isRefetching } = useDelayedLoading(loading, 150, productId);

  useEffect(() => {
    if (!loading && product) markHasData();
  }, [loading, product, markHasData]);

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
    const quantity = Number(adjust.quantityDelta);
    const needsPositive = adjust.entryType === "ISSUE" || adjust.entryType === "RECEIVE" || adjust.entryType === "RESERVE" || adjust.entryType === "RELEASE";
    if (!sku || !Number.isInteger(quantity) || (needsPositive ? quantity <= 0 : quantity === 0)) {
      toast(t("catalog.errors.INVALID_ADJUSTMENT"), "fail");
      return;
    }
    if (adjust.entryType !== "RECEIVE" && !adjust.reason.trim()) {
      toast(t("catalog.errors.STOCK_REASON_REQUIRED"), "fail");
      return;
    }
    setBusy(true);
    try {
      await productApi.adjustStock(shopId, sku.id, {
        entryType: adjust.entryType,
        quantityDelta: quantity,
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

  if (showSkeleton && loading && !product) {
    return (
      <MerchantShell>
        <SkeletonForm fields={6} />
        <SkeletonTable rows={4} cols={7} className="pm-detail" />
      </MerchantShell>
    );
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
        <LoadError
          message={catalogMessage(t, error)}
          onRetry={() => load()}
          retryLabel={t("catalog.actions.retry")}
        />
      </MerchantShell>
    );
  }

  const image = product.images[activeImage] || product.images[0];
  const locale = i18n.language;
  const stockTotal = product.skus.reduce((sum, sku) => sum + sku.available, 0);
  const movementKey: Record<StockMovement, string> = { RECEIVE: "receive", ISSUE: "issue", ADJUST: "adjust", RESERVE: "reserve", RELEASE: "release" };
  const movementLabel = (entry: LedgerEntry) => {
    const key = movementKey[entry.entryType as StockMovement];
    return key ? t(`catalog.detail.${key}`) : entry.entryType;
  };
  const movements: StockMovement[] = ["RECEIVE", "ISSUE", "ADJUST", "RESERVE", "RELEASE"];
  const movementText = (type: StockMovement) => t(`catalog.detail.${movementKey[type]}`);
  const skuLabel = (sku: ProductSku) => sku.skuCode;
  const selectedSku = product.skus.find((sku) => sku.id === adjust.skuId);
  const stockMark = (sku: ProductSku) => (sku.available <= 0 && sku.onHand > 0 ? "low" : sku.available <= 0 ? "out" : null);
  const names = { th: product.nameTh, en: product.nameEn, zh: product.nameZh };
  const descriptions = { th: product.descriptionTh, en: product.descriptionEn, zh: product.descriptionZh };
  const storefrontReady = canOpenStorefront(product.status, shopSlug, product.slug);
  const storefrontTip = storefrontReady ? undefined : t("catalog.actions.notPublished");
  return (
    <MerchantShell notice={notice} tone={tone}>
      <SkeletonBusy refetching={isRefetching} busy={isRefetching}>
      <header className="pm-heading">
        <div>
          <h1>{product.nameTh}</h1>
          <p><span className={product.status === "PUBLISHED" ? "pm-status ready" : product.status === "ARCHIVED" ? "pm-status empty" : "pm-status low"}>{t(`catalog.status.${product.status}`)}</span></p>
        </div>
        <div className="pm-actions">
          <button
            type="button"
            title={storefrontTip ?? t("catalog.actions.viewStorefront")}
            disabled={!storefrontReady}
            onClick={() => window.open(productPageUrl(shopSlug, product.slug), "_blank", "noopener,noreferrer")}
          >
            <ExternalLink />{t("catalog.actions.viewStorefront")}
          </button>
          <button
            type="button"
            title={storefrontTip ?? t("catalog.actions.copyLink")}
            disabled={!storefrontReady}
            onClick={() => {
              void copyText(productShareLink(shopSlug, product.slug)).then((ok) => {
                toast(ok ? t("catalog.toast.linkCopied") : catalogMessage(t, null), ok ? "ok" : "fail");
              });
            }}
          >
            <Link2 />{t("catalog.actions.copyLink")}
          </button>
          <button
            type="button"
            title={storefrontTip ?? t("catalog.actions.qrCode")}
            disabled={!storefrontReady}
            onClick={() => setQrOpen(true)}
          >
            <QrCode />{t("catalog.actions.qrCode")}
          </button>
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
              <div><dt>{t("catalog.form.price")}</dt><dd><span className="num">{formatMoney(product.basePrice, locale)}</span>{product.compareAtPrice ? <s className="num">{formatMoney(product.compareAtPrice, locale)}</s> : null}</dd></div>
              <div><dt>{t("catalog.columns.stock")}</dt><dd className="num">{stockTotal}</dd></div>
              <div><dt>{t("catalog.form.type")}</dt><dd>{t(product.productType === "VARIABLE" ? "catalog.form.variable" : "catalog.form.simple")}</dd></div>
              <div><dt>{t("catalog.form.brand")}</dt><dd>{product.brand || "—"}</dd></div>
              <div><dt>{t("catalog.form.weight")}</dt><dd className="num">{product.weightGrams ?? "—"}</dd></div>
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
              <thead><tr><th>{t("catalog.form.sku")}</th><th>{t("catalog.form.options")}</th><th>{t("catalog.form.price")}</th><th>{t("catalog.detail.onHand")}</th><th>{t("catalog.detail.reserved")}</th><th>{t("catalog.detail.safetyStock")}</th><th>{t("catalog.detail.available")}</th></tr></thead>
              <tbody>
                {product.skus.map((sku) => {
                  const mark = stockMark(sku);
                  return (
                  <tr key={sku.id} className={sku.id === adjust.skuId ? "is-selected" : ""}>
                    <td className="num">{sku.skuCode}</td>
                    <td>{sku.optionLabel || "—"}</td>
                    <td className="num">{formatMoney(sku.price, locale)}</td>
                    <td className="num">{sku.onHand}</td>
                    <td className="num">{sku.reservedQty}</td>
                    <td className="num">{sku.safetyStock ?? 0}</td>
                    <td className="num">{sku.available}{mark === "out" ? <span className="pm-status empty">{t("catalog.badges.out")}</span> : mark === "low" ? <span className="pm-status low">{t("catalog.badges.low")}</span> : null}</td>
                  </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <form className="pm-adjust" onSubmit={(event) => { event.preventDefault(); void submitStock(); }}>
            <SearchableSelect
              label={t("catalog.form.sku")}
              placeholder={t("catalog.form.sku")}
              options={product.skus.map(skuLabel)}
              value={selectedSku ? skuLabel(selectedSku) : ""}
              onChange={(picked) => {
                const match = product.skus.find((sku) => skuLabel(sku) === picked);
                if (match) setAdjust({ ...adjust, skuId: match.id });
              }}
            />
            <SearchableSelect
              label={t("catalog.detail.entry")}
              placeholder={t("catalog.detail.entry")}
              options={movements.map(movementText)}
              value={movementText(adjust.entryType)}
              onChange={(picked) => {
                const match = movements.find((type) => movementText(type) === picked);
                if (match) setAdjust({ ...adjust, entryType: match });
              }}
            />
            <label>{t("catalog.detail.delta")}
              <input className="num" inputMode="numeric" value={adjust.quantityDelta} onChange={(event) => setAdjust({ ...adjust, quantityDelta: event.target.value })} />
            </label>
            {adjust.entryType !== "RECEIVE" ? (
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
                <thead><tr><th>{t("catalog.detail.when")}</th><th>{t("catalog.form.sku")}</th><th>{t("catalog.detail.entry")}</th><th>{t("catalog.detail.delta")}</th><th>{t("catalog.detail.before")}</th><th>{t("catalog.detail.after")}</th><th>{t("catalog.detail.reservedBefore")}</th><th>{t("catalog.detail.reservedAfter")}</th><th>{t("catalog.detail.actor")}</th><th>{t("catalog.detail.reason")}</th></tr></thead>
                <tbody>
                  {ledger.map((entry) => (
                    <tr key={entry.id}>
                      <td>{formatWhen(entry.createdAt, locale)}</td>
                      <td className="num">{entry.skuCode}</td>
                      <td>{movementLabel(entry)}</td>
                      <td className={`num ${entry.quantityDelta < 0 ? "is-down" : "is-up"}`}>{entry.quantityDelta > 0 ? `+${entry.quantityDelta}` : entry.quantityDelta}</td>
                      <td className="num">{entry.onHandBefore}</td>
                      <td className="num">{entry.onHandAfter}</td>
                      <td className="num">{entry.reservedBefore ?? "—"}</td>
                      <td className="num">{entry.reservedAfter ?? "—"}</td>
                      <td>{entry.actorName || "—"}</td>
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
        <ConfirmDialog
          title={t("catalog.archiveTitle")}
          message={t("catalog.archiveBody")}
          cancelLabel={t("catalog.actions.cancel")}
          confirmLabel={t("catalog.actions.confirm")}
          confirmDisabled={busy}
          onCancel={() => setConfirm(false)}
          onConfirm={() => void act("archive")}
        />
      )}
      {qrOpen && storefrontReady ? (
        <ProductQrModal
          productUrl={productPageUrl(shopSlug, product.slug)}
          name={product.nameTh}
          onClose={() => setQrOpen(false)}
        />
      ) : null}
      </SkeletonBusy>
    </MerchantShell>
  );
}
