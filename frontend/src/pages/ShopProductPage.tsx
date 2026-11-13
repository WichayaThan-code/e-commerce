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
import StarRating from "../components/reviews/StarRating";
import { useShopCrumb, useShopPublic } from "../contexts/ShopPublicContext";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { localizedText } from "../services/catalogText";
import {
  ApiError,
  mediaUrl,
  recommendationApi,
  reviewApi,
  shopApi,
  type ProductReview,
  type ReviewFilterCounts,
  type PublicProduct,
  type PublicShopCategory,
  type PublicSku,
} from "../services/api";
import { shopCategoryPath, shopPath, shopProductPath } from "../services/shopRoutes";
import NotFoundPage from "./NotFoundPage";
import ProductDescriptionHtml, { productDescriptionPlainText } from "../components/merchant/ProductDescriptionHtml";
import ProductConfiguratorPanel from "../components/shop/ProductConfiguratorPanel";
import ProductImagePreview, { buildProductPreviewItems } from "../components/shop/ProductImagePreview";
import ProductQrModal from "../components/shop/ProductQrModal";
import { copyText, productPageUrl, productShareLink } from "../utils/productLinks";
import "../styles/recommended-products.css";
import "../styles/shop-page.css";

const REVIEW_PAGE_SIZE = 10;
type ReviewFilter = "all" | "photos" | 1 | 2 | 3 | 4 | 5;

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
/** Max product thumbs visible before requiring left/right scroll. */
const THUMB_VISIBLE = 5;

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
  const [previewCurrent, setPreviewCurrent] = useState(0);
  const mainImageRef = useRef<HTMLButtonElement>(null);
  const thumbsRef = useRef<HTMLDivElement>(null);
  const swipeRef = useRef<HTMLDivElement>(null);
  const [thumbOverflow, setThumbOverflow] = useState(false);
  const [thumbCanPrev, setThumbCanPrev] = useState(false);
  const [thumbCanNext, setThumbCanNext] = useState(false);
  const [reviews, setReviews] = useState<ProductReview[]>([]);
  const [reviewAvg, setReviewAvg] = useState<number | null>(null);
  const [reviewCount, setReviewCount] = useState(0);
  const [reviewFilteredTotal, setReviewFilteredTotal] = useState(0);
  const [reviewFilters, setReviewFilters] = useState<ReviewFilterCounts | null>(null);
  const [reviewFilter, setReviewFilter] = useState<ReviewFilter>("all");
  const [reviewPage, setReviewPage] = useState(0);
  const [reviewsLoading, setReviewsLoading] = useState(false);
  const [reviewsLoadingMore, setReviewsLoadingMore] = useState(false);
  const [reviewLightbox, setReviewLightbox] = useState<string | null>(null);

  const reviewListQuery = (page: number) => {
    const base = { shopSlug: slug, page, size: REVIEW_PAGE_SIZE };
    if (reviewFilter === "photos") return { ...base, withPhotos: true as const };
    if (typeof reviewFilter === "number") return { ...base, rating: reviewFilter };
    return base;
  };

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
    if (!product?.slug) {
      setReviews([]);
      setReviewAvg(null);
      setReviewCount(0);
      setReviewFilteredTotal(0);
      setReviewFilters(null);
      setReviewPage(0);
      setReviewFilter("all");
      return;
    }
    let cancelled = false;
    setReviewsLoading(true);
    reviewApi.list(product.slug, reviewListQuery(0))
      .then((page) => {
        if (cancelled) return;
        setReviews(page.items ?? []);
        setReviewAvg(page.ratingAvg != null ? Number(page.ratingAvg) : null);
        setReviewCount(page.reviewCount ?? 0);
        setReviewFilteredTotal(page.total ?? page.items?.length ?? 0);
        setReviewFilters(page.filters ?? null);
        setReviewPage(page.page ?? 0);
      })
      .catch(() => {
        if (cancelled) return;
        setReviews([]);
        setReviewAvg(null);
        setReviewCount(0);
        setReviewFilteredTotal(0);
        setReviewFilters(null);
        setReviewPage(0);
      })
      .finally(() => { if (!cancelled) setReviewsLoading(false); });
    return () => { cancelled = true; };
  }, [product?.slug, slug, reviewFilter]);

  useEffect(() => {
    if (state === "ok" && product) markHasData();
  }, [state, product, markHasData]);

  const loadMoreReviews = async () => {
    if (!product?.slug || reviewsLoadingMore || reviews.length >= reviewFilteredTotal) return;
    setReviewsLoadingMore(true);
    try {
      const nextPage = reviewPage + 1;
      const page = await reviewApi.list(product.slug, reviewListQuery(nextPage));
      setReviews((prev) => [...prev, ...(page.items ?? [])]);
      setReviewPage(page.page ?? nextPage);
      setReviewAvg(page.ratingAvg != null ? Number(page.ratingAvg) : reviewAvg);
      setReviewCount(page.reviewCount ?? reviewCount);
      setReviewFilteredTotal(page.total ?? reviewFilteredTotal);
      if (page.filters) setReviewFilters(page.filters);
    } catch {
      /* keep current list */
    } finally {
      setReviewsLoadingMore(false);
    }
  };

  const filterCount = (filter: ReviewFilter) => {
    if (!reviewFilters) return 0;
    if (filter === "all") return reviewFilters.all;
    if (filter === "photos") return reviewFilters.withPhotos;
    return reviewFilters[`star${filter}` as keyof ReviewFilterCounts] as number;
  };

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
  const scrollThumbIntoViewIfNeeded = (index: number) => {
    const node = thumbsRef.current;
    if (!node) return;
    const btn = node.children[index] as HTMLElement | undefined;
    if (!btn) return;
    const btnLeft = btn.offsetLeft;
    const btnRight = btnLeft + btn.offsetWidth;
    const viewLeft = node.scrollLeft;
    const viewRight = viewLeft + node.clientWidth;
    // Only nudge when out of view — never re-center (avoids hover jump after nav scroll).
    if (btnLeft < viewLeft) node.scrollTo({ left: btnLeft, behavior: "smooth" });
    else if (btnRight > viewRight) node.scrollTo({ left: btnRight - node.clientWidth, behavior: "smooth" });
  };

  useEffect(() => {
    if (!product || !selectedSku?.imageId) return;
    const index = product.images.findIndex((image) => image.id === selectedSku.imageId);
    if (index >= 0) {
      setActive(index);
      scrollThumbIntoViewIfNeeded(index);
    }
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
    const measure = () => {
      const max = Math.max(0, node.scrollWidth - node.clientWidth);
      const overflow = max > 4;
      setThumbOverflow(overflow);
      setThumbCanPrev(overflow && node.scrollLeft > 2);
      setThumbCanNext(overflow && node.scrollLeft < max - 2);
    };
    measure();
    const onScroll = () => measure();
    node.addEventListener("scroll", onScroll, { passive: true });
    const observer = new ResizeObserver(measure);
    observer.observe(node);
    return () => {
      node.removeEventListener("scroll", onScroll);
      observer.disconnect();
    };
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
  const previewItems = useMemo(() => (product ? buildProductPreviewItems(product) : []), [product]);
  const openPreviewAt = (productImageIndex: number) => {
    const mapped = previewItems.findIndex((item) => item.productIndex === productImageIndex);
    setPreviewCurrent(mapped >= 0 ? mapped : Math.max(0, Math.min(productImageIndex, previewItems.length - 1)));
    setLightbox(true);
  };
  const onPreviewCurrentChange = (index: number) => {
    setPreviewCurrent(index);
    const productIndex = previewItems[index]?.productIndex;
    if (productIndex == null) return;
    setActive(productIndex);
    scrollThumbIntoViewIfNeeded(productIndex);
  };
  const category = categories.find((item) => item.id === product?.categoryId);
  const categoryLabel = category
    ? localizedText(localeTag, category.nameTh, category.nameEn, category.nameZh) || category.nameTh
    : "";
  const shopName = lang === "en" && shop.nameEn ? shop.nameEn : shop.name;
  useShopCrumb(name || null);
  useDocumentMeta({
    title: name ? `${name} · ${shopName}` : null,
    description: description ? productDescriptionPlainText(description) || null : null,
  });
  const stats = [
    productTotal != null ? [tp("shop.view.productCount"), String(productTotal)] : null,
    shop.category ? [tp("shop.view.category"), shop.category] : null,
    shop.province ? [tp("common.area"), shop.province] : null,
  ].filter((item): item is [string, string] => Boolean(item));

  const scrollThumbs = (direction: number) => {
    const node = thumbsRef.current;
    if (!node) return;
    // Advance by one visible "page" (up to 5 thumbs), falling back to one thumb slot.
    const step = Math.max(node.clientWidth, 64);
    node.scrollBy({ left: direction * step, behavior: "smooth" });
  };
  const showThumbNav = Boolean(product && product.images.length > THUMB_VISIBLE) || thumbOverflow;
  const valueEnabled = (group: Group, value: string) => product?.skus.some((sku) => {
    const own = group.key === "option" ? sku.optionLabel : sku.optionValues?.[group.key];
    return own === value && sku.available > 0 && skuMatches(sku, groups, picked, group.key);
  });

  if (state === "missing") {
    return <NotFoundPage kind="product" bare />;
  }

  return (
    <div className="buyer-product" data-page="product">
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
                  <button
                    ref={mainImageRef}
                    type="button"
                    className="buyer-main"
                    onClick={() => openPreviewAt(active)}
                  >
                    <img src={mediaUrl(image.url)} alt={name} />
                  </button>
                ) : <div className="buyer-main buyer-empty">{tp("shop.view.noImage")}</div>}
                <div className="buyer-swipe" ref={swipeRef} onScroll={(event) => {
                  const node = event.currentTarget;
                  const next = Math.round(node.scrollLeft / Math.max(node.clientWidth, 1));
                  if (product.images[next]) setActive(next);
                }}>
                  {product.images.map((item, index) => (
                    <img
                      key={item.id}
                      src={mediaUrl(item.url)}
                      alt=""
                      onClick={() => openPreviewAt(index)}
                    />
                  ))}
                </div>
                {product.images.length > 0 ? <span className="buyer-count">{tp("shop.view.imageIndex", { index: active + 1, total: product.images.length })}</span> : null}
              </div>
              {product.images.length > 0 && (
                <div className={`buyer-thumbs${showThumbNav ? " has-nav" : ""}`}>
                  {showThumbNav ? (
                    <button type="button" className="buyer-thumbs-nav" aria-label={tp("shop.view.prevImage")} disabled={!thumbCanPrev} onClick={() => scrollThumbs(-1)}>
                      <ChevronLeft aria-hidden="true" />
                    </button>
                  ) : null}
                  <div ref={thumbsRef} className="buyer-thumbs-track">
                    {product.images.map((item, index) => (
                      <button
                        type="button"
                        key={item.id}
                        className={index === active ? "active" : ""}
                        onMouseEnter={() => setActive(index)}
                        onClick={() => {
                          setActive(index);
                          scrollThumbIntoViewIfNeeded(index);
                        }}
                      >
                        <img src={mediaUrl(item.url)} alt="" />
                      </button>
                    ))}
                  </div>
                  {showThumbNav ? (
                    <button type="button" className="buyer-thumbs-nav" aria-label={tp("shop.view.nextImage")} disabled={!thumbCanNext} onClick={() => scrollThumbs(1)}>
                      <ChevronRight aria-hidden="true" />
                    </button>
                  ) : null}
                </div>
              )}
            </div>
            <div className="buyer-info">
              <h1>{name}</h1>
              <div className="buyer-rating-row">
                {reviewsLoading ? (
                  <span className="is-muted">{tp("shop.view.ratingLoading")}</span>
                ) : reviewCount > 0 && reviewAvg != null ? (
                  <>
                    <StarRating value={reviewAvg} readOnly size={18} ariaLabel={tp("shop.view.rating")} />
                    <b className="num">{reviewAvg.toFixed(1)}</b>
                    <span>({tp("shop.view.reviewCount", { count: reviewCount })})</span>
                  </>
                ) : (
                  <span className="is-muted">{tp("shop.view.noReviews")}</span>
                )}
              </div>
              <div className="buyer-price">
                <b className="num">{currentPrice != null ? money(currentPrice, localeTag) : low != null && high != null && low !== high ? `${money(low, localeTag)} – ${money(high, localeTag)}` : low != null ? money(low, localeTag) : "—"}</b>
                {discount > 0 && compare != null ? <><s className="num">{money(compare, localeTag)}</s><em className="num">{tp("shop.view.discount", { percent: discount })}</em></> : null}
              </div>
              <dl>
                <div><dt>{tp("shop.view.shipping")}</dt><dd><Truck aria-hidden="true" />{tp("shop.view.shippingNote")}</dd></div>
                {groups.map((group) => (
                  <div key={group.key}>
                    <dt>{group.label}</dt>
                    <dd className="buyer-chips">
                      {group.values.map((value) => {
                        const enabled = valueEnabled(group, value);
                        const on = picked[group.key] === value;
                        return (
                          <button type="button" key={value} className={on ? "active" : ""} disabled={!enabled} onClick={() => { setPicked({ ...picked, [group.key]: value }); setWarn(false); setQty(1); }}>
                            {on ? <Check aria-hidden="true" /> : null}{value}
                          </button>
                        );
                      })}
                    </dd>
                  </div>
                ))}
                {warn ? <p className="buyer-warn" role="alert">{tp("shop.view.pickOptions")}</p> : null}
                <div>
                  <dt>{tp("shop.view.quantity")}</dt>
                  <dd className="buyer-qty">
                    <div>
                      <button type="button" aria-label="-" disabled={!ready || qty <= 1} onClick={() => setQty((value) => Math.max(1, value - 1))}><Minus aria-hidden="true" /></button>
                      <input className="num" inputMode="numeric" value={ready ? qty : 0} onChange={(event) => {
                        const next = Number(event.target.value);
                        if (!Number.isFinite(next)) return;
                        setQty(Math.min(stock, Math.max(1, Math.trunc(next))));
                      }} />
                      <button type="button" aria-label="+" disabled={!ready || qty >= stock} onClick={() => setQty((value) => Math.min(stock, value + 1))}><Plus aria-hidden="true" /></button>
                    </div>
                    <span>{complete && stock <= 0 ? tp("shop.view.soldOut") : selectedSku ? (stock <= LOW_STOCK ? tp("shop.view.lowStock", { count: stock }) : tp("shop.view.inStock", { count: stock })) : null}</span>
                  </dd>
                </div>
              </dl>
              <div className="buyer-actions">
                <button type="button" className="cart" disabled={soldOut || adding || own} onClick={() => void addToCart()}><ShoppingCart aria-hidden="true" />{tp("shop.view.addToCart")}</button>
                <button type="button" className="buy" disabled={soldOut || adding || own} onClick={() => void buyNow()}>{tp("shop.view.buyNow")}</button>
              </div>
              <div className="buyer-share">
                <span>{tp("shop.view.share")}</span>
                <button type="button" aria-label={tp("shop.view.copyLink")} onClick={() => void copyLink()}><Link2 aria-hidden="true" /></button>
                <button type="button" aria-label={tp("shop.view.shareFacebook")} onClick={() => share("facebook")}><Facebook aria-hidden="true" /></button>
                <button type="button" aria-label={tp("shop.view.shareLine")} onClick={() => share("line")}>{tp("shop.view.line")}</button>
                <button type="button" aria-label={tp("shop.view.shareX")} onClick={() => share("x")}>X</button>
                <button type="button" aria-label={tp("shop.view.qr")} onClick={() => setQrOpen(true)}><QrCode aria-hidden="true" /></button>
              </div>
              {own ? <p className="buyer-warn" role="alert">{tp("cart.own.label")}</p> : null}
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
              <span className="buyer-shop-logo">{shop.logoUrl ? <img src={mediaUrl(shop.logoUrl)} alt={tp("shop.view.logoAlt", { name: shopName })} /> : <Store aria-hidden="true" />}</span>
              <div className="buyer-shop-meta">
                <b>{shopName}</b>
              </div>
              <Link className="buyer-shop-follow" to={shopPath(slug)}>{tp("shop.view.viewShop")}</Link>
            </div>
            {stats.length > 0 && (
              <dl className="buyer-shop-stats" style={{ "--shop-stats": stats.length } as CSSProperties}>
                {stats.map(([label, value]) => <div key={label}><dt>{label}</dt><dd className={/^\d/.test(value) ? "num" : undefined}>{value}</dd></div>)}
              </dl>
            )}
          </section>
          <section className="buyer-card buyer-detail">
            <div className="buyer-detail-desc">
              {description ? (
                <>
                  <h2>{tp("shop.view.details")}</h2>
                  <ProductDescriptionHtml value={description} className="buyer-detail-rich" />
                </>
              ) : null}
            </div>
            <div className="buyer-detail-specs">
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
            </div>
          </section>
          <section className="buyer-card buyer-reviews" aria-label={tp("shop.view.reviewsTitle")}>
            <div className="buyer-reviews-head">
              <div>
                <h2>{tp("shop.view.reviewsTitle")}</h2>
                {reviewCount > 0 ? (
                  <p className="buyer-reviews-count">{tp("shop.view.reviewCount", { count: reviewCount })}</p>
                ) : null}
              </div>
              {reviewCount > 0 && reviewAvg != null ? (
                <div className="buyer-reviews-summary">
                  <strong className="num">{reviewAvg.toFixed(1)}</strong>
                  <div>
                    <StarRating value={reviewAvg} readOnly size={18} ariaLabel={tp("shop.view.rating")} />
                    <span>{tp("shop.view.reviewCount", { count: reviewCount })}</span>
                  </div>
                </div>
              ) : null}
            </div>
            {reviewCount > 0 ? (
              <div className="buyer-review-filters" role="toolbar" aria-label={tp("shop.view.reviewFilters")}>
                {([
                  { key: "all" as const, label: tp("shop.view.reviewFilterAll") },
                  { key: "photos" as const, label: tp("shop.view.reviewFilterPhotos") },
                  { key: 5 as const, label: tp("shop.view.reviewFilterStars", { stars: 5 }) },
                  { key: 4 as const, label: tp("shop.view.reviewFilterStars", { stars: 4 }) },
                  { key: 3 as const, label: tp("shop.view.reviewFilterStars", { stars: 3 }) },
                  { key: 2 as const, label: tp("shop.view.reviewFilterStars", { stars: 2 }) },
                  { key: 1 as const, label: tp("shop.view.reviewFilterStars", { stars: 1 }) },
                ]).map((chip) => {
                  const count = filterCount(chip.key);
                  const active = reviewFilter === chip.key;
                  return (
                    <button
                      key={String(chip.key)}
                      type="button"
                      className={`buyer-review-filter${active ? " is-active" : ""}`}
                      aria-pressed={active}
                      disabled={chip.key !== "all" && count === 0}
                      onClick={() => setReviewFilter(chip.key)}
                    >
                      <span>{chip.label}</span>
                      <em className="num">{count}</em>
                    </button>
                  );
                })}
              </div>
            ) : null}
            {reviewsLoading ? (
              <p className="buyer-reviews-empty">{tp("shop.view.ratingLoading")}</p>
            ) : reviews.length === 0 ? (
              <p className="buyer-reviews-empty">
                {reviewCount > 0 ? tp("shop.view.noFilteredReviews") : tp("shop.view.noReviews")}
              </p>
            ) : (
              <>
                <div className="buyer-review-list">
                  {reviews.map((review) => {
                    const initial = (review.reviewerName || "?").trim().charAt(0);
                    const images = review.images ?? [];
                    return (
                      <article className="buyer-review-item" key={review.id}>
                        <span className="buyer-review-avatar" aria-hidden="true">{initial}</span>
                        <div className="buyer-review-body">
                          <div className="buyer-review-meta">
                            <b>{review.reviewerName}</b>
                            <time dateTime={review.createdAt}>
                              {new Date(review.createdAt).toLocaleDateString(
                                localeTag.startsWith("en") ? "en-US" : localeTag.startsWith("zh") ? "zh-CN" : "th-TH",
                              )}
                            </time>
                          </div>
                          <div className="buyer-review-stars">
                            <StarRating value={review.rating} readOnly size={15} ariaLabel={tp("shop.view.rating")} />
                          </div>
                          {review.optionLabel ? (
                            <p className="buyer-review-option">{tp("shop.view.reviewOption", { option: review.optionLabel })}</p>
                          ) : null}
                          {review.comment ? <p>{review.comment}</p> : null}
                          {images.length > 0 ? (
                            <div className="buyer-review-photos">
                              {images.map((image) => (
                                <button
                                  key={image.id}
                                  type="button"
                                  className="buyer-review-photo"
                                  onClick={() => setReviewLightbox(image.url)}
                                  aria-label={tp("shop.view.reviewPhotoOpen")}
                                >
                                  <img src={mediaUrl(image.url)} alt="" loading="lazy" />
                                </button>
                              ))}
                            </div>
                          ) : null}
                        </div>
                      </article>
                    );
                  })}
                </div>
                {reviews.length < reviewFilteredTotal ? (
                  <button type="button" className="buyer-reviews-more" disabled={reviewsLoadingMore} onClick={() => void loadMoreReviews()}>
                    {reviewsLoadingMore ? tp("shop.view.ratingLoading") : tp("shop.view.loadMoreReviews")}
                  </button>
                ) : null}
              </>
            )}
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
      {previewItems.length > 0 ? (
        <ProductImagePreview
          open={lightbox}
          current={previewCurrent}
          items={previewItems}
          alt={name}
          onOpenChange={setLightbox}
          onCurrentChange={onPreviewCurrentChange}
          returnFocusRef={mainImageRef}
        />
      ) : null}
      {reviewLightbox ? (
        <div className="buyer-lightbox" role="dialog" aria-modal="true" onClick={() => setReviewLightbox(null)}>
          <button type="button" className="close" aria-label={tp("shop.view.close")} onClick={() => setReviewLightbox(null)}><X /></button>
          <img src={mediaUrl(reviewLightbox)} alt="" onClick={(e) => e.stopPropagation()} />
        </div>
      ) : null}
      {toast ? <div className="buyer-toast" role="status">{toast}</div> : null}
    </div>
  );
}
