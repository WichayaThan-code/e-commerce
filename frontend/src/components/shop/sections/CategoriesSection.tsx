import { useEffect, useMemo, useState } from "react";
import { Skeleton } from "../../feedback/Skeleton";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { localizedText } from "../../../services/catalogText";
import { shopApi, type PublicShopCategory } from "../../../services/api";
import { useRevealOnScroll } from "../../../hooks/useRevealOnScroll";
import { categoryIcon } from "../categoryIcons";
import { resolveStorefrontTheme } from "../ShopThemeShell";
import type { ShopSectionProps } from "./types";
import "../../../styles/shop-category-icons.css";

export default function CategoriesSection({ shop, catalog, preview }: ShopSectionProps) {
  const { tp, localeTag } = usePublicTranslation();
  const revealRef = useRevealOnScroll<HTMLElement>();
  const [rows, setRows] = useState<PublicShopCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const templateKey = useMemo(() => resolveStorefrontTheme(shop).templateKey, [shop]);

  useEffect(() => {
    if (!shop.slug || preview) {
      setRows([]);
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    shopApi.categories(shop.slug)
      .then((data) => { if (active) setRows(data); })
      .catch(() => { if (active) setRows([]); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [shop.slug, preview]);

  if (!loading && rows.length === 0) return null;

  return (
    <section id="shop-categories" ref={revealRef} className="shop-categories recommend-width shop-reveal">
      <header className="shop-section-head">
        <h2>{tp("shop.categoriesTitle")}</h2>
      </header>
      {loading ? (
        <div className="shop-category-grid" aria-hidden="true">
          {Array.from({ length: 8 }, (_, index) => (
            <div className="shop-category-tile" key={index}>
              <Skeleton className="shop-category-skel" />
              <Skeleton width="70%" height={12} />
            </div>
          ))}
        </div>
      ) : (
        <div className="shop-category-scroller">
          <div className="shop-category-grid">
            {rows.map((row) => {
              const name = localizedText(localeTag, row.nameTh, row.nameEn, row.nameZh) || row.nameTh;
              const selected = catalog.categoryId === row.id;
              const Icon = categoryIcon(row, templateKey);
              return (
                <button
                  type="button"
                  key={row.id}
                  className={`shop-category-tile ${selected ? "is-active" : ""}`}
                  onClick={() => catalog.onCategory?.(selected ? null : row.id)}
                >
                  <span className="shop-category-icon" aria-hidden="true"><Icon /></span>
                  <span className="shop-category-name">{name}</span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </section>
  );
}
