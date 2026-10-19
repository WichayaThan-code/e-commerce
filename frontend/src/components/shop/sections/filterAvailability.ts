export type ShopFilterKey = "category" | "price" | "brand" | "inStock" | "onSale" | "rating";
/** on = ทำงานจริง, soon = แสดงในแผงพร้อมป้าย "เร็วๆ นี้" แต่ไม่กรอง, off = ไม่แสดง */
export type ShopFilterAvailability = "on" | "soon" | "off";

export const SHOP_FILTER_AVAILABILITY: Record<ShopFilterKey, ShopFilterAvailability> = {
  category: "on", // GET /api/shops/{slug}/categories + ?categoryId= (มีแล้ว)
  price: "on", // ?priceBands= (มีแล้ว)
  // TODO(backend): GET /api/shops/{slug}/brands (distinct p.brand ของสินค้า PUBLISHED) + publicList รับ ?brand=
  brand: "soon",
  // TODO(backend): publicList รับ ?stock=in (ProductService.stockClause มีแล้ว ฝั่ง merchant ใช้อยู่)
  inStock: "soon",
  // TODO(backend): publicList รับ ?onSale=true (compareAtPrice > basePrice) — อาจเพิ่ม index
  onSale: "soon",
  // TODO(backend/DB): rating จาก product_reviews (V1, ยังไม่มี entity/flow รีวิว) + PublicProduct.rating + ?minRating=
  rating: "soon",
};

export const isFilterOn = (key: ShopFilterKey) => SHOP_FILTER_AVAILABILITY[key] === "on";
export const isFilterShown = (key: ShopFilterKey) => SHOP_FILTER_AVAILABILITY[key] !== "off";
