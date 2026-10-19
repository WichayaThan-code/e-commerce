import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { mediaUrl } from "../../../services/api";
import { useRevealOnScroll } from "../../../hooks/useRevealOnScroll";
import type { ShopSectionProps } from "./types";

function bannerHref(link?: string) {
  const value = (link || "").trim();
  if (!value) return null;
  if (value.startsWith("/") && !value.startsWith("//")) return { to: value, external: false as const };
  if (/^https:\/\//i.test(value)) return { to: value, external: true as const };
  return null;
}

export default function BannersSection({ config }: ShopSectionProps) {
  const banners = (config.banners || []).filter((item) => item.imageUrl?.trim());
  const revealRef = useRevealOnScroll<HTMLElement>();
  const [active, setActive] = useState(0);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (banners.length < 2 || paused) return;
    const id = window.setInterval(() => setActive((current) => (current + 1) % banners.length), 4500);
    return () => window.clearInterval(id);
  }, [banners.length, paused]);

  if (!banners.length) return null;

  const side = banners.slice(1, 3);

  const renderLink = (index: number, className: string) => {
    const banner = banners[index];
    if (!banner) return null;
    const target = bannerHref(banner.link);
    const img = <img src={mediaUrl(banner.imageUrl)} alt={banner.title || ""} />;
    if (!target) return <div className={className}>{img}</div>;
    if (target.external) {
      return <a className={className} href={target.to} target="_blank" rel="noopener noreferrer">{img}</a>;
    }
    return <Link className={className} to={target.to}>{img}</Link>;
  };

  return (
    <section
      id="shop-banners"
      ref={revealRef}
      className="shop-banners recommend-width shop-reveal"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onTouchStart={() => setPaused(true)}
    >
      <div className="shop-banners-desktop">
        {renderLink(0, "shop-banner-main")}
        {side.length > 0 && (
          <div className="shop-banners-side">
            {side.map((_, index) => renderLink(index + 1, "shop-banner-side"))}
          </div>
        )}
      </div>
      <div className="shop-banners-mobile">
        <div className="shop-banner-carousel" style={{ transform: `translateX(-${active * 100}%)` }}>
          {banners.map((banner, index) => {
            const target = bannerHref(banner.link);
            const img = <img src={mediaUrl(banner.imageUrl)} alt={banner.title || ""} />;
            const slide = target?.external
              ? <a href={target.to} target="_blank" rel="noopener noreferrer">{img}</a>
              : target
                ? <Link to={target.to}>{img}</Link>
                : img;
            return <div className="shop-banner-slide" key={`${banner.imageUrl}-${index}`}>{slide}</div>;
          })}
        </div>
        {banners.length > 1 && (
          <div className="shop-banner-dots" role="tablist">
            {banners.map((_, index) => (
              <button
                key={index}
                type="button"
                className={index === active ? "is-active" : ""}
                aria-label={`${index + 1}`}
                onClick={() => setActive(index)}
              />
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
