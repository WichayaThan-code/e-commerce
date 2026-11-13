import type { ProductSort, PublicProduct, PublicShop } from "../../../services/api";
import type { LayoutBlock, SectionConfig, ThemeConfig } from "../../../theme/shopTheme";

export type ProductLoadState = "loading" | "error" | "ok";

/** UI/URL sort keys for shop catalog. */
export const SHOP_SORT_KEYS = ["popular", "newest", "best", "price", "price_desc"] as const;
export type ShopSortKey = (typeof SHOP_SORT_KEYS)[number];

/** Unknown / legacy values → popular. `featured` kept as alias of popular. */
export function parseShopSortKey(value: string | null | undefined): ShopSortKey {
  if (value === "featured") return "popular";
  return (SHOP_SORT_KEYS as readonly string[]).includes(value ?? "") ? (value as ShopSortKey) : "popular";
}

export function shopApiSort(sort: ShopSortKey): ProductSort {
  if (sort === "price") return "price_asc";
  if (sort === "price_desc") return "price_desc";
  if (sort === "best") return "best_selling";
  return "newest"; // popular | newest
}

/** Compact page list: 1-based page numbers + "gap" ellipsis. */
export function shopPageWindow(current: number, total: number): Array<number | "gap"> {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const picks = new Set<number>([1, total, current]);
  for (let i = current - 1; i <= current + 1; i++) {
    if (i > 1 && i < total) picks.add(i);
  }
  const sorted = [...picks].sort((a, b) => a - b);
  const out: Array<number | "gap"> = [];
  for (let i = 0; i < sorted.length; i++) {
    if (i > 0 && sorted[i]! - sorted[i - 1]! > 1) out.push("gap");
    out.push(sorted[i]!);
  }
  return out;
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

/** AG4 content blocks (STEPS, FEATURE, GALLERY, SOCIAL, RICH_TEXT) receive their block (and its `content`) directly. */
export type ShopNewBlockProps = {
  shop: PublicShop;
  config: ThemeConfig;
  block: LayoutBlock;
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
    sort: "popular",
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
