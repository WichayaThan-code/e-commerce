import { useEffect, useState, type MouseEvent } from "react";
import { Link, useLocation } from "react-router-dom";
import {
  BarChart3, Box, CircleHelp, CircleUserRound, Crown, Headphones, Home,
  Menu, Palette, Settings, ShoppingBag, Store, TicketPercent, Truck, WalletCards, X,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import MerchantLogoutButton from "./MerchantLogoutButton";
import { usePermissions } from "../../hooks/usePermissions";
import { useActiveShop } from "../../contexts/ActiveShopContext";
import { merchantOrderApi } from "../../services/api";
import "../../styles/merchant-dashboard.css";
import "../../styles/product-management.css";
import "../../styles/merchant-responsive.css";

/** Single source of truth for the /merchant sidebar menu. Edit menu items here only. */
export const MERCHANT_MENU = [
  [Home, "dashboard", "/merchant/dashboard"],
  [Box, "products", "/merchant/products"],
  [ShoppingBag, "orders", "/merchant/orders"],
  [CircleUserRound, "customers", "/merchant/customers"],
  [TicketPercent, "promotions", "/merchant/promotions"],
  [BarChart3, "reports", "/merchant/reports"],
  [WalletCards, "finance", "/merchant/finance"],
  [Truck, "shipping", "/merchant/shipping"],
  [Store, "store", "/merchant/store"],
  [Palette, "theme", "/merchant/store/theme"],
  [Settings, "settings", "/merchant/settings"],
] as const;

export type MerchantMenuKey = (typeof MERCHANT_MENU)[number][1];

/** Orders waiting for the merchant to ship (same status as the "to ship" KPI on /merchant/orders). */
const ORDERS_BADGE_STATUS = "MERCHANT_CONFIRMED";
const ORDERS_ITEM = MERCHANT_MENU[2];

type Props = {
  /** Menu key to highlight as the current page. */
  active: MerchantMenuKey;
  /** Optional guard (e.g. unsaved changes). Call event.preventDefault() to block navigation. */
  onNavigate?: (event: MouseEvent<HTMLAnchorElement>, href: string) => void;
  /** Optional badge override per menu key. The orders badge is loaded automatically. */
  badges?: Partial<Record<MerchantMenuKey, string | number>>;
};

/** Phones and small tablets: the sidebar becomes a slide-out drawer (desktop markup is unchanged). */
const DRAWER_QUERY = "(max-width: 900px)";
function useIsDrawer() {
  const [match, setMatch] = useState(() => typeof window !== "undefined" && window.matchMedia(DRAWER_QUERY).matches);
  useEffect(() => {
    const mq = window.matchMedia(DRAWER_QUERY);
    const onChange = () => setMatch(mq.matches);
    onChange();
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);
  return match;
}

/** Real count of to-ship orders for the active shop; null when 0, not permitted, or on error. */
function useOrdersBadge(enabled: boolean) {
  const { shopId } = useActiveShop();
  const [count, setCount] = useState<number | null>(null);
  useEffect(() => {
    if (!enabled || shopId == null) { setCount(null); return; }
    let alive = true;
    merchantOrderApi.list(shopId, { status: ORDERS_BADGE_STATUS, size: 1 })
      .then((page) => { if (alive) setCount(page.totalItems > 0 ? page.totalItems : null); })
      .catch(() => { if (alive) setCount(null); });
    return () => { alive = false; };
  }, [enabled, shopId]);
  return count;
}

export default function MerchantSidebar({ active, onNavigate, badges }: Props) {
  const { t } = useTranslation("merchant");
  const { canSeeMenuItem } = usePermissions();
  const canSeeOrders = canSeeMenuItem(ORDERS_ITEM);
  const ordersCount = useOrdersBadge(canSeeOrders);
  const [notice, setNotice] = useState("");
  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(""), 1800);
    return () => window.clearTimeout(timer);
  }, [notice]);
  const isDrawer = useIsDrawer();
  const [open, setOpen] = useState(false);
  const drawerOpen = isDrawer && open;
  const { pathname, search } = useLocation();
  useEffect(() => { setOpen(false); }, [pathname, search, isDrawer]);
  useEffect(() => {
    if (!drawerOpen) return;
    const onKey = (event: KeyboardEvent) => { if (event.key === "Escape") setOpen(false); };
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.removeEventListener("keydown", onKey); document.body.style.overflow = prev; };
  }, [drawerOpen]);
  const guard = (href: string) => (onNavigate || isDrawer
    ? (event: MouseEvent<HTMLAnchorElement>) => { onNavigate?.(event, href); if (!event.defaultPrevented) setOpen(false); }
    : undefined);
  const menuLabel = drawerOpen
    ? t("catalog.menu.closeMenu", { defaultValue: "ปิดเมนู" })
    : t("catalog.menu.openMenu", { defaultValue: "เปิดเมนู" });
  const badgeOf = (key: MerchantMenuKey) => {
    const override = badges?.[key];
    if (override != null && override !== "") return override;
    if (key === "orders" && ordersCount) return ordersCount > 99 ? "99+" : ordersCount;
    return null;
  };
  return (
    <>
    {isDrawer ? (
      <button type="button" className="pm-drawer-toggle" aria-label={menuLabel} title={menuLabel} aria-expanded={drawerOpen} aria-controls="merchant-drawer" onClick={() => setOpen((v) => !v)}>
        {drawerOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
      </button>
    ) : null}
    {drawerOpen ? <div className="pm-drawer-overlay" aria-hidden="true" onClick={() => setOpen(false)} /> : null}
    <aside className={drawerOpen ? "merchant-sidebar pm-sidebar is-open" : "merchant-sidebar pm-sidebar"} id={isDrawer ? "merchant-drawer" : undefined}>
      <nav>
        {MERCHANT_MENU.filter(canSeeMenuItem).map(([Icon, key, href]) => {
          const badge = badgeOf(key);
          return (
            <Link
              key={key}
              to={href}
              className={key === active ? "active" : undefined}
              aria-current={key === active ? "page" : undefined}
              onClick={guard(href)}
            >
              <Icon />
              {t(`catalog.menu.${key}`)}
              {badge != null ? <i>{badge}</i> : null}
            </Link>
          );
        })}
      </nav>
      <section className="upgrade-card">
        <Crown />
        <h3>{t("catalog.menu.upgradeTitle")}</h3>
        <p>{t("catalog.menu.upgradeBody")}</p>
        <button type="button" title={t("catalog.menu.upgradeAction")} aria-label={t("catalog.menu.upgradeAction")} onClick={() => setNotice("กำลังเปิดรายละเอียดแพ็กเกจ")}><Crown className="rail-only" aria-hidden="true" />{t("catalog.menu.upgradeAction")}</button>
      </section>
      <div className="merchant-support">
        <Link to="/help" title={t("catalog.menu.help")} aria-label={t("catalog.menu.help")} onClick={guard("/help")}><CircleHelp />{t("catalog.menu.help")}</Link>
        <button type="button" title={t("catalog.menu.contact")} aria-label={t("catalog.menu.contact")} onClick={() => setNotice("กำลังติดต่อทีมงาน")}><Headphones />{t("catalog.menu.contact")}</button>
        <MerchantLogoutButton />
      </div>
      {notice ? <div className="merchant-toast" role="status">{notice}</div> : null}
    </aside>
    </>
  );
}
