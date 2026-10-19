import { useEffect, useRef } from "react";
import { useLocation } from "react-router-dom";
import { Copy, TicketPercent, Truck } from "lucide-react";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { useShopPromotions } from "../../hooks/useShopPromotions";
import type { PublicPromotion } from "../../services/api";
import { PROMO_COUPONS_ANCHOR } from "./promoTargets";
import "../../styles/shop-coupons.css";

type Props = {
  slug: string;
  /** Storefront toast (catalog.onNotify). */
  onNotify?: (message: string) => void;
};

async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch { /* fall through to the textarea fallback */ }
  try {
    const area = document.createElement("textarea");
    area.value = text;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.select();
    const ok = document.execCommand("copy");
    area.remove();
    return ok;
  } catch {
    return false;
  }
}

/**
 * Public coupon strip (ShopPage only — not in the theme editor preview, which has no ShopPublicProvider).
 * Shows codes only: redeeming at checkout is not implemented yet. Theme colours via --shop-* variables.
 */
export default function ShopCouponStrip({ slug, onNotify }: Props) {
  const { tp, localeTag } = usePublicTranslation();
  const location = useLocation();
  const { promotions, ready } = useShopPromotions(slug);
  const scrolled = useRef(false);

  // Deep link (/shop/x#shop-promotions from another tab page): scroll once the list has rendered.
  useEffect(() => {
    if (!ready || !promotions.length || scrolled.current) return;
    if (location.hash !== `#${PROMO_COUPONS_ANCHOR}`) return;
    scrolled.current = true;
    requestAnimationFrame(() => document.getElementById(PROMO_COUPONS_ANCHOR)?.scrollIntoView({ behavior: "smooth", block: "start" }));
  }, [ready, promotions.length, location.hash]);

  if (!promotions.length) return null;

  const money = (value: number) => Number(value).toLocaleString(localeTag, { maximumFractionDigits: 2 });
  const date = (iso: string) => new Intl.DateTimeFormat(localeTag, { day: "numeric", month: "short", year: "numeric", timeZone: "Asia/Bangkok" }).format(Date.parse(iso));
  const headline = (promotion: PublicPromotion) =>
    promotion.type === "PERCENT" ? tp("shop.promotions.percentOff", { value: money(promotion.value) })
      : promotion.type === "FIXED_AMOUNT" ? tp("shop.promotions.amountOff", { value: money(promotion.value) })
        : tp("shop.promotions.freeShipping");
  const copy = async (code: string) => {
    const ok = await copyText(code);
    onNotify?.(ok ? tp("shop.promotions.copied", { code }) : tp("shop.promotions.copyFailed", { code }));
  };

  return (
    <section id={PROMO_COUPONS_ANCHOR} className="shop-coupons recommend-width" aria-labelledby={`${PROMO_COUPONS_ANCHOR}-title`}>
      <header className="shop-coupons-head">
        <h2 id={`${PROMO_COUPONS_ANCHOR}-title`}>{tp("shop.promotions.title")}</h2>
        <p>{tp("shop.promotions.checkoutSoon")}</p>
      </header>
      <ul className="shop-coupons-list">
        {promotions.map((promotion) => (
          <li key={promotion.code} className="shop-coupon">
            <span className="shop-coupon-icon" aria-hidden="true">{promotion.type === "FREE_SHIPPING" ? <Truck /> : <TicketPercent />}</span>
            <div className="shop-coupon-body">
              <b>{headline(promotion)}</b>
              <small className="shop-coupon-name">{promotion.name}</small>
              <small className="shop-coupon-terms">
                {promotion.minOrderAmount && promotion.minOrderAmount > 0
                  ? tp("shop.promotions.minSpend", { value: money(promotion.minOrderAmount) })
                  : tp("shop.promotions.noMin")}
                {promotion.maxDiscountAmount != null && <> · {tp("shop.promotions.maxDiscount", { value: money(promotion.maxDiscountAmount) })}</>}
              </small>
              <small className="shop-coupon-terms">{promotion.endsAt ? tp("shop.promotions.until", { date: date(promotion.endsAt) }) : tp("shop.promotions.noExpiry")}</small>
            </div>
            <div className="shop-coupon-code">
              <code>{promotion.code}</code>
              <button type="button" onClick={() => void copy(promotion.code)} aria-label={tp("shop.promotions.copyAria", { code: promotion.code })}>
                <Copy aria-hidden="true" />{tp("shop.promotions.copy")}
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
