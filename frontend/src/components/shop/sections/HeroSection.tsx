import { Link } from "react-router-dom";
import { BadgeCheck, MessageCircle, UserPlus } from "lucide-react";
import ShopLogo from "../../ShopLogo";
import ShopTabs from "../ShopTabs";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import recommendReference from "../../../assets/images/references/storefront/recommend-product.png";
import { mediaUrl } from "../../../services/api";
import { useRevealOnScroll } from "../../../hooks/useRevealOnScroll";
import type { ShopSectionProps } from "./types";

function ctaTarget(link: string): { href: string; external: boolean } | null {
  const value = link.trim();
  if (!value) return null;
  if (value.startsWith("/") && !value.startsWith("//")) return { href: value, external: false };
  if (/^https:\/\//i.test(value)) return { href: value, external: true };
  return null;
}

function formatJoined(iso: string | null | undefined, locale: string) {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  const tag = locale.startsWith("en") ? "en-US" : locale.startsWith("zh") ? "zh-CN" : "th-TH";
  return new Intl.DateTimeFormat(tag, { year: "numeric", month: "short" }).format(date);
}

export default function HeroSection({ shop, config, catalog, preview }: ShopSectionProps) {
  const { tp, localeTag } = usePublicTranslation();
  const revealRef = useRevealOnScroll<HTMLElement>();
  const { hero } = config;
  const title = hero.title.trim() || shop.name;
  const subtitle = hero.subtitle.trim() || shop.description || "";
  const cta = hero.ctaLabel.trim() ? ctaTarget(hero.ctaLink) : null;
  const cover = shop.coverUrl ? mediaUrl(shop.coverUrl) : recommendReference;
  const productCount = shop.productCount ?? (catalog.total > 0 ? catalog.total : null);
  const joined = formatJoined(shop.createdAt, localeTag);

  const stats: { label: string; value: string; mono?: boolean }[] = [];
  if (productCount != null) {
    stats.push({ label: tp("shop.view.productCount"), value: String(productCount), mono: true });
  }
  if (shop.province) stats.push({ label: tp("common.area"), value: shop.province });
  if (joined) stats.push({ label: tp("shop.joinedAt"), value: joined, mono: true });

  return (
    <>
      <section id="shop-home" ref={revealRef} className="shop-mall-hero recommend-width shop-reveal" data-variant="MALL">
        <div className="shop-mall-hero-cover" style={{ backgroundImage: `url(${cover})` }} aria-hidden="true" />
        <div className="shop-mall-hero-shade" aria-hidden="true" />
        <div className="shop-mall-hero-body">
          <div className="shop-mall-hero-brand">
            <div className="shop-mall-logo">
              <ShopLogo src={shop.logoUrl} name={shop.name} />
            </div>
            <div className="shop-mall-hero-copy">
              <div className="shop-mall-title-row">
                <h1>{title}</h1>
                <span className="shop-official-badge"><BadgeCheck />{tp("shop.mallBadge")}</span>
              </div>
              {shop.nameEn && hero.title.trim() === "" ? <p className="shop-profile-en">{shop.nameEn}</p> : null}
              {subtitle ? <p className="shop-mall-sub">{subtitle}</p> : null}
              {shop.category ? <span className="shop-chip">{shop.category}</span> : null}
            </div>
          </div>
          {stats.length > 0 && (
            <ul className="shop-mall-stats">
              {stats.map((stat) => (
                <li key={stat.label}>
                  <b className={stat.mono ? "num" : undefined}>{stat.value}</b>
                  <span>{stat.label}</span>
                </li>
              ))}
            </ul>
          )}
          <div className="shop-mall-actions">
            <button type="button" className="shop-mall-btn is-primary" onClick={() => catalog.onNotify(tp("shop.view.soon"))}>
              <UserPlus />{tp("shop.follow")}
            </button>
            <button type="button" className="shop-mall-btn is-outline" onClick={() => catalog.onNotify(tp("shop.view.soon"))}>
              <MessageCircle />{tp("shop.chat")}
            </button>
            {cta ? (
              cta.external ? (
                <a className="shop-mall-btn is-ghost" href={cta.href} target="_blank" rel="noopener noreferrer">{hero.ctaLabel.trim()}</a>
              ) : (
                <Link className="shop-mall-btn is-ghost" to={cta.href}>{hero.ctaLabel.trim()}</Link>
              )
            ) : null}
          </div>
        </div>
      </section>
      <ShopTabs shop={shop} preview={preview} onNotify={catalog.onNotify} />
    </>
  );
}
