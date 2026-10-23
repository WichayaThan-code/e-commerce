import { FEATURES } from "../config/features";
import { TERMS_VERSION } from "../components/checkout/termsContent";
import { ApiError, request, type CheckoutAddress, type CheckoutExtras, type OrderDetail } from "./api";

// TODO(backend, Supitcha): planned contracts — do not call while flag is off
export type DeliveryEstimate = { orderId: string; minDate: string; maxDate: string; source: "LOCAL" | "API" };
export type ShippingCoverage = { shopId: number; methodId: string; covered: boolean; reason?: string | null };
export type { CheckoutExtras };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function addBusinessDays(from: Date, days: number): Date {
  const cursor = new Date(from);
  let left = days;
  while (left > 0) {
    cursor.setDate(cursor.getDate() + 1);
    if (cursor.getDay() !== 0) left -= 1; // skip Sunday
  }
  return cursor;
}

function toIsoDate(value: Date): string {
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, "0");
  const d = String(value.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function localEstimate(order: OrderDetail): DeliveryEstimate {
  const placed = order.placedAt ? new Date(order.placedAt) : new Date();
  const start = Number.isNaN(placed.getTime()) ? new Date() : placed;
  const carrier = (order.shipping?.carrierCode ?? order.shipment?.carrierCode ?? "").toUpperCase();
  const ems = carrier.includes("THAIPOST") || carrier.includes("EMS") || carrier === "THP";
  const minDays = ems ? 2 : 3;
  const maxDays = ems ? 4 : 5;
  return {
    orderId: order.id,
    minDate: toIsoDate(addBusinessDays(start, minDays)),
    maxDate: toIsoDate(addBusinessDays(start, maxDays)),
    source: "LOCAL",
  };
}

/** Flag off → local window from placedAt + carrier; flag on → API with local fallback (never throws to UI). */
export async function estimateDelivery(order: OrderDetail): Promise<DeliveryEstimate> {
  if (!FEATURES.deliveryEstimateApi) return localEstimate(order);
  try {
    return await request<DeliveryEstimate>(`/api/orders/${order.id}/delivery-estimate`, { cache: "no-store" }, true);
  } catch {
    return localEstimate(order);
  }
}

/** Flag off → always covered (no network). */
export async function checkShippingCoverage(
  shopId: number,
  methodId: string,
  address: Pick<CheckoutAddress, "postalCode">,
): Promise<ShippingCoverage> {
  if (!FEATURES.shippingCoverageApi) {
    return { shopId, methodId, covered: true };
  }
  const query = new URLSearchParams({
    shopId: String(shopId),
    methodId,
    postalCode: address.postalCode,
  });
  try {
    return await request<ShippingCoverage>(`/api/checkout/shipping-coverage?${query}`, { cache: "no-store" }, true);
  } catch (error) {
    if (error instanceof ApiError) {
      return { shopId, methodId, covered: false, reason: error.message };
    }
    return { shopId, methodId, covered: false, reason: null };
  }
}

/** Flag off → {}. Never include empty optional keys. */
export function buildCheckoutExtras(opts: { addressId?: string | null }): CheckoutExtras {
  const extras: CheckoutExtras = {};
  if (FEATURES.termsVersionApi) extras.termsVersion = TERMS_VERSION;
  if (FEATURES.addressIdCheckout && opts.addressId && UUID.test(opts.addressId)) {
    extras.shippingAddressId = opts.addressId;
  }
  return extras;
}
