import { categoryEmoji } from "../shop/categoryIcons";
import type { ProductCategory } from "../../services/api";

/** Design grid is 11 cells on Marketplace (10 categories + “more”). */
export const CATEGORY_SLOT_COUNT = 10;

export type CategoryTile = {
  key: string;
  label: string;
  emoji: string;
  isMore: boolean;
  categoryId: number | undefined;
};

export function localizedCategory(c: ProductCategory, localeTag: string) {
  if (localeTag.startsWith("en") && c.nameEn) return c.nameEn;
  if (localeTag.startsWith("zh") && c.nameZh) return c.nameZh;
  return c.nameTh;
}

/** Soft wrap long category labels into two lines for the existing `<span>` split. */
export function categoryLabelLines(name: string) {
  const trimmed = name.trim();
  if (trimmed.includes("\n")) return trimmed;
  const parts = trimmed.split(/\s+/);
  if (parts.length >= 2 && trimmed.length > 10) {
    const mid = Math.ceil(parts.length / 2);
    return `${parts.slice(0, mid).join(" ")}\n${parts.slice(mid).join(" ")}`;
  }
  return trimmed;
}

/** Marketplace home tiles: up to 10 categories + “อื่นๆ” (more). */
export function buildMarketplaceCategoryTiles(
  categories: ProductCategory[] | null | undefined,
  fallbackLabels: string[],
  localeTag: string,
): CategoryTile[] {
  const moreLabel = fallbackLabels[fallbackLabels.length - 1] ?? "อื่นๆ";
  const rows = (categories && categories.length > 0 ? categories : []).slice(0, CATEGORY_SLOT_COUNT);
  if (rows.length === 0) {
    return fallbackLabels.map((label, index) => {
      const isMore = index === fallbackLabels.length - 1;
      const flat = label.replace(/\n/g, " ");
      return {
        key: `fallback-${index}`,
        label,
        emoji: isMore ? "" : categoryEmoji({ nameTh: flat, nameEn: flat }),
        isMore,
        categoryId: undefined as number | undefined,
      };
    });
  }
  return [
    ...rows.map((row) => ({
      key: String(row.id),
      label: categoryLabelLines(localizedCategory(row, localeTag)),
      emoji: categoryEmoji(row),
      isMore: false,
      categoryId: row.id as number | undefined,
    })),
    {
      key: "more",
      label: moreLabel,
      emoji: "",
      isMore: true,
      categoryId: undefined as number | undefined,
    },
  ];
}

/**
 * Recommended page tiles: “ทั้งหมด” + the same category slots as Marketplace (no “more”).
 * Fallback tiles without id do not filter when clicked.
 */
export function buildRecommendCategoryTiles(
  categories: ProductCategory[] | null | undefined,
  fallbackLabels: string[],
  localeTag: string,
  allLabel: string,
): CategoryTile[] {
  const marketplace = buildMarketplaceCategoryTiles(categories, fallbackLabels, localeTag).filter((tile) => !tile.isMore);
  return [
    {
      key: "all",
      label: allLabel,
      emoji: "",
      isMore: false,
      categoryId: undefined,
    },
    ...marketplace,
  ];
}

/** Parse `?category=` — only plain positive integers; otherwise undefined (treat as all). */
export function parseCategoryParam(value: string | null | undefined): number | undefined {
  if (!value) return undefined;
  if (!/^\d+$/.test(value)) return undefined;
  const id = Number(value);
  return Number.isFinite(id) ? id : undefined;
}

/** Match a hub category by Thai/EN/ZH name hints (case-insensitive substring). */
export function findCategoryIdByHints(
  rows: ProductCategory[] | null | undefined,
  hints: string[],
): number | undefined {
  if (!rows?.length || hints.length === 0) return undefined;
  const needles = hints.map((hint) => hint.trim().toLowerCase()).filter(Boolean);
  if (!needles.length) return undefined;
  const found = rows.find((row) => {
    const hay = `${row.nameTh} ${row.nameEn ?? ""} ${row.nameZh ?? ""}`.toLowerCase();
    return needles.some((needle) => hay.includes(needle));
  });
  return found?.id;
}
