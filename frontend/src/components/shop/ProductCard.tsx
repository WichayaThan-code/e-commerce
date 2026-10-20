import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import { loginPathForCart, useOptionalCart } from "../../contexts/CartContext";
import { cartErrorMessage, isOwnShop } from "../cart/cartFormat";
import { Heart, ShoppingCart, Star } from "lucide-react";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { mediaUrl, type PublicProduct, type PublicShop } from "../../services/api";
import { localizedText } from "../../services/catalogText";

export type ProductCardVariant = "catalog" | "featured-row" | "featured-large" | "menu-row";

type ProductCardProps = {
  shop: PublicShop;
  product: PublicProduct;
  variant?: ProductCardVariant;
  favorites: string[];
  onToggleFavorite: (productId: string) => void;
  onNotify: (message: string) => void;
  className?: string;
  /** Bestseller rail rank 1–10. */
  rank?: number;
  /** Show Mall corner badge on catalog/featured cards. */
  mallBadge?: boolean;
};

function money(value: number, locale: string) {
  const tag = locale.startsWith("en") ? "en-US" : locale.startsWith("zh") ? "zh-CN" : "th-TH";
  const fraction = Math.round(value * 100) % 100 !== 0;
  return `฿${new Intl.NumberFormat(tag, { minimumFractionDigits: fraction ? 2 : 0, maximumFractionDigits: 2 }).format(value)}`;
}

function amount(value: number | string | null | undefined) {
  if (value == null || value === "") return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function optionalMetric(product: PublicProduct, key: "rating" | "soldCount" | "salesCount") {
  const raw = (product as PublicProduct & Record<string, unknown>)[key];
  if (raw == null || raw === "") return null;
  const number = Number(raw);
  return Number.isFinite(number) && number > 0 ? number : null;
}

export default function ProductCard({
  shop,
  product,
  variant = "catalog",
  favorites,
  onToggleFavorite,
  onNotify,
  className,
  rank,
  mallBadge = true,
}: ProductCardProps) {
  const { tp, localeTag } = usePublicTranslation();
  const sortedImages = [...product.images].sort((a, b) => Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder);
  const primary = sortedImages[0];
  const secondary = sortedImages[1];
  const name = localizedText(localeTag, product.nameTh, product.nameEn, product.nameZh);
  const description = localizedText(localeTag, product.descriptionTh, product.descriptionEn, product.descriptionZh);
  const price = amount(product.basePrice ?? product.skus[0]?.price);
  const compare = amount(product.compareAtPrice ?? product.skus[0]?.compareAtPrice);
  const discount = price != null && compare != null && compare > price ? Math.round((1 - price / compare) * 100) : null;
  const href = `/shop/${shop.slug}/product/${product.slug}`;
  const liked = favorites.includes(product.id);
  const rating = optionalMetric(product, "rating");
  const sold = optionalMetric(product, "soldCount") ?? optionalMetric(product, "salesCount");
  const rail = variant === "featured-row" || variant === "featured-large";
  const menu = variant === "menu-row";
  const roleClass = menu ? "is-menu-row" : rail ? "is-featured-row" : "is-catalog";
  const sizeClass = variant === "featured-large" ? "is-featured-large" : "";
  const classes = ["recommend-product", "shop-mall-card", roleClass, sizeClass, className].filter(Boolean).join(" ");
  const hasAlt = Boolean(secondary);

  const heart = (
    <button
      type="button"
      className={liked ? "liked" : ""}
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();
        onToggleFavorite(product.id);
      }}
      aria-label={tp("catalog.favorite")}
    >
      <Heart />
    </button>
  );

  const { user } = useAuth();
  const cartApi = useOptionalCart();
  const navigate = useNavigate();
  const [adding, setAdding] = useState(false);
  const own = isOwnShop(user, shop.shopId);
  const skus = product.skus ?? [];
  const sellable = skus.filter((sku) => sku.available > 0);
  /**
   * 1 SKU → add 1 piece through the cart API. More than 1 SKU → open the product page to choose the variant (toast explains).
   * Outside CartProvider (theme-editor preview) keep the old notice so the preview never calls the cart API.
   */
  const addFromCard = async () => {
    if (!cartApi) { onNotify(tp("shop.view.soon")); return; }
    // guests sign in and come back; admins blocked; merchants may buy other shops but not their own
    if (cartApi.access === "guest") { onNotify(tp("cart.loginToAdd")); window.location.assign(loginPathForCart()); return; }
    if (cartApi.access === "blocked") { onNotify(tp("cart.blocked.toast")); return; }
    if (own) { onNotify(tp("cart.own.toast")); return; }
    if (cartApi.access === "loading") return;
    if (skus.length > 1) { onNotify(tp("cart.card.chooseOption")); navigate(href); return; }
    const sku = skus[0];
    if (!sku || sellable.length === 0) { onNotify(tp("cart.card.soldOut")); return; }
    setAdding(true);
    try {
      const result = await cartApi.add(sku.id, 1);
      onNotify(result?.adjusted && result.appliedQuantity < 1 ? tp("shop.view.notEnough") : tp("shop.view.added"));
    } catch (error) {
      onNotify(cartErrorMessage(error, tp));
    } finally {
      setAdding(false);
    }
  };

  const cart = (
    <button
      type="button"
      className="shop-cart-btn"
      disabled={adding || own}
      title={own ? tp("cart.own.label") : undefined}
      aria-busy={adding || undefined}
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();
        void addFromCard();
      }}
      aria-label={tp("recommend.addToCart")}
    >
      <ShoppingCart />
      <span className="shop-cart-label">{tp("recommend.addToCart")}</span>
    </button>
  );

  const showRank = rank != null && rank >= 1 && rank <= 10;
  const showMall = mallBadge && !menu;
  const badges = (
    <>
      {(showRank || showMall) && (
        <div className="shop-card-badges" aria-hidden="true">
          {showRank && <span className="shop-rank-badge num">{rank}</span>}
          {showMall && <span className="shop-mall-badge">{tp("shop.mallBadge")}</span>}
        </div>
      )}
      {discount != null && <span className="shop-discount-badge num">-{discount}%</span>}
    </>
  );

  if (menu) {
    return (
      <article className={classes}>
        <Link className="shop-card-hit" to={href} aria-label={name} tabIndex={0} />
        <div className="recommend-photo">
          {primary ? <img className="shop-photo-primary" src={mediaUrl(primary.url)} alt="" draggable={false} /> : null}
        </div>
        <div className="recommend-product-info">
          <h2><span>{name}</span></h2>
          {description ? <p className="shop-menu-desc">{description}</p> : null}
          <div className="shop-menu-foot">
            <p className="recommend-price shop-price-catalog">
              <b className="num">{price == null ? "—" : money(price, localeTag)}</b>
              {compare != null && discount != null && <del className="num">{money(compare, localeTag)}</del>}
            </p>
            {cart}
          </div>
        </div>
      </article>
    );
  }

  const photo = (
    <div className={`recommend-photo ${hasAlt ? "has-alt" : "is-single"}`}>
      {primary ? <img className="shop-photo-primary" src={mediaUrl(primary.url)} alt="" draggable={false} /> : null}
      {secondary ? <img className="shop-photo-alt" src={mediaUrl(secondary.url)} alt="" draggable={false} /> : null}
      {badges}
      {heart}
    </div>
  );

  const title = (
    <h2 className="shop-product-title">
      <span>{name}</span>
    </h2>
  );

  const priceRow = (
    <p className={`recommend-price ${rail ? "shop-price-featured" : "shop-price-catalog"}`}>
      <b className="num">{price == null ? "—" : money(price, localeTag)}</b>
      {compare != null && discount != null && <del className="num">{money(compare, localeTag)}</del>}
    </p>
  );

  const meta = (rating != null || sold != null) ? (
    <p className="shop-product-meta">
      {rating != null && (
        <span className="shop-rating-badge">
          <Star />
          <b className="num">{rating.toFixed(1)}</b>
        </span>
      )}
      {sold != null && (
        <span className="shop-sold muted">{tp("shop.soldCount", { count: sold })}</span>
      )}
    </p>
  ) : null;

  return (
    <article className={classes}>
      <Link className="shop-card-hit" to={href} aria-label={name} tabIndex={0} />
      {photo}
      <div className="recommend-product-info">
        {title}
        {priceRow}
        {meta}
        {!rail ? cart : null}
      </div>
    </article>
  );
}
