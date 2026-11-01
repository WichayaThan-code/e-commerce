import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  parseShopSortKey,
  shopApiSort,
  type ShopCatalogControls,
  type ShopSortKey,
} from "../components/shop/sections/types";
import { useShopPublic } from "../contexts/ShopPublicContext";
import { useDelayedLoading } from "./useDelayedLoading";
import { useFavorites } from "./useFavorites";
import { usePublicTranslation } from "./usePublicTranslation";
import { shopApi, type PublicProduct } from "../services/api";
import { SHOP_PRICE_BAND_IDS, shopPriceBandsQuery, type ShopPriceBandId } from "../services/shopPriceBands";

const DEFAULT_PAGE_SIZE = 30;

function parsePriceBands(raw: string | null): ShopPriceBandId[] {
  if (!raw) return [];
  return raw
    .split(",")
    .filter((id): id is ShopPriceBandId => (SHOP_PRICE_BAND_IDS as readonly string[]).includes(id));
}

function parsePage(raw: string | null): number {
  const value = Number(raw);
  return Number.isFinite(value) && value >= 1 ? Math.trunc(value) : 1;
}

function parseCategoryParam(raw: string | null): number | null {
  if (!raw || !/^\d+$/.test(raw)) return null;
  return Number(raw);
}

export function useShopCatalog(options: {
  slug: string;
  categoryId?: number | null;
  categoryLocked?: boolean;
  q?: string | null;
  enabled?: boolean;
  pageSize?: number;
  onCategory?: (categoryId: number | null) => void;
}): { products: PublicProduct[]; catalog: ShopCatalogControls } {
  const {
    slug,
    categoryId: optionCategoryId,
    categoryLocked = false,
    q = null,
    enabled = true,
    pageSize = DEFAULT_PAGE_SIZE,
    onCategory: onCategoryOption,
  } = options;
  const [searchParams, setSearchParams] = useSearchParams();
  const paramsRef = useRef(searchParams);
  paramsRef.current = searchParams;
  const { notify } = useShopPublic();

  const page = parsePage(searchParams.get("page"));
  const sort = parseShopSortKey(searchParams.get("sort"));
  const priceBands = parsePriceBands(searchParams.get("price"));
  const priceKey = priceBands.join(",");
  const grid = searchParams.get("view") !== "list";
  const categoryParam = parseCategoryParam(searchParams.get("category"));
  const categoryId = optionCategoryId !== undefined ? optionCategoryId : categoryParam;

  const [products, setProducts] = useState<PublicProduct[]>([]);
  const [total, setTotal] = useState(0);
  const [productState, setProductState] = useState<"loading" | "error" | "ok">(enabled ? "loading" : "ok");
  const [brandQuery, setBrandQuery] = useState("");
  const { tp } = usePublicTranslation();
  const favoriteMessages = useMemo(() => ({
    login: tp("shop.general.favoritesLogin"), added: tp("shop.general.favoriteAdded"),
    removed: tp("shop.general.favoriteRemoved"), failed: tp("shop.general.favoriteFailed"),
  }), [tp]);
  const { ids: favorites, toggle: toggleFavorite } = useFavorites(slug, notify, favoriteMessages);
  const [reloadKey, setReloadKey] = useState(0);

  const productLoading = productState === "loading";
  const {
    showSkeleton,
    markHasData,
    isRefetching,
  } = useDelayedLoading(productLoading, 150, `${slug}|${categoryId ?? ""}|${q ?? ""}`);

  useEffect(() => {
    if (productState === "ok") markHasData();
  }, [productState, markHasData]);

  const writeParams = useCallback((mutate: (next: URLSearchParams) => void) => {
    const next = new URLSearchParams(paramsRef.current);
    mutate(next);
    paramsRef.current = next;
    setSearchParams(next, { preventScrollReset: true });
  }, [setSearchParams]);

  const onSort = useCallback((next: ShopSortKey) => {
    writeParams((params) => {
      params.delete("page");
      if (next === "featured") params.delete("sort");
      else params.set("sort", next);
    });
  }, [writeParams]);

  const onGrid = useCallback((nextGrid: boolean) => {
    writeParams((params) => {
      if (nextGrid) params.delete("view");
      else params.set("view", "list");
    });
  }, [writeParams]);

  const onPage = useCallback((nextPage: number) => {
    writeParams((params) => {
      if (nextPage <= 1) params.delete("page");
      else params.set("page", String(nextPage));
    });
  }, [writeParams]);

  const onTogglePriceBand = useCallback((band: string) => {
    writeParams((params) => {
      params.delete("page");
      const current = parsePriceBands(params.get("price"));
      const next = current.includes(band as ShopPriceBandId)
        ? current.filter((item) => item !== band)
        : [...current, band as ShopPriceBandId].filter((id) =>
          (SHOP_PRICE_BAND_IDS as readonly string[]).includes(id));
      if (next.length === 0) params.delete("price");
      else params.set("price", next.join(","));
    });
  }, [writeParams]);

  const defaultOnCategory = useCallback((id: number | null) => {
    writeParams((params) => {
      params.delete("page");
      if (id == null) params.delete("category");
      else params.set("category", String(id));
    });
  }, [writeParams]);

  const onCategory = onCategoryOption ?? defaultOnCategory;

  const loadProducts = useCallback(() => {
    setReloadKey((key) => key + 1);
  }, []);

  useEffect(() => {
    if (!enabled) {
      setProducts([]);
      setTotal(0);
      setProductState("ok");
      return;
    }
    let active = true;
    setProductState("loading");
    shopApi
      .products(slug, {
        size: pageSize,
        page: page - 1,
        sort: shopApiSort(sort),
        priceBands: shopPriceBandsQuery(priceBands),
        categoryId: categoryId ?? undefined,
        q: q && q.trim() ? q.trim() : undefined,
      })
      .then((result) => {
        if (!active) return;
        setProducts(result.items);
        setTotal(result.total);
        setProductState("ok");
      })
      .catch(() => {
        if (!active) return;
        setProductState("error");
      });
    return () => {
      active = false;
    };
  }, [slug, enabled, page, sort, priceKey, categoryId, q, pageSize, reloadKey]);

  const catalog = useMemo<ShopCatalogControls>(() => ({
    productState,
    showSkeleton,
    refetching: isRefetching,
    total,
    page,
    pageSize,
    sort,
    grid,
    priceBands,
    brandQuery,
    favorites,
    toggleFavorite,
    products,
    categoryId,
    categoryLocked,
    onSort,
    onGrid,
    onPage,
    onTogglePriceBand,
    onBrandQuery: setBrandQuery,
    onToggleFavorite: (productId) => {
      void toggleFavorite(productId, products.find((product) => product.id === productId)?.slug);
    },
    onCategory,
    onRetry: loadProducts,
    onNotify: notify,
  }), [
    productState,
    showSkeleton,
    isRefetching,
    total,
    page,
    pageSize,
    sort,
    grid,
    priceBands,
    brandQuery,
    favorites,
    categoryId,
    categoryLocked,
    onSort,
    onGrid,
    onPage,
    onTogglePriceBand,
    onCategory,
    loadProducts,
    notify,
  ]);

  return { products, catalog };
}
