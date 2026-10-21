import { ApiError, moneyValue, type AuthUser, type Money } from "../../services/api";

/** True when the signed-in user is a member/staff of the shop (public shop number). */
export function isOwnShop(user: AuthUser | null | undefined, shopNo: number | null | undefined) {
  return !!user && shopNo != null && (user.shopPermissions ?? []).some((grant) => grant.shopId === shopNo);
}

/** ฿ amount in the active locale (2 decimals only when needed). Used by cart, checkout and success pages. */
export function formatBaht(value: Money | null | undefined, localeTag: string) {
  const amount = moneyValue(value);
  const tag = localeTag.startsWith("en") ? "en-US" : localeTag.startsWith("zh") ? "zh-CN" : "th-TH";
  const fraction = Math.round(amount * 100) % 100 !== 0;
  return `฿${new Intl.NumberFormat(tag, { minimumFractionDigits: fraction ? 2 : 0, maximumFractionDigits: 2 }).format(amount)}`;
}

/** Error codes that have a translated message under public.json → cart.errors.* */
const KNOWN_ERRORS = new Set([
  "COUPON_NOT_FOUND", "COUPON_INACTIVE", "COUPON_NOT_STARTED", "COUPON_EXPIRED", "COUPON_USAGE_LIMIT_REACHED",
  "COUPON_PER_CUSTOMER_LIMIT_REACHED", "COUPON_MIN_SUBTOTAL_NOT_MET", "COUPON_INVALID", "COUPON_NOT_COLLECTED", "COUPON_LOGIN_REQUIRED", "SHOP_NOT_FOUND", "CART_EMPTY", "CART_FULL",
  "INSUFFICIENT_STOCK", "OUT_OF_STOCK", "PRICE_CHANGED", "PRODUCT_UNAVAILABLE", "CHECKOUT_INVALID", "NETWORK_ERROR",
]);

/** Translated message for an API error code; falls back to the server message (already localized by Accept-Language). */
export function cartErrorMessage(error: unknown, tp: (key: string, options?: Record<string, unknown>) => string) {
  if (error instanceof ApiError && error.code === "INSUFFICIENT_STOCK") {
    // 409 body (CartExceptionHandler.StockError): availableQuantity, inCartQuantity, canAddQuantity → Shopee-style "เหลือ N ชิ้น"
    const available = Number(error.details.availableQuantity);
    const inCart = Number(error.details.inCartQuantity);
    if (Number.isFinite(available)) {
      if (available <= 0) return tp("cart.errors.stockNone");
      return Number.isFinite(inCart) && inCart > 0
        ? tp("cart.errors.stockLeftInCart", { count: available, inCart })
        : tp("cart.errors.stockLeft", { count: available });
    }
  }
  if (error instanceof ApiError && error.code === "OWN_SHOP_PURCHASE_FORBIDDEN") {
    return tp("cart.own.toast");
  }
  if (error instanceof ApiError && error.code === "TERMS_NOT_ACCEPTED") {
    return tp("checkout.terms.rejected");
  }
  if (error instanceof ApiError) {
    if (KNOWN_ERRORS.has(error.code)) return tp(`cart.errors.${error.code}`);
    return error.message || tp("cart.errors.generic");
  }
  return tp("cart.errors.generic");
}

export function couponReasonMessage(reason: string | null | undefined, tp: (key: string, options?: Record<string, unknown>) => string, fallback?: string | null) {
  if (reason && KNOWN_ERRORS.has(reason)) return tp(`cart.errors.${reason}`);
  return fallback || tp("cart.errors.generic");
}

/** Stable per-attempt key for POST /api/checkout (8–100 chars, [A-Za-z0-9._:-]). */
const CHECKOUT_KEY = "thaishopfun.checkoutKey";
export function checkoutKey() {
  try {
    const existing = sessionStorage.getItem(CHECKOUT_KEY);
    if (existing) return existing;
    const fresh = `chk-${crypto.randomUUID()}`;
    sessionStorage.setItem(CHECKOUT_KEY, fresh);
    return fresh;
  } catch {
    return `chk-${crypto.randomUUID()}`;
  }
}
export function clearCheckoutKey() {
  try { sessionStorage.removeItem(CHECKOUT_KEY); } catch { /* storage unavailable */ }
}

/** Persists checkout address/note across a trip to /cart (never stores acceptTerms). */
const CHECKOUT_FORM_KEY = "thaishopfun.checkoutForm";

export type CheckoutFormData = {
  contact: { recipientName: string; phone: string; addressLine1: string; addressLine2: string };
  area: { province: string; district: string; subdistrict: string; postalCode: string };
  note: string;
};

type CheckoutFormStored = CheckoutFormData & { userId: string };

export function loadCheckoutForm(userId: string): CheckoutFormData | null {
  try {
    const raw = sessionStorage.getItem(CHECKOUT_FORM_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CheckoutFormStored;
    if (!parsed || typeof parsed !== "object" || parsed.userId !== userId) {
      if (parsed && parsed.userId !== userId) clearCheckoutForm();
      return null;
    }
    if (!parsed.contact || !parsed.area) { clearCheckoutForm(); return null; }
    return { contact: parsed.contact, area: parsed.area, note: typeof parsed.note === "string" ? parsed.note : "" };
  } catch {
    clearCheckoutForm();
    return null;
  }
}

export function saveCheckoutForm(userId: string, data: CheckoutFormData) {
  try {
    const payload: CheckoutFormStored = { userId, contact: data.contact, area: data.area, note: data.note };
    sessionStorage.setItem(CHECKOUT_FORM_KEY, JSON.stringify(payload));
  } catch { /* storage unavailable */ }
}

export function clearCheckoutForm() {
  try { sessionStorage.removeItem(CHECKOUT_FORM_KEY); } catch { /* storage unavailable */ }
}
