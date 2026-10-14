import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useAuth } from "./AuthContext";

const CHANGE_EVENT = "thaishopfun-cart";

function storageKey(userId: string) {
  return `thaishopfun.cart.${userId}`;
}

export type CartLine = {
  id: string;
  name: string;
  shop: string;
  optionLabel: string;
  price: number;
  old: number;
  quantity: number;
  stock: number;
  imageUrl: string | null;
};

type CartValue = {
  items: CartLine[];
  count: number;
  addLine: (line: CartLine) => { added: number };
  setQuantity: (id: string, quantity: number) => void;
  remove: (ids: string[]) => void;
};

const CartContext = createContext<CartValue | null>(null);

function isLine(value: unknown): value is CartLine {
  if (!value || typeof value !== "object") return false;
  const line = value as CartLine;
  return typeof line.id === "string" && typeof line.name === "string" && typeof line.quantity === "number" && typeof line.price === "number";
}

function normalize(line: CartLine): CartLine {
  const price = Number(line.price) || 0;
  const old = Number(line.old) > price ? Number(line.old) : price;
  const quantity = Math.max(1, Math.trunc(Number(line.quantity) || 1));
  const stock = Math.max(quantity, Math.trunc(Number(line.stock) || quantity));
  return {
    id: line.id,
    name: line.name,
    shop: line.shop || "",
    optionLabel: line.optionLabel || "",
    price,
    old,
    quantity: Math.min(quantity, stock),
    stock,
    imageUrl: line.imageUrl || null,
  };
}

export function readCart(userId: string | null): CartLine[] {
  if (!userId) return [];
  try {
    const parsed = JSON.parse(localStorage.getItem(storageKey(userId)) || "[]");
    return Array.isArray(parsed) ? parsed.filter(isLine).map(normalize) : [];
  } catch {
    return [];
  }
}

function saveCart(userId: string, lines: CartLine[]) {
  localStorage.setItem(storageKey(userId), JSON.stringify(lines));
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const userId = user?.id ?? null;
  const [items, setItems] = useState<CartLine[]>([]);

  useEffect(() => {
    setItems(readCart(userId));
    const sync = (event: Event) => {
      if (event instanceof StorageEvent && (!userId || event.key !== storageKey(userId))) return;
      setItems(readCart(userId));
    };
    window.addEventListener(CHANGE_EVENT, sync);
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener(CHANGE_EVENT, sync);
      window.removeEventListener("storage", sync);
    };
  }, [userId]);

  const value = useMemo<CartValue>(() => ({
    items,
    count: items.reduce((sum, item) => sum + item.quantity, 0),
    addLine(line) {
      if (!userId) return { added: 0 };
      const current = readCart(userId);
      const found = current.find((item) => item.id === line.id);
      const room = found ? Math.max(0, line.stock - found.quantity) : line.stock;
      const added = Math.min(Math.max(0, line.quantity), room);
      if (added < 1) return { added: 0 };
      const next = found
        ? current.map((item) => item.id === line.id ? { ...line, quantity: item.quantity + added } : item)
        : [...current, { ...line, quantity: added }];
      saveCart(userId, next);
      setItems(next);
      return { added };
    },
    setQuantity(id, quantity) {
      if (!userId) return;
      const next = readCart(userId).map((item) => item.id === id ? { ...item, quantity: Math.min(item.stock, Math.max(1, Math.trunc(quantity))) } : item);
      saveCart(userId, next);
      setItems(next);
    },
    remove(ids) {
      if (!userId) return;
      const next = readCart(userId).filter((item) => !ids.includes(item.id));
      saveCart(userId, next);
      setItems(next);
    },
  }), [items, userId]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const value = useContext(CartContext);
  if (!value) throw new Error("useCart must be used inside CartProvider");
  return value;
}
