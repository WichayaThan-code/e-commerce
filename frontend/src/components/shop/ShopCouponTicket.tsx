import { Check, Clock3, TicketPercent, Truck } from "lucide-react";
import { Link } from "react-router-dom";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import type { PublicPromotion } from "../../services/api";

/** idle = can collect · busy = request running · collected = in the wallet · preview = theme editor (no action). */
export type CouponTicketState = "idle" | "busy" | "collected" | "preview";

const ENDING_SOON_MS = 3 * 24 * 60 * 60 * 1000;

type Props = {
  promotion: PublicPromotion;
  state: CouponTicketState;
  onCollect?: (code: string) => void;
};

/** One premium "ticket" coupon card. Pure: no API, no context besides i18n, so the theme editor preview can use it. */
export default function ShopCouponTicket({ promotion, state, onCollect }: Props) {
  const { tp, localeTag } = usePublicTranslation();
  const money = (value: number) => Number(value).toLocaleString(localeTag, { maximumFractionDigits: 2 });
  const date = (iso: string) => new Intl.DateTimeFormat(localeTag, { day: "numeric", month: "short", year: "numeric", timeZone: "Asia/Bangkok" }).format(Date.parse(iso));
  const shortDate = (iso: string) => new Intl.DateTimeFormat(localeTag, { day: "numeric", month: "short", timeZone: "Asia/Bangkok" }).format(Date.parse(iso));
  const shipping = promotion.type === "FREE_SHIPPING";
  const value = shipping ? tp("shop.promotions.freeShipping")
    : promotion.type === "PERCENT" ? `${money(promotion.value)}%` : `฿${money(promotion.value)}`;
  const headline = promotion.type === "PERCENT" ? tp("shop.promotions.percentOff", { value: money(promotion.value) })
    : promotion.type === "FIXED_AMOUNT" ? tp("shop.promotions.amountOff", { value: money(promotion.value) })
      : tp("shop.promotions.freeShipping");
  const endsAt = promotion.endsAt ? Date.parse(promotion.endsAt) : NaN;
  const endingSoon = Number.isFinite(endsAt) && endsAt - Date.now() > 0 && endsAt - Date.now() < ENDING_SOON_MS;
  const terms = [
    promotion.minOrderAmount && promotion.minOrderAmount > 0 ? tp("shop.promotions.minSpend", { value: money(promotion.minOrderAmount) }) : tp("shop.promotions.noMin"),
    promotion.maxDiscountAmount != null && !shipping ? tp("shop.promotions.maxDiscount", { value: money(promotion.maxDiscountAmount) }) : null,
  ].filter(Boolean).join(" · ");
  const until = promotion.endsAt ? tp("shop.promotions.untilShort", { date: shortDate(promotion.endsAt) }) : tp("shop.promotions.noExpiryShort");
  const untilFull = promotion.endsAt ? tp("shop.promotions.until", { date: date(promotion.endsAt) }) : tp("shop.promotions.noExpiry");

  return (
    <li className={`shop-ticket${state === "collected" ? " is-collected" : ""}${shipping ? " is-shipping" : ""}`}>
      <div className="shop-ticket-stub" aria-hidden="true">
        {shipping ? <Truck /> : <TicketPercent />}
        <strong className={shipping ? "is-text" : undefined}>{value}</strong>
        <small>{shipping ? tp("shop.promotions.stubShipping") : tp("shop.promotions.stubOff")}</small>
      </div>
      <div className="shop-ticket-main">
        <div className="shop-ticket-top">
          <b className="shop-ticket-title">{promotion.name || headline}</b>
          {endingSoon && <span className="shop-ticket-badge"><Clock3 aria-hidden="true" />{tp("shop.promotions.endingSoon")}</span>}
        </div>
        <span className="sr-only">{headline}</span>
        <small className="shop-ticket-terms">{terms}</small>
        <small className="shop-ticket-terms" title={untilFull}>{until}</small>
        <div className="shop-ticket-foot">
          <code className="shop-ticket-code" title={promotion.code}>{promotion.code}</code>
          {state === "collected" ? (
            <Link className="shop-ticket-btn is-done" to="/cart" aria-label={tp("shop.promotions.useAria", { code: promotion.code })}>
              <Check aria-hidden="true" />{tp("shop.promotions.useNow")}
            </Link>
          ) : (
            <button
              type="button"
              className="shop-ticket-btn"
              disabled={state !== "idle"}
              aria-busy={state === "busy" || undefined}
              title={state === "preview" ? tp("shop.promotions.previewNote") : undefined}
              aria-label={tp("shop.promotions.collectAria", { code: promotion.code })}
              onClick={() => onCollect?.(promotion.code)}
            >
              {state === "preview" ? tp("shop.promotions.previewSoon") : state === "busy" ? tp("shop.promotions.collecting") : tp("shop.promotions.collect")}
            </button>
          )}
        </div>
      </div>
    </li>
  );
}
