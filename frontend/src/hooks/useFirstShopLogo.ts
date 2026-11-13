import { useEffect, useState } from "react";
import { mediaUrl, merchantApi } from "../services/api";

/** Loads the owner's first shop logo (smallest shopId) for merchant account menus. */
export function useFirstShopLogo(enabled: boolean, userId?: string | null) {
  const [logoUrl, setLogoUrl] = useState("");
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
    if (!enabled || !userId) {
      setLogoUrl("");
      return;
    }
    let cancelled = false;
    merchantApi
      .shops()
      .then((rows) => {
        if (cancelled) return;
        const firstShop = [...rows].sort((a, b) => a.shopId - b.shopId)[0];
        setLogoUrl(mediaUrl(firstShop?.logoUrl));
      })
      .catch(() => {
        if (!cancelled) setLogoUrl("");
      });
    return () => {
      cancelled = true;
    };
  }, [enabled, userId]);

  return {
    logoUrl: failed ? "" : logoUrl,
    onLogoError: () => setFailed(true),
  };
}
