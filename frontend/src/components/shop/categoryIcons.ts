import {
  BookOpen,
  Baby,
  Car,
  CookingPot,
  Dumbbell,
  Flower2,
  Gem,
  Palette,
  PawPrint,
  Refrigerator,
  Shirt,
  Smartphone,
  Sofa,
  Soup,
  Sparkles,
  Tag,
  UtensilsCrossed,
  Watch,
  type LucideIcon,
} from "lucide-react";
import type { ShopThemeTemplateKey } from "../../theme/shopTheme";

/**
 * ต้องตรงกับ ProductCategorySeeder.slug (backend): lower → [^a-z0-9]+ → "-" → trim "-"
 * ใช้ category.slug จาก API ก่อน ถ้าไม่มีจึงคำนวณจาก nameEn; หมวดที่ไม่รู้จักได้ไอคอน `Tag`
 */
export function categorySlugKey(row: { slug?: string | null; nameEn?: string | null }): string {
  if (row.slug) return row.slug;
  return (row.nameEn || "").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

export const CATEGORY_ICON_BY_SLUG: Record<string, LucideIcon> = {
  "fashion-apparel": Shirt,
  "food-beverages": UtensilsCrossed,
  "beauty-wellness": Sparkles,
  "home-lifestyle": Sofa,
  "thai-crafts-goods": Palette,
  electronics: Smartphone,
  "sports-equipment": Dumbbell,
  "mom-baby": Baby,
  "automotive-parts": Car,
  pets: PawPrint,
  "home-appliances": Refrigerator,
  "books-stationery": BookOpen,
  "jewelry-watches": Watch,
};

/**
 * Template-specific swaps — only where the default glyph fights the theme's mood.
 * Same meaning, different character; every other slug falls back to CATEGORY_ICON_BY_SLUG, then Tag.
 */
export const CATEGORY_ICON_TEMPLATE_OVERRIDES: Partial<Record<ShopThemeTemplateKey, Record<string, LucideIcon>>> = {
  FASHION: {
    "jewelry-watches": Gem,
    "beauty-wellness": Flower2,
  },
  FOOD_BEVERAGE: {
    "food-beverages": Soup,
    "home-appliances": CookingPot,
  },
};

/**
 * Keyword fallback for shop-defined categories whose slug is not a platform seeder slug.
 * Order matters: the first rule whose keyword appears in slug / nameEn / nameTh wins.
 */
const CATEGORY_KEYWORD_ICONS: [string[], LucideIcon][] = [
  [["jewel", "watch", "\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e1b\u0e23\u0e30\u0e14\u0e31\u0e1a", "\u0e19\u0e32\u0e2c\u0e34\u0e01\u0e32"], Watch],
  [["fashion", "apparel", "cloth", "shirt", "dress", "\u0e40\u0e2a\u0e37\u0e49\u0e2d", "\u0e41\u0e1f\u0e0a\u0e31\u0e48\u0e19", "\u0e01\u0e32\u0e07\u0e40\u0e01\u0e07"], Shirt],
  [["food", "beverage", "drink", "snack", "coffee", "\u0e2d\u0e32\u0e2b\u0e32\u0e23", "\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e14\u0e37\u0e48\u0e21", "\u0e02\u0e19\u0e21", "\u0e01\u0e32\u0e41\u0e1f"], UtensilsCrossed],
  [["appliance", "\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e43\u0e0a\u0e49\u0e44\u0e1f\u0e1f\u0e49\u0e32"], Refrigerator],
  [["electronic", "gadget", "phone", "mobile", "computer", "-it-", "\u0e44\u0e2d\u0e17\u0e35", "\u0e2d\u0e34\u0e40\u0e25\u0e47\u0e01\u0e17\u0e23\u0e2d\u0e19\u0e34\u0e01\u0e2a\u0e4c", "\u0e21\u0e37\u0e2d\u0e16\u0e37\u0e2d", "\u0e04\u0e2d\u0e21"], Smartphone],
  [["beauty", "cosmetic", "skin", "wellness", "\u0e04\u0e27\u0e32\u0e21\u0e07\u0e32\u0e21", "\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e2a\u0e33\u0e2d\u0e32\u0e07", "\u0e2a\u0e01\u0e34\u0e19"], Sparkles],
  [["home", "furniture", "living", "decor", "\u0e1a\u0e49\u0e32\u0e19", "\u0e40\u0e1f\u0e2d\u0e23\u0e4c\u0e19\u0e34\u0e40\u0e08\u0e2d\u0e23\u0e4c"], Sofa],
  [["sport", "fitness", "gym", "\u0e01\u0e35\u0e2c\u0e32", "\u0e2d\u0e2d\u0e01\u0e01\u0e33\u0e25\u0e31\u0e07"], Dumbbell],
  [["kid", "baby", "child", "toy", "mom", "\u0e40\u0e14\u0e47\u0e01", "\u0e41\u0e21\u0e48", "\u0e02\u0e2d\u0e07\u0e40\u0e25\u0e48\u0e19"], Baby],
  [["-pet", "dog", "-cat-", "cats", "\u0e2a\u0e31\u0e15\u0e27\u0e4c", "\u0e2b\u0e21\u0e32", "\u0e41\u0e21\u0e27"], PawPrint],
  [["book", "stationer", "\u0e2b\u0e19\u0e31\u0e07\u0e2a\u0e37\u0e2d", "\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e40\u0e02\u0e35\u0e22\u0e19"], BookOpen],
  [["-car-", "-cars-", "auto", "motor", "\u0e23\u0e16", "\u0e22\u0e32\u0e19\u0e22\u0e19\u0e15\u0e4c"], Car],
  [["craft", "-art-", "arts", "handmade", "otop", "\u0e2b\u0e31\u0e15\u0e16\u0e01\u0e23\u0e23\u0e21", "\u0e28\u0e34\u0e25\u0e1b"], Palette],
  [["flower", "plant", "garden", "\u0e14\u0e2d\u0e01\u0e44\u0e21\u0e49", "\u0e15\u0e49\u0e19\u0e44\u0e21\u0e49"], Flower2],
];

export function categoryKeywordIcon(row: { slug?: string | null; nameEn?: string | null; nameTh?: string | null }): LucideIcon | null {
  const haystack = `-${categorySlugKey(row)}- ${(row.nameEn || "").toLowerCase()} ${row.nameTh || ""}`;
  for (const [keywords, icon] of CATEGORY_KEYWORD_ICONS) {
    if (keywords.some((keyword) => haystack.includes(keyword))) return icon;
  }
  return null;
}

export function categoryIcon(
  row: { slug?: string | null; nameEn?: string | null; nameTh?: string | null },
  template?: ShopThemeTemplateKey,
): LucideIcon {
  const key = categorySlugKey(row);
  return (template ? CATEGORY_ICON_TEMPLATE_OVERRIDES[template]?.[key] : undefined) ?? CATEGORY_ICON_BY_SLUG[key] ?? categoryKeywordIcon(row) ?? Tag;
}
