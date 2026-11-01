import { useState } from "react";
import { LogOut } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useAuth } from "../../contexts/AuthContext";

/** Shared merchant sign-out: same flow as AdminSidebar (logout, then hard redirect to "/" so all page state/polling resets). */
export function useMerchantSignOut() {
  const { logout } = useAuth();
  const [signingOut, setSigningOut] = useState(false);
  const signOut = async () => {
    if (signingOut) return;
    setSigningOut(true);
    try {
      await logout();
    } finally {
      window.location.replace("/");
    }
  };
  return { signingOut, signOut };
}

export default function MerchantLogoutButton({ className = "merchant-logout" }: { className?: string }) {
  const { t } = useTranslation("merchant");
  const { signingOut, signOut } = useMerchantSignOut();
  return (
    <button type="button" className={className} title={t("shopMenu.logout")} aria-label={t("shopMenu.logout")} disabled={signingOut} aria-busy={signingOut} onClick={() => void signOut()}>
      <LogOut aria-hidden="true" />
      {signingOut ? t("shopMenu.loggingOut") : t("shopMenu.logout")}
    </button>
  );
}
