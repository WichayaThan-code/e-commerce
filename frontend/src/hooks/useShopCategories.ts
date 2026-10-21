import { useCallback, useEffect, useState } from "react";
import { shopApi, type PublicShopCategory } from "../services/api";

const inflight = new Map<string, Promise<PublicShopCategory[]>>();

function loadCategories(slug: string) {
  const existing = inflight.get(slug);
  if (existing) return existing;
  const promise = shopApi.categories(slug).finally(() => {
    inflight.delete(slug);
  });
  inflight.set(slug, promise);
  return promise;
}

export function useShopCategories(
  slug: string | null | undefined,
  options?: { enabled?: boolean },
): { state: "loading" | "ok" | "error"; rows: PublicShopCategory[]; reload: () => void } {
  const enabled = options?.enabled !== false && Boolean(slug);
  const [state, setState] = useState<"loading" | "ok" | "error">(enabled ? "loading" : "ok");
  const [rows, setRows] = useState<PublicShopCategory[]>([]);
  const [reloadKey, setReloadKey] = useState(0);

  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  useEffect(() => {
    if (!enabled || !slug) {
      setState("ok");
      setRows([]);
      return;
    }
    let active = true;
    setState("loading");
    loadCategories(slug)
      .then((data) => {
        if (!active) return;
        setRows(data);
        setState("ok");
      })
      .catch(() => {
        if (!active) return;
        setRows([]);
        setState("error");
      });
    return () => {
      active = false;
    };
  }, [slug, enabled, reloadKey]);

  return { state, rows, reload };
}
