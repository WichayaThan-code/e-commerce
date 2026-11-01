import { createContext, useContext, useRef, type CSSProperties, type PointerEvent } from "react";
import { Link } from "react-router-dom";
import { MessageCircle, UserPlus } from "lucide-react";
import ShopTabs from "../ShopTabs";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { usePrefersReducedMotion } from "../../../hooks/usePrefersReducedMotion";
import { safeBlockLink } from "../../../theme/blockContent";
import { HERO_RANGES, clampInt, type ThemeHero } from "../../../theme/shopTheme";
import { themeMediaSrc } from "../chromeShop";
import { sectionSettingClasses } from "./sectionClasses";
import HeroSlider, { heroSlideUrls } from "./HeroSlider";
import type { ShopSectionProps } from "./types";

/** Provided by the theme editor so stickers can be dragged on the preview (not provided on the live storefront). */
export type StickerDragApi = { onMove: (index: number, x: number, y: number) => void };
export const HeroStickerDragContext = createContext<StickerDragApi | null>(null);

export function isMediaFullHero(hero: ThemeHero, sectionVariant: string | null | undefined, custom: boolean): boolean {
  return custom && (sectionVariant === "MEDIA_FULL" || hero.layout === "MEDIA_FULL");
}

function ctaLink(label: string, link: string, className: string) {
  const text = label.trim();
  const target = text ? safeBlockLink(link) : null;
  if (!target) return null;
  return target.external ? (
    <a className={className} href={target.href} target="_blank" rel="noopener noreferrer">{text}</a>
  ) : (
    <Link className={className} to={target.href}>{text}</Link>
  );
}

/**
 * CUSTOM-only full-bleed hero: image / muted looping video / gradient background, tint overlay and up to six
 * stickers positioned in percent. Shown with the same follow / chat actions as the mall hero; ShopTabs stay below.
 * prefers-reduced-motion swaps the video for its poster.
 */
export default function HeroMediaFull({ shop, config, catalog, preview }: ShopSectionProps) {
  const { tp } = usePublicTranslation();
  const reduced = usePrefersReducedMotion();
  const drag = useContext(HeroStickerDragContext);
  const stageRef = useRef<HTMLDivElement>(null);
  const { hero } = config;
  const title = hero.title.trim() || shop.name;
  const subtitle = hero.subtitle.trim() || shop.description || "";
  const background = hero.background;
  const slides = heroSlideUrls(hero.slides);
  const bgType = background?.type ?? "GRADIENT";
  const mainSrc = themeMediaSrc(background?.url);
  const posterSrc = themeMediaSrc(background?.posterUrl);
  const overlay = clampInt(hero.overlay, HERO_RANGES.overlay[0], HERO_RANGES.overlay[1]) ?? 35;
  const heightVh = clampInt(hero.heightVh, HERO_RANGES.heightVh[0], HERO_RANGES.heightVh[1]) ?? 70;
  const align = hero.textAlign === "CENTER" ? "center" : "left";
  const size = hero.headlineSize === "XL" ? "xl" : "l";
  const showVideo = bgType === "VIDEO" && Boolean(mainSrc) && !reduced;
  const stillSrc = bgType === "IMAGE" ? mainSrc : bgType === "VIDEO" ? posterSrc : null;
  const stickers = (hero.stickers ?? []).map((sticker, index) => ({ sticker, index, src: themeMediaSrc(sticker.url) }));

  const startDrag = (index: number) => (event: PointerEvent<HTMLElement>) => {
    const stage = stageRef.current;
    if (!drag || !stage) return;
    event.preventDefault();
    const target = event.currentTarget;
    target.setPointerCapture?.(event.pointerId);
    const move = (moveEvent: globalThis.PointerEvent) => {
      const rect = stage.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return;
      const x = Math.round(((moveEvent.clientX - rect.left) / rect.width) * 100);
      const y = Math.round(((moveEvent.clientY - rect.top) / rect.height) * 100);
      drag.onMove(
        index,
        Math.max(HERO_RANGES.x[0], Math.min(HERO_RANGES.x[1], x)),
        Math.max(HERO_RANGES.y[0], Math.min(HERO_RANGES.y[1], y)),
      );
    };
    const stop = () => {
      target.removeEventListener("pointermove", move);
      target.removeEventListener("pointerup", stop);
      target.removeEventListener("pointercancel", stop);
    };
    target.addEventListener("pointermove", move);
    target.addEventListener("pointerup", stop);
    target.addEventListener("pointercancel", stop);
  };

  return (
    <>
      <section
        id="shop-home"
        className={["shop-hero-media", "recommend-width", slides.length ? "has-slider" : "", ...sectionSettingClasses("HERO", config)].filter(Boolean).join(" ")}
        data-variant="MEDIA_FULL"
        data-align={align}
        data-size={size}
        data-bg={bgType.toLowerCase()}
        style={{ "--shop-hero-height": `${heightVh}vh`, "--shop-hero-overlay": String(overlay / 100) } as CSSProperties}
      >
        {slides.length ? (
          <div className="shop-hero-media-bg"><HeroSlider urls={slides} /></div>
        ) : (
        <div className="shop-hero-media-bg" aria-hidden="true">
          {showVideo && mainSrc ? (
            <video
              className="shop-hero-media-video"
              src={mainSrc}
              poster={posterSrc ?? undefined}
              muted
              autoPlay
              loop
              playsInline
              preload="metadata"
              disablePictureInPicture
              tabIndex={-1}
            />
          ) : stillSrc ? (
            <img className="shop-hero-media-img" src={stillSrc} alt="" />
          ) : null}
        </div>
        )}
        <div className="shop-hero-media-shade" aria-hidden="true" />
        <div className="shop-hero-media-stage" ref={stageRef}>
          {stickers.map(({ sticker, index, src }) =>
            src ? (
              <img
                key={`${index}-${sticker.mediaId}`}
                className={["shop-sticker", sticker.hideOnMobile ? "is-hide-mobile" : "", drag ? "is-draggable" : ""].filter(Boolean).join(" ")}
                src={src}
                alt=""
                draggable={false}
                style={{
                  left: `${sticker.x}%`,
                  top: `${sticker.y}%`,
                  width: `${sticker.size}px`,
                  "--shop-sticker-rotate": `${sticker.rotate}deg`,
                } as CSSProperties}
                onPointerDown={startDrag(index)}
              />
            ) : null,
          )}
        </div>
        <div className="shop-hero-media-body">
          <h1>{title}</h1>
          {subtitle ? <p className="shop-hero-media-sub">{subtitle}</p> : null}
          <div className="shop-hero-media-actions">
            {ctaLink(hero.ctaLabel, hero.ctaLink, "shop-mall-btn is-primary")}
            {hero.ctaSecondary ? ctaLink(hero.ctaSecondary.label, hero.ctaSecondary.link, "shop-mall-btn is-outline") : null}
            <button type="button" className="shop-mall-btn is-ghost" onClick={() => catalog.onNotify(tp("shop.view.soon"))}>
              <UserPlus />{tp("shop.follow")}
            </button>
            <button type="button" className="shop-mall-btn is-ghost" onClick={() => catalog.onNotify(tp("shop.view.soon"))}>
              <MessageCircle />{tp("shop.chat")}
            </button>
          </div>
        </div>
      </section>
      <ShopTabs shop={shop} preview={preview} onNotify={catalog.onNotify} />
    </>
  );
}
