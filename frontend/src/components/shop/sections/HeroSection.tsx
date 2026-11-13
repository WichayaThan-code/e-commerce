import { Link } from "react-router-dom";
import { BadgeCheck, MessageCircle, UserPlus } from "lucide-react";
import ShopLogo from "../../ShopLogo";
import ShopTabs from "../ShopTabs";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import recommendReference from "../../../assets/images/references/storefront/recommend-product.png";
import { mediaUrl } from "../../../services/api";
import { useRevealOnScroll } from "../../../hooks/useRevealOnScroll";
import { sectionSettingClasses } from "./sectionClasses";
import HeroMediaFull, { isMediaFullHero } from "./HeroMediaFull";
import HeroSlider, { heroSlideUrls } from "./HeroSlider";
import { TsfHero } from "../general/GeneralStorefront";
import "../../../styles/general-hero.css";
import type { ShopSectionProps } from "./types";
import { resolveStorefrontTheme } from "../ShopThemeShell";
import { shopCategoriesPath, shopContactPath, shopProductsPath } from "../../../services/shopRoutes";
import { resolveHeroSecondaryAction } from "../../../theme/shopTheme";

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

/** AG5: CUSTOM-only MEDIA_FULL goes to its own component; every other hero is the unchanged mall hero below. */
export default function HeroSection(props: ShopSectionProps) {
  // Item 2: the editor preview renders tsf templates through ShopSections; use the same TsfHero as the public
  // storefront (ShopPage) so FULL / SPLIT / POSTER / STRIP / MEDIA_FULL preview exactly what will be published.
  const templateKey = resolveStorefrontTheme(props.shop).templateKey;
  if (templateKey === "GENERAL" || templateKey === "FASHION" || templateKey === "FOOD_BEVERAGE") return <TsfHero shop={props.shop} config={props.config} />;
  if (isMediaFullHero(props.config.hero, props.section.variant, Boolean(props.config.layout))) {
    return <HeroMediaFull {...props} />;
  }
  if (templateKey === "CUSTOM") return <CustomTsfHero {...props} />;
  if (templateKey === "GENERAL") return <GeneralHero {...props} />;
  return <MallHero {...props} />;
}

/**
 * Open item 3: CUSTOM honours hero.layout (FULL / SPLIT / POSTER / STRIP; MEDIA_FULL handled above) and hero.textAlign by
 * reusing the tsf hero inside a scoped .tsf-store wrapper (tsf-custom-hero maps Custom fonts / colours via --shop-* vars).
 * Old configs resolve to FULL. The shop tabs the legacy mall hero rendered are kept below it.
 */
function CustomTsfHero({ shop, config, catalog, preview }: ShopSectionProps) {
  const align = config.hero.textAlign === "CENTER" ? "CENTER" : "LEFT";
  return (
    <>
      <div className={["tsf-store", "tsf-custom-hero", ...sectionSettingClasses("HERO", config)].filter(Boolean).join(" ")}>
        <TsfHero shop={shop} config={config} align={align} />
      </div>
      <ShopTabs shop={shop} preview={preview} onNotify={catalog.onNotify} />
    </>
  );
}

function CtaLink({ target, fallback, className, label }: { target: { href: string; external: boolean } | null; fallback: string; className: string; label: string }) {
  if (target?.external) {
    return <a className={className} href={target.href} target="_blank" rel="noopener noreferrer">{label}</a>;
  }
  return <Link className={className} to={target?.href ?? fallback}>{label}</Link>;
}

/**
 * GENERAL (themes spec) hero: full-width navy band, decorative circle, eyebrow, large headline, subtitle and 2 CTAs.
 * Every text is merchant-editable plain text (theme settings → ส่วนหัวร้าน); React escapes it, never HTML.
 */
function GeneralHero({ shop, config }: ShopSectionProps) {
  const { tp } = usePublicTranslation();
  const revealRef = useRevealOnScroll<HTMLElement>();
  const { hero } = config;
  const eyebrow = (hero.eyebrow ?? "").trim() || shop.category || "";
  const title = hero.title.trim() || shop.name;
  const subtitle = hero.subtitle.trim() || shop.description || "";
  const primaryLabel = hero.ctaLabel.trim() || tp("shop.general.ctaShop");
  const secondaryAction = resolveHeroSecondaryAction(hero.ctaSecondaryAction, hero.ctaSecondaryLabel, hero.ctaSecondaryLink);
  const secondaryOverride = (hero.ctaSecondaryLabel ?? "").trim();
  const secondary =
    secondaryAction === "NONE" ? null
      : secondaryAction === "CUSTOM" ? (() => {
        const target = ctaTarget(hero.ctaSecondaryLink ?? "");
        if (!target) return null;
        return { label: secondaryOverride || tp("shop.general.ctaProducts"), target, fallback: shopProductsPath(shop.slug) };
      })()
        : {
          label: secondaryOverride || (
            secondaryAction === "PRODUCTS" ? tp("shop.general.ctaProducts")
              : secondaryAction === "CATEGORIES" ? tp("shop.general.ctaCategories")
                : tp("shop.general.ctaContact")
          ),
          target: null as { href: string; external: boolean } | null,
          fallback:
            secondaryAction === "PRODUCTS" ? shopProductsPath(shop.slug)
              : secondaryAction === "CATEGORIES" ? shopCategoriesPath(shop.slug)
                : shopContactPath(shop.slug),
        };
  const slides = heroSlideUrls(hero.slides);
  const cover = slides[0] ?? (shop.coverUrl ? mediaUrl(shop.coverUrl) : null);
  const hasMedia = slides.length > 0 || Boolean(cover);

  return (
    <section
      id="shop-home"
      ref={revealRef}
      className={["shop-general-hero", "shop-reveal", hasMedia ? "has-cover" : "", ...sectionSettingClasses("HERO", config)].filter(Boolean).join(" ")}
      data-general-hero="true"
    >
      {slides.length ? <div className="shop-general-hero-cover"><HeroSlider urls={slides} /></div>
        : cover ? <div className="shop-general-hero-cover" style={{ backgroundImage: `url(${cover})` }} aria-hidden="true" /> : null}
      <span className="shop-general-hero-circle" aria-hidden="true" />
      <div className="shop-general-hero-inner recommend-width">
        {eyebrow ? <p className="shop-general-hero-eyebrow">{eyebrow}</p> : null}
        <h1>{title}</h1>
        {subtitle ? <p className="shop-general-hero-sub">{subtitle}</p> : null}
        {hero.showCtas !== false ? (
          <div className="shop-general-ctas">
            <CtaLink target={ctaTarget(hero.ctaLink)} fallback={shopProductsPath(shop.slug)} className="shop-general-cta is-primary" label={primaryLabel} />
            {secondary ? (
              <CtaLink target={secondary.target} fallback={secondary.fallback} className="shop-general-cta is-secondary" label={secondary.label} />
            ) : null}
          </div>
        ) : null}
      </div>
    </section>
  );
}

function MallHero({ shop, config, catalog, preview }: ShopSectionProps) {
  const { tp, localeTag } = usePublicTranslation();
  const revealRef = useRevealOnScroll<HTMLElement>();
  const { hero } = config;
  const title = hero.title.trim() || shop.name;
  const subtitle = hero.subtitle.trim() || shop.description || "";
  const cta = hero.showCtas !== false && hero.ctaLabel.trim() ? ctaTarget(hero.ctaLink) : null;
  const slides = heroSlideUrls(hero.slides);
  const cover = slides[0] ?? (shop.coverUrl ? mediaUrl(shop.coverUrl) : recommendReference);
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
      <section
        id="shop-home"
        ref={revealRef}
        className={["shop-mall-hero", "recommend-width", "shop-reveal", ...sectionSettingClasses("HERO", config), config?.gradients?.hero ? "shop-hero-gradient" : ""].filter(Boolean).join(" ")}
        data-variant="MALL"
      >
        {slides.length ? <div className="shop-mall-hero-cover"><HeroSlider urls={slides} /></div>
          : <div className="shop-mall-hero-cover" style={{ backgroundImage: `url(${cover})` }} aria-hidden="true" />}
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
