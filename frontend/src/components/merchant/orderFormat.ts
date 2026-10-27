import type { TFunction } from "i18next";
import { ApiError } from "../../services/api";

export const BANGKOK_TZ = "Asia/Bangkok";

export const ORDER_STATUSES = [
  "PENDING_PAYMENT",
  "PAID_AWAITING_MERCHANT_CONFIRM",
  "MERCHANT_CONFIRMED",
  "READY_TO_PACK",
  "PACKING",
  "PACKED",
  "DISPATCHED",
  "DELIVERED",
  "COMPLETED",
  "CANCELLED",
  "REFUND_REQUESTED",
  "REFUNDED",
] as const;

export const PAYMENT_STATUSES = ["UNPAID", "PENDING", "PAID", "REFUNDED", "PARTIALLY_REFUNDED", "FAILED"] as const;
export const FULFILLMENT_STATUSES = ["UNFULFILLED", "PARTIAL", "FULFILLED", "RETURNED"] as const;

/** YYYY-MM-DD of an instant in Asia/Bangkok (what the order list `from` / `to` filters expect). */
export function bangkokDate(date: Date) {
  return new Intl.DateTimeFormat("en-CA", { timeZone: BANGKOK_TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
}

export type PeriodKey = "all" | "7d" | "30d" | "month";

export function periodRange(period: PeriodKey, now = new Date()): { from?: string; to?: string } {
  if (period === "all") return {};
  const to = bangkokDate(now);
  if (period === "month") return { from: `${to.slice(0, 8)}01`, to };
  const days = period === "7d" ? 6 : 29;
  return { from: bangkokDate(new Date(now.getTime() - days * 86_400_000)), to };
}

function localeFor(localeTag: string) {
  return localeTag.startsWith("en") ? "en-US" : localeTag.startsWith("zh") ? "zh-CN" : "th-TH";
}

export function formatBangkokDateTime(value: string | null | undefined, localeTag: string) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat(localeFor(localeTag), { timeZone: BANGKOK_TZ, dateStyle: "medium", timeStyle: "short" }).format(date);
}

/** date + time as two short strings (list cell shows them on two lines). */
export function formatBangkokParts(value: string | null | undefined, localeTag: string): [string, string] | null {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  const lng = localeFor(localeTag);
  return [
    new Intl.DateTimeFormat(lng, { timeZone: BANGKOK_TZ, day: "numeric", month: "short", year: "numeric" }).format(date),
    new Intl.DateTimeFormat(lng, { timeZone: BANGKOK_TZ, hour: "2-digit", minute: "2-digit", hour12: false }).format(date),
  ];
}

export type PillTone = "paid" | "pending" | "waiting" | "shipping" | "delivered" | "cancelled";

export function statusTone(status?: string | null): PillTone {
  switch (status) {
    case "COMPLETED":
    case "DELIVERED":
      return "delivered";
    case "DISPATCHED":
      return "shipping";
    case "CANCELLED":
    case "REFUNDED":
      return "cancelled";
    case "MERCHANT_CONFIRMED":
    case "READY_TO_PACK":
    case "PACKING":
    case "PACKED":
      return "shipping";
    default:
      return "waiting";
  }
}

export function paymentTone(status?: string | null): PillTone {
  switch (status) {
    case "PAID":
    case "CAPTURED":
      return "paid";
    case "REFUNDED":
    case "PARTIALLY_REFUNDED":
    case "FAILED":
    case "CANCELLED":
      return "cancelled";
    default:
      return "pending";
  }
}

/** Translated message for an order / payment / shipment API error; unknown codes get the generic translated message (never raw server text). */
export function orderErrorMessage(t: TFunction, error: unknown) {
  if (error instanceof ApiError) {
    const key = `orderManagement.errors.${error.code}`;
    const translated = t(key, { defaultValue: "" });
    return translated || t("orderManagement.errors.REQUEST_FAILED");
  }
  return t("orderManagement.errors.REQUEST_FAILED");
}

export function isStaleOrderError(error: unknown) {
  return error instanceof ApiError && (error.code === "ORDER_VERSION_CONFLICT" || error.code === "INVALID_STATUS_TRANSITION" || error.code === "SHIPMENT_VERSION_CONFLICT");
}

export function numberOf(value: number | string | null | undefined) {
  if (value == null || value === "") return 0;
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : 0;
}

/** yyyy-MM-ddTHH:mm of "now" in Bangkok, for datetime-local inputs (the backend reads it as Bangkok time). */
export function bangkokNowInput(now = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: BANGKOK_TZ, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false,
  }).formatToParts(now);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "00";
  const hour = get("hour") === "24" ? "00" : get("hour");
  return `${get("year")}-${get("month")}-${get("day")}T${hour}:${get("minute")}`;
}
