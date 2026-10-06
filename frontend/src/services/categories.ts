import type { Locale } from "../i18n"
import type { ProductCategory } from "./api"

export function labelForCategory(category: ProductCategory, locale: Locale) {
  if (locale === "en") return category.nameEn
  if (locale === "zh") return category.nameZh
  return category.nameTh
}
