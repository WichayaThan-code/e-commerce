import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { Outlet } from "react-router-dom";
import { merchantApi, type MerchantShopOption } from "../services/api";
import { useAuth } from "./AuthContext";

const STORAGE_KEY = "tsf_active_shop";

type ActiveShopValue = {
  shops: MerchantShopOption[];
  shopId: number | null;
  setShopId: (id: number) => void;
  ready: boolean;
};

const ActiveShopContext = createContext<ActiveShopValue | null>(null);

export default function ActiveShopProvider() {
  const { user, loading } = useAuth();
  const [shops, setShops] = useState<MerchantShopOption[]>([]);
  const [shopId, setShopIdState] = useState<number | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (loading) return;
    if (!user) {
      setShops([]);
      setShopIdState(null);
      setReady(true);
      return;
    }
    let active = true;
    setReady(false);
    merchantApi.shops().then((rows) => {
      if (!active) return;
      setShops(rows);
      const saved = Number(localStorage.getItem(STORAGE_KEY));
      const match = rows.find((row) => row.shopId === saved);
      const next = match?.shopId ?? rows[0]?.shopId ?? null;
      setShopIdState(next);
      if (next != null) localStorage.setItem(STORAGE_KEY, String(next));
    }).catch(() => {
      if (!active) return;
      setShops([]);
      setShopIdState(null);
    }).finally(() => {
      if (active) setReady(true);
    });
    return () => { active = false; };
  }, [loading, user]);

  const setShopId = (id: number) => {
    setShopIdState(id);
    localStorage.setItem(STORAGE_KEY, String(id));
  };

  const value = useMemo(() => ({ shops, shopId, setShopId, ready }), [shops, shopId, ready]);
  return (
    <ActiveShopContext.Provider value={value}>
      <Outlet />
    </ActiveShopContext.Provider>
  );
}

/** Same as useActiveShop but returns null outside the merchant area (used by usePermissions). */
export function useOptionalActiveShop() {
  return useContext(ActiveShopContext);
}

export function useActiveShop() {
  const value = useContext(ActiveShopContext);
  if (!value) throw new Error("useActiveShop must be used inside ActiveShopProvider");
  return value;
}
