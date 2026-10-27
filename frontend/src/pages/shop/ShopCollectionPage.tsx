import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import ProductGrid from "../../components/shop/sections/ProductGrid";
import { LoadError, Skeleton } from "../../components/feedback/Skeleton";
import { useShopPublic, useShopCrumb } from "../../contexts/ShopPublicContext";
import { useDocumentMeta } from "../../hooks/useDocumentMeta";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { ApiError, mediaUrl } from "../../services/api";
import { publicCollectionApi, type PublicCollectionDetail } from "../../services/collectionsApi";
import NotFoundPage from "../NotFoundPage";
import "../../styles/shop-collections.css";

const PAGE_SIZE = 24;

/** V41 `/shop/:slug/collections/:collectionSlug` — CUSTOM storefronts only; public endpoints only. */
export default function ShopCollectionPage() {
  const { slug, shop, theme, notify } = useShopPublic();
  const { collectionSlug = "" } = useParams();
  const { tp, localeTag } = usePublicTranslation();
  const [page, setPage] = useState(0);
  const [detail, setDetail] = useState<PublicCollectionDetail | null>(null);
  const [state, setState] = useState<"loading" | "ok" | "missing" | "error">("loading");
  const [reloadKey, setReloadKey] = useState(0);
  const [favorites, setFavorites] = useState<string[]>([]);
  const custom = theme.templateKey === "CUSTOM";

  useEffect(() => { setPage(0); }, [slug, collectionSlug]);

  useEffect(() => {
    if (!custom) return;
    let alive = true;
    setState("loading");
    publicCollectionApi.get(slug, collectionSlug, { page, size: PAGE_SIZE, lang: localeTag.slice(0, 2) })
      .then((result) => { if (alive) { setDetail(result); setState("ok"); } })
      .catch((error) => { if (alive) setState(error instanceof ApiError && error.status === 404 ? "missing" : "error"); });
    return () => { alive = false; };
  }, [custom, slug, collectionSlug, page, localeTag, reloadKey]);

  const name = detail?.collection.name ?? null;
  useShopCrumb(state === "ok" ? name : null);
  useDocumentMeta({ title: name ? `${name} · ${shop.name}` : null });

  if (!custom || state === "missing") return <NotFoundPage kind="page" bare />;
  if (state === "error") {
    return (
      <section className="recommend-width">
        <LoadError message={tp("shop.loadError")} onRetry={() => setReloadKey((key) => key + 1)} retryLabel={tp("shop.productsRetry")} />
      </section>
    );
  }
  if (state === "loading" || !detail) {
    return (
      <section className="recommend-width shop-collection-page" aria-hidden="true">
        <Skeleton width="40%" height={24} />
      </section>
    );
  }

  const { collection, products } = detail;
  const pages = Math.max(1, Math.ceil(products.total / PAGE_SIZE));
  return (
    <section className="recommend-width shop-collection-page">
      <header className="shop-collection-page-head">
        {collection.coverUrl ? <img className="shop-collection-page-cover" src={mediaUrl(collection.coverUrl)} alt="" /> : null}
        <h1>{collection.name}</h1>
        {collection.description ? <p>{collection.description}</p> : null}
        <p className="shop-collection-count">{tp("shop.blocks.collections.count", { count: products.total })}</p>
      </header>
      {products.items.length ? (
        <ProductGrid
          shop={shop}
          products={products.items}
          favorites={favorites}
          onToggleFavorite={(id) => setFavorites((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]))}
          onNotify={notify}
        />
      ) : (
        <p className="shop-collection-empty">{tp("shop.blocks.collections.emptyProducts")}</p>
      )}
      {pages > 1 ? (
        <nav className="shop-collection-pager" aria-label={tp("shop.productsPageAria")}>
          <button type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>{tp("shop.blocks.collections.prev")}</button>
          <span>{page + 1} / {pages}</span>
          <button type="button" disabled={page + 1 >= pages} onClick={() => setPage((value) => value + 1)}>{tp("shop.blocks.collections.next")}</button>
        </nav>
      ) : null}
    </section>
  );
}
