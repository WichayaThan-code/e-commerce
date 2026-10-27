import { useEffect, useMemo, type CSSProperties, type MouseEvent, type ReactNode } from "react";
import { SkeletonBusy } from "../feedback/Skeleton";
import type { PublicShop } from "../../services/api";
import { customFontFaces, customFontVars } from "../../theme/customFonts";
import ShopCustomHeader from "./ShopCustomHeader";
import ShopCustomFooter from "./ShopCustomFooter";
import {
  ensureThemeFontLink,
  normalizeTheme,
  themeCssVars,
  type ShopTheme,
} from "../../theme/shopTheme";
import "../../styles/recommended-products.css";
import "../../styles/shop-page.css";
import "../../styles/shop-variants/hero-strip.css";
import "../../styles/shop-variants/products-large.css";
import "../../styles/shop-variants/products-menu.css";
import "../../styles/shop-mall.css";
import "../../styles/shop-chrome.css";

type ShopThemeShellProps = {
  shop: PublicShop;
  header?: ReactNode;
  children: ReactNode;
  after?: ReactNode;
  refetching?: boolean;
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

export default function ShopThemeShell({
  shop,
  header,
  children,
  after,
  refetching = false,
  previewWidth,
}: ShopThemeShellProps) {
  const theme = useMemo(() => resolveStorefrontTheme(shop), [shop]);
  const { config, templateKey } = theme;

  useEffect(() => {
    ensureThemeFontLink(config.font);
  }, [config.font]);

  const preview = previewWidth != null;
  const classes = ["recommend-page", "shop-directory", "shop-themed", "shop-storefront-cq"];
  if (preview) classes.push("shop-preview-frame");

  const custom = templateKey === "CUSTOM";
  // AG5: generated @font-face from our own public media URLs only (no merchant CSS); absent/failed -> legacy `font` stack.
  const customFonts = useMemo(() => (custom ? customFontFaces(shop.slug, config) : null), [custom, shop.slug, config]);

  const style = {
    ...themeCssVars(config, templateKey),
    ...(custom ? customFontVars(shop.slug, config) : {}),
    ...(preview ? { maxWidth: previewWidth, width: "100%" } : {}),
  } as CSSProperties;

  const blockNavigation = preview
    ? (event: MouseEvent<HTMLDivElement>) => {
        if ((event.target as HTMLElement).closest("a")) event.preventDefault();
      }
    : undefined;

  return (
    <div
      className={classes.join(" ")}
      style={style}
      data-template={templateKey}
      data-heading-font={customFonts?.heading ? "custom" : undefined}
      onClickCapture={blockNavigation}
    >
      {customFonts ? <style>{customFonts.css}</style> : null}
      {custom && config.header ? <ShopCustomHeader shop={shop} header={config.header} preview={preview} /> : null}
      {header}
      <SkeletonBusy refetching={refetching} busy={refetching}>
        {children}
      </SkeletonBusy>
      {custom && config.footer ? <ShopCustomFooter shop={shop} footer={config.footer} /> : null}
      {after}
    </div>
  );
}
