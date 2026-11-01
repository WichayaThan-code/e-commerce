import type { ReactNode } from "react";
import { ShoppingBag } from "lucide-react";
import MerchantSidebar, { type MerchantMenuKey } from "./MerchantSidebar";
import MerchantTopbarSearch from "./MerchantTopbarSearch";
import MerchantTopbarActions from "./MerchantTopbarActions";
import "../../styles/merchant-dashboard.css";
import "../../styles/product-management.css";

export default function MerchantShell({ children, notice, tone = "ok", active = "products" }: { children: ReactNode; active?: MerchantMenuKey; notice?: string; tone?: "ok" | "fail" }) {
  return (
    <main className="merchant-shell pm-shell catalog-shell">
      <header className="merchant-topbar">
        <a className="merchant-brand" href="/">
          <span><ShoppingBag /><i>✓</i></span>
          <div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div>
        </a>
        <MerchantTopbarSearch />
        <MerchantTopbarActions />
      </header>
      <MerchantSidebar active={active} />
      <section className="pm-content">{children}</section>
      {notice ? <div className={tone === "fail" ? "catalog-toast fail" : "catalog-toast"} role="status">{notice}</div> : null}
    </main>
  );
}
