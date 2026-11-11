import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  Gift,
  Grid2X2,
  Heart,
  Leaf,
  List,
  Palette,
  Search,
  ShoppingCart,
  Sparkles,
  Store,
} from "lucide-react";
import EmptyState from "../components/feedback/EmptyState";
import { loginPathForCart, useOptionalCart } from "../contexts/CartContext";
import {
  ApiError,
  bestSellerApi,
  catalogApi,
  mediaUrl,
  recommendationApi,
  type BestSellerProduct,
  type ProductCategory,
  type RecommendationSort,
} from "../services/api";
import thaiBrandReference from "../assets/images/references/storefront/thai-brand.png";
import "../styles/recommended-products.css";
import "../styles/best-seller.css";
import "../styles/thai-brands.css";

/** Hub categories that define “Thai products” for this page. */
const THAI_SCOPES = [
  { key: "all" as const, label: "ทั้งหมด", Icon: Sparkles },
  { key: "otop" as const, label: "สินค้า OTOP", Icon: Store },
  { key: "crafts" as const, label: "สินค้าและงานฝีมือไทย", Icon: Palette },
];

const PRODUCT_TABS = [
  "ทั้งหมด",
  "สินค้าแนะนำ",
  "สินค้าขายดี",
  "สินค้าใหม่",
  "ราคาพิเศษ",
] as const;

const PAGE_SIZE = 48;

function isOtopCategory(c: ProductCategory) {
  const en = (c.nameEn ?? "").toLowerCase();
  return en.includes("otop") || c.nameTh.toUpperCase().includes("OTOP");
}
function isCraftsCategory(c: ProductCategory) {
  const en = (c.nameEn ?? "").toLowerCase();
  return en.includes("thai crafts") || c.nameTh.includes("งานฝีมือไทย");
}

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
    (a, b) => Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder,
  )[0];
const discountOf = (p: BestSellerProduct) => {
  const price = priceOf(p);
  const compare = compareOf(p);
  return compare && compare > price ? Math.round(((compare - price) / compare) * 100) : 0;
};
const badgeFor = (p: BestSellerProduct, scopeKey: (typeof THAI_SCOPES)[number]["key"], otopIds: Set<number>) => {
  if (scopeKey === "otop" || (p.categoryId != null && otopIds.has(p.categoryId)) || (p.brand ?? "").toUpperCase().includes("OTOP")) {
    return { label: "OTOP", tone: "green" as const };
  }
  if (discountOf(p) >= 20) return { label: "ราคาพิเศษ", tone: "orange" as const };
  return { label: "แบรนด์ไทย", tone: "red" as const };
};

function mergeProducts(rows: BestSellerProduct[]) {
  const byId = new Map<string, BestSellerProduct>();
  for (const item of rows) {
    const prev = byId.get(item.id);
    if (!prev || item.soldCount > prev.soldCount) byId.set(item.id, item);
  }
  return [...byId.values()];
}

async function loadThaiProducts(opts: {
  categoryIds: number[];
  mode: "recommend" | "best_selling";
  sort: RecommendationSort;
  period?: "7d" | "30d" | "all";
}): Promise<BestSellerProduct[]> {
  if (opts.categoryIds.length === 0) return [];

  if (opts.mode === "best_selling") {
    const pages = await Promise.all(
      opts.categoryIds.map((categoryId) =>
        bestSellerApi.list({
          categoryId,
          sort: "best_selling",
          period: opts.period ?? "all",
          page: 0,
          size: PAGE_SIZE,
        }),
      ),
    );
    const sold = mergeProducts(pages.flatMap((page) => page.items));
    if (sold.length > 0) return sold.sort((a, b) => b.soldCount - a.soldCount || a.nameTh.localeCompare(b.nameTh));
  }

  const pages = await Promise.all(
    opts.categoryIds.map((categoryId) =>
      recommendationApi.list({
        categoryId,
        sort: opts.sort,
        page: 0,
        size: PAGE_SIZE,
      }),
    ),
  );
  return mergeProducts(pages.flatMap((page) => page.items)).sort((a, b) => {
    if (opts.sort === "price_asc") return priceOf(a) - priceOf(b);
    if (opts.sort === "newest") return a.rank - b.rank;
    return b.soldCount - a.soldCount || a.nameTh.localeCompare(b.nameTh);
  });
}

export default function ThaiBrandsPage() {
  const cartApi = useOptionalCart();
  const [activeScope, setActiveScope] = useState(0);
  const [activeTab, setActiveTab] = useState(0);
  const [notice, setNotice] = useState("");
  const [query, setQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [sort, setSort] = useState("ยอดนิยม");
  const [grid, setGrid] = useState(true);
  const [favorites, setFavorites] = useState<string[]>([]);
  const [items, setItems] = useState<BestSellerProduct[]>([]);
  const [fetching, setFetching] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [categoryList, setCategoryList] = useState<ProductCategory[]>([]);
  const [categoriesReady, setCategoriesReady] = useState(false);
  const loadRequest = useRef(0);
  const productCache = useRef(new Map<string, BestSellerProduct[]>());
  const hasShownItems = useRef(false);

  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 1900);
  };

  useEffect(() => {
    catalogApi
      .categories()
      .then(setCategoryList)
      .catch(() => setCategoryList([]))
      .finally(() => setCategoriesReady(true));
  }, []);

  const otopIds = useMemo(
    () => new Set(categoryList.filter(isOtopCategory).map((c) => c.id)),
    [categoryList],
  );
  const craftsIds = useMemo(
    () => new Set(categoryList.filter(isCraftsCategory).map((c) => c.id)),
    [categoryList],
  );

  const scope = THAI_SCOPES[activeScope];
  const selectedCategoryKey = useMemo(() => {
    const ids =
      scope.key === "otop"
        ? [...otopIds]
        : scope.key === "crafts"
          ? [...craftsIds]
          : [...new Set([...otopIds, ...craftsIds])];
    return ids.slice().sort((a, b) => a - b).join(",");
  }, [scope.key, otopIds, craftsIds]);
  const selectedCategoryIds = useMemo(
    () => (selectedCategoryKey ? selectedCategoryKey.split(",").map(Number) : []),
    [selectedCategoryKey],
  );

  // Tab "ราคาพิเศษ" filters client-side; keep the same server query as "ทั้งหมด".
  const serverTab = activeTab === 4 ? 0 : activeTab;
  const recommendSort: RecommendationSort =
    serverTab === 1
      ? "featured"
      : serverTab === 3 || sort === "สินค้าใหม่"
        ? "newest"
        : sort === "ราคาต่ำสุด"
          ? "price_asc"
          : "best";
  const useBestSelling = serverTab === 2 || (serverTab === 0 && sort === "ยอดนิยม");
  const apiPeriod = serverTab === 3 ? "7d" : "all";

  const queryKey = `${selectedCategoryKey}|${useBestSelling ? "best" : "rec"}|${recommendSort}|${apiPeriod}`;

  useEffect(() => {
    if (!categoriesReady) return;
    const cached = productCache.current.get(queryKey);
    setLoadError(false);
    if (cached) {
      // Instant cache hit — no network, no opacity flash when toggling tabs/scopes.
      setItems(cached);
      hasShownItems.current = cached.length > 0;
      setFetching(false);
      return;
    }

    let alive = true;
    const requestId = ++loadRequest.current;
    if (!hasShownItems.current) setFetching(true);
    // Keep showing the previous list while a cold query loads.
    loadThaiProducts({
      categoryIds: selectedCategoryIds,
      mode: useBestSelling ? "best_selling" : "recommend",
      sort: recommendSort,
      period: apiPeriod,
    })
      .then((rows) => {
        if (!alive || requestId !== loadRequest.current) return;
        productCache.current.set(queryKey, rows);
        setItems(rows);
        hasShownItems.current = rows.length > 0 || hasShownItems.current;
        setLoadError(false);
      })
      .catch(() => {
        if (!alive || requestId !== loadRequest.current) return;
        if (!hasShownItems.current) {
          setItems([]);
          setLoadError(true);
        }
      })
      .finally(() => {
        if (!alive || requestId !== loadRequest.current) return;
        setFetching(false);
      });
    return () => {
      alive = false;
    };
  }, [categoriesReady, queryKey, selectedCategoryIds, useBestSelling, recommendSort, apiPeriod]);

  const popularBrandsLive = useMemo(() => {
    const counts = new Map<string, number>();
    for (const item of items) {
      const brand = item.brand?.trim();
      if (!brand) continue;
      counts.set(brand, (counts.get(brand) ?? 0) + 1);
    }
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], "th"))
      .slice(0, 10)
      .map(([name]) => name);
  }, [items]);
  const stableBrands = useRef<string[]>([]);
  if (popularBrandsLive.length > 0) stableBrands.current = popularBrandsLive;
  const popularBrands = popularBrandsLive.length > 0 ? popularBrandsLive : stableBrands.current;

  const products = useMemo(() => {
    const q = appliedQuery.trim().toLowerCase();
    let rows = items;
    if (q) {
      rows = rows.filter((p) => {
        const hay = `${p.nameTh} ${p.nameEn ?? ""} ${p.brand ?? ""} ${p.shopName}`.toLowerCase();
        return hay.includes(q);
      });
    }
    if (activeTab === 4) rows = rows.filter((p) => discountOf(p) > 0);
    if (sort === "ราคาต่ำสุด") return [...rows].sort((a, b) => priceOf(a) - priceOf(b));
    if (sort === "สินค้าใหม่") return [...rows].sort((a, b) => a.rank - b.rank);
    return rows;
  }, [items, appliedQuery, activeTab, sort]);

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
      notify(error instanceof ApiError ? error.message : "เพิ่มสินค้าไม่สำเร็จ");
    }
  };

  const submitSearch = () => setAppliedQuery(query.trim());

  return (
    <main className="thai-brand-page best-seller-page recommend-page">
      <section className="thai-hero">
        <img
          src={thaiBrandReference}
          alt="แบรนด์ไทย ของดีไทยสู่ทุกบ้าน สนับสนุนผู้ประกอบการไทย"
        />
      </section>
      <p className="thai-breadcrumb recommend-width">
        <Link to="/">หน้าแรก</Link>
        <span>›</span>แบรนด์ไทย
      </p>
      <section className="thai-content recommend-width">
        <aside className="thai-filters">
          <h2>ค้นหาแบรนด์ไทย</h2>
          <form
            className="thai-search"
            onSubmit={(event) => {
              event.preventDefault();
              submitSearch();
            }}
          >
            <Search aria-hidden="true" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="ค้นหาสินค้า / แบรนด์ไทย..."
              aria-label="ค้นหาสินค้าแบรนด์ไทย"
            />
          </form>
          <h3>หมวดหมู่สินค้า</h3>
          {THAI_SCOPES.map(({ key, label, Icon }, i) => (
            <button
              type="button"
              className={activeScope === i ? "active" : ""}
              key={key}
              onClick={() => setActiveScope(i)}
            >
              <Icon aria-hidden="true" />
              {label}
            </button>
          ))}
          <div className="thai-merchant">
            <span aria-hidden="true">🏪</span>
            <h3>
              เป็นส่วนหนึ่ง
              <br />
              ในการสนับสนุนแบรนด์ไทย
            </h3>
            <Link to="/merchant/register">สมัครเปิดร้านค้า →</Link>
          </div>
        </aside>

        <div className="thai-main">
          <div className="thai-section-head">
            <h1>แบรนด์ไทยยอดนิยม</h1>
            <button
              type="button"
              onClick={() => {
                setActiveScope(0);
                setActiveTab(0);
                setAppliedQuery("");
                setQuery("");
              }}
            >
              ◉ ดูทั้งหมด →
            </button>
          </div>

          <div className="thai-brand-strip">
            <img src={thaiBrandReference} alt="" />
            <div>
              {(popularBrands.length > 0 ? popularBrands : Array.from({ length: 10 }, () => "")).map((brand, index) => (
                <button
                  type="button"
                  key={brand || `empty-${index}`}
                  disabled={!brand}
                  onClick={() => {
                    if (!brand) return;
                    setQuery(brand);
                    setAppliedQuery(brand);
                    notify(`กรองแบรนด์ ${brand}`);
                  }}
                  aria-label={brand || undefined}
                />
              ))}
            </div>
          </div>

          <div className="thai-product-head">
            <h2>สินค้าแบรนด์ไทย</h2>
            <div>
              <span>เรียงตาม</span>
              <select
                value={sort}
                onChange={(e) => setSort(e.target.value)}
                aria-label="เรียงสินค้า"
              >
                <option>ยอดนิยม</option>
                <option>ราคาต่ำสุด</option>
                <option>สินค้าใหม่</option>
              </select>
              <button
                type="button"
                className={grid ? "active" : ""}
                onClick={() => setGrid(true)}
                aria-label="มุมมองตาราง"
              >
                <Grid2X2 />
              </button>
              <button
                type="button"
                className={!grid ? "active" : ""}
                onClick={() => setGrid(false)}
                aria-label="มุมมองรายการ"
              >
                <List />
              </button>
            </div>
          </div>

          <div className="thai-tabs">
            {PRODUCT_TABS.map((label, i) => (
              <button
                type="button"
                className={activeTab === i ? "active" : ""}
                key={label}
                onClick={() => setActiveTab(i)}
              >
                {label}
              </button>
            ))}
          </div>

          {fetching && items.length === 0 ? (
            <p className="best-meta" role="status">
              กำลังโหลดสินค้าแบรนด์ไทย...
            </p>
          ) : loadError && products.length === 0 ? (
            <EmptyState variant="error" title="โหลดสินค้าแบรนด์ไทยไม่สำเร็จ" />
          ) : !fetching && products.length === 0 ? (
            <EmptyState
              icon={Leaf}
              title={appliedQuery ? `ไม่พบสินค้าสำหรับ “${appliedQuery}”` : "ยังไม่มีสินค้าในหมวดแบรนด์ไทย"}
            />
          ) : null}

          <div
            className={`thai-product-grid${grid ? "" : " list-view"}`}
            aria-busy={fetching}
          >
            {products.map((p) => {
                const price = priceOf(p);
                const compare = compareOf(p);
                const discount = discountOf(p);
                const image = primaryImage(p);
                const href = `/shop/${p.shopSlug}/product/${p.slug}`;
                const badge = badgeFor(p, scope.key, otopIds);
                return (
                  <article className="thai-product" key={p.id}>
                    <div className="thai-photo">
                      <a className="thai-sprite" href={href} aria-label={p.nameTh}>
                        {image ? (
                          <img className="thai-product-img" src={mediaUrl(image.url)} alt="" />
                        ) : (
                          <span className="thai-photo-fallback" aria-hidden="true">
                            <Gift />
                          </span>
                        )}
                      </a>
                      <span className={`thai-tag ${badge.tone}`}>{badge.label}</span>
                      <button
                        type="button"
                        className={favorites.includes(p.id) ? "liked" : ""}
                        onClick={() =>
                          setFavorites((v) =>
                            v.includes(p.id) ? v.filter((id) => id !== p.id) : [...v, p.id],
                          )
                        }
                        aria-label="รายการโปรด"
                      >
                        <Heart />
                      </button>
                    </div>
                    <div className="thai-info">
                      <h2>
                        <a href={href} style={{ color: "inherit", textDecoration: "none" }}>
                          <span>{p.nameTh}</span>
                        </a>
                      </h2>
                      <p className="thai-shop">
                        <Store aria-hidden="true" />{" "}
                        <a href={`/shop/${p.shopSlug}`} style={{ color: "inherit", textDecoration: "none" }}>
                          {p.shopName}
                        </a>
                      </p>
                      <p className="thai-price">
                        <b>฿{price.toLocaleString()}</b>
                        {discount > 0 && compare ? (
                          <>
                            <del>฿{compare.toLocaleString()}</del>
                            <em>-{discount}%</em>
                          </>
                        ) : null}
                      </p>
                      <p className="thai-meta">
                        {p.rating != null ? (
                          <span>
                            <b>★ {p.rating}</b> ({(p.reviewCount ?? 0).toLocaleString()})
                          </span>
                        ) : (
                          <span />
                        )}
                        <span>ขายแล้ว {p.soldCount.toLocaleString()} ชิ้น</span>
                      </p>
                      <button type="button" onClick={() => void addToCart(p)}>
                        <ShoppingCart />
                        เพิ่มลงตะกร้า
                      </button>
                    </div>
                  </article>
                );
              })}
          </div>

          <div className="thai-banners">
            <button
              type="button"
              onClick={() => {
                setActiveScope(2);
                setActiveTab(0);
              }}
              aria-label="เลือกซื้อสินค้าเกษตรไทย / งานฝีมือ"
            >
              <span className="thai-banner-sprite banner-1">
                <img src={thaiBrandReference} alt="จากภูมิปัญญาไทย สู่สินค้าคุณภาพ" />
              </span>
            </button>
            <button
              type="button"
              onClick={() => {
                setActiveScope(1);
                setActiveTab(0);
              }}
              aria-label="ชมสินค้า OTOP"
            >
              <span className="thai-banner-sprite banner-2">
                <img src={thaiBrandReference} alt="หัตถกรรมไทย สืบสานคุณค่า" />
              </span>
            </button>
            <a className="thai-banner-link" href="/merchant/register" aria-label="ร่วมสนับสนุนแบรนด์ไทย">
              <span className="thai-banner-sprite banner-3">
                <img src={thaiBrandReference} alt="ไทยซื้อไทย ช่วยคนไทย" />
              </span>
            </a>
          </div>
        </div>
      </section>
      {notice ? (
        <div className="recommend-toast" role="status">
          {notice}
        </div>
      ) : null}
    </main>
  );
}
