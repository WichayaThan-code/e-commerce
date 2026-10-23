import { ApiError } from "../../services/api";

export type CheckoutErrorAction =
  | "INLINE"
  | "REDIRECT_CART"
  | "FOCUS_TERMS"
  | "RESET_PAYMENT"
  | "FOCUS_ADDRESS"
  | "FOCUS_SHIPPING";

export type CheckoutErrorResult = { message: string; action: CheckoutErrorAction; shopId?: number };

type Tp = (key: string, options?: Record<string, unknown>) => string;

const COUPON_CODES = [
  "COUPON_NOT_FOUND",
  "COUPON_INACTIVE",
  "COUPON_NOT_STARTED",
  "COUPON_EXPIRED",
  "COUPON_USAGE_LIMIT_REACHED",
  "COUPON_PER_CUSTOMER_LIMIT_REACHED",
  "COUPON_MIN_SUBTOTAL_NOT_MET",
  "COUPON_LOGIN_REQUIRED",
  "COUPON_NOT_COLLECTED",
  "COUPON_INVALID",
] as const;

const REDIRECT_CART_CODES = [
  "PRICE_CHANGED",
  "OUT_OF_STOCK",
  "INSUFFICIENT_STOCK",
  "PRODUCT_UNAVAILABLE",
  "CART_EMPTY",
] as const;

const SHIPPING_CODES = [
  "SHIPPING_METHOD_NOT_FOUND",
  "SHIPPING_METHOD_REQUIRED",
  "SHIPPING_METHOD_UNAVAILABLE",
  "SHIPPING_NOT_COVERED",
] as const;

const COD_FIELD_CODES = new Set([
  "PAYMENT_METHOD_NOT_AVAILABLE",
  "COD_LIMIT_EXCEEDED",
  "COD_NOT_ALLOWED",
  "COD_NOT_AVAILABLE",
  "COD_MAX_AMOUNT_EXCEEDED",
]);

export const CHECKOUT_ERROR_CODES = [
  ...COUPON_CODES,
  ...REDIRECT_CART_CODES,
  ...SHIPPING_CODES,
  "COD_NOT_ALLOWED",
  "COD_NOT_AVAILABLE",
  "COD_MAX_AMOUNT_EXCEEDED",
  "ORDER_VERSION_CONFLICT",
  "ORDER_NOT_ACCEPTED",
  "ORDER_GENERIC",
  "TERMS_NOT_ACCEPTED",
  "TERMS_VERSION_OUTDATED",
  "CHECKOUT_CONFLICT",
  "OWN_SHOP_PURCHASE_FORBIDDEN",
  "VALIDATION_FAILED",
  "LOGIN_REQUIRED",
] as const;

const COUPON_SET = new Set<string>(COUPON_CODES);
const REDIRECT_SET = new Set<string>(REDIRECT_CART_CODES);
const SHIPPING_SET = new Set<string>(SHIPPING_CODES);

/** Used by cartFormat.couponReasonMessage — first lookup under checkout.errors.*. */
export function messageForCheckoutCouponCode(reason: string, tp: Tp): string | null {
  if (!COUPON_SET.has(reason)) return null;
  return tp(`checkout.errors.${reason}`);
}

function shopIdFrom(error: ApiError): number | undefined {
  const raw = error.details.shopId;
  const n = typeof raw === "number" ? raw : Number(raw);
  return Number.isFinite(n) ? n : undefined;
}

function fieldMessages(error: ApiError): string {
  return (error.fieldErrors ?? []).map((field) => field.message).filter(Boolean).join(" · ");
}

/**
 * Maps checkout/cart API errors to a buyer-facing message + UI action.
 * Pass `fallback` (usually cartErrorMessage) for stock-count copy and unknown codes.
 */
export function checkoutErrorMessage(
  error: unknown,
  tp: Tp,
  fallback: (error: unknown, tp: Tp) => string,
): CheckoutErrorResult {
  if (!(error instanceof ApiError)) {
    return { message: fallback(error, tp), action: "INLINE" };
  }

  const code = error.code;

  if (code === "CHECKOUT_INVALID") {
    const fields = error.fieldErrors ?? [];
    const onlyCod = fields.length > 0 && fields.every((field) => COD_FIELD_CODES.has(field.code) || field.code.startsWith("COD_"));
    if (onlyCod) {
      return {
        message: fieldMessages(error) || error.message || tp("checkout.errors.COD_NOT_AVAILABLE"),
        action: "RESET_PAYMENT",
      };
    }
    return { message: fallback(error, tp), action: "REDIRECT_CART" };
  }

  if (REDIRECT_SET.has(code)) {
    return { message: fallback(error, tp), action: "REDIRECT_CART" };
  }

  if (code === "LOGIN_REQUIRED") {
    return { message: fallback(error, tp), action: "INLINE" };
  }

  if (SHIPPING_SET.has(code)) {
    return {
      message: tp(`checkout.errors.${code}`),
      action: "FOCUS_SHIPPING",
      shopId: shopIdFrom(error),
    };
  }

  if (code.startsWith("COD_") || COD_FIELD_CODES.has(code)) {
    const key = COUPON_SET.has(code) ? code : (
      ["COD_NOT_ALLOWED", "COD_NOT_AVAILABLE", "COD_MAX_AMOUNT_EXCEEDED"].includes(code)
        ? code
        : "COD_NOT_AVAILABLE"
    );
    return {
      message: tp(`checkout.errors.${key}`, { defaultValue: error.message }),
      action: "RESET_PAYMENT",
    };
  }

  if (code === "TERMS_NOT_ACCEPTED" || code === "TERMS_VERSION_OUTDATED") {
    return { message: tp(`checkout.errors.${code}`), action: "FOCUS_TERMS" };
  }

  if (code === "CHECKOUT_CONFLICT") {
    return { message: tp("checkout.errors.CHECKOUT_CONFLICT"), action: "INLINE" };
  }

  if (code === "OWN_SHOP_PURCHASE_FORBIDDEN") {
    return { message: tp("cart.own.checkout"), action: "INLINE" };
  }

  if (code === "VALIDATION_ERROR" || code === "VALIDATION_FAILED" || (error.fieldErrors?.length ?? 0) > 0) {
    const joined = fieldMessages(error);
    return {
      message: joined || tp("checkout.errors.VALIDATION_FAILED"),
      action: "FOCUS_ADDRESS",
    };
  }

  if (COUPON_SET.has(code)) {
    return { message: tp(`checkout.errors.${code}`), action: "INLINE" };
  }

  if (code.startsWith("ORDER_")) {
    const known = code === "ORDER_VERSION_CONFLICT" || code === "ORDER_NOT_ACCEPTED";
    return {
      message: known ? tp(`checkout.errors.${code}`) : tp("checkout.errors.ORDER_GENERIC"),
      action: "INLINE",
    };
  }

  return { message: fallback(error, tp), action: "INLINE" };
}
