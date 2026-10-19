import { BadgeCheck, PackageCheck, RefreshCw, ShieldCheck } from "lucide-react";
import { useRevealOnScroll } from "../../../hooks/useRevealOnScroll";
import type { ShopSectionProps } from "./types";

const ICONS = {
  AUTHENTIC: BadgeCheck,
  FAST_SHIP: PackageCheck,
  RETURNS: RefreshCw,
  SECURE_PAY: ShieldCheck,
  CUSTOM: ShieldCheck,
} as const;

export default function TrustSection({ config }: ShopSectionProps) {
  const revealRef = useRevealOnScroll<HTMLElement>();
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
              <span>{item.label}</span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
