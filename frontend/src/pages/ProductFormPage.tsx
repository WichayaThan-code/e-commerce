import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useBlocker, useNavigate, useParams } from "react-router-dom";
import { Boxes, Check, LoaderCircle, Package } from "lucide-react";
import { useTranslation } from "react-i18next";
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
import ProductDescriptionEditor from "../components/merchant/ProductDescriptionEditor";
import ProductTranslationPanel from "../components/merchant/ProductTranslationPanel";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { labelForCategory } from "../services/categories";
import { catalogMessage } from "../services/catalogText";
import { ApiError, catalogApi, merchantPlanUsageApi, productApi, type MerchantPlanUsage, type Product, type ProductCategory } from "../services/api";
import {
  applyDefaultPricing, axesFromGroups, axesFromSkus, emptyDraft, emptySku, englishStem, fromProduct, groupsFromAxes, optionKey, randomStem, toWrite, uncoveredOptionLabels, validateDraft, variantIssues,
  type VariantIssue,
  type OptionGroupDraft, type ProductDraft,
} from "./productFormModel";
import "../styles/form-fields.css";
import "../styles/product-management.css";

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
  const [planUsage, setPlanUsage] = useState<MerchantPlanUsage | null>(null);
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
    if (shopId == null) {
      setPlanUsage(null);
      return;
    }
    let cancelled = false;
    merchantPlanUsageApi.get(shopId)
      .then((row) => { if (!cancelled) setPlanUsage(row); })
      .catch(() => { if (!cancelled) setPlanUsage(null); });
    return () => { cancelled = true; };
  }, [shopId]);

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

  const productMetric = planUsage?.metrics.find((metric) => metric.code === "PRODUCTS");
  const publishLimit = productMetric?.limit.kind === "FINITE" ? productMetric.limit.value : null;
  const alreadyPublished = product?.status === "PUBLISHED";
  const publishQuotaBlocked = !alreadyPublished
    && publishLimit != null
    && (productMetric?.status === "FULL" || productMetric?.status === "OVER" || (productMetric?.used ?? 0) >= publishLimit);
  const publishQuotaTitle = publishQuotaBlocked ? t("catalog.publishQuota.blocked") : undefined;

  const save = async (publish: boolean, confirmRemoveStocked = false) => {
    if (shopId == null) return;
    if (publish && publishQuotaBlocked) {
      toast(t("catalog.publishQuota.blocked"), "fail");
      return;
    }
    setSubmitAttempt((current) => current + 1);
    const synced = {
      ...draft,
      skus: draft.skus.map((sku) => {
        if (sku.removed) return sku;
        if (draft.productType === "SIMPLE") {
          return { ...sku, price: draft.basePrice, compareAtPrice: draft.compareAtPrice };
        }
        return {
          ...sku,
          price: sku.price.trim() ? sku.price : draft.basePrice,
          compareAtPrice: sku.compareAtPrice.trim() ? sku.compareAtPrice : draft.compareAtPrice,
        };
      }),
    };
    if (JSON.stringify(synced.skus) !== JSON.stringify(draft.skus)) setDraft(synced);
    const imageCount = (product?.images.length || 0) + local.length;
    const nextErrors = validateDraft({ ...synced, optionSchema: axesFromGroups(groups) }, publish, imageCount);
    setErrors(nextErrors);
    const taken = new Set(variantIssuesList.filter((issue) => issue.code === "SKU_CODE_TAKEN").map((issue) => synced.skus[issue.index]?.skuCode.trim() || "").filter(Boolean));
    const liveIssues = synced.productType === "VARIABLE"
      ? variantIssues({ productType: "VARIABLE", skus: synced.skus, optionSchema: axesFromGroups(groups) }, taken)
      : [];
    const gaps = synced.productType === "VARIABLE" ? uncoveredOptionLabels(axesFromGroups(groups), synced.skus) : [];
    if (Object.keys(nextErrors).length || liveIssues.length > 0 || gaps.length > 0) return;
    setSaving(publish ? "publish" : "draft");
    setStockConfirm(null);
    let savedId = productId;
    try {
      const payload = toWrite({ ...synced, optionSchema: axesFromGroups(groups), confirmRemoveStocked });
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
        if (synced.skus.some((sku) => sku.imageLocalKey && !sku.removed)) {
          const keyToId = new Map(local.map((image, index) => [image.key, uploadedIds[index]]));
          const boundSkus = synced.skus.map((sku) => {
            const match = saved.skus.find((row) => row.skuCode === sku.skuCode.trim());
            return {
              ...sku,
              id: sku.id || match?.id,
              imageId: sku.imageLocalKey ? keyToId.get(sku.imageLocalKey) || sku.imageId : sku.imageId,
              imageLocalKey: undefined,
            };
          });
          saved = await productApi.update(shopId, saved.id, toWrite({
            ...synced,
            skus: boundSkus,
            optionSchema: axesFromGroups(groups),
            confirmRemoveStocked,
          }));
        }
      }
      const adjustments = saved.skus.flatMap((sku) => {
        const source = synced.skus.find((item) => item.id === sku.id && !item.removed);
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
        const activeIndexes = synced.skus.flatMap((sku, index) => sku.removed ? [] : [index]);
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
            <div className="pm-desc-field">
              <span className="pm-lang-label">{t("catalog.form.description")}</span>
              <ProductDescriptionEditor
                shopId={shopId}
                value={draft.descriptionTh}
                onChange={(html) => setDraft({ ...draft, descriptionTh: html })}
                placeholder={t("catalog.form.descriptionPh")}
              />
            </div>
          </div>
          <ProductTranslationPanel
            shopId={shopId}
            productId={productId}
            product={product}
            nameTh={draft.nameTh}
            descriptionTh={draft.descriptionTh}
            nameEn={draft.nameEn}
            nameZh={draft.nameZh}
            descriptionEn={draft.descriptionEn}
            descriptionZh={draft.descriptionZh}
            onChange={(patch) => setDraft((current) => ({ ...current, ...patch }))}
            onUseAuto={(field, locale) => void applyTranslation("auto", field, locale)}
            onRetry={(field, locale) => void applyTranslation("retry", field, locale)}
          />
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
              <button type="button" className={draft.productType === "SIMPLE" ? "active" : ""} onClick={() => {
                if (draft.productType !== "SIMPLE") { setSubmitAttempt(0); setVariantIssuesList([]); }
                const base = draft.skus.find((sku) => !sku.removed) || draft.skus[0] || emptySku();
                setDraft({
                  ...draft,
                  productType: "SIMPLE",
                  optionSchema: [],
                  skus: [{ ...base, price: draft.basePrice, compareAtPrice: draft.compareAtPrice, removed: false }],
                });
              }}>
                <i>{draft.productType === "SIMPLE" ? <Check /> : null}</i>
                <Package />
                <span><b>{t("catalog.form.simple")}</b></span>
              </button>
              <button type="button" className={draft.productType === "VARIABLE" ? "active" : ""} onClick={() => {
                if (draft.productType !== "VARIABLE") { setSubmitAttempt(0); setVariantIssuesList([]); }
                const seeded = groups.length ? groups : [{ key: optionKey(), name: "", values: [] }];
                setGroups(seeded);
                setDraft({
                  ...draft,
                  productType: "VARIABLE",
                  optionSchema: axesFromGroups(seeded),
                  skus: applyDefaultPricing(draft.skus, {
                    price: draft.basePrice,
                    compareAtPrice: draft.compareAtPrice,
                  }),
                });
              }}>
                <i>{draft.productType === "VARIABLE" ? <Check /> : null}</i>
                <Boxes />
                <span><b>{t("catalog.form.variable")}</b></span>
              </button>
            </div>
          </fieldset>
          {errors.skus ? <FieldError text={t(`catalog.errors.${errors.skus}`)} /> : null}
          <h3>{t("catalog.form.price")}</h3>
          {draft.productType === "VARIABLE" ? (
            <p className="pm-lang-hint">{t("catalog.form.priceDefaultHint")}</p>
          ) : null}
          <div className="merchant-two">
            <label>
              <span className="pm-lang-label">{t("catalog.form.price")} <em>*</em></span>
              <input
                className="num"
                inputMode="decimal"
                value={draft.basePrice}
                aria-invalid={Boolean(errors.basePrice) || Boolean(errors["skus[0].price"])}
                onChange={(event) => {
                  const basePrice = event.target.value;
                  setDraft((current) => ({
                    ...current,
                    basePrice,
                    skus: current.productType === "SIMPLE"
                      ? current.skus.map((sku) => (sku.removed ? sku : { ...sku, price: basePrice }))
                      : applyDefaultPricing(current.skus, { price: basePrice }, { price: current.basePrice }),
                  }));
                }}
              />
              <FieldError text={fieldError("basePrice") || fieldError("skus[0].price")} />
            </label>
            <label>
              <span className="pm-lang-label">{t("catalog.form.compare")}</span>
              <input
                className="num"
                inputMode="decimal"
                value={draft.compareAtPrice}
                aria-invalid={Boolean(errors.compareAtPrice) || Boolean(errors["skus[0].compareAtPrice"])}
                onChange={(event) => {
                  const compareAtPrice = event.target.value;
                  setDraft((current) => ({
                    ...current,
                    compareAtPrice,
                    skus: current.productType === "SIMPLE"
                      ? current.skus.map((sku) => (sku.removed ? sku : { ...sku, compareAtPrice }))
                      : applyDefaultPricing(current.skus, { compareAtPrice }, { compareAtPrice: current.compareAtPrice }),
                  }));
                }}
              />
              <FieldError text={fieldError("compareAtPrice") || fieldError("skus[0].compareAtPrice")} />
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
                compareAtPrice={draft.compareAtPrice}
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
                          {sku.id ? <p className="product-stock-note">{t("catalog.form.currentStock")}: {sku.initialStock}</p> : (
                            <>
                              <input className="num" inputMode="numeric" aria-label={named(t("catalog.form.stock"))} value={sku.initialStock} aria-invalid={Boolean(errors[`skus[${index}].initialStock`])} onChange={(event) => setSku(index, { initialStock: event.target.value })} />
                              <FieldError text={fieldError(`skus[${index}].initialStock`)} />
                            </>
                          )}
                        </td>
                        <td>
                          <input
                            className="num"
                            inputMode="numeric"
                            aria-label={named(t("catalog.form.safetyStock"))}
                            value={sku.safetyStock}
                            aria-invalid={Boolean(errors[`skus[${index}].safetyStock`])}
                            onChange={(event) => setSku(index, { safetyStock: event.target.value })}
                            onBlur={() => { if (!sku.safetyStock.trim()) setSku(index, { safetyStock: "0" }); }}
                          />
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
          {publishQuotaBlocked ? (
            <p className="form-alert" role="alert">
              {t("catalog.publishQuota.fullAlert")}{" "}
              <Link to="/merchant/settings?tab=plan">{t("catalog.publishQuota.link")}</Link>
            </p>
          ) : null}
          <div className="pm-actions">
            <button type="submit" disabled={Boolean(saving)}>{saving === "draft" ? <LoaderCircle className="pm-spin" /> : null}{t("catalog.actions.saveDraft")}</button>
            <button type="button" className="primary" disabled={Boolean(saving) || publishQuotaBlocked} title={publishQuotaTitle} onClick={() => void save(true)}>{saving === "publish" ? <LoaderCircle className="pm-spin" /> : null}{t("catalog.actions.savePublish")}</button>
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
