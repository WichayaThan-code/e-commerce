import { useMemo, useState } from "react";
import "../styles/member-home.css";
import { useAuth } from "../contexts/AuthContext";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import {
  ArrowRight,
  CircleEllipsis,
  Gift,
  Headphones,
  Heart,
  PackageCheck,
  ShieldCheck,
  ShoppingBag,
  Store,
  Truck,
} from "lucide-react";

const benefitIcons = [Truck, ShieldCheck, Store, PackageCheck, Headphones];

type HomeBenefit = { title: string; text: string };
type HomeProductMeta = { price: number; old: number; rating: string; sold: number; emoji: string; bg: string };

export default function MarketplacePage() {
  const [notice, setNotice] = useState("");
  const { user } = useAuth();
  const { tp, localeTag } = usePublicTranslation();

  const benefits = tp("home.benefits", { returnObjects: true }) as unknown as HomeBenefit[];
  const categories = tp("home.categories", { returnObjects: true }) as unknown as string[];
  const categoryEmojis = tp("home.categoryEmojis", { returnObjects: true }) as unknown as string[];
  const openStoreBullets = tp("home.openStoreBullets", { returnObjects: true }) as unknown as string[];

  const products = useMemo(() => {
    const productMeta = tp("home.productMeta", { returnObjects: true }) as unknown as HomeProductMeta[];
    const productNames = tp("home.products", { returnObjects: true }) as unknown as string[];
    return productMeta.map((meta, index) => ({ ...meta, name: productNames[index] ?? "" }));
  }, [tp]);

  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 2200);
  };

  const displayName = user ? user.firstName || user.email : "";

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
          <a href="#categories">
            {tp("common.viewAll")} <ArrowRight size={15} />
          </a>
        </div>
        <div className="category-grid">
          {categories.map((label, i) => {
            const emoji = categoryEmojis[i];
            const isMore = i === categories.length - 1;
            return (
              <button
                className={`category ${isMore ? "category-more" : ""}`}
                key={label}
                onClick={() => notify(tp("home.pickCategory", { name: label.replace("\n", " ") }))}
              >
                <span>{isMore ? <CircleEllipsis /> : emoji}</span>
                <b>
                  {label.split("\n").map((x, j) => (
                    <span key={j}>{x}</span>
                  ))}
                </b>
              </button>
            );
          })}
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
            <button onClick={() => notify(tp("home.promoThaiToast"))}>
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
            <button onClick={() => notify(tp("home.promoGadgetToast"))}>
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
            <button onClick={() => notify(tp("home.promoBeautyToast"))}>
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
          <div className="product-grid">
            {products.map((p) => {
              const discount = Math.round((1 - p.price / p.old) * 100);
              return (
                <article className="product-card" key={p.name}>
                  <button className="heart" aria-label={tp("home.addFavorite")} onClick={() => notify(tp("home.addedFavorite"))}>
                    <Heart size={17} />
                  </button>
                  <button
                    className="product-image"
                    style={{ background: p.bg }}
                    onClick={() => notify(tp("home.addedCart"))}
                  >
                    <span>{p.emoji}</span>
                  </button>
                  <div className="product-info">
                    <h3>{p.name}</h3>
                    <div>
                      <strong>฿{p.price.toLocaleString(localeTag)}</strong>
                      <del>฿{p.old.toLocaleString(localeTag)}</del>
                      <em>-{discount}%</em>
                    </div>
                    <small>
                      <b>★ {p.rating}</b> ({p.sold.toLocaleString(localeTag)})
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
