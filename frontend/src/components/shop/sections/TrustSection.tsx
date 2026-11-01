import { BadgeCheck, PackageCheck, RefreshCw, ShieldCheck } from "lucide-react";
import { usesTsfTree } from "../../../theme/shopTheme";
import { useRevealOnScroll } from "../../../hooks/useRevealOnScroll";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { resolveStorefrontTheme } from "../ShopThemeShell";
import type { ShopSectionProps } from "./types";

const ICONS = {
  AUTHENTIC: BadgeCheck,
  FAST_SHIP: PackageCheck,
  RETURNS: RefreshCw,
  SECURE_PAY: ShieldCheck,
  CUSTOM: ShieldCheck,
} as const;

export default function TrustSection({ shop, config }: ShopSectionProps) {
  const revealRef = useRevealOnScroll<HTMLElement>();
  const { tp } = usePublicTranslation();
  // GENERAL (themes spec): grey cards with a bold title + a small grey sub-line for the 4 standard promises.
  const general = usesTsfTree(resolveStorefrontTheme(shop).templateKey);
  const trust = config.trust;
  if (!trust?.enabled) return null;
  const items = (trust.items || []).filter((item) => item.enabled && item.label?.trim());
  if (!items.length) return null;

  return (
    <section ref={revealRef} className="shop-trust recommend-width shop-reveal" aria-label="trust">
      <ul className="shop-trust-list">
        {items.map((item, index) => {
          const Icon = ICONS[item.icon as keyof typeof ICONS] || ShieldCheck;
          return (
            <li key={`${item.icon}-${index}`}>
              <Icon aria-hidden="true" />
              {general ? (
                <span className="shop-trust-text">
                  <b>{item.label}</b>
                  {item.icon !== "CUSTOM" && item.icon in ICONS ? <small>{tp(`shop.general.trustSub.${item.icon}`)}</small> : null}
                </span>
              ) : (
                <span>{item.label}</span>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
