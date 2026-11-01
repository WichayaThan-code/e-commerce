import { useEffect, useMemo } from "react";
import TsfHome from "../components/shop/general/TsfHome";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { localizedText } from "../services/catalogText";
import { useLocation, useNavigate } from "react-router-dom";
import ShopCouponStrip from "../components/shop/ShopCouponStrip";
import { ShopSections } from "../components/shop/ShopStorefront";
import { useShopPublic } from "../contexts/ShopPublicContext";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import { useShopCatalog } from "../hooks/useShopCatalog";
import { useShopAllProducts } from "../hooks/useShopAllProducts";
import { useShopCategories } from "../hooks/useShopCategories";
import { shopCategoryPath } from "../services/shopRoutes";
import { usesTsfTree } from "../theme/shopTheme";

export default function ShopPage() {
  const { slug, shop, theme } = useShopPublic();
  const { tp, localeTag } = usePublicTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const categories = useShopCategories(slug);
  const { products, catalog } = useShopCatalog({
    slug,
    onCategory: (id) => {
      if (id == null) return;
      const row = categories.rows.find((item) => item.id === id) ?? { id };
      navigate(shopCategoryPath(slug, row));
    },
  });
  const allProducts = useShopAllProducts(slug, usesTsfTree(theme.templateKey));

  useDocumentMeta({ title: shop.name, description: shop.description });

  useEffect(() => {
    if (location.hash) {
      document.getElementById(location.hash.slice(1))?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [location.hash, location.key]);

  const categoryNames = useMemo(
    () => new Map(categories.rows.map((row) => [row.id, localizedText(localeTag, row.nameTh, row.nameEn, row.nameZh) || row.nameTh])),
    [categories.rows, localeTag],
  );

  if (usesTsfTree(theme.templateKey)) {
    return (
      <TsfHome slug={slug} shop={shop} theme={theme} products={products} catalog={catalog} allProducts={allProducts}
        categoryNames={categoryNames} couponStrip={<ShopCouponStrip slug={slug} shopId={shop.shopId} onNotify={catalog.onNotify} />} />
    );
  }

  return (
    <>
      <ShopSections
        shop={shop}
        products={products}
        catalog={catalog}
        preview={false}
        couponStrip={<ShopCouponStrip slug={slug} shopId={shop.shopId} onNotify={catalog.onNotify} />}
      />
    </>
  );
}
