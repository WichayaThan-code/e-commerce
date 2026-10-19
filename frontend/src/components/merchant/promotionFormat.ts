import type { Promotion, PromotionStatus, PromotionType, PublicPromotion } from "../../services/api";

/** Asia/Bangkok has no DST: UTC+7 all year. */
const BANGKOK_OFFSET_MS = 7 * 60 * 60 * 1000;
export const ENDING_SOON_MS = 3 * 24 * 60 * 60 * 1000;

/** ISO instant -> value for <input type="datetime-local"> in Bangkok time ("YYYY-MM-DDTHH:mm"). */
export function toBangkokInput(iso: string | null | undefined): string {
  if (!iso) return "";
  const time = Date.parse(iso);
  if (Number.isNaN(time)) return "";
  return new Date(time + BANGKOK_OFFSET_MS).toISOString().slice(0, 16);
}

/** Now in Bangkok as a datetime-local value (minute precision). */
export function bangkokNowInput(offsetDays = 0): string {
  return new Date(Date.now() + BANGKOK_OFFSET_MS + offsetDays * 86_400_000).toISOString().slice(0, 16);
}

export function formatThaiDate(iso: string | null | undefined, locale = "th-TH"): string {
  if (!iso) return "ไม่มีกำหนด";
  const time = Date.parse(iso);
  if (Number.isNaN(time)) return "-";
  return new Intl.DateTimeFormat(locale, { day: "numeric", month: "short", year: "numeric", timeZone: "Asia/Bangkok" }).format(time);
}

export function formatBaht(value: number | null | undefined): string {
  return Number(value ?? 0).toLocaleString("th-TH", { maximumFractionDigits: 2 });
}

export const TYPE_LABEL: Record<PromotionType, "ส่วนลด %" | "ส่วนลดบาท" | "ส่งฟรี"> = {
  PERCENT: "ส่วนลด %",
  FIXED_AMOUNT: "ส่วนลดบาท",
  FREE_SHIPPING: "ส่งฟรี",
};

export const TYPE_CLASS: Record<PromotionType, "percent" | "cash" | "shipping"> = {
  PERCENT: "percent",
  FIXED_AMOUNT: "cash",
  FREE_SHIPPING: "shipping",
};

/** Merchant badge: backend status, plus "ใกล้หมดอายุ" for ACTIVE promotions ending within 3 days. */
export function statusBadge(promotion: Pick<Promotion, "status" | "endsAt">, now = Date.now()): { label: string; className: string } {
  const labels: Record<PromotionStatus, { label: string; className: string }> = {
    ACTIVE: { label: "ใช้งานอยู่", className: "on" },
    UPCOMING: { label: "ยังไม่เริ่ม", className: "upcoming" },
    PAUSED: { label: "หยุดชั่วคราว", className: "paused" },
    EXPIRED: { label: "หมดอายุ", className: "expired" },
  };
  if (promotion.status === "ACTIVE" && promotion.endsAt) {
    const left = Date.parse(promotion.endsAt) - now;
    if (left > 0 && left <= ENDING_SOON_MS) return { label: "ใกล้หมดอายุ", className: "ending" };
  }
  return labels[promotion.status];
}

/** Thai benefit line used by the merchant table ("ลด 10%", "ลด 50 บาท", "ฟรีค่าจัดส่ง"). */
export function benefitText(promotion: Pick<PublicPromotion, "type" | "value">): string {
  if (promotion.type === "PERCENT") return `ลด ${formatBaht(promotion.value)}%`;
  if (promotion.type === "FIXED_AMOUNT") return `ลด ${formatBaht(promotion.value)} บาท`;
  return "ฟรีค่าจัดส่ง";
}
