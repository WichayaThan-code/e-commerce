import { useCallback, useEffect, useMemo, useState, type CSSProperties } from "react";
import { usesTsfTree } from "../theme/shopTheme";
import { Link, Outlet, useLocation, useMatch, useNavigate, useParams } from "react-router-dom";
import { ChevronLeft } from "lucide-react";
import { LoadError, Skeleton } from "../components/feedback/Skeleton";
import ShopTabs from "../components/shop/ShopTabs";
import ShopThemeShell, { resolveStorefrontTheme } from "../components/shop/ShopThemeShell";
import { TsfMobileTabBar } from "../components/shop/general/GeneralStorefront";
import { ShopPublicProvider } from "../contexts/ShopPublicContext";
import { useDelayedLoading } from "../hooks/useDelayedLoading";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import NotFoundPage from "../pages/NotFoundPage";
import { ApiError, shopApi, type PublicShop } from "../services/api";
import { shopPath } from "../services/shopRoutes";
import { shopThemeFontStack } from "../theme/shopTheme";

type LoadState = "loading" | "error" | "missing" | "ok";

export default function ShopLayout() {
  const { slug = "" } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const isHome = Boolean(useMatch({ path: "/shop/:slug", end: true }));
  const { tp } = usePublicTranslation();
  const [shop, setShop] = useState<PublicShop | null>(null);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [notice, setNotice] = useState("");
  const [crumb, setCrumb] = useState<string | null>(null);

  const shopLoading = loadState === "loading";
  const {
    showSkeleton: showShopSkeleton,
    markHasData: markShopLoaded,
    isRefetching: shopRefetching,
  } = useDelayedLoading(shopLoading, 150, slug);

  useEffect(() => {
    if (loadState === "ok" && shop) markShopLoaded();
  }, [loadState, shop, markShopLoaded]);

  const notify = useCallback((message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 1900);
  }, []);

  const reloadShop = useCallback(() => {
    setLoadState("loading");
    shopApi
      .getBySlug(slug)
      .then((data) => {
        setShop(data);
        setLoadState("ok");
      })
      .catch((error) => {
        setShop(null);
        setLoadState(error instanceof ApiError && error.status === 404 ? "missing" : "error");
      });
  }, [slug]);

  // Load when slug changes only (not location.key) — remount after theme editor still refreshes.
  useEffect(() => {
    let active = true;
    setShop(null);
    setCrumb(null);
    setLoadState("loading");
    shopApi
      .getBySlug(slug)
      .then((data) => {
        if (!active) return;
        setShop(data);
        setLoadState("ok");
      })
      .catch((error) => {
        if (!active) return;
        setShop(null);
        setLoadState(error instanceof ApiError && error.status === 404 ? "missing" : "error");
      });
    return () => {
      active = false;
    };
  }, [slug]);

  useEffect(() => {
    if (location.hash) {
      const id = location.hash.slice(1);
      window.requestAnimationFrame(() => {
        document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
      });
      return;
    }
    window.scrollTo(0, 0);
  }, [location.pathname, location.hash]);

  const shopTitle = shop?.name || tp("common.shopPlaceholder");
  const mobileTitle = crumb ?? shopTitle;

  // GENERAL (themes spec): store home gets the burger / logo / search / cart header + bottom tab bar on mobile.
  // Themes port: GENERAL renders the dedicated .tsf-store tree (announce / header / footer / tab bar live in ShopThemeShell).
  const general = Boolean(shop && usesTsfTree(resolveStorefrontTheme(shop).templateKey));

  const header = (
    <section className="recommend-intro recommend-width shop-page-intro">
      <div className="shop-mobile-bar">
        <button type="button" className="shop-back" onClick={() => navigate(-1)} aria-label={tp("shop.productBack")}>
          <ChevronLeft />
        </button>
        <h1 className="shop-mobile-title">{mobileTitle}</h1>
      </div>
      <div className="shop-breadcrumb-wrap">
        <p className="recommend-breadcrumb">
          <Link to="/">{tp("common.home")}</Link>
          <span>›</span>
          <Link to="/shops">{tp("shop.breadcrumb")}</Link>
          <span>›</span>
          {crumb && shop ? (
            <>
              <Link to={shopPath(slug)}>{shopTitle}</Link>
              <span>›</span>
              <b>{crumb}</b>
            </>
          ) : (
            shopTitle
          )}
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

  const contextValue = useMemo(() => {
    if (!shop) return null;
    return {
      slug,
      shop,
      theme: resolveStorefrontTheme(shop),
      shopRefetching,
      reloadShop,
      notify,
      setCrumb,
    };
  }, [slug, shop, shopRefetching, reloadShop, notify]);

  if (loadState === "missing") {
    return <NotFoundPage kind="shop" />;
  }

  if (shop && contextValue) {
    return (
      <ShopPublicProvider value={contextValue}>
        {general ? (
          <ShopThemeShell
            shop={shop}
            header={isHome ? null : <div className="tsf-wrap">{header}</div>}
            refetching={shopRefetching}
            after={<>{toast}<TsfMobileTabBar shop={shop} onNotify={notify} /></>}
          >
            {isHome ? <Outlet /> : <div className="tsf-wrap tsf-subpage"><Outlet /></div>}
          </ShopThemeShell>
        ) : (
          <ShopThemeShell shop={shop} header={header} refetching={shopRefetching} after={toast}>
            {!isHome && <ShopTabs shop={shop} onNotify={notify} />}
            <Outlet />
          </ShopThemeShell>
        )}
      </ShopPublicProvider>
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
