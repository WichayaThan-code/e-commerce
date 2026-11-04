/** Locale-aware date for article cards / detail (empty string for missing or invalid values). */
export function formatArticleDate(value: string | null | undefined, localeTag: string): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat(localeTag, { dateStyle: "medium" }).format(date);
}
