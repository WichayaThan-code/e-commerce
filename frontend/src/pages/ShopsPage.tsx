import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  CircleEllipsis,
  Grid2X2,
  Leaf,
  List,
  MapPin,
  Search,
  Shirt,
  Sofa,
  Sparkles,
  Store,
  Utensils,
} from "lucide-react";
import ShopLogo from "../components/ShopLogo";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import recommendReference from "../assets/images/references/storefront/recommend-product.png";
import { ApiError, shopApi, type PublicShop } from "../services/api";
import "../styles/recommended-products.css";
import "../styles/shop-page.css";

const categoryIcons = [Sparkles, Store, Utensils, Shirt, Sofa, Leaf, CircleEllipsis];
const PAGE_SIZE = 30;
type ListState = "loading" | "empty" | "error" | "ok";
type SortKey = "latest" | "name";

export default function ShopsPage() {
  const { tp, localeTag } = usePublicTranslation();
  const [shops, setShops] = useState<PublicShop[]>([]);
  const [listState, setListState] = useState<ListState>("loading");
  const [activeCategory, setActiveCategory] = useState(0);
  const [provinces, setProvinces] = useState<string[]>([]);
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortKey>("latest");
  const [grid, setGrid] = useState(true);
  const [page, setPage] = useState(1);

  const allLabel = tp("common.all");

  useEffect(() => {
    let active = true;
    setListState("loading");
    shopApi
      .list()
      .then((data) => {
        if (!active) return;
        const open = data.filter((shop) => shop.status === "APPROVED");
        setShops(open);
        setListState(open.length ? "ok" : "empty");
      })
      .catch((error) => {
        if (!active) return;
        setShops([]);
        setListState("error");
        if (error instanceof ApiError) console.error(error.message);
      });
    return () => {
      active = false;
    };
  }, []);

  const categories = useMemo(
    () => [allLabel, ...new Set(shops.map((shop) => shop.category).filter(Boolean))],
    [allLabel, shops],
  );
  const provinceOptions = useMemo(
    () => [...new Set(shops.map((shop) => shop.province).filter(Boolean))].sort((a, b) => a.localeCompare(b, localeTag)),
    [localeTag, shops],
  );
  const visibleShops = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    const selectedCategory = categories[activeCategory];
    const rows = shops.filter((shop) => {
      const categoryOk = activeCategory === 0 || shop.category === selectedCategory;
      const provinceOk = provinces.length === 0 || provinces.includes(shop.province);
      const text = `${shop.name} ${shop.nameEn || ""} ${shop.description || ""}`.toLowerCase();
      return categoryOk && provinceOk && (!keyword || text.includes(keyword));
    });
    if (sort === "name") return [...rows].sort((a, b) => a.name.localeCompare(b.name, "th"));
    return rows;
  }, [activeCategory, categories, provinces, query, shops, sort]);

  const pageCount = Math.max(1, Math.ceil(visibleShops.length / PAGE_SIZE));
  const safePage = Math.min(page, pageCount);
  const pageShops = visibleShops.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE);

  useEffect(() => {
    setPage(1);
  }, [activeCategory, provinces, query, sort]);

  useEffect(() => {
    if (page > pageCount) setPage(pageCount);
  }, [page, pageCount]);

  const listMessage =
    listState === "loading"
      ? tp("shops.loading")
      : listState === "empty"
        ? tp("shops.empty")
        : listState === "error"
          ? tp("shops.loadError")
          : "";

  const toggleProvince = (province: string) => {
    setProvinces((current) =>
      current.includes(province) ? current.filter((item) => item !== province) : [...current, province],
    );
  };

  return (
    <div className="recommend-page shop-directory">
      <section className="recommend-intro recommend-width">
        <div>
          <p className="recommend-breadcrumb">
            <Link to="/">{tp("common.home")}</Link>
            <span>›</span>
            {tp("shops.breadcrumb")}
          </p>
          <h1>{tp("shops.title")}</h1>
          <p>{tp("shops.subtitle")}</p>
        </div>
        <div className="recommend-hero">
          <img src={recommendReference} alt={tp("shops.heroAlt")} />
        </div>
      </section>

      <section className="recommend-categories recommend-width">
        {categories.map((label, index) => {
          const Icon = categoryIcons[index % categoryIcons.length];
          return (
            <button
              className={activeCategory === index ? "active" : ""}
              key={label}
              type="button"
              onClick={() => setActiveCategory(index)}
            >
              <Icon />
              <span>{label}</span>
            </button>
          );
        })}
      </section>

      <section className="recommend-catalog recommend-width">
        <aside className="recommend-filters">
          <h2>{tp("shops.filtersTitle")}</h2>
          <hr />
          <h3>{tp("shops.searchTitle")}</h3>
          <div className="brand-search">
            <Search />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={tp("shops.searchPlaceholder")}
              aria-label={tp("shops.searchAria")}
            />
          </div>
          <hr />
          <h3>{tp("shops.provinces")}</h3>
          {provinceOptions.length ? (
            provinceOptions.map((province) => (
              <label key={province}>
                <input
                  type="checkbox"
                  checked={provinces.includes(province)}
                  onChange={() => toggleProvince(province)}
                />
                {province}
              </label>
            ))
          ) : (
            <label>{tp("shops.noProvinces")}</label>
          )}
        </aside>

        <div className="recommend-results">
          <div className="recommend-tools">
            <span>{tp("common.sortBy")}</span>
            <select value={sort} onChange={(event) => setSort(event.target.value as SortKey)} aria-label={tp("shops.sortAria")}>
              <option value="latest">{tp("shops.sortLatest")}</option>
              <option value="name">{tp("shops.sortName")}</option>
            </select>
            <button className={grid ? "active" : ""} type="button" onClick={() => setGrid(true)} aria-label={tp("common.gridView")}>
              <Grid2X2 />
            </button>
            <button className={!grid ? "active" : ""} type="button" onClick={() => setGrid(false)} aria-label={tp("common.listView")}>
              <List />
            </button>
          </div>

          {pageShops.length ? (
            <div className={`shop-card-grid ${grid ? "" : "is-list"}`}>
              {pageShops.map((shop) => (
                <Link className="shop-card" to={`/shop/${shop.slug}`} key={shop.slug}>
                  <div className="shop-card-photo">
                    <ShopLogo src={shop.logoUrl} name={shop.name} />
                  </div>
                  <div className="shop-card-body">
                    <span className="shop-chip">{shop.category}</span>
                    <h2>{shop.name}</h2>
                    {shop.nameEn && <p className="shop-card-en">{shop.nameEn}</p>}
                    <p className="shop-card-place">
                      <MapPin /> {[shop.district, shop.province].filter(Boolean).join(" ")}
                    </p>
                    <p className="shop-card-text">{shop.description || tp("common.defaultShopBlurb")}</p>
                    <span className="shop-enter">
                      {tp("shops.enterShop")} <ArrowRight />
                    </span>
                  </div>
                </Link>
              ))}
            </div>
          ) : listState === "empty" || (listState === "ok" && shops.length === 0) ? (
            <article className="shop-list-empty shop-list-none">
              <Store aria-hidden="true" />
              <p>{tp("shops.empty")}</p>
            </article>
          ) : (
            <article className="shop-list-empty">{listState === "ok" ? tp("shops.filterEmpty") : listMessage}</article>
          )}

          {visibleShops.length > PAGE_SIZE && (
            <nav className="shop-pagination" aria-label={tp("shops.pageAria")}>
              <button type="button" disabled={safePage <= 1} onClick={() => setPage((value) => Math.max(1, value - 1))} aria-label={tp("shops.pagePrev")}>
                <ChevronLeft />
              </button>
              {Array.from({ length: pageCount }, (_, index) => index + 1).map((number) => (
                <button type="button" className={safePage === number ? "current" : ""} key={number} onClick={() => setPage(number)}>
                  {number}
                </button>
              ))}
              <button type="button" disabled={safePage >= pageCount} onClick={() => setPage((value) => Math.min(pageCount, value + 1))} aria-label={tp("shops.pageNext")}>
                <ChevronRight />
              </button>
            </nav>
          )}

          <div className="recommend-banners">
            <article>
              <div>
                <h2>
                  {tp("shops.bannerOpenTitle")
                    .split("\n")
                    .map((line, index) => (
                      <span key={index}>
                        {line}
                        {index === 0 && <br />}
                      </span>
                    ))}
                </h2>
                <p>{tp("shops.bannerOpenText")}</p>
                <Link to="/merchant/register">{tp("shops.bannerOpenCta")}</Link>
              </div>
              <span>🏪</span>
            </article>
            <article>
              <div>
                <h2>
                  {tp("shops.bannerProductsTitle")
                    .split("\n")
                    .map((line, index) => (
                      <span key={index}>
                        {line}
                        {index === 0 && <br />}
                      </span>
                    ))}
                </h2>
                <p>{tp("shops.bannerProductsText")}</p>
                <Link to="/recommendations">{tp("shops.bannerProductsCta")}</Link>
              </div>
              <span>🎁</span>
            </article>
          </div>
        </div>
      </section>
    </div>
  );
}
