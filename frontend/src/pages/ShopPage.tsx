import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { MapPin } from "lucide-react";
import ShopLogo from "../components/ShopLogo";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import recommendReference from "../assets/images/references/storefront/recommend-product.png";
import { ApiError, mediaUrl, shopApi, type PublicShop } from "../services/api";
import "../styles/recommended-products.css";
import "../styles/shop-page.css";

const sampleMeta = [
  { price: 290, sold: 120, emoji: "👜", tone: "#f3eee8" },
  { price: 89, sold: 320, emoji: "🍘", tone: "#fff4dd" },
  { price: 590, sold: 85, emoji: "🧴", tone: "#f3f1ef" },
  { price: 450, sold: 64, emoji: "🧣", tone: "#f7efe6" },
  { price: 350, sold: 48, emoji: "🎁", tone: "#fff0e8" },
  { price: 180, sold: 210, emoji: "🍵", tone: "#eef6e8" },
  { price: 350, sold: 73, emoji: "🪴", tone: "#eef6f1" },
  { price: 259, sold: 156, emoji: "👕", tone: "#eef3fb" },
];

type LoadState = "loading" | "error" | "ok";

export default function ShopPage() {
  const { slug = "" } = useParams();
  const { tp, localeTag } = usePublicTranslation();
  const [shop, setShop] = useState<PublicShop | null>(null);
  const [loadState, setLoadState] = useState<LoadState>("loading");

  const samples = tp("shop.samples", { returnObjects: true }) as unknown as string[];

  useEffect(() => {
    let active = true;
    setShop(null);
    setLoadState("loading");
    shopApi
      .getBySlug(slug)
      .then((data) => {
        if (!active) return;
        setShop(data);
        setLoadState("ok");
      })
      .catch((error) => {
        if (!active) return;
        setLoadState("error");
        if (error instanceof ApiError) console.error(error.message);
      });
    return () => {
      active = false;
    };
  }, [slug]);

  const address = shop
    ? [shop.address, shop.subdistrict, shop.district, shop.province, shop.postalCode].filter(Boolean).join(" ")
    : "";

  const products = useMemo(() => {
    if (!shop) return [];
    const start = [...shop.slug].reduce((total, char) => total + char.charCodeAt(0), 0) % sampleMeta.length;
    return Array.from({ length: 6 }, (_, index) => {
      const meta = sampleMeta[(start + index) % sampleMeta.length];
      const name = samples[(start + index) % samples.length];
      return { ...meta, name };
    });
  }, [samples, shop]);

  const statusLabel =
    shop?.status === "APPROVED"
      ? tp("shop.statusApproved")
      : shop?.status === "REJECTED"
        ? tp("shop.statusRejected")
        : shop?.status === "SUSPENDED"
          ? tp("shop.statusSuspended")
          : tp("shop.statusPending");

  const message = loadState === "loading" ? tp("shop.loading") : loadState === "error" ? tp("shop.loadError") : "";

  return (
    <div className="recommend-page shop-directory">
      <section className="recommend-intro recommend-width">
        <div>
          <p className="recommend-breadcrumb">
            <Link to="/">{tp("common.home")}</Link>
            <span>›</span>
            <Link to="/shops">{tp("shop.breadcrumb")}</Link>
            <span>›</span>
            {shop?.name || tp("common.shopPlaceholder")}
          </p>
          {!shop && (
            <>
              <h1>{message}</h1>
              <p>{tp("shop.defaultDescription")}</p>
            </>
          )}
        </div>
      </section>

      {shop ? (
        <>
          <section className="shop-hero recommend-width">
            <img className="shop-hero-photo" src={shop.coverUrl ? mediaUrl(shop.coverUrl) : recommendReference} alt="" />
            <div className="shop-hero-shade" />
            <div className="shop-hero-panel">
              <div className="shop-profile-mark">
                <ShopLogo src={shop.logoUrl} name={shop.name} />
              </div>
              <div className="shop-hero-copy">
                <span className="shop-chip">{shop.category}</span>
                <h1>{shop.name}</h1>
                {shop.nameEn && <p className="shop-profile-en">{shop.nameEn}</p>}
                <p>{shop.description || tp("shop.noDescription")}</p>
              </div>
            </div>
          </section>
          <ul className="shop-facts recommend-width">
            <li>
              <MapPin />
              <span>{address || tp("common.noAddress")}</span>
            </li>
            <li>
              <b>{tp("common.area")}</b>
              <span>{[shop.district, shop.province].filter(Boolean).join(" ") || "—"}</span>
            </li>
            <li>
              <b>{tp("common.status")}</b>
              <span>{statusLabel}</span>
            </li>
          </ul>

          <section className="shop-products recommend-width">
            <header>
              <h2>{tp("shop.productsTitle")}</h2>
              <p>{tp("shop.productsHint")}</p>
            </header>
            <div className="shop-product-grid">
              {products.map((product) => (
                <article key={product.name}>
                  <div style={{ background: product.tone }}>{product.emoji}</div>
                  <h3>{product.name}</h3>
                  <p>
                    <b>฿{product.price.toLocaleString(localeTag)}</b>
                    <span>
                      {tp("common.sold")} {product.sold.toLocaleString(localeTag)}
                    </span>
                  </p>
                </article>
              ))}
            </div>
          </section>
        </>
      ) : (
        <section className="recommend-width">
          <article className="shop-list-empty">{message}</article>
        </section>
      )}
    </div>
  );
}
