import { useEffect, useMemo, type CSSProperties, type MouseEvent, type ReactNode } from "react";
import { SkeletonBusy } from "../feedback/Skeleton";
import type { PublicProduct, PublicShop } from "../../services/api";
import {
  ensureThemeFontLink,
  normalizeTheme,
  themeCssVars,
  type ShopTheme,
} from "../../theme/shopTheme";
import { SHOP_SECTION_COMPONENTS } from "./sections/registry";
import type { ShopCatalogControls } from "./sections/types";
import "../../styles/recommended-products.css";
import "../../styles/shop-page.css";
import "../../styles/shop-variants/hero-strip.css";
import "../../styles/shop-variants/products-large.css";
import "../../styles/shop-variants/products-menu.css";
import "../../styles/shop-mall.css";

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

/** Resolve the theme a storefront should render; shops without a saved theme keep the original page. */
export function resolveStorefrontTheme(shop: PublicShop) {
  const theme = normalizeTheme(shop.theme as Partial<ShopTheme> | null | undefined);
  if (shop.theme) return theme;
  return {
    ...theme,
    config: {
      ...theme.config,
      sections: theme.config.sections.map((section) =>
        section.key === "FEATURED" || section.key === "HOURS" ? { ...section, enabled: false } : section,
      ),
    },
  };
}

export default function ShopStorefront({ shop, products, catalog, header, children, refetching = false, previewWidth }: ShopStorefrontProps) {
  const theme = useMemo(() => resolveStorefrontTheme(shop), [shop]);
  const { config, templateKey } = theme;

  useEffect(() => {
    ensureThemeFontLink(config.font);
  }, [config.font]);

  const preview = previewWidth != null;
  const classes = ["recommend-page", "shop-directory", "shop-themed", "shop-storefront-cq"];
  if (preview) classes.push("shop-preview-frame");

  const style = {
    ...themeCssVars(config),
    ...(preview ? { maxWidth: previewWidth, width: "100%" } : {}),
  } as CSSProperties;

  // Links inside the merchant preview must not navigate away from the editor.
  const blockNavigation = preview
    ? (event: MouseEvent<HTMLDivElement>) => {
        if ((event.target as HTMLElement).closest("a")) event.preventDefault();
      }
    : undefined;

  return (
    <div className={classes.join(" ")} style={style} data-template={templateKey} onClickCapture={blockNavigation}>
      {header}
      <SkeletonBusy refetching={refetching} busy={refetching}>
        {config.sections
          .filter((section) => section.enabled)
          .map((section) => {
            const Component = SHOP_SECTION_COMPONENTS[section.key];
            return (
              <Component
                key={section.key}
                shop={shop}
                config={config}
                section={section}
                products={products}
                catalog={catalog}
                preview={preview}
              />
            );
          })}
      </SkeletonBusy>
      {children}
    </div>
  );
}
