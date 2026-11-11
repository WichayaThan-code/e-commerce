import { useEffect, useMemo, type CSSProperties, type MouseEvent, type ReactNode } from "react";
import { SkeletonBusy } from "../feedback/Skeleton";
import type { PublicShop } from "../../services/api";
import { customFontFaces, customFontVars } from "../../theme/customFonts";
import ShopCustomHeader from "./ShopCustomHeader";
import ShopCustomFooter from "./ShopCustomFooter";
import { TsfAnnouncementBar, TsfStoreFooter, TsfStoreHeader } from "./general/GeneralStorefront";
import {
  ensureThemeFontLink,
  normalizeTheme,
  resolvedProductCardGap,
  resolvedProductCardRadius,
  themeCssVars,
  tsfThemeDefaults,
  tsfThemeName,
  usesTsfTree,
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
    if (config.fontBody) ensureThemeFontLink(config.fontBody);
  }, [config.font, config.fontBody]);

  const preview = previewWidth != null;
  const classes = ["recommend-page", "shop-directory", "shop-themed", "shop-storefront-cq"];
  if (preview) classes.push("shop-preview-frame");
  if (usesTsfTree(templateKey)) classes.push("tsf-store");

  const custom = templateKey === "CUSTOM";
  const general = usesTsfTree(templateKey);
  const tsfDefaults = tsfThemeDefaults(templateKey);
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

  const chrome = (
    <>
      {customFonts ? <style>{customFonts.css}</style> : null}
      {custom && config.header ? <ShopCustomHeader shop={shop} header={config.header} preview={preview} /> : null}
      {general ? <TsfAnnouncementBar config={config} /> : null}
      {general ? <TsfStoreHeader shop={shop} config={config} /> : null}
      {header}
      <SkeletonBusy refetching={refetching} busy={refetching}>
        {children}
      </SkeletonBusy>
      {custom && config.footer ? <ShopCustomFooter shop={shop} footer={config.footer} /> : null}
      {general ? <TsfStoreFooter shop={shop} /> : null}
    </>
  );

  return (
    <div
      className={classes.join(" ")}
      style={style}
      data-template={templateKey}
      data-theme={tsfThemeName(templateKey)}
      data-btn={general ? (config.style?.buttonShape ?? "ROUNDED") : undefined}
      data-card={general ? (config.style?.cardStyle ?? "BORDERED") : undefined}
      data-card-radius={resolvedProductCardRadius(theme)}
      data-card-gap={resolvedProductCardGap(theme)}
      data-ratio={general ? (config.productCard?.imageRatio ?? tsfDefaults.ratio) : undefined}
      data-heading-font={customFonts?.heading || (custom && config.fontBody) ? "custom" : undefined}
      onClickCapture={blockNavigation}
    >
      {/* Preview: scroll body + pin after (tab bar) like the live mobile viewport. Live store keeps flat DOM. */}
      {preview ? <div className="shop-preview-scroll">{chrome}</div> : chrome}
      {after}
    </div>
  );
}
