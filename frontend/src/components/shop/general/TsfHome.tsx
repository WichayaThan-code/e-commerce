import type { ReactNode, RefObject } from "react";
import type { PublicProduct, PublicShop } from "../../../services/api";
import type { ShopCatalogControls } from "../sections/types";
import type { ThemeConfig } from "../../../theme/shopTheme";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { shopProductsPath } from "../../../services/shopRoutes";
import { tsfThemeDefaults } from "../../../theme/shopTheme";
import { TsfCategoryRail, TsfHero, TsfHoursCard, TsfPromoBanners, TsfProductSection, TsfStoryBlock, TsfTrustStrip } from "./GeneralStorefront";

/**
 * Store-home body for the tsf templates (GENERAL / FASHION / FOOD_BEVERAGE). Shared by the public ShopPage and the
 * merchant theme-editor preview so the preview renders exactly the same components, fed by the (unsaved) draft config.
 */
export type TsfHomeProps = {
  slug: string;
  shop: PublicShop;
  theme: { templateKey: string; config: ThemeConfig };
  products: PublicProduct[];
  catalog: ShopCatalogControls;
  allProducts: { items: PublicProduct[]; hasMore: boolean; sentinelRef?: RefObject<HTMLDivElement | null> };
  categoryNames: Map<number, string>;
  couponStrip: ReactNode;
};

export default function TsfHome({ slug, shop, theme, products, catalog, allProducts, categoryNames, couponStrip }: TsfHomeProps) {
  const { tp } = usePublicTranslation();
  if (theme.templateKey === "FASHION" || theme.templateKey === "FOOD_BEVERAGE") {
    // Themes port (FASHION): follows the saved section order (preset: HERO, NEW_ARRIVALS LARGE, BANNERS, CATEGORIES tile,
    // BESTSELLERS, INTRO, TRUST). FOOD_BEVERAGE uses the same path (preset: HERO STRIP, CATEGORIES chip, FEATURED MENU,
    // BESTSELLERS MENU, BANNERS, HOURS, INTRO).
    const bySold = [...products].sort((a, b) => (b.soldCount ?? 0) - (a.soldCount ?? 0));
    const byNew = [...products].sort((a, b) => (Date.parse(b.createdAt ?? "") || 0) - (Date.parse(a.createdAt ?? "") || 0));
    const common = { shop, categories: categoryNames, favorites: catalog.favorites, onToggleFavorite: catalog.onToggleFavorite, onNotify: catalog.onNotify };
    const seeAll = { to: shopProductsPath(slug), label: tp("shop.seeAll") };
    const layoutOf = (v: unknown) => (v === "LARGE" || v === "MENU" ? v : "GRID");
    const railStyle = tsfThemeDefaults(theme.templateKey).categoryStyle;
    return (
      <main>
        {theme.config.sections.filter((section) => section.enabled).map((section) => {
          switch (section.key) {
            case "HERO": return <TsfHero key="HERO" shop={shop} config={theme.config} />;
            case "TRUST": return <TsfTrustStrip key="TRUST" config={theme.config} />;
            case "BANNERS": return <TsfPromoBanners key="BANNERS" config={theme.config} />;
            case "CATEGORIES": return <TsfCategoryRail key="CATEGORIES" shop={shop} railStyle={railStyle} />;
            case "NEW_ARRIVALS": return <TsfProductSection key="NEW_ARRIVALS" {...common} title={tp("shop.newArrivalsTitle")} products={byNew.slice(0, 8)} link={seeAll} variant={layoutOf(section.variant)} />;
            case "FEATURED": return <TsfProductSection key="FEATURED" {...common} title={tp("shop.featuredTitle")} products={products.slice(0, 4)} link={seeAll} variant={layoutOf(section.variant)} />;
            case "BESTSELLERS": return bySold.some((p) => (p.soldCount ?? 0) > 0)
              ? <TsfProductSection key="BESTSELLERS" {...common} title={tp("shop.bestsellersTitle")} products={bySold.slice(0, 8)} link={seeAll} variant={layoutOf(section.variant)} />
              : null;
            case "CATALOG": return (
              <section key="CATALOG" id="shop-products">
                <TsfProductSection {...common} title={tp("shop.general.allProducts")} products={allProducts.items} variant={layoutOf(section.variant)}
                  after={allProducts.hasMore ? <div ref={allProducts.sentinelRef} aria-hidden="true" style={{ width: 1, height: 1 }} /> : null} />
              </section>
            );
            case "INTRO": return <TsfStoryBlock key="INTRO" shop={shop} config={theme.config} />;
            case "HOURS": return <TsfHoursCard key="HOURS" shop={shop} />;
            default: return null;
          }
        })}
        <div className="tsf-wrap">{couponStrip}</div>
      </main>
    );
  }

  if (theme.templateKey === "GENERAL") {
    // Themes port (GENERAL): HERO FULL → TRUST → CATEGORIES → FEATURED → BESTSELLERS → CATALOG.
    const bySold = [...products].sort((a, b) => (b.soldCount ?? 0) - (a.soldCount ?? 0));
    const common = { shop, categories: categoryNames, favorites: catalog.favorites, onToggleFavorite: catalog.onToggleFavorite, onNotify: catalog.onNotify };
    const rawLayout = theme.config.sections.find((item) => item.key === "CATALOG")?.variant;
    const catalogLayout = rawLayout === "LARGE" || rawLayout === "MENU" ? rawLayout : "GRID";
    const seeAll = { to: shopProductsPath(slug), label: tp("shop.seeAll") };
    return (
      <main>
        <TsfHero shop={shop} config={theme.config} />
        <TsfTrustStrip config={theme.config} />
        <div className="tsf-wrap">{couponStrip}</div>
        <TsfCategoryRail shop={shop} />
        <TsfProductSection {...common} title={tp("shop.featuredTitle")} products={products.slice(0, 4)} link={seeAll} />
        {theme.config.sections.find((item) => item.key === "BANNERS")?.enabled !== false ? <TsfPromoBanners config={theme.config} /> : null}
        {bySold.some((p) => (p.soldCount ?? 0) > 0) ? (
          <TsfProductSection {...common} title={tp("shop.bestsellersTitle")} products={bySold.slice(0, 4)} link={seeAll} background="SURFACE" />
        ) : null}
        {theme.config.sections.find((item) => item.key === "INTRO")?.enabled ? <TsfStoryBlock shop={shop} config={theme.config} /> : null}
        {theme.config.sections.find((item) => item.key === "HOURS")?.enabled ? <TsfHoursCard shop={shop} /> : null}
        <section id="shop-products">
          <TsfProductSection {...common} title={tp("shop.general.allProducts")} products={allProducts.items}
            variant={catalogLayout}
            after={allProducts.hasMore ? <div ref={allProducts.sentinelRef} aria-hidden="true" style={{ width: 1, height: 1 }} /> : null} />
        </section>
      </main>
    );
  }

  return null;
}
