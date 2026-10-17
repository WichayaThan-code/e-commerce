import type { CatalogVariant, HeroLayout, ShopSectionKey, SectionVariant } from "../../../theme/shopTheme";

export function resolveVariant(key: ShopSectionKey, variant: SectionVariant | undefined): SectionVariant {
  if (key === "HERO") return variant === "STRIP" ? "STRIP" : "FULL";
  if (key === "FEATURED" || key === "BESTSELLERS" || key === "NEW_ARRIVALS" || key === "CATALOG") {
    return variant === "LARGE" || variant === "MENU" ? variant : "GRID";
  }
  return null;
}

/** Section root className including variant suffix (e.g. shop-featured-menu). */
export function sectionRootClass(key: ShopSectionKey, variant: SectionVariant | undefined, ...extra: string[]) {
  const resolved = resolveVariant(key, variant);
  if (key === "HERO") {
    return resolved === "STRIP"
      ? ["shop-hero-strip", "shop-hero-variant-strip", "recommend-width", ...extra].filter(Boolean).join(" ")
      : ["shop-hero", "shop-hero-variant-full", "recommend-width", ...extra].filter(Boolean).join(" ");
  }
  const base =
    key === "FEATURED" ? "shop-featured"
    : key === "CATALOG" ? "shop-catalog"
    : key === "HOURS" ? "shop-hours"
    : key === "INTRO" ? "shop-intro"
    : "shop-section";
  const variantClass = resolved ? `${base}-${String(resolved).toLowerCase()}` : "";
  return [base, variantClass, "recommend-width", ...extra].filter(Boolean).join(" ");
}

export type { CatalogVariant, HeroLayout };
