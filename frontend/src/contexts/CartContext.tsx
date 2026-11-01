import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useAuth } from "./AuthContext";
import {
  cartApi,
  type AuthUser,
  type Cart,
  type CartActionResult,
  type CartLinePatch,
  type CartMergeNote,
  type CartValidation,
} from "../services/api";
import { accountKind } from "../services/permissions";

type CartStatus = "loading" | "ready" | "error";

/**
 * Who may use the cart (R2/T2). guest = not signed in (login first, no guest cart any more);
 * blocked = admin accounts (and anyone without CART_READ); merchants may buy from other shops;
 * own-shop purchases are blocked in the UI + backend, not here.
 */
export type CartAccess = "loading" | "guest" | "blocked" | "buyer";

export function cartAccessOf(user: AuthUser | null, loading: boolean): CartAccess {
  if (loading) return "loading";
  if (!user) return "guest";
  if (accountKind(user) === "ADMIN") return "blocked";
  if (user.permissions && !user.permissions.includes("CART_READ")) return "blocked";
  return "buyer";
}

/** /login?next=<current page> so the user comes back to the same product / cart after signing in. */
export function loginPathForCart(next = `${window.location.pathname}${window.location.search}${window.location.hash}`) {
  return `/login?next=${encodeURIComponent(next)}`;
}

export type CartValue = {
  cart: Cart | null;
  status: CartStatus;
  /** Kept for CheckoutPage: "user" only for a signed-in buyer. */
  mode: "user" | "guest";
  access: CartAccess;
  /** Total item quantity for the header badge (0 for guests / blocked accounts / while loading). */
  count: number;
  /** Always empty since the guest cart (and its merge on login) was removed; kept so CartPage stays unchanged. */
  mergeNotes: CartMergeNote[];
  dismissMergeNotes: () => void;
  refresh: () => Promise<void>;
  /** Adds a SKU; returns lastAction (adjusted = capped by stock) or null. Throws ApiError on failure. Buyers only. */
  add: (skuId: string, quantity: number) => Promise<CartActionResult | null>;
  /** V43: adds a product-configurator line (server re-quotes; rejected while the backend flag is off). */
  addConfigured: (skuId: string, quantity: number, configuration: { selections: Record<string, unknown>; version?: number }) => Promise<CartActionResult | null>;
  /** AB3 Buy Now: adds the line, then leaves ONLY that line selected so /checkout buys just it. */
  buyNow: (skuId: string, quantity: number, configuration?: { selections: Record<string, unknown>; version?: number }) => Promise<CartActionResult | null>;
  update: (itemId: string, patch: CartLinePatch) => Promise<CartActionResult | null>;
  setSelected: (itemIds: string[], selected: boolean) => Promise<void>;
  remove: (itemIds: string[]) => Promise<void>;
  applyCoupon: (shopId: number, code: string) => Promise<void>;
  removeCoupon: (shopId: number) => Promise<void>;
  chooseShipping: (shopId: number, methodId: string | null) => Promise<void>;
  acceptPrices: () => Promise<void>;
  validate: () => Promise<CartValidation>;
};

const CartContext = createContext<CartValue | null>(null);
const NO_NOTES: CartMergeNote[] = [];

export function CartProvider({ children }: { children: ReactNode }) {
  const { user, loading: authLoading } = useAuth();
  const access = cartAccessOf(user, authLoading);
  const [cart, setCart] = useState<Cart | null>(null);
  const [status, setStatus] = useState<CartStatus>("loading");
  const seq = useRef(0);

  /** Only the newest request may update state (protects against out-of-order responses). */
  const apply = useCallback((ticket: number, next: Cart) => {
    if (ticket !== seq.current) return;
    setCart(next);
    setStatus("ready");
  }, []);

  const refresh = useCallback(async () => {
    const ticket = ++seq.current;
    if (access !== "buyer") {
      // guests and merchant/admin accounts never call the cart API
      setCart(null);
      setStatus(access === "loading" ? "loading" : "ready");
      return;
    }
    setStatus((current) => (current === "ready" ? current : "loading"));
    try {
      apply(ticket, await cartApi.get());
    } catch {
      if (ticket === seq.current) setStatus("error");
    }
  }, [apply, access]);

  useEffect(() => { void refresh(); }, [refresh, user?.id]);

  const mutate = useCallback(async (call: () => Promise<Cart>) => {
    const ticket = ++seq.current;
    const next = await call();
    apply(ticket, next);
    return next.lastAction ?? null;
  }, [apply]);

  const value = useMemo<CartValue>(() => ({
    cart: access === "buyer" ? cart : null,
    status,
    mode: access === "buyer" ? "user" : "guest",
    access,
    count: access === "buyer" ? cart?.counts.itemQuantity ?? 0 : 0,
    mergeNotes: NO_NOTES,
    dismissMergeNotes: () => {},
    refresh,
    add: (skuId, quantity) => mutate(() => cartApi.add(skuId, quantity)),
    addConfigured: (skuId, quantity, configuration) => mutate(() => cartApi.addConfigured(skuId, quantity, configuration)),
    buyNow: async (skuId, quantity, configuration) => {
      const ticket = ++seq.current;
      let next = configuration ? await cartApi.addConfigured(skuId, quantity, configuration) : await cartApi.add(skuId, quantity);
      const action = next.lastAction ?? null;
      const lines = (cart: Cart) => cart.groups.flatMap((group) => group.items);
      const targetId = action?.itemId ?? lines(next).find((line) => line.skuId === skuId && !line.configuration)?.id;
      if (targetId && !(action && action.appliedQuantity < 1)) {
        const others = lines(next).filter((line) => line.selected && line.id !== targetId).map((line) => line.id);
        if (others.length) next = await cartApi.select(others, false);
        if (lines(next).some((line) => line.id === targetId && !line.selected)) next = await cartApi.select([targetId], true);
      }
      apply(ticket, next);
      return action;
    },
    update: (itemId, patch) => mutate(() => cartApi.update(itemId, patch)),
    setSelected: async (itemIds, selected) => { await mutate(() => cartApi.select(itemIds, selected)); },
    remove: async (itemIds) => {
      await mutate(() => (itemIds.length === 1 ? cartApi.remove(itemIds[0]) : cartApi.removeMany(itemIds)));
    },
    applyCoupon: async (shopId, code) => { await mutate(() => cartApi.applyCoupon(shopId, code.trim().toUpperCase())); },
    removeCoupon: async (shopId) => { await mutate(() => cartApi.removeCoupon(shopId)); },
    chooseShipping: async (shopId, methodId) => { await mutate(() => cartApi.chooseShipping(shopId, methodId)); },
    acceptPrices: async () => { await mutate(() => cartApi.acceptPrices()); },
    validate: async () => {
      const result = await cartApi.validate();
      const ticket = ++seq.current;
      apply(ticket, result.cart);
      return result;
    },
  }), [cart, status, access, refresh, mutate, apply]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const value = useContext(CartContext);
  if (!value) throw new Error("useCart must be used inside CartProvider");
  return value;
}

/** Same as useCart but returns null outside CartProvider (theme-editor preview renders ProductCard without it). */
export function useOptionalCart() {
  return useContext(CartContext);
}
