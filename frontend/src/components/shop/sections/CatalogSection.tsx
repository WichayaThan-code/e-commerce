import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { Check, ChevronDown, ChevronLeft, ChevronRight, Grid2X2, List, Search, SlidersHorizontal, Star, X } from "lucide-react";
import { LoadError, SkeletonBusy, SkeletonCard } from "../../feedback/Skeleton";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { SHOP_PRICE_BAND_IDS } from "../../../services/shopPriceBands";
import ProductGrid, { productGridClass } from "./ProductGrid";
import { resolveVariant, sectionRootClass } from "./sectionClasses";
import type { ShopSectionProps, ShopSortKey } from "./types";

const GRID_COLS = 8;
const BRAND_PREVIEW = 5;
/** Star fill levels for rating filters (4.5 / 4 / 3.5 / 3). */
const RATING_LEVELS = [4.5, 4, 3.5, 3] as const;

type Tp = (key: string, opts?: Record<string, unknown>) => string;

type FilterChip = { id: string; label: string; onRemove: () => void };

function FilterCheckbox({ checked, onChange, children }: { checked: boolean; onChange: () => void; children: ReactNode }) {
  return (
    <label className={`shop-filter-row ${checked ? "is-checked" : ""}`}>
      <span className="shop-filter-check" aria-hidden="true">
        <input type="checkbox" checked={checked} onChange={onChange} />
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
  children,
}: {
  title: string;
  open: boolean;
  onToggle: () => void;
  children: ReactNode;
}) {
  return (
    <div className={`shop-filter-group ${open ? "is-open" : ""}`}>
      <button type="button" className="shop-filter-group-head" aria-expanded={open} onClick={onToggle}>
        <span>{title}</span>
        <ChevronDown />
      </button>
      {open ? <div className="shop-filter-group-body">{children}</div> : null}
    </div>
  );
}

function FiltersBody({
  catalog,
  priceRanges,
  brandTags,
  tp,
  selectedRatings,
  onToggleRating,
  selectedBrands,
  onToggleBrand,
  brandExpanded,
  onExpandBrands,
  hideTitle,
}: {
  catalog: ShopSectionProps["catalog"];
  priceRanges: string[];
  brandTags: string[];
  tp: Tp;
  selectedRatings: number[];
  onToggleRating: (value: number) => void;
  selectedBrands: string[];
  onToggleBrand: (brand: string) => void;
  brandExpanded: boolean;
  onExpandBrands: () => void;
  hideTitle?: boolean;
}) {
  const [openPrice, setOpenPrice] = useState(true);
  const [openRating, setOpenRating] = useState(true);
  const [openBrand, setOpenBrand] = useState(true);
  const filteredBrands = brandTags.filter((label) => label.toLowerCase().includes(catalog.brandQuery.trim().toLowerCase()));
  const visibleBrands = brandExpanded ? filteredBrands : filteredBrands.slice(0, BRAND_PREVIEW);
  const hiddenBrandCount = Math.max(0, filteredBrands.length - BRAND_PREVIEW);

  return (
    <div className="shop-filters-body">
      {!hideTitle && <h2 className="shop-filters-title">{tp("catalog.filters")}</h2>}
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
      <FilterGroup title={tp("catalog.rating")} open={openRating} onToggle={() => setOpenRating((v) => !v)}>
        {RATING_LEVELS.map((value) => {
          const checked = selectedRatings.includes(value);
          return (
            <FilterCheckbox key={value} checked={checked} onChange={() => onToggleRating(value)}>
              <RatingStars value={value} />
              <span className="shop-filter-and-up">{tp("shop.andUp")}</span>
            </FilterCheckbox>
          );
        })}
      </FilterGroup>
      <FilterGroup title={tp("catalog.brands")} open={openBrand} onToggle={() => setOpenBrand((v) => !v)}>
        <div className="shop-brand-search">
          <Search />
          <input
            value={catalog.brandQuery}
            onChange={(event) => catalog.onBrandQuery(event.target.value)}
            placeholder={tp("catalog.brandSearch")}
          />
        </div>
        {visibleBrands.map((label) => (
          <FilterCheckbox key={label} checked={selectedBrands.includes(label)} onChange={() => onToggleBrand(label)}>
            <span>{label}</span>
          </FilterCheckbox>
        ))}
        {!brandExpanded && hiddenBrandCount > 0 && (
          <button type="button" className="shop-brand-more" onClick={onExpandBrands}>
            {tp("shop.brandMore", { count: hiddenBrandCount })}
          </button>
        )}
      </FilterGroup>
    </div>
  );
}

export default function CatalogSection({ shop: _shop, section, products, catalog, preview }: ShopSectionProps) {
  const { tp } = usePublicTranslation();
  const variant = resolveVariant("CATALOG", section.variant) || "GRID";
  const priceRanges = tp("catalog.priceRanges", { returnObjects: true }) as unknown as string[];
  const brandTags = tp("catalog.brandTags", { returnObjects: true }) as unknown as string[];
  const pageCount = Math.max(1, Math.ceil(catalog.total / catalog.pageSize));
  const productLoading = catalog.productState === "loading";
  const [sheetOpen, setSheetOpen] = useState(false);
  const [selectedRatings, setSelectedRatings] = useState<number[]>([]);
  const [selectedBrands, setSelectedBrands] = useState<string[]>([]);
  const [brandExpanded, setBrandExpanded] = useState(false);
  const sheetRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const selectedFilterCount = catalog.priceBands.length + selectedRatings.length + selectedBrands.length;

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

  const toggleRating = (value: number) => {
    setSelectedRatings((current) => (current.includes(value) ? current.filter((item) => item !== value) : [...current, value]));
  };

  const toggleBrand = (brand: string) => {
    setSelectedBrands((current) => (current.includes(brand) ? current.filter((item) => item !== brand) : [...current, brand]));
  };

  const clearFilters = () => {
    catalog.priceBands.slice().forEach((band) => catalog.onTogglePriceBand(band));
    catalog.onBrandQuery("");
    setSelectedRatings([]);
    setSelectedBrands([]);
  };

  const chips: FilterChip[] = [
    ...catalog.priceBands.map((band) => {
      const index = SHOP_PRICE_BAND_IDS.indexOf(band as (typeof SHOP_PRICE_BAND_IDS)[number]);
      return {
        id: `price-${band}`,
        label: priceRanges[index] || band,
        onRemove: () => catalog.onTogglePriceBand(band),
      };
    }),
    ...selectedRatings.map((value) => ({
      id: `rating-${value}`,
      label: `${value} ${tp("shop.andUp")}`,
      onRemove: () => toggleRating(value),
    })),
    ...selectedBrands.map((brand) => ({
      id: `brand-${brand}`,
      label: brand,
      onRemove: () => toggleBrand(brand),
    })),
  ];

  const filterProps = {
    catalog,
    priceRanges,
    brandTags,
    tp,
    selectedRatings,
    onToggleRating: toggleRating,
    selectedBrands,
    onToggleBrand: toggleBrand,
    brandExpanded,
    onExpandBrands: () => setBrandExpanded(true),
  };

  return (
    <section id="shop-catalog" className={`${sectionRootClass("CATALOG", variant)} recommend-catalog`} data-variant={variant}>
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
              onChange={(event) => catalog.onSort(event.target.value as ShopSortKey)}
              aria-label={tp("catalog.sortAria")}
            >
              <option value="featured">{tp("common.sortBy")}: {tp("catalog.sortFeatured")}</option>
              <option value="price">{tp("common.sortBy")}: {tp("catalog.sortPrice")}</option>
              <option value="best">{tp("common.sortBy")}: {tp("catalog.sortBest")}</option>
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
              shop={_shop}
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
