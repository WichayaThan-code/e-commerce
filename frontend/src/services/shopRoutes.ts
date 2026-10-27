export type CategoryRef = { id: number; slug?: string | null };

export const shopPath = (slug: string) => `/shop/${slug}`;
export const shopProductsPath = (slug: string) => `/shop/${slug}/products`;
export const shopProductPath = (slug: string, productSlug: string) => `/shop/${slug}/product/${productSlug}`;
export const shopCategoriesPath = (slug: string) => `/shop/${slug}/category`;
export const shopCollectionPath = (slug: string, collectionSlug: string) => `/shop/${slug}/collections/${encodeURIComponent(collectionSlug)}`;

/**
 * backend ยังไม่มี slug ใน category DTO → URL ใช้ id ไปก่อน;
 * เมื่อ API ส่ง `slug` มา URL จะเปลี่ยนเองโดยไม่ต้องแก้หน้าอื่น
 * ใช้ category.slug ถ้ามี ไม่งั้นใช้ id
 */
export const shopCategoryPath = (slug: string, category: CategoryRef) =>
  `/shop/${slug}/category/${encodeURIComponent(category.slug || String(category.id))}`;

export const shopSearchPath = (slug: string, q: string) =>
  `/shop/${slug}/search?q=${encodeURIComponent(q.trim())}`;

/** Marketplace-wide search page (Header search outside a shop). */
export const marketplaceSearchPath = (q: string, brand?: string | null) => {
  const params = new URLSearchParams();
  params.set("q", q.trim());
  if (brand) params.set("brand", brand);
  return `/search?${params.toString()}`;
};

export const shopContactPath = (slug: string) => `/shop/${slug}/contact`;
export const shopPoliciesPath = (slug: string) => `/shop/${slug}/policies`;

/** แปลง :categoryKey → หา category ใน list: ตัวเลขล้วน = id, อื่นๆ = slug (รองรับเมื่อ backend เพิ่ม slug) */
export function findCategory<T extends { id: number; slug?: string | null }>(rows: T[], key: string): T | undefined {
  if (/^\d+$/.test(key)) {
    const id = Number(key);
    return rows.find((row) => row.id === id);
  }
  return rows.find((row) => row.slug === key);
}
