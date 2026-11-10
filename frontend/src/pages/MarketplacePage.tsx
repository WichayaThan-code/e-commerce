import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import "../styles/member-home.css";
import "../styles/shop-page.css";
import EmptyState from "../components/feedback/EmptyState";
import { useAuth } from "../contexts/AuthContext";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import {
  ApiError,
  catalogApi,
  mediaUrl,
  recommendationApi,
  type BestSellerProduct,
  type ProductCategory,
} from "../services/api";
import { shopProductPath } from "../services/shopRoutes";
import {
  ArrowRight,
  CircleEllipsis,
  Gift,
  Headphones,
  Heart,
  Package,
  PackageCheck,
  ShieldCheck,
  ShoppingBag,
  Store,
  Truck,
} from "lucide-react";

const benefitIcons = [Truck, ShieldCheck, Store, PackageCheck, Headphones];
const HOME_PRODUCT_COUNT = 6;
const CATEGORY_SLOT_COUNT = 10; // design grid is 11 cells; last slot = "more"

type HomeBenefit = { title: string; text: string };
type HomeProductMeta = { price: number; old: number; rating: string; sold: number; emoji: string; bg: string };

const num = (value: number | string | null | undefined) =>
  value === null || value === undefined || value === "" ? null : Number(value);

/** Lowest sellable price: basePrice or cheapest SKU (variants). */
function lowestPrice(p: BestSellerProduct) {
  const base = num(p.basePrice);
  const skuPrices = (p.skus ?? [])
    .map((sku) => num(sku.price))
    .filter((value): value is number => value != null && Number.isFinite(value) && value >= 0);
  const candidates = [...(base != null && Number.isFinite(base) ? [base] : []), ...skuPrices];
  return candidates.length ? Math.min(...candidates) : 0;
}

function comparePrice(p: BestSellerProduct) {
  const base = num(p.compareAtPrice);
  if (base != null && Number.isFinite(base) && base > 0) return base;
  const skuCompares = (p.skus ?? [])
    .map((sku) => num(sku.compareAtPrice))
    .filter((value): value is number => value != null && Number.isFinite(value) && value > 0);
  return skuCompares.length ? Math.min(...skuCompares) : null;
}

function primaryImageUrl(p: BestSellerProduct) {
  const image = [...(p.images ?? [])].sort(
    (a, b) => Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder,
  )[0];
  return image?.url ? mediaUrl(image.url) : null;
}

function localizedName(p: BestSellerProduct, localeTag: string) {
  if (localeTag.startsWith("en") && p.nameEn) return p.nameEn;
  if (localeTag.startsWith("zh") && p.nameZh) return p.nameZh;
  return p.nameTh;
}

function localizedCategory(c: ProductCategory, localeTag: string) {
  if (localeTag.startsWith("en") && c.nameEn) return c.nameEn;
  if (localeTag.startsWith("zh") && c.nameZh) return c.nameZh;
  return c.nameTh;
}

/** Soft wrap long category labels into two lines for the existing `<span>` split. */
function categoryLabelLines(name: string) {
  const trimmed = name.trim();
  if (trimmed.includes("\n")) return trimmed;
  const parts = trimmed.split(/\s+/);
  if (parts.length >= 2 && trimmed.length > 10) {
    const mid = Math.ceil(parts.length / 2);
    return `${parts.slice(0, mid).join(" ")}\n${parts.slice(mid).join(" ")}`;
  }
  return trimmed;
}

export default function MarketplacePage() {
  const [notice, setNotice] = useState("");
  const { user } = useAuth();
  const { tp, localeTag } = usePublicTranslation();
  const navigate = useNavigate();

  const benefits = tp("home.benefits", { returnObjects: true }) as unknown as HomeBenefit[];
  const categoryEmojis = tp("home.categoryEmojis", { returnObjects: true }) as unknown as string[];
  const fallbackCategories = tp("home.categories", { returnObjects: true }) as unknown as string[];
  const openStoreBullets = tp("home.openStoreBullets", { returnObjects: true }) as unknown as string[];
  const productMeta = tp("home.productMeta", { returnObjects: true }) as unknown as HomeProductMeta[];

  const [categories, setCategories] = useState<ProductCategory[] | null>(null);
  const [products, setProducts] = useState<BestSellerProduct[] | null>(null);
  const [productsLoading, setProductsLoading] = useState(true);
  const [productsError, setProductsError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    setProductsLoading(true);
    setProductsError("");
    catalogApi.categories()
      .then((rows) => { if (active) setCategories(rows) })
      .catch(() => { if (active) setCategories([]) });
    recommendationApi.list({ size: HOME_PRODUCT_COUNT, sort: "featured" })
      .then((page) => { if (active) setProducts(page.items ?? []) })
      .catch((error) => {
        if (!active) return;
        setProducts([]);
        setProductsError(error instanceof ApiError ? error.message : tp("home.loadError"));
      })
      .finally(() => { if (active) setProductsLoading(false) });
    return () => { active = false };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reloadKey only; tp is for error copy
  }, [reloadKey]);

  const categoryButtons = useMemo(() => {
    const moreLabel = fallbackCategories[fallbackCategories.length - 1] ?? "อื่นๆ";
    const rows = (categories && categories.length > 0 ? categories : []).slice(0, CATEGORY_SLOT_COUNT);
    if (rows.length === 0) {
      // API empty → keep design slots from i18n labels (still non-interactive beyond toast/nav)
      return fallbackCategories.map((label, index) => ({
        key: `fallback-${index}`,
        label,
        emoji: categoryEmojis[index],
        isMore: index === fallbackCategories.length - 1,
        categoryId: undefined as number | undefined,
      }));
    }
    return [
      ...rows.map((row, index) => ({
        key: String(row.id),
        label: categoryLabelLines(localizedCategory(row, localeTag)),
        emoji: categoryEmojis[index] ?? "🛍️",
        isMore: false,
        categoryId: row.id as number | undefined,
      })),
      {
        key: "more",
        label: moreLabel,
        emoji: categoryEmojis[CATEGORY_SLOT_COUNT] ?? "🎁",
        isMore: true,
        categoryId: undefined as number | undefined,
      },
    ];
  }, [categories, categoryEmojis, fallbackCategories, localeTag]);

  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 2200);
  };

  const displayName = user ? user.firstName || user.email : "";

  const openCategory = (item: (typeof categoryButtons)[number]) => {
    if (item.isMore || item.categoryId == null) {
      navigate("/best-sellers");
      return;
    }
    navigate(`/recommendations?category=${item.categoryId}`);
    notify(tp("home.pickCategory", { name: item.label.replace("\n", " ") }));
  };

  return (
    <>
      {user && (
        <section className="member-welcome page-width" aria-labelledby="member-welcome-title">
          <div className="member-welcome-copy">
            <span className="member-eyebrow">
              <span /> {tp("home.memberEyebrow")}
            </span>
            <h1 id="member-welcome-title">
              {tp("home.memberGreeting", { name: displayName })} <span>{tp("home.memberHello")}</span>
            </h1>
            <p>{tp("home.memberLead")}</p>
          </div>
          <div className="member-shortcuts">
            <a href="/deals">
              <span>
                <Gift />
              </span>
              <b>{tp("home.memberDeals")}</b>
              <small>{tp("home.memberDealsHint")}</small>
            </a>
            <a href="/cart">
              <span>
                <ShoppingBag />
              </span>
              <b>{tp("home.memberCart")}</b>
              <small>{tp("home.memberCartHint")}</small>
            </a>
            <a href="/help">
              <span>
                <Headphones />
              </span>
              <b>{tp("home.memberHelp")}</b>
              <small>{tp("home.memberHelpHint")}</small>
            </a>
          </div>
        </section>
      )}
      <section className="hero">
        <img className="hero-image" src="/assets/images/home/hero-complete-clean-v4.png" alt={tp("home.heroAlt")} />
        <button
          className="hero-hotspot"
          aria-label={tp("home.shopNow")}
          onClick={() => document.getElementById("recommended")?.scrollIntoView({ behavior: "smooth" })}
        />
      </section>

      <section className="benefits">
        <div className="page-width benefit-grid">
          {benefits.map(({ title, text }, index) => {
            const Icon = benefitIcons[index] ?? Truck;
            return (
              <div className="benefit" key={title}>
                <span>
                  <Icon />
                </span>
                <div>
                  <b>{title}</b>
                  <small>{text}</small>
                </div>
              </div>
            );
          })}
        </div>
      </section>

      <section className="page-width section" id="categories">
        <div className="section-head">
          <h2>{tp("home.shopByCategory")}</h2>
          <a href="/best-sellers">
            {tp("common.viewAll")} <ArrowRight size={15} />
          </a>
        </div>
        <div className="category-grid">
          {categoryButtons.map((item) => (
            <button
              className={`category ${item.isMore ? "category-more" : ""}`}
              key={item.key}
              onClick={() => openCategory(item)}
            >
              <span>{item.isMore ? <CircleEllipsis /> : item.emoji}</span>
              <b>
                {item.label.split("\n").map((line, j) => (
                  <span key={j}>{line}</span>
                ))}
              </b>
            </button>
          ))}
        </div>
      </section>

      <section className="page-width promo-grid" id="deals">
        <article className="promo thai">
          <div>
            <h3>
              {tp("home.promoThaiTitle")
                .split("\n")
                .map((line, index) => (
                  <span key={index}>
                    {line}
                    {index === 0 && <br />}
                  </span>
                ))}
            </h3>
            <p>
              {tp("home.promoThaiText")
                .split("\n")
                .map((line, index) => (
                  <span key={index}>
                    {line}
                    {index === 0 && <br />}
                  </span>
                ))}
            </p>
            <button onClick={() => navigate("/best-sellers")}>
              {tp("home.promoThaiBtn")} <ArrowRight />
            </button>
          </div>
          <div className="promo-art">
            🥜<span>🥭</span>
          </div>
        </article>
        <article className="promo gadget">
          <div>
            <h3>
              {tp("home.promoGadgetTitle")
                .split("\n")
                .map((line, index) => (
                  <span key={index}>
                    {line}
                    {index === 0 && <br />}
                  </span>
                ))}
            </h3>
            <p>{tp("home.promoGadgetText")}</p>
            <button onClick={() => navigate("/best-sellers")}>
              {tp("home.shopNow")} <ArrowRight />
            </button>
          </div>
          <div className="promo-art">
            🎧<span>💻</span>
          </div>
        </article>
        <article className="promo beauty">
          <div>
            <h3>
              {tp("home.promoBeautyTitle")
                .split("\n")
                .map((line, index) => (
                  <span key={index}>
                    {line}
                    {index === 0 && <br />}
                  </span>
                ))}
            </h3>
            <p>
              {tp("home.promoBeautyText")
                .split("\n")
                .map((line, index) => (
                  <span key={index}>
                    {line}
                    {index === 0 && <br />}
                  </span>
                ))}
            </p>
            <button onClick={() => navigate("/recommendations")}>
              {tp("home.shopNow")} <ArrowRight />
            </button>
          </div>
          <div className="promo-art">
            🧴<span>🌿</span>
          </div>
        </article>
      </section>

      <section className="page-width section recommended" id="recommended">
        <div className="section-head">
          <h2>{tp("home.recommended")}</h2>
          <a href="/recommendations">
            {tp("common.viewAll")} <ArrowRight size={15} />
          </a>
        </div>
        <div className="product-layout">
          <div className="product-grid" aria-busy={productsLoading}>
            {productsLoading &&
              Array.from({ length: HOME_PRODUCT_COUNT }, (_, index) => {
                const meta = productMeta[index] ?? productMeta[0];
                return (
                  <article className="product-card" key={`skeleton-${index}`} aria-hidden="true">
                    <button className="heart" tabIndex={-1} aria-hidden="true"><Heart size={17} /></button>
                    <button className="product-image" style={{ background: meta?.bg ?? "#eef3f8" }} tabIndex={-1} aria-hidden="true">
                      <span />
                    </button>
                    <div className="product-info">
                      <h3>&nbsp;</h3>
                      <div><strong>&nbsp;</strong></div>
                      <small>&nbsp;</small>
                    </div>
                  </article>
                );
              })}
            {!productsLoading && productsError && (
              <EmptyState
                className="product-grid-empty"
                variant="error"
                title={productsError}
                action={{ label: tp("home.retry"), onClick: () => setReloadKey((value) => value + 1) }}
              />
            )}
            {!productsLoading && !productsError && products && products.length === 0 && (
              <EmptyState
                className="product-grid-empty"
                icon={Package}
                title={tp("home.emptyRecommended")}
                action={{ label: tp("emptyState.browseShops"), to: "/shops" }}
              />
            )}
            {!productsLoading && !productsError && products?.map((p, index) => {
              const price = lowestPrice(p);
              const old = comparePrice(p);
              const discount = old && old > price ? Math.round((1 - price / old) * 100) : null;
              const imageUrl = primaryImageUrl(p);
              const meta = productMeta[index % productMeta.length];
              const name = localizedName(p, localeTag);
              const href = shopProductPath(p.shopSlug, p.slug);
              const rating = p.rating != null && Number.isFinite(p.rating) ? p.rating.toFixed(1) : null;
              const sold = p.soldCount ?? null;
              return (
                <article className="product-card" key={p.id}>
                  <button className="heart" aria-label={tp("home.addFavorite")} onClick={() => notify(tp("home.addedFavorite"))}>
                    <Heart size={17} />
                  </button>
                  <button
                    className="product-image"
                    style={
                      imageUrl
                        ? { backgroundImage: `url(${imageUrl})`, backgroundSize: "cover", backgroundPosition: "center", backgroundColor: "#f3f5f8" }
                        : { background: meta?.bg ?? "#f3eee8" }
                    }
                    onClick={() => navigate(href)}
                    aria-label={name}
                  >
                    {!imageUrl && <span>{meta?.emoji ?? "🛍️"}</span>}
                  </button>
                  <div className="product-info">
                    <h3>{name}</h3>
                    <div>
                      <strong>฿{price.toLocaleString(localeTag)}</strong>
                      {old != null && old > price && (
                        <>
                          <del>฿{old.toLocaleString(localeTag)}</del>
                          {discount != null && discount > 0 && <em>-{discount}%</em>}
                        </>
                      )}
                    </div>
                    <small>
                      {/* TODO: show ★ rating only when review aggregate is present (API may return null). */}
                      {rating != null && <b>★ {rating}</b>}
                      {sold != null && <>{rating != null ? " " : ""}({sold.toLocaleString(localeTag)})</>}
                    </small>
                  </div>
                </article>
              );
            })}
          </div>
          <aside className="seller-card" id="shops">
            <div className="shop-illustration">
              <span>🏪</span>
              <i>📦</i>
            </div>
            <h3>{tp("home.openStoreTitle")}</h3>
            <h4>{tp("home.openStoreSubtitle")}</h4>
            <ul>
              {openStoreBullets.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
            <a className="primary" href="/merchant/register">
              {tp("home.openStoreCta")} <ArrowRight size={17} />
            </a>
          </aside>
        </div>
      </section>
      {notice && (
        <div className="toast" role="status">
          {notice}
        </div>
      )}
    </>
  );
}
