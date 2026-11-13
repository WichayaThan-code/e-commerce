import { TsfCatalogPage } from "../../components/shop/general/GeneralStorefront";
import { usesTsfTree } from "../../theme/shopTheme";
import CatalogSection from "../../components/shop/sections/CatalogSection";
import { useShopPublic, useShopCrumb } from "../../contexts/ShopPublicContext";
import { useDocumentMeta } from "../../hooks/useDocumentMeta";
import { useShopCatalog } from "../../hooks/useShopCatalog";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";

export default function ShopProductsPage() {
  const { slug, shop, theme } = useShopPublic();
  const { tp } = usePublicTranslation();
  const label = tp("shop.tabs.products");
  useShopCrumb(label);
  useDocumentMeta({ title: `${label} · ${shop.name}` });

  /** Desktop grid 4×6 → 24 items per page on /shop/:slug/products. */
  const { products, catalog } = useShopCatalog({ slug, pageSize: 24 });
  const section = theme.config.sections.find((item) => item.key === "CATALOG");

  if (usesTsfTree(theme.templateKey)) {
    return <TsfCatalogPage shop={shop} title={tp("shop.general.allProducts")} products={products} catalog={catalog} />;
  }

  return (
    <>
      <CatalogSection
        shop={shop}
        config={theme.config}
        section={{ key: "CATALOG", enabled: true, variant: section?.variant ?? null }}
        products={products}
        catalog={catalog}
        preview={false}
      />
    </>
  );
}
