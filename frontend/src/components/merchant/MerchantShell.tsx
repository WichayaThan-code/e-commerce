import type { ReactNode } from "react";
import MerchantSidebar, { type MerchantMenuKey } from "./MerchantSidebar";
import MerchantTopbarSearch from "./MerchantTopbarSearch";
import MerchantTopbarActions from "./MerchantTopbarActions";
import MerchantBrand from "./MerchantBrand";
import "../../styles/merchant-dashboard.css";
import "../../styles/product-management.css";

export default function MerchantShell({ children, notice, tone = "ok", active = "products" }: { children: ReactNode; active?: MerchantMenuKey; notice?: string; tone?: "ok" | "fail" }) {
  return (
    <main className="merchant-shell pm-shell catalog-shell">
      <header className="merchant-topbar">
        <MerchantBrand />
        <MerchantTopbarSearch />
        <MerchantTopbarActions />
      </header>
      <MerchantSidebar active={active} />
      <section className="pm-content">{children}</section>
      {notice ? <div className={tone === "fail" ? "catalog-toast fail" : "catalog-toast"} role="status">{notice}</div> : null}
    </main>
  );
}
