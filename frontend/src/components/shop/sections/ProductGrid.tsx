import { useMemo } from "react";
import ProductCard, { type ProductCardVariant } from "../ProductCard";
import type { PublicProduct, PublicShop } from "../../../services/api";
import { isCustomTemplate, normalizeTheme, type SectionVariant, type ShopTheme, type ThemeProductCard } from "../../../theme/shopTheme";

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

/** AG1 productCard tokens apply to CUSTOM only; other templates always get undefined. */
export function useCustomProductCard(shop: PublicShop): ThemeProductCard | undefined {
  return useMemo(() => {
    if (!shop.theme) return undefined;
    const theme = normalizeTheme(shop.theme as Partial<ShopTheme>);
    return isCustomTemplate(theme.templateKey) ? theme.config.productCard : undefined;
  }, [shop.theme]);
}

export function productCardClasses(productCard?: ThemeProductCard): string[] {
  const extras: string[] = [];
  if (productCard?.edgeToEdge) extras.push("shop-products-edge");
  if (productCard?.showPrice === false) extras.push("shop-products-hide-price");
  if (productCard?.ctaStyle === "NONE") extras.push("shop-products-no-cta");
  if (productCard?.titleAlign === "CENTER") extras.push("shop-products-title-center");
  return extras;
}

export function productGridClass(variant: SectionVariant | undefined, grid = true, productCard?: ThemeProductCard) {
  const extras = productCardClasses(productCard);
  if (variant === "LARGE") return ["recommend-product-grid", "shop-products-large", ...extras].join(" ");
  if (variant === "MENU") return ["recommend-product-grid", "shop-products-menu", ...extras].join(" ");
  return ["recommend-product-grid", "shop-products-grid", grid ? "" : "list-view", ...extras].filter(Boolean).join(" ");
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
  const productCard = useCustomProductCard(shop);
  return (
    <div className={productGridClass(variant, grid, productCard)}>
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
