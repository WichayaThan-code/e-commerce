import type { Product, ProductWrite, SkuWrite } from "../services/api";

export type SkuDraft = {
  id?: string;
  skuCode: string;
  barcode: string;
  optionLabel: string;
  optionValues: Record<string, string>;
  price: string;
  compareAtPrice: string;
  weightGrams: string;
  initialStock: string;
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
  skus: SkuDraft[];
};

export type OptionAxis = { name: string; values: string[] };

export function emptySku(partial: Partial<SkuDraft> = {}): SkuDraft {
  return {
    skuCode: "",
    barcode: "",
    optionLabel: "",
    optionValues: {},
    price: "",
    compareAtPrice: "",
    weightGrams: "",
    initialStock: "0",
    ...partial,
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
    skus: product.skus.length
      ? product.skus.map((sku) => emptySku({
          id: sku.id,
          skuCode: sku.skuCode,
          barcode: sku.barcode || "",
          optionLabel: sku.optionLabel || "",
          optionValues: sku.optionValues || {},
          price: String(sku.price ?? ""),
          compareAtPrice: sku.compareAtPrice == null ? "" : String(sku.compareAtPrice),
          weightGrams: sku.weightGrams == null ? "" : String(sku.weightGrams),
          initialStock: String(sku.onHand ?? 0),
        }))
      : [emptySku()],
  };
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

function codeFrom(parts: string[], index: number) {
  const latin = parts.join("-").toUpperCase().replace(/[^A-Z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 64);
  return latin.length >= 2 ? latin : `SKU-${index + 1}`;
}

export function combineSkus(axes: OptionAxis[], current: SkuDraft[], price: string): { skus: SkuDraft[]; error?: "TOO_MANY" } {
  const usable = axes
    .map((axis) => ({ name: axis.name.trim(), values: axis.values.map((value) => value.trim()).filter(Boolean) }))
    .filter((axis) => axis.name && axis.values.length);
  if (!usable.length) return { skus: current.length ? current : [emptySku({ price })] };
  const combos = usable.reduce<Record<string, string>[]>((rows, axis) => {
    if (!rows.length) return axis.values.map((value) => ({ [axis.name]: value }));
    return rows.flatMap((row) => axis.values.map((value) => ({ ...row, [axis.name]: value })));
  }, []);
  if (combos.length > 80) return { skus: current, error: "TOO_MANY" };
  return {
    skus: combos.map((optionValues, index) => {
      const optionLabel = Object.values(optionValues).join(" / ");
      const found = current.find((sku) => sku.optionLabel === optionLabel);
      if (found) return { ...found, optionValues, optionLabel };
      return emptySku({
        optionLabel,
        optionValues,
        price,
        skuCode: codeFrom(Object.values(optionValues), index),
      });
    }),
  };
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

const BARCODE = /^[A-Za-z0-9-]{0,64}$/;
const MAX_STOCK = 1_000_000;

/** Client-side mirror of the backend rules (ProductService.validate); the server re-checks everything. */
export function validateDraft(draft: ProductDraft, publishing: boolean, imageCount: number) {
  const errors: Record<string, string> = {};
  if (!draft.nameTh.trim()) errors.nameTh = "NAME_REQUIRED";
  const price = money(draft.basePrice);
  const compare = money(draft.compareAtPrice);
  const grams = weight(draft.weightGrams);
  if (draft.basePrice.trim() && (price == null || Number.isNaN(price) || price <= 0)) errors.basePrice = "PRICE_INVALID";
  if (compare != null && (Number.isNaN(compare) || compare <= 0 || (price != null && !Number.isNaN(price) && compare < price))) errors.compareAtPrice = "COMPARE_PRICE_INVALID";
  if (grams != null && (Number.isNaN(grams) || grams < 0 || grams > MAX_STOCK)) errors.weightGrams = "WEIGHT_INVALID";
  if (draft.productType === "SIMPLE" && draft.skus.length !== 1) errors.skus = "SIMPLE_SKU_COUNT";
  if (draft.productType === "VARIABLE" && draft.skus.length < 1) errors.skus = "SKU_REQUIRED";
  const codes = new Set<string>();
  const barcodes = new Set<string>();
  draft.skus.forEach((sku, index) => {
    const code = sku.skuCode.trim();
    if (!code) errors[`skus.${index}.skuCode`] = "SKU_CODE_REQUIRED";
    else if (code.length > 64) errors[`skus.${index}.skuCode`] = "SKU_CODE_TOO_LONG";
    else if (codes.has(code)) errors[`skus.${index}.skuCode`] = "SKU_CODE_DUPLICATE";
    codes.add(code);
    const barcode = sku.barcode.trim();
    if (barcode && !BARCODE.test(barcode)) errors[`skus.${index}.barcode`] = "BARCODE_INVALID";
    else if (barcode && barcodes.has(barcode.toLowerCase())) errors[`skus.${index}.barcode`] = "BARCODE_DUPLICATE";
    if (barcode) barcodes.add(barcode.toLowerCase());
    const skuPrice = money(sku.price);
    const skuCompare = money(sku.compareAtPrice);
    if (skuPrice == null || Number.isNaN(skuPrice) || skuPrice <= 0) errors[`skus.${index}.price`] = "PRICE_INVALID";
    if (skuCompare != null && (Number.isNaN(skuCompare) || skuCompare <= 0 || (skuPrice != null && !Number.isNaN(skuPrice) && skuCompare < skuPrice))) {
      errors[`skus.${index}.compareAtPrice`] = "COMPARE_PRICE_INVALID";
    }
    const opening = Number(sku.initialStock || "0");
    if (!sku.id && (!Number.isInteger(opening) || opening < 0 || opening > MAX_STOCK)) errors[`skus.${index}.initialStock`] = "STOCK_INVALID";
  });
  if (publishing && imageCount < 1) errors.images = "PUBLISH_IMAGE_REQUIRED";
  if (publishing && (price == null || Number.isNaN(price)) && draft.skus.some((sku) => money(sku.price) == null)) errors.basePrice = "PUBLISH_INCOMPLETE";
  return errors;
}

export function toWrite(draft: ProductDraft): ProductWrite {
  const price = money(draft.basePrice);
  const compare = money(draft.compareAtPrice);
  const grams = weight(draft.weightGrams);
  const skus: SkuWrite[] = draft.skus.map((sku) => ({
    id: sku.id,
    skuCode: sku.skuCode.trim(),
    barcode: sku.barcode.trim() || null,
    optionLabel: sku.optionLabel.trim() || null,
    optionValues: sku.optionValues,
    price: money(sku.price) ?? 0,
    compareAtPrice: money(sku.compareAtPrice),
    weightGrams: weight(sku.weightGrams),
    // opening stock is saved with the product (ledger RECEIVE in the same transaction); existing SKUs ignore it
    initialStock: sku.id ? null : Number(sku.initialStock || "0") || 0,
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
    skus,
  };
}
