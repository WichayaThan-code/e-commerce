import { useEffect, useRef, useState } from "react";
import { ChevronDown, LogOut, Plus, Store } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useAuth } from "../contexts/AuthContext";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { ApiError, mediaUrl, merchantApi, type StoreAccess } from "../services/api";
import { writeMerchantDraft } from "../services/merchantDraft";
import { useMerchantSignOut } from "./merchant/MerchantLogoutButton";
import "../styles/product-management.css";

const STATUS_LABEL: Record<NonNullable<StoreAccess["status"]>, string> = {
  APPROVED: "ใช้งาน",
  PENDING_REVIEW: "รออนุมัติ",
  REJECTED: "ปฏิเสธ",
  SUSPENDED: "ถูกระงับ",
};

const STATUS_TONE: Record<NonNullable<StoreAccess["status"]>, string> = {
  APPROVED: "is-active",
  PENDING_REVIEW: "is-pending",
  REJECTED: "is-bad",
  SUSPENDED: "is-bad",
};

export default function MerchantShopMenu() {
  const { t } = useTranslation("merchant");
  const { user } = useAuth();
  const { shops, shopId, setShopId, ready } = useActiveShop();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [failedLogos, setFailedLogos] = useState<Record<string, true>>({});
  const root = useRef<HTMLDivElement>(null);
  const current = shops.find((shop) => shop.shopId === shopId);

  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [open]);

  const addShop = async () => {
    const ownerId = current?.ownerId;
    if (!ownerId || busy) return;
    setBusy(true);
    try {
      await merchantApi.openAnotherShop(ownerId);
      // New draft linked to this owner; the owner step prefills from it and no step is skipped.
      writeMerchantDraft(user?.id, { ownerId });
      window.location.assign("/merchant/register");
    } catch (error) {
      setBusy(false);
      setNotice(error instanceof ApiError ? error.message : t("shopMenu.openFailed"));
      window.setTimeout(() => setNotice(""), 2200);
    }
  };

  const renderShopAvatar = (shop?: { shopId: number; logoUrl?: string | null }) => {
    const src = shop?.logoUrl ? mediaUrl(shop.logoUrl) : "";
    const key = shop ? `${shop.shopId}:${src}` : "";
    if (src && !failedLogos[key]) {
      return (
        <img
          className="shop-switch-logo"
          src={src}
          alt=""
          onError={() => setFailedLogos((prev) => ({ ...prev, [key]: true }))}
        />
      );
    }
    return <Store size={16} strokeWidth={2.25} />;
  };

  const { signingOut, signOut } = useMerchantSignOut();

  return (
    <>
    <div className="merchant-shop-menu" ref={root}>
      <button className="shop-switch" type="button" aria-haspopup="listbox" aria-expanded={open} onClick={() => setOpen((value) => !value)}>
        <span className="shop-switch-icon" aria-hidden="true">{renderShopAvatar(current)}</span>
        <span className="shop-switch-copy">
          <strong>{ready ? current?.shopName || "ร้านค้า" : "..."}</strong>
          <em className={current ? STATUS_TONE[current.status] : undefined}>
            {current ? STATUS_LABEL[current.status] : "เจ้าของร้าน"}
          </em>
        </span>
        <ChevronDown className="shop-switch-caret" size={16} aria-hidden="true" />
      </button>
      {open && (
        <div className="merchant-shop-menu-panel" role="listbox" aria-label="เลือกร้านค้า">
          {shops.map((shop) => (
            <button
              type="button"
              role="option"
              aria-selected={shop.shopId === shopId}
              className={shop.shopId === shopId ? "active" : ""}
              key={shop.shopId}
              onClick={() => {
                setShopId(shop.shopId);
                setOpen(false);
              }}
            >
              <span className="shop-menu-option-main">
                <span className="shop-switch-icon" aria-hidden="true">{renderShopAvatar(shop)}</span>
                <b>{shop.shopName}</b>
              </span>
              <small>{STATUS_LABEL[shop.status]}</small>
            </button>
          ))}
          <button type="button" className="add" disabled={busy || !current?.ownerId} onClick={() => void addShop()}>
            <Plus />
            {busy ? "กำลังเตรียมร้านใหม่..." : "เพิ่มร้านค้า"}
          </button>
          <button type="button" className="logout" disabled={signingOut} aria-busy={signingOut} onClick={() => void signOut()}>
            <LogOut aria-hidden="true" />
            {signingOut ? t("shopMenu.loggingOut") : t("shopMenu.logout")}
          </button>
        </div>
      )}
    </div>
    {notice ? <div className="catalog-toast fail" role="status">{notice}</div> : null}
    </>
  );
}
