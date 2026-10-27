import {
  gradientCss, isSectionKey, PRODUCT_BLOCK_TYPES,
  type LayoutBlock, type SectionConfig, type ShopSectionKey, type ThemeConfig, type ThemeSectionSetting,
} from "../../theme/shopTheme";

/**
 * The legacy section row a block renders through (existing SHOP_SECTION_COMPONENTS take a SectionConfig).
 * Only meaningful for the ten legacy block types; AG4 content blocks render through SHOP_BLOCK_COMPONENTS.
 */
export function blockSection(block: LayoutBlock): SectionConfig {
  return { key: block.type as ShopSectionKey, enabled: block.enabled, variant: block.variant ?? null };
}

/**
 * Section components already know how to apply surface/primary/gradient backgrounds, centered headings,
 * vertical padding and column counts from `config.sectionSettings` (AG1 classes). A block hands its own settings to
 * its section through that same channel so migrated blocks render exactly like the legacy sections did.
 * IMAGE backgrounds, overlay and full-width are handled by ShopBlockShell.
 */
export function blockSectionConfig(block: LayoutBlock, config: ThemeConfig): ThemeConfig {
  if (!isSectionKey(block.type)) return config;
  const { settings } = block;
  const legacy: ThemeSectionSetting = {};
  if (settings.background === "SURFACE" || settings.background === "PRIMARY" || settings.background === "GRADIENT") {
    legacy.background = settings.background;
  }
  if (settings.align === "CENTER") legacy.align = "CENTER";
  if (settings.paddingY !== "NORMAL") legacy.paddingY = settings.paddingY;
  if (settings.columns != null && PRODUCT_BLOCK_TYPES.includes(block.type)) legacy.columns = settings.columns;
  return { ...config, sectionSettings: { [block.type]: legacy } };
}

/** Only same-origin paths or https URLs may reach a CSS url(). */
export function safeBackgroundUrl(url: string | undefined): string | null {
  const value = url?.trim();
  if (!value) return null;
  if (/[\s"'()\\]/.test(value)) return null;
  if (value.startsWith("/") && !value.startsWith("//")) return value;
  if (/^https:\/\//i.test(value)) return value;
  return null;
}

export function blockUsesBox(block: LayoutBlock): boolean {
  return block.settings.fullWidth || (block.settings.background === "IMAGE" && safeBackgroundUrl(block.settings.backgroundUrl) != null);
}

export function blockGradientVar(block: LayoutBlock): string | null {
  return block.settings.background === "GRADIENT" ? gradientCss(block.settings.gradient) : null;
}
