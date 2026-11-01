import { useCallback, useEffect, useSyncExternalStore } from "react";
import { loginPathForCart, useOptionalCart } from "../contexts/CartContext";
import { favoritesApi, type FavoriteItem } from "../services/api";

/** Shared per-shop favourites store: buyers sync with /api/me/favorites, guests are sent to login. */
type Entry = { items: FavoriteItem[]; loaded: boolean; loading: boolean };
const store = new Map<string, Entry>();
const listeners = new Set<() => void>();
const EMPTY: Entry = { items: [], loaded: false, loading: false };
const emit = () => listeners.forEach((listener) => listener());
const subscribe = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; };
const setEntry = (slug: string, entry: Entry) => { store.set(slug, entry); emit(); };

async function load(slug: string) {
  const current = store.get(slug);
  if (current?.loading || current?.loaded) return;
  setEntry(slug, { items: current?.items ?? [], loaded: false, loading: true });
  try {
    setEntry(slug, { items: await favoritesApi.list(slug), loaded: true, loading: false });
  } catch {
    setEntry(slug, { items: [], loaded: true, loading: false });
  }
}

export function useFavorites(slug: string, notify?: (message: string) => void, messages?: { login: string; added: string; removed: string; failed: string }) {
  const cart = useOptionalCart();
  const access = cart?.access ?? "guest";
  const entry = useSyncExternalStore(subscribe, () => store.get(slug) ?? EMPTY);
  useEffect(() => {
    if (access === "buyer") void load(slug);
    else if (access === "guest" && store.has(slug)) { store.delete(slug); emit(); }
  }, [access, slug]);
  const ids = entry.items.map((item) => item.productId);
  const toggle = useCallback(async (productId: string, productSlug?: string) => {
    if (access === "loading") return;
    if (access !== "buyer") {
      if (messages) notify?.(messages.login);
      window.location.assign(loginPathForCart());
      return;
    }
    const before = store.get(slug) ?? { ...EMPTY, loaded: true };
    const has = before.items.some((item) => item.productId === productId);
    const optimistic = has
      ? before.items.filter((item) => item.productId !== productId)
      : [{ productId, productSlug: productSlug ?? "", shopSlug: slug, createdAt: new Date().toISOString() }, ...before.items];
    setEntry(slug, { ...before, items: optimistic });
    try {
      if (has) await favoritesApi.remove(productId);
      else await favoritesApi.add(productId);
      if (messages) notify?.(has ? messages.removed : messages.added);
    } catch {
      setEntry(slug, before);
      if (messages) notify?.(messages.failed);
    }
  }, [access, slug, notify, messages]);
  return { access, ids, items: entry.items, loaded: entry.loaded || access === "guest", toggle };
}
