import { useEffect, useRef, useState } from "react";
import { Link, Outlet, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../contexts/AuthContext";
import { hasMerchantAccess } from "../services/permissions";
import { useActiveShop } from "../contexts/ActiveShopContext";
import MerchantPendingStatus from "./MerchantPendingStatus";
import MerchantSuspendedStatus from "./MerchantSuspendedStatus";
import { merchantApi, type StoreAccess } from "../services/api";
import "../styles/merchant-signup-gate.css";

const APPEAL_PATH = "/merchant/reinstatement";

export default function MerchantStoreGuard() {
  const { user, loading, reload } = useAuth();
  const { shops, shopId, ready } = useActiveShop();
  const location = useLocation();
  const { t } = useTranslation("merchant");
  const appealRoute = location.pathname === APPEAL_PATH;
  const [access, setAccess] = useState<StoreAccess | null>(null);
  const [gate, setGate] = useState<"checking" | "none" | "no-shop" | "locked">("checking");
  // re-read the session at most once (e.g. right after approval) so a stale account can never loop
  const reloaded = useRef(false);

  useEffect(() => {
    if (loading || !user || !ready) return;
    if (shops.length === 0 || shopId == null) {
      setGate("no-shop");
      return;
    }
    let active = true;
    setGate("checking");
    merchantApi
      .storeAccess(shopId)
      .then(async (result) => {
        if (!active) return;
        setAccess(result);
        if (!result.hasShop) {
          setGate("no-shop");
          return;
        }
        // Suspended shops may only open the reinstatement form — no other merchant menus.
        const suspended = result.status === "SUSPENDED";
        const canAppeal = appealRoute && suspended;
        // Pending / rejected: block every merchant page (no settings/store exception).
        if (!result.approved && !canAppeal) {
          setGate("locked");
          return;
        }
        if (result.approved && !hasMerchantAccess(user) && !reloaded.current) {
          reloaded.current = true;
          await reload();
        }
        if (active) setGate("none");
      })
      .catch(() => {
        if (active) setGate("no-shop");
      });
    return () => {
      active = false;
    };
  }, [loading, user, reload, appealRoute, ready, shopId, shops.length]);

  if (loading || !ready || (user && gate === "checking")) {
    return (
      <main className="merchant-signup-gate merchant-signup-gate--checking">
        {t("pendingGate.checking")}
      </main>
    );
  }
  if (!user) {
    window.location.replace("/login");
    return null;
  }
  if (gate === "no-shop") {
    return (
      <main className="merchant-signup-gate">
        <div className="merchant-signup-gate__glow" aria-hidden="true" />
        <div className="merchant-signup-gate__layout">
          <div className="merchant-signup-gate__copy">
            <div role="alert">
              <h1 className="merchant-signup-gate__title">{t("pendingGate.noShopTitle")}</h1>
              <p className="merchant-signup-gate__lead">{t("pendingGate.noShopDescription")}</p>
            </div>
            <div className="merchant-signup-gate__actions">
              <Link className="merchant-signup-gate__btn merchant-signup-gate__btn--primary" to="/merchant/register">
                {t("pendingGate.register")}
              </Link>
              <Link className="merchant-signup-gate__btn merchant-signup-gate__btn--secondary" to="/">
                {t("pendingGate.home")}
              </Link>
            </div>
          </div>
        </div>
      </main>
    );
  }
  if (gate === "locked") {
    if (access?.status === "SUSPENDED") {
      return <MerchantSuspendedStatus shopName={access?.shopName} />;
    }
    return (
      <MerchantPendingStatus shopName={access?.shopName} rejected={access?.status === "REJECTED"} />
    );
  }
  return <Outlet context={access} />;
}
