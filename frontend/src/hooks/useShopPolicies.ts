import { useCallback, useEffect, useState } from "react";
import { ApiError, shopApi, type ShopPolicyDto, type ShopPolicyType } from "../services/api";

export type { ShopPolicyType, ShopPolicyDto };
export type ShopPolicy = { type: ShopPolicyType; title: string; body: string; updatedAt?: string | null };

export const SHOP_POLICY_TYPES: ShopPolicyType[] = ["shipping", "returns", "privacy", "terms"];

function mapPolicy(row: ShopPolicyDto): ShopPolicy {
  return {
    type: row.type,
    title: row.title?.trim() || "",
    body: row.body,
    updatedAt: row.updatedAt ?? null,
  };
}

export function useShopPolicies(slug: string): {
  state: "loading" | "ok" | "error";
  policies: ShopPolicy[];
  reload: () => void;
} {
  const [state, setState] = useState<"loading" | "ok" | "error">("loading");
  const [policies, setPolicies] = useState<ShopPolicy[]>([]);
  const [reloadKey, setReloadKey] = useState(0);

  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  useEffect(() => {
    if (!slug) {
      setState("ok");
      setPolicies([]);
      return;
    }
    let active = true;
    setState("loading");
    shopApi
      .policies(slug)
      .then((rows) => {
        if (!active) return;
        setPolicies(rows.map(mapPolicy));
        setState("ok");
      })
      .catch((error) => {
        if (!active) return;
        if (error instanceof ApiError && error.status === 404) {
          setPolicies([]);
          setState("ok");
          return;
        }
        setPolicies([]);
        setState("error");
      });
    return () => {
      active = false;
    };
  }, [slug, reloadKey]);

  return { state, policies, reload };
}
