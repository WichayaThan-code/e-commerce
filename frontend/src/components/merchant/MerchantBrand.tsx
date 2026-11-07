import BrandLogo from "../BrandLogo";

/** Merchant topbar brand: logo-with-name PNG only; symbol-only when the bar collapses. */
export default function MerchantBrand() {
  return (
    <a className="merchant-brand" href="/" aria-label="ThaiShopFun">
      <BrandLogo variant="withName" className="merchant-brand-logo" alt="" />
      <BrandLogo variant="symbol" className="merchant-brand-mark" alt="" />
    </a>
  );
}
