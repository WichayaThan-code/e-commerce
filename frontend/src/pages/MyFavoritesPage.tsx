import { useCallback, useEffect, useState } from "react";
import { Heart, ShoppingCart, Store } from "lucide-react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import EmptyState from "../components/feedback/EmptyState";
import { cartErrorMessage, isOwnShop } from "../components/cart/cartFormat";
import { useAuth } from "../contexts/AuthContext";
import { loginPathForCart, useOptionalCart } from "../contexts/CartContext";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import {
  ApiError,
  favoritesApi,
  mediaUrl,
  shopApi,
  type FavoriteItem,
  type PublicProduct,
  type PublicShop,
} from "../services/api";
import { localizedText } from "../services/catalogText";
import { shopProductPath } from "../services/shopRoutes";
import "../styles/product-card.css";
import "../styles/favorites.css";

type FavRow = {
  item: FavoriteItem;
  product: PublicProduct;
  shop: PublicShop;
};

const MAX_RESOLVE = 80;

function amount(value: number | string | null | undefined) {
  if (value == null || value === "") return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function money(value: number, locale: string) {
  const tag = locale.startsWith("en") ? "en-US" : locale.startsWith("zh") ? "zh-CN" : "th-TH";
  const fraction = Math.round(value * 100) % 100 !== 0;
  return `฿${new Intl.NumberFormat(tag, { minimumFractionDigits: fraction ? 2 : 0, maximumFractionDigits: 2 }).format(value)}`;
}

export default function MyFavoritesPage() {
  const { user, loading: authLoading } = useAuth();
  const { tp, localeTag } = usePublicTranslation();
  const navigate = useNavigate();
  const cartApi = useOptionalCart();
  const [rows, setRows] = useState<FavRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [toast, setToast] = useState("");
  const [addingId, setAddingId] = useState<string | null>(null);

  useDocumentMeta({
    title: tp("favorites.title"),
    description: tp("favorites.subtitle"),
  });

  const notify = useCallback((message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(""), 2200);
  }, []);

  const reload = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const list = await favoritesApi.list();
      const capped = list.slice(0, MAX_RESOLVE);
      const shopCache = new Map<string, PublicShop>();
      const uniqueShops = [...new Set(capped.map((row) => row.shopSlug))];
      await Promise.all(
        uniqueShops.map(async (slug) => {
          try {
            shopCache.set(slug, await shopApi.getBySlug(slug));
          } catch {
            /* skip missing shops */
          }
        }),
      );
      const resolved = await Promise.all(
        capped.map(async (item) => {
          const shop = shopCache.get(item.shopSlug);
          if (!shop) return null;
          try {
            const product = await shopApi.product(item.shopSlug, item.productSlug);
            return { item, product, shop } satisfies FavRow;
          } catch {
            return null;
          }
        }),
      );
      setRows(resolved.filter((row): row is FavRow => Boolean(row)));
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        navigate(`/login?next=${encodeURIComponent("/favorites")}`, { replace: true });
        return;
      }
      setRows([]);
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    if (authLoading) return;
    if (!user) return;
    void reload();
  }, [authLoading, user, reload]);

  const removeFavorite = async (productId: string) => {
    setRows((prev) => prev.filter((row) => row.product.id !== productId));
    try {
      await favoritesApi.remove(productId);
      notify(tp("favorites.removed"));
    } catch (error) {
      notify(error instanceof ApiError ? error.message : tp("favorites.actionFail"));
      void reload();
    }
  };

  const addToCart = async (row: FavRow) => {
    const href = shopProductPath(row.shop.slug, row.product.slug);
    if (!cartApi) { navigate(href); return; }
    if (cartApi.access === "guest") {
      notify(tp("cart.loginToAdd"));
      window.location.assign(loginPathForCart());
      return;
    }
    if (cartApi.access === "blocked") { notify(tp("cart.blocked.toast")); return; }
    if (isOwnShop(user, row.shop.shopId)) { notify(tp("cart.own.toast")); return; }
    if (cartApi.access === "loading") return;
    const skus = row.product.skus ?? [];
    if (skus.length > 1) {
      notify(tp("cart.card.chooseOption"));
      navigate(href);
      return;
    }
    const sku = skus[0];
    if (!sku || sku.available <= 0) {
      notify(tp("cart.card.soldOut"));
      return;
    }
    setAddingId(row.product.id);
    try {
      const result = await cartApi.add(sku.id, 1);
      notify(result?.adjusted && result.appliedQuantity < 1 ? tp("shop.view.notEnough") : tp("shop.view.added"));
    } catch (error) {
      notify(cartErrorMessage(error, tp));
    } finally {
      setAddingId(null);
    }
  };

  if (!authLoading && !user) {
    return <Navigate to={`/login?next=${encodeURIComponent("/favorites")}`} replace />;
  }

  return (
    <main className="favorites-page">
      <section className="favorites-hero favorites-width">
        <p className="favorites-breadcrumb">
          <Link to="/">{tp("common.home")}</Link>
          <span aria-hidden="true">/</span>
          <span>{tp("favorites.title")}</span>
        </p>
        <div className="favorites-hero-card">
          <div className="favorites-hero-copy">
            <span className="favorites-kicker">
              <Heart aria-hidden="true" />
              {tp("favorites.kicker")}
            </span>
            <h1>{tp("favorites.title")}</h1>
            <p>{tp("favorites.subtitle")}</p>
          </div>
          <div className="favorites-count" aria-live="polite">
            <strong className="num">{loading ? "—" : rows.length}</strong>
            <span>{tp("favorites.countLabel")}</span>
          </div>
        </div>
      </section>

      <section className="favorites-main favorites-width">
        <div className="favorites-toolbar">
          <h2>{tp("favorites.listTitle")}</h2>
          <Link to="/recommendations">{tp("favorites.browseCta")}</Link>
        </div>

        {authLoading || loading ? (
          <div className="favorites-grid" aria-busy="true" aria-label={tp("favorites.loading")}>
            {Array.from({ length: 8 }, (_, index) => (
              <div className="favorites-skeleton" key={index} aria-hidden="true">
                <span />
                <div>
                  <i /><i /><i /><i />
                </div>
              </div>
            ))}
          </div>
        ) : loadError ? (
          <div className="favorites-empty-wrap">
            <EmptyState
              icon={Heart}
              title={tp("favorites.loadFail")}
              action={{ label: tp("favorites.retry"), onClick: () => void reload() }}
            />
          </div>
        ) : rows.length === 0 ? (
          <div className="favorites-empty-wrap">
            <EmptyState
              icon={Heart}
              title={tp("favorites.empty")}
              description={tp("favorites.emptyHint")}
              action={{ label: tp("favorites.emptyCta"), to: "/best-sellers" }}
            />
          </div>
        ) : (
          <div className="favorites-grid">
            {rows.map((row) => {
              const { product, shop } = row;
              const href = shopProductPath(shop.slug, product.slug);
              const name = localizedText(localeTag, product.nameTh, product.nameEn, product.nameZh);
              const shopName = localizedText(localeTag, shop.name, shop.nameEn, shop.nameZh);
              const image = [...product.images].sort((a, b) => Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder)[0];
              const price = amount(product.basePrice ?? product.skus[0]?.price);
              const compare = amount(product.compareAtPrice ?? product.skus[0]?.compareAtPrice);
              const discount = price != null && compare != null && compare > price
                ? Math.round((1 - price / compare) * 100)
                : null;
              const own = isOwnShop(user, shop.shopId);
              const busy = addingId === product.id;
              return (
                <article className="fav-product" key={product.id}>
                  <div className="fav-photo">
                    <Link to={href} className="fav-photo-link" aria-label={name}>
                      {image ? <img src={mediaUrl(image.url)} alt="" loading="lazy" /> : <span aria-hidden="true" />}
                    </Link>
                    <button
                      type="button"
                      className="liked"
                      aria-label={tp("catalog.favorite")}
                      onClick={() => void removeFavorite(product.id)}
                    >
                      <Heart aria-hidden="true" />
                    </button>
                  </div>
                  <div className="fav-info">
                    <h2>
                      <Link to={href}><span>{name}</span></Link>
                    </h2>
                    <p className="fav-shop">
                      <Store aria-hidden="true" />
                      <Link to={`/shop/${shop.slug}`}>{shopName}</Link>
                    </p>
                    <p className="fav-price">
                      <b className="num">{price == null ? "—" : money(price, localeTag)}</b>
                      {discount != null && compare != null ? (
                        <>
                          <del className="num">{money(compare, localeTag)}</del>
                          <em>-{discount}%</em>
                        </>
                      ) : null}
                    </p>
                    <button
                      type="button"
                      className="fav-cart"
                      disabled={busy || own}
                      title={own ? tp("cart.own.label") : undefined}
                      onClick={() => void addToCart(row)}
                    >
                      <ShoppingCart aria-hidden="true" />
                      {tp("recommend.addToCart")}
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      {toast ? <div className="favorites-toast" role="status">{toast}</div> : null}
    </main>
  );
}
