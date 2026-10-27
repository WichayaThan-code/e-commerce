import { useEffect, useState } from "react";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { shopApi, type PublicProduct } from "../../../services/api";
import { shopProductsPath } from "../../../services/shopRoutes";
import { resolveVariant, sectionSettingClasses } from "./sectionClasses";
import ProductRail from "./ProductRail";
import type { ProductCardVariant } from "../ProductCard";
import type { ShopSectionProps } from "./types";

const FEATURED_LIMIT = 12;

function cardVariantFor(variant: string): ProductCardVariant {
  if (variant === "LARGE") return "featured-large";
  if (variant === "MENU") return "menu-row";
  return "featured-row";
}

export default function FeaturedSection({ shop, section, products, catalog, preview, config }: ShopSectionProps) {
  const { tp } = usePublicTranslation();
  const variant = resolveVariant("FEATURED", section.variant) || "GRID";
  const [items, setItems] = useState<PublicProduct[]>([]);
  const [loading, setLoading] = useState(!preview);

  useEffect(() => {
    if (!preview) return;
    setItems(products.slice(0, FEATURED_LIMIT));
    setLoading(false);
  }, [preview, products]);

  useEffect(() => {
    if (preview) return;
    if (!shop.slug) {
      setItems([]);
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    // TODO(backend): ใช้ sort "featured" (is_featured/featured_rank) เมื่อมี — ตอนนี้ใช้สินค้าใหม่ล่าสุด
    shopApi.products(shop.slug, { size: FEATURED_LIMIT, page: 0, sort: "newest" })
      .then((result) => { if (active) setItems(result.items); })
      .catch(() => { if (active) setItems([]); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [shop.slug, preview]);

  // Hide the whole block when there is nothing to show (no empty white panel).
  if (!loading && items.length === 0) return null;

  return (
    <ProductRail
      shop={shop}
      products={items}
      title={tp("shop.featuredTitleForYou")}
      seeAllHref={shopProductsPath(shop.slug)}
      cardVariant={cardVariantFor(variant)}
      loading={loading}
      favorites={catalog.favorites}
      onToggleFavorite={catalog.onToggleFavorite}
      onNotify={catalog.onNotify}
      className={[`shop-featured-${String(variant).toLowerCase()}`, ...sectionSettingClasses("FEATURED", config)].filter(Boolean).join(" ")}
    />
  );
}
