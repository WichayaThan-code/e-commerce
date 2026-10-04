import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
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
  Search,
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
import recommendReference from "../assets/images/references/storefront/recommend-product.png";
import "../styles/recommended-products.css";

const categoryIcons = [Sparkles, Shirt, Wine, Sofa, ShoppingBag, Utensils, Dumbbell, Baby, Gift, Gift, Leaf, CircleEllipsis];
const ratingStars = ["★★★★", "★★★½", "★★★", "★★"] as const;
type SortKey = "featured" | "price" | "best";

type RecommendProductRow = {
  tagClass: string;
  price: number;
  old: number;
  discount: number;
  rating: string;
  reviews: number;
  sold: string;
  crop: string;
};
type RecommendProductInfo = { tag: string; name: string; shop: string };
type RecommendBanner = { title: string; text: string; btn: string; toast: string };

export default function RecommendedProductsPage() {
  const { tp, localeTag } = usePublicTranslation();
  const [activeCategory, setActiveCategory] = useState(0);
  const [notice, setNotice] = useState("");
  const [sort, setSort] = useState<SortKey>("featured");
  const [grid, setGrid] = useState(true);
  const [brandQuery, setBrandQuery] = useState("");
  const [favorites, setFavorites] = useState<number[]>([]);

  const recommendCategories = tp("recommend.categories", { returnObjects: true }) as unknown as string[];
  const priceRanges = tp("catalog.priceRanges", { returnObjects: true }) as unknown as string[];
  const ratingRanges = tp("catalog.ratingRanges", { returnObjects: true }) as unknown as string[];
  const brandTags = tp("catalog.brandTags", { returnObjects: true }) as unknown as string[];
  const banners = tp("recommend.banners", { returnObjects: true }) as unknown as RecommendBanner[];

  const products = useMemo(() => {
    const productRows = tp("recommend.productRows", { returnObjects: true }) as unknown as RecommendProductRow[];
    const productInfos = tp("recommend.products", { returnObjects: true }) as unknown as RecommendProductInfo[];
    return productRows.map((row, index) => {
      const info = productInfos[index];
      return { ...row, tag: info.tag, name: info.name, shop: info.shop };
    });
  }, [tp]);

  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 1900);
  };

  const visibleProducts = useMemo(
    () => (sort === "price" ? [...products].sort((a, b) => a.price - b.price) : products),
    [products, sort],
  );

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
        {recommendCategories.map((label, index) => {
          const Icon = categoryIcons[index % categoryIcons.length];
          return (
            <button
              className={activeCategory === index ? "active" : ""}
              key={`${label}-${index}`}
              type="button"
              onClick={() => {
                setActiveCategory(index);
                notify(tp("catalog.pickCategory", { name: label }));
              }}
            >
              <Icon />
              <span>{label}</span>
            </button>
          );
        })}
      </section>

      <section className="recommend-catalog recommend-width">
        <aside className="recommend-filters">
          <h2>{tp("catalog.filters")}</h2>
          <hr />
          <h3>{tp("catalog.price")}</h3>
          {priceRanges.map((x) => (
            <label key={x}>
              <input type="checkbox" />
              {x}
            </label>
          ))}
          <hr />
          <h3>{tp("catalog.rating")}</h3>
          {ratingRanges.map((text, index) => (
            <label className="rating-filter" key={text}>
              <input type="checkbox" />
              <b>{ratingStars[index]}</b>
              {text}
            </label>
          ))}
          <hr />
          <h3>{tp("catalog.brands")}</h3>
          <div className="brand-search">
            <Search />
            <input
              value={brandQuery}
              onChange={(e) => setBrandQuery(e.target.value)}
              placeholder={tp("catalog.brandSearch")}
            />
          </div>
          {brandTags
            .filter((x) => x.toLowerCase().includes(brandQuery.trim().toLowerCase()))
            .map((x) => (
              <label key={x}>
                <input type="checkbox" />
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
            <button
              className={grid ? "active" : ""}
              type="button"
              onClick={() => setGrid(true)}
              aria-label={tp("common.gridView")}
            >
              <Grid2X2 />
            </button>
            <button
              className={!grid ? "active" : ""}
              type="button"
              onClick={() => setGrid(false)}
              aria-label={tp("common.listView")}
            >
              <List />
            </button>
          </div>
          <div className={`recommend-product-grid ${grid ? "" : "list-view"}`}>
            {visibleProducts.map((p, index) => (
              <article className="recommend-product" key={`${p.name}-${index}`}>
                <div className="recommend-photo">
                  <div className={`recommend-sprite ${p.crop}`}>
                    <img src={recommendReference} alt="" />
                  </div>
                  <span className={`recommend-tag ${p.tagClass}`}>{p.tag}</span>
                  <button
                    type="button"
                    className={favorites.includes(index) ? "liked" : ""}
                    onClick={() =>
                      setFavorites((v) => (v.includes(index) ? v.filter((x) => x !== index) : [...v, index]))
                    }
                    aria-label={tp("catalog.favorite")}
                  >
                    <Heart />
                  </button>
                </div>
                <div className="recommend-product-info">
                  <h2>
                    {p.name.split("\n").map((line, i) => (
                      <span key={i}>{line}</span>
                    ))}
                  </h2>
                  <p className="recommend-shop">
                    <Store /> {p.shop}
                  </p>
                  <p className="recommend-price">
                    <b>฿{p.price.toLocaleString(localeTag)}</b>
                    <del>฿{p.old.toLocaleString(localeTag)}</del>
                    <em>-{p.discount}%</em>
                  </p>
                  <p className="recommend-meta">
                    <b>★ {p.rating}</b> ({p.reviews.toLocaleString(localeTag)}){" "}
                    <span>
                      {tp("common.sold")} {p.sold}
                    </span>
                  </p>
                  <button type="button" onClick={() => notify(tp("home.addedCart"))}>
                    <ShoppingCart />
                    {tp("recommend.addToCart")}
                  </button>
                </div>
              </article>
            ))}
          </div>
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
          <button className="view-all" type="button" onClick={() => notify(tp("recommend.loadMore"))}>
            {tp("recommend.viewAllRecommended")} <ArrowRight />
          </button>
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
