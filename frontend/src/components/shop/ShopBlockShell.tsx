import type { CSSProperties, ReactNode } from "react";
import type { LayoutBlock } from "../../theme/shopTheme";
import { blockGradientVar, blockUsesBox, safeBackgroundUrl } from "./blockLayout";

type ShopBlockShellProps = {
  block: LayoutBlock;
  children: ReactNode;
};

/**
 * AG3 wrapper around one CUSTOM layout block. Styling lives in shop-mall.css under
 * `.shop-themed[data-template="CUSTOM"]` (the storefront only renders blocks for CUSTOM themes).
 *
 * - Plain blocks use `display: contents`, so sections keep the exact flex-child layout (and sticky tabs) they had
 *   before blocks existed. Surface/primary/gradient backgrounds, alignment, padding and columns are applied by the
 *   section itself via blockSectionConfig.
 * - IMAGE backgrounds and full-width blocks get a real box.
 */
export default function ShopBlockShell({ block, children }: ShopBlockShellProps) {
  const { settings } = block;
  const image = settings.background === "IMAGE" ? safeBackgroundUrl(settings.backgroundUrl) : null;
  const gradient = blockGradientVar(block);
  const classes = [
    "shop-block",
    `shop-block-type-${block.type.toLowerCase().replace(/_/g, "-")}`,
    blockUsesBox(block) ? "shop-block-boxed" : "shop-block-contents",
    `shop-block-pad-${settings.paddingY.toLowerCase()}`,
    settings.align === "CENTER" ? "shop-block-align-center" : "",
    settings.fullWidth ? "shop-block-fullwidth" : "",
    image ? "shop-block-bg-image" : "",
  ].filter(Boolean).join(" ");

  const style: Record<string, string> = {};
  if (gradient) style["--shop-gradient-section"] = gradient;
  if (image) {
    style.backgroundImage = `url("${image}")`;
    style["--shop-block-overlay"] = String((settings.overlay ?? 0) / 100);
  }

  return (
    <div className={classes} style={style as CSSProperties} data-block-id={block.id}>
      {children}
    </div>
  );
}
