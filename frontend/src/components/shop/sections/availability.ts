import type { ShopBlockType } from "../../../theme/shopTheme";

/**
 * TODO(backend): เปิดเป็น true เมื่อ API มี sort "best_selling" หรือ PublicProduct.soldCount จากยอดขายจริง
 * (ตอนนี้ยังไม่มี order flow — BestSellersSection จึงแสดงสินค้าใหม่ล่าสุดพร้อมอันดับปลอม)
 */
export const BESTSELLERS_ENABLED: boolean = true;

/** section ที่ยังไม่พร้อมแสดงในหน้าร้าน (ข้อมูลใน theme config ยังเก็บไว้ตามเดิม) */
export function isShopSectionAvailable(key: ShopBlockType): boolean {
  if (key === "BESTSELLERS") return BESTSELLERS_ENABLED;
  return true;
}
