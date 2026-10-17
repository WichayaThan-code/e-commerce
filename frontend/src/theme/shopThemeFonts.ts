/**
 * @deprecated Prefer `theme/shopTheme.ts` for storefront theme fonts.
 * Kept for callers that still import this module.
 */
export {
  SHOP_THEME_FONTS as SHOP_THEME_FONT_IDS,
  type ShopThemeFontKey as ShopThemeFontId,
  shopThemeFontStack,
  SHOP_THEME_FONT_STACKS,
} from './shopTheme'

export const DEFAULT_SHOP_THEME_FONT = 'IBM_PLEX_SANS_THAI' as const
