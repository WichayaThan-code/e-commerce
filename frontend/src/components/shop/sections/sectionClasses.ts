import type {
  CatalogVariant, HeroLayout, ShopSectionKey, SectionVariant, ThemeConfig,
} from "../../../theme/shopTheme";

export function resolveVariant(key: ShopSectionKey, variant: SectionVariant | undefined): SectionVariant {
  if (key === "HERO") return variant === "STRIP" ? "STRIP" : "FULL";
  if (key === "FEATURED" || key === "BESTSELLERS" || key === "NEW_ARRIVALS" || key === "CATALOG") {
    return variant === "LARGE" || variant === "MENU" ? variant : "GRID";
  }
  return null;
}

/**
 * AG1 sectionSettings classes. Storefront configs come from normalizeTheme, which drops sectionSettings for
 * non-CUSTOM templates, so old templates always get []. CSS for these classes is also CUSTOM-scoped.
 */
export function sectionSettingClasses(key: ShopSectionKey, config?: ThemeConfig | null): string[] {
  const setting = config?.sectionSettings?.[key];
  if (!setting) return [];
  const classes: string[] = [];
  if (setting.background && setting.background !== "NONE") {
    classes.push(`shop-sec-bg-${setting.background.toLowerCase()}`);
  }
  if (setting.align === "CENTER") classes.push("shop-sec-align-center");
  if (setting.paddingY) classes.push(`shop-sec-pad-${setting.paddingY.toLowerCase()}`);
  if (setting.columns && (key === "FEATURED" || key === "BESTSELLERS" || key === "NEW_ARRIVALS" || key === "CATALOG")) {
    classes.push(`shop-sec-cols-${setting.columns}`);
  }
  return classes;
}

/** Section root className including variant suffix (e.g. shop-featured-menu). */
export function sectionRootClass(
  key: ShopSectionKey,
  variant: SectionVariant | undefined,
  configOrExtra?: ThemeConfig | string | null,
  ...extra: string[]
) {
  const config = configOrExtra && typeof configOrExtra === "object" ? configOrExtra : null;
  const rest = [
    ...(typeof configOrExtra === "string" ? [configOrExtra] : []),
    ...extra,
    ...sectionSettingClasses(key, config),
  ];
  const resolved = resolveVariant(key, variant);
  if (key === "HERO") {
    return resolved === "STRIP"
      ? ["shop-hero-strip", "shop-hero-variant-strip", "recommend-width", ...rest].filter(Boolean).join(" ")
      : ["shop-hero", "shop-hero-variant-full", "recommend-width", ...rest].filter(Boolean).join(" ");
  }
  const base =
    key === "FEATURED" ? "shop-featured"
    : key === "CATALOG" ? "shop-catalog"
    : key === "HOURS" ? "shop-hours"
    : key === "INTRO" ? "shop-intro"
    : "shop-section";
  const variantClass = resolved ? `${base}-${String(resolved).toLowerCase()}` : "";
  return [base, variantClass, "recommend-width", ...rest].filter(Boolean).join(" ");
}

export type { CatalogVariant, HeroLayout };
