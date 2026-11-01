import LanguageSwitcher from "../LanguageSwitcher";
import MerchantBell from "./MerchantBell";
import MerchantShopMenu from "../MerchantShopMenu";

/** Right-side merchant topbar cluster: language, notifications, active shop. */
export default function MerchantTopbarActions() {
  return (
    <div className="merchant-topbar-actions">
      <LanguageSwitcher />
      <MerchantBell />
      <MerchantShopMenu />
    </div>
  );
}
