import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { Link } from "react-router-dom";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { SkeletonCard } from "../../feedback/Skeleton";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import ProductCard, { type ProductCardVariant } from "../ProductCard";
import type { PublicProduct, PublicShop } from "../../../services/api";
import { useRevealOnScroll } from "../../../hooks/useRevealOnScroll";
import { productCardClasses, useCustomProductCard } from "./ProductGrid";

const DRAG_THRESHOLD = 5;

type ProductRailProps = {
  shop: PublicShop;
  products: PublicProduct[];
  title: string;
  seeAllHref: string;
  cardVariant?: ProductCardVariant;
  loading?: boolean;
  favorites: string[];
  onToggleFavorite: (productId: string) => void;
  onNotify: (message: string) => void;
  /** Show 1–10 rank badges on cards (bestsellers). */
  ranked?: boolean;
  className?: string;
};

export default function ProductRail({
  shop,
  products,
  title,
  seeAllHref,
  cardVariant = "featured-row",
  loading = false,
  favorites,
  onToggleFavorite,
  onNotify,
  ranked = false,
  className = "",
}: ProductRailProps) {
  const { tp } = usePublicTranslation();
  const revealRef = useRevealOnScroll<HTMLElement>();
  const scrollerRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<{
    pointerId: number;
    startX: number;
    startScroll: number;
    moved: boolean;
    snap: string;
  } | null>(null);
  const [canPrev, setCanPrev] = useState(false);
  const [canNext, setCanNext] = useState(false);
  const [dragging, setDragging] = useState(false);
  const empty = !loading && products.length === 0;
  const productCard = useCustomProductCard(shop);

  const updateArrows = () => {
    const node = scrollerRef.current;
    if (!node) return;
    const max = node.scrollWidth - node.clientWidth;
    setCanPrev(node.scrollLeft > 2);
    setCanNext(node.scrollLeft < max - 2);
  };

  useEffect(() => {
    if (empty) return;
    const node = scrollerRef.current;
    if (!node) return;
    updateArrows();
    const onScroll = () => updateArrows();
    node.addEventListener("scroll", onScroll, { passive: true });
    const observer = typeof ResizeObserver !== "undefined" ? new ResizeObserver(updateArrows) : null;
    observer?.observe(node);
    return () => {
      node.removeEventListener("scroll", onScroll);
      observer?.disconnect();
    };
  }, [empty, products.length, loading]);

  if (empty) return null;

  const productExtras = productCardClasses(productCard).join(" ");

  const scrollByCard = (direction: -1 | 1) => {
    const node = scrollerRef.current;
    if (!node) return;
    node.scrollBy({ left: direction * Math.max(160, Math.round(node.clientWidth * 0.42)), behavior: "smooth" });
  };

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.pointerType === "touch" || event.button !== 0) return;
    const node = scrollerRef.current;
    if (!node) return;
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startScroll: node.scrollLeft,
      moved: false,
      snap: node.style.scrollSnapType,
    };
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    const node = scrollerRef.current;
    if (!drag || !node || drag.pointerId !== event.pointerId) return;
    const delta = event.clientX - drag.startX;
    if (!drag.moved && Math.abs(delta) < DRAG_THRESHOLD) return;
    if (!drag.moved) {
      drag.moved = true;
      setDragging(true);
      node.style.scrollSnapType = "none";
      node.classList.add("is-dragging");
      try { node.setPointerCapture(event.pointerId); } catch { /* ignore */ }
    }
    node.scrollLeft = drag.startScroll - delta;
    event.preventDefault();
  };

  const endDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    const node = scrollerRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    dragRef.current = null;
    if (node) {
      node.style.scrollSnapType = drag.snap || "";
      node.classList.remove("is-dragging");
      try { node.releasePointerCapture(event.pointerId); } catch { /* ignore */ }
    }
    setDragging(false);
    if (drag.moved) {
      node?.setAttribute("data-suppress-click", "1");
      window.setTimeout(() => node?.removeAttribute("data-suppress-click"), 0);
    }
    updateArrows();
  };

  return (
    <section ref={revealRef} className={`shop-featured shop-rail recommend-width shop-reveal ${className} ${productExtras}`.trim()}>
      <header className="shop-featured-head">
        <h2>{title}</h2>
        <Link className="shop-featured-all" to={seeAllHref}>{tp("shop.seeAll")} ›</Link>
      </header>
      <div className={`shop-featured-rail ${dragging ? "is-dragging" : ""}`}>
        <button type="button" className={`shop-featured-arrow is-prev ${canPrev ? "" : "is-disabled"}`} onClick={() => scrollByCard(-1)} aria-label={tp("shops.pagePrev")} disabled={!canPrev}>
          <ChevronLeft />
        </button>
        <div
          className="shop-featured-scroll"
          ref={scrollerRef}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={endDrag}
          onPointerCancel={endDrag}
          onClickCapture={(event) => {
            if (scrollerRef.current?.getAttribute("data-suppress-click") === "1") {
              event.preventDefault();
              event.stopPropagation();
            }
          }}
        >
          {loading
            ? Array.from({ length: 6 }, (_, index) => (
                <div className="shop-featured-skeleton" key={index}><SkeletonCard /></div>
              ))
            : products.map((product, index) => (
                <ProductCard
                  key={product.id}
                  shop={shop}
                  product={product}
                  variant={cardVariant}
                  favorites={favorites}
                  onToggleFavorite={onToggleFavorite}
                  onNotify={onNotify}
                  rank={ranked ? index + 1 : undefined}
                />
              ))}
          {!loading && (
            <Link className="shop-featured-more" to={seeAllHref} aria-label={tp("shop.seeMore")}>
              <span className="shop-featured-more-icon"><ChevronRight /></span>
              <span>{tp("shop.seeMore")} ›</span>
            </Link>
          )}
        </div>
        <button type="button" className={`shop-featured-arrow is-next ${canNext ? "" : "is-disabled"}`} onClick={() => scrollByCard(1)} aria-label={tp("shops.pageNext")} disabled={!canNext}>
          <ChevronRight />
        </button>
      </div>
    </section>
  );
}
