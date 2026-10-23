import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { cartErrorMessage } from "../cart/cartFormat";
import { Skeleton } from "../feedback/Skeleton";
import { useCouponWallet } from "../../hooks/useCouponWallet";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { useShopPromotions } from "../../hooks/useShopPromotions";
import type { PublicPromotion } from "../../services/api";
import { shopPath } from "../../services/shopRoutes";
import ShopCouponTicket from "./ShopCouponTicket";
import { PROMO_COUPONS_ANCHOR } from "./promoTargets";
import "../../styles/shop-coupons.css";

type Props = {
  slug: string;
  shopId: number;
  /** Storefront toast (catalog.onNotify). */
  onNotify?: (message: string) => void;
};

const COLLECT_PARAM = "collect";
const DRAG_THRESHOLD = 5;

function useCouponRail(itemCount: number, enabled: boolean) {
  const scrollerRef = useRef<HTMLUListElement>(null);
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

  const updateArrows = () => {
    const node = scrollerRef.current;
    if (!node) return;
    const max = node.scrollWidth - node.clientWidth;
    setCanPrev(node.scrollLeft > 2);
    setCanNext(node.scrollLeft < max - 2);
  };

  useEffect(() => {
    if (!enabled) return;
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
  }, [enabled, itemCount]);

  const scrollByCard = (direction: -1 | 1) => {
    const node = scrollerRef.current;
    if (!node) return;
    const card = node.querySelector<HTMLElement>(".shop-ticket");
    const step = card ? card.offsetWidth + 12 : Math.max(200, Math.round(node.clientWidth * 0.7));
    node.scrollBy({ left: direction * step, behavior: "smooth" });
  };

  const onPointerDown = (event: ReactPointerEvent<HTMLUListElement>) => {
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

  const onPointerMove = (event: ReactPointerEvent<HTMLUListElement>) => {
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

  const endDrag = (event: ReactPointerEvent<HTMLUListElement>) => {
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

  return {
    scrollerRef,
    canPrev,
    canNext,
    dragging,
    scrollByCard,
    onPointerDown,
    onPointerMove,
    endDrag,
  };
}

function StripFrame({
  children,
  subtitle,
  rail,
}: {
  children: React.ReactNode;
  subtitle: string;
  rail: ReturnType<typeof useCouponRail>;
}) {
  const { tp } = usePublicTranslation();
  return (
    <section id={PROMO_COUPONS_ANCHOR} className="shop-coupons recommend-width" aria-labelledby={`${PROMO_COUPONS_ANCHOR}-title`}>
      <header className="shop-coupons-head">
        <div>
          <h2 id={`${PROMO_COUPONS_ANCHOR}-title`}>{tp("shop.promotions.title")}</h2>
          <p>{subtitle}</p>
        </div>
      </header>
      <div className={`shop-coupons-rail ${rail.dragging ? "is-dragging" : ""}`}>
        <button
          type="button"
          className={`shop-coupons-arrow is-prev ${rail.canPrev ? "" : "is-disabled"}`}
          onClick={() => rail.scrollByCard(-1)}
          aria-label={tp("shops.pagePrev")}
          disabled={!rail.canPrev}
        >
          <ChevronLeft />
        </button>
        {children}
        <button
          type="button"
          className={`shop-coupons-arrow is-next ${rail.canNext ? "" : "is-disabled"}`}
          onClick={() => rail.scrollByCard(1)}
          aria-label={tp("shops.pageNext")}
          disabled={!rail.canNext}
        >
          <ChevronRight />
        </button>
      </div>
    </section>
  );
}

/**
 * Storefront coupon strip (ShopPage). Shopee/Lazada style: the buyer COLLECTS a public code here (POST /api/me/coupons)
 * and can then use it in the cart. Guests go to /login and come back with ?collect=CODE, which is collected automatically.
 * Horizontal rail matches product rails (arrows + drag) — no "see all" until a coupon vault page exists.
 */
export default function ShopCouponStrip({ slug, shopId, onNotify }: Props) {
  const { tp } = usePublicTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const { promotions, ready } = useShopPromotions(slug);
  const wallet = useCouponWallet(shopId);
  const [busyCode, setBusyCode] = useState<string | null>(null);
  const scrolled = useRef(false);
  const autoCollected = useRef(false);
  const rail = useCouponRail(ready ? promotions.length : 3, ready ? promotions.length > 0 : true);

  // Deep link (/shop/x#shop-promotions from another tab page): scroll once the list has rendered.
  useEffect(() => {
    if (!ready || !promotions.length || scrolled.current) return;
    if (location.hash !== `#${PROMO_COUPONS_ANCHOR}`) return;
    scrolled.current = true;
    requestAnimationFrame(() => document.getElementById(PROMO_COUPONS_ANCHOR)?.scrollIntoView({ behavior: "smooth", block: "start" }));
  }, [ready, promotions.length, location.hash]);

  const collect = async (code: string) => {
    if (!wallet.signedIn) {
      const next = `${shopPath(slug)}?${COLLECT_PARAM}=${encodeURIComponent(code)}#${PROMO_COUPONS_ANCHOR}`;
      onNotify?.(tp("shop.promotions.loginToCollect"));
      window.location.assign(`/login?next=${encodeURIComponent(next)}`);
      return;
    }
    setBusyCode(code);
    try {
      const result = await wallet.collect(code);
      onNotify?.(result.alreadyCollected ? tp("shop.promotions.alreadyCollected", { code }) : tp("shop.promotions.collectedToast", { code }));
    } catch (error) {
      onNotify?.(cartErrorMessage(error, tp));
    } finally {
      setBusyCode(null);
    }
  };

  // Back from /login with ?collect=CODE: collect once, then drop the param (keep the #shop-promotions hash).
  useEffect(() => {
    const code = new URLSearchParams(location.search).get(COLLECT_PARAM);
    if (!code || autoCollected.current || !ready || !wallet.ready || !wallet.signedIn) return;
    autoCollected.current = true;
    const params = new URLSearchParams(location.search);
    params.delete(COLLECT_PARAM);
    const search = params.toString();
    navigate({ pathname: location.pathname, search: search ? `?${search}` : "", hash: location.hash }, { replace: true });
    const known = promotions.some((promotion) => promotion.code.toUpperCase() === code.toUpperCase());
    if (known && !wallet.codes.has(code.toUpperCase())) void collect(code.toUpperCase());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.search, ready, wallet.ready, wallet.signedIn]);

  const listProps = {
    ref: rail.scrollerRef,
    className: "shop-coupons-list",
    onPointerDown: rail.onPointerDown,
    onPointerMove: rail.onPointerMove,
    onPointerUp: rail.endDrag,
    onPointerCancel: rail.endDrag,
    onClickCapture: (event: React.MouseEvent) => {
      if (rail.scrollerRef.current?.getAttribute("data-suppress-click") === "1") {
        event.preventDefault();
        event.stopPropagation();
      }
    },
  };

  if (!ready) {
    return (
      <StripFrame subtitle={tp("shop.promotions.subtitle")} rail={rail}>
        <ul {...listProps} aria-busy="true">
          {[0, 1, 2].map((key) => (
            <li key={key} className="shop-ticket is-skeleton">
              <Skeleton className="shop-ticket-skel" height="100%" radius="inherit" />
            </li>
          ))}
        </ul>
      </StripFrame>
    );
  }
  if (!promotions.length) return null;

  return (
    <StripFrame subtitle={tp("shop.promotions.subtitle")} rail={rail}>
      <ul {...listProps} aria-label={tp("shop.promotions.listAria")}>
        {promotions.map((promotion) => (
          <ShopCouponTicket
            key={promotion.code}
            promotion={promotion}
            state={busyCode === promotion.code ? "busy" : wallet.codes.has(promotion.code.toUpperCase()) ? "collected" : "idle"}
            onCollect={(code) => void collect(code)}
          />
        ))}
      </ul>
    </StripFrame>
  );
}

/** Theme editor preview: sample coupons, no API / ShopPublicContext / cart. "Collect" is shown disabled ("เร็วๆ นี้"). */
export function ShopCouponStripPreview() {
  const { tp } = usePublicTranslation();
  const now = new Date().toISOString();
  const in2Days = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString();
  const in30Days = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString();
  const samples: PublicPromotion[] = [
    { code: "WELCOME10", name: tp("shop.promotions.sample.welcome"), type: "PERCENT", value: 10, minOrderAmount: 300, maxDiscountAmount: 100, startsAt: now, endsAt: in30Days },
    { code: "SAVE50", name: tp("shop.promotions.sample.save"), type: "FIXED_AMOUNT", value: 50, minOrderAmount: 500, maxDiscountAmount: null, startsAt: now, endsAt: in2Days },
    { code: "FREESHIP", name: tp("shop.promotions.sample.ship"), type: "FREE_SHIPPING", value: 0, minOrderAmount: 0, maxDiscountAmount: null, startsAt: now, endsAt: null },
  ];
  const rail = useCouponRail(samples.length, true);
  return (
    <StripFrame subtitle={tp("shop.promotions.previewNote")} rail={rail}>
      <ul
        ref={rail.scrollerRef}
        className="shop-coupons-list"
        aria-label={tp("shop.promotions.listAria")}
        onPointerDown={rail.onPointerDown}
        onPointerMove={rail.onPointerMove}
        onPointerUp={rail.endDrag}
        onPointerCancel={rail.endDrag}
      >
        {samples.map((promotion) => (
          <ShopCouponTicket key={promotion.code} promotion={promotion} state="preview" />
        ))}
      </ul>
    </StripFrame>
  );
}
