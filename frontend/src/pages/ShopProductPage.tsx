import { useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import EmptyState from "../components/feedback/EmptyState";
import { Skeleton, SkeletonText } from "../components/feedback/Skeleton";
import { useDelayedLoading } from "../hooks/useDelayedLoading";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Check, ChevronLeft, ChevronRight, Facebook, Link2, Minus, Plus, QrCode, ShoppingCart, Store, Truck, X } from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { loginPathForCart, useCart } from "../contexts/CartContext";
import { cartErrorMessage, isOwnShop } from "../components/cart/cartFormat";
import { useShopCrumb, useShopPublic } from "../contexts/ShopPublicContext";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { localizedText } from "../services/catalogText";
import { ApiError, mediaUrl, recommendationApi, shopApi, type PublicProduct, type PublicShopCategory, type PublicSku } from "../services/api";
import { shopCategoryPath, shopPath, shopProductPath } from "../services/shopRoutes";
import NotFoundPage from "./NotFoundPage";
import ProductConfiguratorPanel from "../components/shop/ProductConfiguratorPanel";
import ProductQrModal from "../components/shop/ProductQrModal";
import { copyText, productPageUrl, productShareLink } from "../utils/productLinks";
import "../styles/recommended-products.css";
import "../styles/shop-page.css";

type Group = { key: string; label: string; values: string[] };

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

function groupsFor(product: PublicProduct, optionLabel: string): Group[] {
  if (product.optionSchema?.length) {
    return product.optionSchema
      .filter((group) => group.name && group.values?.length)
      .map((group) => ({ key: group.name, label: group.name, values: group.values }));
  }
  const named = new Map<string, string[]>();
  product.skus.forEach((sku) => {
    Object.entries(sku.optionValues || {}).forEach(([key, value]) => {
      if (!value) return;
      const list = named.get(key) || [];
      if (!list.includes(value)) list.push(value);
      named.set(key, list);
    });
  });
  if (named.size > 0) return [...named.entries()].map(([key, values]) => ({ key, label: key, values }));
  const labels = product.skus.map((sku) => sku.optionLabel).filter((value): value is string => Boolean(value));
  const unique = [...new Set(labels)];
  if (product.skus.length > 1 && unique.length > 1) return [{ key: "option", label: optionLabel, values: unique }];
  return [];
}

function skuMatches(sku: PublicSku, groups: Group[], picked: Record<string, string>, ignore?: string) {
  return groups.every((group) => {
    if (group.key === ignore || !picked[group.key]) return true;
    const value = group.key === "option" ? sku.optionLabel : sku.optionValues?.[group.key];
    return value === picked[group.key];
  });
}

/** Shopee-style "เหลือ N ชิ้น" when stock is low. */
const LOW_STOCK = 10;

export default function ShopProductPage() {
  const { slug = "", productSlug = "" } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { shop } = useShopPublic();
  const cart = useCart();
  const own = isOwnShop(user, shop.shopId);
  const [adding, setAdding] = useState(false);
  const { tp, localeTag } = usePublicTranslation();
  const lang = localeTag.startsWith("en") ? "en" : localeTag.startsWith("zh") ? "zh" : "th";
  const [product, setProduct] = useState<PublicProduct | null>(null);
  const [others, setOthers] = useState<PublicProduct[]>([]);
  const [productTotal, setProductTotal] = useState<number | null>(null);
  const [categories, setCategories] = useState<PublicShopCategory[]>([]);
  const [state, setState] = useState<"loading" | "error" | "missing" | "ok">("loading");
  const pageLoading = state === "loading";
  const { showSkeleton, markHasData, isRefetching } = useDelayedLoading(pageLoading, 150, `${slug}/${productSlug}`);
  const [active, setActive] = useState(0);
  const [picked, setPicked] = useState<Record<string, string>>({});
  const [qty, setQty] = useState(1);
  const [warn, setWarn] = useState(false);
  const [toast, setToast] = useState("");
  const [lightbox, setLightbox] = useState(false);
  const thumbsRef = useRef<HTMLDivElement>(null);
  const swipeRef = useRef<HTMLDivElement>(null);
  const [thumbOverflow, setThumbOverflow] = useState(false);

  const load = () => {
    setState("loading");
    setOthers([]);
    setProductTotal(null);
    shopApi.product(slug, productSlug)
      .then((data) => {
        setProduct(data);
        setActive(Math.max(0, data.images.findIndex((image) => image.primary)));
        setPicked({});
        setQty(1);
        setWarn(false);
        setState("ok");
      })
      .catch((error) => setState(error instanceof ApiError && error.status === 404 ? "missing" : "error"));
    shopApi.products(slug, { size: 12, sort: "newest" }).then((page) => {
      setProductTotal(page.total);
      setOthers(page.items.filter((item) => item.slug !== productSlug).slice(0, 6));
    }).catch(() => {
      setProductTotal(null);
      setOthers([]);
    });
    shopApi.categories(slug).then(setCategories).catch(() => setCategories([]));
  };

  useEffect(() => { load(); }, [slug, productSlug]);

  // Record a product view for recommendations / recently viewed (fire-and-forget, never blocks the page).
  useEffect(() => { if (product?.id) recommendationApi.recordView(product.id).catch(() => {}); }, [product?.id]);

  useEffect(() => {
    if (state === "ok" && product) markHasData();
  }, [state, product, markHasData]);

  const notify = (text: string) => {
    setToast(text);
    window.setTimeout(() => setToast(""), 1800);
  };

  const groups = useMemo(() => (product ? groupsFor(product, tp("shop.view.option")) : []), [product, tp]);
  const complete = groups.every((group) => picked[group.key]);
  const selectedSku = product
    ? groups.length === 0
      ? product.skus[0]
      : complete
        ? product.skus.find((sku) => groups.every((group) => (group.key === "option" ? sku.optionLabel : sku.optionValues?.[group.key]) === picked[group.key]))
        : undefined
    : undefined;
  useEffect(() => {
    if (!product || !selectedSku?.imageId) return;
    const index = product.images.findIndex((image) => image.id === selectedSku.imageId);
    if (index >= 0) setActive(index);
  }, [product, selectedSku?.imageId]);
  const stock = selectedSku ? Math.max(0, selectedSku.available) : 0;
  const soldOut = Boolean(selectedSku) && stock <= 0;
  const ready = Boolean(selectedSku) && stock > 0;

  useEffect(() => {
    if (stock > 0 && qty > stock) setQty(stock);
  }, [stock, qty]);
  useEffect(() => {
    const node = thumbsRef.current;
    if (!node) return;
    const measure = () => setThumbOverflow(node.scrollWidth > node.clientWidth + 4);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(node);
    return () => observer.disconnect();
  }, [product, state]);

  const prices = (product?.skus || []).map((sku) => amount(sku.price)).filter((value): value is number => value != null);
  const low = prices.length ? Math.min(...prices) : amount(product?.basePrice);
  const high = prices.length ? Math.max(...prices) : low;
  const currentPrice = selectedSku ? amount(selectedSku.price) : prices.length === 1 ? prices[0] : null;
  const compare = selectedSku ? amount(selectedSku.compareAtPrice) ?? amount(product?.compareAtPrice) : prices.length <= 1 ? amount(product?.compareAtPrice) : null;
  const discount = currentPrice != null && compare != null && compare > currentPrice ? Math.round((1 - currentPrice / compare) * 100) : 0;

  /** Clean product page URL (no query) for QR / fallback sharing. */
  const pageUrl = product ? productPageUrl(slug, product.slug) : window.location.href.split(/[?#]/)[0];
  /** Facebook / LINE need the backend OG page (/share/p/...) on a public domain; on localhost use the page URL. */
  const shareUrl = product ? productShareLink(slug, product.slug) : pageUrl;
  const [qrOpen, setQrOpen] = useState(false);
  const share = (kind: "facebook" | "line" | "x") => {
    const url = encodeURIComponent(shareUrl);
    const target = kind === "facebook"
      ? `https://www.facebook.com/sharer/sharer.php?u=${url}`
      : kind === "line"
        ? `https://social-plugins.line.me/lineit/share?url=${url}`
        : `https://twitter.com/intent/tweet?url=${url}`;
    window.open(target, "_blank", "noopener,noreferrer");
  };
  const copyLink = async () => {
    const ok = await copyText(shareUrl);
    notify(ok ? tp("shop.view.copied") : tp("shop.productError"));
  };
  /** Adds the chosen SKU through the cart API. Returns true when at least 1 piece was added. */
  const placeInCart = async (configuration?: { selections: Record<string, unknown>; version?: number }, now = false) => {
    // guests sign in first; admins blocked; merchants may buy other shops but not their own
    if (cart.access === "guest") { notify(tp("cart.loginToAdd")); window.location.assign(loginPathForCart()); return false; }
    if (cart.access === "blocked") { notify(tp("cart.blocked.toast")); return false; }
    if (own) { notify(tp("cart.own.toast")); return false; }
    if (cart.access === "loading") return false;
    if (!product || !selectedSku || !complete) { setWarn(true); return false; }
    if (!ready || qty < 1 || qty > stock) {
      notify(stock > 0 ? tp("cart.errors.stockLeft", { count: stock }) : tp("cart.errors.stockNone"));
      return false;
    }
    setAdding(true);
    try {
      const result = now
        ? await cart.buyNow(selectedSku.id, qty, configuration)
        : configuration ? await cart.addConfigured(selectedSku.id, qty, configuration) : await cart.add(selectedSku.id, qty);
      if (result?.adjusted) {
        if (result.appliedQuantity < 1) { notify(tp("cart.errors.stockLeftInCart", { count: stock, inCart: stock })); return false; }
        notify(tp("cart.quantityAdjusted", { count: result.appliedQuantity }));
      }
      return true;
    } catch (error) {
      notify(cartErrorMessage(error, tp));
      return false;
    } finally {
      setAdding(false);
    }
  };
  const addToCart = async () => {
    if (adding) return;
    if (await placeInCart()) notify(tp("shop.view.added"));
  };
  const buyNow = async () => {
    if (adding) return;
    if (await placeInCart(undefined, true)) navigate("/checkout");
  };
  // QR "checkout" links carry ?buy=1: run Buy Now once when the page is ready (guests are sent to login and come back here)
  const autoBuy = useRef(new URLSearchParams(window.location.search).get("buy") === "1");
  useEffect(() => {
    if (!autoBuy.current || !product || cart.access === "loading") return;
    autoBuy.current = false;
    void buyNow();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [product, cart.access, selectedSku]);

  const name = product ? localizedText(localeTag, product.nameTh, product.nameEn, product.nameZh) : "";
  const description = product ? localizedText(localeTag, product.descriptionTh, product.descriptionEn, product.descriptionZh) : "";
  const image = product?.images[active] || product?.images[0];
  const category = categories.find((item) => item.id === product?.categoryId);
  const categoryLabel = category
    ? localizedText(localeTag, category.nameTh, category.nameEn, category.nameZh) || category.nameTh
    : "";
  const shopName = lang === "en" && shop.nameEn ? shop.nameEn : shop.name;
  useShopCrumb(name || null);
  useDocumentMeta({ title: name ? `${name} · ${shopName}` : null, description: description || null });
  const stats = [
    productTotal != null ? [tp("shop.view.productCount"), String(productTotal)] : null,
    shop.category ? [tp("shop.view.category"), shop.category] : null,
    shop.province ? [tp("common.area"), shop.province] : null,
  ].filter((item): item is [string, string] => Boolean(item));

  const scrollThumbs = (direction: number) => thumbsRef.current?.scrollBy({ left: direction * 180, behavior: "smooth" });
  const valueEnabled = (group: Group, value: string) => product?.skus.some((sku) => {
    const own = group.key === "option" ? sku.optionLabel : sku.optionValues?.[group.key];
    return own === value && sku.available > 0 && skuMatches(sku, groups, picked, group.key);
  });

  if (state === "missing") {
    return <NotFoundPage kind="product" bare />;
  }

  return (
    <div className="buyer-product">
      {showSkeleton && pageLoading && !product && (
        <section className="recommend-width buyer-skeleton" aria-label={tp("shop.productLoading")}>
          <article className="buyer-card buyer-hero">
            <Skeleton className="buyer-main" height={360} width="100%" radius={8} />
            <div>
              <SkeletonText lines={2} />
              <Skeleton height={40} width="55%" />
              <Skeleton height={120} width="100%" />
            </div>
          </article>
        </section>
      )}
      {state === "error" && (
        <section className="recommend-width">
          <EmptyState variant="error" title={tp("shop.productError")} action={{ label: tp("shop.productsRetry"), onClick: load }} />
        </section>
      )}
      {product && (state === "ok" || isRefetching) && (
        <div className={`recommend-width ${isRefetching ? "skeleton-busy is-refetching" : ""}`}>
          <article className="buyer-card buyer-hero">
            <div className="buyer-gallery">
              <div className="buyer-stage">
                {image ? (
                  <button type="button" className="buyer-main" onClick={() => setLightbox(true)}>
                    <img src={mediaUrl(image.url)} alt={name} />
                  </button>
                ) : <div className="buyer-main buyer-empty">{tp("shop.view.noImage")}</div>}
                <div className="buyer-swipe" ref={swipeRef} onScroll={(event) => {
                  const node = event.currentTarget;
                  const next = Math.round(node.scrollLeft / Math.max(node.clientWidth, 1));
                  if (product.images[next]) setActive(next);
                }}>
                  {product.images.map((item) => <img key={item.id} src={mediaUrl(item.url)} alt="" />)}
                </div>
                {product.images.length > 0 ? <span className="buyer-count">{tp("shop.view.imageIndex", { index: active + 1, total: product.images.length })}</span> : null}
              </div>
              {product.images.length > 0 && (
                <div className="buyer-thumbs">
                  {thumbOverflow ? <button type="button" aria-label={tp("shop.view.prevImage")} onClick={() => scrollThumbs(-1)}><ChevronLeft /></button> : null}
                  <div ref={thumbsRef}>
                    {product.images.map((item, index) => (
                      <button type="button" key={item.id} className={index === active ? "active" : ""} onMouseEnter={() => setActive(index)} onClick={() => setActive(index)}>
                        <img src={mediaUrl(item.url)} alt="" />
                      </button>
                    ))}
                  </div>
                  {thumbOverflow ? <button type="button" aria-label={tp("shop.view.nextImage")} onClick={() => scrollThumbs(1)}><ChevronRight /></button> : null}
                </div>
              )}
              <div className="buyer-share">
                <span>{tp("shop.view.share")}</span>
                <button type="button" aria-label={tp("shop.view.copyLink")} onClick={() => void copyLink()}><Link2 /></button>
                <button type="button" aria-label={tp("shop.view.shareFacebook")} onClick={() => share("facebook")}><Facebook /></button>
                <button type="button" aria-label={tp("shop.view.shareLine")} onClick={() => share("line")}>{tp("shop.view.line")}</button>
                <button type="button" aria-label={tp("shop.view.shareX")} onClick={() => share("x")}>X</button>
                <button type="button" aria-label={tp("shop.view.qr")} onClick={() => setQrOpen(true)}><QrCode /></button>
              </div>
            </div>
            <div className="buyer-info">
              <h1>{name}</h1>
              <div className="buyer-price">
                <b className="num">{currentPrice != null ? money(currentPrice, localeTag) : low != null && high != null && low !== high ? `${money(low, localeTag)} – ${money(high, localeTag)}` : low != null ? money(low, localeTag) : "—"}</b>
                {discount > 0 && compare != null ? <><s className="num">{money(compare, localeTag)}</s><em className="num">{tp("shop.view.discount", { percent: discount })}</em></> : null}
              </div>
              <dl>
                <div><dt>{tp("shop.view.shipping")}</dt><dd><Truck />{tp("shop.view.shippingNote")}</dd></div>
                {groups.map((group) => (
                  <div key={group.key}>
                    <dt>{group.label}</dt>
                    <dd className="buyer-chips">
                      {group.values.map((value) => {
                        const enabled = valueEnabled(group, value);
                        const on = picked[group.key] === value;
                        return (
                          <button type="button" key={value} className={on ? "active" : ""} disabled={!enabled} onClick={() => { setPicked({ ...picked, [group.key]: value }); setWarn(false); setQty(1); }}>
                            {on ? <Check /> : null}{value}
                          </button>
                        );
                      })}
                    </dd>
                  </div>
                ))}
                {warn ? <p className="buyer-warn">{tp("shop.view.pickOptions")}</p> : null}
                <div>
                  <dt>{tp("shop.view.quantity")}</dt>
                  <dd className="buyer-qty">
                    <div>
                      <button type="button" aria-label="-" disabled={!ready || qty <= 1} onClick={() => setQty((value) => Math.max(1, value - 1))}><Minus /></button>
                      <input className="num" inputMode="numeric" value={ready ? qty : 0} onChange={(event) => {
                        const next = Number(event.target.value);
                        if (!Number.isFinite(next)) return;
                        setQty(Math.min(stock, Math.max(1, Math.trunc(next))));
                      }} />
                      <button type="button" aria-label="+" disabled={!ready || qty >= stock} onClick={() => setQty((value) => Math.min(stock, value + 1))}><Plus /></button>
                    </div>
                    <span>{complete && stock <= 0 ? tp("shop.view.soldOut") : selectedSku ? (stock <= LOW_STOCK ? tp("shop.view.lowStock", { count: stock }) : tp("shop.view.inStock", { count: stock })) : null}</span>
                  </dd>
                </div>
              </dl>
              <div className="buyer-actions">
                <button type="button" className="cart" disabled={soldOut || adding || own} onClick={() => void addToCart()}><ShoppingCart />{tp("shop.view.addToCart")}</button>
                <button type="button" className="buy" disabled={soldOut || adding || own} onClick={() => void buyNow()}>{tp("shop.view.buyNow")}</button>
              </div>
              {own ? <p className="buyer-warn">{tp("cart.own.label")}</p> : null}
              {product ? <ProductConfiguratorPanel shop={shop} productId={product.id} money={(value) => money(value, localeTag)}
                onAddToCart={async ({ selections, version }) => {
                  if (adding) return false;
                  const ok = await placeInCart({ selections, version });
                  if (ok) notify(tp("shop.view.added"));
                  return ok;
                }}
                onBuyNow={async ({ selections, version }) => {
                  if (adding) return false;
                  const ok = await placeInCart({ selections, version }, true);
                  if (ok) navigate("/checkout");
                  return ok;
                }} /> : null}
              {qrOpen && product ? <ProductQrModal productUrl={pageUrl} name={name} onClose={() => setQrOpen(false)} /> : null}
            </div>
          </article>
          <section className="buyer-card buyer-shop">
            <div className="buyer-shop-id">
              <span className="buyer-shop-logo">{shop.logoUrl ? <img src={mediaUrl(shop.logoUrl)} alt={tp("shop.view.logoAlt", { name: shopName })} /> : <Store />}</span>
              <div>
                <b>{shopName}</b>
                <Link to={shopPath(slug)}>{tp("shop.view.viewShop")}</Link>
              </div>
            </div>
            {stats.length > 0 && (
              <dl className="buyer-shop-stats" style={{ "--shop-stats": stats.length } as CSSProperties}>
                {stats.map(([label, value]) => <div key={label}><dt>{label}</dt><dd className={/^\d/.test(value) ? "num" : undefined}>{value}</dd></div>)}
              </dl>
            )}
          </section>
          <section className="buyer-card buyer-detail">
            <h2>{tp("shop.view.specs")}</h2>
            <dl>
              {categoryLabel && category ? (
                <div>
                  <dt>{tp("shop.view.category")}</dt>
                  <dd><Link to={shopCategoryPath(slug, category)}>{categoryLabel}</Link></dd>
                </div>
              ) : null}
              {selectedSku ? <div><dt>{tp("shop.view.sku")}</dt><dd className="num">{selectedSku.skuCode}</dd></div> : null}
              {product.brand ? <div><dt>{tp("shop.productBrand")}</dt><dd>{product.brand}</dd></div> : null}
            </dl>
            {description ? <><h2>{tp("shop.view.details")}</h2><p>{description}</p></> : null}
          </section>
          {others.length > 0 && (
            <section className="buyer-more">
              <h2>{tp("shop.view.more")}</h2>
              <div>
                {others.map((item) => {
                  const photo = item.images.find((imageItem) => imageItem.primary) || item.images[0];
                  const title = localizedText(localeTag, item.nameTh, item.nameEn, item.nameZh);
                  const price = amount(item.basePrice) ?? amount(item.skus[0]?.price);
                  return (
                    <Link key={item.id} to={shopProductPath(slug, item.slug)}>
                      {photo ? <img src={mediaUrl(photo.url)} alt="" /> : <span />}
                      <b>{title}</b>
                      {price != null ? <strong className="num">{money(price, localeTag)}</strong> : null}
                    </Link>
                  );
                })}
              </div>
            </section>
          )}
        </div>
      )}
      {lightbox && image && (
        <div className="buyer-lightbox" role="dialog" aria-modal="true">
          <button type="button" className="close" aria-label={tp("shop.view.close")} onClick={() => setLightbox(false)}><X /></button>
          {product && product.images.length > 1 ? <button type="button" aria-label={tp("shop.view.prevImage")} onClick={() => setActive((index) => (index - 1 + product.images.length) % product.images.length)}><ChevronLeft /></button> : null}
          <img src={mediaUrl(image.url)} alt={name} />
          {product && product.images.length > 1 ? <button type="button" aria-label={tp("shop.view.nextImage")} onClick={() => setActive((index) => (index + 1) % product.images.length)}><ChevronRight /></button> : null}
        </div>
      )}
      {toast ? <div className="buyer-toast" role="status">{toast}</div> : null}
    </div>
  );
}
