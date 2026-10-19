import { useEffect, useState } from "react";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { shopApi, type PublicProduct } from "../../../services/api";
import { resolveVariant } from "./sectionClasses";
import ProductRail from "./ProductRail";
import type { ProductCardVariant } from "../ProductCard";
import type { ShopSectionProps } from "./types";

function cardVariantFor(variant: string): ProductCardVariant {
  if (variant === "LARGE") return "featured-large";
  if (variant === "MENU") return "menu-row";
  return "featured-row";
}

export default function NewArrivalsSection({ shop, section, products, catalog, preview }: ShopSectionProps) {
  const { tp } = usePublicTranslation();
  const variant = resolveVariant("NEW_ARRIVALS", section.variant) || "GRID";
  const [items, setItems] = useState<PublicProduct[]>([]);
  const [loading, setLoading] = useState(!preview);

  useEffect(() => {
    if (!preview) return;
    setItems(products.slice(0, 12));
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
    shopApi.products(shop.slug, { size: 12, page: 0, sort: "newest" })
      .then((result) => { if (active) setItems(result.items); })
      .catch(() => { if (active) setItems([]); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [shop.slug, preview]);

  return (
    <ProductRail
      shop={shop}
      products={items}
      title={tp("shop.newArrivalsTitle")}
      seeAllHref="#shop-catalog"
      cardVariant={cardVariantFor(variant)}
      loading={loading}
      favorites={catalog.favorites}
      onToggleFavorite={catalog.onToggleFavorite}
      onNotify={catalog.onNotify}
      className={`shop-featured-${String(variant).toLowerCase()} shop-new-arrivals`}
    />
  );
}
