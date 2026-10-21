import { useState } from "react";
import { Link } from "react-router-dom";
import { Check, Tag } from "lucide-react";
import { useCouponWallet } from "../../hooks/useCouponWallet";
import { useShopPromotions } from "../../hooks/useShopPromotions";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { Skeleton } from "../feedback/Skeleton";
import { couponReasonMessage, formatBaht } from "./cartFormat";
import { moneyValue, type CartGroup, type PublicPromotion } from "../../services/api";
import { shopPath } from "../../services/shopRoutes";
import { PROMO_COUPONS_ANCHOR } from "../shop/promoTargets";

const VISIBLE = 3;

type Offer = { promo: PublicPromotion; eligible: boolean; missing: number; saving: number };

/** Estimated saving for sorting only; the real discount always comes from the cart API totals. */
function estimate(promo: PublicPromotion, subtotal: number, shippingFee: number) {
  if (promo.type === "FREE_SHIPPING") return shippingFee;
  if (promo.type === "PERCENT") {
    const raw = (subtotal * Number(promo.value)) / 100;
    return promo.maxDiscountAmount != null ? Math.min(raw, Number(promo.maxDiscountAmount)) : raw;
  }
  return Math.min(Number(promo.value), subtotal);
}

/**
 * Coupons the buyer COLLECTED for this shop (GET /api/me/coupons?shopId=, status USABLE; includes private codes the
 * buyer typed before) shown inside the summary coupon box: eligible first, best saving first, 3 visible + "ดูทั้งหมด".
 * One coupon per shop (backend rule): tapping another code replaces the applied one. Public codes not collected yet
 * are only counted for the "collect more at the shop" hint (useShopPromotions). Design unchanged from P3.
 */
export default function CartCouponOffers({ group, busy, onApply }: { group: CartGroup; busy: boolean; onApply: (code: string) => void }) {
  const { tp, localeTag } = usePublicTranslation();
  const { promotions: publicCodes, ready: publicReady } = useShopPromotions(group.shopSlug);
  // refreshKey: a private code typed in the cart is auto-collected by the backend, re-read the wallet after apply
  const wallet = useCouponWallet(group.shopId, group.coupon?.code ?? null);
  const ready = publicReady && wallet.ready;
  const promotions: PublicPromotion[] = wallet.coupons.filter((row) => row.status === "USABLE").map((row) => ({
    code: row.code, name: row.name, type: row.type, value: Number(row.value),
    minOrderAmount: row.minOrderAmount ?? null, maxDiscountAmount: row.maxDiscountAmount ?? null,
    startsAt: row.startsAt ?? row.collectedAt, endsAt: row.endsAt ?? null,
  }));
  const uncollected = publicCodes.filter((promo) => promo.code && !wallet.codes.has(promo.code.toUpperCase())).length;
  const shopCoupons = `${shopPath(group.shopSlug)}#${PROMO_COUPONS_ANCHOR}`;
  const hint = !wallet.signedIn
    ? (publicCodes.length > 0 ? <small className="cart-offers-hint"><Link to={`/login?next=${encodeURIComponent(shopCoupons)}`}>{tp("cart.coupon.loginToCollect")}</Link></small> : null)
    : uncollected > 0 ? <small className="cart-offers-hint"><Link to={shopCoupons}>{tp("cart.coupon.collectMore", { count: uncollected })}</Link></small> : null;
  const [expanded, setExpanded] = useState(false);
  const money = (value: number) => formatBaht(value, localeTag);
  const subtotal = moneyValue(group.totals.subtotal);
  const shippingFee = moneyValue(group.totals.shippingFee);
  const applied = group.coupon;

  if (!ready) {
    return <div className="cart-offers" aria-busy="true">{[0, 1].map((key) => <Skeleton key={key} className="cart-offer-skel" height={46} radius={9}/>)}</div>;
  }
  const offers: Offer[] = promotions.filter((promo) => promo.code).map((promo) => {
    const min = promo.minOrderAmount != null ? Number(promo.minOrderAmount) : 0;
    const missing = Math.max(0, min - subtotal);
    return { promo, eligible: missing === 0, missing, saving: estimate(promo, subtotal, shippingFee) };
  }).sort((a, b) => Number(b.eligible) - Number(a.eligible) || b.saving - a.saving || a.missing - b.missing);
  if (!offers.length) return hint ? <div className="cart-offers">{hint}</div> : null;
  const shown = expanded ? offers : offers.slice(0, VISIBLE);

  const benefit = (promo: PublicPromotion) => promo.type === "FREE_SHIPPING"
    ? tp("cart.coupon.benefit.freeShipping")
    : promo.type === "PERCENT"
      ? promo.maxDiscountAmount != null
        ? tp("cart.coupon.benefit.percentMax", { value: Number(promo.value), max: money(Number(promo.maxDiscountAmount)) })
        : tp("cart.coupon.benefit.percent", { value: Number(promo.value) })
      : tp("cart.coupon.benefit.fixed", { amount: money(Number(promo.value)) });
  const date = (iso: string) => new Intl.DateTimeFormat(localeTag, { day: "numeric", month: "short", timeZone: "Asia/Bangkok" }).format(new Date(iso));

  return <div className="cart-offers">
    <ul aria-label={tp("cart.coupon.available")}>
      {shown.map(({ promo, eligible, missing }) => {
        const isApplied = applied?.code === promo.code;
        const meta = [
          promo.minOrderAmount != null && Number(promo.minOrderAmount) > 0 ? tp("cart.coupon.minOrder", { amount: money(Number(promo.minOrderAmount)) }) : null,
          promo.endsAt ? tp("cart.coupon.endsAt", { date: date(promo.endsAt) }) : null,
        ].filter(Boolean).join(" · ");
        return <li key={promo.code}>
          <button type="button" className={`cart-offer${isApplied ? " is-applied" : ""}${eligible ? "" : " is-locked"}`} disabled={busy || !eligible || isApplied} aria-pressed={isApplied} onClick={() => onApply(promo.code)}>
            <span className="cart-offer-code num"><Tag size={12}/>{promo.code}</span>
            <span className="cart-offer-copy"><b>{benefit(promo)}</b>{meta ? <small>{meta}</small> : null}</span>
            <span className="cart-offer-state">{isApplied ? <><Check size={12}/>{tp("cart.coupon.inUse")}</> : eligible ? tp("cart.coupon.use") : <span className="num">{tp("cart.coupon.missing", { amount: money(missing) })}</span>}</span>
          </button>
          {isApplied && applied && !applied.applicable ? <small className="cart-line-notice">{couponReasonMessage(applied.reason, tp, applied.message)}</small> : null}
        </li>;
      })}
    </ul>
    {hint}
    {offers.length > VISIBLE && <button type="button" className="cart-offers-more" onClick={() => setExpanded((value) => !value)}>{expanded ? tp("cart.coupon.showLess") : tp("cart.coupon.showAll", { count: offers.length })}</button>}
  </div>;
}
