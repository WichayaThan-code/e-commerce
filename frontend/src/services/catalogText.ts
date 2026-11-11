import type { TFunction } from "i18next";
import { ApiError } from "./api";

export function catalogMessage(t: TFunction, error: unknown) {
  if (error instanceof ApiError) {
    // Backend Thai message already names remaining slots — show it verbatim.
    if (error.code === "PRODUCT_QUOTA_EXCEEDED" && error.message?.trim()) return error.message;
    const key = `catalog.errors.${error.code}`;
    const translated = t(key);
    return translated === key ? error.message : translated;
  }
  return t("catalog.errors.REQUEST_FAILED");
}

export function localizedText(locale: string, th?: string | null, en?: string | null, zh?: string | null) {
  if (locale.startsWith("en") && en?.trim()) return en.trim();
  if (locale.startsWith("zh") && zh?.trim()) return zh.trim();
  return th?.trim() || en?.trim() || zh?.trim() || "";
}

export function formatMoney(value: number | string | null | undefined, locale: string) {
  if (value == null || value === "") return "—";
  const amount = Number(value);
  if (Number.isNaN(amount)) return String(value);
  return new Intl.NumberFormat(locale.startsWith("en") ? "en-US" : locale.startsWith("zh") ? "zh-CN" : "th-TH", {
    style: "currency",
    currency: "THB",
    maximumFractionDigits: 2,
  }).format(amount);
}

export function formatWhen(value: string | null | undefined, locale: string) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat(locale.startsWith("en") ? "en-US" : locale.startsWith("zh") ? "zh-CN" : "th-TH", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}
