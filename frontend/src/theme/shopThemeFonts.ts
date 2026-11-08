/**
 * @deprecated Prefer `theme/shopTheme.ts` for storefront theme fonts.
 * Kept for callers that still import this module.
 */
export {
  SHOP_THEME_FONTS as SHOP_THEME_FONT_IDS,
  SHOP_THEME_FONT_CATALOG,
  type ShopThemeFontKey as ShopThemeFontId,
  type ShopThemeFontDefinition,
  shopThemeFontStack,
  shopThemeFontDefinition,
  SHOP_THEME_FONT_STACKS,
  SHOP_THEME_FONT_HREF,
} from './shopTheme'

export const DEFAULT_SHOP_THEME_FONT = 'IBM_PLEX_SANS_THAI' as const
