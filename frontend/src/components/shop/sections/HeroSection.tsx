import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { BadgeCheck, MessageCircle, UserPlus } from "lucide-react";
import ShopLogo from "../../ShopLogo";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import recommendReference from "../../../assets/images/references/storefront/recommend-product.png";
import { mediaUrl } from "../../../services/api";
import { useRevealOnScroll } from "../../../hooks/useRevealOnScroll";
import type { ShopSectionProps } from "./types";

const TAB_TARGETS = [
  { id: "shop-home", key: "home" as const },
  { id: "shop-catalog", key: "products" as const },
  { id: "shop-categories", key: "categories" as const },
  { id: "shop-banners", key: "promos" as const },
];

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

export default function HeroSection({ shop, config, catalog }: ShopSectionProps) {
  const { tp, localeTag } = usePublicTranslation();
  const revealRef = useRevealOnScroll<HTMLElement>();
  const tabsRef = useRef<HTMLElement>(null);
  const [activeTab, setActiveTab] = useState<(typeof TAB_TARGETS)[number]["key"]>("home");
  const { hero } = config;
  const title = hero.title.trim() || shop.name;
  const subtitle = hero.subtitle.trim() || shop.description || "";
  const cta = hero.ctaLabel.trim() ? ctaTarget(hero.ctaLink) : null;
  const cover = shop.coverUrl ? mediaUrl(shop.coverUrl) : recommendReference;
  const productCount = shop.productCount ?? (catalog.total > 0 ? catalog.total : null);
  const joined = formatJoined(shop.createdAt, localeTag);

  useEffect(() => {
    const tabs = tabsRef.current;
    if (!tabs) return;
    const root = tabs.closest(".shop-storefront-cq") as HTMLElement | null;
    const apply = () => {
      const raw = Math.round(tabs.getBoundingClientRect().height);
      // Cap — sticky flex-stretch can report page-tall height; never feed that back into --shop-tabs-h.
      const height = raw > 56 ? 44 : Math.min(56, Math.max(44, raw));
      if (root) root.style.setProperty("--shop-tabs-h", `${height}px`);
    };
    apply();
    const observer = typeof ResizeObserver !== "undefined" ? new ResizeObserver(apply) : null;
    observer?.observe(tabs);
    return () => observer?.disconnect();
  }, []);

  useEffect(() => {
    const nodes = TAB_TARGETS
      .map((tab) => ({ tab, el: document.getElementById(tab.id) }))
      .filter((row): row is { tab: (typeof TAB_TARGETS)[number]; el: HTMLElement } => Boolean(row.el));
    if (!nodes.length || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        if (!visible?.target?.id) return;
        const match = TAB_TARGETS.find((tab) => tab.id === visible.target.id);
        if (match) setActiveTab(match.key);
      },
      { rootMargin: "-20% 0px -55% 0px", threshold: [0.08, 0.2, 0.4] },
    );
    nodes.forEach(({ el }) => observer.observe(el));
    return () => observer.disconnect();
  }, []);

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
      <nav ref={tabsRef} className="shop-mall-tabs recommend-width" aria-label={tp("shop.tabsAria")}>
        {TAB_TARGETS.map((tab) => (
          <a
            key={tab.id}
            href={`#${tab.id}`}
            className={activeTab === tab.key ? "is-active" : undefined}
            onClick={() => setActiveTab(tab.key)}
          >
            {tp(`shop.tabs.${tab.key}`)}
          </a>
        ))}
      </nav>
    </>
  );
}
