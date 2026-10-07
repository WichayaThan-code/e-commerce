import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ChevronLeft, ChevronRight, Grid2X2, Heart, List, MapPin, Search, ShoppingCart, Store } from "lucide-react";
import ShopLogo from "../components/ShopLogo";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import recommendReference from "../assets/images/references/storefront/recommend-product.png";
import { localizedText } from "../services/catalogText";
import { mediaUrl, shopApi, type ProductSort, type PublicProduct, type PublicShop } from "../services/api";
import { SHOP_PRICE_BAND_IDS, shopPriceBandsQuery, type ShopPriceBandId } from "../services/shopPriceBands";
import "../styles/recommended-products.css";
import "../styles/shop-page.css";

type LoadState = "loading" | "error" | "ok";
type SortKey = "featured" | "price" | "best";

const PAGE_SIZE = 30;
const ratingStars = ["★★★★", "★★★½", "★★★", "★★"] as const;

function money(value: number, locale: string) {
  const tag = locale.startsWith("en") ? "en-US" : locale.startsWith("zh") ? "zh-CN" : "th-TH";
  const fraction = Math.round(value * 100) % 100 !== 0;
  return `฿${new Intl.NumberFormat(tag, { minimumFractionDigits: fraction ? 2 : 0, maximumFractionDigits: 2 }).format(value)}`;
}

function amount(value: number | string | null | undefined) {
  if (value == null || value === "") return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function apiSort(sort: SortKey): ProductSort {
  return sort === "price" ? "price_asc" : "newest";
}

export default function ShopPage() {
  const { slug = "" } = useParams();
  const { tp, localeTag } = usePublicTranslation();
  const [shop, setShop] = useState<PublicShop | null>(null);
  const [products, setProducts] = useState<PublicProduct[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<SortKey>("featured");
  const [grid, setGrid] = useState(true);
  const [brandQuery, setBrandQuery] = useState("");
  const [favorites, setFavorites] = useState<string[]>([]);
  const [priceBands, setPriceBands] = useState<ShopPriceBandId[]>([]);
  const [notice, setNotice] = useState("");
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [productState, setProductState] = useState<LoadState>("loading");

  const priceRanges = tp("catalog.priceRanges", { returnObjects: true }) as unknown as string[];
  const ratingRanges = tp("catalog.ratingRanges", { returnObjects: true }) as unknown as string[];
  const brandTags = tp("catalog.brandTags", { returnObjects: true }) as unknown as string[];
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 1900);
  };

  const productQuery = () => ({
    size: PAGE_SIZE,
    page: page - 1,
    sort: apiSort(sort),
    priceBands: shopPriceBandsQuery(priceBands),
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

  useEffect(() => {
    let active = true;
    setShop(null);
    setProducts([]);
    setTotal(0);
    setPage(1);
    setSort("featured");
    setPriceBands([]);
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
  }, [slug]);

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
  }, [shop, slug, page, sort, priceBands]);

  const address = shop
    ? [shop.address, shop.subdistrict, shop.district, shop.province, shop.postalCode].filter(Boolean).join(" ")
    : "";

  const statusLabel =
    shop?.status === "APPROVED"
      ? tp("shop.statusApproved")
      : shop?.status === "REJECTED"
        ? tp("shop.statusRejected")
        : shop?.status === "SUSPENDED"
          ? tp("shop.statusSuspended")
          : tp("shop.statusPending");

  const message = loadState === "loading" ? tp("shop.loading") : loadState === "error" ? tp("shop.loadError") : "";

  return (
    <div className="recommend-page shop-directory">
      <section className="recommend-intro recommend-width">
        <div>
          <p className="recommend-breadcrumb">
            <Link to="/">{tp("common.home")}</Link>
            <span>›</span>
            <Link to="/shops">{tp("shop.breadcrumb")}</Link>
            <span>›</span>
            {shop?.name || tp("common.shopPlaceholder")}
          </p>
          {!shop && (
            <>
              <h1>{message}</h1>
              <p>{tp("shop.defaultDescription")}</p>
            </>
          )}
        </div>
      </section>

      {shop ? (
        <>
          <section className="shop-hero recommend-width">
            <img className="shop-hero-photo" src={shop.coverUrl ? mediaUrl(shop.coverUrl) : recommendReference} alt="" />
            <div className="shop-hero-shade" />
            <div className="shop-hero-panel">
              <div className="shop-profile-mark">
                <ShopLogo src={shop.logoUrl} name={shop.name} />
              </div>
              <div className="shop-hero-copy">
                <span className="shop-chip">{shop.category}</span>
                <h1>{shop.name}</h1>
                {shop.nameEn && <p className="shop-profile-en">{shop.nameEn}</p>}
                <p>{shop.description || tp("shop.noDescription")}</p>
              </div>
            </div>
          </section>
          <ul className="shop-facts recommend-width">
            <li>
              <MapPin />
              <span>{address || tp("common.noAddress")}</span>
            </li>
            <li>
              <b>{tp("common.area")}</b>
              <span>{[shop.district, shop.province].filter(Boolean).join(" ") || "—"}</span>
            </li>
            <li>
              <b>{tp("common.status")}</b>
              <span>{statusLabel}</span>
            </li>
          </ul>

          <section className="recommend-catalog recommend-width shop-catalog">
            <aside className="recommend-filters">
              <h2>{tp("catalog.filters")}</h2>
              <hr />
              <h3>{tp("catalog.price")}</h3>
              {priceRanges.map((label, index) => {
                const band = SHOP_PRICE_BAND_IDS[index];
                if (!band) return null;
                return (
                  <label key={band}>
                    <input
                      type="checkbox"
                      checked={priceBands.includes(band)}
                      onChange={() => togglePriceBand(band)}
                    />
                    {label}
                  </label>
                );
              })}
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
                  onChange={(event) => setBrandQuery(event.target.value)}
                  placeholder={tp("catalog.brandSearch")}
                />
              </div>
              {brandTags
                .filter((label) => label.toLowerCase().includes(brandQuery.trim().toLowerCase()))
                .map((label) => (
                  <label key={label}>
                    <input type="checkbox" />
                    {label}
                  </label>
                ))}
            </aside>
            <div className="recommend-results">
              <div className="recommend-tools">
                <span>{tp("common.sortBy")}</span>
                <select
                  value={sort}
                  onChange={(event) => {
                    setSort(event.target.value as SortKey);
                    setPage(1);
                  }}
                  aria-label={tp("catalog.sortAria")}
                >
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
              {productState === "loading" && (
                <div className="recommend-product-grid" aria-label={tp("shop.productsLoading")}>
                  {Array.from({ length: 5 }, (_, index) => <article key={index} className="shop-product-skeleton" />)}
                </div>
              )}
              {productState === "error" && (
                <article className="shop-list-empty">
                  <p>{tp("shop.productsError")}</p>
                  <button type="button" onClick={loadProducts}>{tp("shop.productsRetry")}</button>
                </article>
              )}
              {productState === "ok" && products.length === 0 && <article className="shop-list-empty">{tp("shop.productsEmpty")}</article>}
              {productState === "ok" && products.length > 0 && (
                <div className={`recommend-product-grid ${grid ? "" : "list-view"}`}>
                  {products.map((product) => {
                    const image = product.images.find((item) => item.primary) || product.images[0];
                    const name = localizedText(localeTag, product.nameTh, product.nameEn, product.nameZh);
                    const price = amount(product.basePrice ?? product.skus[0]?.price);
                    const compare = amount(product.compareAtPrice ?? product.skus[0]?.compareAtPrice);
                    const discount = price != null && compare != null && compare > price ? Math.round((1 - price / compare) * 100) : null;
                    const href = `/shop/${shop.slug}/product/${product.slug}`;
                    return (
                      <article className="recommend-product" key={product.id}>
                        <div className="recommend-photo">
                          <Link to={href} aria-label={name}>
                            {image ? <img src={mediaUrl(image.url)} alt="" /> : null}
                          </Link>
                          <button
                            type="button"
                            className={favorites.includes(product.id) ? "liked" : ""}
                            onClick={() => setFavorites((current) => (
                              current.includes(product.id) ? current.filter((id) => id !== product.id) : [...current, product.id]
                            ))}
                            aria-label={tp("catalog.favorite")}
                          >
                            <Heart />
                          </button>
                        </div>
                        <div className="recommend-product-info">
                          <h2>
                            <Link to={href}><span>{name}</span></Link>
                          </h2>
                          <p className="recommend-shop">
                            <Store /> {shop.name}
                          </p>
                          <p className="recommend-price">
                            <b>{price == null ? "—" : money(price, localeTag)}</b>
                            {discount != null && compare != null && <del>{money(compare, localeTag)}</del>}
                            {discount != null && <em>-{discount}%</em>}
                          </p>
                          <button type="button" onClick={() => notify(tp("shop.view.soon"))}>
                            <ShoppingCart />
                            {tp("recommend.addToCart")}
                          </button>
                        </div>
                      </article>
                    );
                  })}
                </div>
              )}
              {productState === "ok" && total > PAGE_SIZE && (
                <nav className="shop-pagination" aria-label={tp("shop.productsPageAria")}>
                  <button type="button" disabled={page <= 1} onClick={() => setPage((value) => Math.max(1, value - 1))} aria-label={tp("shops.pagePrev")}>
                    <ChevronLeft />
                  </button>
                  {Array.from({ length: pageCount }, (_, index) => index + 1).map((number) => (
                    <button type="button" className={page === number ? "current" : ""} key={number} onClick={() => setPage(number)}>
                      {number}
                    </button>
                  ))}
                  <button type="button" disabled={page >= pageCount} onClick={() => setPage((value) => Math.min(pageCount, value + 1))} aria-label={tp("shops.pageNext")}>
                    <ChevronRight />
                  </button>
                </nav>
              )}
            </div>
          </section>
        </>
      ) : (
        <section className="recommend-width">
          <article className="shop-list-empty">{message}</article>
        </section>
      )}
      {notice && (
        <div className="recommend-toast" role="status">
          {notice}
        </div>
      )}
    </div>
  );
}
