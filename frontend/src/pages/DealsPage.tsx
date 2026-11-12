import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowRight,
  BadgePercent,
  Clock3,
  Flame,
  Gift,
  Heart,
  ShoppingBag,
  ShoppingCart,
  Tag,
  TicketPercent,
  Zap,
} from "lucide-react";
import EmptyState from "../components/feedback/EmptyState";
import { LoadError, SkeletonCard } from "../components/feedback/Skeleton";
import { loginPathForCart, useOptionalCart } from "../contexts/CartContext";
import { useAuth } from "../contexts/AuthContext";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import {
  ApiError,
  catalogApi,
  dealsApi,
  favoritesApi,
  mediaUrl,
  walletApi,
  type DealCoupon,
  type DealProduct,
  type DealTabKey,
  type ProductCategory,
} from "../services/api";
import "../styles/deals.css";
import "../styles/deal-hero-fix.css";
import "../styles/shop-page.css";
import "../styles/product-card.css";

const TAB_KEYS: DealTabKey[] = [
  "today",
  "weekly",
  "flash",
  "under199",
  "bogo",
  "coupons",
  "special",
];
const TAB_ICONS = [Flame, ShoppingBag, Zap, Tag, Gift, TicketPercent, BadgePercent] as const;
const PRICE_BANDS: { key: string; band?: string }[] = [
  { key: "all" },
  { key: "under100", band: "under100" },
  { key: "100-300", band: "100-300" },
  { key: "301-500", band: "301-500" },
  { key: "501-1000", band: "501-1000" },
  { key: "over1000", band: "over1000" },
];
const DISCOUNT_OPTIONS = [0, 10, 20, 30, 50] as const;
const PAGE_SIZE = 24;

const num = (value: number | string | null | undefined) =>
  value === null || value === undefined || value === "" ? null : Number(value);
const priceOf = (p: DealProduct) => {
  const sku = p.skus?.[0];
  return num(p.basePrice) ?? num(sku?.price) ?? 0;
};
const compareOf = (p: DealProduct) =>
  num(p.compareAtPrice) ?? num(p.skus?.[0]?.compareAtPrice);
const discountOf = (p: DealProduct) => {
  const price = priceOf(p);
  const compare = compareOf(p);
  if (!compare || compare <= price) return 0;
  return Math.round(((compare - price) / compare) * 100);
};
const primaryImage = (p: DealProduct) =>
  [...(p.images ?? [])].sort(
    (a, b) => Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder,
  )[0];
const productName = (p: DealProduct, locale: string) => {
  if (locale.startsWith("en") && p.nameEn) return p.nameEn;
  if (locale.startsWith("zh") && p.nameZh) return p.nameZh;
  return p.nameTh;
};

export default function DealsPage() {
  const { user } = useAuth();
  const { tp, localeTag } = usePublicTranslation();
  const cartApi = useOptionalCart();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = (() => {
    const raw = searchParams.get("tab");
    const idx = raw ? TAB_KEYS.indexOf(raw as DealTabKey) : -1;
    return idx >= 0 ? idx : 0;
  })();
  const [activeTab, setActiveTab] = useState(initialTab);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [activeCategory, setActiveCategory] = useState(0);
  const [priceBand, setPriceBand] = useState(0);
  const [minDiscount, setMinDiscount] = useState(0);
  const [products, setProducts] = useState<DealProduct[]>([]);
  const [coupons, setCoupons] = useState<DealCoupon[]>([]);
  const [total, setTotal] = useState(0);
  const [endsAt, setEndsAt] = useState<string | null>(null);
  const [headingTitle, setHeadingTitle] = useState<string | null>(null);
  const [fetching, setFetching] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [notice, setNotice] = useState("");
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [remaining, setRemaining] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const tab = TAB_KEYS[activeTab];
  const isCoupons = tab === "coupons";

  const notify = (text: string) => {
    setNotice(text);
    window.setTimeout(() => setNotice(""), 1800);
  };

  useEffect(() => {
    catalogApi.categories().then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    if (!user) { setFavorites(new Set()); return; }
    favoritesApi.list().then((rows) => setFavorites(new Set(rows.map((r) => r.productId)))).catch(() => setFavorites(new Set()));
  }, [user]);

  useEffect(() => {
    let cancelled = false;
    setFetching(true);
    setLoadError(false);
    const categoryId = activeCategory === 0 ? undefined : categories[activeCategory - 1]?.id;
    const band = PRICE_BANDS[priceBand]?.band;
    const discount = minDiscount > 0 ? minDiscount : undefined;
    const run = async () => {
      try {
        if (isCoupons) {
          const page = await dealsApi.listCoupons({ categoryId, page: 0, size: PAGE_SIZE });
          if (cancelled) return;
          setCoupons(page.items ?? []);
          setProducts([]);
          setTotal(page.total ?? 0);
          setEndsAt(null);
          setHeadingTitle(null);
        } else {
          const page = await dealsApi.listProducts({
            tab,
            categoryId,
            priceBand: band,
            minDiscount: discount,
            page: 0,
            size: PAGE_SIZE,
          });
          if (cancelled) return;
          setProducts(page.items ?? []);
          setCoupons([]);
          setTotal(page.total ?? 0);
          setEndsAt(page.endsAt ?? null);
          setHeadingTitle(page.title ?? null);
        }
      } catch {
        if (!cancelled) {
          setLoadError(true);
          setProducts([]);
          setCoupons([]);
          setTotal(0);
          setEndsAt(null);
        }
      } finally {
        if (!cancelled) setFetching(false);
      }
    };
    void run();
    return () => { cancelled = true; };
  }, [tab, isCoupons, activeCategory, categories, priceBand, minDiscount, reloadKey]);

  useEffect(() => {
    if (!endsAt) { setRemaining(0); return; }
    const target = new Date(endsAt).getTime();
    if (Number.isNaN(target)) { setRemaining(0); return; }
    const tick = () => setRemaining(Math.max(0, Math.floor((target - Date.now()) / 1000)));
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [endsAt]);

  const countdown = useMemo(
    () =>
      [
        Math.floor(remaining / 3600),
        Math.floor((remaining % 3600) / 60),
        remaining % 60,
      ].map((value) => String(value).padStart(2, "0")),
    [remaining],
  );

  const categoryButtons = useMemo(
    () => [{ id: undefined as number | undefined, name: tp("deals.filters.all") }, ...categories.map((c) => ({
      id: c.id,
      name: localeTag.startsWith("en") && c.nameEn ? c.nameEn : localeTag.startsWith("zh") && c.nameZh ? c.nameZh : c.nameTh,
    }))],
    [categories, localeTag, tp],
  );

  const toggleFavorite = async (productId: string) => {
    if (!user) {
      window.location.assign(`/login?next=${encodeURIComponent("/deals")}`);
      return;
    }
    const liked = favorites.has(productId);
    setFavorites((prev) => {
      const next = new Set(prev);
      if (liked) next.delete(productId);
      else next.add(productId);
      return next;
    });
    try {
      if (liked) await favoritesApi.remove(productId);
      else await favoritesApi.add(productId);
      notify(liked ? tp("deals.toast.unfavorited") : tp("deals.toast.favorited"));
    } catch (error) {
      setFavorites((prev) => {
        const next = new Set(prev);
        if (liked) next.add(productId);
        else next.delete(productId);
        return next;
      });
      notify(error instanceof ApiError ? error.message : tp("deals.toast.favoriteFail"));
    }
  };

  const addToCart = async (p: DealProduct) => {
    const href = `/shop/${encodeURIComponent(p.shopSlug)}/product/${encodeURIComponent(p.slug)}`;
    if (!cartApi) {
      window.location.assign(href);
      return;
    }
    if (cartApi.access === "guest") {
      window.location.assign(loginPathForCart(href));
      return;
    }
    if (cartApi.access === "blocked") {
      notify(tp("deals.toast.addFail"));
      return;
    }
    if (cartApi.access === "loading") return;
    const skus = p.skus ?? [];
    if (skus.length > 1) {
      notify(tp("deals.toast.chooseVariant"));
      window.location.assign(href);
      return;
    }
    const sku = skus[0];
    if (!sku || sku.available <= 0) {
      notify(tp("deals.toast.outOfStock"));
      return;
    }
    try {
      const result = await cartApi.add(sku.id, 1);
      notify(
        result?.adjusted && result.appliedQuantity < 1
          ? tp("deals.toast.insufficientStock")
          : tp("deals.toast.addedToCart"),
      );
    } catch (error) {
      notify(error instanceof ApiError ? error.message : tp("deals.toast.addFail"));
    }
  };

  const collectCoupon = async (coupon: DealCoupon) => {
    if (!user) {
      window.location.assign(`/login?next=${encodeURIComponent(`/deals?tab=coupons&collect=${coupon.code}`)}`);
      return;
    }
    try {
      const result = await walletApi.collect(coupon.shopId, coupon.code);
      setCoupons((rows) =>
        rows.map((row) =>
          row.code === coupon.code && row.shopId === coupon.shopId
            ? { ...row, collected: true }
            : row,
        ),
      );
      notify(result.alreadyCollected ? tp("deals.toast.couponAlready") : tp("deals.toast.couponCollected"));
    } catch (error) {
      notify(error instanceof ApiError ? error.message : tp("deals.toast.couponFail"));
    }
  };

  useEffect(() => {
    const collect = searchParams.get("collect");
    if (!collect || !user || !isCoupons || fetching) return;
    const match = coupons.find((c) => c.code.toUpperCase() === collect.toUpperCase() && !c.collected);
    if (!match) {
      if (coupons.length > 0 || !fetching) {
        const next = new URLSearchParams(searchParams);
        if (next.has("collect")) {
          next.delete("collect");
          setSearchParams(next, { replace: true });
        }
      }
      return;
    }
    void collectCoupon(match).finally(() => {
      const next = new URLSearchParams(searchParams);
      next.delete("collect");
      setSearchParams(next, { replace: true });
    });
    // run when coupon list arrives after login redirect
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, isCoupons, coupons, fetching]);

  const title = headingTitle || tp(`deals.tabs.${tab}`);
  const showCountdown = Boolean(endsAt) && (tab === "today" || tab === "weekly" || tab === "flash" || tab === "bogo");

  return (
    <main className="deals-page">
      <div className="deal-width">
        <p className="breadcrumbs">
          <Link to="/">{tp("deals.breadcrumb.home")}</Link>
          <span>›</span>
          {tp("deals.breadcrumb.current")}
        </p>
        <section className="deal-hero">
          <img
            src="/assets/images/deals/deal-reference.png"
            alt={tp("deals.heroAlt")}
          />
        </section>
      </div>

      <section className="deal-tabs" aria-label={tp("deals.tabsLabel")}>
        <div className="deal-width">
          {TAB_KEYS.map((key, index) => {
            const Icon = TAB_ICONS[index];
            return (
              <button
                type="button"
                className={index === activeTab ? "active" : ""}
                key={key}
                onClick={() => setActiveTab(index)}
              >
                <span>
                  <Icon aria-hidden="true" />
                </span>
                {tp(`deals.tabs.${key}`)}
              </button>
            );
          })}
        </div>
      </section>

      <section className="deal-catalog deal-width">
        <aside className="deal-filters">
          <div>
            <h3>{tp("deals.filters.category")}</h3>
            {categoryButtons.map((item, index) => (
              <button
                type="button"
                className={index === activeCategory ? "active" : ""}
                key={`${item.id ?? "all"}-${item.name}`}
                onClick={() => setActiveCategory(index)}
              >
                <span>{index === 0 ? "◉" : "○"}</span>
                {item.name}
              </button>
            ))}
          </div>
          {!isCoupons ? (
            <div className="filter-box">
              <h3>{tp("deals.filters.price")}</h3>
              {PRICE_BANDS.map((band, index) => (
                <label key={band.key}>
                  <input
                    type="radio"
                    name="deal-price"
                    checked={priceBand === index}
                    onChange={() => setPriceBand(index)}
                  />
                  {tp(`deals.priceBands.${band.key}`)}
                </label>
              ))}
              <hr />
              <h3>{tp("deals.filters.discount")}</h3>
              {DISCOUNT_OPTIONS.map((value) => (
                <label key={value}>
                  <input
                    type="radio"
                    name="deal-discount"
                    checked={minDiscount === value}
                    onChange={() => setMinDiscount(value)}
                  />
                  {value === 0
                    ? tp("deals.filters.all")
                    : tp("deals.discountMin", { value })}
                </label>
              ))}
            </div>
          ) : null}
        </aside>

        <div className="deal-main">
          <div className="deal-heading">
            <div>
              <h1>{title}</h1>
              <p>{tp(`deals.leads.${tab}`)}</p>
            </div>
            {showCountdown ? (
              <div className="deal-countdown" role="timer" aria-live="off">
                <Clock3 aria-hidden="true" />
                <b>{remaining > 0 ? tp("deals.countdown.endsIn") : tp("deals.countdown.ended")}</b>
                {countdown.map((x, i) => (
                  <span key={i}>
                    <strong>{x}</strong>
                    <small>{tp(`deals.countdown.${["hours", "minutes", "seconds"][i]}`)}</small>
                  </span>
                ))}
              </div>
            ) : <div />}
            <button type="button" onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}>
              {tp("deals.seeAll")} <ArrowRight aria-hidden="true" />
            </button>
          </div>

          {fetching ? (
            <div className="deal-grid">
              {Array.from({ length: 8 }).map((_, i) => (
                <SkeletonCard key={i} />
              ))}
            </div>
          ) : loadError ? (
            <LoadError
              message={tp("deals.error")}
              onRetry={() => setReloadKey((n) => n + 1)}
              retryLabel={tp("deals.retry")}
            />
          ) : isCoupons ? (
            coupons.length === 0 ? (
              <EmptyState
                icon={TicketPercent}
                title={tp("deals.empty.couponsTitle")}
                description={tp("deals.empty.couponsBody")}
              />
            ) : (
              <div className="deal-coupon-grid">
                {coupons.map((coupon) => (
                  <article className="deal-coupon-card" key={`${coupon.shopId}-${coupon.code}`}>
                    <div>
                      <b>{coupon.code}</b>
                      <h2>{coupon.name}</h2>
                      <p>
                        {coupon.type === "PERCENT"
                          ? tp("deals.coupon.percent", { value: Number(coupon.value) })
                          : coupon.type === "FIXED_AMOUNT"
                            ? tp("deals.coupon.fixed", { value: Number(coupon.value).toLocaleString() })
                            : tp("deals.coupon.freeShipping")}
                      </p>
                      <small>
                        {coupon.shopName}
                        {coupon.minOrderAmount
                          ? ` · ${tp("deals.coupon.minOrder", { value: Number(coupon.minOrderAmount).toLocaleString() })}`
                          : ""}
                      </small>
                    </div>
                    <button
                      type="button"
                      disabled={Boolean(coupon.collected)}
                      onClick={() => void collectCoupon(coupon)}
                    >
                      {coupon.collected ? tp("deals.coupon.collected") : tp("deals.coupon.collect")}
                    </button>
                  </article>
                ))}
              </div>
            )
          ) : products.length === 0 ? (
            <EmptyState
              icon={Gift}
              title={tp("deals.empty.productsTitle")}
              description={tp("deals.empty.productsBody")}
            />
          ) : (
            <div className="deal-grid">
              {products.map((p) => {
                const href = `/shop/${encodeURIComponent(p.shopSlug)}/product/${encodeURIComponent(p.slug)}`;
                const image = primaryImage(p);
                const discount = discountOf(p);
                const compare = compareOf(p);
                const liked = favorites.has(p.id);
                return (
                  <article className="deal-card" key={p.id}>
                    {discount > 0 ? <span className="discount">-{discount}%</span> : null}
                    <button
                      type="button"
                      className={`deal-heart${liked ? " active" : ""}`}
                      aria-label={tp("deals.favorite")}
                      onClick={() => void toggleFavorite(p.id)}
                    >
                      <Heart aria-hidden="true" fill={liked ? "currentColor" : "none"} />
                    </button>
                    <Link to={href} className="deal-photo">
                      {image?.url ? (
                        <img src={mediaUrl(image.url)} alt="" />
                      ) : (
                        <span aria-hidden="true">🛍️</span>
                      )}
                    </Link>
                    <div className="deal-info">
                      <h2>
                        <Link to={href}>{productName(p, localeTag)}</Link>
                      </h2>
                      <p className="deal-price">
                        <b>฿{priceOf(p).toLocaleString()}</b>
                        {compare && compare > priceOf(p) ? (
                          <del>฿{compare.toLocaleString()}</del>
                        ) : null}
                      </p>
                      <p className="pcard-meta">
                        {p.rating != null ? (
                          <span className="pcard-rating">
                            <b>★ {Number(p.rating).toFixed(1)}</b>
                            <span className="pcard-reviews">({p.reviewCount ?? 0})</span>
                          </span>
                        ) : null}
                        {p.rating != null && (p.soldCount ?? 0) > 0 ? (
                          <span className="pcard-sep" aria-hidden="true">·</span>
                        ) : null}
                        {(p.soldCount ?? 0) > 0 ? (
                          <span className="pcard-sold">
                            {tp("deals.sold", { count: (p.soldCount ?? 0).toLocaleString() })}
                          </span>
                        ) : null}
                      </p>
                      <button type="button" onClick={() => void addToCart(p)}>
                        <ShoppingCart aria-hidden="true" />
                        {tp("deals.addToCart")}
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          )}

          {!isCoupons && !fetching && !loadError ? (
            <>
              <div className="deal-banners">
                <article>
                  <div>
                    <b>⚡ FLASH SALE</b>
                    <h3>{tp("deals.banners.flashTitle")}</h3>
                    <button type="button" onClick={() => setActiveTab(2)}>
                      {tp("deals.banners.shopNow")} →
                    </button>
                  </div>
                  <span aria-hidden="true">⏰</span>
                  <strong>
                    {tp("deals.banners.upTo")}
                    <br />
                    <em>70%</em>
                  </strong>
                </article>
                <article>
                  <div>
                    <b>{tp("deals.tabs.coupons")}</b>
                    <h3>{tp("deals.banners.couponTitle")}</h3>
                    <button type="button" onClick={() => setActiveTab(5)}>
                      {tp("deals.banners.collectNow")} →
                    </button>
                  </div>
                  <span aria-hidden="true">🎟️</span>
                </article>
              </div>
              {total > 0 ? (
                <p className="deal-result-count">
                  {tp("deals.resultCount", { shown: products.length, total })}
                </p>
              ) : null}
            </>
          ) : null}
        </div>
      </section>

      {notice ? (
        <div className="deal-toast" role="status">
          {notice}
        </div>
      ) : null}
    </main>
  );
}
