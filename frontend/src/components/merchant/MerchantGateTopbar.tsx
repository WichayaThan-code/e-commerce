import MerchantBrand from "./MerchantBrand";
import MerchantTopbarSearch from "./MerchantTopbarSearch";
import MerchantTopbarActions from "./MerchantTopbarActions";
import "../../styles/merchant-dashboard.css";
import "../../styles/product-management.css";

/** Full merchant topbar for locked/status gates (brand, search, language, bell, shop switch). */
export default function MerchantGateTopbar() {
  return (
    <header className="merchant-topbar">
      <MerchantBrand />
      <MerchantTopbarSearch />
      <MerchantTopbarActions />
    </header>
  );
}
