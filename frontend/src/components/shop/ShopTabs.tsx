import { useEffect, useMemo, useRef, type MouseEvent } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import type { PublicShop } from "../../services/api";
import { useShopPromotions } from "../../hooks/useShopPromotions";
import { hasPromoBanners, PROMO_BANNERS_ANCHOR, PROMO_COUPONS_ANCHOR } from "./promoTargets";
import {
  shopCategoriesPath,
  shopContactPath,
  shopPath,
  shopPoliciesPath,
  shopProductsPath,
} from "../../services/shopRoutes";

type ShopTabsProps = {
  shop: PublicShop;
  preview?: boolean;
  /** Toast hook (ShopLayout notify / catalog.onNotify). Used when there are neither coupons nor banners. */
  onNotify?: (message: string) => void;
};

export default function ShopTabs({ shop, preview = false, onNotify }: ShopTabsProps) {
  const { tp } = usePublicTranslation();
  const location = useLocation();
  const tabsRef = useRef<HTMLElement>(null);
  const slug = shop.slug;
  const bannersReady = useMemo(() => hasPromoBanners(shop), [shop]);
  // Preview (theme editor) never fetches; storefront shares the cached list with ShopCouponStrip.
  const { promotions } = useShopPromotions(preview ? null : slug);
  const promoAnchor = promotions.length > 0 ? PROMO_COUPONS_ANCHOR : bannersReady ? PROMO_BANNERS_ANCHOR : null;
  const promoReady = promoAnchor !== null;

  useEffect(() => {
    const tabs = tabsRef.current;
    if (!tabs) return;
    const root = tabs.closest(".shop-storefront-cq") as HTMLElement | null;
    const apply = () => {
      const raw = Math.round(tabs.getBoundingClientRect().height);
      // Cap — sticky flex-stretch can report page-tall height; never feed that back into --shop-tabs-h.
      const height = raw > 56 ? 44 : Math.min(56, Math.max(44, raw));
      if (root) root.style.setProperty("--shop-tabs-h", `${height}px`);
    };
    apply();
    const observer = typeof ResizeObserver !== "undefined" ? new ResizeObserver(apply) : null;
    observer?.observe(tabs);
    return () => observer?.disconnect();
  }, []);

  const items = [
    { key: "home" as const, to: shopPath(slug), end: true },
    { key: "products" as const, to: shopProductsPath(slug) },
    { key: "categories" as const, to: shopCategoriesPath(slug) },
    { key: "promos" as const, to: { pathname: shopPath(slug), hash: promoAnchor ?? PROMO_BANNERS_ANCHOR }, neverActive: true },
    { key: "contact" as const, to: shopContactPath(slug) },
    { key: "policies" as const, to: shopPoliciesPath(slug) },
  ];

  const onPromoClick = (event: MouseEvent<HTMLAnchorElement>) => {
    if (!promoReady) {
      // Neither coupons nor banners to scroll to — same "coming soon" toast pattern as the catalog filters.
      event.preventDefault();
      onNotify?.(tp("shop.tabs.promosSoon"));
      return;
    }
    // Same URL + same hash: router does not re-run ShopLayout's hash effect, so scroll here.
    if (location.pathname === shopPath(slug) && location.hash === `#${promoAnchor}`) {
      event.preventDefault();
      document.getElementById(promoAnchor)?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  };

  return (
    <nav ref={tabsRef} className="shop-mall-tabs recommend-width" aria-label={tp("shop.tabsAria")}>
      {preview
        ? items.map((tab) => (
          <a key={tab.key} href="#" className={tab.key === "home" ? "is-active" : undefined}>
            {tp(`shop.tabs.${tab.key}`)}
          </a>
        ))
        : items.map((tab) => (
          <NavLink
            key={tab.key}
            to={tab.to}
            end={"end" in tab ? tab.end : undefined}
            onClick={tab.key === "promos" ? onPromoClick : undefined}
            aria-disabled={tab.key === "promos" && !promoReady ? true : undefined}
            className={({ isActive }) => (tab.neverActive ? undefined : isActive ? "is-active" : undefined)}
          >
            {tp(`shop.tabs.${tab.key}`)}
          </NavLink>
        ))}
    </nav>
  );
}
