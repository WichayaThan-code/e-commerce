import { useMemo } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import CatalogSection from "../../components/shop/sections/CatalogSection";
import CategoriesSection from "../../components/shop/sections/CategoriesSection";
import { previewCatalogControls } from "../../components/shop/sections/types";
import { LoadError, Skeleton } from "../../components/feedback/Skeleton";
import { useShopPublic, useShopCrumb } from "../../contexts/ShopPublicContext";
import { useDocumentMeta } from "../../hooks/useDocumentMeta";
import { useShopCatalog } from "../../hooks/useShopCatalog";
import { useShopCategories } from "../../hooks/useShopCategories";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { localizedText } from "../../services/catalogText";
import { findCategory, shopCategoriesPath, shopCategoryPath } from "../../services/shopRoutes";
import NotFoundPage from "../NotFoundPage";

export default function ShopCategoryPage() {
  const { slug, shop, theme, notify } = useShopPublic();
  const { categoryKey } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { tp, localeTag } = usePublicTranslation();
  const categories = useShopCategories(slug);

  const category = useMemo(
    () => (categoryKey && categories.state === "ok" ? findCategory(categories.rows, categoryKey) : undefined),
    [categoryKey, categories.state, categories.rows],
  );

  const listLabel = tp("shop.categoriesTitle");
  const categoryLabel = category
    ? localizedText(localeTag, category.nameTh, category.nameEn, category.nameZh) || category.nameTh
    : listLabel;
  const crumbLabel = !categoryKey
    ? listLabel
    : category
      ? categoryLabel
      : categories.state === "loading"
        ? listLabel
        : null;
  useShopCrumb(crumbLabel);
  useDocumentMeta({ title: crumbLabel ? `${crumbLabel} · ${shop.name}` : null });

  const { products, catalog } = useShopCatalog({
    slug,
    categoryId: category?.id ?? null,
    categoryLocked: Boolean(categoryKey),
    enabled: Boolean(categoryKey) ? Boolean(category) : false,
    onCategory: (id) => {
      const next = new URLSearchParams(searchParams);
      next.delete("page");
      next.delete("category");
      const search = next.toString();
      navigate({
        pathname: id == null
          ? shopCategoriesPath(slug)
          : shopCategoryPath(slug, categories.rows.find((row) => row.id === id) ?? { id }),
        search: search ? `?${search}` : "",
      });
    },
  });

  if (categories.state === "loading") {
    return (
      <section className="recommend-width" aria-hidden="true">
        <div className="shop-category-grid">
          {Array.from({ length: 8 }, (_, index) => (
            <div className="shop-category-tile" key={index}>
              <Skeleton className="shop-category-skel" />
              <Skeleton width="70%" height={12} />
            </div>
          ))}
        </div>
      </section>
    );
  }

  if (categories.state === "error") {
    return (
      <section className="recommend-width">
        <LoadError message={tp("shop.loadError")} onRetry={categories.reload} retryLabel={tp("shop.productsRetry")} />
      </section>
    );
  }

  if (!categoryKey) {
    const listCatalog = previewCatalogControls({
      onNotify: notify,
      onCategory: (id) => {
        if (id == null) return;
        navigate(shopCategoryPath(slug, categories.rows.find((row) => row.id === id) ?? { id }));
      },
    });
    return (
      <>
        <CategoriesSection
          shop={shop}
          config={theme.config}
          section={{ key: "CATEGORIES", enabled: true, variant: null }}
          products={[]}
          catalog={listCatalog}
          preview={false}
        />
      </>
    );
  }

  if (!category) {
    return <NotFoundPage kind="page" bare />;
  }

  const catalogSection = theme.config.sections.find((item) => item.key === "CATALOG");

  return (
    <>
      <CategoriesSection
        shop={shop}
        config={theme.config}
        section={{ key: "CATEGORIES", enabled: true, variant: null }}
        products={products}
        catalog={catalog}
        preview={false}
      />
      <CatalogSection
        shop={shop}
        config={theme.config}
        section={{ key: "CATALOG", enabled: true, variant: catalogSection?.variant ?? null }}
        products={products}
        catalog={catalog}
        preview={false}
      />
    </>
  );
}
