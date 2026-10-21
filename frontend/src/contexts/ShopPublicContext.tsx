import { createContext, useContext, useEffect, type ReactNode } from "react";
import type { PublicShop } from "../services/api";
import { resolveStorefrontTheme } from "../components/shop/ShopThemeShell";

export type ShopPublicContextValue = {
  slug: string;
  shop: PublicShop;
  theme: ReturnType<typeof resolveStorefrontTheme>;
  shopRefetching: boolean;
  reloadShop: () => void;
  notify: (message: string) => void;
  setCrumb: (label: string | null) => void;
};

const ShopPublicContext = createContext<ShopPublicContextValue | null>(null);

export function ShopPublicProvider({
  value,
  children,
}: {
  value: ShopPublicContextValue;
  children: ReactNode;
}) {
  return <ShopPublicContext.Provider value={value}>{children}</ShopPublicContext.Provider>;
}

export function useShopPublic(): ShopPublicContextValue {
  const value = useContext(ShopPublicContext);
  if (!value) throw new Error("useShopPublic must be used inside ShopLayout");
  return value;
}

/** Sets the PDP crumb on mount/change and clears it on unmount. */
export function useShopCrumb(label: string | null): void {
  const { setCrumb } = useShopPublic();
  useEffect(() => {
    setCrumb(label);
    return () => setCrumb(null);
  }, [label, setCrumb]);
}
