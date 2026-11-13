import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  BarChart3,
  TrendingUp,
  Grid2X2,
  Heart,
  LayoutGrid,
  List,
  ShoppingCart,
  Store,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { loginPathForCart, useOptionalCart } from "../contexts/CartContext";
import {
  ApiError,
  bestSellerApi,
  catalogApi,
  favoritesApi,
  mediaUrl,
  type BestSellerProduct,
  type ProductCategory,
} from "../services/api";
import bestSellerReference from "../assets/images/references/storefront/best-seller.png";
import EmptyState from "../components/feedback/EmptyState";
import {
  buildRecommendCategoryTiles,
  findCategoryIdByHints,
  parseCategoryParam,
} from "../components/marketplace/categoryTiles";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import "../styles/recommended-products.css";
import "../styles/best-seller.css";
import "../styles/shop-page.css";
import "../styles/product-card.css";

/** Sidebar price radios -> [min, max] (inclusive, baht). */
const priceBands: [string, number | undefined, number | undefined][] = [
  ["ทั้งหมด", undefined, undefined],
  ["ต่ำกว่า 100 บาท", undefined, 99.99],
  ["100 - 300 บาท", 100, 300],
  ["301 - 500 บาท", 301, 500],
  ["501 - 1,000 บาท", 501, 1000],
  ["มากกว่า 1,000 บาท", 1000.01, undefined],
];
const rankTabs = [
  "ยอดขายสูงสุด",
  "คะแนนรีวิวสูงสุด",
  "สินค้ายอดนิยมประจำสัปดาห์",
  "สินค้า OTOP ขายดี",
];
/** Rank tab -> sales period + ranking mode (tab 1 = top_rated by reviews; tab 3 = OTOP category by slug). */
const rankPeriods = ["all", "all", "7d", "all"] as const;
const rankSorts = ["best_selling", "top_rated", "best_selling", "best_selling"] as const;
const OTOP_CATEGORY_SLUG = "otop-products";
const PAGE_SIZE = 24;
const num = (value: number | string | null | undefined) =>
  value === null || value === undefined || value === "" ? null : Number(value);
const priceOf = (p: BestSellerProduct) => {
  const sku = p.skus?.[0];
  return num(p.basePrice) ?? num(sku?.price) ?? 0;
};
const compareOf = (p: BestSellerProduct) =>
  num(p.compareAtPrice) ?? num(p.skus?.[0]?.compareAtPrice);
const primaryImage = (p: BestSellerProduct) =>
  [...(p.images ?? [])].sort(
    (a, b) =>
      Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder,
  )[0];


export default function BestSellerPage() {
  const { tp, localeTag } = usePublicTranslation();
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const lang = localeTag.startsWith("zh") ? "zh" : localeTag.startsWith("en") ? "en" : "th";
  const allLabel = lang === "en" ? "All" : lang === "zh" ? "全部" : "ทั้งหมด";
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | undefined>(() =>
    parseCategoryParam(searchParams.get("category")),
  );
  const [activeRank, setActiveRank] = useState(0);
  const cartApi = useOptionalCart();
  const [categoryList, setCategoryList] = useState<ProductCategory[] | null>(null);
  const [priceBand, setPriceBand] = useState(0);
  const [appliedPrice, setAppliedPrice] = useState<{
    min?: number;
    max?: number;
  }>({});
  const [items, setItems] = useState<BestSellerProduct[]>([]);
  const [total, setTotal] = useState(0);
  const [fetching, setFetching] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [notice, setNotice] = useState("");
  const [sort, setSort] = useState("ยอดขายสูงสุด");
  const [grid, setGrid] = useState(true);
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const fallbackCategories = tp("home.categories", { returnObjects: true }) as unknown as string[];
  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 1900);
  };
  useEffect(() => {
    catalogApi
      .categories()
      .then(setCategoryList)
      .catch(() => setCategoryList([]));
  }, []);
  useEffect(() => {
    if (!user) { setFavorites(new Set()); return; }
    favoritesApi.list()
      .then((rows) => setFavorites(new Set(rows.map((row) => row.productId))))
      .catch(() => setFavorites(new Set()));
  }, [user]);

  useEffect(() => {
    const fromId = parseCategoryParam(searchParams.get("category"));
    if (fromId != null) {
      setSelectedCategoryId(fromId);
      return;
    }
    const hint = searchParams.get("categoryName")?.trim();
    if (hint) {
      if (!categoryList?.length) return;
      const matched = findCategoryIdByHints(categoryList, [hint]);
      if (matched == null) {
        setSelectedCategoryId(undefined);
        return;
      }
      setSelectedCategoryId(matched);
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        next.delete("categoryName");
        next.set("category", String(matched));
        return next;
      }, { replace: true });
      return;
    }
    setSelectedCategoryId(undefined);
  }, [searchParams, categoryList, setSearchParams]);

  useEffect(() => {
    if (!categoryList || categoryList.length === 0) return;
    if (selectedCategoryId == null) return;
    if (categoryList.some((c) => c.id === selectedCategoryId)) return;
    setSelectedCategoryId(undefined);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete("category");
      next.delete("categoryName");
      return next;
    }, { replace: true });
  }, [categoryList, selectedCategoryId, setSearchParams]);

  const toggleFavorite = async (productId: string) => {
    if (!user) {
      window.location.assign(`/login?next=${encodeURIComponent("/best-sellers")}`);
      return;
    }
    const liked = favorites.has(productId);
    setFavorites((prev) => {
      const next = new Set(prev);
      if (liked) next.delete(productId);
      else next.add(productId);
      return next;
    });
    try {
      if (liked) await favoritesApi.remove(productId);
      else await favoritesApi.add(productId);
      notify(liked ? tp("favorites.removed") : tp("favorites.added"));
    } catch (error) {
      setFavorites((prev) => {
        const next = new Set(prev);
        if (liked) next.add(productId);
        else next.delete(productId);
        return next;
      });
      notify(error instanceof ApiError ? error.message : tp("favorites.actionFail"));
    }
  };
  const categoryTiles = useMemo(
    () => buildRecommendCategoryTiles(categoryList, fallbackCategories, localeTag, allLabel),
    [categoryList, fallbackCategories, localeTag, allLabel],
  );
  const otopTab = activeRank === 3;
  const selectedCategory = otopTab ? undefined : selectedCategoryId;
  const setCategoryInUrl = (id: number | undefined) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete("categoryName");
      if (id == null) next.delete("category");
      else next.set("category", String(id));
      return next;
    }, { replace: true });
    setSelectedCategoryId(id);
  };
  const selectCategory = (id: number | undefined, label: string) => {
    setCategoryInUrl(id);
    notify(tp("catalog.pickCategory", { name: label.replace(/\n/g, " ") }));
  };
  const [bandMin, bandMax] = [priceBands[priceBand][1], priceBands[priceBand][2]];
  const queryMin = appliedPrice.min ?? bandMin;
  const queryMax = appliedPrice.max ?? bandMax;
  useEffect(() => {
    let alive = true;
    setFetching(true);
    setLoadError(false);
    bestSellerApi
      .list({
        categoryId: selectedCategory,
        categorySlug: otopTab ? OTOP_CATEGORY_SLUG : undefined,
        sort: rankSorts[activeRank],
        period: rankPeriods[activeRank],
        minPrice: queryMin,
        maxPrice: queryMax,
        page: 0,
        size: PAGE_SIZE,
      })
      .then((page) => {
        if (!alive) return;
        setItems(page.items);
        setTotal(page.total);
      })
      .catch((error) => {
        if (!alive) return;
        setItems([]);
        setTotal(0);
        // public endpoint: never surface auth/server messages (e.g. "please log in") here
        void error;
        setLoadError(true);
      })
      .finally(() => alive && setFetching(false));
    return () => {
      alive = false;
    };
  }, [selectedCategory, activeRank, queryMin, queryMax]);
  const products = useMemo(
    () =>
      sort === "ราคาต่ำสุด"
        ? [...items].sort((a, b) => priceOf(a) - priceOf(b))
        : items,
    [sort, items],
  );
  const applyPrice = () => {
    const min = minPrice.trim() === "" ? undefined : Number(minPrice);
    const max = maxPrice.trim() === "" ? undefined : Number(maxPrice);
    if ((min !== undefined && Number.isNaN(min)) || (max !== undefined && Number.isNaN(max))) {
      notify("กรุณากรอกราคาเป็นตัวเลข");
      return;
    }
    setAppliedPrice({ min, max });
    notify("ใช้ตัวกรองราคาแล้ว");
  };
  const addToCart = async (p: BestSellerProduct) => {
    const href = `/shop/${p.shopSlug}/product/${p.slug}`;
    if (!cartApi) {
      window.location.assign(href);
      return;
    }
    if (cartApi.access === "guest") {
      notify("กรุณาเข้าสู่ระบบก่อนเพิ่มสินค้าลงตะกร้า");
      window.location.assign(loginPathForCart());
      return;
    }
    if (cartApi.access === "blocked") {
      notify("บัญชีนี้ไม่สามารถใช้ตะกร้าสินค้าได้");
      return;
    }
    if (cartApi.access === "loading") return;
    const skus = p.skus ?? [];
    if (skus.length > 1) {
      notify("กรุณาเลือกตัวเลือกสินค้า");
      window.location.assign(href);
      return;
    }
    const sku = skus[0];
    if (!sku || sku.available <= 0) {
      notify("สินค้าหมด");
      return;
    }
    try {
      const result = await cartApi.add(sku.id, 1);
      notify(
        result?.adjusted && result.appliedQuantity < 1
          ? "สินค้าในสต็อกไม่เพียงพอ"
          : "เพิ่มสินค้าลงตะกร้าแล้ว",
      );
    } catch (error) {
      notify(
        error instanceof ApiError ? error.message : "เพิ่มสินค้าไม่สำเร็จ",
      );
    }
  };
  return (
    <main className="best-seller-page recommend-page">
      <section className="best-hero">
        <img
          src={bestSellerReference}
          alt="สินค้าขายดี สินค้ายอดนิยมจากร้านค้าไทย ที่ลูกค้าเลือกซื้อมากที่สุด"
        />
      </section>
      <section className="recommend-categories best-categories recommend-width" aria-label={tp("home.shopByCategory")}>
        {categoryTiles.map((tile) => {
          const isAll = tile.key === "all";
          const isActive = isAll
            ? selectedCategoryId == null
            : tile.categoryId != null && tile.categoryId === selectedCategoryId;
          return (
            <button
              className={`category${isActive ? " is-active" : ""}`}
              key={tile.key}
              type="button"
              aria-pressed={isActive}
              onClick={() => {
                if (isAll || tile.categoryId == null) {
                  selectCategory(undefined, tile.label);
                  return;
                }
                selectCategory(tile.categoryId, tile.label);
              }}
            >
              {isAll ? (
                <span className="category-more-icon" aria-hidden="true">
                  <LayoutGrid />
                </span>
              ) : (
                <span aria-hidden="true">{tile.emoji}</span>
              )}
              <b>
                {tile.label.split("\n").map((line, j) => (
                  <span key={j}>{line}</span>
                ))}
              </b>
            </button>
          );
        })}
      </section>
      <section className="best-catalog recommend-width">
        <aside className="best-filters">
          <h2>ตัวกรองสินค้า</h2>
          <h3>หมวดหมู่</h3>
          {[{ id: undefined as number | undefined, nameTh: allLabel }, ...(categoryList ?? [])].map((c) => (
            <label key={c.id == null ? "all" : String(c.id)}>
              <input
                type="checkbox"
                checked={c.id == null ? selectedCategoryId == null : selectedCategoryId === c.id}
                onChange={() => setCategoryInUrl(c.id)}
              />
              {c.nameTh}
            </label>
          ))}
          <hr />
          <h3>ช่วงราคา</h3>
          {priceBands.map(([x], i) => (
            <label key={x}>
              <input
                type="radio"
                name="price"
                checked={priceBand === i}
                onChange={() => {
                  setPriceBand(i);
                  setAppliedPrice({});
                  setMinPrice("");
                  setMaxPrice("");
                }}
              />
              {x}
            </label>
          ))}
          <div className="best-price-range">
            <input
              aria-label="ราคาต่ำสุด"
              inputMode="numeric"
              pattern="[0-9]*"
              value={minPrice}
              onChange={(e) => setMinPrice(e.target.value.replace(/\D/g, ""))}
              onKeyDown={(e) => {
                if (e.key === "-" || e.key === "e" || e.key === "E" || e.key === "+" || e.key === ".") e.preventDefault();
              }}
              placeholder="฿ ขั้นต่ำ"
            />
            <span>-</span>
            <input
              aria-label="ราคาสูงสุด"
              inputMode="numeric"
              pattern="[0-9]*"
              value={maxPrice}
              onChange={(e) => setMaxPrice(e.target.value.replace(/\D/g, ""))}
              onKeyDown={(e) => {
                if (e.key === "-" || e.key === "e" || e.key === "E" || e.key === "+" || e.key === ".") e.preventDefault();
              }}
              placeholder="฿ สูงสุด"
            />
            <button type="button" onClick={applyPrice}>ตกลง</button>
          </div>
        </aside>
        <div className="best-results">
          <div className="best-heading">
            <div>
              <BarChart3 />
              <span>
                <h1>สินค้าขายดี</h1>
                <p>อัปเดตยอดขายล่าสุด สินค้าที่ลูกค้าเลือกซื้อมากที่สุด</p>
              </span>
            </div>
            <div>
              <small>
                {total === 0
                  ? "แสดง 0 รายการ"
                  : `แสดง 1 - ${Math.min(products.length, total).toLocaleString()} จาก ${total.toLocaleString()} รายการ`}
              </small>
              <b>เรียงตาม</b>
              <select
                aria-label="เรียงสินค้า"
                value={sort}
                onChange={(e) => setSort(e.target.value)}
              >
                <option>ยอดขายสูงสุด</option>
                <option>ราคาต่ำสุด</option>
                <option>คะแนนรีวิวสูงสุด</option>
              </select>
              <button
                className={grid ? "active" : ""}
                onClick={() => setGrid(true)}
                aria-label="มุมมองตาราง"
              >
                <Grid2X2 />
              </button>
              <button
                className={!grid ? "active" : ""}
                onClick={() => setGrid(false)}
                aria-label="มุมมองรายการ"
              >
                <List />
              </button>
            </div>
          </div>
          <div className="best-rank-tabs">
            {rankTabs.map((x, i) => (
              <button
                className={activeRank === i ? "active" : ""}
                key={x}
                onClick={() => {
                  setActiveRank(i);
                  notify(`เลือก ${x}`);
                }}
              >
                {x}
              </button>
            ))}
          </div>
          {fetching ? (
            <p className="best-meta" role="status">{tp("bestSeller.loading")}</p>
          ) : loadError ? (
            <EmptyState variant="error" title={tp("bestSeller.loadError")} />
          ) : products.length === 0 ? (
            <EmptyState icon={TrendingUp} title={tp("bestSeller.empty")} />
          ) : null}
          <div className={`best-product-grid ${grid ? "" : "list-view"}`}>
            {!fetching && products.map((p, index) => {
              const price = priceOf(p);
              const compare = compareOf(p);
              const discount =
                compare && compare > price
                  ? Math.round(((compare - price) / compare) * 100)
                  : 0;
              const image = primaryImage(p);
              const href = `/shop/${p.shopSlug}/product/${p.slug}`;
              const rank = sort === "ราคาต่ำสุด" ? index + 1 : p.rank;
              return (
              <article className="best-product" key={p.id}>
                <div className="best-photo">
                  <a className="best-sprite" href={href} aria-label={p.nameTh}>
                    {image ? (
                      <img
                        src={mediaUrl(image.url)}
                        alt=""
                        style={{ left: 0, top: 0, width: "100%", height: "100%", objectFit: "cover" }}
                      />
                    ) : null}
                  </a>
                  <strong className={`rank rank-${rank}`}>
                    {rank}
                  </strong>
                  {rank === 1 && (
                    <span className="best-badge">
                      BEST
                      <br />
                      SELLER
                    </span>
                  )}
                  <button
                    type="button"
                    className={favorites.has(p.id) ? "liked" : ""}
                    onClick={() => void toggleFavorite(p.id)}
                    aria-label={tp("catalog.favorite")}
                  >
                    <Heart />
                  </button>
                </div>
                <div className="best-info">
                  <h2>
                    <a href={href} style={{ color: "inherit", textDecoration: "none" }}>
                      <span>{p.nameTh}</span>
                    </a>
                  </h2>
                  <p className="best-shop">
                    <Store /> <a href={`/shop/${p.shopSlug}`} style={{ color: "inherit", textDecoration: "none" }}>{p.shopName}</a>
                  </p>
                  <p className="best-price">
                    <b>฿{price.toLocaleString()}</b>
                    {discount > 0 && compare ? (
                      <>
                        <del>฿{compare.toLocaleString()}</del>
                        <em>-{discount}%</em>
                      </>
                    ) : null}
                  </p>
                  <p className="best-meta">
                    {p.rating != null ? (
                      <span className="pcard-rating">
                        <b>★ {p.rating}</b>
                        <span className="pcard-reviews">({(p.reviewCount ?? 0).toLocaleString()})</span>
                      </span>
                    ) : null}
                    {p.rating != null ? <span className="pcard-sep" aria-hidden="true">·</span> : null}
                    <span className="pcard-sold">ขายแล้ว {p.soldCount.toLocaleString()} ชิ้น</span>
                  </p>
                  <button onClick={() => void addToCart(p)}>
                    <ShoppingCart />
                    เพิ่มลงตะกร้า
                  </button>
                </div>
              </article>
              );
            })}
          </div>
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
