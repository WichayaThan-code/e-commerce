import { ShoppingBag } from "lucide-react";

/** Shared merchant topbar brand (same markup as OrderManagementPage). */
export default function MerchantBrand() {
  return (
    <a className="merchant-brand" href="/">
      <span>
        <ShoppingBag />
        <i>✓</i>
      </span>
      <div>
        <b>
          ThaiShop<span>Fun</span>
        </b>
        <small>Shop Local · Support Thai · Smile Together</small>
      </div>
    </a>
  );
}
