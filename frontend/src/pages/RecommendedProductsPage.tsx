import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  ArrowRight,
  Baby,
  CircleEllipsis,
  Dumbbell,
  Gift,
  Grid2X2,
  Heart,
  Leaf,
  List,
  Package,
  Search,
  SearchX,
  Shirt,
  ShoppingBag,
  ShoppingCart,
  Sofa,
  Sparkles,
  Store,
  Utensils,
  Wine,
} from "lucide-react";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { useAuth } from "../contexts/AuthContext";
import { loginPathForCart, useOptionalCart } from "../contexts/CartContext";
import {
  ApiError,
  catalogApi,
  mediaUrl,
  recommendationApi,
  type BestSellerProduct,
  type ProductCategory,
  type RecommendationSort,
} from "../services/api";
import recommendReference from "../assets/images/references/storefront/recommend-product.png";
import EmptyState from "../components/feedback/EmptyState";
import "../styles/recommended-products.css";
import "../styles/shop-page.css";

const categoryIcons = [Sparkles, Shirt, Wine, Sofa, ShoppingBag, Utensils, Dumbbell, Baby, Gift, Gift, Leaf, CircleEllipsis];
const ratingStars = ["★★★★", "★★★½", "★★★", "★★"] as const;
/** Same order as catalog.priceRanges / catalog.ratingRanges in the locale files. */
const priceBands: [number | undefined, number | undefined][] = [[undefined, 99.99], [100, 300], [301, 500], [501, 1000], [1000.01, undefined]];
const ratingMins = [4.5, 4, 3.5, 3];
type SortKey = "featured" | "price" | "best";
const sortParam: Record<SortKey, RecommendationSort> = { featured: "featured", price: "price_asc", best: "best" };
const PAGE_SIZE = 12;

const num = (value: number | string | null | undefined) =>
  value === null || value === undefined || value === "" ? null : Number(value);
const priceOf = (p: BestSellerProduct) => num(p.basePrice) ?? num(p.skus?.[0]?.price) ?? 0;
const compareOf = (p: BestSellerProduct) => num(p.compareAtPrice) ?? num(p.skus?.[0]?.compareAtPrice);
const primaryImage = (p: BestSellerProduct) =>
  [...(p.images ?? [])].sort((a, b) => Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder)[0];
const fillImage = { left: 0, top: 0, width: "100%", height: "100%", objectFit: "cover" as const };

const texts = {
  th: { all: "ทั้งหมด", login: "กรุณาเข้าสู่ระบบก่อน", blocked: "บัญชีนี้ไม่สามารถใช้ตะกร้าสินค้าได้", pick: "กรุณาเลือกตัวเลือกสินค้า", out: "สินค้าหมด", low: "สินค้าในสต็อกไม่เพียงพอ", added: "เพิ่มสินค้าลงตะกร้าแล้ว", failed: "เพิ่มสินค้าไม่สำเร็จ", error: "โหลดสินค้าแนะนำไม่สำเร็จ", retry: "ลองใหม่", empty: "ยังไม่มีสินค้าที่ตรงกับตัวกรอง", noBrand: "ยังไม่มีแบรนด์", end: "แสดงสินค้าครบแล้ว" },
  en: { all: "All", login: "Please sign in first", blocked: "This account cannot use the cart", pick: "Please choose product options", out: "Out of stock", low: "Not enough stock", added: "Added to cart", failed: "Could not add to cart", error: "Could not load recommendations", retry: "Retry", empty: "No products match these filters", noBrand: "No brands yet", end: "You've seen everything" },
  zh: { all: "全部", login: "请先登录", blocked: "此账户无法使用购物车", pick: "请选择商品规格", out: "已售罄", low: "库存不足", added: "已加入购物车", failed: "加入购物车失败", error: "推荐商品加载失败", retry: "重试", empty: "没有符合筛选条件的商品", noBrand: "暂无品牌", end: "已显示全部商品" },
};

type RecommendBanner = { title: string; text: string; btn: string; toast: string };

export default function RecommendedProductsPage() {
  const { tp, localeTag } = usePublicTranslation();
  const lang = localeTag.startsWith("zh") ? "zh" : localeTag.startsWith("en") ? "en" : "th";
  const t = texts[lang];
  const navigate = useNavigate();
  const { user } = useAuth();
  const cartApi = useOptionalCart();
  const [activeCategory, setActiveCategory] = useState(0);
  const [categoryList, setCategoryList] = useState<ProductCategory[]>([]);
  const [notice, setNotice] = useState("");
  const [sort, setSort] = useState<SortKey>("featured");
  const [grid, setGrid] = useState(true);
  const [brandQuery, setBrandQuery] = useState("");
  const [favorites, setFavorites] = useState<string[]>([]);
  const [priceChecked, setPriceChecked] = useState<number[]>([]);
  const [ratingChecked, setRatingChecked] = useState<number[]>([]);
  const [brandChecked, setBrandChecked] = useState<string[]>([]);
  const [knownBrands, setKnownBrands] = useState<string[]>([]);
  const [items, setItems] = useState<BestSellerProduct[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [fetching, setFetching] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [reload, setReload] = useState(0);

  const priceRanges = tp("catalog.priceRanges", { returnObjects: true }) as unknown as string[];
  const ratingRanges = tp("catalog.ratingRanges", { returnObjects: true }) as unknown as string[];
  const banners = tp("recommend.banners", { returnObjects: true }) as unknown as RecommendBanner[];

  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 1900);
  };

  useEffect(() => {
    catalogApi.categories().then(setCategoryList).catch(() => setCategoryList([]));
  }, []);

  const categoryName = (c: ProductCategory) => (lang === "en" ? c.nameEn : lang === "zh" ? c.nameZh : c.nameTh) || c.nameTh;
  const chips = useMemo(
    () => [{ id: undefined as number | undefined, label: t.all }, ...categoryList.slice(0, categoryIcons.length - 1).map((c) => ({ id: c.id as number | undefined, label: categoryName(c) }))],
    [categoryList, lang],
  );
  const selectedCategory = chips[activeCategory]?.id;

  const priceQuery = useMemo(() => {
    if (!priceChecked.length) return {};
    const bands = priceChecked.map((i) => priceBands[i]);
    const min = bands.some(([lo]) => lo === undefined) ? undefined : Math.min(...bands.map(([lo]) => lo as number));
    const max = bands.some(([, hi]) => hi === undefined) ? undefined : Math.max(...bands.map(([, hi]) => hi as number));
    return { minPrice: min, maxPrice: max };
  }, [priceChecked]);
  const minRating = ratingChecked.length ? Math.min(...ratingChecked.map((i) => ratingMins[i])) : undefined;
  const brands = brandChecked.join(",");
  const filterKey = JSON.stringify([selectedCategory, priceQuery, minRating, brands, sort, reload]);

  useEffect(() => { setPage(0); }, [filterKey]);

  useEffect(() => {
    let alive = true;
    setFetching(true);
    setLoadError(false);
    recommendationApi
      .list({ categoryId: selectedCategory, ...priceQuery, minRating, brands: brands || undefined, sort: sortParam[sort], page, size: PAGE_SIZE })
      .then((result) => {
        if (!alive) return;
        setItems((prev) => (page === 0 ? result.items : [...prev, ...result.items.filter((p) => !prev.some((x) => x.id === p.id))]));
        setTotal(result.total);
        setKnownBrands((prev) => {
          const next = new Set(prev);
          result.items.forEach((p) => { if (p.brand?.trim()) next.add(p.brand.trim()); });
          return [...next].sort((a, b) => a.localeCompare(b));
        });
      })
      .catch(() => { if (alive) { setLoadError(true); if (page === 0) { setItems([]); setTotal(0); } } })
      .finally(() => { if (alive) setFetching(false); });
    return () => { alive = false; };
  }, [filterKey, page]);

  const toggle = <T,>(setter: (fn: (v: T[]) => T[]) => void, value: T) =>
    setter((v) => (v.includes(value) ? v.filter((x) => x !== value) : [...v, value]));

  const toggleFavorite = (id: string) => {
    if (!user) {
      notify(t.login);
      navigate(`/login?next=${encodeURIComponent("/recommendations")}`);
      return;
    }
    setFavorites((v) => (v.includes(id) ? v.filter((x) => x !== id) : [...v, id]));
  };

  const addToCart = async (p: BestSellerProduct) => {
    const href = `/shop/${p.shopSlug}/product/${p.slug}`;
    if (!cartApi) { navigate(href); return; }
    if (cartApi.access === "guest") { notify(t.login); window.location.assign(loginPathForCart()); return; }
    if (cartApi.access === "blocked") { notify(t.blocked); return; }
    if (cartApi.access === "loading") return;
    const skus = p.skus ?? [];
    if (skus.length > 1) { notify(t.pick); navigate(href); return; }
    const sku = skus[0];
    if (!sku || sku.available <= 0) { notify(t.out); return; }
    try {
      const result = await cartApi.add(sku.id, 1);
      notify(result?.adjusted && result.appliedQuantity < 1 ? t.low : t.added);
    } catch (error) {
      notify(error instanceof ApiError ? error.message : t.failed);
    }
  };

  const productName = (p: BestSellerProduct) => (lang === "en" ? p.nameEn : lang === "zh" ? p.nameZh : p.nameTh) || p.nameTh;
  const shopName = (p: BestSellerProduct) => (lang !== "th" && p.shopNameEn) || p.shopName;
  const shownBrands = [...new Set([...knownBrands, ...brandChecked])].filter((x) => x.toLowerCase().includes(brandQuery.trim().toLowerCase()));
  const hasMore = items.length < total;
  const hasActiveFilters =
    brandChecked.length > 0 || priceChecked.length > 0 || ratingChecked.length > 0 || activeCategory > 0;
  const bannerEmojis = ["🎁", "🚚", "🌍"];

  return (
    <main className="recommend-page">
      <section className="recommend-intro recommend-width">
        <div>
          <p className="recommend-breadcrumb">
            <Link to="/">{tp("common.home")}</Link>
            <span>›</span>
            {tp("recommend.breadcrumb")}
          </p>
          <h1>{tp("recommend.title")}</h1>
          <p>{tp("recommend.subtitle")}</p>
        </div>
        <div className="recommend-hero">
          <img src={recommendReference} alt={tp("recommend.heroAlt")} />
        </div>
      </section>

      <section className="recommend-categories recommend-width">
        {chips.map((chip, index) => {
          const Icon = categoryIcons[index % categoryIcons.length];
          return (
            <button
              className={activeCategory === index ? "active" : ""}
              key={`${chip.id ?? "all"}-${index}`}
              type="button"
              onClick={() => {
                setActiveCategory(index);
                notify(tp("catalog.pickCategory", { name: chip.label }));
              }}
            >
              <Icon />
              <span>{chip.label}</span>
            </button>
          );
        })}
      </section>

      <section className="recommend-catalog recommend-width">
        <aside className="recommend-filters">
          <h2>{tp("catalog.filters")}</h2>
          <hr />
          <h3>{tp("catalog.price")}</h3>
          {priceRanges.map((x, index) => (
            <label key={x}>
              <input type="checkbox" checked={priceChecked.includes(index)} onChange={() => toggle(setPriceChecked, index)} />
              {x}
            </label>
          ))}
          <hr />
          <h3>{tp("catalog.rating")}</h3>
          {ratingRanges.map((text, index) => (
            <label className="rating-filter" key={text}>
              <input type="checkbox" checked={ratingChecked.includes(index)} onChange={() => toggle(setRatingChecked, index)} />
              <b>{ratingStars[index]}</b>
              {text}
            </label>
          ))}
          <hr />
          <h3>{tp("catalog.brands")}</h3>
          <div className="brand-search">
            <Search />
            <input value={brandQuery} onChange={(e) => setBrandQuery(e.target.value)} placeholder={tp("catalog.brandSearch")} />
          </div>
          {shownBrands.length === 0 && !fetching && <p className="recommend-shop">{t.noBrand}</p>}
          {shownBrands.map((x) => (
            <label key={x}>
              <input type="checkbox" checked={brandChecked.includes(x)} onChange={() => toggle(setBrandChecked, x)} />
              {x}
            </label>
          ))}
        </aside>
        <div className="recommend-results">
          <div className="recommend-tools">
            <span>{tp("common.sortBy")}</span>
            <select value={sort} onChange={(e) => setSort(e.target.value as SortKey)} aria-label={tp("catalog.sortAria")}>
              <option value="featured">{tp("catalog.sortFeatured")}</option>
              <option value="price">{tp("catalog.sortPrice")}</option>
              <option value="best">{tp("catalog.sortBest")}</option>
            </select>
            <button className={grid ? "active" : ""} type="button" onClick={() => setGrid(true)} aria-label={tp("common.gridView")}>
              <Grid2X2 />
            </button>
            <button className={!grid ? "active" : ""} type="button" onClick={() => setGrid(false)} aria-label={tp("common.listView")}>
              <List />
            </button>
          </div>
          {loadError && items.length === 0 ? (
            <EmptyState
              variant="error"
              title={tp("home.loadError")}
              action={{ label: tp("home.retry"), onClick: () => setReload((v) => v + 1) }}
            />
          ) : !fetching && items.length === 0 ? (
            hasActiveFilters ? (
              <EmptyState variant="filtered" icon={SearchX} title={tp("recommend.emptyFiltered")} />
            ) : (
              <EmptyState icon={Package} title={tp("home.emptyRecommended")} />
            )
          ) : (
            <div className={`recommend-product-grid ${grid ? "" : "list-view"}`} aria-busy={fetching}>
              {items.map((p) => {
                const image = primaryImage(p);
                const price = priceOf(p);
                const compare = compareOf(p);
                const discount = compare && compare > price ? Math.round((1 - price / compare) * 100) : 0;
                const href = `/shop/${p.shopSlug}/product/${p.slug}`;
                return (
                  <article className="recommend-product" key={p.id}>
                    <div className="recommend-photo">
                      <div className="recommend-sprite" role="link" tabIndex={0} onClick={() => navigate(href)} onKeyDown={(e) => { if (e.key === "Enter") navigate(href); }} aria-label={productName(p)} style={{ cursor: "pointer" }}>
                        {image && <img src={mediaUrl(image.url)} alt={productName(p)} style={fillImage} loading="lazy" />}
                      </div>
                      <button
                        type="button"
                        className={favorites.includes(p.id) ? "liked" : ""}
                        onClick={() => toggleFavorite(p.id)}
                        aria-label={tp("catalog.favorite")}
                      >
                        <Heart />
                      </button>
                    </div>
                    <div className="recommend-product-info">
                      <h2>
                        <Link to={href}><span>{productName(p)}</span></Link>
                      </h2>
                      <p className="recommend-shop">
                        <Store /> <Link to={`/shop/${p.shopSlug}`}>{shopName(p)}</Link>
                      </p>
                      <p className="recommend-price">
                        <b>฿{price.toLocaleString(localeTag)}</b>
                        {discount > 0 && compare && <del>฿{compare.toLocaleString(localeTag)}</del>}
                        {discount > 0 && <em>-{discount}%</em>}
                      </p>
                      <p className="recommend-meta">
                        {p.rating !== null && p.rating !== undefined && (
                          <>
                            <b>★ {Number(p.rating).toLocaleString(localeTag)}</b> ({(p.reviewCount ?? 0).toLocaleString(localeTag)}){" "}
                          </>
                        )}
                        {p.soldCount > 0 && (
                          <span>
                            {tp("common.sold")} {p.soldCount.toLocaleString(localeTag)}
                          </span>
                        )}
                      </p>
                      <button type="button" onClick={() => void addToCart(p)}>
                        <ShoppingCart />
                        {tp("recommend.addToCart")}
                      </button>
                    </div>
                  </article>
                );
              })}
              {fetching &&
                Array.from({ length: page === 0 ? 6 : 3 }, (_, i) => (
                  <article className="recommend-product" key={`skeleton-${i}`} aria-hidden="true" style={{ opacity: 0.55 }}>
                    <div className="recommend-photo" style={{ background: "#eef0f3" }} />
                    <div className="recommend-product-info">
                      <h2><span style={{ display: "block", height: 14, width: "80%", background: "#eef0f3", borderRadius: 6 }} /></h2>
                      <p className="recommend-shop"><span style={{ display: "block", height: 10, width: "50%", background: "#eef0f3", borderRadius: 6 }} /></p>
                      <p className="recommend-price"><span style={{ display: "block", height: 14, width: "35%", background: "#eef0f3", borderRadius: 6 }} /></p>
                    </div>
                  </article>
                ))}
            </div>
          )}
          <div className="recommend-banners">
            {banners.map((banner, index) => (
              <article key={banner.btn}>
                <div>
                  <h2>
                    {banner.title.split("\n").map((line, i) => (
                      <span key={i}>
                        {line}
                        {i === 0 && banner.title.includes("\n") && <br />}
                      </span>
                    ))}
                  </h2>
                  <p>{banner.text}</p>
                  <button type="button" onClick={() => notify(banner.toast)}>
                    {banner.btn}
                  </button>
                </div>
                <span>{bannerEmojis[index]}</span>
              </article>
            ))}
          </div>
          {items.length > 0 && (
            <button
              className="view-all"
              type="button"
              disabled={fetching || !hasMore}
              onClick={() => {
                if (!hasMore) { notify(t.end); return; }
                notify(tp("recommend.loadMore"));
                setPage((v) => v + 1);
              }}
            >
              {hasMore ? tp("recommend.viewAllRecommended") : t.end} <ArrowRight />
            </button>
          )}
        </div>
      </section>
      {notice && (
        <div className="recommend-toast" role="status">
          {notice}
        </div>
      )}
    </main>
  );
}
