import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import ProductCard from "../../components/shop/ProductCard";
import { useShopPublic, useShopCrumb } from "../../contexts/ShopPublicContext";
import { loginPathForCart } from "../../contexts/CartContext";
import { useDocumentMeta } from "../../hooks/useDocumentMeta";
import { useFavorites } from "../../hooks/useFavorites";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { shopApi, type PublicProduct } from "../../services/api";
import { shopProductsPath } from "../../services/shopRoutes";

/** /shop/:slug/favorites - the signed-in buyer's favourites in this shop (public product API only). */
export default function ShopFavoritesPage() {
  const { slug, shop, notify } = useShopPublic();
  const { tp } = usePublicTranslation();
  const label = tp("shop.general.favoritesTitle");
  useShopCrumb(label);
  useDocumentMeta({ title: `${label} \u00b7 ${shop.name}` });
  const messages = useMemo(() => ({
    login: tp("shop.general.favoritesLogin"), added: tp("shop.general.favoriteAdded"),
    removed: tp("shop.general.favoriteRemoved"), failed: tp("shop.general.favoriteFailed"),
  }), [tp]);
  const { access, ids, items, loaded, toggle } = useFavorites(slug, notify, messages);
  const [products, setProducts] = useState<Record<string, PublicProduct>>({});
  const slugKey = items.map((item) => item.productSlug).join(",");

  useEffect(() => {
    let alive = true;
    const wanted = items.filter((item) => item.productSlug && !products[item.productId]).slice(0, 60);
    if (!wanted.length) return;
    void Promise.all(wanted.map((item) => shopApi.product(slug, item.productSlug).catch(() => null))).then((rows) => {
      if (!alive) return;
      setProducts((current) => {
        const next = { ...current };
        rows.forEach((row) => { if (row) next[row.id] = row; });
        return next;
      });
    });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slug, slugKey]);

  const shown = items.map((item) => products[item.productId]).filter((row): row is PublicProduct => Boolean(row));
  return (
    <section className="tsf-section tsf-favorites" data-pad="NORMAL" data-align="LEFT">
      <div className="tsf-wrap">
        <div className="tsf-section-head"><div><h1 className="tsf-display">{label}</h1></div></div>
        {access === "guest" ? (
          <p className="tsf-favorites-empty">
            {tp("shop.general.favoritesLogin")} <a className="tsf-btn tsf-btn-link" href={loginPathForCart()}>{tp("shop.general.favoritesLoginCta")}</a>
          </p>
        ) : !loaded ? (
          <p className="tsf-favorites-empty" aria-busy="true">{tp("shop.general.favoritesLoading")}</p>
        ) : !items.length ? (
          <p className="tsf-favorites-empty">
            {tp("shop.general.favoritesEmpty")} <Link className="tsf-btn tsf-btn-link" to={shopProductsPath(slug)}>{tp("shop.general.allProducts")}</Link>
          </p>
        ) : (
          <div className="tsf-grid tsf-favorites-grid">
            {shown.map((product) => (
              <ProductCard key={product.id} shop={shop} product={product} favorites={ids} onToggleFavorite={(id) => void toggle(id, product.slug)} onNotify={notify} />
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
