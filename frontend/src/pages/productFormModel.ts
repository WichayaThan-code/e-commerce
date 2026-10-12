import type { Product, ProductWrite, SkuWrite } from "../services/api";

export type SkuDraft = {
  clientKey: string;
  id?: string;
  skuCode: string;
  barcode: string;
  optionLabel: string;
  optionValues: Record<string, string>;
  price: string;
  compareAtPrice: string;
  costPrice: string;
  weightGrams: string;
  initialStock: string;
  active: boolean;
  imageId?: string;
  imageLocalKey?: string;
  removed?: boolean;
  stockBaseline?: number;
  reservedQty?: number;
};

export type ProductDraft = {
  nameTh: string;
  nameEn: string;
  nameZh: string;
  descriptionTh: string;
  descriptionEn: string;
  descriptionZh: string;
  categoryId: string;
  brand: string;
  productType: "SIMPLE" | "VARIABLE";
  basePrice: string;
  compareAtPrice: string;
  weightGrams: string;
  optionSchema: OptionAxis[];
  confirmRemoveStocked?: boolean;
  skus: SkuDraft[];
};

export type OptionAxis = { name: string; values: string[] };
export type OptionValueDraft = { key: string; label: string };
export type OptionGroupDraft = { key: string; name: string; values: OptionValueDraft[] };

export function optionKey() {
  return `opt-${Math.random().toString(36).slice(2, 10)}`;
}

export function emptySku(partial: Partial<SkuDraft> = {}): SkuDraft {
  return {
    skuCode: "",
    barcode: "",
    optionLabel: "",
    optionValues: {},
    price: "",
    compareAtPrice: "",
    costPrice: "",
    weightGrams: "",
    initialStock: "0",
    active: true,
    ...partial,
    clientKey: partial.clientKey || optionKey(),
  };
}

export function emptyDraft(): ProductDraft {
  return {
    nameTh: "",
    nameEn: "",
    nameZh: "",
    descriptionTh: "",
    descriptionEn: "",
    descriptionZh: "",
    categoryId: "",
    brand: "",
    productType: "SIMPLE",
    basePrice: "",
    compareAtPrice: "",
    weightGrams: "",
    optionSchema: [],
    skus: [emptySku()],
  };
}

export function fromProduct(product: Product): ProductDraft {
  return {
    nameTh: product.nameTh || "",
    nameEn: product.nameEn || "",
    nameZh: product.nameZh || "",
    descriptionTh: product.descriptionTh || "",
    descriptionEn: product.descriptionEn || "",
    descriptionZh: product.descriptionZh || "",
    categoryId: product.categoryId == null ? "" : String(product.categoryId),
    brand: product.brand || "",
    productType: product.productType,
    basePrice: product.basePrice == null ? "" : String(product.basePrice),
    compareAtPrice: product.compareAtPrice == null ? "" : String(product.compareAtPrice),
    weightGrams: product.weightGrams == null ? "" : String(product.weightGrams),
    optionSchema: product.optionSchema?.length
      ? product.optionSchema.map((group) => ({ name: group.name, values: [...group.values] }))
      : axesFromSkus(product.skus.map((sku) => emptySku({ optionValues: sku.optionValues || {} }))),
    skus: product.skus.length
      ? product.skus.map((sku) => emptySku({
          clientKey: sku.id,
          id: sku.id,
          skuCode: sku.skuCode,
          barcode: sku.barcode || "",
          optionLabel: sku.optionLabel || "",
          optionValues: sku.optionValues || {},
          price: String(sku.price ?? ""),
          compareAtPrice: sku.compareAtPrice == null ? "" : String(sku.compareAtPrice),
          costPrice: sku.costPrice == null ? "" : String(sku.costPrice),
          weightGrams: sku.weightGrams == null ? "" : String(sku.weightGrams),
          initialStock: String(sku.onHand ?? 0),
          active: sku.active,
          imageId: sku.imageId || undefined,
          stockBaseline: sku.onHand ?? 0,
          reservedQty: sku.reservedQty ?? 0,
        }))
      : [emptySku()],
  };
}

export function groupsFromAxes(axes: OptionAxis[]): OptionGroupDraft[] {
  return axes.map((axis) => ({
    key: optionKey(),
    name: axis.name,
    values: axis.values.map((label) => ({ key: optionKey(), label })),
  }));
}

export function axesFromGroups(groups: OptionGroupDraft[]): OptionAxis[] {
  return groups
    .map((group) => ({
      name: group.name.trim(),
      values: group.values.map((value) => value.label.trim()).filter(Boolean),
    }))
    .filter((group) => group.name && group.values.length);
}

export function axesFromSkus(skus: SkuDraft[]): OptionAxis[] {
  const names: string[] = [];
  skus.forEach((sku) => {
    Object.keys(sku.optionValues || {}).forEach((name) => {
      if (!names.includes(name)) names.push(name);
    });
  });
  return names.map((name) => ({
    name,
    values: [...new Set(skus.map((sku) => sku.optionValues?.[name]).filter((value): value is string => Boolean(value)))],
  }));
}

const GROUP_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

export function latinToken(value: string) {
  return value.toUpperCase().replace(/[^A-Z0-9]+/g, "");
}

export function optionCodeSegment(value: string, groupIndex: number, valueIndex: number) {
  const latin = latinToken(value);
  if (latin) return latin;
  const letter = GROUP_LETTERS[groupIndex] ?? "Z";
  return `${letter}${valueIndex + 1}`;
}

export function skuAxesFromGroups(groups: OptionGroupDraft[]): OptionAxis[] {
  return groups
    .map((group) => ({
      name: group.name.trim(),
      values: group.values.map((value) => value.label.trim()).filter(Boolean),
    }))
    .filter((group) => group.name && group.values.length)
    .slice(0, 3);
}

export function segmentsForOptionValues(groups: OptionAxis[], optionValues: Record<string, string>) {
  return groups.map((group, groupIndex) => {
    const label = optionValues[group.name] ?? "";
    const valueIndex = group.values.indexOf(label);
    return optionCodeSegment(label, groupIndex, valueIndex < 0 ? 0 : valueIndex);
  });
}

export function composeSkuCode(prefix: string, segments: string[]) {
  return [latinToken(prefix), ...segments].filter(Boolean).join("-").slice(0, 64);
}

export function dedupeSkuCode(base: string, used: Set<string>) {
  const root = (base || "SKU").slice(0, 64);
  if (!used.has(root)) {
    used.add(root);
    return root;
  }
  for (let n = 2; n < 10000; n += 1) {
    const suffix = `-${n}`;
    const next = `${root.slice(0, Math.max(1, 64 - suffix.length))}${suffix}`;
    if (!used.has(next)) {
      used.add(next);
      return next;
    }
  }
  return root;
}

export function applyGeneratedSkuCodes(
  skus: SkuDraft[],
  groups: OptionAxis[],
  prefix: string,
  targets: Set<number>,
  reserved: string[] = [],
) {
  const used = new Set(reserved.map((code) => code.trim()).filter(Boolean));
  skus.forEach((sku, index) => {
    if (targets.has(index)) return;
    const code = sku.skuCode.trim();
    if (code) used.add(code);
  });
  return skus.map((sku, index) => {
    if (!targets.has(index)) return sku;
    const base = composeSkuCode(prefix, segmentsForOptionValues(groups, sku.optionValues));
    return { ...sku, skuCode: dedupeSkuCode(base, used) };
  });
}

export function uncoveredOptionLabels(schema: OptionAxis[], skus: SkuDraft[]) {
  const active = skus.filter((sku) => !sku.removed);
  const missing: string[] = [];
  schema.forEach((group) => {
    group.values.forEach((value) => {
      if (!active.some((sku) => sku.optionValues?.[group.name] === value)) missing.push(`${group.name}: ${value}`);
    });
  });
  return missing;
}

function comboKey(values: Record<string, string>, order: string[]) {
  return order.map((name) => `${name}=${values[name] ?? ""}`).join("|");
}

export function reconcileVariants(
  previous: OptionGroupDraft[],
  next: OptionGroupDraft[],
  skus: SkuDraft[],
  price: string,
): { skus: SkuDraft[]; error?: "TOO_MANY" } {
  const usable = next
    .map((group) => ({
      ...group,
      name: group.name.trim(),
      values: group.values.map((value) => ({ ...value, label: value.label.trim() })).filter((value) => value.label),
    }))
    .filter((group) => group.name && group.values.length)
    .slice(0, 3);
  if (!usable.length) return { skus };
  const prevByKey = new Map(previous.map((group) => [group.key, group]));
  const renameGroup = new Map<string, string>();
  const renameValue = new Map<string, Map<string, string>>();
  usable.forEach((group) => {
    const prev = prevByKey.get(group.key);
    if (!prev) return;
    const oldName = prev.name.trim();
    if (oldName && oldName !== group.name) renameGroup.set(oldName, group.name);
    const map = new Map<string, string>();
    const oldLabels = new Map(prev.values.map((value) => [value.key, value.label.trim()]));
    group.values.forEach((value) => {
      const oldLabel = oldLabels.get(value.key);
      if (oldLabel && oldLabel !== value.label) map.set(oldLabel, value.label);
    });
    if (map.size) renameValue.set(group.name, map);
  });
  const translate = (values: Record<string, string>) => {
    const out: Record<string, string> = {};
    Object.entries(values || {}).forEach(([name, label]) => {
      const nextName = renameGroup.get(name) || name;
      out[nextName] = renameValue.get(nextName)?.get(label) || label;
    });
    return out;
  };
  const order = usable.map((group) => group.name);
  const combos = usable.reduce<Record<string, string>[]>((rows, group) => {
    if (!rows.length) return group.values.map((value) => ({ [group.name]: value.label }));
    return rows.flatMap((row) => group.values.map((value) => ({ ...row, [group.name]: value.label })));
  }, []);
  if (combos.length > 100) return { skus, error: "TOO_MANY" };
  const pool = skus.map((sku) => ({ ...sku, optionValues: translate(sku.optionValues) }));
  const used = new Set<number>();
  const fresh = new Set<number>();
  const active: SkuDraft[] = [];
  combos.forEach((optionValues) => {
    const key = comboKey(optionValues, order);
    const found = pool.findIndex((sku, skuIndex) => !used.has(skuIndex) && comboKey(sku.optionValues, order) === key);
    const optionLabel = order.map((name) => optionValues[name]).join(" / ");
    if (found >= 0) {
      used.add(found);
      active.push({ ...pool[found], optionValues, optionLabel, removed: false });
      return;
    }
    fresh.add(active.length);
    active.push(emptySku({ optionLabel, optionValues, price, skuCode: "" }));
  });
  const removed = pool
    .filter((sku, index) => !used.has(index) && (sku.id || sku.skuCode || sku.removed))
    .map((sku) => ({ ...sku, removed: true }));
  const axes = usable.map((group) => ({ name: group.name, values: group.values.map((value) => value.label) }));
  const coded = applyGeneratedSkuCodes(active, axes, "", fresh, removed.map((sku) => sku.skuCode));
  return { skus: [...coded, ...removed] };
}

export function combineSkus(axes: OptionAxis[], current: SkuDraft[], price: string): { skus: SkuDraft[]; error?: "TOO_MANY" } {
  const previous = groupsFromAxes(axes);
  return reconcileVariants(previous, previous, current, price);
}

function money(value: string) {
  if (!value.trim()) return null;
  const amount = Number(value);
  return Number.isFinite(amount) ? amount : Number.NaN;
}

function weight(value: string) {
  if (!value.trim()) return null;
  const amount = Number(value);
  return Number.isInteger(amount) ? amount : Number.NaN;
}

export function validateDraft(draft: ProductDraft, publishing: boolean, imageCount: number) {
  const errors: Record<string, string> = {};
  if (!draft.nameTh.trim()) errors.nameTh = "NAME_REQUIRED";
  const price = money(draft.basePrice);
  const compare = money(draft.compareAtPrice);
  const grams = weight(draft.weightGrams);
  if (draft.basePrice.trim() && (price == null || Number.isNaN(price) || price < 0)) errors.basePrice = "PRICE_INVALID";
  if (compare != null && (Number.isNaN(compare) || (price != null && !Number.isNaN(price) && compare <= price))) errors.compareAtPrice = "COMPARE_PRICE_INVALID";
  if (grams != null && (Number.isNaN(grams) || grams < 0)) errors.weightGrams = "WEIGHT_INVALID";
  const active = draft.skus.filter((sku) => !sku.removed);
  if (draft.productType === "SIMPLE" && active.length !== 1) errors.skus = "SIMPLE_SKU_COUNT";
  if (draft.productType === "VARIABLE" && active.length < 1) errors.skus = "SKU_REQUIRED";
  draft.skus.forEach((sku, index) => {
    if (sku.removed) return;
    if (!sku.skuCode.trim()) errors[`skus.${index}.skuCode`] = "SKU_CODE_REQUIRED";
    const skuPrice = money(sku.price);
    const skuCompare = money(sku.compareAtPrice);
    const skuCost = money(sku.costPrice);
    if (skuPrice == null || Number.isNaN(skuPrice) || skuPrice < 0) errors[`skus.${index}.price`] = "PRICE_INVALID";
    if (skuCompare != null && (Number.isNaN(skuCompare) || (skuPrice != null && !Number.isNaN(skuPrice) && skuCompare <= skuPrice))) {
      errors[`skus.${index}.compareAtPrice`] = "COMPARE_PRICE_INVALID";
    }
    if (skuCost != null && (Number.isNaN(skuCost) || skuCost < 0)) errors[`skus.${index}.costPrice`] = "PRICE_INVALID";
    const opening = Number(sku.initialStock || "0");
    if (!sku.id && (!Number.isInteger(opening) || opening < 0)) errors[`skus.${index}.initialStock`] = "INVALID_ADJUSTMENT";
  });
  if (publishing && imageCount < 1) errors.images = "PUBLISH_INCOMPLETE";
  if (publishing && (price == null || Number.isNaN(price)) && draft.skus.some((sku) => !sku.removed && money(sku.price) == null)) errors.basePrice = "PUBLISH_INCOMPLETE";
  return errors;
}

export function toWrite(draft: ProductDraft): ProductWrite {
  const price = money(draft.basePrice);
  const compare = money(draft.compareAtPrice);
  const grams = weight(draft.weightGrams);
  const skus: SkuWrite[] = draft.skus.filter((sku) => !sku.removed).map((sku) => ({
    id: sku.id,
    skuCode: sku.skuCode.trim(),
    barcode: sku.barcode.trim() || null,
    optionLabel: sku.optionLabel.trim() || null,
    optionValues: sku.optionValues,
    price: money(sku.price) ?? 0,
    costPrice: money(sku.costPrice),
    compareAtPrice: money(sku.compareAtPrice),
    weightGrams: weight(sku.weightGrams),
    initialStock: sku.id ? null : Number(sku.initialStock || "0"),
    imageId: sku.imageId || null,
    active: sku.active,
  }));
  return {
    nameTh: draft.nameTh.trim(),
    nameEn: draft.nameEn.trim() || null,
    nameZh: draft.nameZh.trim() || null,
    descriptionTh: draft.descriptionTh.trim() || null,
    descriptionEn: draft.descriptionEn.trim() || null,
    descriptionZh: draft.descriptionZh.trim() || null,
    categoryId: draft.categoryId ? Number(draft.categoryId) : null,
    productType: draft.productType,
    brand: draft.brand.trim() || null,
    basePrice: price != null && !Number.isNaN(price) ? price : null,
    compareAtPrice: compare != null && !Number.isNaN(compare) ? compare : null,
    weightGrams: grams != null && !Number.isNaN(grams) ? grams : null,
    optionSchema: draft.productType === "VARIABLE" ? draft.optionSchema : [],
    confirmRemoveStocked: Boolean(draft.confirmRemoveStocked),
    skus,
  };
}

export type VariantIssue = { index: number; field: string; code: string };

export function variantIssues(draft: Pick<ProductDraft, "productType" | "skus" | "optionSchema">, taken: Set<string>) {
  const issues: VariantIssue[] = [];
  if (draft.productType !== "VARIABLE") return issues;
  const active = draft.skus.map((sku, index) => ({ sku, index })).filter((row) => !row.sku.removed);
  if (!draft.optionSchema.length) issues.push({ index: -1, field: "optionSchema", code: "OPTION_SCHEMA_INVALID" });
  if (uncoveredOptionLabels(draft.optionSchema, draft.skus).length) issues.push({ index: -1, field: "optionSchema", code: "optionUncovered" });
  if (active.length > 100) issues.push({ index: -1, field: "skus", code: "SKU_LIMIT_EXCEEDED" });
  const codes = new Map<string, number>();
  const combos = new Map<string, number>();
  active.forEach(({ sku, index }) => {
    const code = sku.skuCode.trim();
    if (!code) issues.push({ index, field: "skuCode", code: "SKU_CODE_REQUIRED" });
    else if (codes.has(code)) issues.push({ index, field: "skuCode", code: "SKU_CODE_DUPLICATE_IN_FORM" });
    else if (taken.has(code)) issues.push({ index, field: "skuCode", code: "SKU_CODE_TAKEN" });
    else codes.set(code, index);
    const price = money(sku.price);
    if (price == null || Number.isNaN(price) || price < 0) issues.push({ index, field: "price", code: "PRICE_INVALID" });
    const order = draft.optionSchema.map((group) => group.name);
    const key = comboKey(sku.optionValues, order);
    if (draft.optionSchema.length && combos.has(key)) issues.push({ index, field: "optionValues", code: "VARIANT_COMBINATION_DUPLICATE" });
    else combos.set(key, index);
    const opening = Number(sku.initialStock || "0");
    if (!sku.id && (!Number.isInteger(opening) || opening < 0)) issues.push({ index, field: "initialStock", code: "INVALID_ADJUSTMENT" });
  });
  return issues;
}
