import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import ShopCouponStrip from "../components/shop/ShopCouponStrip";
import { ShopSections } from "../components/shop/ShopStorefront";
import { useShopPublic } from "../contexts/ShopPublicContext";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import { useShopCatalog } from "../hooks/useShopCatalog";
import { useShopCategories } from "../hooks/useShopCategories";
import { shopCategoryPath } from "../services/shopRoutes";

export default function ShopPage() {
  const { slug, shop } = useShopPublic();
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

  useDocumentMeta({ title: shop.name, description: shop.description });

  useEffect(() => {
    if (location.hash) {
      document.getElementById(location.hash.slice(1))?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [location.hash, location.key]);

  return (
    <>
      <ShopSections
        shop={shop}
        products={products}
        catalog={catalog}
        preview={false}
        couponStrip={<ShopCouponStrip slug={slug} onNotify={catalog.onNotify} />}
      />
    </>
  );
}
