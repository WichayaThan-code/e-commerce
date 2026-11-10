import { useCallback, useEffect, useMemo, useState, type CSSProperties } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ArrowRight, ChevronLeft, ChevronRight, MapPin, PackageSearch, SearchX, Store, Tag, X } from "lucide-react";
import EmptyState from "../components/feedback/EmptyState";
import ProductCard from "../components/shop/ProductCard";
import { productGridClass } from "../components/shop/sections/ProductGrid";
import ShopLogo from "../components/ShopLogo";
import { Skeleton, SkeletonCard } from "../components/feedback/Skeleton";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import {
  searchApi,
  type MarketplaceSearchResponse,
  type MarketplaceSearchShopRef,
  type MarketplaceSearchSort,
  type PublicShop,
} from "../services/api";
import { localizedText } from "../services/catalogText";
import { shopPath } from "../services/shopRoutes";
import { normalizeTheme, themeCssVars } from "../theme/shopTheme";
import "../styles/recommended-products.css";
import "../styles/shop-page.css";
import "../styles/shop-mall.css";
import "../styles/marketplace-search.css";

const PAGE_SIZE = 20;
const SORTS: MarketplaceSearchSort[] = ["newest", "price_asc", "price_desc", "name"];
type LoadState = "idle" | "loading" | "ok" | "error";

/** ProductCard needs a PublicShop; search hits carry only the public shop reference used for links. */
function cardShop(ref: MarketplaceSearchShopRef): PublicShop {
  return {
    name: ref.name, nameEn: ref.nameEn ?? undefined, category: "",
    address: "", province: "", district: "", subdistrict: "", postalCode: "",
    slug: ref.slug, path: shopPath(ref.slug), status: "APPROVED", shopId: 0,
  };
}

function parseSort(value: string | null): MarketplaceSearchSort {
  return SORTS.includes(value as MarketplaceSearchSort) ? (value as MarketplaceSearchSort) : "newest";
}

export default function SearchPage() {
  const { tp, localeTag } = usePublicTranslation();
  const [params, setParams] = useSearchParams();
  const q = params.get("q")?.trim() ?? "";
  const brand = params.get("brand")?.trim() || "";
  const sort = parseSort(params.get("sort"));
  const page = Math.max(0, Number(params.get("page") || "1") - 1) || 0;

  const [result, setResult] = useState<MarketplaceSearchResponse | null>(null);
  const [state, setState] = useState<LoadState>("idle");
  const [reloadKey, setReloadKey] = useState(0);
  const [favorites, setFavorites] = useState<string[]>([]);
  const [toast, setToast] = useState("");

  // default storefront tokens so ProductCard (which reads --shop-*) renders like the in-shop catalog
  const themeStyle = useMemo(() => themeCssVars(normalizeTheme(null).config) as CSSProperties, []);

  useDocumentMeta({ title: q ? tp("marketplaceSearch.title", { q }) : tp("marketplaceSearch.titleEmpty") });

  useEffect(() => {
    if (!q && !brand) {
      setResult(null);
      setState("idle");
      return;
    }
    const controller = new AbortController();
    setState("loading");
    searchApi
      .search({ q, brand: brand || undefined, sort, page, size: PAGE_SIZE }, controller.signal)
      .then((response) => {
        setResult(response);
        setState("ok");
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || (error instanceof DOMException && error.name === "AbortError")) return;
        setState("error");
      });
    return () => controller.abort();
  }, [q, brand, sort, page, reloadKey]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), 2200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const update = useCallback(
    (patch: Record<string, string | null>) => {
      const next = new URLSearchParams(params);
      for (const [key, value] of Object.entries(patch)) {
        if (value == null || value === "") next.delete(key);
        else next.set(key, value);
      }
      if (!("page" in patch)) next.delete("page");
      setParams(next);
    },
    [params, setParams],
  );

  const toggleFavorite = (productId: string) =>
    setFavorites((current) => (current.includes(productId) ? current.filter((id) => id !== productId) : [...current, productId]));

  const products = result?.products.items ?? [];
  const productTotal = result?.products.total ?? 0;
  const pages = Math.max(1, Math.ceil(productTotal / PAGE_SIZE));
  const shops = result?.shops ?? [];
  const brands = result?.brands ?? [];
  const loading = state === "loading";
  const nothing = state === "ok" && productTotal === 0 && shops.length === 0 && brands.length === 0;
  const showPreviews = page === 0 && !brand;

  return (
    <div className="recommend-page shop-directory shop-themed marketplace-search" style={themeStyle}>
      <section className="recommend-width marketplace-search-head">
        <p className="recommend-breadcrumb">
          <Link to="/">{tp("common.home")}</Link>
          <span>›</span>
          {tp("marketplaceSearch.breadcrumb")}
        </p>
        <h1>{q ? tp("marketplaceSearch.title", { q }) : tp("marketplaceSearch.titleEmpty")}</h1>
        {state === "ok" && !nothing && (
          <p className="marketplace-search-summary">
            {tp("marketplaceSearch.summary", { products: productTotal, shops: shops.length, brands: brands.length })}
          </p>
        )}
      </section>

      {state === "idle" && (
        <section className="recommend-width">
          <EmptyState icon={PackageSearch} title={tp("marketplaceSearch.prompt")} />
        </section>
      )}

      {state === "error" && (
        <section className="recommend-width">
          <EmptyState
            variant="error"
            title={tp("marketplaceSearch.error")}
            action={{ label: tp("marketplaceSearch.retry"), onClick: () => setReloadKey((key) => key + 1) }}
          />
        </section>
      )}

      {nothing && (
        <section className="recommend-width">
          <EmptyState variant="filtered" icon={SearchX} title={tp("marketplaceSearch.empty", { q: q || brand })} />
        </section>
      )}

      {(loading || (state === "ok" && !nothing)) && (
        <>
          {showPreviews && (loading || shops.length > 0) && (
            <section className="recommend-width marketplace-search-section" aria-labelledby="ms-shops">
              <h2 id="ms-shops"><Store aria-hidden="true" /> {tp("marketplaceSearch.shops")}</h2>
              <div className="shop-card-grid marketplace-search-shops">
                {loading
                  ? Array.from({ length: 4 }, (_, index) => <SkeletonCard key={index} />)
                  : shops.map((shop) => (
                      <Link className="shop-card" to={shopPath(shop.slug)} key={shop.slug}>
                        <div className="shop-card-photo">
                          <ShopLogo src={shop.logoUrl} name={shop.name} />
                        </div>
                        <div className="shop-card-body">
                          {shop.category && <span className="shop-chip">{shop.category}</span>}
                          <h3>{localizedText(localeTag, shop.name, shop.nameEn ?? null, shop.nameZh ?? null)}</h3>
                          {shop.province && (
                            <p className="shop-card-place"><MapPin /> {shop.province}</p>
                          )}
                          <p className="shop-card-text">
                            {tp("marketplaceSearch.productCount", { count: shop.productCount })}
                          </p>
                          <span className="shop-enter">{tp("marketplaceSearch.enterShop")} <ArrowRight /></span>
                        </div>
                      </Link>
                    ))}
              </div>
            </section>
          )}

          {showPreviews && (loading || brands.length > 0) && (
            <section className="recommend-width marketplace-search-section" aria-labelledby="ms-brands">
              <h2 id="ms-brands"><Tag aria-hidden="true" /> {tp("marketplaceSearch.brands")}</h2>
              <div className="marketplace-search-brands">
                {loading
                  ? Array.from({ length: 6 }, (_, index) => <Skeleton key={index} width={96} height={36} radius={999} />)
                  : brands.map((item) => (
                      <button type="button" key={item.name} onClick={() => update({ brand: item.name })}>
                        <span>{item.name}</span>
                        <span className="num">{item.productCount}</span>
                      </button>
                    ))}
              </div>
            </section>
          )}

          <section className="recommend-width marketplace-search-section" aria-labelledby="ms-products">
            <div className="marketplace-search-products-head">
              <h2 id="ms-products"><PackageSearch aria-hidden="true" /> {tp("marketplaceSearch.products")}</h2>
              {brand && (
                <button type="button" className="marketplace-search-brand-filter" onClick={() => update({ brand: null })} aria-label={tp("marketplaceSearch.clearBrand")}>
                  {tp("marketplaceSearch.brandFilter", { brand })} <X aria-hidden="true" />
                </button>
              )}
              <label className="marketplace-search-sort">
                <span className="visually-hidden">{tp("marketplaceSearch.sortAria")}</span>
                <select value={sort} onChange={(event) => update({ sort: event.target.value === "newest" ? null : event.target.value })} aria-label={tp("marketplaceSearch.sortAria")}>
                  <option value="newest">{tp("marketplaceSearch.sortNewest")}</option>
                  <option value="price_asc">{tp("marketplaceSearch.sortPriceAsc")}</option>
                  <option value="price_desc">{tp("marketplaceSearch.sortPriceDesc")}</option>
                  <option value="name">{tp("marketplaceSearch.sortName")}</option>
                </select>
              </label>
            </div>

            <div className="shop-catalog-grid">
              {loading ? (
                <div className={productGridClass(undefined)}>
                  {Array.from({ length: 10 }, (_, index) => <SkeletonCard key={index} />)}
                </div>
              ) : products.length > 0 ? (
                <div className={productGridClass(undefined)}>
                  {products.map((hit) => (
                    <ProductCard
                      key={hit.product.id}
                      shop={cardShop(hit.shop)}
                      product={hit.product}
                      favorites={favorites}
                      onToggleFavorite={toggleFavorite}
                      onNotify={setToast}
                      mallBadge={false}
                    />
                  ))}
                </div>
              ) : (
                <EmptyState variant="filtered" icon={PackageSearch} title={tp("marketplaceSearch.noProducts", { q: q || brand })} />
              )}
            </div>

            {!loading && pages > 1 && (
              <nav className="shop-pagination" aria-label={tp("marketplaceSearch.pageAria")}>
                <button type="button" disabled={page <= 0} onClick={() => update({ page: page <= 1 ? null : String(page) })} aria-label={tp("marketplaceSearch.pagePrev")}>
                  <ChevronLeft />
                </button>
                <span className="marketplace-search-pageof num">{tp("marketplaceSearch.pageOf", { page: page + 1, pages })}</span>
                <button type="button" disabled={page + 1 >= pages} onClick={() => update({ page: String(page + 2) })} aria-label={tp("marketplaceSearch.pageNext")}>
                  <ChevronRight />
                </button>
              </nav>
            )}
          </section>
        </>
      )}

      {toast && <div className="recommend-toast" role="status">{toast}</div>}
    </div>
  );
}
