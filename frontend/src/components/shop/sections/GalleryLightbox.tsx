import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { ChevronLeft, ChevronRight, ExternalLink, X } from "lucide-react";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { BlockLink } from "./blockParts";

export type LightboxItem = { src: string; caption: string; link?: string };

type GalleryLightboxProps = {
  items: LightboxItem[];
  index: number;
  label: string;
  onIndex: (index: number) => void;
  onClose: () => void;
};

const FOCUSABLE = "button:not([disabled]), a[href]";

/**
 * Keyboard-accessible image viewer: Esc closes, ←/→ move between images, Tab is trapped inside the dialog
 * (light focus trap) and focus returns to the opener on close. Rendered in a portal so no storefront ancestor can
 * clip a fixed-position overlay; it carries `data-template="CUSTOM"` because galleries only exist on CUSTOM themes.
 */
export default function GalleryLightbox({ items, index, label, onIndex, onClose }: GalleryLightboxProps) {
  const { tp } = usePublicTranslation();
  const rootRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const total = items.length;
  const current = items[index];

  // Latest handlers/state without re-binding the document listener on every navigation.
  const stateRef = useRef({ index, total, onIndex, onClose });
  stateRef.current = { index, total, onIndex, onClose };

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    closeRef.current?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      const { index: at, total: count, onIndex: go, onClose: close } = stateRef.current;
      if (event.key === "Escape") {
        event.preventDefault();
        close();
      } else if (event.key === "ArrowRight" && count > 1) {
        event.preventDefault();
        go((at + 1) % count);
      } else if (event.key === "ArrowLeft" && count > 1) {
        event.preventDefault();
        go((at - 1 + count) % count);
      } else if (event.key === "Tab") {
        const root = rootRef.current;
        if (!root) return;
        const nodes = Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE));
        if (!nodes.length) return;
        const first = nodes[0];
        const last = nodes[nodes.length - 1];
        const active = document.activeElement as HTMLElement | null;
        if (!active || !root.contains(active)) {
          event.preventDefault();
          first.focus();
        } else if (event.shiftKey && active === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && active === last) {
          event.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
      opener?.focus?.();
    };
  }, []);

  if (!current) return null;

  return createPortal(
    <div
      ref={rootRef}
      className="shop-gallery-lightbox"
      data-template="CUSTOM"
      role="dialog"
      aria-modal="true"
      aria-label={label}
      onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}
    >
      <button ref={closeRef} type="button" className="shop-gallery-lightbox-close" aria-label={tp("shop.blocks.gallery.close")} onClick={onClose}>
        <X aria-hidden="true" />
      </button>
      {total > 1 ? (
        <button type="button" className="shop-gallery-lightbox-nav is-prev" aria-label={tp("shop.blocks.gallery.prev")} onClick={() => onIndex((index - 1 + total) % total)}>
          <ChevronLeft aria-hidden="true" />
        </button>
      ) : null}
      <figure className="shop-gallery-lightbox-figure">
        <img src={current.src} alt={current.caption} />
        <figcaption>
          {current.caption ? <span>{current.caption}</span> : null}
          <span className="shop-gallery-lightbox-count num" aria-live="polite">{index + 1} / {total}</span>
          <BlockLink link={current.link} className="shop-gallery-lightbox-link">
            <ExternalLink aria-hidden="true" />{tp("shop.blocks.gallery.visit")}
          </BlockLink>
        </figcaption>
      </figure>
      {total > 1 ? (
        <button type="button" className="shop-gallery-lightbox-nav is-next" aria-label={tp("shop.blocks.gallery.next")} onClick={() => onIndex((index + 1) % total)}>
          <ChevronRight aria-hidden="true" />
        </button>
      ) : null}
    </div>,
    document.body,
  );
}
