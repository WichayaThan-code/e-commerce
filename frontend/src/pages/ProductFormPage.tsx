import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useBlocker, useNavigate, useParams } from "react-router-dom";
import { Boxes, Check, LoaderCircle, Package } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { SkeletonForm, LoadError } from "../components/feedback/Skeleton";
import ConfirmDialog from "../components/form/ConfirmDialog";
import { useDelayedLoading } from "../hooks/useDelayedLoading";
import { FieldError } from "../components/form/FormField";
import ToggleSwitch from "../components/form/ToggleSwitch";
import SearchableSelect from "../components/SearchableSelect";
import MerchantShell from "../components/merchant/MerchantShell";
import ProductImagesField, { type LocalImage } from "../components/merchant/ProductImagesField";
import VariantEditor from "../components/merchant/VariantEditor";
import ProductConfiguratorEditor from "../components/merchant/ProductConfiguratorEditor";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { labelForCategory } from "../services/categories";
import { catalogMessage } from "../services/catalogText";
import { ApiError, catalogApi, productApi, type Product, type ProductCategory, type TranslationMeta } from "../services/api";
import {
  axesFromGroups, axesFromSkus, emptyDraft, emptySku, englishStem, fromProduct, groupsFromAxes, optionKey, randomStem, toWrite, uncoveredOptionLabels, validateDraft, variantIssues,
  type VariantIssue,
  type OptionGroupDraft, type ProductDraft,
} from "./productFormModel";
import "../styles/form-fields.css";

const langFields = [
  { id: "th" as const, locale: null as null, nameKey: "nameTh" as const, descriptionKey: "descriptionTh" as const, required: true },
  { id: "en" as const, locale: "en" as const, nameKey: "nameEn" as const, descriptionKey: "descriptionEn" as const, required: false, nameMetaKey: "nameEnMeta" as const, descriptionMetaKey: "descriptionEnMeta" as const },
  { id: "zh" as const, locale: "zh" as const, nameKey: "nameZh" as const, descriptionKey: "descriptionZh" as const, required: false, nameMetaKey: "nameZhMeta" as const, descriptionMetaKey: "descriptionZhMeta" as const },
];

function isManual(meta?: TranslationMeta | null) {
  return meta?.origin === "HUMAN" || meta?.origin === "MANUAL";
}

function translationBadge(t: TFunction<"merchant">, meta?: TranslationMeta | null) {
  if (!meta) return null;
  if (meta.status === "PENDING") return t("catalog.form.translationPending");
  if (meta.status === "FAILED") return t("catalog.form.translationFailed");
  return isManual(meta) ? t("catalog.form.translationManual") : t("catalog.form.translationAuto");
}

export default function ProductFormPage() {
  const { productId } = useParams();
  const navigate = useNavigate();
  const { t, i18n } = useTranslation("merchant");
  const { shopId } = useActiveShop();
  const editing = Boolean(productId);
  const [draft, setDraft] = useState<ProductDraft>(emptyDraft);
  const [baseline, setBaseline] = useState(() => JSON.stringify(draft));
  const [groups, setGroups] = useState<OptionGroupDraft[]>([]);
  const [variantIssuesList, setVariantIssuesList] = useState<VariantIssue[]>([]);
  const [submitAttempt, setSubmitAttempt] = useState(0);
  const [stockConfirm, setStockConfirm] = useState<boolean | null>(null);
  const [remote, setRemote] = useState<Product[]>([]);
  const [local, setLocal] = useState<LocalImage[]>([]);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(editing);
  const [error, setError] = useState<unknown>(null);
  const [saving, setSaving] = useState<"draft" | "publish" | null>(null);
  const [notice, setNotice] = useState("");
  const [tone, setTone] = useState<"ok" | "fail">("ok");
  const product = remote[0];
  const snapshot = JSON.stringify(draft);
  const dirty = snapshot !== baseline || local.length > 0;
  const skipBlock = useRef(false);
  const fallbackStem = useRef("");
  if (!fallbackStem.current) fallbackStem.current = randomStem();
  const codeStem = englishStem(draft.nameEn) || fallbackStem.current;
  const blocker = useBlocker(({ currentLocation, nextLocation }) => !skipBlock.current && dirty && currentLocation.pathname !== nextLocation.pathname);
  const formLoading = editing && loading;
  const { showSkeleton, markHasData } = useDelayedLoading(formLoading, 150, productId);

  useEffect(() => {
    if (editing && !loading && !error) markHasData();
  }, [editing, loading, error, markHasData]);

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
      setGroups(groupsFromAxes(next.optionSchema.length ? next.optionSchema : axesFromSkus(next.skus)));
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
  const fieldError = (key: string) => (errors[key] ? t(`catalog.errors.${errors[key]}`) : undefined);
  const setSku = (index: number, patch: Partial<ProductDraft["skus"][number]>) => {
    setDraft((current) => ({ ...current, skus: current.skus.map((sku, skuIndex) => skuIndex === index ? { ...sku, ...patch } : sku) }));
  };
  const toast = (text: string, next: "ok" | "fail" = "ok") => {
    setTone(next);
    setNotice(text);
  };

  const applyTranslation = async (kind: "auto" | "retry", field: "name" | "description", locale: "en" | "zh") => {
    if (shopId == null || !productId) return;
    try {
      const saved = kind === "retry"
        ? await productApi.retryTranslation(shopId, productId, { field, locale })
        : await productApi.useAutoTranslation(shopId, productId, { field, locale });
      setRemote([saved]);
      setDraft(fromProduct(saved));
      toast(t(kind === "retry" ? "catalog.form.translationRetry" : "catalog.form.useAutoTranslation"), "ok");
    } catch (caught) {
      toast(catalogMessage(t, caught), "fail");
    }
  };

  const onVariantIssues = useCallback((next: VariantIssue[]) => {
    setVariantIssuesList((current) => {
      if (current.length === next.length && current.every((item, index) => item.index === next[index].index && item.field === next[index].field && item.code === next[index].code)) return current;
      return next;
    });
  }, []);
  const optionGaps = useMemo(
    () => (draft.productType === "VARIABLE" ? uncoveredOptionLabels(axesFromGroups(groups), draft.skus) : []),
    [draft.productType, draft.skus, groups],
  );
  const submitAttempted = submitAttempt > 0;
  useEffect(() => {
    if (!submitAttempted || draft.productType !== "VARIABLE") return;
    const taken = new Set(variantIssuesList.filter((issue) => issue.code === "SKU_CODE_TAKEN").map((issue) => draft.skus[issue.index]?.skuCode.trim() || "").filter(Boolean));
    const live = variantIssues({ productType: "VARIABLE", skus: draft.skus, optionSchema: axesFromGroups(groups) }, taken);
    const first = live.find((issue) => issue.index >= 0);
    const target = first ? document.getElementById(`variant-row-${first.index}`) : document.getElementById("variant-problems");
    target?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [submitAttempt]);

  const save = async (publish: boolean, confirmRemoveStocked = false) => {
    if (shopId == null) return;
    setSubmitAttempt((current) => current + 1);
    const imageCount = (product?.images.length || 0) + local.length;
    const nextErrors = validateDraft({ ...draft, optionSchema: axesFromGroups(groups) }, publish, imageCount);
    setErrors(nextErrors);
    const taken = new Set(variantIssuesList.filter((issue) => issue.code === "SKU_CODE_TAKEN").map((issue) => draft.skus[issue.index]?.skuCode.trim() || "").filter(Boolean));
    const liveIssues = draft.productType === "VARIABLE"
      ? variantIssues({ productType: "VARIABLE", skus: draft.skus, optionSchema: axesFromGroups(groups) }, taken)
      : [];
    const gaps = draft.productType === "VARIABLE" ? uncoveredOptionLabels(axesFromGroups(groups), draft.skus) : [];
    if (Object.keys(nextErrors).length || liveIssues.length > 0 || gaps.length > 0) return;
    setSaving(publish ? "publish" : "draft");
    setStockConfirm(null);
    let savedId = productId;
    try {
      const payload = toWrite({ ...draft, optionSchema: axesFromGroups(groups), confirmRemoveStocked });
      let saved = editing && productId ? await productApi.update(shopId, productId, payload) : await productApi.create(shopId, payload);
      savedId = saved.id;
      if (local.length) {
        const uploadedIds: string[] = [];
        for (const image of local) {
          saved = await productApi.addImage(shopId, saved.id, image.file);
          const known = new Set(uploadedIds);
          const added = saved.images.find((item) => !known.has(item.id));
          if (added) uploadedIds.push(added.id);
        }
        const primaryIndex = Math.max(0, local.findIndex((image) => image.primary));
        if (saved.images.length > 1 && primaryIndex > 0 && uploadedIds[primaryIndex]) {
          const rest = saved.images.map((image) => image.id).filter((id) => id !== uploadedIds[primaryIndex]);
          const primaryId = uploadedIds[primaryIndex];
          const insertAt = saved.images.findIndex((image) => image.id === primaryId);
          const ordered = saved.images.map((image) => image.id);
          if (insertAt > 0) {
            ordered.splice(insertAt, 1);
            ordered.splice(Math.min(primaryIndex, ordered.length), 0, primaryId);
          }
          saved = await productApi.orderImages(shopId, saved.id, ordered.length ? ordered : [primaryId, ...rest], primaryId);
        }
        if (draft.skus.some((sku) => sku.imageLocalKey && !sku.removed)) {
          const keyToId = new Map(local.map((image, index) => [image.key, uploadedIds[index]]));
          const boundSkus = draft.skus.map((sku) => {
            const match = saved.skus.find((row) => row.skuCode === sku.skuCode.trim());
            return {
              ...sku,
              id: sku.id || match?.id,
              imageId: sku.imageLocalKey ? keyToId.get(sku.imageLocalKey) || sku.imageId : sku.imageId,
              imageLocalKey: undefined,
            };
          });
          saved = await productApi.update(shopId, saved.id, toWrite({
            ...draft,
            skus: boundSkus,
            optionSchema: axesFromGroups(groups),
            confirmRemoveStocked,
          }));
        }
      }
      const adjustments = saved.skus.flatMap((sku) => {
        const source = draft.skus.find((item) => item.id === sku.id && !item.removed);
        if (!source || source.stockBaseline == null) return [];
        const desired = Number(source.initialStock || 0);
        const delta = desired - source.stockBaseline;
        if (!Number.isInteger(delta) || delta === 0) return [];
        return [{
          skuId: sku.id,
          entryType: delta > 0 ? "RECEIVE" as const : "ADJUST" as const,
          quantityDelta: delta,
          version: sku.version,
          reason: t("catalog.variants.stockReason"),
        }];
      });
      if (adjustments.length) {
        await productApi.adjustStockBulk(shopId, saved.id, { idempotencyKey: `form-${saved.id}-${Date.now()}`, items: adjustments });
      }
      if (publish) await productApi.publish(shopId, saved.id);
      local.forEach((image) => URL.revokeObjectURL(image.preview));
      skipBlock.current = true;
      navigate(`/merchant/products/${saved.id}`, { replace: true });
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === "SKU_HAS_STOCK" && !confirmRemoveStocked) {
        setStockConfirm(publish);
      }
      const mapped: Record<string, string> = {};
      if (caught instanceof ApiError) {
        const activeIndexes = draft.skus.flatMap((sku, index) => sku.removed ? [] : [index]);
        caught.fieldErrors.forEach((item) => {
          const match = /^skus\[(\d+)\]\.(.+)$/.exec(item.field);
          if (match) {
            const index = activeIndexes[Number(match[1])];
            if (index != null) mapped[`skus[${index}].${match[2]}`] = item.code;
          } else mapped[item.field] = item.code;
        });
      }
      if (Object.keys(mapped).length) setErrors(mapped);
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
      {showSkeleton && formLoading ? <SkeletonForm fields={8} /> : error && editing ? (
        <LoadError
          message={catalogMessage(t, error)}
          onRetry={() => window.location.reload()}
          retryLabel={t("catalog.actions.retry")}
        />
      ) : shopId == null ? null : (!editing || !loading) ? (
        <form className="product-form-card" onSubmit={(event) => { event.preventDefault(); void save(false); }}>
          {errors.form ? <p className="form-field-error">{t(`catalog.errors.${errors.form}`)}</p> : null}
          <h3>{t("catalog.form.general")}</h3>
          <p className="pm-lang-hint">{t("catalog.form.langsHint")}</p>
          <div className="pm-lang-block">
            <h4>{t("catalog.form.langTh")}</h4>
            <label>
              <span className="pm-lang-label">{t("catalog.form.name")} <em>*</em></span>
              <input
                value={draft.nameTh}
                aria-invalid={Boolean(errors.nameTh)}
                onChange={(event) => setDraft({ ...draft, nameTh: event.target.value })}
                placeholder={t("catalog.form.namePh")}
              />
              <FieldError text={fieldError("nameTh")} />
            </label>
            <label>
              <span className="pm-lang-label">{t("catalog.form.description")}</span>
              <textarea
                value={draft.descriptionTh}
                onChange={(event) => setDraft({ ...draft, descriptionTh: event.target.value })}
                placeholder={t("catalog.form.descriptionPh")}
              />
            </label>
          </div>
          <details className="pm-translation-fold">
            <summary>{t("catalog.form.translationSection")}</summary>
            <p className="pm-lang-hint">{t("catalog.form.translationSectionHint")}</p>
            {langFields.filter((field) => field.id !== "th").map((field) => (
            <div className="pm-lang-block" key={field.id}>
              <h4>
                {t(`catalog.form.lang${field.id === "en" ? "En" : "Zh"}`)}
                <small>{t("catalog.form.langOptional")}</small>
              </h4>
              <label>
                <span className="pm-lang-label">
                  {t("catalog.form.name")}
                  {product ? <span className="pm-translation-badge">{translationBadge(t, product[field.nameMetaKey!])}</span> : null}
                </span>
                <input
                  value={draft[field.nameKey]}
                  onChange={(event) => setDraft({ ...draft, [field.nameKey]: event.target.value })}
                  placeholder={t("catalog.form.namePh")}
                />
                {isManual(product?.[field.nameMetaKey!]) && productId ? (
                  <button type="button" className="pm-use-auto" onClick={() => void applyTranslation("auto", "name", field.locale!)}>
                    {t("catalog.form.useAutoTranslation")}
                  </button>
                ) : null}
                {product?.[field.nameMetaKey!]?.status === "FAILED" && productId ? (
                  <button type="button" className="pm-use-auto" onClick={() => void applyTranslation("retry", "name", field.locale!)}>
                    {t("catalog.form.translationRetry")}
                  </button>
                ) : null}
              </label>
              <label>
                <span className="pm-lang-label">
                  {t("catalog.form.description")}
                  {product ? <span className="pm-translation-badge">{translationBadge(t, product[field.descriptionMetaKey!])}</span> : null}
                </span>
                <textarea
                  value={draft[field.descriptionKey]}
                  onChange={(event) => setDraft({ ...draft, [field.descriptionKey]: event.target.value })}
                  placeholder={t("catalog.form.descriptionPh")}
                />
                {isManual(product?.[field.descriptionMetaKey!]) && productId ? (
                  <button type="button" className="pm-use-auto" onClick={() => void applyTranslation("auto", "description", field.locale!)}>
                    {t("catalog.form.useAutoTranslation")}
                  </button>
                ) : null}
                {product?.[field.descriptionMetaKey!]?.status === "FAILED" && productId ? (
                  <button type="button" className="pm-use-auto" onClick={() => void applyTranslation("retry", "description", field.locale!)}>
                    {t("catalog.form.translationRetry")}
                  </button>
                ) : null}
              </label>
            </div>
            ))}
          </details>
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
              <span className="pm-lang-label">{t("catalog.form.brand")}</span>
              <input value={draft.brand} placeholder={t("catalog.form.brandPh")} onChange={(event) => setDraft({ ...draft, brand: event.target.value })} />
            </label>
          </div>
          <fieldset>
            <legend>{t("catalog.form.type")}</legend>
            <div className="sale-types">
              <button type="button" className={draft.productType === "SIMPLE" ? "active" : ""} onClick={() => { if (draft.productType !== "SIMPLE") { setSubmitAttempt(0); setVariantIssuesList([]); } setDraft({ ...draft, productType: "SIMPLE", optionSchema: [], skus: [(draft.skus.find((sku) => !sku.removed) || draft.skus[0] || emptySku())] }); }}>
                <i>{draft.productType === "SIMPLE" ? <Check /> : null}</i>
                <Package />
                <span><b>{t("catalog.form.simple")}</b></span>
              </button>
              <button type="button" className={draft.productType === "VARIABLE" ? "active" : ""} onClick={() => {
                if (draft.productType !== "VARIABLE") { setSubmitAttempt(0); setVariantIssuesList([]); }
                const seeded = groups.length ? groups : [{ key: optionKey(), name: "", values: [] }];
                setGroups(seeded);
                setDraft({ ...draft, productType: "VARIABLE", optionSchema: axesFromGroups(seeded) });
              }}>
                <i>{draft.productType === "VARIABLE" ? <Check /> : null}</i>
                <Boxes />
                <span><b>{t("catalog.form.variable")}</b></span>
              </button>
            </div>
          </fieldset>
          {errors.skus ? <FieldError text={t(`catalog.errors.${errors.skus}`)} /> : null}
          <h3>{t("catalog.form.price")}</h3>
          <div className="merchant-two">
            <label>
              <span className="pm-lang-label">{t("catalog.form.price")} <em>*</em></span>
              <input className="num" inputMode="decimal" value={draft.basePrice} aria-invalid={Boolean(errors.basePrice)} onChange={(event) => setDraft({ ...draft, basePrice: event.target.value })} />
              <FieldError text={fieldError("basePrice")} />
            </label>
            <label>
              <span className="pm-lang-label">{t("catalog.form.compare")}</span>
              <input className="num" inputMode="decimal" value={draft.compareAtPrice} aria-invalid={Boolean(errors.compareAtPrice)} onChange={(event) => setDraft({ ...draft, compareAtPrice: event.target.value })} />
              <FieldError text={fieldError("compareAtPrice")} />
            </label>
            <label>
              <span className="pm-lang-label">{t("catalog.form.weight")}</span>
              <input className="num" inputMode="numeric" value={draft.weightGrams} aria-invalid={Boolean(errors.weightGrams)} onChange={(event) => setDraft({ ...draft, weightGrams: event.target.value })} />
              <FieldError text={fieldError("weightGrams")} />
            </label>
          </div>
          <h3>{t("catalog.form.sku")}</h3>
          {draft.productType === "VARIABLE" ? (
            <>
              <VariantEditor
                shopId={shopId}
                productId={product?.id}
                codeStem={codeStem}
                groups={groups}
                skus={draft.skus}
                price={draft.basePrice}
                images={product?.images || []}
                localImages={local}
                serverErrors={errors}
                submitAttempted={submitAttempted}
                onIssues={onVariantIssues}
                onChange={(nextGroups, nextSkus) => {
                  setGroups(nextGroups);
                  setDraft((current) => ({ ...current, skus: nextSkus, optionSchema: axesFromGroups(nextGroups) }));
                }}
              />
              {Object.entries(errors).filter(([field]) => /^(optionGroups|optionSchema)(\.|\[)/.test(field) && !/^(optionGroups|optionSchema)\[\d+\]\.(name|values\[\d+\]\.value)$/.test(field)).map(([field, code]) => (
                <FieldError key={field} text={t(`catalog.errors.${code}`, { defaultValue: code })} />
              ))}
              {submitAttempted && optionGaps.length > 0 ? <FieldError text={t("catalog.variants.optionUncovered")} /> : null}
            </>
          ) : (
          <div className="pm-detail">
            <div className="pm-table-scroll">
              <table>
                <thead>
                  <tr>
                    <th><span className="pm-lang-label">{t("catalog.form.sku")} <em>*</em></span></th>
                    <th><span className="pm-lang-label">{t("catalog.form.price")} <em>*</em></span></th>
                    <th><span className="pm-lang-label">{t("catalog.form.compare")}</span></th>
                    <th><span className="pm-lang-label">{t("catalog.form.stock")}</span></th>
                    <th><span className="pm-lang-label">{t("catalog.form.safetyStock")}</span></th>
                    <th><span className="pm-lang-label">{t("catalog.variants.active")}</span></th>
                  </tr>
                </thead>
                <tbody>
                  {draft.skus.map((sku, index) => {
                    const rowName = sku.optionLabel || t("catalog.form.simple");
                    const named = (column: string) => `${column} ${rowName}`;
                    return (
                      <tr key={sku.clientKey}>
                        <td>
                          <input className="num" aria-label={named(t("catalog.form.sku"))} value={sku.skuCode} aria-invalid={Boolean(errors[`skus[${index}].skuCode`])} onChange={(event) => setSku(index, { skuCode: event.target.value })} />
                          <FieldError text={fieldError(`skus[${index}].skuCode`)} />
                        </td>
                        <td>
                          <input className="num" inputMode="decimal" aria-label={named(t("catalog.form.price"))} value={sku.price} aria-invalid={Boolean(errors[`skus[${index}].price`])} onChange={(event) => setSku(index, { price: event.target.value })} />
                          <FieldError text={fieldError(`skus[${index}].price`)} />
                        </td>
                        <td>
                          <input className="num" inputMode="decimal" aria-label={named(t("catalog.form.compare"))} value={sku.compareAtPrice} aria-invalid={Boolean(errors[`skus[${index}].compareAtPrice`])} onChange={(event) => setSku(index, { compareAtPrice: event.target.value })} />
                          <FieldError text={fieldError(`skus[${index}].compareAtPrice`)} />
                        </td>
                        <td>
                          {sku.id ? <p className="product-stock-note">{t("catalog.form.currentStock")}: {sku.initialStock}</p> : (
                            <>
                              <input className="num" inputMode="numeric" aria-label={named(t("catalog.form.stock"))} value={sku.initialStock} aria-invalid={Boolean(errors[`skus[${index}].initialStock`])} onChange={(event) => setSku(index, { initialStock: event.target.value })} />
                              <FieldError text={fieldError(`skus[${index}].initialStock`)} />
                            </>
                          )}
                        </td>
                        <td>
                          <input className="num" inputMode="numeric" aria-label={named(t("catalog.form.safetyStock"))} value={sku.safetyStock} aria-invalid={Boolean(errors[`skus[${index}].safetyStock`])} onChange={(event) => setSku(index, { safetyStock: event.target.value })} />
                          <FieldError text={fieldError(`skus[${index}].safetyStock`)} />
                        </td>
                        <td>
                          <ToggleSwitch
                            checked={sku.active}
                            label={named(t("catalog.variants.active"))}
                            onText={t("catalog.toggle.on")}
                            offText={t("catalog.toggle.off")}
                            onChange={(checked) => setSku(index, { active: checked })}
                          />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
          )}
          <ProductImagesField
            shopId={shopId}
            productId={product?.id}
            remote={product?.images || []}
            local={local}
            onLocal={setLocal}
            onRemote={(saved) => setRemote([saved])}
            onError={(code) => toast(t(`catalog.errors.${code}`), "fail")}
          />
          {errors.images ? <FieldError text={t(`catalog.errors.${errors.images}`)} /> : null}
          <ProductConfiguratorEditor shopId={shopId} productId={product?.id} />
          <div className="pm-actions">
            <button type="submit" disabled={Boolean(saving)}>{saving === "draft" ? <LoaderCircle className="pm-spin" /> : null}{t("catalog.actions.saveDraft")}</button>
            <button type="button" className="primary" disabled={Boolean(saving)} onClick={() => void save(true)}>{saving === "publish" ? <LoaderCircle className="pm-spin" /> : null}{t("catalog.actions.savePublish")}</button>
          </div>
        </form>
      ) : null}
      {stockConfirm != null && (
        <ConfirmDialog
          title={t("catalog.variants.confirmStockTitle")}
          message={t("catalog.variants.confirmStockBody")}
          cancelLabel={t("catalog.actions.cancel")}
          confirmLabel={t("catalog.actions.confirm")}
          danger
          onCancel={() => setStockConfirm(null)}
          onConfirm={() => void save(stockConfirm, true)}
        />
      )}
      {blocker.state === "blocked" && (
        <ConfirmDialog
          title={t("catalog.form.leaveTitle")}
          message={t("catalog.form.leaveBody")}
          cancelLabel={t("catalog.form.leaveStay")}
          confirmLabel={t("catalog.form.leaveGo")}
          onCancel={() => blocker.reset()}
          onConfirm={() => blocker.proceed()}
        />
      )}
    </MerchantShell>
  );
}
