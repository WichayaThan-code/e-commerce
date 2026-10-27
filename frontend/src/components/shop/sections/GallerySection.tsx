import { useMemo, useRef, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { mediaUrl } from "../../../services/api";
import { isLocalizedEmpty, resolveGalleryContent } from "../../../theme/blockContent";
import GalleryLightbox, { type LightboxItem } from "./GalleryLightbox";
import { BlockHeading, panelClasses, useLocalized } from "./blockParts";
import type { ShopNewBlockProps } from "./types";

/** AG4 GALLERY: GRID / MASONRY / CAROUSEL of library images; every image opens a keyboard-accessible lightbox. */
export default function GallerySection({ block }: ShopNewBlockProps) {
  const content = useMemo(() => resolveGalleryContent(block.content), [block.content]);
  const { tp } = usePublicTranslation();
  const l = useLocalized();
  const [open, setOpen] = useState<number | null>(null);
  const scroller = useRef<HTMLUListElement>(null);

  const items = content.items.filter((item) => item.url);
  const lightboxItems: LightboxItem[] = items.map((item) => ({
    src: mediaUrl(item.url),
    caption: l(item.caption),
    link: item.link,
  }));
  if (!items.length && isLocalizedEmpty(content.title)) return null;

  const title = l(content.title);
  const label = title || tp("shop.blocks.gallery.label");
  const layoutClass = `shop-gallery-${content.layout.toLowerCase()}`;
  const scrollBy = (direction: 1 | -1) => {
    const node = scroller.current;
    if (!node) return;
    node.scrollBy({ left: direction * Math.max(220, node.clientWidth * 0.8), behavior: "smooth" });
  };

  return (
    <section className={panelClasses(block, "shop-gallery-section", layoutClass)} aria-label={label}>
      <BlockHeading title={content.title} prefix="shop-gallery" />
      {items.length ? (
        <div className="shop-gallery-frame" {...(content.layout === "CAROUSEL" ? { role: "region", "aria-roledescription": "carousel", "aria-label": label } : {})}>
          {content.layout === "CAROUSEL" && items.length > 1 ? (
            <button type="button" className="shop-gallery-arrow is-prev" aria-label={tp("shop.blocks.gallery.scrollPrev")} onClick={() => scrollBy(-1)}>
              <ChevronLeft aria-hidden="true" />
            </button>
          ) : null}
          <ul className="shop-gallery-list" ref={scroller}>
            {items.map((item, index) => (
              <li key={`${item.mediaId}-${index}`} className="shop-gallery-item">
                <button
                  type="button"
                  className="shop-gallery-open"
                  aria-label={`${tp("shop.blocks.gallery.open")}${l(item.caption) ? `: ${l(item.caption)}` : ` ${index + 1}`}`}
                  aria-haspopup="dialog"
                  onClick={() => setOpen(index)}
                >
                  <img src={mediaUrl(item.url)} alt={l(item.caption)} loading="lazy" />
                </button>
                {!isLocalizedEmpty(item.caption) ? <span className="shop-gallery-caption">{l(item.caption)}</span> : null}
              </li>
            ))}
          </ul>
          {content.layout === "CAROUSEL" && items.length > 1 ? (
            <button type="button" className="shop-gallery-arrow is-next" aria-label={tp("shop.blocks.gallery.scrollNext")} onClick={() => scrollBy(1)}>
              <ChevronRight aria-hidden="true" />
            </button>
          ) : null}
        </div>
      ) : null}
      {open != null && lightboxItems[open] ? (
        <GalleryLightbox
          items={lightboxItems}
          index={open}
          label={label}
          onIndex={setOpen}
          onClose={() => setOpen(null)}
        />
      ) : null}
    </section>
  );
}
