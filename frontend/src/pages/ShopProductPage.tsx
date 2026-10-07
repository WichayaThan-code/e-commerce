import { useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { Link, useParams } from "react-router-dom";
import { Check, ChevronLeft, ChevronRight, Facebook, Link2, Minus, Plus, ShoppingCart, Store, Truck, X } from "lucide-react";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { labelForCategory } from "../services/categories";
import { localizedText } from "../services/catalogText";
import { ApiError, catalogApi, mediaUrl, shopApi, type ProductCategory, type PublicProduct, type PublicShop, type PublicSku } from "../services/api";
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

export default function ShopProductPage() {
  const { slug = "", productSlug = "" } = useParams();
  const { tp, localeTag } = usePublicTranslation();
  const lang = localeTag.startsWith("en") ? "en" : localeTag.startsWith("zh") ? "zh" : "th";
  const [product, setProduct] = useState<PublicProduct | null>(null);
  const [shop, setShop] = useState<PublicShop | null>(null);
  const [others, setOthers] = useState<PublicProduct[]>([]);
  const [productTotal, setProductTotal] = useState<number | null>(null);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [state, setState] = useState<"loading" | "error" | "missing" | "ok">("loading");
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
    setShop(null);
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
    shopApi.getBySlug(slug).then(setShop).catch(() => setShop(null));
    shopApi.products(slug, { size: 12, sort: "newest" }).then((page) => {
      setProductTotal(page.total);
      setOthers(page.items.filter((item) => item.slug !== productSlug).slice(0, 6));
    }).catch(() => {
      setProductTotal(null);
      setOthers([]);
    });
    catalogApi.categories().then(setCategories).catch(() => setCategories([]));
  };

  useEffect(() => { load(); }, [slug, productSlug]);

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

  const share = (kind: "facebook" | "line" | "x") => {
    const url = encodeURIComponent(window.location.href);
    const target = kind === "facebook"
      ? `https://www.facebook.com/sharer/sharer.php?u=${url}`
      : kind === "line"
        ? `https://social-plugins.line.me/lineit/share?url=${url}`
        : `https://twitter.com/intent/tweet?url=${url}`;
    window.open(target, "_blank", "noopener,noreferrer");
  };
  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      notify(tp("shop.view.copied"));
    } catch {
      notify(tp("shop.productError"));
    }
  };
  const addToCart = () => {
    if (!complete) { setWarn(true); return; }
    if (!ready) return;
    notify(tp("shop.view.soon"));
  };
  const buyNow = () => {
    if (!complete) { setWarn(true); return; }
    if (!ready) return;
    notify(tp("shop.view.soon"));
  };

  const name = product ? localizedText(localeTag, product.nameTh, product.nameEn, product.nameZh) : "";
  const description = product ? localizedText(localeTag, product.descriptionTh, product.descriptionEn, product.descriptionZh) : "";
  const image = product?.images[active] || product?.images[0];
  const category = categories.find((item) => item.id === product?.categoryId);
  const shopName = shop ? (lang === "en" && shop.nameEn ? shop.nameEn : shop.name) : "";
  const stats = [
    productTotal != null ? [tp("shop.view.productCount"), String(productTotal)] : null,
    shop?.category ? [tp("shop.view.category"), shop.category] : null,
    shop?.province ? [tp("common.area"), shop.province] : null,
  ].filter((item): item is [string, string] => Boolean(item));

  const scrollThumbs = (direction: number) => thumbsRef.current?.scrollBy({ left: direction * 180, behavior: "smooth" });
  const valueEnabled = (group: Group, value: string) => product?.skus.some((sku) => {
    const own = group.key === "option" ? sku.optionLabel : sku.optionValues?.[group.key];
    return own === value && sku.available > 0 && skuMatches(sku, groups, picked, group.key);
  });

  return (
    <div className="recommend-page shop-directory buyer-product">
      <section className="recommend-intro recommend-width">
        <p className="recommend-breadcrumb">
          <Link to="/">{tp("common.home")}</Link>
          <span>›</span>
          <Link to={`/shop/${slug}`}>{shopName || tp("shop.productBack")}</Link>
          {name ? <><span>›</span><b>{name}</b></> : null}
        </p>
      </section>
      {state === "loading" && (
        <section className="recommend-width buyer-skeleton" aria-label={tp("shop.productLoading")}>
          <div /><div /><div />
        </section>
      )}
      {state === "missing" && <section className="recommend-width"><article className="shop-list-empty">{tp("shop.productMissing")}</article></section>}
      {state === "error" && (
        <section className="recommend-width">
          <article className="shop-list-empty">
            <p>{tp("shop.productError")}</p>
            <button type="button" onClick={load}>{tp("shop.productsRetry")}</button>
          </article>
        </section>
      )}
      {state === "ok" && product && (
        <div className="recommend-width">
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
                {product.images.length > 0 ? <span className="buyer-count">{tp("shop.view.imageIndex", { current: active + 1, total: product.images.length })}</span> : null}
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
              </div>
            </div>
            <div className="buyer-info">
              <h1>{name}</h1>
              <div className="buyer-price">
                <b>{currentPrice != null ? money(currentPrice, localeTag) : low != null && high != null && low !== high ? `${money(low, localeTag)} – ${money(high, localeTag)}` : low != null ? money(low, localeTag) : "—"}</b>
                {discount > 0 && compare != null ? <><s>{money(compare, localeTag)}</s><em>{tp("shop.view.discount", { percent: discount })}</em></> : null}
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
                      <input inputMode="numeric" value={ready ? qty : 0} onChange={(event) => {
                        const next = Number(event.target.value);
                        if (!Number.isFinite(next)) return;
                        setQty(Math.min(stock, Math.max(1, Math.trunc(next))));
                      }} />
                      <button type="button" aria-label="+" disabled={!ready || qty >= stock} onClick={() => setQty((value) => Math.min(stock, value + 1))}><Plus /></button>
                    </div>
                    <span>{complete && stock <= 0 ? tp("shop.view.soldOut") : selectedSku ? tp("shop.view.inStock", { count: stock }) : null}</span>
                  </dd>
                </div>
              </dl>
              <div className="buyer-actions">
                <button type="button" className="cart" disabled={soldOut} onClick={addToCart}><ShoppingCart />{tp("shop.view.addToCart")}</button>
                <button type="button" className="buy" disabled={soldOut} onClick={buyNow}>{tp("shop.view.buyNow")}</button>
              </div>
            </div>
          </article>
          {shop && (
            <section className="buyer-card buyer-shop">
              <div className="buyer-shop-id">
                <span className="buyer-shop-logo">{shop.logoUrl ? <img src={mediaUrl(shop.logoUrl)} alt={tp("shop.view.logoAlt", { name: shopName })} /> : <Store />}</span>
                <div>
                  <b>{shopName}</b>
                  <Link to={`/shop/${slug}`}>{tp("shop.view.viewShop")}</Link>
                </div>
              </div>
              {stats.length > 0 && (
                <dl className="buyer-shop-stats" style={{ "--shop-stats": stats.length } as CSSProperties}>
                  {stats.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
                </dl>
              )}
            </section>
          )}
          <section className="buyer-card buyer-detail">
            <h2>{tp("shop.view.specs")}</h2>
            <dl>
              {category ? <div><dt>{tp("shop.view.category")}</dt><dd>{labelForCategory(category, lang)}</dd></div> : null}
              {selectedSku ? <div><dt>{tp("shop.view.sku")}</dt><dd>{selectedSku.skuCode}</dd></div> : null}
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
                    <Link key={item.id} to={`/shop/${slug}/product/${item.slug}`}>
                      {photo ? <img src={mediaUrl(photo.url)} alt="" /> : <span />}
                      <b>{title}</b>
                      {price != null ? <strong>{money(price, localeTag)}</strong> : null}
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
