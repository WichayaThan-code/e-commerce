import { useCallback, useEffect, useRef, useState } from "react";
import { shopApi, type PublicProduct } from "../services/api";

const PAGE = 100; // backend MAX_PAGE_SIZE

/** Loads ALL public products of a shop, 100 at a time; the next page is fetched when the sentinel scrolls into view. */
export function useShopAllProducts(slug: string, enabled = true) {
  const [items, setItems] = useState<PublicProduct[]>([]);
  const [total, setTotal] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const nextPage = useRef(0);
  const busy = useRef(false);

  const loadMore = useCallback(() => {
    if (!enabled || busy.current) return;
    if (total != null && nextPage.current * PAGE >= total) return;
    busy.current = true;
    setLoading(true);
    const page = nextPage.current;
    shopApi.products(slug, { size: PAGE, page })
      .then((result) => {
        nextPage.current = page + 1;
        setItems((current) => {
          const seen = new Set(current.map((item) => item.id));
          return [...current, ...result.items.filter((item) => !seen.has(item.id))];
        });
        setTotal(result.items.length < PAGE ? (page * PAGE + result.items.length) : result.total);
      })
      .catch(() => setTotal((current) => current ?? 0))
      .finally(() => { busy.current = false; setLoading(false); });
  }, [slug, enabled, total]);

  useEffect(() => {
    setItems([]); setTotal(null); nextPage.current = 0; busy.current = false;
  }, [slug]);

  useEffect(() => {
    if (enabled && total == null && !busy.current) loadMore();
  }, [enabled, total, loadMore]);

  const hasMore = total == null || items.length < total;
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    const node = sentinelRef.current;
    if (!node || !hasMore || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) loadMore();
    }, { rootMargin: "600px 0px" });
    observer.observe(node);
    return () => observer.disconnect();
  }, [hasMore, loadMore, items.length]);

  return { items, loading, hasMore, sentinelRef };
}
