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

export function categoryIcon(
  row: { slug?: string | null; nameEn?: string | null },
  template?: ShopThemeTemplateKey,
): LucideIcon {
  const key = categorySlugKey(row);
  return (template ? CATEGORY_ICON_TEMPLATE_OVERRIDES[template]?.[key] : undefined) ?? CATEGORY_ICON_BY_SLUG[key] ?? Tag;
}
