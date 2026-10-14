import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useBlocker, useNavigate, useParams } from "react-router-dom";
import { Boxes, Check, LoaderCircle, Package, Plus, Trash2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { FieldError } from "../components/form/FormField";
import SearchableSelect from "../components/SearchableSelect";
import MerchantShell from "../components/merchant/MerchantShell";
import ProductImagesField, { type LocalImage } from "../components/merchant/ProductImagesField";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { usePermissions } from "../hooks/usePermissions";
import { labelForCategory } from "../services/categories";
import { catalogMessage } from "../services/catalogText";
import { ApiError, catalogApi, productApi, type Product, type ProductCategory, type TranslationMeta } from "../services/api";
import {
  axesFromSkus, combineSkus, emptyDraft, emptySku, fromProduct, toWrite, validateDraft,
  type OptionAxis, type ProductDraft,
} from "./productFormModel";
import "../styles/form-fields.css";

/** Error keys that have an inline slot in the form; anything else the server reports is listed at the top. */
const placedField = /^(nameTh|nameEn|nameZh|brand|basePrice|compareAtPrice|weightGrams|skus|images|skus\.\d+\.(skuCode|barcode|price|compareAtPrice|initialStock))$/;

const langFields = [
  { id: "th" as const, locale: null as null, nameKey: "nameTh" as const, descriptionKey: "descriptionTh" as const, required: true },
  { id: "en" as const, locale: "en" as const, nameKey: "nameEn" as const, descriptionKey: "descriptionEn" as const, required: false, nameMetaKey: "nameEnMeta" as const, descriptionMetaKey: "descriptionEnMeta" as const },
  { id: "zh" as const, locale: "zh" as const, nameKey: "nameZh" as const, descriptionKey: "descriptionZh" as const, required: false, nameMetaKey: "nameZhMeta" as const, descriptionMetaKey: "descriptionZhMeta" as const },
];

function translationBadge(t: TFunction<"merchant">, meta?: TranslationMeta | null) {
  if (!meta) return null;
  if (meta.status === "PENDING") return t("catalog.form.translationPending");
  if (meta.status === "FAILED") return t("catalog.form.translationFailed");
  return meta.origin === "HUMAN" ? t("catalog.form.translationManual") : t("catalog.form.translationAuto");
}

export default function ProductFormPage() {
  const { productId } = useParams();
  const navigate = useNavigate();
  const { t, i18n } = useTranslation("merchant");
  const { shopId } = useActiveShop();
  const { canInShop, deniedTitle } = usePermissions();
  const editing = Boolean(productId);
  const canCreate = canInShop("PRODUCT_CREATE");
  const canUpdate = canInShop("PRODUCT_UPDATE");
  // create needs PRODUCT_CREATE; edit, image upload and publish need PRODUCT_UPDATE (same codes the API checks)
  const canSaveDraft = editing ? canUpdate : canCreate;
  const canPublish = editing ? canUpdate : canCreate && canUpdate;
  const [draft, setDraft] = useState<ProductDraft>(emptyDraft);
  const [baseline, setBaseline] = useState(() => JSON.stringify(emptyDraft()));
  const [axes, setAxes] = useState<OptionAxis[]>([]);
  const [remote, setRemote] = useState<Product[]>([]);
  const [local, setLocal] = useState<LocalImage[]>([]);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverMessages, setServerMessages] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(editing);
  const [error, setError] = useState<unknown>(null);
  const [saving, setSaving] = useState<"draft" | "publish" | null>(null);
  const [notice, setNotice] = useState("");
  const [tone, setTone] = useState<"ok" | "fail">("ok");
  const product = remote[0];
  const snapshot = JSON.stringify(draft);
  const dirty = snapshot !== baseline || local.length > 0;
  const skipBlock = useRef(false);
  const blocker = useBlocker(({ currentLocation, nextLocation }) => !skipBlock.current && dirty && currentLocation.pathname !== nextLocation.pathname);

  useEffect(() => {
    catalogApi.categories().then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    if (!editing || shopId == null || !productId) return;
    let active = true;
    setLoading(true);
    productApi.get(shopId, productId).then((data) => {
      if (!active) return;
      const next = fromProduct(data);
      setDraft(next);
      setBaseline(JSON.stringify(next));
      setAxes(axesFromSkus(next.skus));
      setRemote([data]);
    }).catch((caught) => {
      if (active) setError(caught);
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [editing, shopId, productId]);

  useEffect(() => {
    if (!dirty) return;
    const onLeave = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", onLeave);
    return () => window.removeEventListener("beforeunload", onLeave);
  }, [dirty]);

  const locale = i18n.language.startsWith("en") ? "en" : i18n.language.startsWith("zh") ? "zh" : "th";
  const errorText = (code: string, fallback?: string) => {
    const key = `catalog.errors.${code}`;
    const translated = t(key);
    return translated === key ? fallback || code : translated;
  };
  const fieldError = (key: string) => (errors[key] ? errorText(errors[key], serverMessages[key]) : undefined);
  const unplaced = Object.keys(errors).filter((key) => key !== "form" && !placedField.test(key));
  const setSku = (index: number, patch: Partial<ProductDraft["skus"][number]>) => {
    setDraft((current) => ({ ...current, skus: current.skus.map((sku, skuIndex) => skuIndex === index ? { ...sku, ...patch } : sku) }));
  };
  const toast = (text: string, next: "ok" | "fail" = "ok") => {
    setTone(next);
    setNotice(text);
  };

  const revertAuto = async (field: "name" | "description", locale: "en" | "zh") => {
    if (shopId == null || !productId) return;
    try {
      const saved = await productApi.useAutoTranslation(shopId, productId, { field, locale });
      setRemote([saved]);
      setDraft(fromProduct(saved));
      toast(t("catalog.form.useAutoTranslation"), "ok");
    } catch (caught) {
      toast(catalogMessage(t, caught), "fail");
    }
  };

  const save = async (publish: boolean) => {
    if (shopId == null) return;
    const imageCount = (product?.images.length || 0) + local.length;
    if (!(publish ? canPublish : canSaveDraft)) return;
    const nextErrors = validateDraft(draft, publish, imageCount);
    setErrors(nextErrors);
    setServerMessages({});
    if (Object.keys(nextErrors).length) return;
    setSaving(publish ? "publish" : "draft");
    let savedId = productId;
    try {
      const payload = toWrite(draft);
      let saved = editing && productId ? await productApi.update(shopId, productId, payload) : await productApi.create(shopId, payload);
      savedId = saved.id;
      if (!editing) {
        for (const image of local) saved = await productApi.addImage(shopId, saved.id, image.file);
        const primaryIndex = Math.max(0, local.findIndex((image) => image.primary));
        if (saved.images.length > 1 && primaryIndex > 0 && saved.images[primaryIndex]) {
          saved = await productApi.orderImages(shopId, saved.id, saved.images.map((image) => image.id), saved.images[primaryIndex].id);
        }
      }
      if (publish) await productApi.publish(shopId, saved.id);
      local.forEach((image) => URL.revokeObjectURL(image.preview));
      skipBlock.current = true;
      navigate(`/merchant/products/${saved.id}`, { replace: true });
    } catch (caught) {
      if (caught instanceof ApiError && caught.fieldErrors?.length) {
        // field-level 400 from the API: highlight each field with the server's code/message
        setErrors(Object.fromEntries(caught.fieldErrors.map((item) => [item.field, item.code])));
        setServerMessages(Object.fromEntries(caught.fieldErrors.map((item) => [item.field, item.message])));
      }
      toast(catalogMessage(t, caught), "fail");
      if (!editing && savedId && savedId !== productId) {
        skipBlock.current = true;
        navigate(`/merchant/products/${savedId}/edit`, { replace: true });
      }
    } finally {
      setSaving(null);
    }
  };

  const heading = editing ? t("catalog.form.editTitle") : t("catalog.form.newTitle");
  const skeleton = useMemo(() => Array.from({ length: 4 }, (_, index) => <span key={index} />), []);

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

  return (
    <MerchantShell notice={notice} tone={tone}>
      <header className="pm-heading">
        <div>
          <h1>{heading}</h1>
          <p>{t("catalog.lead")}</p>
        </div>
        <Link to="/merchant/products">{t("catalog.actions.back")}</Link>
      </header>
      {loading ? <div className="pm-skeleton-list">{skeleton}</div> : error ? (
        <div className="pm-state">
          <p>{catalogMessage(t, error)}</p>
          <button type="button" onClick={() => window.location.reload()}>{t("catalog.actions.retry")}</button>
        </div>
      ) : shopId == null ? null : (
        <form className="product-form-card" onSubmit={(event) => { event.preventDefault(); void save(false); }}>
          {errors.form ? <p className="form-field-error">{t(`catalog.errors.${errors.form}`)}</p> : null}
          {unplaced.length ? <p className="form-field-error">{unplaced.map((key) => errorText(errors[key], serverMessages[key])).join(" · ")}</p> : null}
          <h3>{t("catalog.form.general")}</h3>
          <p className="pm-lang-hint">{t("catalog.form.langsHint")}</p>
          {langFields.map((field) => (
            <div className="pm-lang-block" key={field.id}>
              <h4>
                {t(`catalog.form.lang${field.id === "th" ? "Th" : field.id === "en" ? "En" : "Zh"}`)}
                {!field.required ? <small>{t("catalog.form.langOptional")}</small> : null}
              </h4>
              <label>
                <span className="pm-lang-label">
                  {t("catalog.form.name")} {field.required ? <em>*</em> : null}
                  {field.locale && product ? (
                    <span className="pm-translation-badge">{translationBadge(t, product[field.nameMetaKey!])}</span>
                  ) : null}
                </span>
                <input
                  value={draft[field.nameKey]}
                  aria-invalid={Boolean(errors[field.nameKey])}
                  onChange={(event) => setDraft({ ...draft, [field.nameKey]: event.target.value })}
                  placeholder={t("catalog.form.namePh")}
                />
                {field.locale && product?.[field.nameMetaKey!]?.origin === "HUMAN" && productId ? (
                  <button type="button" className="pm-use-auto" onClick={() => void revertAuto("name", field.locale!)}>
                    {t("catalog.form.useAutoTranslation")}
                  </button>
                ) : null}
                <FieldError text={fieldError(field.nameKey)} />
              </label>
              <label>
                <span className="pm-lang-label">
                  {t("catalog.form.description")}
                  {field.locale && product ? (
                    <span className="pm-translation-badge">{translationBadge(t, product[field.descriptionMetaKey!])}</span>
                  ) : null}
                </span>
                <textarea
                  value={draft[field.descriptionKey]}
                  onChange={(event) => setDraft({ ...draft, [field.descriptionKey]: event.target.value })}
                  placeholder={t("catalog.form.descriptionPh")}
                />
                {field.locale && product?.[field.descriptionMetaKey!]?.origin === "HUMAN" && productId ? (
                  <button type="button" className="pm-use-auto" onClick={() => void revertAuto("description", field.locale!)}>
                    {t("catalog.form.useAutoTranslation")}
                  </button>
                ) : null}
              </label>
            </div>
          ))}
          <div className="merchant-two">
            <SearchableSelect
              label={t("catalog.form.category")}
              placeholder={t("catalog.form.categoryPh")}
              options={categories.map((category) => labelForCategory(category, locale))}
              value={categories.find((category) => String(category.id) === draft.categoryId) ? labelForCategory(categories.find((category) => String(category.id) === draft.categoryId)!, locale) : ""}
              onChange={(picked) => {
                const match = categories.find((category) => labelForCategory(category, locale) === picked);
                setDraft({ ...draft, categoryId: match ? String(match.id) : "" });
              }}
            />
            <label>
              {t("catalog.form.brand")}
              <input value={draft.brand} aria-invalid={Boolean(errors.brand)} placeholder={t("catalog.form.brandPh")} onChange={(event) => setDraft({ ...draft, brand: event.target.value })} />
              <FieldError text={fieldError("brand")} />
            </label>
          </div>
          <fieldset>
            <legend>{t("catalog.form.type")}</legend>
            <div className="sale-types">
              <button type="button" className={draft.productType === "SIMPLE" ? "active" : ""} onClick={() => setDraft({ ...draft, productType: "SIMPLE", skus: [draft.skus[0] || emptySku()] })}>
                <i>{draft.productType === "SIMPLE" ? <Check /> : null}</i>
                <Package />
                <span><b>{t("catalog.form.simple")}</b></span>
              </button>
              <button type="button" className={draft.productType === "VARIABLE" ? "active" : ""} onClick={() => setDraft({ ...draft, productType: "VARIABLE" })}>
                <i>{draft.productType === "VARIABLE" ? <Check /> : null}</i>
                <Boxes />
                <span><b>{t("catalog.form.variable")}</b></span>
              </button>
            </div>
          </fieldset>
          {errors.skus ? <FieldError text={fieldError("skus")} /> : null}
          <h3>{t("catalog.form.price")}</h3>
          <div className="merchant-two">
            <label>
              {t("catalog.form.price")}
              <input inputMode="decimal" value={draft.basePrice} aria-invalid={Boolean(errors.basePrice)} onChange={(event) => setDraft({ ...draft, basePrice: event.target.value })} />
              <FieldError text={fieldError("basePrice")} />
            </label>
            <label>
              {t("catalog.form.compare")}
              <input inputMode="decimal" value={draft.compareAtPrice} aria-invalid={Boolean(errors.compareAtPrice)} onChange={(event) => setDraft({ ...draft, compareAtPrice: event.target.value })} />
              <FieldError text={fieldError("compareAtPrice")} />
            </label>
            <label>
              {t("catalog.form.weight")}
              <input inputMode="numeric" value={draft.weightGrams} aria-invalid={Boolean(errors.weightGrams)} onChange={(event) => setDraft({ ...draft, weightGrams: event.target.value })} />
              <FieldError text={fieldError("weightGrams")} />
            </label>
          </div>
          <h3>{t("catalog.form.sku")}</h3>
          {draft.productType === "VARIABLE" && (
            <div className="pm-options">
              {axes.map((axis, index) => (
                <div key={`${axis.name}-${index}`}>
                  <label>
                    {t("catalog.form.optionName")}
                    <input value={axis.name} placeholder={t("catalog.form.optionName")} onChange={(event) => setAxes(axes.map((item, itemIndex) => itemIndex === index ? { ...item, name: event.target.value } : item))} />
                  </label>
                  <label>
                    {t("catalog.form.optionValue")}
                    <input placeholder={t("catalog.form.optionValue")} onKeyDown={(event) => {
                      if (event.key !== "Enter") return;
                      event.preventDefault();
                      const value = event.currentTarget.value.trim();
                      if (!value || axis.values.includes(value)) return;
                      setAxes(axes.map((item, itemIndex) => itemIndex === index ? { ...item, values: [...item.values, value] } : item));
                      event.currentTarget.value = "";
                    }} />
                  </label>
                  <div className="pm-chips">
                    {axis.values.map((value) => <button type="button" key={value} onClick={() => setAxes(axes.map((item, itemIndex) => itemIndex === index ? { ...item, values: item.values.filter((entry) => entry !== value) } : item))}>{value} ×</button>)}
                  </div>
                  <button type="button" className="product-text-button" onClick={() => setAxes(axes.filter((_, itemIndex) => itemIndex !== index))}><Trash2 />{t("catalog.form.remove")}</button>
                </div>
              ))}
              <div className="product-option-actions">
                <button type="button" onClick={() => setAxes([...axes, { name: "", values: [] }])}><Plus />{t("catalog.form.addOption")}</button>
                <button type="button" className="login-submit" onClick={() => {
                  const generated = combineSkus(axes, draft.skus, draft.basePrice);
                  if (generated.error) {
                    setErrors({ skus: "SKU_REQUIRED" });
                    return;
                  }
                  setDraft({ ...draft, skus: generated.skus });
                }}>{t("catalog.form.generate")}</button>
              </div>
            </div>
          )}
          <div className="pm-sku-table">
            {draft.skus.map((sku, index) => (
              <div key={sku.id || index}>
                <strong>{sku.optionLabel || t("catalog.form.simple")}</strong>
                <label>
                  {t("catalog.form.sku")} <em>*</em>
                  <input value={sku.skuCode} aria-invalid={Boolean(errors[`skus.${index}.skuCode`])} onChange={(event) => setSku(index, { skuCode: event.target.value })} />
                  <FieldError text={fieldError(`skus.${index}.skuCode`)} />
                </label>
                <label>
                  {t("catalog.form.price")} <em>*</em>
                  <input inputMode="decimal" value={sku.price} aria-invalid={Boolean(errors[`skus.${index}.price`])} onChange={(event) => setSku(index, { price: event.target.value })} />
                  <FieldError text={fieldError(`skus.${index}.price`)} />
                </label>
                <label>
                  {t("catalog.form.compare")}
                  <input inputMode="decimal" value={sku.compareAtPrice} aria-invalid={Boolean(errors[`skus.${index}.compareAtPrice`])} onChange={(event) => setSku(index, { compareAtPrice: event.target.value })} />
                  <FieldError text={fieldError(`skus.${index}.compareAtPrice`)} />
                </label>
                {sku.id ? <p className="product-stock-note">{t("catalog.form.currentStock")}: {sku.initialStock}</p> : (
                  <label>
                    {t("catalog.form.stock")}
                    <input inputMode="numeric" value={sku.initialStock} aria-invalid={Boolean(errors[`skus.${index}.initialStock`])} onChange={(event) => setSku(index, { initialStock: event.target.value })} />
                    <FieldError text={fieldError(`skus.${index}.initialStock`)} />
                  </label>
                )}
                <label>
                  {t("catalog.form.barcode")}
                  <input value={sku.barcode} aria-invalid={Boolean(errors[`skus.${index}.barcode`])} placeholder={t("catalog.form.barcodePh")} onChange={(event) => setSku(index, { barcode: event.target.value })} />
                  <FieldError text={fieldError(`skus.${index}.barcode`)} />
                </label>
                {draft.productType === "VARIABLE" && draft.skus.length > 1 ? <button type="button" className="product-text-button" onClick={() => setDraft({ ...draft, skus: draft.skus.filter((_, skuIndex) => skuIndex !== index) })}><Trash2 />{t("catalog.form.remove")}</button> : null}
              </div>
            ))}
          </div>
          <ProductImagesField
            shopId={shopId}
            productId={product?.id}
            remote={product?.images || []}
            local={local}
            onLocal={setLocal}
            onRemote={(saved) => setRemote([saved])}
            onError={(code) => toast(errorText(code), "fail")}
            disabled={!canUpdate}
          />
          {errors.images ? <FieldError text={fieldError("images")} /> : null}
          <div className="product-form-actions">
            <button type="submit" className="product-draft" disabled={Boolean(saving) || !canSaveDraft} title={deniedTitle(canSaveDraft)}>{saving === "draft" ? <LoaderCircle className="pm-spin" /> : null}{t("catalog.actions.saveDraft")}</button>
            <button type="button" className="login-submit" disabled={Boolean(saving) || !canPublish} title={deniedTitle(canPublish)} onClick={() => void save(true)}>{saving === "publish" ? <LoaderCircle className="pm-spin" /> : null}{t("catalog.actions.savePublish")}</button>
          </div>
        </form>
      )}
      {blocker.state === "blocked" && (
        <div className="pm-dialog" role="dialog" aria-modal="true">
          <section>
            <h2>{t("catalog.form.leaveTitle")}</h2>
            <p>{t("catalog.form.leaveBody")}</p>
            <div>
              <button type="button" onClick={() => blocker.reset()}>{t("catalog.form.leaveStay")}</button>
              <button type="button" className="primary" onClick={() => blocker.proceed()}>{t("catalog.form.leaveGo")}</button>
            </div>
          </section>
        </div>
      )}
    </MerchantShell>
  );
}
