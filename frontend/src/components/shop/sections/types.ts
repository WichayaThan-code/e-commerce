import type { PublicProduct, PublicShop } from "../../../services/api";
import type { SectionConfig, ThemeConfig } from "../../../theme/shopTheme";

export type ProductLoadState = "loading" | "error" | "ok";
export type ShopSortKey = "featured" | "price" | "best";

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
