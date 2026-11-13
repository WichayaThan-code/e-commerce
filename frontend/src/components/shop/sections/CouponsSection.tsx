import ShopCouponStrip, { ShopCouponStripPreview } from "../ShopCouponStrip";
import type { ShopSectionProps } from "./types";

/** Shop discount coupons strip — enabled/ordered via theme `sections` like banners. */
export default function CouponsSection({ shop, catalog, preview = false }: ShopSectionProps) {
  return (
    <div className="tsf-wrap shop-coupons-section">
      {preview
        ? <ShopCouponStripPreview />
        : <ShopCouponStrip slug={shop.slug} shopId={shop.shopId} onNotify={catalog.onNotify} />}
    </div>
  );
}
