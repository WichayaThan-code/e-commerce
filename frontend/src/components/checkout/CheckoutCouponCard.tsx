import { useState } from "react";
import { Tag } from "lucide-react";
import CartCouponOffers from "../cart/CartCouponOffers";
import { cartErrorMessage, couponReasonMessage, formatBaht } from "../cart/cartFormat";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { moneyValue, type CartGroup } from "../../services/api";

type Props = {
  groups: CartGroup[];
  busy: boolean;
  disabled?: boolean;
  onApply: (shopId: number, code: string) => Promise<void>;
  onRemove: (shopId: number) => Promise<void>;
};

export default function CheckoutCouponCard({ groups, busy, disabled, onApply, onRemove }: Props) {
  const { tp, localeTag } = usePublicTranslation();
  const money = (value: unknown) => formatBaht(value as number, localeTag);
  const [shopId, setShopId] = useState<number | null>(groups[0]?.shopId ?? null);
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [working, setWorking] = useState(false);
  const target = groups.find((group) => group.shopId === shopId) ?? groups[0] ?? null;
  const locked = busy || disabled || working;

  if (!groups.length) return null;

  const apply = async (nextCode?: string) => {
    if (!target || locked) return;
    const value = (nextCode ?? code).trim();
    if (!value) return;
    setWorking(true);
    setError("");
    try {
      await onApply(target.shopId, value);
      setCode("");
    } catch (caught) {
      setError(cartErrorMessage(caught, tp));
    } finally {
      setWorking(false);
    }
  };

  const remove = async (id: number) => {
    if (locked) return;
    setWorking(true);
    setError("");
    try {
      await onRemove(id);
    } catch (caught) {
      setError(cartErrorMessage(caught, tp));
    } finally {
      setWorking(false);
    }
  };

  return (
    <section className="checkout-card checkout-coupon-card" aria-labelledby="checkout-coupon">
      <h2 id="checkout-coupon"><Tag size={18} />{tp("checkout.coupon.title")}</h2>
      <p className="checkout-coupon-lead">{tp("checkout.coupon.hint")}</p>

      {groups.length > 1 ? (
        <label className="checkout-field">
          <span>{tp("cart.coupon.shop")}</span>
          <select
            value={target?.shopId ?? ""}
            disabled={locked}
            onChange={(event) => setShopId(Number(event.target.value))}
          >
            {groups.map((group) => (
              <option key={group.shopId} value={group.shopId}>{group.shopName}</option>
            ))}
          </select>
        </label>
      ) : null}

      {groups.filter((group) => group.coupon).map((group) => {
        const applied = group.coupon!;
        return (
          <p key={group.shopId} className="checkout-meta checkout-coupon-applied">
            <span>
              {tp("cart.coupon.code", { code: applied.code })}
              {groups.length > 1 ? ` · ${group.shopName}` : ""}
              <button type="button" className="cart-clear-code" disabled={locked} onClick={() => void remove(group.shopId)}>
                {tp("cart.coupon.remove")}
              </button>
              {!applied.applicable ? (
                <small className="cart-line-notice">{couponReasonMessage(applied.reason, tp, applied.message)}</small>
              ) : null}
            </span>
            <b className="cart-saving num">
              −{money(moneyValue(applied.discountAmount) + moneyValue(applied.shippingDiscountAmount))}
            </b>
          </p>
        );
      })}

      {target ? (
        <>
          <div className="checkout-coupon-input">
            <input
              className="num"
              value={code}
              disabled={locked}
              placeholder={tp("cart.coupon.input")}
              aria-label={tp("cart.coupon.input")}
              onChange={(event) => setCode(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  void apply();
                }
              }}
            />
            <button type="button" disabled={locked || !code.trim()} onClick={() => void apply()}>
              {tp("cart.coupon.apply")}
            </button>
          </div>
          <CartCouponOffers
            group={target}
            busy={locked}
            onApply={(value) => void apply(value)}
          />
        </>
      ) : null}

      {error ? <p className="checkout-problem" role="alert">{error}</p> : null}
    </section>
  );
}
