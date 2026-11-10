/**
 * GENERAL storefront tree — a port of the ThaiShopFun Themes artifact components (General template),
 * wired to real public data only (PublicShop, theme config, public products / categories, cart).
 * Never reads merchant APIs, never renders shop HTML, never shows the owner's phone / email.
 */
import { tsfThemeDefaults } from "../../../theme/shopTheme";
import { useState, type FormEvent, type MouseEvent, type ReactNode } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { Package } from "lucide-react";
import EmptyState from "../../feedback/EmptyState";
import { useAuth } from "../../../contexts/AuthContext";
import { loginPathForCart, useOptionalCart } from "../../../contexts/CartContext";
import { cartErrorMessage, isOwnShop } from "../../cart/cartFormat";
import HeroSlider, { heroSlideUrls } from "../sections/HeroSlider";
import { categoryIcon } from "../categoryIcons";
import { todayHours } from "../shopHours";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { useShopCategories } from "../../../hooks/useShopCategories";
import { mediaUrl, type PublicProduct, type PublicShop } from "../../../services/api";
import { localizedText } from "../../../services/catalogText";
import {
  shopCategoriesPath,
  shopCategoryPath,
  shopContactPath,
  shopPath,
  shopPoliciesPath, shopFavoritesPath,
  shopProductPath,
  shopProductsPath,
  shopSearchPath,
} from "../../../services/shopRoutes";
import type { ThemeConfig } from "../../../theme/shopTheme";
import { parseShopSortKey, type ShopCatalogControls } from "../sections/types";
import "../../../styles/tsf-general.css";

const ICON_PATHS: Record<string, string> = {
  search: "M11 4a7 7 0 1 0 4.4 12.4l4.1 4.1 1.4-1.4-4.1-4.1A7 7 0 0 0 11 4Zm0 2a5 5 0 1 1 0 10 5 5 0 0 1 0-10Z",
  bag: "M7 8V7a5 5 0 0 1 10 0v1h3l-1 13H5L4 8h3Zm2 0h6V7a3 3 0 0 0-6 0v1Zm-2.9 2 .7 9h10.4l.7-9H6.1Z",
  menu: "M3 6h18v2H3V6Zm0 5h18v2H3v-2Zm0 5h18v2H3v-2Z",
  heart: "M12 20.3 4.6 13a4.8 4.8 0 0 1 6.8-6.8l.6.6.6-.6a4.8 4.8 0 1 1 6.8 6.8L12 20.3Zm0-2.8 6-6a2.8 2.8 0 0 0-4-4L12 9.6 10 7.5a2.8 2.8 0 0 0-4 4l6 6Z",
  user: "M12 12a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9Zm0-2a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Zm-8 11a8 8 0 0 1 16 0h-2a6 6 0 0 0-12 0H4Z",
  home: "M12 3 2 11.5l1.3 1.5L5 11.6V21h6v-6h2v6h6v-9.4l1.7 1.4 1.3-1.5L12 3Zm5 16h-2v-6H9v6H7V9.9l5-4.2 5 4.2V19Z",
  grid: "M4 4h7v7H4V4Zm2 2v3h3V6H6Zm7-2h7v7h-7V4Zm2 2v3h3V6h-3ZM4 13h7v7H4v-7Zm2 2v3h3v-3H6Zm7-2h7v7h-7v-7Zm2 2v3h3v-3h-3Z",
  truck: "M2 5h12v3h4l4 5v5h-2.2a3 3 0 0 1-5.6 0H9.8a3 3 0 0 1-5.6 0H2V5Zm2 2v9h.2a3 3 0 0 1 5.6 0H12V7H4Zm10 3v6h.2a3 3 0 0 1 5.6 0h.2v-2.3L17 10h-3ZM7 17a1 1 0 1 0 0 2 1 1 0 0 0 0-2Zm10 0a1 1 0 1 0 0 2 1 1 0 0 0 0-2Z",
  shield: "M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3Zm0 2.1 6 2.3V11c0 3.9-2.5 7.4-6 8.9-3.5-1.5-6-5-6-8.9V6.4l6-2.3Zm-1 10.5-2.3-2.3-1.4 1.4L11 17.4l5.7-5.7-1.4-1.4L11 14.6Z",
  refresh: "M12 4a8 8 0 0 1 7.4 5H22l-3.5 4L15 9h2.2A6 6 0 0 0 6 12H4a8 8 0 0 1 8-8Zm8 8a8 8 0 0 1-15.4 3H2l3.5-4L9 15H6.8A6 6 0 0 0 18 12h2Z",
  star: "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9L12 3Z",
  check: "M9.5 16.2 5.3 12l-1.4 1.4 5.6 5.6L20.1 8.4 18.7 7 9.5 16.2Z",
  clock: "M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20Zm0 2a8 8 0 1 0 0 16 8 8 0 0 0 0-16Zm1 3v4.6l3.2 3.2-1.4 1.4L11 12.4V7h2Z",
  pin: "M12 2a7 7 0 0 1 7 7c0 5-7 13-7 13S5 14 5 9a7 7 0 0 1 7-7Zm0 4.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5Z",
  close: "M6.4 5 5 6.4 10.6 12 5 17.6 6.4 19l5.6-5.6 5.6 5.6 1.4-1.4-5.6-5.6L19 6.4 17.6 5 12 10.6 6.4 5Z",
};

export function TsfIcon({ name, size = 20 }: { name: string; size?: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="currentColor" aria-hidden="true" focusable="false">
      <path d={ICON_PATHS[name] ?? ""} />
    </svg>
  );
}

type ButtonVariant = "primary" | "secondary" | "accent" | "inverse" | "ghost-inverse" | "link";
export function TsfButton({ variant = "primary", size, to, href, children, className, onClick, disabled }: {
  variant?: ButtonVariant; size?: "s" | "l"; to?: string; href?: string; children: ReactNode; className?: string; onClick?: () => void; disabled?: boolean;
}) {
  const cls = ["tsf-btn", `tsf-btn-${variant}`, size ? `tsf-btn-${size}` : "", className ?? ""].filter(Boolean).join(" ");
  if (to) return <Link className={cls} to={to}>{children}</Link>;
  if (href) return <a className={cls} href={href} target="_blank" rel="noopener noreferrer">{children}</a>;
  return <button type="button" className={cls} onClick={onClick} disabled={disabled}>{children}</button>;
}

export function TsfBadge({ tone = "neutral", children }: { tone?: "sale" | "new" | "ink" | "neutral"; children: ReactNode }) {
  return <span className={`tsf-badge tsf-badge-${tone}`}>{children}</span>;
}

/** Artifact ImageSlot motifs (viewBox 0 0 100 100), used as placeholders when there is no image. */
const MOTIFS: Record<string, ReactNode> = {
  bag: <><path d="M25 38 H75 L70 86 H30 Z" /><path d="M38 40 Q38 20 50 20 Q62 20 62 40" fill="none" stroke="currentColor" strokeWidth={5} /></>,
  tee: <path d="M30 22 L42 16 Q50 25 58 16 L70 22 L84 37 L73 46 L68 41 L68 84 L32 84 L32 41 L27 46 L16 37 Z" />,
  bowl: <><path d="M14 50 H86 Q84 82 50 84 Q16 82 14 50 Z" /><path d="M38 40 Q34 32 40 24 M50 40 Q46 30 52 20 M62 40 Q58 32 64 24" fill="none" stroke="currentColor" strokeWidth={4} strokeLinecap="round" /></>,
  jar: <path d="M34 18 H66 V28 H34 Z M30 32 H70 Q75 32 75 38 V80 Q75 87 68 87 H32 Q25 87 25 80 V38 Q25 32 30 32 Z" />,
  cup: <><path d="M31 28 H69 L63 88 H37 Z" /><path d="M54 28 L61 8" fill="none" stroke="currentColor" strokeWidth={5} strokeLinecap="round" /></>,
  sneaker: <path d="M10 64 Q12 50 24 48 L40 40 Q46 46 56 46 L72 54 Q90 58 90 68 L90 74 H10 Z" />,
  sack: <><path d="M30 32 Q50 24 70 32 L77 80 Q50 92 23 80 Z" /><path d="M38 22 Q50 30 62 22" fill="none" stroke="currentColor" strokeWidth={5} /></>,
  cap: <path d="M18 62 Q20 32 50 30 Q77 32 79 57 L94 64 Q60 71 18 62 Z" />,
  dessert: <><rect x={22} y={46} width={56} height={12} rx={3} /><rect x={22} y={60} width={56} height={12} rx={3} /><rect x={22} y={74} width={56} height={10} rx={3} /><circle cx={50} cy={36} r={7} /></>,
  cloth: <><rect x={18} y={22} width={64} height={10} /><rect x={18} y={40} width={64} height={10} /><rect x={18} y={58} width={64} height={10} /><rect x={18} y={76} width={64} height={6} /></>,
};

export function TsfImageSlot({ src, alt, tone, className, ratio, motif, scale = 0.62 }: { src?: string | null; alt?: string; tone?: string; className?: string; ratio?: string; motif?: string; scale?: number }) {
  const shapes = motif ? MOTIFS[motif] : null;
  const off = (100 - 100 * scale) / 2;
  return (
    <div className={["tsf-img", tone ? `tsf-tone-${tone}` : "", className ?? ""].filter(Boolean).join(" ")} style={ratio ? { aspectRatio: ratio } : undefined}>
      {src ? <img src={src} alt={alt ?? ""} loading="lazy" /> : shapes ? (
        <svg className="tsf-motif" viewBox="0 0 100 100" fill="currentColor" aria-hidden="true" focusable="false">
          <g transform={`translate(${off} ${off}) scale(${scale})`}>{shapes}</g>
        </svg>
      ) : null}
    </div>
  );
}

function navLinks(slug: string) {
  return [
    { key: "home", to: shopPath(slug), end: true },
    { key: "products", to: shopProductsPath(slug), end: false },
    { key: "categories", to: shopCategoriesPath(slug), end: false },
    { key: "contact", to: shopContactPath(slug), end: false },
    { key: "policies", to: shopPoliciesPath(slug), end: false },
  ];
}

export function TsfAnnouncementBar({ config }: { config: ThemeConfig }) {
  const { tp } = usePublicTranslation();
  const custom = (config.announcement?.items ?? []).map((item) => item.trim()).filter(Boolean).slice(0, 2);
  const items = custom.length ? custom : config.trust?.enabled
    ? config.trust.items.filter((item) => item.enabled && item.label.trim()).map((item) => item.label.trim()).slice(0, 2)
    : [];
  if (!items.length) return null;
  return (
    <div className="tsf-announce" role="region" aria-label={tp("shop.general.announceAria")}>
      <div className="tsf-wrap">
        <span>{items[0]}</span>
        {items[1] ? <span className="tsf-announce-alt">{items[1]}</span> : null}
      </div>
    </div>
  );
}

function TsfLogo({ shop }: { shop: PublicShop }) {
  return (
    <Link className="tsf-logo" to={shopPath(shop.slug)} aria-label={shop.name}>
      {shop.logoUrl ? <img src={mediaUrl(shop.logoUrl)} alt="" /> : <span className="tsf-logo-mark" aria-hidden="true">{shop.name.slice(0, 1)}</span>}
      <span>{shop.name}</span>
    </Link>
  );
}

export function TsfStoreHeader({ shop, config }: { shop: PublicShop; config?: ThemeConfig }) {
  const { tp } = usePublicTranslation();
  const navigate = useNavigate();
  const cart = useOptionalCart();
  const count = cart?.count ?? 0;
  const [menuOpen, setMenuOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState("");
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!query.trim()) return;
    setSearchOpen(false);
    navigate(shopSearchPath(shop.slug, query.trim()));
  };
  return (
    <header className="tsf-header" data-layout={config?.brand?.logoPosition === "CENTER" ? "LOGO_CENTER" : "LOGO_LEFT"}>
      <div className="tsf-wrap tsf-header-row">
        <button type="button" className="tsf-icon-btn tsf-burger" aria-expanded={menuOpen} aria-label={menuOpen ? tp("shop.general.menuClose") : tp("shop.general.menuOpen")} onClick={() => setMenuOpen((v) => !v)}>
          <TsfIcon name={menuOpen ? "close" : "menu"} />
        </button>
        <TsfLogo shop={shop} />
        <nav className="tsf-nav" aria-label={tp("shop.chrome.menuAria")}>
          {navLinks(shop.slug).map((item) => (
            <NavLink key={item.key} to={item.to} end={item.end}>
              {tp(`shop.tabs.${item.key}`)}
            </NavLink>
          ))}
        </nav>
        <div className="tsf-header-tools">
          <form className="tsf-search-pill" role="search" onSubmit={submit}>
            <TsfIcon name="search" />
            <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder={tp("shop.chrome.searchPlaceholder")} aria-label={tp("shop.chrome.searchAria")} />
          </form>
          <button type="button" className="tsf-icon-btn tsf-header-search-mobile" aria-expanded={searchOpen} aria-label={tp("shop.general.searchOpen")} onClick={() => setSearchOpen((v) => !v)}>
            <TsfIcon name="search" />
          </button>
          <Link className="tsf-icon-btn" to="/account/orders" aria-label={tp("shop.general.account")}><TsfIcon name="user" /></Link>
          <Link className="tsf-icon-btn" to="/cart" aria-label={tp("shop.general.cart")}>
            <TsfIcon name="bag" />
            {count > 0 ? <span className="tsf-cart-count">{count > 99 ? "99+" : count}</span> : null}
          </Link>
        </div>
      </div>
      {searchOpen ? (
        <form className="tsf-wrap tsf-msearch" role="search" onSubmit={submit}>
          <input type="search" autoFocus value={query} onChange={(e) => setQuery(e.target.value)} placeholder={tp("shop.chrome.searchPlaceholder")} aria-label={tp("shop.chrome.searchAria")} />
          <TsfButton variant="primary" onClick={() => { if (query.trim()) { setSearchOpen(false); navigate(shopSearchPath(shop.slug, query.trim())); } }}>{tp("shop.chrome.searchSubmit")}</TsfButton>
        </form>
      ) : null}
      {menuOpen ? (
        <nav className="tsf-drawer" aria-label={tp("shop.chrome.menuAria")}>
          {navLinks(shop.slug).map((item) => (
            <NavLink key={item.key} to={item.to} end={item.end} onClick={() => setMenuOpen(false)}>{tp(`shop.tabs.${item.key}`)}</NavLink>
          ))}
        </nav>
      ) : null}
    </header>
  );
}

function ctaTarget(link: string | undefined): { to?: string; href?: string } | null {
  const value = (link ?? "").trim();
  if (!value) return null;
  if (value.startsWith("/") && !value.startsWith("//")) return { to: value };
  if (/^https:\/\//i.test(value)) return { href: value };
  return null;
}

/** `align` overrides the brand-level alignment (CUSTOM heroes use their own `hero.textAlign`). */
export function TsfHero({ shop, config, align: alignOverride }: { shop: PublicShop; config: ThemeConfig; align?: "LEFT" | "CENTER" }) {
  const align = alignOverride ?? (config.brand?.textAlign === "CENTER" ? "CENTER" : "LEFT");
  const { tp } = usePublicTranslation();
  const hero = config.hero;
  const title = hero.title.trim() || shop.name;
  const eyebrow = (hero.eyebrow ?? "").trim() || shop.category || "";
  const subtitle = hero.subtitle.trim() || shop.description || "";
  const primary = ctaTarget(hero.ctaLink) ?? { to: shopProductsPath(shop.slug) };
  const secondary = ctaTarget(hero.ctaSecondaryLink) ?? { to: shopCategoriesPath(shop.slug) };
  const layout = hero.layout === "STRIP" || hero.layout === "MEDIA_FULL" || hero.layout === "SPLIT" || hero.layout === "POSTER" ? hero.layout : "FULL";
  const badges = (hero.badges ?? []).map((badge) => badge.trim()).filter(Boolean).slice(0, 3);
  const strip = layout === "STRIP";
  const split = layout === "SPLIT";
  const onLight = strip || split;
  const copy = (
    <div className="tsf-hero-copy">
      {eyebrow ? <p className="tsf-eyebrow">{eyebrow}</p> : null}
      <h1 className="tsf-display tsf-hero-title">{title}</h1>
      {subtitle ? <p className="tsf-hero-sub">{subtitle}</p> : null}
      <div className="tsf-hero-ctas">
        <TsfButton size="l" variant={onLight ? "primary" : "inverse"} {...primary}>{hero.ctaLabel.trim() || tp("shop.general.ctaShop")}</TsfButton>
        <TsfButton size="l" variant={onLight ? "link" : "ghost-inverse"} {...secondary}>{(hero.ctaSecondaryLabel ?? "").trim() || tp("shop.general.ctaCategories")}</TsfButton>
      </div>
    </div>
  );
  const slides = heroSlideUrls(hero.slides);
  // Item 1: hero slides replace the old cover; coverUrl is only the last-resort fallback for shops without slides.
  const cover = slides[0] ?? (shop.coverUrl ? mediaUrl(shop.coverUrl) : null);
  const media = (
    <div className="tsf-hero-media">
      {slides.length ? (
        <div className={`tsf-img has-slider tsf-tone-${onLight ? "accent" : "ink"}`}><HeroSlider urls={slides} /></div>
      ) : (
        <TsfImageSlot src={cover} alt="" tone={onLight ? "accent" : "ink"} />
      )}
    </div>
  );
  // Artifact Hero: STRIP = copy on shop-secondary with the image beside (stacks on mobile); MEDIA_FULL = centred poster.
  // Item 2: SPLIT = copy on one side + media on the other on the page surface (stacks on mobile); POSTER = big centred
  // typography over the media (GENERAL badges as stickers). Works for GENERAL / FASHION / FOOD_BEVERAGE, slides included.
  if (split) {
    return (
      <section id="shop-home" className="tsf-hero tsf-hero-split" data-layout="SPLIT" data-align={align}>
        <div className="tsf-wrap">{copy}{media}</div>
      </section>
    );
  }
  if (strip) {
    return (
      <section id="shop-home" className="tsf-hero tsf-hero-strip" data-align={align}>
        <div className="tsf-wrap">{copy}{media}</div>
      </section>
    );
  }
  if (layout === "MEDIA_FULL" || layout === "POSTER") {
    return (
      <section id="shop-home" className={`tsf-hero tsf-hero-poster${layout === "POSTER" ? " tsf-hero-poster-type" : ""}`} data-layout={layout} data-align={align}>
        {media}
        {badges.length ? (
          <div className="tsf-hero-badges" aria-hidden="true">
            {badges.map((badge, index) => <span key={index} className="tsf-hero-badge" data-slot={index + 1}>{badge}</span>)}
          </div>
        ) : null}
        {copy}
      </section>
    );
  }
  return (
    <section id="shop-home" className="tsf-hero tsf-hero-full" data-align={align}>
      {media}
      <div className="tsf-wrap" style={{ position: "relative", zIndex: 1, width: "100%" }}>{copy}</div>
    </section>
  );
}

function TsfSection({ title, link, background, pad, id, children }: { title?: string; link?: { to: string; label: string }; background?: "SURFACE"; pad?: "COMPACT"; id?: string; children: ReactNode }) {
  return (
    <section className="tsf-section" id={id} data-bg={background ?? "NONE"} data-pad={pad ?? "NORMAL"} data-align="LEFT">
      <div className="tsf-wrap">
        {title ? (
          <div className="tsf-section-head">
            <div><h2 className="tsf-display">{title}</h2></div>
            {link ? <Link className="tsf-btn tsf-btn-link" to={link.to}>{link.label}</Link> : null}
          </div>
        ) : null}
        {children}
      </div>
    </section>
  );
}

/** StoryBlock link: internal path or http(s) (validated on save). */
function storyTarget(link: string | undefined): { to?: string; href?: string } | null {
  const value = (link ?? "").trim();
  if (!value) return null;
  if (value.startsWith("/") && !value.startsWith("//")) return { to: value };
  if (/^https?:\/\//i.test(value)) return { href: value };
  return null;
}

/** INTRO section (artifact StoryBlock): image + copy on the surface band; body falls back to the shop description. */
export function TsfStoryBlock({ shop, config }: { shop: PublicShop; config: ThemeConfig }) {
  const { localeTag } = usePublicTranslation();
  const intro = config.intro ?? {};
  const description = localizedText(localeTag, shop.description ?? "", shop.descriptionEn ?? null, shop.descriptionZh ?? null) || shop.description || "";
  const body = (intro.body ?? "").trim() || description.trim();
  const title = (intro.title ?? "").trim();
  if (!body && !title) return null;
  const eyebrow = (intro.eyebrow ?? "").trim();
  const label = (intro.ctaLabel ?? "").trim();
  const cta = label ? storyTarget(intro.ctaLink) : null;
  const image = intro.imageUrl ? mediaUrl(intro.imageUrl) : heroSlideUrls(config.hero?.slides)[0] ?? (shop.coverUrl ? mediaUrl(shop.coverUrl) : null);
  return (
    <TsfSection background="SURFACE" id="shop-story">
      <div className={intro.reverse ? "tsf-story is-reverse" : "tsf-story"}>
        <TsfImageSlot src={image} alt="" tone="secondary" motif="sun" scale={0.9} />
        <div>
          {eyebrow ? <p className="tsf-eyebrow">{eyebrow}</p> : null}
          <h2 className="tsf-display">{title || shop.name}</h2>
          {body ? <p style={{ whiteSpace: "pre-line" }}>{body}</p> : null}
          {cta ? <TsfButton variant="secondary" {...cta}>{label}</TsfButton> : null}
        </div>
      </div>
    </TsfSection>
  );
}

const HOURS_DAY_KEYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"] as const;
/** A Date whose local getters read Asia/Bangkok (UTC+7, no DST) wall time. */
function bangkokNow(now = new Date()) {
  return new Date(now.getTime() + (now.getTimezoneOffset() + 7 * 60) * 60000);
}

/** HOURS section (artifact HoursCard): day table (today bold), open/closed pill in Asia/Bangkok, map search link. */
export function TsfHoursCard({ shop }: { shop: PublicShop }) {
  const { tp } = usePublicTranslation();
  const hours = shop.openingHours;
  if (!hours || hours.length !== 7 || !hours.some((row) => row.open)) return null;
  const today = todayHours(hours, bangkokNow());
  const address = [shop.address, shop.subdistrict, shop.district, shop.province, shop.postalCode]
    .map((part) => (part ?? "").trim()).filter(Boolean).join(" ");
  const mapHref = address ? "https://www.google.com/maps/search/?api=1&query=" + encodeURIComponent(address) : null;
  const phone = (shop.phone ?? "").trim();
  const open = !!today?.openNow;
  return (
    <TsfSection title={tp("shop.general.hoursSectionTitle")} id="shop-hours">
      <div className="tsf-hours">
        <div className="tsf-hours-card">
          <h3><TsfIcon name="clock" />{tp("shop.general.hoursCardTitle")}<span className={open ? "tsf-open" : "tsf-open is-closed"}>{open ? tp("shop.openNow") : tp("shop.closedNow")}</span></h3>
          {HOURS_DAY_KEYS.map((day, index) => {
            const row = hours[index];
            return (
              <div key={day} className={today?.dayIndex === index ? "tsf-hours-row is-today" : "tsf-hours-row"}>
                <span>{tp("shop.hours." + day)}</span>
                <span className="tsf-num">{row.open ? row.start + " - " + row.end : tp("shop.hoursClosed")}</span>
              </div>
            );
          })}
        </div>
        {address ? (
          <div className="tsf-hours-card" style={{ display: "grid", gap: 12, alignContent: "start" }}>
            <a className="tsf-hours-map" href={mapHref ?? undefined} target="_blank" rel="noopener noreferrer" aria-label={tp("shop.general.mapOpen")}>
              <span className="tsf-map-pin"><TsfIcon name="pin" size={28} /></span>
            </a>
            <div style={{ display: "flex", gap: 8 }}><TsfIcon name="pin" /><span>{address}</span></div>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
              {mapHref ? <TsfButton variant="secondary" size="s" href={mapHref}>{tp("shop.general.mapOpen")}</TsfButton> : null}
              {phone ? <a className="tsf-btn tsf-btn-primary tsf-btn-s" href={"tel:" + phone.replace(/[^0-9+]/g, "")}>{tp("shop.general.call", { phone })}</a> : null}
            </div>
          </div>
        ) : null}
      </div>
    </TsfSection>
  );
}

const TRUST_ICON: Record<string, string> = { AUTHENTIC: "shield", FAST_SHIP: "truck", RETURNS: "refresh", SECURE_PAY: "check", CUSTOM: "star" };
export function TsfTrustStrip({ config }: { config: ThemeConfig }) {
  const { tp } = usePublicTranslation();
  const items = config.trust?.enabled ? config.trust.items.filter((item) => item.enabled && item.label.trim()) : [];
  if (!items.length) return null;
  return (
    <TsfSection pad="COMPACT">
      <div className="tsf-trust">
        {items.map((item, index) => (
          <div key={`${item.icon}-${index}`} className="tsf-trust-item">
            <span className="tsf-trust-ico"><TsfIcon name={TRUST_ICON[item.icon] ?? "star"} /></span>
            <div>
              <strong>{item.label}</strong>
              {item.sub?.trim() ? <span>{item.sub.trim()}</span> : item.icon !== "CUSTOM" && TRUST_ICON[item.icon] ? <span>{tp(`shop.general.trustSub.${item.icon}`)}</span> : null}
            </div>
          </div>
        ))}
      </div>
    </TsfSection>
  );
}

export function TsfCategoryRail({ shop, activeId, title, railStyle = "circle" }: { shop: PublicShop; activeId?: number | null; title?: string; railStyle?: "circle" | "tile" | "chip" }) {
  const { tp, localeTag } = usePublicTranslation();
  const { rows } = useShopCategories(shop.slug);
  if (!rows.length) return null;
  return (
    <TsfSection title={title ?? tp("shop.categoriesTitle")} link={activeId === undefined ? { to: shopCategoriesPath(shop.slug), label: tp("shop.seeAll") } : undefined} pad="COMPACT" id="shop-categories">
      <div className="tsf-cats" data-style={railStyle}>
        {rows.map((row) => {
          const Glyph = categoryIcon(row);
          return (
          <Link key={row.id} className="tsf-cat" to={shopCategoryPath(shop.slug, row)} aria-current={activeId === row.id ? "page" : undefined}>
            <div className="tsf-img tsf-cat-icon"><Glyph aria-hidden="true" strokeWidth={1.6} /></div>
            <span className="tsf-cat-label">{localizedText(localeTag, row.nameTh, row.nameEn, row.nameZh) || row.nameTh}</span>
          </Link>
          );
        })}
      </div>
    </TsfSection>
  );
}

function num(value: number | string | null | undefined) {
  if (value == null || value === "") return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}
const baht = (n: number) => `฿${n.toLocaleString("en-US", { maximumFractionDigits: 2 })}`;

/** Card add-to-cart: 1 SKU adds 1 piece; several SKUs/options open the product page (same rules as the mall ProductCard). */
function useCardAddToCart(shop: PublicShop, product: PublicProduct, to: string, onNotify?: (message: string) => void) {
  const { tp } = usePublicTranslation();
  const { user } = useAuth();
  const cartApi = useOptionalCart();
  const navigate = useNavigate();
  const [adding, setAdding] = useState(false);
  const notify = (message: string) => onNotify?.(message);
  const add = async (event?: MouseEvent) => {
    event?.preventDefault();
    const skus = product.skus ?? [];
    if (skus.length > 1 || (product.optionSchema?.length ?? 0) > 0) { notify(tp("cart.card.chooseOption")); navigate(to); return; }
    if (!cartApi) { notify(tp("shop.view.soon")); return; }
    if (cartApi.access === "guest") { notify(tp("cart.loginToAdd")); window.location.assign(loginPathForCart()); return; }
    if (cartApi.access === "blocked") { notify(tp("cart.blocked.toast")); return; }
    if (isOwnShop(user, shop.shopId)) { notify(tp("cart.own.toast")); return; }
    if (cartApi.access === "loading") return;
    const sku = skus[0];
    if (!sku || sku.available <= 0) { notify(tp("cart.card.soldOut")); return; }
    setAdding(true);
    try {
      const result = await cartApi.add(sku.id, 1);
      notify(result?.adjusted && result.appliedQuantity < 1 ? tp("shop.view.notEnough") : tp("shop.view.added"));
    } catch (error) {
      notify(cartErrorMessage(error, tp));
    } finally {
      setAdding(false);
    }
  };
  return { add, adding };
}

export function TsfProductCard({ shop, product, categoryName, favorite, onToggleFavorite, ratio, onNotify }: {
  shop: PublicShop; product: PublicProduct; categoryName?: string; favorite: boolean; onToggleFavorite: (id: string) => void; ratio?: string;
  onNotify?: (message: string) => void;
}) {
  const { tp, localeTag } = usePublicTranslation();
  const name = localizedText(localeTag, product.nameTh, product.nameEn, product.nameZh) || product.nameTh;
  const price = num(product.basePrice ?? product.skus[0]?.price);
  const compare = num(product.compareAtPrice ?? product.skus[0]?.compareAtPrice);
  const sale = price != null && compare != null && compare > price;
  const pct = sale ? Math.round((1 - price! / compare!) * 100) : 0;
  const image = [...product.images].sort((a, b) => Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder)[0];
  const sold = product.soldCount ?? 0;
  const createdMs = product.createdAt ? Date.parse(product.createdAt) : NaN;
  const isNew = Number.isFinite(createdMs) && Date.now() - createdMs < 14 * 24 * 60 * 60 * 1000;
  const meta = [categoryName, sold > 0 ? tp("shop.general.soldCount", { n: sold }) : null].filter(Boolean).join(" · ");
  const to = shopProductPath(shop.slug, product.slug);
  const { add, adding } = useCardAddToCart(shop, product, to, onNotify);
  return (
    <article className="tsf-card">
      <div className="tsf-card-badges">
        {isNew ? <TsfBadge tone="new">{tp("shop.general.newBadge")}</TsfBadge> : null}
        {sale ? <TsfBadge tone="sale">-{pct}%</TsfBadge> : null}
      </div>
      <button type="button" className="tsf-card-fav" aria-pressed={favorite} aria-label={tp("shop.general.wishlist")} onClick={() => onToggleFavorite(product.id)}>
        <TsfIcon name="heart" size={18} />
      </button>
      <Link to={to} aria-label={name}><TsfImageSlot src={image ? mediaUrl(image.url) : null} alt={image?.altText || name} ratio={ratio} /></Link>
      <div className="tsf-card-body">
        {meta ? <span className="tsf-card-meta">{meta}</span> : null}
        <h3 className="tsf-card-name"><Link to={to}>{name}</Link></h3>
        <div className="tsf-card-price">
          <span className={sale ? "is-sale" : undefined}>{price == null ? "—" : baht(price)}</span>
          {sale ? <s>{baht(compare!)}</s> : null}
        </div>
        <button type="button" className="tsf-btn tsf-btn-secondary tsf-btn-s tsf-card-add" disabled={adding} onClick={add} aria-label={`${tp("shop.general.addToCart")} ${name}`}>
          <TsfIcon name="bag" size={16} />{tp("shop.general.addToCart")}
        </button>
      </div>
    </article>
  );
}

/** Artifact MenuRow (ProductSection MENU): thumb · name/meta/price · + (opens the product to choose options). */
export function TsfMenuRow({ shop, product, categoryName }: { shop: PublicShop; product: PublicProduct; categoryName?: string }) {
  const { tp, localeTag } = usePublicTranslation();
  const name = localizedText(localeTag, product.nameTh, product.nameEn, product.nameZh) || product.nameTh;
  const price = num(product.basePrice ?? product.skus[0]?.price);
  const compare = num(product.compareAtPrice ?? product.skus[0]?.compareAtPrice);
  const sale = price != null && compare != null && compare > price;
  const image = [...product.images].sort((a, b) => Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder)[0];
  const to = shopProductPath(shop.slug, product.slug);
  return (
    <article className="tsf-menu-row">
      <Link to={to} aria-label={name}><TsfImageSlot src={image ? mediaUrl(image.url) : null} alt={image?.altText || name} /></Link>
      <div>
        <h3><Link to={to}>{name}</Link>{sale ? <> <TsfBadge tone="sale">-{Math.round((1 - price! / compare!) * 100)}%</TsfBadge></> : null}</h3>
        {categoryName ? <p>{categoryName}</p> : null}
        <div className="tsf-card-price"><span className={sale ? "is-sale" : undefined}>{price == null ? "—" : baht(price)}</span>{sale ? <s>{baht(compare!)}</s> : null}</div>
      </div>
      <Link className="tsf-add" to={to} aria-label={`${tp("shop.general.addToCart")} ${name}`}>+</Link>
    </article>
  );
}

export type TsfProductLayout = "GRID" | "LARGE" | "MENU";

export function TsfProductSection({ shop, title, products, link, background, categories, favorites, onToggleFavorite, columns = 4, variant = "GRID", after, onNotify }: {
  shop: PublicShop; title: string; products: PublicProduct[]; link?: { to: string; label: string }; background?: "SURFACE";
  categories: Map<number, string>; favorites: string[]; onToggleFavorite: (id: string) => void; columns?: number;
  variant?: TsfProductLayout | null; after?: ReactNode; onNotify?: (message: string) => void;
}) {
  if (!products.length) return null;
  const cat = (product: PublicProduct) => (product.categoryId != null ? categories.get(product.categoryId) : undefined);
  let body: ReactNode;
  if (variant === "MENU") {
    body = <div className="tsf-menu">{products.map((product) => <TsfMenuRow key={product.id} shop={shop} product={product} categoryName={cat(product)} />)}</div>;
  } else if (variant === "LARGE") {
    body = (
      <div className="tsf-rail" tabIndex={0} aria-label={title}>
        {products.map((product) => (
          <TsfProductCard key={product.id} shop={shop} product={product} ratio="4 / 5" categoryName={cat(product)} onNotify={onNotify}
            favorite={favorites.includes(product.id)} onToggleFavorite={onToggleFavorite} />
        ))}
        {after}
      </div>
    );
  } else {
    body = (
      <div className="tsf-grid" style={{ ["--tsf-cols" as string]: columns }}>
        {products.map((product) => (
          <TsfProductCard key={product.id} shop={shop} product={product} categoryName={cat(product)} onNotify={onNotify}
            favorite={favorites.includes(product.id)} onToggleFavorite={onToggleFavorite} />
        ))}
      </div>
    );
  }
  return (
    <TsfSection title={title} link={link} background={background}>
      {body}
      {variant === "LARGE" ? null : after}
    </TsfSection>
  );
}

export function TsfStoreFooter({ shop }: { shop: PublicShop }) {
  const { tp } = usePublicTranslation();
  const slug = shop.slug;
  const year = new Date().getFullYear();
  return (
    <footer className="tsf-footer">
      <div className="tsf-wrap">
        <div className="tsf-footer-grid">
          <div className="tsf-footer-about">
            <TsfLogo shop={shop} />
            {shop.description ? <p>{shop.description}</p> : null}
          </div>
          <div>
            <h4>{tp("shop.general.footerShop")}</h4>
            <ul>
              <li><Link to={shopProductsPath(slug)}>{tp("shop.tabs.products")}</Link></li>
              <li><Link to={shopCategoriesPath(slug)}>{tp("shop.tabs.categories")}</Link></li>
            </ul>
          </div>
          <div>
            <h4>{tp("shop.general.footerHelp")}</h4>
            <ul>
              <li><Link to="/account/orders">{tp("shop.general.footerOrders")}</Link></li>
              <li><Link to="/track-order">{tp("shop.general.footerTrack")}</Link></li>
            </ul>
          </div>
          <div>
            <h4>{tp("shop.general.footerStore")}</h4>
            <ul>
              <li><Link to={shopPoliciesPath(slug)}>{tp("shop.tabs.policies")}</Link></li>
              <li><Link to={shopContactPath(slug)}>{tp("shop.tabs.contact")}</Link></li>
            </ul>
          </div>
        </div>
        <div className="tsf-footer-base">
          <span>© {year} {shop.name}</span>
          <span>Powered by ThaiShopFun</span>
        </div>
      </div>
    </footer>
  );
}

export function TsfMobileTabBar({ shop, onNotify }: { shop: PublicShop; onNotify: (message: string) => void }) {
  const { tp } = usePublicTranslation();
  const cart = useOptionalCart();
  const count = cart?.count ?? 0;
  const current = ({ isActive }: { isActive: boolean }) => (isActive ? "is-active" : "");
  return (
    <nav className="tsf-tabbar" aria-label={tp("shop.general.tabBarAria")}>
      <NavLink to={shopPath(shop.slug)} end className={current}><TsfIcon name="home" />{tp("shop.tabs.home")}</NavLink>
      <NavLink to={shopCategoriesPath(shop.slug)} className={current}><TsfIcon name="grid" />{tp("shop.tabs.categories")}</NavLink>
      <NavLink to={shopFavoritesPath(shop.slug)} className={current}><TsfIcon name="heart" />{tp("shop.general.wishlist")}</NavLink>
      <Link to="/cart">
        <TsfIcon name="bag" />
        {count > 0 ? <span className="tsf-cart-count">{count > 99 ? "99+" : count}</span> : null}
        {tp("shop.general.cart")}
      </Link>
    </nav>
  );
}


/** Artifact PromoBanners: merchant banners from theme config (ตั้งค่าธีม → แบนเนอร์), after FEATURED. */
export function TsfPromoBanners({ config }: { config: ThemeConfig }) {
  const items = (config.banners ?? []).filter((banner) => banner.imageUrl.trim()).slice(0, 3);
  if (!items.length) return null;
  return (
    <TsfSection pad="COMPACT">
      <div className="tsf-banners" data-count={items.length}>
        {items.map((banner, index) => {
          const target = ctaTarget(banner.link);
          const body = (
            <>
              <TsfImageSlot src={mediaUrl(banner.imageUrl)} alt={banner.title ?? ""} tone="ink" />
              {banner.title?.trim() ? <div className="tsf-banner-copy"><h3 className="tsf-display">{banner.title}</h3></div> : null}
            </>
          );
          if (target && "to" in target && target.to) return <Link key={index} className="tsf-banner on-dark" to={target.to}>{body}</Link>;
          if (target && "href" in target && target.href) return <a key={index} className="tsf-banner on-dark" href={target.href} target="_blank" rel="noopener noreferrer">{body}</a>;
          return <div key={index} className="tsf-banner on-dark">{body}</div>;
        })}
      </div>
    </TsfSection>
  );
}

/** GENERAL catalog page (products / category / search): artifact section head + chip toolbar + 2/3/4 grid + pager. */
export function TsfCatalogPage({ shop, title, products, catalog, activeCategoryId, showRail = true, before }: {
  shop: PublicShop; title: string; products: PublicProduct[]; catalog: ShopCatalogControls;
  activeCategoryId?: number | null; showRail?: boolean; before?: ReactNode;
}) {
  const { tp, localeTag } = usePublicTranslation();
  const { rows } = useShopCategories(shop.slug);
  const categories = new Map(rows.map((row) => [row.id, localizedText(localeTag, row.nameTh, row.nameEn, row.nameZh) || row.nameTh]));
  const pageCount = Math.max(1, Math.ceil(catalog.total / Math.max(1, catalog.pageSize)));
  return (
    <main>
      {showRail ? <TsfCategoryRail shop={shop} activeId={activeCategoryId ?? null} railStyle={tsfThemeDefaults((shop.theme as { templateKey?: string } | null | undefined)?.templateKey).categoryStyle} /> : null}
      {before}
      <section className="tsf-section" data-bg="NONE" data-pad="NORMAL" data-align="LEFT">
        <div className="tsf-wrap">
          <div className="tsf-section-head">
            <div><h2 className="tsf-display">{title}</h2>{catalog.productState === "ok" ? <p className="tsf-catalog-count">{tp("shop.productResultCount", { count: catalog.total })}</p> : null}</div>
            <label className="tsf-sort">
              <span className="visually-hidden">{tp("common.sortBy")}</span>
              <select value={catalog.sort} onChange={(event) => catalog.onSort(parseShopSortKey(event.target.value))} aria-label={tp("catalog.sortAria")}>
                <option value="featured">{tp("catalog.sortFeatured")}</option>
                <option value="price">{tp("catalog.sortPrice")}</option>
                <option value="price_desc">{tp("shop.sortPriceHigh")}</option>
              </select>
            </label>
          </div>
          {rows.length > 0 && !catalog.categoryLocked ? (
            <div className="tsf-cats tsf-catalog-chips" data-style="chip" role="toolbar">
              <button type="button" className="tsf-chip" aria-pressed={catalog.categoryId == null} onClick={() => catalog.onCategory?.(null)}>{tp("shop.general.allProducts")}</button>
              {rows.map((row) => (
                <button key={row.id} type="button" className="tsf-chip" aria-pressed={catalog.categoryId === row.id} onClick={() => catalog.onCategory?.(row.id)}>{categories.get(row.id)}</button>
              ))}
            </div>
          ) : null}
          {catalog.productState === "error" ? (
            <EmptyState variant="error" title={tp("shop.productsError")} action={{ label: tp("shop.productsRetry"), onClick: catalog.onRetry }} />
          ) : catalog.showSkeleton && !products.length ? (
            <div className="tsf-grid">{Array.from({ length: 8 }, (_, index) => <div key={index} className="tsf-img tsf-skel" style={{ aspectRatio: "4 / 5" }} />)}</div>
          ) : products.length ? (
            <div className="tsf-grid" style={{ ["--tsf-cols" as string]: 4 }}>
              {products.map((product) => (
                <TsfProductCard key={product.id} shop={shop} product={product} onNotify={catalog.onNotify}
                  categoryName={product.categoryId != null ? categories.get(product.categoryId) : undefined}
                  favorite={catalog.favorites.includes(product.id)} onToggleFavorite={catalog.onToggleFavorite} />
              ))}
            </div>
          ) : catalog.productState === "ok" ? <EmptyState icon={Package} title={tp("shop.productsEmpty")} /> : null}
          {pageCount > 1 ? (
            <nav className="tsf-pager" aria-label={tp("shop.productsPageAria")}>
              <button type="button" className="tsf-chip" disabled={catalog.page <= 1} onClick={() => catalog.onPage(Math.max(1, catalog.page - 1))} aria-label={tp("shops.pagePrev")}>‹</button>
              {Array.from({ length: pageCount }, (_, index) => index + 1).map((number) => (
                <button key={number} type="button" className="tsf-chip" aria-pressed={catalog.page === number} onClick={() => catalog.onPage(number)}>{number}</button>
              ))}
              <button type="button" className="tsf-chip" disabled={catalog.page >= pageCount} onClick={() => catalog.onPage(Math.min(pageCount, catalog.page + 1))} aria-label={tp("shops.pageNext")}>›</button>
            </nav>
          ) : null}
        </div>
      </section>
    </main>
  );
}
