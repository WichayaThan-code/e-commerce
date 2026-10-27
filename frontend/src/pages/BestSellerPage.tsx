import { useEffect, useMemo, useState } from "react";
import {
  Baby,
  BarChart3,
  CircleEllipsis,
  Dumbbell,
  Gift,
  Grid2X2,
  Heart,
  Leaf,
  List,
  Shirt,
  ShoppingBag,
  ShoppingCart,
  Sofa,
  Sparkles,
  Store,
  Utensils,
  Wine,
} from "lucide-react";
import { loginPathForCart, useOptionalCart } from "../contexts/CartContext";
import {
  ApiError,
  bestSellerApi,
  catalogApi,
  mediaUrl,
  type BestSellerProduct,
  type ProductCategory,
} from "../services/api";
import bestSellerReference from "../assets/images/references/storefront/best-seller.png";
import "../styles/recommended-products.css";
import "../styles/best-seller.css";

/** Chip icons (design order); labels come from /api/categories. Chip 0 = "ทั้งหมด". */
const categoryIcons = [
  Sparkles,
  Shirt,
  Wine,
  Sofa,
  ShoppingBag,
  Utensils,
  Dumbbell,
  Baby,
  Gift,
  Leaf,
  CircleEllipsis,
] as const;
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
  const [activeCategory, setActiveCategory] = useState(0);
  const [activeRank, setActiveRank] = useState(0);
  const cartApi = useOptionalCart();
  const [categoryList, setCategoryList] = useState<ProductCategory[]>([]);
  const [priceBand, setPriceBand] = useState(0);
  const [appliedPrice, setAppliedPrice] = useState<{
    min?: number;
    max?: number;
  }>({});
  const [items, setItems] = useState<BestSellerProduct[]>([]);
  const [total, setTotal] = useState(0);
  const [fetching, setFetching] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [notice, setNotice] = useState("");
  const [sort, setSort] = useState("ยอดขายสูงสุด");
  const [grid, setGrid] = useState(true);
  const [favorites, setFavorites] = useState<string[]>([]);
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
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
  const chips = useMemo(
    () => [
      { id: undefined as number | undefined, label: "ทั้งหมด" },
      ...categoryList
        .slice(0, categoryIcons.length - 1)
        .map((c) => ({ id: c.id as number | undefined, label: c.nameTh })),
    ],
    [categoryList],
  );
  const otopTab = activeRank === 3;
  const selectedCategory = otopTab
    ? undefined
    : activeCategory > 0
      ? categoryList[activeCategory - 1]?.id
      : undefined;
  const [bandMin, bandMax] = [priceBands[priceBand][1], priceBands[priceBand][2]];
  const queryMin = appliedPrice.min ?? bandMin;
  const queryMax = appliedPrice.max ?? bandMax;
  useEffect(() => {
    let alive = true;
    setFetching(true);
    setLoadError("");
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
        setLoadError("ยังไม่มีข้อมูลยอดขาย กรุณาลองใหม่อีกครั้งภายหลัง");
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
      <section className="recommend-categories best-categories recommend-width">
        {chips.map(({ label }, i) => {
          const Icon = categoryIcons[i];
          return (
          <button
            className={activeCategory === i ? "active" : ""}
            key={`${i}-${label}`}
            onClick={() => {
              setActiveCategory(i);
              notify(`เลือกหมวด ${label}`);
            }}
          >
            <Icon />
            <span>{label}</span>
          </button>
          );
        })}
      </section>
      <section className="best-catalog recommend-width">
        <aside className="best-filters">
          <h2>ตัวกรองสินค้า</h2>
          <h3>หมวดหมู่</h3>
          {[{ id: undefined as number | undefined, nameTh: "ทั้งหมด" }, ...categoryList].map((c, i) => (
            <label key={`${i}-${c.nameTh}`}>
              <input
                type="checkbox"
                checked={i === 0 ? activeCategory === 0 : categoryList[activeCategory - 1]?.id === c.id}
                onChange={() => setActiveCategory(i)}
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
              value={minPrice}
              onChange={(e) => setMinPrice(e.target.value)}
              placeholder="฿ ขั้นต่ำ"
            />
            <span>-</span>
            <input
              aria-label="ราคาสูงสุด"
              value={maxPrice}
              onChange={(e) => setMaxPrice(e.target.value)}
              placeholder="฿ สูงสุด"
            />
            <button onClick={applyPrice}>ตกลง</button>
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
            <p className="best-meta" role="status">กำลังโหลดสินค้าขายดี...</p>
          ) : products.length === 0 ? (
            <p className="best-meta" role="status">{loadError || "ยังไม่มีข้อมูลยอดขาย"}</p>
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
                    className={favorites.includes(p.id) ? "liked" : ""}
                    onClick={() =>
                      setFavorites((v) =>
                        v.includes(p.id)
                          ? v.filter((x) => x !== p.id)
                          : [...v, p.id],
                      )
                    }
                    aria-label="รายการโปรด"
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
                      <>
                        <b>★ {p.rating}</b> ({(p.reviewCount ?? 0).toLocaleString()}){" "}
                      </>
                    ) : null}
                    <span>ขายแล้ว {p.soldCount.toLocaleString()} ชิ้น</span>
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
