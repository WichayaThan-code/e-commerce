import { useEffect, useMemo, useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { AlertTriangle, ArrowRight, Headphones, Heart, Minus, PackageCheck, ShieldCheck, ShoppingCart, Store, Tag, Trash2, Truck } from "lucide-react";
import { loginPathForCart, useCart } from "../contexts/CartContext";
import { useAuth } from "../contexts/AuthContext";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { useDelayedLoading } from "../hooks/useDelayedLoading";
import { Skeleton } from "../components/feedback/Skeleton";
import { cartErrorMessage, couponReasonMessage, formatBaht } from "../components/cart/cartFormat";
import CartCouponOffers from "../components/cart/CartCouponOffers";
import { mediaUrl, moneyValue, shopApi, type CartActionResult, type CartGroup, type CartLine, type PublicProduct } from "../services/api";
import { localizedText } from "../services/catalogText";
import { homePath } from "../services/permissions";
import "../styles/cart.css";

type Row = CartLine & { shopId: number; shopName: string; shopSlug: string };

function lineNotice(line: CartLine, tp: (key: string, options?: Record<string, unknown>) => string) {
  if (line.status === "OK") return null;
  const text = line.status === "INSUFFICIENT_STOCK"
    ? tp("cart.status.INSUFFICIENT_STOCK", { count: Math.max(0, line.availableQuantity ?? line.maxQuantity ?? 0) })
    : tp(`cart.status.${line.status}`);
  return <small className={`cart-line-notice is-${line.status.toLowerCase()}`} role="status"><AlertTriangle size={12}/>{text}</small>;
}

/** "สินค้าแนะนำ": real products of the first shop in the cart (public shop API), old card markup; hidden when nothing to show. */
function useSuggestions(slug: string | null, exclude: string[]) {
  const [items, setItems] = useState<PublicProduct[]>([]);
  const excludeKey = exclude.join(",");
  useEffect(() => {
    if (!slug) { setItems([]); return; }
    let alive = true;
    shopApi.products(slug, { size: 12, sort: "newest" })
      .then((page) => { if (alive) setItems(page.items.filter((product) => !excludeKey.split(",").includes(product.id)).slice(0, 5)); })
      .catch(() => { if (alive) setItems([]); });
    return () => { alive = false; };
  }, [slug, excludeKey]);
  return items;
}

export default function CartPage() {
  const cart = useCart();
  const { user } = useAuth();
  const { tp, localeTag } = usePublicTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const [coupon, setCoupon] = useState("");
  const [couponShop, setCouponShop] = useState<number | null>(null);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [liked, setLiked] = useState<string[]>([]);
  const { showSkeleton, markHasData } = useDelayedLoading(cart.status === "loading");
  const money = (value: unknown) => formatBaht(value as number, localeTag);

  const data = cart.cart;
  const groups: CartGroup[] = data?.groups ?? [];
  const items: Row[] = useMemo(() => groups.flatMap((group) => group.items.map((line) => ({ ...line, shopId: group.shopId, shopName: group.shopName, shopSlug: group.shopSlug }))), [groups]);
  const selectedItems = items.filter((item) => item.selected);
  const allSelected = items.length > 0 && selectedItems.length === items.length;
  const priceChanged = items.some((item) => item.priceChanged);
  const blocked = selectedItems.some((item) => item.status !== "OK");
  const summary = data?.summary;
  /** Shops that have selected lines: the single coupon box applies to one of them (picker shown only when > 1). */
  const couponShops = groups.filter((group) => group.items.some((line) => line.selected));
  const targetShop = couponShops.find((group) => group.shopId === couponShop) ?? couponShops[0] ?? null;
  const appliedCoupons = groups.filter((group) => group.coupon);
  const productSaving = selectedItems.reduce((sum, item) => {
    const compare = moneyValue(item.compareAtPrice);
    const unit = moneyValue(item.unitPrice);
    return compare > unit ? sum + (compare - unit) * item.quantity : sum;
  }, 0);
  const saving = productSaving + (summary ? moneyValue(summary.discount) + moneyValue(summary.shippingDiscount) : 0);
  const suggestions = useSuggestions(groups[0]?.shopSlug ?? null, items.map((item) => item.productId));

  useEffect(() => { if (data) markHasData(); }, [data, markHasData]);
  useEffect(() => {
    const state = location.state as { checkoutIssues?: boolean } | null;
    if (state?.checkoutIssues) setNotice(tp("cart.checkoutBlocked"));
  }, [location.state, tp]);

  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 2400);
  };
  const run = async (action: () => Promise<unknown>, success?: string) => {
    setBusy(true);
    try {
      const outcome = (await action()) as CartActionResult | null | undefined;
      if (outcome?.adjusted) notify(tp("cart.quantityAdjusted", { count: outcome.appliedQuantity }));
      else if (success) notify(success);
      return true;
    } catch (error) {
      notify(cartErrorMessage(error, tp));
      return false;
    } finally {
      setBusy(false);
    }
  };
  const setQuantity = (item: Row, difference: number) => run(() => cart.update(item.id, { quantity: item.quantity + difference }));
  const remove = (ids: string[]) => run(() => cart.remove(ids), tp("cart.removed"));
  const toggle = (item: Row) => run(() => cart.update(item.id, { selected: !item.selected }));
  const applyCoupon = async () => {
    const code = coupon.trim();
    if (!code || !targetShop) return;
    if (await run(() => cart.applyCoupon(targetShop.shopId, code), targetShop.coupon ? tp("cart.coupon.replaced") : tp("cart.coupon.applied"))) setCoupon("");
  };
  const goCheckout = async () => {
    if (!user) { navigate(`/login?next=${encodeURIComponent("/checkout")}`); return; }
    setBusy(true);
    try {
      const result = await cart.validate();
      if (result.valid) navigate("/checkout");
      else notify(result.issues.find((issue) => issue.severity === "ERROR")?.message ?? tp("cart.checkoutBlocked"));
    } catch (error) {
      notify(cartErrorMessage(error, tp));
    } finally {
      setBusy(false);
    }
  };

  // R2: buyers only. Guests sign in first and come back here; merchant / admin accounts get a friendly message (no cart API call).
  if (cart.access === "guest") return <Navigate to={loginPathForCart("/cart")} replace/>;
  if (cart.access === "blocked") {
    return <main className="cart-page">
      <section className="cart-content cart-width">
        <div className="cart-main-column">
          <div className="cart-empty" role="status"><Store/><h2>{tp("cart.blocked.title")}</h2><p>{tp("cart.blocked.body")}</p><Link to={homePath(user)}>{tp("cart.blocked.action")}</Link></div>
        </div>
      </section>
    </main>;
  }

  return <main className="cart-page">
    <div className="cart-heading cart-width"><Link to="/">← {tp("cart.backToShopping")}</Link><ol aria-label={tp("checkout.steps.label")}><li aria-current="step"><span>1</span> {tp("checkout.steps.cart")}</li><li><span>2</span> {tp("checkout.steps.checkout")}</li><li><span>3</span> {tp("checkout.steps.done")}</li></ol></div>
    <div className="cart-page-title cart-width"><div className="cart-title"><ShoppingCart/><div><h1>{tp("cart.title")} <span>({tp("cart.lineCount", { count: items.length })})</span></h1><p>{tp("cart.subtitle")}</p></div></div></div>
    <section className="cart-content cart-width">
      <div className="cart-main-column">
        {priceChanged && <div className="cart-price-notice" role="alert"><AlertTriangle size={16}/><span>{tp("cart.priceChangedBanner")}</span><button type="button" disabled={busy} onClick={() => run(cart.acceptPrices, tp("cart.pricesAccepted"))}>{tp("cart.acceptPrices")}</button></div>}
        {cart.mergeNotes.length > 0 && <div className="cart-price-notice is-info" role="status"><span>{cart.mergeNotes.map((note) => note.message).join(" · ")}</span><button type="button" onClick={cart.dismissMergeNotes}>{tp("cart.dismiss")}</button></div>}

        <div className="cart-select-row"><label><input type="checkbox" checked={allSelected} disabled={busy || !items.length} onChange={() => run(() => cart.setSelected([], !allSelected))}/>{tp("cart.selectAll")}</label><button disabled={busy || !selectedItems.length} onClick={() => remove(selectedItems.map((item) => item.id))}><Trash2/>{tp("cart.removeSelected")}</button></div>
        <section className="cart-list" aria-busy={showSkeleton || undefined}>
          {showSkeleton && !data && [0, 1, 2].map((key) => <article key={key} aria-hidden="true">
            <span/><Skeleton className="cart-photo"/><div className="cart-item-copy"><Skeleton width="70%" height={16}/><Skeleton width="40%" height={13}/></div>
          </article>)}
          {items.map((item) => <article key={item.id} className={item.selected ? "cart-item-selected" : ""}>
            <input aria-label={tp("cart.selectLine", { name: item.productName })} type="checkbox" checked={item.selected} disabled={busy} onChange={() => toggle(item)}/>
            <span className="cart-photo cart-photo-real">{item.imageUrl ? <img src={mediaUrl(item.imageUrl)} alt={item.productName}/> : null}</span>
            <div className="cart-item-copy"><h2><Link to={`/shop/${item.shopSlug}/product/${item.productSlug}`}>{item.productName}</Link></h2><p><Store/>{item.shopName}</p><em>{item.optionLabel || tp("cart.ready")}</em>{lineNotice(item, tp)}</div>
            <div className="cart-unit-price"><b className="num">{money(item.unitPrice)}</b>{item.priceChanged
              ? <del className="num" title={tp("cart.oldPrice")}>{money(item.priceSnapshot)}</del>
              : moneyValue(item.compareAtPrice) > moneyValue(item.unitPrice) ? <><del className="num">{money(item.compareAtPrice)}</del><em className="num">-{Math.round((1 - moneyValue(item.unitPrice) / moneyValue(item.compareAtPrice)) * 100)}%</em></> : null}</div>
            <div className="cart-quantity"><button aria-label={tp("cart.decrease")} disabled={busy || item.quantity <= 1} onClick={() => setQuantity(item, -1)}><Minus/></button><b className="num">{item.quantity}</b><button aria-label={tp("cart.increase")} disabled={busy || item.quantity >= item.maxQuantity} onClick={() => setQuantity(item, 1)}>＋</button></div>
            <strong className="cart-line-total num">{money(item.lineTotal)}</strong>
            <button className="cart-remove" aria-label={tp("cart.removeLine", { name: item.productName })} disabled={busy} onClick={() => remove([item.id])}><Trash2/></button>
          </article>)}
          {cart.status === "error" && !data && <div className="cart-empty" role="alert"><AlertTriangle/><h2>{tp("cart.loadError")}</h2><button type="button" className="cart-retry" onClick={() => void cart.refresh()}>{tp("cart.retry")}</button></div>}
          {cart.status === "ready" && !items.length && <div className="cart-empty"><ShoppingCart/><h2>{tp("cart.empty")}</h2><Link to="/recommendations">{tp("cart.startShopping")}</Link></div>}
        </section>
      </div>

      <aside className="cart-side">
        <section className="cart-summary">
          <span className="cart-summary-kicker">{tp("cart.summaryKicker")}</span><h2>{tp("cart.summary")}</h2>
          <section className="cart-code"><h2><Tag/>{tp("cart.coupon.title")}</h2><p>{targetShop ? tp("cart.coupon.forShop", { shop: targetShop.shopName }) : tp("cart.coupon.hint")}</p>
            {couponShops.length > 1 && <select className="cart-code-shop" aria-label={tp("cart.coupon.shop")} value={targetShop?.shopId ?? ""} onChange={(event) => setCouponShop(Number(event.target.value))}>{couponShops.map((group) => <option key={group.shopId} value={group.shopId}>{group.shopName}</option>)}</select>}
            <div><input id="coupon" onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); void applyCoupon(); } }} aria-label={tp("cart.coupon.input")} value={coupon} onChange={(event) => setCoupon(event.target.value)} placeholder={tp("cart.coupon.input")} disabled={!targetShop}/><button type="button" disabled={busy || !targetShop || !coupon.trim()} onClick={() => void applyCoupon()}>{tp("cart.coupon.apply")}</button></div>
            {targetShop && <CartCouponOffers key={targetShop.shopId} group={targetShop} busy={busy} onApply={(code) => void run(() => cart.applyCoupon(targetShop.shopId, code), targetShop.coupon ? tp("cart.coupon.replaced") : tp("cart.coupon.applied"))}/>}
          </section>
          {showSkeleton && !summary ? <><Skeleton height={18}/><Skeleton height={18}/><Skeleton height={24}/></> : <>
          <p><span>{tp("cart.subtotal", { count: data?.counts.selectedQuantity ?? 0 })}</span><b className="num">{money(summary?.subtotal)}</b></p>
          {appliedCoupons.map((group) => { const applied = group.coupon!; return <p key={group.shopId}><span>{tp("cart.coupon.code", { code: applied.code })}{appliedCoupons.length > 1 || groups.length > 1 ? ` · ${group.shopName}` : ""} <button className="cart-clear-code" disabled={busy} onClick={() => run(() => cart.removeCoupon(group.shopId))}>{tp("cart.coupon.remove")}</button>{!applied.applicable && <small className="cart-line-notice">{couponReasonMessage(applied.reason, tp, applied.message)}</small>}</span><b className="cart-saving num">−{money(moneyValue(applied.discountAmount) + moneyValue(applied.shippingDiscountAmount))}</b></p>; })}
          <p><span>{tp("cart.discount")}</span><b className="cart-saving num">−{money(summary?.discount)}</b></p>
          <p><span>{tp("cart.shippingEstimate")}</span><b className="num">{money(summary?.shippingFee)}</b></p>
          {summary && moneyValue(summary.shippingDiscount) > 0 && <p><span>{tp("cart.shippingDiscount")}</span><b className="cart-saving num">−{money(summary.shippingDiscount)}</b></p>}
          <hr/>
          <p className="cart-grand"><span>{tp("cart.grandTotal")}</span><b className="num" aria-live="polite">{money(summary?.grandTotal)}</b></p>
          {saving > 0 && <div className="cart-savings-note"><Tag size={14}/> {tp("cart.saved")} <span className="num">{money(saving)}</span></div>}
          </>}
          <button disabled={busy || !selectedItems.length || blocked || priceChanged} onClick={goCheckout}>{tp("cart.checkout")} <ArrowRight/></button>
          <small className="cart-checkout-note"><ShieldCheck size={14}/> {blocked || priceChanged ? tp("cart.fixBeforeCheckout") : tp("cart.checkoutNote")}</small>
        </section>
      </aside>
    </section>

    {suggestions.length > 0 && <div className="cart-width cart-recommend-wrap"><section className="cart-recommend"><header><h2>{tp("cart.recommend.title")}</h2><Link to={`/shop/${groups[0]?.shopSlug}`}>{tp("cart.recommend.all")} <ArrowRight/></Link></header><div>
      {suggestions.map((product) => {
        const name = localizedText(localeTag, product.nameTh, product.nameEn, product.nameZh);
        const image = product.images.find((photo) => photo.primary) ?? product.images[0];
        const price = moneyValue(product.basePrice ?? product.skus?.[0]?.price);
        const old = moneyValue(product.compareAtPrice ?? product.skus?.[0]?.compareAtPrice);
        return <article key={product.id}>
          <span className="cart-photo cart-photo-real">{image ? <img src={mediaUrl(image.url)} alt={name}/> : null}</span>
          <button type="button" aria-label={tp("cart.recommend.like")} aria-pressed={liked.includes(product.id)} onClick={() => setLiked((current) => current.includes(product.id) ? current.filter((id) => id !== product.id) : [...current, product.id])}><Heart/></button>
          <h3><Link to={`/shop/${groups[0]?.shopSlug}/product/${product.slug}`}>{name}</Link></h3>
          <p><b className="num">{money(price)}</b>{old > price && <><del className="num">{money(old)}</del><em className="num">-{Math.round((1 - price / old) * 100)}%</em></>}</p>
        </article>;
      })}
    </div></section></div>}

    <footer className="cart-footer"><div className="cart-width"><p><Heart/><span><b>{tp("cart.footer.thai")}</b><small>{tp("cart.footer.thaiSub")}</small></span></p><p><ShieldCheck/><span><b>{tp("cart.footer.secure")}</b><small>{tp("cart.footer.secureSub")}</small></span></p><p><Truck/><span><b>{tp("cart.footer.shipping")}</b><small>{tp("cart.footer.shippingSub")}</small></span></p><p><PackageCheck/><span><b>{tp("cart.footer.returns")}</b><small>{tp("cart.footer.returnsSub")}</small></span></p><p><Headphones/><span><b>{tp("cart.footer.help")}</b><small>{tp("cart.footer.helpSub")}</small></span><Link to="/help">{tp("cart.footer.chat")}</Link></p></div></footer>
    {notice && <div className="cart-toast" role="status">{notice}</div>}
  </main>;
}
