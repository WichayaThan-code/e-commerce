import type { PublicShop } from "../../services/api";
import { resolveStorefrontTheme } from "./ShopThemeShell";

/** Anchor of ShopCouponStrip (ShopPage) and ShopCouponStripPreview (theme editor preview). */
export const PROMO_COUPONS_ANCHOR = "shop-promotions";
/** Anchor of BannersSection. */
export const PROMO_BANNERS_ANCHOR = "shop-banners";

/** BannersSection renders only when the section is enabled and ≥1 banner has an image. */
export function hasPromoBanners(shop: PublicShop): boolean {
  const { config } = resolveStorefrontTheme(shop);
  const enabled = config.sections.some((section) => section.key === "BANNERS" && section.enabled);
  return enabled && (config.banners || []).some((banner) => Boolean(banner.imageUrl?.trim()));
}
