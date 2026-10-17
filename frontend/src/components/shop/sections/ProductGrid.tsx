import ProductCard, { type ProductCardVariant } from "../ProductCard";
import type { PublicProduct, PublicShop } from "../../../services/api";
import type { SectionVariant } from "../../../theme/shopTheme";

type ProductGridProps = {
  shop: PublicShop;
  products: PublicProduct[];
  variant?: SectionVariant;
  /** GRID variant only: false renders the existing list-view layout. */
  grid?: boolean;
  cardVariant?: ProductCardVariant;
  favorites: string[];
  onToggleFavorite: (productId: string) => void;
  onNotify: (message: string) => void;
};

export function productGridClass(variant: SectionVariant | undefined, grid = true) {
  if (variant === "LARGE") return "recommend-product-grid shop-products-large";
  if (variant === "MENU") return "recommend-product-grid shop-products-menu";
  return `recommend-product-grid shop-products-grid ${grid ? "" : "list-view"}`.trim();
}

function resolveCardVariant(variant: SectionVariant | undefined, override?: ProductCardVariant): ProductCardVariant {
  if (override) return override;
  if (variant === "MENU") return "menu-row";
  return "catalog";
}

export default function ProductGrid({
  shop,
  products,
  variant,
  grid = true,
  cardVariant,
  favorites,
  onToggleFavorite,
  onNotify,
}: ProductGridProps) {
  const resolved = resolveCardVariant(variant, cardVariant);
  return (
    <div className={productGridClass(variant, grid)}>
      {products.map((product) => (
        <ProductCard
          key={product.id}
          shop={shop}
          product={product}
          variant={resolved}
          favorites={favorites}
          onToggleFavorite={onToggleFavorite}
          onNotify={onNotify}
        />
      ))}
    </div>
  );
}
