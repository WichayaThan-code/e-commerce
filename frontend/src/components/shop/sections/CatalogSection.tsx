import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { Check, ChevronDown, ChevronLeft, ChevronRight, Grid2X2, List, Search, SlidersHorizontal, Star, X } from "lucide-react";
import { LoadError, Skeleton, SkeletonBusy, SkeletonCard } from "../../feedback/Skeleton";
import { useShopCategories } from "../../../hooks/useShopCategories";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { localizedText } from "../../../services/catalogText";
import { SHOP_PRICE_BAND_IDS } from "../../../services/shopPriceBands";
import type { PublicShopCategory } from "../../../services/api";
import { isFilterOn, isFilterShown } from "./filterAvailability";
import ProductGrid, { productGridClass } from "./ProductGrid";
import { resolveVariant, sectionRootClass } from "./sectionClasses";
import { parseShopSortKey, type ShopSectionProps } from "./types";

const GRID_COLS = 8;
const BRAND_PREVIEW = 5;
/** Star fill levels for rating filters (4.5 / 4 / 3.5 / 3). */
const RATING_LEVELS = [4.5, 4, 3.5, 3] as const;

type Tp = (key: string, opts?: Record<string, unknown>) => string;

type FilterChip = { id: string; label: string; onRemove: () => void };

type CategoriesState = {
  state: "loading" | "ok" | "error";
  rows: PublicShopCategory[];
};

function FilterCheckbox({
  checked,
  onChange,
  soon,
  children,
}: {
  checked: boolean;
  onChange: () => void;
  soon?: boolean;
  children: ReactNode;
}) {
  return (
    <label className={`shop-filter-row ${checked ? "is-checked" : ""} ${soon ? "is-soon" : ""}`}>
      <span className="shop-filter-check" aria-hidden="true">
        <input type="checkbox" checked={checked} onChange={onChange} aria-disabled={soon || undefined} />
        {checked ? <Check strokeWidth={3} /> : null}
      </span>
      <span className="shop-filter-row-main">{children}</span>
    </label>
  );
}

function RatingStars({ value }: { value: number }) {
  return (
    <span className="shop-rating-stars" aria-hidden="true">
      {[1, 2, 3, 4, 5].map((star) => {
        const fill = value >= star ? 1 : value >= star - 0.5 ? 0.5 : 0;
        if (fill === 0.5) {
          return (
            <span key={star} className="shop-star is-half">
              <Star />
              <span className="shop-star-half"><Star /></span>
            </span>
          );
        }
        return (
          <span key={star} className={`shop-star is-${fill === 1 ? "full" : "empty"}`}>
            <Star />
          </span>
        );
      })}
    </span>
  );
}

function FilterGroup({
  title,
  open,
  onToggle,
  note,
  onHeadClick,
  children,
}: {
  title: string;
  open: boolean;
  onToggle: () => void;
  note?: string;
  onHeadClick?: () => void;
  children: ReactNode;
}) {
  return (
    <div className={`shop-filter-group ${open ? "is-open" : ""}`}>
      <button
        type="button"
        className="shop-filter-group-head"
        aria-expanded={open}
        onClick={() => {
          onToggle();
          onHeadClick?.();
        }}
      >
        <span>
          {title}
          {note ? <span className="shop-filter-and-up"> · {note}</span> : null}
        </span>
        <ChevronDown />
      </button>
      {open ? <div className="shop-filter-group-body">{children}</div> : null}
    </div>
  );
}

function FiltersBody({
  catalog,
  priceRanges,
  categories,
  localeTag,
  tp,
  hideTitle,
}: {
  catalog: ShopSectionProps["catalog"];
  priceRanges: string[];
  categories: CategoriesState;
  localeTag: string;
  tp: Tp;
  hideTitle?: boolean;
}) {
  const [openCategory, setOpenCategory] = useState(true);
  const [openPrice, setOpenPrice] = useState(true);
  const [openBrand, setOpenBrand] = useState(false);
  const [openServices, setOpenServices] = useState(false);
  const [openRating, setOpenRating] = useState(false);
  const [categoryExpanded, setCategoryExpanded] = useState(false);

  const soonNotice = () => catalog.onNotify(tp("shop.filters.comingSoonNotice"));
  const soonNote = tp("shop.filters.comingSoon");

  const showCategory =
    isFilterShown("category")
    && isFilterOn("category")
    && Boolean(catalog.onCategory)
    && (categories.state === "loading" || (categories.state === "ok" && categories.rows.length > 0));

  const categoryRows = categories.rows;
  const visibleCategories = (() => {
    if (categoryExpanded) return categoryRows;
    const base = categoryRows.slice(0, BRAND_PREVIEW);
    const selectedId = catalog.categoryId;
    if (selectedId == null) return base;
    const selected = categoryRows.find((row) => row.id === selectedId);
    if (selected && !base.some((row) => row.id === selectedId)) return [...base, selected];
    return base;
  })();
  const hiddenCategoryCount = Math.max(0, categoryRows.length - BRAND_PREVIEW);

  const showBrand = isFilterShown("brand");
  const showInStock = isFilterShown("inStock");
  const showOnSale = isFilterShown("onSale");
  const showServices = showInStock || showOnSale;
  const showRating = isFilterShown("rating");

  return (
    <div className="shop-filters-body">
      {!hideTitle && <h2 className="shop-filters-title">{tp("catalog.filters")}</h2>}

      {showCategory && (
        <FilterGroup title={tp("shop.filters.category")} open={openCategory} onToggle={() => setOpenCategory((v) => !v)}>
          {categories.state === "loading" && Array.from({ length: 4 }, (_, index) => (
            <div className="shop-filter-row" aria-hidden="true" key={index}>
              <Skeleton width={18} height={18} radius={4} />
              <Skeleton width="70%" height={12} />
            </div>
          ))}
          {categories.state === "ok" && visibleCategories.map((row) => {
            const checked = catalog.categoryId === row.id;
            const name = localizedText(localeTag, row.nameTh, row.nameEn, row.nameZh) || row.nameTh;
            return (
              <FilterCheckbox
                key={row.id}
                checked={checked}
                onChange={() => catalog.onCategory?.(checked ? null : row.id)}
              >
                <span>{name}</span>
                <span className="shop-filter-and-up num shop-filter-count">{row.productCount}</span>
              </FilterCheckbox>
            );
          })}
          {categories.state === "ok" && !categoryExpanded && hiddenCategoryCount > 0 && (
            <button type="button" className="shop-brand-more" onClick={() => setCategoryExpanded(true)}>
              {tp("shop.brandMore", { count: hiddenCategoryCount })}
            </button>
          )}
        </FilterGroup>
      )}

      {isFilterShown("price") && (
        <FilterGroup title={tp("catalog.price")} open={openPrice} onToggle={() => setOpenPrice((v) => !v)}>
          {priceRanges.map((label, index) => {
            const band = SHOP_PRICE_BAND_IDS[index];
            if (!band) return null;
            const checked = catalog.priceBands.includes(band);
            return (
              <FilterCheckbox key={band} checked={checked} onChange={() => catalog.onTogglePriceBand(band)}>
                <span>{label}</span>
              </FilterCheckbox>
            );
          })}
        </FilterGroup>
      )}

      {showBrand && (
        <FilterGroup
          title={tp("catalog.brands")}
          open={openBrand}
          onToggle={() => setOpenBrand((v) => !v)}
          note={soonNote}
          onHeadClick={soonNotice}
        >
          <div className="shop-brand-search is-soon">
            <Search />
            <input
              readOnly
              value=""
              placeholder={tp("catalog.brandSearch")}
              onClick={soonNotice}
              onFocus={soonNotice}
            />
          </div>
          <p className="shop-filter-and-up">{tp("shop.filters.brandPending")}</p>
        </FilterGroup>
      )}

      {showServices && (
        <FilterGroup
          title={tp("shop.filters.services")}
          open={openServices}
          onToggle={() => setOpenServices((v) => !v)}
          note={soonNote}
          onHeadClick={soonNotice}
        >
          {showInStock && (
            <FilterCheckbox soon checked={false} onChange={soonNotice}>
              <span>{tp("shop.filters.inStock")}</span>
            </FilterCheckbox>
          )}
          {showOnSale && (
            <FilterCheckbox soon checked={false} onChange={soonNotice}>
              <span>{tp("shop.filters.onSale")}</span>
            </FilterCheckbox>
          )}
        </FilterGroup>
      )}

      {showRating && (
        <FilterGroup
          title={tp("catalog.rating")}
          open={openRating}
          onToggle={() => setOpenRating((v) => !v)}
          note={soonNote}
          onHeadClick={soonNotice}
        >
          {RATING_LEVELS.map((value) => (
            <FilterCheckbox key={value} soon checked={false} onChange={soonNotice}>
              <RatingStars value={value} />
              <span className="shop-filter-and-up">{tp("shop.andUp")}</span>
            </FilterCheckbox>
          ))}
        </FilterGroup>
      )}
    </div>
  );
}

export default function CatalogSection({ shop, section, products, catalog, preview, config }: ShopSectionProps) {
  const { tp, localeTag } = usePublicTranslation();
  const variant = resolveVariant("CATALOG", section.variant) || "GRID";
  const priceRanges = tp("catalog.priceRanges", { returnObjects: true }) as unknown as string[];
  const pageCount = Math.max(1, Math.ceil(catalog.total / catalog.pageSize));
  const productLoading = catalog.productState === "loading";
  const [sheetOpen, setSheetOpen] = useState(false);
  const sheetRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const categories = useShopCategories(shop.slug, { enabled: !preview });

  const categoryActive = isFilterOn("category") && !catalog.categoryLocked && catalog.categoryId != null;
  const selectedFilterCount = catalog.priceBands.length + (categoryActive ? 1 : 0);

  useEffect(() => {
    if (!sheetOpen) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    closeRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setSheetOpen(false);
        return;
      }
      if (event.key !== "Tab" || !sheetRef.current) return;
      const focusable = [...sheetRef.current.querySelectorAll<HTMLElement>("button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])")]
        .filter((node) => !node.hasAttribute("disabled"));
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", onKey);
    };
  }, [sheetOpen]);

  const clearFilters = () => {
    catalog.priceBands.slice().forEach((band) => catalog.onTogglePriceBand(band));
    if (categoryActive) catalog.onCategory?.(null);
  };

  const categoryChipLabel = (() => {
    if (!categoryActive || catalog.categoryId == null) return "";
    const row = categories.rows.find((item) => item.id === catalog.categoryId);
    if (!row) return tp("shop.filters.category");
    return localizedText(localeTag, row.nameTh, row.nameEn, row.nameZh) || row.nameTh;
  })();

  const chips: FilterChip[] = [
    ...catalog.priceBands.map((band) => {
      const index = SHOP_PRICE_BAND_IDS.indexOf(band as (typeof SHOP_PRICE_BAND_IDS)[number]);
      return {
        id: `price-${band}`,
        label: priceRanges[index] || band,
        onRemove: () => catalog.onTogglePriceBand(band),
      };
    }),
    ...(categoryActive
      ? [{
          id: `category-${catalog.categoryId}`,
          label: categoryChipLabel,
          onRemove: () => catalog.onCategory?.(null),
        }]
      : []),
  ];

  const filterProps = {
    catalog,
    priceRanges,
    categories,
    localeTag,
    tp,
  };

  return (
    <section id="shop-catalog" className={`${sectionRootClass("CATALOG", variant, config)} recommend-catalog`} data-variant={variant}>
      <aside className="shop-filters-desktop">
        <header className="shop-filters-head">
          <h2>{tp("catalog.filters")}</h2>
          {selectedFilterCount > 0 && (
            <button type="button" className="shop-filters-clear" onClick={clearFilters}>{tp("catalog.clear")}</button>
          )}
        </header>
        <FiltersBody {...filterProps} hideTitle />
      </aside>

      <div className="recommend-results">
        <div className="shop-toolbar recommend-tools">
          <button
            type="button"
            className="shop-filter-trigger"
            onClick={() => !preview && setSheetOpen(true)}
          >
            <SlidersHorizontal />
            <span>
              {tp("catalog.filters")}
              {selectedFilterCount > 0 ? ` (${selectedFilterCount})` : ""}
            </span>
          </button>
          <p className="shop-result-count">
            {tp("shop.foundCount", { count: catalog.total }).split(/(\d+)/).map((part, index) => (
              /^\d+$/.test(part) ? <span className="num" key={index}>{part}</span> : <span key={index}>{part}</span>
            ))}
          </p>
          <label className="shop-sort-label">
            <span className="visually-hidden">{tp("common.sortBy")}</span>
            <select
              value={catalog.sort}
              onChange={(event) => catalog.onSort(parseShopSortKey(event.target.value))}
              aria-label={tp("catalog.sortAria")}
            >
              <option value="featured">{tp("common.sortBy")}: {tp("catalog.sortFeatured")}</option>
              <option value="price">{tp("common.sortBy")}: {tp("catalog.sortPrice")}</option>
              <option value="price_desc">{tp("common.sortBy")}: {tp("shop.sortPriceHigh")}</option>
            </select>
          </label>
          {variant === "GRID" && (
            <div className="shop-view-toggle">
              <button className={catalog.grid ? "active" : ""} type="button" onClick={() => catalog.onGrid(true)} aria-label={tp("common.gridView")}>
                <Grid2X2 />
              </button>
              <button className={!catalog.grid ? "active" : ""} type="button" onClick={() => catalog.onGrid(false)} aria-label={tp("common.listView")}>
                <List />
              </button>
            </div>
          )}
        </div>

        {chips.length > 0 && (
          <div className="shop-filter-chips">
            {chips.map((chip) => (
              <button type="button" className="shop-filter-chip" key={chip.id} onClick={chip.onRemove}>
                <span>{chip.label}</span>
                <X />
              </button>
            ))}
            <button type="button" className="shop-filter-chips-clear" onClick={clearFilters}>{tp("shop.clearAll")}</button>
          </div>
        )}

        <p className="shop-result-count-mobile">{tp("shop.productResultCount", { count: catalog.total })}</p>

        {catalog.showSkeleton && productLoading && products.length === 0 && (
          <div className={productGridClass(variant, catalog.grid)} aria-label={tp("shop.productsLoading")}>
            {Array.from({ length: GRID_COLS }, (_, index) => (
              <SkeletonCard key={index} />
            ))}
          </div>
        )}
        {catalog.productState === "error" && (
          <LoadError message={tp("shop.productsError")} onRetry={catalog.onRetry} retryLabel={tp("shop.productsRetry")} />
        )}
        {catalog.productState === "ok" && products.length === 0 && <article className="shop-list-empty">{tp("shop.productsEmpty")}</article>}
        {products.length > 0 && (
          <SkeletonBusy refetching={catalog.refetching} busy={catalog.refetching}>
            <ProductGrid
              shop={shop}
              products={products}
              variant={variant}
              grid={catalog.grid}
              favorites={catalog.favorites}
              onToggleFavorite={catalog.onToggleFavorite}
              onNotify={catalog.onNotify}
            />
          </SkeletonBusy>
        )}
        {catalog.productState === "ok" && catalog.total > catalog.pageSize && (
          <nav className="shop-pagination" aria-label={tp("shop.productsPageAria")}>
            <button type="button" disabled={catalog.page <= 1} onClick={() => catalog.onPage(Math.max(1, catalog.page - 1))} aria-label={tp("shops.pagePrev")}>
              <ChevronLeft />
            </button>
            {Array.from({ length: pageCount }, (_, index) => index + 1).map((number) => (
              <button type="button" className={`num ${catalog.page === number ? "current" : ""}`} key={number} onClick={() => catalog.onPage(number)}>
                {number}
              </button>
            ))}
            <button type="button" disabled={catalog.page >= pageCount} onClick={() => catalog.onPage(Math.min(pageCount, catalog.page + 1))} aria-label={tp("shops.pageNext")}>
              <ChevronRight />
            </button>
          </nav>
        )}
      </div>

      {sheetOpen && (
        <div
          className="shop-filter-sheet"
          role="dialog"
          aria-modal="true"
          aria-labelledby={titleId}
          onClick={(event) => {
            if (event.target === event.currentTarget) setSheetOpen(false);
          }}
        >
          <section ref={sheetRef}>
            <div className="shop-filter-sheet-handle" aria-hidden="true" />
            <header>
              <h2 id={titleId}>{tp("catalog.filters")}</h2>
              <button ref={closeRef} type="button" className="shop-filter-sheet-close" onClick={() => setSheetOpen(false)} aria-label={tp("shop.view.close")}>
                <X />
              </button>
            </header>
            <div className="shop-filter-sheet-body">
              <FiltersBody {...filterProps} hideTitle />
            </div>
            <div className="shop-filter-sheet-actions">
              <button type="button" className="secondary" onClick={clearFilters}>{tp("catalog.clear")}</button>
              <button type="button" className="primary" onClick={() => setSheetOpen(false)}>
                {tp("catalog.applyCount", { count: catalog.total })}
              </button>
            </div>
          </section>
        </div>
      )}
    </section>
  );
}
