import type { ProductSort, PublicProduct, PublicShop } from "../../../services/api";
import type { SectionConfig, ThemeConfig } from "../../../theme/shopTheme";

export type ProductLoadState = "loading" | "error" | "ok";

/** คีย์ใน UI/URL: featured = ใหม่ล่าสุด, price = ราคาต่ำ→สูง (คงชื่อเดิม), price_desc = ราคาสูง→ต่ำ */
export const SHOP_SORT_KEYS = ["featured", "price", "price_desc"] as const;
export type ShopSortKey = (typeof SHOP_SORT_KEYS)[number];

/** ค่าที่ไม่รู้จัก (รวมถึง "best" จากลิงก์เก่า) → "featured" ไม่ throw */
export function parseShopSortKey(value: string | null | undefined): ShopSortKey {
  return (SHOP_SORT_KEYS as readonly string[]).includes(value ?? "") ? (value as ShopSortKey) : "featured";
}

/** TODO(backend): เพิ่ม "best" → "best_selling" เมื่อ backend รองรับ */
export function shopApiSort(sort: ShopSortKey): ProductSort {
  if (sort === "price") return "price_asc";
  if (sort === "price_desc") return "price_desc";
  return "newest";
}

/** State + callbacks the catalog needs; owned by the page (live) or a preview wrapper. */
export type ShopCatalogControls = {
  productState: ProductLoadState;
  showSkeleton: boolean;
  refetching: boolean;
  total: number;
  page: number;
  pageSize: number;
  sort: ShopSortKey;
  grid: boolean;
  priceBands: string[];
  brandQuery: string;
  favorites: string[];
  categoryId?: number | null;
  /** true = หมวดมาจาก route (หน้า Category) — ไม่นับเป็นตัวกรอง ไม่แสดง chip และ "ล้าง" ไม่ล้างหมวด */
  categoryLocked?: boolean;
  onSort: (sort: ShopSortKey) => void;
  onGrid: (grid: boolean) => void;
  onPage: (page: number) => void;
  onTogglePriceBand: (band: string) => void;
  onBrandQuery: (query: string) => void;
  onToggleFavorite: (productId: string) => void;
  onCategory?: (categoryId: number | null) => void;
  onRetry: () => void;
  onNotify: (message: string) => void;
};

export type ShopSectionProps = {
  shop: PublicShop;
  config: ThemeConfig;
  section: SectionConfig;
  products: PublicProduct[];
  catalog: ShopCatalogControls;
  preview?: boolean;
};

export function previewCatalogControls(overrides: Partial<ShopCatalogControls> = {}): ShopCatalogControls {
  const noop = () => undefined;
  return {
    productState: "ok",
    showSkeleton: false,
    refetching: false,
    total: 0,
    page: 1,
    pageSize: 30,
    sort: "featured",
    grid: true,
    priceBands: [],
    brandQuery: "",
    favorites: [],
    categoryId: null,
    onCategory: noop,
    onSort: noop,
    onGrid: noop,
    onPage: noop,
    onTogglePriceBand: noop,
    onBrandQuery: noop,
    onToggleFavorite: noop,
    onRetry: noop,
    onNotify: noop,
    ...overrides,
  };
}
