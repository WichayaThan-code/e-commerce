import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  ChevronLeft,
  ChevronRight,
  CircleEllipsis,
  Grid2X2,
  LayoutGrid,
  List,
  MapPin,
  Search,
  SearchX,
  Store,
} from "lucide-react";
import EmptyState from "../components/feedback/EmptyState";
import ShopLogo from "../components/ShopLogo";
import { categoryEmoji } from "../components/shop/categoryIcons";
import { categoryLabelLines } from "../components/marketplace/categoryTiles";
import { shopPageWindow } from "../components/shop/sections/types";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import recommendReference from "../assets/images/references/storefront/recommend-product.png";
import type { Locale } from "../i18n";
import { ApiError, catalogApi, shopApi, type ProductCategory, type PublicShop } from "../services/api";
import { labelForCategory } from "../services/categories";
import "../styles/recommended-products.css";
import "../styles/shop-page.css";

/** Desktop shop grid is 5 columns → 4 rows = 20 shops per page (same density as recommended products). */
const PAGE_SIZE = 20;
type ListState = "loading" | "empty" | "error" | "ok";
type SortKey = "latest" | "name";
type CategoryTab = {
  key: string;
  label: string;
  emoji: string;
  /** "all" | "other" use Lucide frame; categories use emoji. */
  kind: "all" | "category" | "other";
  /** Thai name used to match `shop.category`; null for all/other. */
  nameTh: string | null;
};

function localeOf(localeTag: string): Locale {
  if (localeTag.startsWith("zh")) return "zh";
  if (localeTag.startsWith("en")) return "en";
  return "th";
}

function shopCategoryName(shop: PublicShop) {
  return (shop.category ?? "").trim();
}

function matchesKnownCategory(shop: PublicShop, rows: ProductCategory[]) {
  const name = shopCategoryName(shop);
  if (!name) return false;
  return rows.some((row) => row.nameTh.trim() === name);
}

export default function ShopsPage() {
  const { tp, localeTag } = usePublicTranslation();
  const locale = localeOf(localeTag);
  const [shops, setShops] = useState<PublicShop[]>([]);
  const [listState, setListState] = useState<ListState>("loading");
  const [categoryRows, setCategoryRows] = useState<ProductCategory[] | null>(null);
  const [activeKey, setActiveKey] = useState("all");
  const [provinces, setProvinces] = useState<string[]>([]);
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortKey>("latest");
  const [grid, setGrid] = useState(true);
  const [page, setPage] = useState(1);
  const resultsRef = useRef<HTMLDivElement>(null);

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

  useEffect(() => {
    let active = true;
    catalogApi
      .categories()
      .then((rows) => {
        if (!active) return;
        setCategoryRows(rows.length ? rows : []);
      })
      .catch(() => {
        if (!active) return;
        setCategoryRows([]);
      });
    return () => {
      active = false;
    };
  }, []);

  const apiCategories = categoryRows && categoryRows.length > 0 ? categoryRows : null;

  const fallbackNames = useMemo(() => {
    const names = new Set<string>();
    for (const shop of shops) {
      const name = shopCategoryName(shop);
      if (name) names.add(name);
    }
    return [...names];
  }, [shops]);

  const hasOtherShops = useMemo(() => {
    if (!apiCategories) return false;
    return shops.some((shop) => !matchesKnownCategory(shop, apiCategories));
  }, [apiCategories, shops]);

  const categoryTabs = useMemo<CategoryTab[]>(() => {
    const tabs: CategoryTab[] = [
      { key: "all", label: tp("common.all"), emoji: "", kind: "all", nameTh: null },
    ];
    if (apiCategories) {
      for (const row of apiCategories) {
        tabs.push({
          key: `cat-${row.id}`,
          label: categoryLabelLines(labelForCategory(row, locale)),
          emoji: categoryEmoji(row),
          kind: "category",
          nameTh: row.nameTh.trim(),
        });
      }
      if (hasOtherShops) {
        tabs.push({
          key: "other",
          label: tp("shops.otherCategory"),
          emoji: "",
          kind: "other",
          nameTh: null,
        });
      }
      return tabs;
    }

    for (const name of fallbackNames) {
      tabs.push({
        key: `fallback-${name}`,
        label: categoryLabelLines(name),
        emoji: categoryEmoji({ nameTh: name, nameEn: name }),
        kind: "category",
        nameTh: name,
      });
    }
    return tabs;
  }, [apiCategories, fallbackNames, hasOtherShops, locale, tp]);

  useEffect(() => {
    if (!categoryTabs.some((tab) => tab.key === activeKey)) {
      setActiveKey("all");
    }
  }, [activeKey, categoryTabs]);

  const activeTab = categoryTabs.find((tab) => tab.key === activeKey) ?? categoryTabs[0];

  const provinceOptions = useMemo(
    () => [...new Set(shops.map((shop) => shop.province).filter(Boolean))].sort((a, b) => a.localeCompare(b, localeTag)),
    [localeTag, shops],
  );

  const visibleShops = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    const rows = shops.filter((shop) => {
      const name = shopCategoryName(shop);
      let categoryOk = true;
      if (activeKey === "all") {
        categoryOk = true;
      } else if (activeKey === "other" && apiCategories) {
        categoryOk = !matchesKnownCategory(shop, apiCategories);
      } else if (activeTab?.nameTh) {
        categoryOk = name === activeTab.nameTh;
      }
      const provinceOk = provinces.length === 0 || provinces.includes(shop.province);
      const text = `${shop.name} ${shop.nameEn || ""} ${shop.description || ""}`.toLowerCase();
      return categoryOk && provinceOk && (!keyword || text.includes(keyword));
    });
    if (sort === "name") return [...rows].sort((a, b) => a.name.localeCompare(b.name, "th"));
    return rows;
  }, [activeKey, activeTab, apiCategories, provinces, query, shops, sort]);

  const pageCount = Math.max(1, Math.ceil(visibleShops.length / PAGE_SIZE));
  const safePage = Math.min(page, pageCount);
  const pageShops = visibleShops.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE);

  useEffect(() => {
    setPage(1);
  }, [activeKey, provinces, query, sort]);

  useEffect(() => {
    if (page > pageCount) setPage(pageCount);
  }, [page, pageCount]);

  const goPage = (next: number) => {
    if (next === page || next < 1 || next > pageCount) return;
    setPage(next);
    resultsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const toggleProvince = (province: string) => {
    setProvinces((current) =>
      current.includes(province) ? current.filter((item) => item !== province) : [...current, province],
    );
  };

  const hasSearchOrProvince = query.trim().length > 0 || provinces.length > 0;
  const categoryEmpty =
    listState === "ok" &&
    shops.length > 0 &&
    activeKey !== "all" &&
    !hasSearchOrProvince &&
    visibleShops.length === 0;

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

      <section className="recommend-categories recommend-width" aria-label={tp("home.shopByCategory")}>
        {categoryTabs.map((tab) => {
          const active = activeKey === tab.key;
          return (
            <button
              className={`category${active ? " is-active" : ""}`}
              key={tab.key}
              type="button"
              aria-pressed={active}
              onClick={() => setActiveKey(tab.key)}
            >
              {tab.kind === "all" ? (
                <span className="category-more-icon" aria-hidden="true">
                  <LayoutGrid />
                </span>
              ) : tab.kind === "other" ? (
                <span className="category-more-icon" aria-hidden="true">
                  <CircleEllipsis />
                </span>
              ) : (
                <span aria-hidden="true">{tab.emoji}</span>
              )}
              <b>
                {tab.label.split("\n").map((line, j) => (
                  <span key={j}>{line}</span>
                ))}
              </b>
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

        <div className="recommend-results" ref={resultsRef}>
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
                    <h2 title={shop.name}>{shop.name}</h2>
                    <p className="shop-card-place">
                      <MapPin aria-hidden="true" />
                      <span>{shop.province?.trim() || "—"}</span>
                    </p>
                    <p className="shop-card-text">{shop.description?.trim() || tp("common.defaultShopBlurb")}</p>
                  </div>
                </Link>
              ))}
            </div>
          ) : listState === "loading" ? (
            <article className="shop-list-empty" aria-busy="true">{tp("shops.loading")}</article>
          ) : listState === "error" ? (
            <EmptyState variant="error" title={tp("shops.loadError")} />
          ) : listState === "empty" || (listState === "ok" && shops.length === 0) ? (
            <EmptyState icon={Store} title={tp("shops.empty")} />
          ) : categoryEmpty ? (
            <EmptyState
              icon={Store}
              title={tp("shops.categoryEmpty", { category: activeTab?.label ?? "" })}
              action={{ label: tp("shops.viewAllCategories"), onClick: () => setActiveKey("all") }}
            />
          ) : (
            <EmptyState variant="filtered" icon={SearchX} title={tp("shops.filterEmpty")} />
          )}

          {visibleShops.length > PAGE_SIZE && (
            <nav className="recommend-pagination" aria-label={tp("shops.pageAria")}>
              <button type="button" disabled={safePage <= 1} onClick={() => goPage(safePage - 1)} aria-label={tp("shops.pagePrev")}>
                <ChevronLeft />
              </button>
              {shopPageWindow(safePage, pageCount).map((entry, index) =>
                entry === "gap" ? (
                  <span className="recommend-pagination-gap" key={`gap-${index}`} aria-hidden="true">
                    …
                  </span>
                ) : (
                  <button
                    type="button"
                    className={safePage === entry ? "current" : ""}
                    key={entry}
                    aria-current={safePage === entry ? "page" : undefined}
                    onClick={() => goPage(entry)}
                  >
                    {entry}
                  </button>
                ),
              )}
              <button type="button" disabled={safePage >= pageCount} onClick={() => goPage(safePage + 1)} aria-label={tp("shops.pageNext")}>
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
