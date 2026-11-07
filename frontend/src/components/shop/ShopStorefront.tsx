import { Fragment, type ReactNode } from "react";
import type { PublicProduct, PublicShop } from "../../services/api";
import { isShopSectionAvailable } from "./sections/availability";
import { SHOP_BLOCK_COMPONENTS, SHOP_SECTION_COMPONENTS } from "./sections/registry";
import { isNewBlockType } from "../../theme/blockContent";
import { isSectionKey } from "../../theme/shopTheme";
import type { ShopCatalogControls } from "./sections/types";
import ShopThemeShell, { resolveStorefrontTheme } from "./ShopThemeShell";
import { hasPromoBanners } from "./promoTargets";
import { ShopCouponStripPreview } from "./ShopCouponStrip";
import ShopBlockShell from "./ShopBlockShell";
import { blockSection, blockSectionConfig } from "./blockLayout";
import TsfHome from "./general/TsfHome";
import { TsfMobileTabBar } from "./general/GeneralStorefront";
import { usesTsfTree } from "../../theme/shopTheme";

export { resolveStorefrontTheme };

const PREVIEW_CATEGORY_NAMES = new Map<number, string>();

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
  /** Preview only: category id -> name map (cards show the category label like the public page). */
  categoryNames?: Map<number, string>;
  /** Preview only: cap for the "All products" grid (the public page infinite-scrolls). */
  allProductsLimit?: number;
};

type ShopSectionsProps = {
  shop: PublicShop;
  products: PublicProduct[];
  catalog: ShopCatalogControls;
  preview?: boolean;
  /**
   * Storefront-only coupon strip (ShopPage). Inserted after BANNERS when banners render, else after HERO,
   * else first. The theme editor preview passes ShopCouponStripPreview (sample tickets, collect disabled).
   */
  couponStrip?: ReactNode;
};

/** Enabled theme sections only — Fragment so `.shop-mall-tabs` stays a direct child of `.skeleton-busy`. */
export function ShopSections({ shop, products, catalog, preview = false, couponStrip }: ShopSectionsProps) {
  const { config, templateKey } = resolveStorefrontTheme(shop);
  // AG3: CUSTOM themes with layout.blocks render the blocks; every other theme keeps the section path below.
  const blocks = templateKey === "CUSTOM" ? config.layout?.blocks ?? [] : [];
  if (blocks.length > 0) {
    const visibleBlocks = blocks.filter((block) => block.enabled && isShopSectionAvailable(block.type));
    const anchorBlock = couponStrip
      ? (hasPromoBanners(shop) ? visibleBlocks.find((block) => block.type === "BANNERS") : undefined)
        ?? visibleBlocks.find((block) => block.type === "HERO")
      : undefined;
    return (
      <>
        {couponStrip && !anchorBlock && <Fragment key="coupon-strip">{couponStrip}</Fragment>}
        {visibleBlocks.map((block) => {
          // AG4: STEPS / FEATURE / GALLERY / SOCIAL / RICH_TEXT are block-only; the ten legacy types keep the section path.
          const ContentComponent = isNewBlockType(block.type) ? SHOP_BLOCK_COMPONENTS[block.type] : null;
          const SectionComponent = isSectionKey(block.type) ? SHOP_SECTION_COMPONENTS[block.type] : null;
          return (
            <Fragment key={block.id}>
              <ShopBlockShell block={block}>
                {ContentComponent ? (
                  <ContentComponent shop={shop} config={config} block={block} preview={preview} />
                ) : SectionComponent ? (
                  <SectionComponent
                    shop={shop}
                    config={blockSectionConfig(block, config)}
                    section={blockSection(block)}
                    products={products}
                    catalog={catalog}
                    preview={preview}
                  />
                ) : null}
              </ShopBlockShell>
              {couponStrip && anchorBlock?.id === block.id && couponStrip}
            </Fragment>
          );
        })}
      </>
    );
  }
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
  categoryNames,
  allProductsLimit,
}: ShopStorefrontProps) {
  const theme = resolveStorefrontTheme(shop);
  // Preview parity: tsf templates render the exact same home body as the public ShopPage (TsfHome).
  const tsf = usesTsfTree(theme.templateKey);
  const preview = previewWidth != null;
  // Same chrome as ShopLayout for GENERAL/FASHION: bottom tab bar on mobile + tablet (hidden ≥1024 via @container).
  const after = (
    <>
      {children}
      {preview && tsf ? <TsfMobileTabBar shop={shop} onNotify={() => {}} /> : null}
    </>
  );
  return (
    <ShopThemeShell shop={shop} header={header} refetching={refetching} previewWidth={previewWidth} after={after}>
      {preview && tsf ? (
        <TsfHome slug={shop.slug} shop={shop} theme={theme} products={products} catalog={catalog}
          allProducts={{ items: allProductsLimit != null ? products.slice(0, allProductsLimit) : products, hasMore: false }}
          categoryNames={categoryNames ?? PREVIEW_CATEGORY_NAMES} couponStrip={<ShopCouponStripPreview />} />
      ) : <ShopSections
        shop={shop}
        products={products}
        catalog={catalog}
        preview={preview}
        couponStrip={preview ? <ShopCouponStripPreview /> : undefined}
      />}
    </ShopThemeShell>
  );
}
