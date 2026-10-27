import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { isLocalizedEmpty, localizedText, safeBlockLink, type BlockCta, type LocalizedString } from "../../../theme/blockContent";
import type { LayoutBlock } from "../../../theme/shopTheme";

/** Returns a resolver for `{th, en}` strings in the active UI language (Thai is always the fallback). */
export function useLocalized() {
  const { i18n } = useTranslation();
  return (value: LocalizedString | undefined) => localizedText(value, i18n.language);
}

/**
 * Root classes of an AG4 content section: the shared panel, the section's own class and the background modifier
 * (surface / primary tint / gradient reuse the AG1 `shop-sec-bg-*` names; padding and alignment come from the
 * ShopBlockShell wrapper classes). IMAGE backgrounds are painted by the shell, so no modifier is added.
 */
export function panelClasses(block: LayoutBlock, own: string, ...extra: string[]): string {
  const background = block.settings.background;
  const bg = background === "SURFACE" || background === "PRIMARY" || background === "GRADIENT"
    ? `shop-sec-bg-${background.toLowerCase()}`
    : "";
  return ["shop-ag4-panel", own, "recommend-width", bg, ...extra].filter(Boolean).join(" ");
}

type BlockLinkProps = { link?: string; className?: string; children: ReactNode; ariaLabel?: string };

/** Internal paths use the router; https links open in a new tab with noopener. Anything else renders nothing. */
export function BlockLink({ link, className, children, ariaLabel }: BlockLinkProps) {
  const target = safeBlockLink(link);
  if (!target) return null;
  if (target.external) {
    return <a className={className} href={target.href} target="_blank" rel="noopener noreferrer" aria-label={ariaLabel}>{children}</a>;
  }
  return <Link className={className} to={target.href} aria-label={ariaLabel}>{children}</Link>;
}

/** Optional call-to-action button; hidden when the label or the link is missing / unsafe. */
export function BlockCtaButton({ cta, className }: { cta?: BlockCta; className: string }) {
  const l = useLocalized();
  if (!cta || isLocalizedEmpty(cta.label) || !safeBlockLink(cta.link)) return null;
  return <BlockLink link={cta.link} className={className}>{l(cta.label)}</BlockLink>;
}

/** Section title / subtitle; renders nothing when both are empty. */
export function BlockHeading({ title, subtitle, prefix }: { title?: LocalizedString; subtitle?: LocalizedString; prefix: string }) {
  const l = useLocalized();
  const heading = l(title);
  const sub = l(subtitle);
  if (!heading && !sub) return null;
  return (
    <header className={`${prefix}-head`}>
      {heading ? <h2>{heading}</h2> : null}
      {sub ? <p>{sub}</p> : null}
    </header>
  );
}
