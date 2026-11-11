import {
  Apple,
  Award,
  Baby,
  Bone,
  BookOpen,
  Cake,
  Coffee,
  CookingPot,
  CupSoda,
  Dumbbell,
  Flower2,
  Footprints,
  Gem,
  GlassWater,
  Headphones,
  Home,
  Lamp,
  Laptop,
  Leaf,
  Microwave,
  Milk,
  Palette,
  PawPrint,
  Refrigerator,
  Scissors,
  Shirt,
  ShoppingBag,
  Sofa,
  Soup,
  Sparkles,
  Tag,
  UtensilsCrossed,
  Watch,
  Wheat,
  Wrench,
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

/** Lucide counterparts of Marketplace emoji tiles (monochrome — no color fill). */
export const CATEGORY_ICON_BY_SLUG: Record<string, LucideIcon> = {
  "fashion-apparel": Shirt,
  "food-beverages": UtensilsCrossed,
  "beauty-wellness": Sparkles,
  "home-lifestyle": Home,
  "thai-crafts-goods": Palette,
  electronics: Laptop,
  "sports-equipment": Dumbbell,
  "mom-baby": Baby,
  "automotive-parts": Wrench,
  pets: PawPrint,
  "home-appliances": Refrigerator,
  "books-stationery": BookOpen,
  "jewelry-watches": Watch,
  "otop-products": Award,
};

/**
 * Template-specific swaps — only where the default glyph fights the theme's mood.
 * Same meaning, different character; every other slug falls back to CATEGORY_ICON_BY_SLUG, then keywords, then Tag.
 */
export const CATEGORY_ICON_TEMPLATE_OVERRIDES: Partial<Record<ShopThemeTemplateKey, Record<string, LucideIcon>>> = {
  FASHION: {
    "fashion-apparel": Shirt,
    "jewelry-watches": Gem,
    "beauty-wellness": Flower2,
    "home-lifestyle": Lamp,
    "thai-crafts-goods": Scissors,
    electronics: Headphones,
    "sports-equipment": Footprints,
    "mom-baby": Baby,
    "books-stationery": BookOpen,
  },
  FOOD_BEVERAGE: {
    "food-beverages": Soup,
    "home-appliances": CookingPot,
    "home-lifestyle": UtensilsCrossed,
    "beauty-wellness": Leaf,
    "thai-crafts-goods": Wheat,
    "mom-baby": Milk,
    pets: Bone,
    electronics: Microwave,
    "sports-equipment": Apple,
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
  [["electronic", "gadget", "phone", "mobile", "computer", "-it-", "\u0e44\u0e2d\u0e17\u0e35", "\u0e2d\u0e34\u0e40\u0e25\u0e47\u0e01\u0e17\u0e23\u0e2d\u0e19\u0e34\u0e01\u0e2a\u0e4c", "\u0e21\u0e37\u0e2d\u0e16\u0e37\u0e2d", "\u0e04\u0e2d\u0e21"], Laptop],
  [["beauty", "cosmetic", "skin", "wellness", "\u0e04\u0e27\u0e32\u0e21\u0e07\u0e32\u0e21", "\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e2a\u0e33\u0e2d\u0e32\u0e07", "\u0e2a\u0e01\u0e34\u0e19"], Sparkles],
  [["furniture", "\u0e40\u0e1f\u0e2d\u0e23\u0e4c\u0e19\u0e34\u0e40\u0e08\u0e2d\u0e23\u0e4c"], Sofa],
  [["home", "living", "decor", "\u0e1a\u0e49\u0e32\u0e19"], Home],
  [["sport", "fitness", "gym", "\u0e01\u0e35\u0e2c\u0e32", "\u0e2d\u0e2d\u0e01\u0e01\u0e33\u0e25\u0e31\u0e07"], Dumbbell],
  [["kid", "baby", "child", "toy", "mom", "\u0e40\u0e14\u0e47\u0e01", "\u0e41\u0e21\u0e48", "\u0e02\u0e2d\u0e07\u0e40\u0e25\u0e48\u0e19"], Baby],
  [["-pet", "dog", "-cat-", "cats", "\u0e2a\u0e31\u0e15\u0e27\u0e4c", "\u0e2b\u0e21\u0e32", "\u0e41\u0e21\u0e27"], PawPrint],
  [["book", "stationer", "\u0e2b\u0e19\u0e31\u0e07\u0e2a\u0e37\u0e2d", "\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e40\u0e02\u0e35\u0e22\u0e19"], BookOpen],
  [["-car-", "-cars-", "auto", "motor", "\u0e23\u0e16", "\u0e22\u0e32\u0e19\u0e22\u0e19\u0e15\u0e4c"], Wrench],
  [["otop", "\u0e42\u0e2d\u0e17\u0e2d\u0e1b"], Award],
  [["craft", "-art-", "arts", "handmade", "\u0e2b\u0e31\u0e15\u0e16\u0e01\u0e23\u0e23\u0e21", "\u0e28\u0e34\u0e25\u0e1b"], Palette],
  [["flower", "plant", "garden", "\u0e14\u0e2d\u0e01\u0e44\u0e21\u0e49", "\u0e15\u0e49\u0e19\u0e44\u0e21\u0e49"], Flower2],
];

/** FASHION-only keyword fallback for shop-defined categories (not seeder slugs). */
const FASHION_KEYWORD_ICONS: [string[], LucideIcon][] = [
  [["bag", "handbag", "purse", "wallet", "\u0e01\u0e23\u0e30\u0e40\u0e1b\u0e4b\u0e32", "\u0e40\u0e2a\u0e37\u0e49\u0e2d\u0e22\u0e48\u0e2d"], ShoppingBag],
  [["shoe", "sneaker", "boot", "sandal", "\u0e23\u0e2d\u0e07\u0e40\u0e17\u0e49\u0e32", "\u0e40\u0e2a\u0e37\u0e49\u0e2d\u0e23\u0e2d\u0e07"], Footprints],
];

/** FOOD_BEVERAGE-only keyword fallback for shop-defined categories (not seeder slugs). */
const FOOD_KEYWORD_ICONS: [string[], LucideIcon][] = [
  [["coffee", "\u0e01\u0e32\u0e41\u0e1f"], Coffee],
  [["tea", "\u0e0a\u0e32"], CupSoda],
  [["dessert", "bakery", "cake", "pastry", "\u0e02\u0e19\u0e21", "\u0e40\u0e1a\u0e40\u0e01\u0e2d\u0e23\u0e35\u0e48"], Cake],
  [["fruit", "\u0e1c\u0e25\u0e44\u0e21\u0e49"], Apple],
  [["drink", "beverage", "\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e14\u0e37\u0e48\u0e21"], GlassWater],
];

function matchKeywordIcon(
  row: { slug?: string | null; nameEn?: string | null; nameTh?: string | null },
  rules: [string[], LucideIcon][],
): LucideIcon | null {
  const haystack = `-${categorySlugKey(row)}- ${(row.nameEn || "").toLowerCase()} ${row.nameTh || ""}`;
  for (const [keywords, icon] of rules) {
    if (keywords.some((keyword) => haystack.includes(keyword))) return icon;
  }
  return null;
}

export function categoryKeywordIcon(row: { slug?: string | null; nameEn?: string | null; nameTh?: string | null }): LucideIcon | null {
  return matchKeywordIcon(row, CATEGORY_KEYWORD_ICONS);
}

/**
 * Resolve order: theme override → seeder slug → theme keyword (custom only) → general keyword → Tag.
 */
export function categoryIcon(
  row: { slug?: string | null; nameEn?: string | null; nameTh?: string | null },
  template?: ShopThemeTemplateKey,
): LucideIcon {
  const key = categorySlugKey(row);
  const byTheme = template ? CATEGORY_ICON_TEMPLATE_OVERRIDES[template]?.[key] : undefined;
  if (byTheme) return byTheme;
  const bySlug = CATEGORY_ICON_BY_SLUG[key];
  if (bySlug) return bySlug;
  // Shop-defined categories only (slug not in seeder map).
  if (template === "FOOD_BEVERAGE") {
    const food = matchKeywordIcon(row, FOOD_KEYWORD_ICONS);
    if (food) return food;
  }
  if (template === "FASHION") {
    const fashion = matchKeywordIcon(row, FASHION_KEYWORD_ICONS);
    if (fashion) return fashion;
  }
  return categoryKeywordIcon(row) ?? Tag;
}

/** Marketplace home grid — colorful emoji (same friendly look as the original design). */
export const CATEGORY_EMOJI_BY_SLUG: Record<string, string> = {
  "fashion-apparel": "👗",
  "food-beverages": "🍜",
  "beauty-wellness": "💄",
  "home-lifestyle": "🏠",
  "thai-crafts-goods": "🧵",
  electronics: "💻",
  "sports-equipment": "⚽",
  "mom-baby": "👶",
  "automotive-parts": "🔧",
  pets: "🐶",
  "home-appliances": "🔌",
  "books-stationery": "📚",
  "jewelry-watches": "💍",
  "otop-products": "🏮",
};

const CATEGORY_KEYWORD_EMOJIS: [string[], string][] = [
  [["jewel", "watch", "เครื่องประดับ", "นาฬิกา"], "💍"],
  [["fashion", "apparel", "cloth", "shirt", "dress", "เสื้อ", "แฟชั่น", "กางเกง"], "👗"],
  [["food", "beverage", "drink", "snack", "coffee", "อาหาร", "เครื่องดื่ม", "ขนม", "กาแฟ"], "🍜"],
  [["appliance", "เครื่องใช้ไฟฟ้า"], "🔌"],
  [["electronic", "gadget", "phone", "mobile", "computer", "-it-", "ไอที", "อิเล็กทรอนิกส์", "มือถือ", "คอม"], "💻"],
  [["beauty", "cosmetic", "skin", "wellness", "ความงาม", "เครื่องสำอาง", "สกิน"], "💄"],
  [["furniture", "เฟอร์นิเจอร์"], "🪑"],
  [["home", "living", "decor", "บ้าน", "ไลฟ์สไตล์"], "🏠"],
  [["sport", "fitness", "gym", "กีฬา", "ออกกำลัง"], "⚽"],
  [["kid", "baby", "child", "toy", "mom", "เด็ก", "แม่", "ของเล่น"], "👶"],
  [["-pet", "dog", "-cat-", "cats", "สัตว์", "หมา", "แมว"], "🐶"],
  [["book", "stationer", "หนังสือ", "เครื่องเขียน"], "📚"],
  [["-car-", "-cars-", "auto", "motor", "อะไหล่", "รถ", "ยานยนต์"], "🔧"],
  [["otop"], "🏮"],
  [["craft", "-art-", "arts", "handmade", "หัตถกรรม", "งานฝีมือ", "ศิลปะ"], "🧵"],
  [["eco", "environment", "green", "สิ่งแวดล้อม", "รักษ์โลก"], "♻️"],
  [["flower", "plant", "garden", "ดอกไม้", "ต้นไม้"], "🪴"],
  [["gift", "ของขวัญ"], "🎁"],
];

function matchKeywordEmoji(row: { slug?: string | null; nameEn?: string | null; nameTh?: string | null }): string | null {
  const haystack = `-${categorySlugKey(row)}- ${(row.nameEn || "").toLowerCase()} ${row.nameTh || ""}`;
  for (const [keywords, emoji] of CATEGORY_KEYWORD_EMOJIS) {
    if (keywords.some((keyword) => haystack.includes(keyword))) return emoji;
  }
  return null;
}

/** Colorful emoji for marketplace category tiles; falls back to 🛍️. */
export function categoryEmoji(row: { slug?: string | null; nameEn?: string | null; nameTh?: string | null }): string {
  const key = categorySlugKey(row);
  return CATEGORY_EMOJI_BY_SLUG[key] ?? matchKeywordEmoji(row) ?? "🛍️";
}
