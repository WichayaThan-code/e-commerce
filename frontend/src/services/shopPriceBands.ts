/** Stable ids aligned with `catalog.priceRanges` order in public.json */
export const SHOP_PRICE_BAND_IDS = ["under100", "b100_300", "b301_500", "b501_1000", "over1000"] as const;
export type ShopPriceBandId = (typeof SHOP_PRICE_BAND_IDS)[number];

export function shopPriceBandsQuery(selected: readonly ShopPriceBandId[]) {
  return selected.length ? selected.join(",") : undefined;
}
