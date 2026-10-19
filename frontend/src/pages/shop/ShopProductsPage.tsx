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

  const { products, catalog } = useShopCatalog({ slug });
  const section = theme.config.sections.find((item) => item.key === "CATALOG");

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
