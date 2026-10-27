import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import {
  BarChart3, Box, CircleHelp, CircleUserRound, Crown, Headphones, Home,
  Settings, ShoppingBag, Store, TicketPercent, Truck, WalletCards,
} from "lucide-react";
import MerchantLogoutButton from "./MerchantLogoutButton";
import { useTranslation } from "react-i18next";
import { usePermissions } from "../../hooks/usePermissions";
import LanguageSwitcher from "../LanguageSwitcher";
import MerchantBell from "./MerchantBell";
import MerchantTopbarSearch from "./MerchantTopbarSearch";
import MerchantShopMenu from "../MerchantShopMenu";
import "../../styles/merchant-dashboard.css";
import "../../styles/product-management.css";

const links = [
  [Home, "dashboard", "/merchant/dashboard"],
  [Box, "products", "/merchant/products"],
  [ShoppingBag, "orders", "/merchant/orders"],
  [CircleUserRound, "customers", "/merchant/customers"],
  [TicketPercent, "promotions", "/merchant/promotions"],
  [BarChart3, "reports", "/merchant/reports"],
  [WalletCards, "finance", "/merchant/finance"],
  [Truck, "shipping", "/merchant/shipping"],
  [Store, "store", "/merchant/store"],
  [Settings, "settings", "/merchant/settings"],
] as const;

export default function MerchantShell({ children, notice, tone = "ok" }: { children: ReactNode; notice?: string; tone?: "ok" | "fail" }) {
  const { t } = useTranslation("merchant");
  const { canSeeMenuItem } = usePermissions();
  return (
    <main className="merchant-shell pm-shell catalog-shell">
      <header className="merchant-topbar">
        <a className="merchant-brand" href="/">
          <span><ShoppingBag /><i>✓</i></span>
          <div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div>
        </a>
        <MerchantTopbarSearch />
        <LanguageSwitcher />
        <MerchantBell />
        <MerchantShopMenu />
      </header>
      <aside className="merchant-sidebar pm-sidebar">
        <nav>
          {links.filter(canSeeMenuItem).map(([Icon, key, href]) => (
            <NavLink key={key} to={href} className={({ isActive }) => (isActive ? "active" : undefined)} end={key !== "products"}>
              <Icon />
              {t(`catalog.menu.${key}`)}
            </NavLink>
          ))}
        </nav>
        <section className="upgrade-card">
          <Crown />
          <h3>{t("catalog.menu.upgradeTitle")}</h3>
          <p>{t("catalog.menu.upgradeBody")}</p>
          <a href="/help">{t("catalog.menu.upgradeAction")}</a>
        </section>
        <div className="merchant-support">
          <a href="/help"><CircleHelp />{t("catalog.menu.help")}</a>
          <a href="/help"><Headphones />{t("catalog.menu.contact")}</a>
          <MerchantLogoutButton />
        </div>
      </aside>
      <section className="pm-content">{children}</section>
      {notice ? <div className={tone === "fail" ? "catalog-toast fail" : "catalog-toast"} role="status">{notice}</div> : null}
    </main>
  );
}
