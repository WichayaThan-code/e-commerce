import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { resolveVariant } from "./sectionClasses";
import ProductRail from "./ProductRail";
import type { ProductCardVariant } from "../ProductCard";
import type { ShopSectionProps } from "./types";

const FEATURED_LIMIT = 12;

function cardVariantFor(variant: string): ProductCardVariant {
  if (variant === "LARGE") return "featured-large";
  if (variant === "MENU") return "menu-row";
  return "featured-row";
}

export default function FeaturedSection({ shop, section, products, catalog }: ShopSectionProps) {
  const { tp } = usePublicTranslation();
  const variant = resolveVariant("FEATURED", section.variant) || "GRID";
  const featured = products.slice(0, FEATURED_LIMIT);
  const loading = catalog.showSkeleton && catalog.productState === "loading" && products.length === 0;

  // Hide the whole block when there is nothing to show (no empty white panel).
  if (!loading && featured.length === 0) return null;

  return (
    <ProductRail
      shop={shop}
      products={featured}
      title={tp("shop.featuredTitleForYou")}
      seeAllHref="#shop-catalog"
      cardVariant={cardVariantFor(variant)}
      loading={loading}
      favorites={catalog.favorites}
      onToggleFavorite={catalog.onToggleFavorite}
      onNotify={catalog.onNotify}
      className={`shop-featured-${String(variant).toLowerCase()}`}
    />
  );
}
