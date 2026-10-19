import { useEffect, useState, type CSSProperties } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { ChevronLeft } from "lucide-react";
import { Skeleton, LoadError } from "../components/feedback/Skeleton";
import ShopStorefront from "../components/shop/ShopStorefront";
import type { ShopCatalogControls, ShopSortKey } from "../components/shop/sections/types";
import { useDelayedLoading } from "../hooks/useDelayedLoading";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { shopApi, type ProductSort, type PublicProduct, type PublicShop } from "../services/api";
import { shopPriceBandsQuery, type ShopPriceBandId } from "../services/shopPriceBands";
import { shopThemeFontStack } from "../theme/shopTheme";
import "../styles/recommended-products.css";
import "../styles/shop-page.css";

type LoadState = "loading" | "error" | "ok";

const PAGE_SIZE = 30;

function apiSort(sort: ShopSortKey): ProductSort {
  return sort === "price" ? "price_asc" : "newest";
}

export default function ShopPage() {
  const { slug = "" } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { tp } = usePublicTranslation();
  const [shop, setShop] = useState<PublicShop | null>(null);
  const [products, setProducts] = useState<PublicProduct[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ShopSortKey>("featured");
  const [grid, setGrid] = useState(true);
  const [brandQuery, setBrandQuery] = useState("");
  const [favorites, setFavorites] = useState<string[]>([]);
  const [priceBands, setPriceBands] = useState<ShopPriceBandId[]>([]);
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [notice, setNotice] = useState("");
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [productState, setProductState] = useState<LoadState>("loading");

  const shopLoading = loadState === "loading";
  const productLoading = productState === "loading";
  const {
    showSkeleton: showShopSkeleton,
    markHasData: markShopLoaded,
    isRefetching: shopRefetching,
  } = useDelayedLoading(shopLoading, 150, slug);
  const {
    showSkeleton: showProductSkeleton,
    markHasData: markProductsLoaded,
    isRefetching: productsRefetching,
  } = useDelayedLoading(productLoading, 150, slug);

  useEffect(() => {
    if (loadState === "ok" && shop) markShopLoaded();
  }, [loadState, shop, markShopLoaded]);

  useEffect(() => {
    if (productState === "ok") markProductsLoaded();
  }, [productState, markProductsLoaded]);

  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 1900);
  };

  const productQuery = () => ({
    size: PAGE_SIZE,
    page: page - 1,
    sort: apiSort(sort),
    priceBands: shopPriceBandsQuery(priceBands),
    categoryId: categoryId ?? undefined,
  });

  const togglePriceBand = (band: ShopPriceBandId) => {
    setPriceBands((current) => (current.includes(band) ? current.filter((item) => item !== band) : [...current, band]));
    setPage(1);
  };

  const loadProducts = () => {
    setProductState("loading");
    shopApi.products(slug, productQuery())
      .then((result) => {
        setProducts(result.items);
        setTotal(result.total);
        setProductState("ok");
      })
      .catch(() => setProductState("error"));
  };

  const reloadShop = () => {
    setLoadState("loading");
    setProductState("loading");
    shopApi
      .getBySlug(slug)
      .then((data) => {
        setShop(data);
        setLoadState("ok");
      })
      .catch(() => {
        setLoadState("error");
        setProductState("error");
      });
  };

  // Reload shop on every visit (location.key) so theme edits are never served from stale state.
  useEffect(() => {
    let active = true;
    setShop(null);
    setProducts([]);
    setTotal(0);
    setPage(1);
    setSort("featured");
    setPriceBands([]);
    setCategoryId(null);
    setLoadState("loading");
    setProductState("loading");
    shopApi
      .getBySlug(slug)
      .then((data) => {
        if (!active) return;
        setShop(data);
        setLoadState("ok");
      })
      .catch(() => {
        if (!active) return;
        setLoadState("error");
        setProductState("error");
      });
    return () => {
      active = false;
    };
  }, [slug, location.key]);

  useEffect(() => {
    if (!shop) return;
    let active = true;
    setProductState("loading");
    shopApi.products(slug, productQuery())
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
  }, [shop, slug, page, sort, priceBands, categoryId]);

  const catalog: ShopCatalogControls = {
    productState,
    showSkeleton: showProductSkeleton,
    refetching: productsRefetching,
    total,
    page,
    pageSize: PAGE_SIZE,
    sort,
    grid,
    priceBands,
    brandQuery,
    favorites,
    categoryId,
    onSort: (next) => {
      setSort(next);
      setPage(1);
    },
    onGrid: setGrid,
    onPage: setPage,
    onTogglePriceBand: (band) => togglePriceBand(band as ShopPriceBandId),
    onBrandQuery: setBrandQuery,
    onCategory: (next) => {
      setCategoryId(next);
      setPage(1);
      if (next != null) {
        document.getElementById("shop-catalog")?.scrollIntoView({ behavior: "smooth", block: "start" });
      }
    },
    onToggleFavorite: (productId) => setFavorites((current) => (
      current.includes(productId) ? current.filter((id) => id !== productId) : [...current, productId]
    )),
    onRetry: loadProducts,
    onNotify: notify,
  };

  const shopTitle = shop?.name || tp("common.shopPlaceholder");

  const header = (
    <section className="recommend-intro recommend-width shop-page-intro">
      <div className="shop-mobile-bar">
        <button type="button" className="shop-back" onClick={() => navigate(-1)} aria-label={tp("shop.productBack")}>
          <ChevronLeft />
        </button>
        <h1 className="shop-mobile-title">{shopTitle}</h1>
      </div>
      <div className="shop-breadcrumb-wrap">
        <p className="recommend-breadcrumb">
          <Link to="/">{tp("common.home")}</Link>
          <span>›</span>
          <Link to="/shops">{tp("shop.breadcrumb")}</Link>
          <span>›</span>
          {shopTitle}
        </p>
        {!shop && loadState === "loading" && !showShopSkeleton && (
          <>
            <h1>{tp("shop.loading")}</h1>
            <p>{tp("shop.defaultDescription")}</p>
          </>
        )}
      </div>
    </section>
  );

  const toast = notice ? (
    <div className="recommend-toast shop-toast" role="status">
      {notice}
    </div>
  ) : null;

  if (shop) {
    return (
      <ShopStorefront shop={shop} products={products} catalog={catalog} header={header} refetching={shopRefetching}>
        {toast}
      </ShopStorefront>
    );
  }

  const storefrontFont = { "--font-sans": shopThemeFontStack(undefined) } as CSSProperties;

  return (
    <div className="recommend-page shop-directory shop-storefront-cq" style={storefrontFont}>
      {header}

      {showShopSkeleton && loadState === "loading" && (
        <section className="shop-hero recommend-width shop-hero-skeleton" aria-hidden="true">
          <div className="shop-hero-cover">
            <Skeleton className="shop-hero-photo" height="100%" width="100%" />
          </div>
          <div className="shop-hero-panel">
            <Skeleton className="shop-profile-mark" width={72} height={72} radius={16} />
            <div className="shop-hero-copy">
              <Skeleton width={88} height={22} radius={999} />
              <Skeleton width="70%" height={22} />
              <Skeleton width="45%" height={14} />
              <Skeleton width="90%" height={14} />
              <Skeleton width="80%" height={14} />
            </div>
          </div>
        </section>
      )}

      {loadState === "error" && (
        <section className="recommend-width">
          <LoadError message={tp("shop.loadError")} onRetry={reloadShop} retryLabel={tp("shop.productsRetry")} />
        </section>
      )}
      {toast}
    </div>
  );
}
