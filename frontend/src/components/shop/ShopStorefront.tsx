import { Fragment, type ReactNode } from "react";
import type { PublicProduct, PublicShop } from "../../services/api";
import { isShopSectionAvailable } from "./sections/availability";
import { SHOP_SECTION_COMPONENTS } from "./sections/registry";
import type { ShopCatalogControls } from "./sections/types";
import ShopThemeShell, { resolveStorefrontTheme } from "./ShopThemeShell";
import { hasPromoBanners } from "./promoTargets";

export { resolveStorefrontTheme };

type ShopStorefrontProps = {
  shop: PublicShop;
  products: PublicProduct[];
  catalog: ShopCatalogControls;
  /** Rendered above the sections (e.g. breadcrumb). */
  header?: ReactNode;
  /** Rendered after the sections (e.g. toast). */
  children?: ReactNode;
  /** Dim + mark the sections busy while the shop refetches. */
  refetching?: boolean;
  /** Render at a fixed device width without an iframe (merchant theme preview). */
  previewWidth?: number;
};

type ShopSectionsProps = {
  shop: PublicShop;
  products: PublicProduct[];
  catalog: ShopCatalogControls;
  preview?: boolean;
  /**
   * Storefront-only coupon strip (ShopPage). Inserted after BANNERS when banners render, else after HERO,
   * else first. Never passed by the theme editor preview.
   */
  couponStrip?: ReactNode;
};

/** Enabled theme sections only — Fragment so `.shop-mall-tabs` stays a direct child of `.skeleton-busy`. */
export function ShopSections({ shop, products, catalog, preview = false, couponStrip }: ShopSectionsProps) {
  const { config } = resolveStorefrontTheme(shop);
  const visible = config.sections.filter((section) => section.enabled && isShopSectionAvailable(section.key));
  const anchorKey = couponStrip
    ? hasPromoBanners(shop) ? "BANNERS" : visible.some((section) => section.key === "HERO") ? "HERO" : null
    : null;
  return (
    <>
      {couponStrip && anchorKey === null && <Fragment key="coupon-strip">{couponStrip}</Fragment>}
      {visible.map((section) => {
        const Component = SHOP_SECTION_COMPONENTS[section.key];
        return (
          <Fragment key={section.key}>
            <Component
              shop={shop}
              config={config}
              section={section}
              products={products}
              catalog={catalog}
              preview={preview}
            />
            {couponStrip && anchorKey === section.key && couponStrip}
          </Fragment>
        );
      })}
    </>
  );
}

export default function ShopStorefront({
  shop,
  products,
  catalog,
  header,
  children,
  refetching = false,
  previewWidth,
}: ShopStorefrontProps) {
  return (
    <ShopThemeShell shop={shop} header={header} refetching={refetching} previewWidth={previewWidth} after={children}>
      <ShopSections shop={shop} products={products} catalog={catalog} preview={previewWidth != null} />
    </ShopThemeShell>
  );
}
