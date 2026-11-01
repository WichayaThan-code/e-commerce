import { useCallback, useEffect, useRef, useState, type TouchEvent } from "react";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { mediaUrl } from "../../../services/api";
import type { ThemeHeroSlide } from "../../../theme/shopTheme";
import "../../../styles/hero-slider.css";

const INTERVAL_MS = 5000;

function prefersReducedMotion() {
  return typeof window !== "undefined" && typeof window.matchMedia === "function"
    && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/** Image URLs of the theme hero slides (max 5, plain shop-media URLs only). */
export function heroSlideUrls(slides: ThemeHeroSlide[] | undefined | null): string[] {
  return (slides ?? []).map((slide) => slide.imageUrl?.trim()).filter((url): url is string => Boolean(url)).slice(0, 5).map((url) => mediaUrl(url));
}

/**
 * Hero slider (every template): fills its positioned parent, slides right-to-left every ~5s,
 * dots + prev/next + touch swipe, pauses on hover/focus, no auto-play under prefers-reduced-motion.
 */
export default function HeroSlider({ urls, className }: { urls: string[]; className?: string }) {
  const { tp } = usePublicTranslation();
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const touchX = useRef<number | null>(null);
  const count = urls.length;
  const go = useCallback((next: number) => setIndex(((next % count) + count) % count), [count]);

  useEffect(() => { if (index >= count) setIndex(0); }, [count, index]);
  useEffect(() => {
    if (count < 2 || paused || prefersReducedMotion()) return undefined;
    const timer = window.setInterval(() => setIndex((current) => (current + 1) % count), INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [count, paused]);

  if (!count) return null;
  const onTouchStart = (event: TouchEvent) => { touchX.current = event.touches[0]?.clientX ?? null; };
  const onTouchEnd = (event: TouchEvent) => {
    const start = touchX.current;
    touchX.current = null;
    const end = event.changedTouches[0]?.clientX;
    if (start == null || end == null || Math.abs(end - start) < 40) return;
    go(end < start ? index + 1 : index - 1);
  };

  return (
    <div
      className={["hero-slider", className ?? ""].filter(Boolean).join(" ")}
      role="region"
      aria-roledescription="carousel"
      aria-label={tp("shop.heroSlider.aria")}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
      onTouchStart={onTouchStart}
      onTouchEnd={onTouchEnd}
    >
      <div className="hero-slider-track" style={{ transform: `translateX(-${index * 100}%)` }}>
        {urls.map((url, slide) => (
          <div key={`${url}-${slide}`} className="hero-slider-slide" aria-hidden={slide !== index}>
            <img src={url} alt="" loading={slide === 0 ? "eager" : "lazy"} draggable={false} />
          </div>
        ))}
      </div>
      {count > 1 ? (
        <>
          <button type="button" className="hero-slider-arrow is-prev" aria-label={tp("shop.heroSlider.prev")} onClick={() => go(index - 1)}>&#8249;</button>
          <button type="button" className="hero-slider-arrow is-next" aria-label={tp("shop.heroSlider.next")} onClick={() => go(index + 1)}>&#8250;</button>
          <div className="hero-slider-dots">
            {urls.map((url, slide) => (
              <button
                key={`dot-${url}-${slide}`}
                type="button"
                className={slide === index ? "is-active" : undefined}
                aria-label={tp("shop.heroSlider.goTo", { n: slide + 1 })}
                aria-current={slide === index ? "true" : undefined}
                onClick={() => go(slide)}
              />
            ))}
          </div>
        </>
      ) : null}
    </div>
  );
}