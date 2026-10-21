import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AlertTriangle, ArrowLeft, CreditCard, MapPin, ShieldCheck, Store, Tag, Truck } from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { useCart } from "../contexts/CartContext";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { Skeleton } from "../components/feedback/Skeleton";
import ThaiAddressFields, { emptyThaiAddress, type ThaiAddressValue } from "../components/ThaiAddressFields";
import {
  cartErrorMessage,
  checkoutKey,
  clearCheckoutForm,
  clearCheckoutKey,
  formatBaht,
  loadCheckoutForm,
  saveCheckoutForm,
} from "../components/cart/cartFormat";
import { ApiError, checkoutApi, mediaUrl, moneyValue, type CheckoutAddress } from "../services/api";
import "../styles/cart.css";
import "../styles/checkout.css";

type ContactFields = { recipientName: string; phone: string; addressLine1: string; addressLine2: string };
const emptyContact: ContactFields = { recipientName: "", phone: "", addressLine1: "", addressLine2: "" };
const PHONE = /^0\d{8,9}$/;
const POSTAL = /^\d{5}$/;

export default function CheckoutPage() {
  const { user, loading: authLoading } = useAuth();
  const cart = useCart();
  const navigate = useNavigate();
  const { tp, localeTag } = usePublicTranslation();
  const [contact, setContact] = useState<ContactFields>(emptyContact);
  const [area, setArea] = useState<ThaiAddressValue>(emptyThaiAddress);
  const [note, setNote] = useState("");
  const [acceptTerms, setAcceptTerms] = useState(false);
  const termsRef = useRef<HTMLInputElement>(null);
  const [shippingBusy, setShippingBusy] = useState<number | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [checking, setChecking] = useState(true);
  const [placing, setPlacing] = useState(false);
  const [problem, setProblem] = useState("");
  const [formReady, setFormReady] = useState(false);
  const money = (value: unknown) => formatBaht(value as number, localeTag);

  // checkout needs a signed-in buyer (CHECKOUT_CREATE); guests go to login and come back here
  useEffect(() => {
    if (!authLoading && !user) {
      clearCheckoutForm();
      navigate(`/login?next=${encodeURIComponent("/checkout")}`, { replace: true });
    }
  }, [authLoading, user, navigate]);

  // admin / accounts without cart permission: /cart shows the friendly blocked message
  useEffect(() => {
    if (cart.access === "blocked") navigate("/cart", { replace: true });
  }, [cart.access, navigate]);

  /** Restore address/note for this buyer (never acceptTerms). */
  useEffect(() => {
    if (authLoading || !user) return;
    const saved = loadCheckoutForm(user.id);
    if (saved) {
      setContact({ ...emptyContact, ...saved.contact });
      setArea({ ...emptyThaiAddress, ...saved.area });
      setNote(saved.note ?? "");
    }
    setFormReady(true);
  }, [authLoading, user?.id]);

  /** Persist form while typing so a trip to /cart does not wipe it. */
  useEffect(() => {
    if (!formReady || !user) return;
    saveCheckoutForm(user.id, { contact, area, note });
  }, [formReady, user?.id, contact, area, note]);

  /** Re-validate on entry: price/stock/coupon problems send the buyer back to the cart. */
  useEffect(() => {
    if (authLoading || !user || cart.mode !== "user") return;
    let alive = true;
    setChecking(true);
    cart.validate()
      .then((result) => {
        if (!alive) return;
        if (!result.valid) navigate("/cart", { replace: true, state: { checkoutIssues: true } });
      })
      .catch((error) => { if (alive) setProblem(cartErrorMessage(error, tp)); })
      .finally(() => { if (alive) setChecking(false); });
    return () => { alive = false; };
    // run once per sign-in; cart.validate identity changes with every cart update
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authLoading, user?.id, cart.mode]);

  const groups = useMemo(() => (cart.cart?.groups ?? [])
    .map((group) => ({ ...group, items: group.items.filter((line) => line.selected) }))
    .filter((group) => group.items.length > 0), [cart.cart]);
  const summary = cart.cart?.summary;

  const check = () => {
    const next: Record<string, string> = {};
    if (!contact.recipientName.trim()) next.recipientName = tp("checkout.errors.required");
    if (!PHONE.test(contact.phone.replace(/[\s-]/g, ""))) next.phone = tp("checkout.errors.phone");
    if (!contact.addressLine1.trim()) next.addressLine1 = tp("checkout.errors.required");
    if (!area.province) next.province = tp("checkout.errors.required");
    if (!area.district) next.district = tp("checkout.errors.required");
    if (!POSTAL.test(area.postalCode)) next.postalCode = tp("checkout.errors.postal");
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const chooseShipping = async (shopId: number, methodId: string) => {
    if (placing || shippingBusy != null) return;
    setShippingBusy(shopId);
    setProblem("");
    try {
      await cart.chooseShipping(shopId, methodId);
    } catch (error) {
      setProblem(cartErrorMessage(error, tp));
    } finally {
      setShippingBusy(null);
    }
  };

  const focusTerms = () => {
    termsRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
    termsRef.current?.focus({ preventScroll: true });
  };

  const place = async (event: FormEvent) => {
    event.preventDefault();
    if (placing) return;
    if (!acceptTerms) { setProblem(tp("checkout.terms.required")); focusTerms(); return; }
    if (!check()) return;
    setPlacing(true);
    setProblem("");
    try {
      // validate step right before placing: catches price/stock changes since the page opened
      const validation = await cart.validate();
      if (!validation.valid) { navigate("/cart", { state: { checkoutIssues: true } }); return; }
      const address: CheckoutAddress = {
        recipientName: contact.recipientName.trim(),
        phone: contact.phone.replace(/[\s-]/g, ""),
        addressLine1: contact.addressLine1.trim(),
        addressLine2: contact.addressLine2.trim() || undefined,
        subdistrict: area.subdistrict || undefined,
        district: area.district,
        province: area.province,
        postalCode: area.postalCode,
      };
      const result = await checkoutApi.place(checkoutKey(), address, note.trim(), acceptTerms);
      clearCheckoutKey();
      clearCheckoutForm();
      await cart.refresh();
      navigate(`/checkout/success?orders=${result.orders.map((order) => order.id).join(",")}`, { replace: true, state: { result } });
    } catch (error) {
      if (error instanceof ApiError && (error.code === "CHECKOUT_INVALID" || error.code === "INSUFFICIENT_STOCK")) {
        clearCheckoutKey();
        await cart.refresh();
        navigate("/cart", { state: { checkoutIssues: true } });
        return;
      }
      if (error instanceof ApiError && error.code === "OWN_SHOP_PURCHASE_FORBIDDEN") {
        // keep Idempotency-Key; buyer must remove own-shop lines and retry
        setProblem(tp("cart.own.checkout"));
        return;
      }
      if (error instanceof ApiError && error.code === "TERMS_NOT_ACCEPTED") {
        setAcceptTerms(false);
        setProblem(tp("checkout.terms.rejected"));
        focusTerms();
        return;
      }
      if (error instanceof ApiError && error.code === "VALIDATION_ERROR") {
        const mapped: Record<string, string> = {};
        error.fieldErrors.forEach((field) => { mapped[field.field.replace("shippingAddress.", "")] = field.message; });
        setErrors(mapped);
      }
      // network error / CHECKOUT_CONFLICT keep the same Idempotency-Key, so pressing again cannot create a second order
      setProblem(cartErrorMessage(error, tp));
    } finally {
      setPlacing(false);
    }
  };

  const loading = authLoading || checking || cart.status === "loading";
  const field = (key: keyof ContactFields, label: string, props: Record<string, unknown> = {}) => (
    <label className={`checkout-field ${errors[key] ? "has-error" : ""}`}>
      <span>{label}</span>
      <input value={contact[key]} onChange={(event) => setContact((current) => ({ ...current, [key]: event.target.value }))} aria-invalid={Boolean(errors[key])} {...props}/>
      {errors[key] ? <small role="alert">{errors[key]}</small> : null}
    </label>
  );

  const policyLinks = groups.map((group, index) => {
    const slug = group.shopSlug?.trim();
    const name = group.shopName;
    const node = slug
      ? <a key={group.shopId} href={`/shop/${slug}/policies`} target="_blank" rel="noopener noreferrer">{name}</a>
      : <span key={group.shopId}>{name}</span>;
    return index === 0 ? node : <span key={`sep-${group.shopId}`}>, {node}</span>;
  });

  return <main className="cart-page checkout-page">
    <div className="cart-heading cart-width"><Link to="/cart"><ArrowLeft size={16}/> {tp("checkout.backToCart")}</Link><ol aria-label={tp("checkout.steps.label")}><li><span>1</span> {tp("checkout.steps.cart")}</li><li aria-current="step"><span>2</span> {tp("checkout.steps.checkout")}</li><li><span>3</span> {tp("checkout.steps.done")}</li></ol></div>
    <div className="cart-page-title cart-width"><div className="cart-title"><CreditCard/><div><h1>{tp("checkout.title")}</h1><p>{tp("checkout.subtitle")}</p></div></div></div>

    {loading ? <section className="cart-content cart-width" aria-busy="true"><div className="cart-main-column"><div className="checkout-card"><Skeleton height={20} width="40%"/><Skeleton height={44}/><Skeleton height={44}/><Skeleton height={44}/></div><div className="checkout-card"><Skeleton height={80}/></div></div><aside className="cart-side"><div className="cart-summary"><Skeleton height={18}/><Skeleton height={18}/><Skeleton height={28}/></div></aside></section>
    : !groups.length ? <section className="cart-content cart-width"><div className="cart-main-column"><div className="cart-empty"><AlertTriangle/><h2>{tp("checkout.nothingSelected")}</h2><Link to="/cart">{tp("checkout.backToCart")}</Link></div></div></section>
    : <form className="cart-content cart-width" onSubmit={place} noValidate>
      <div className="cart-main-column">
        <section className="checkout-card" aria-labelledby="checkout-address">
          <h2 id="checkout-address"><MapPin size={18}/>{tp("checkout.address.title")}</h2>
          <div className="checkout-grid">
            {field("recipientName", tp("checkout.address.recipient"), { autoComplete: "name", maxLength: 150 })}
            {field("phone", tp("checkout.address.phone"), { autoComplete: "tel", inputMode: "tel", maxLength: 15 })}
          </div>
          {field("addressLine1", tp("checkout.address.line1"), { autoComplete: "address-line1", maxLength: 255 })}
          {field("addressLine2", tp("checkout.address.line2"), { autoComplete: "address-line2", maxLength: 255 })}
          <ThaiAddressFields value={area} onChange={setArea} disabled={placing} errors={errors}/>
          <label className="checkout-field"><span>{tp("checkout.note")}</span><textarea value={note} maxLength={1000} rows={2} onChange={(event) => setNote(event.target.value)}/></label>
        </section>

        {groups.map((group) => {
          const options = group.shipping?.options ?? [];
          const busy = shippingBusy === group.shopId;
          return <section key={group.shopId} className="checkout-card checkout-shop" aria-label={group.shopName}>
            <header className="cart-shop-head"><Store size={16}/><span>{group.shopName}</span></header>
            <ul className="checkout-lines">
              {group.items.map((line) => <li key={line.id}>
                <span className="cart-photo cart-photo-real">{line.imageUrl ? <img src={mediaUrl(line.imageUrl)} alt={line.productName}/> : null}</span>
                <div><b>{line.productName}</b>{line.optionLabel ? <em>{line.optionLabel}</em> : null}</div>
                <span className="num">× {line.quantity}</span>
                <b className="num">{money(line.lineTotal)}</b>
              </li>)}
            </ul>
            {options.length > 0 ? (
              <div className="checkout-shipping" aria-label={tp("checkout.shippingTitle")}>
                <h3 className="checkout-shipping-title"><Truck size={14}/>{tp("checkout.shippingTitle")}</h3>
                <div className="checkout-shipping-list" role="radiogroup">
                  {options.map((option) => {
                    const selected = option.id === group.shipping?.methodId;
                    const days = option.estimatedDaysMin != null && option.estimatedDaysMax != null
                      ? tp("checkout.shippingDays", { min: option.estimatedDaysMin, max: option.estimatedDaysMax })
                      : null;
                    return <label key={option.id} className={`checkout-payment-option${selected ? " is-selected" : ""}`}>
                      <input
                        type="radio"
                        name={`shipping-${group.shopId}`}
                        checked={selected}
                        disabled={placing || busy}
                        onChange={() => void chooseShipping(group.shopId, option.id)}
                      />
                      <span>
                        <b>{option.name}</b>
                        {days ? <small>{days}</small> : null}
                      </span>
                      <b className="num checkout-shipping-fee">{money(option.fee)}</b>
                    </label>;
                  })}
                </div>
              </div>
            ) : (
              <>
                <p className="checkout-meta"><Truck size={14}/><span>{group.shipping?.methodName ?? tp("cart.shipping.auto")}</span><b className="num">{money(group.totals.shippingFee)}</b></p>
                <small className="checkout-shipping-hint">{tp("checkout.shippingDefault")}</small>
              </>
            )}
            {group.coupon ? <p className="checkout-meta"><Tag size={14}/><span className="num">{group.coupon.code}</span><b className="cart-saving num">−{money(moneyValue(group.totals.discount) + moneyValue(group.totals.shippingDiscount))}</b></p> : null}
            <p className="cart-shop-total"><span>{tp("cart.shopTotal")}</span><b className="num">{money(group.totals.grandTotal)}</b></p>
          </section>;
        })}

        <section className="checkout-card" aria-labelledby="checkout-payment">
          <h2 id="checkout-payment"><CreditCard size={18}/>{tp("checkout.payment.title")}</h2>
          <label className="checkout-payment-option is-selected"><input type="radio" name="payment" checked readOnly/><span><b>{tp("checkout.payment.transfer")}</b><small>{tp("checkout.payment.transferHint")}</small></span></label>
        </section>

        <section className="checkout-card" aria-labelledby="checkout-terms">
          <h2 id="checkout-terms">{tp("checkout.terms.title")}</h2>
          <label className={`checkout-payment-option${acceptTerms ? " is-selected" : ""}`}>
            <input ref={termsRef} type="checkbox" checked={acceptTerms} disabled={placing} onChange={(event) => setAcceptTerms(event.target.checked)}/>
            <span>
              <b>{tp("checkout.terms.label")}</b>
              {policyLinks.length > 0 ? <small className="checkout-terms-shops">{tp("checkout.terms.policiesOf")}: {policyLinks}</small> : null}
            </span>
          </label>
        </section>
      </div>

      <aside className="cart-side">
        <section className="cart-summary">
          <h2>{tp("cart.summary")}</h2>
          <p><span>{tp("cart.subtotal", { count: cart.cart?.counts.selectedQuantity ?? 0 })}</span><b className="num">{money(summary?.subtotal)}</b></p>
          <p><span>{tp("cart.discount")}</span><b className="cart-saving num">−{money(summary?.discount)}</b></p>
          <p><span>{tp("checkout.shipping")}</span><b className="num">{money(summary?.shippingFee)}</b></p>
          {summary && moneyValue(summary.shippingDiscount) > 0 && <p><span>{tp("cart.shippingDiscount")}</span><b className="cart-saving num">−{money(summary.shippingDiscount)}</b></p>}
          <hr/>
          <p className="cart-grand"><span>{tp("cart.grandTotal")}</span><b className="num" aria-live="polite">{money(summary?.grandTotal)}</b></p>
          {problem ? <p className="checkout-problem" role="alert"><AlertTriangle size={14}/>{problem}</p> : null}
          <button type="submit" disabled={placing || !acceptTerms}>{placing ? tp("checkout.placing") : tp("checkout.place")}</button>
          {!acceptTerms ? <small className="checkout-terms-hint">{tp("checkout.terms.required")}</small> : null}
          <small className="cart-checkout-note"><ShieldCheck size={14}/> {tp("checkout.placeNote", { count: groups.length })}</small>
        </section>
      </aside>
    </form>}
  </main>;
}
