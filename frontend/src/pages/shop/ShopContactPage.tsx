import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  BadgeCheck, Check, Clock, Copy, ExternalLink, Facebook, Globe, Headset, Instagram,
  Mail, MapPin, MessageCircle, Music2, Phone, type LucideIcon,
} from "lucide-react";
import ShopLogo from "../../components/ShopLogo";
import { todayHours } from "../../components/shop/shopHours";
import { useShopPublic, useShopCrumb } from "../../contexts/ShopPublicContext";
import { useDocumentMeta } from "../../hooks/useDocumentMeta";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { mediaUrl, type PublicShop } from "../../services/api";
import { shopPoliciesPath, shopProductsPath } from "../../services/shopRoutes";
import { heroSlideUrls } from "../../components/shop/sections/HeroSlider";
import { resolveStorefrontTheme } from "../../components/shop/ShopThemeShell";
import "../../styles/shop-contact.css";

type ChannelKey = "phone" | "email" | "line" | "facebook" | "instagram" | "tiktok" | "website";
type ActionKey = "call" | "email" | "line" | "profile" | "visit";

type ShopContactChannel = {
  key: ChannelKey;
  value: string;
  display: string;
  href?: string;
  external: boolean;
  copy?: string;
  mono?: boolean;
};

const DAY_KEYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"] as const;

const CHANNEL_ICONS: Record<ChannelKey, LucideIcon> = {
  phone: Phone, email: Mail, line: MessageCircle, facebook: Facebook,
  instagram: Instagram, tiktok: Music2, website: Globe,
};
const CHANNEL_ACTION: Record<ChannelKey, ActionKey> = {
  phone: "call", email: "email", line: "line", facebook: "profile",
  instagram: "profile", tiktok: "profile", website: "visit",
};

const HTTP_RE = /^https?:\/\//i;
const SCHEME_RE = /^[a-z][a-z0-9+.-]*:/i;

/** http(s) as-is; bare domain → https; other schemes (javascript:, data:…) → no link. */
function safeWebHref(raw: string): string | undefined {
  const v = raw.trim();
  if (!v) return undefined;
  if (HTTP_RE.test(v)) return v;
  if (SCHEME_RE.test(v) && !/^[\w-]+(\.[\w-]+)+(:\d+)?(\/|$)/.test(v)) return undefined;
  return `https://${v.replace(/^\/+/, "")}`;
}

function displayUrl(raw: string) {
  return raw.trim().replace(HTTP_RE, "").replace(/^www\./i, "").replace(/\/+$/, "");
}

/** LINE OA "@id" → line.me/R/ti/p/%40id ; personal id → line.me/ti/p/~id ; URLs as-is. */
function lineHref(raw: string): string | undefined {
  const v = raw.trim().replace(/\s+/g, "");
  if (!v) return undefined;
  if (HTTP_RE.test(v)) return v;
  if (/^(line\.me|lin\.ee)\//i.test(v)) return `https://${v}`;
  if (SCHEME_RE.test(v)) return undefined;
  return v.startsWith("@")
    ? `https://line.me/R/ti/p/${encodeURIComponent(v)}`
    : `https://line.me/ti/p/~${encodeURIComponent(v)}`;
}

const SOCIAL_BASE = {
  facebook: "https://www.facebook.com/",
  instagram: "https://www.instagram.com/",
  tiktok: "https://www.tiktok.com/@",
} as const;
const SOCIAL_DOMAIN_RE = /^([\w-]+\.)*(facebook|fb|instagram|tiktok)\.com(\/|$)/i;

function socialChannel(key: keyof typeof SOCIAL_BASE, raw?: string | null): ShopContactChannel | null {
  const v = raw?.trim();
  if (!v) return null;
  if (HTTP_RE.test(v) || SOCIAL_DOMAIN_RE.test(v)) {
    return { key, value: v, display: displayUrl(v), href: safeWebHref(v), external: true };
  }
  const handle = v.replace(/^@/, "");
  if (!handle || SCHEME_RE.test(handle)) return { key, value: v, display: v, external: false };
  return {
    key,
    value: v,
    display: key === "facebook" ? handle : `@${handle}`,
    href: `${SOCIAL_BASE[key]}${encodeURIComponent(handle)}`,
    external: true,
  };
}

function buildChannels(shop: PublicShop): ShopContactChannel[] {
  const channels: ShopContactChannel[] = [];
  const phone = shop.phone?.trim();
  if (phone) {
    const tel = phone.replace(/[^\d+]/g, "");
    channels.push({ key: "phone", value: phone, display: phone, href: tel ? `tel:${tel}` : undefined, external: false, copy: phone, mono: true });
  }
  const line = shop.lineId?.trim();
  if (line) {
    channels.push({ key: "line", value: line, display: HTTP_RE.test(line) ? displayUrl(line) : line, href: lineHref(line), external: true, copy: line });
  }
  const email = shop.email?.trim();
  if (email) {
    const valid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    channels.push({ key: "email", value: email, display: email, href: valid ? `mailto:${email}` : undefined, external: false, copy: email });
  }
  for (const key of ["facebook", "instagram", "tiktok"] as const) {
    const raw = key === "facebook" ? shop.facebookUrl : key === "instagram" ? shop.instagramUrl : shop.tiktokUrl;
    const channel = socialChannel(key, raw);
    if (channel) channels.push(channel);
  }
  const website = shop.website?.trim();
  if (website) {
    const href = safeWebHref(website);
    channels.push({ key: "website", value: website, display: displayUrl(website), href, external: true, copy: href ?? website });
  }
  return channels;
}

function externalProps(external: boolean) {
  return external ? { target: "_blank", rel: "noopener noreferrer" } : {};
}

export default function ShopContactPage() {
  const { slug, shop, notify } = useShopPublic();
  const { tp } = usePublicTranslation();
  const label = tp("shop.contact.title");
  useShopCrumb(label);
  useDocumentMeta({ title: `${label} · ${shop.name}` });

  const channels = useMemo(() => buildChannels(shop), [shop]);
  const hours = shop.openingHours?.length === 7 ? shop.openingHours : null;
  const today = useMemo(() => todayHours(hours), [hours]);
  const address = [shop.address, shop.subdistrict, shop.district, shop.province, shop.postalCode]
    .map((part) => part?.trim())
    .filter(Boolean)
    .join(" ");
  const area = [shop.district, shop.province].map((part) => part?.trim()).filter(Boolean).join(" ");
  // Item 1: first hero slide first; coverUrl only for shops that have no slides yet.
  const firstSlide = useMemo(() => heroSlideUrls(resolveStorefrontTheme(shop).config.hero?.slides)[0], [shop]);
  const cover = firstSlide ?? (shop.coverUrl ? mediaUrl(shop.coverUrl) : "");
  const approved = shop.status === "APPROVED";
  const hasAside = Boolean(hours || address);
  const primary =
    channels.find((c) => c.key === "line" && c.href) ??
    channels.find((c) => c.key === "phone" && c.href) ??
    channels.find((c) => c.href);

  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const copyTimer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(copyTimer.current), []);
  const copyText = async (key: string, text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedKey(key);
      notify(tp("shop.contact.copied"));
      window.clearTimeout(copyTimer.current);
      copyTimer.current = window.setTimeout(() => setCopiedKey(null), 1600);
    } catch {
      notify(tp("shop.contact.copyFailed"));
    }
  };

  const statusBadge = today ? (
    <span className={`shop-status-badge ${today.openNow ? "is-success" : "is-danger"}`}>
      {today.openNow ? tp("shop.openNow") : tp("shop.closedNow")}
    </span>
  ) : null;

  const PrimaryIcon = primary ? CHANNEL_ICONS[primary.key] : null;

  return (
    <>
      <section className={`shop-mall-hero shop-contact-hero recommend-width${cover ? " has-cover" : ""}`}>
        {cover ? (
          <>
            <div className="shop-mall-hero-cover" style={{ backgroundImage: `url(${cover})` }} aria-hidden="true" />
            <div className="shop-mall-hero-shade" aria-hidden="true" />
          </>
        ) : null}
        <div className="shop-mall-hero-body">
          <div className="shop-mall-hero-brand">
            <div className="shop-mall-logo">
              <ShopLogo src={shop.logoUrl} name={shop.name} />
            </div>
            <div className="shop-mall-hero-copy">
              <div className="shop-mall-title-row">
                <h1>{shop.name}</h1>
                {approved ? <span className="shop-official-badge"><BadgeCheck />{tp("shop.mallBadge")}</span> : null}
              </div>
              <p className="shop-mall-sub">{tp("shop.contact.subtitle")}</p>
              {(today || area) && (
                <div className="shop-contact-meta">
                  {statusBadge}
                  {today ? (
                    <span>
                      <Clock aria-hidden="true" />
                      {tp("shop.hoursToday")}{" "}
                      {today.hour.open
                        ? <span className="num">{today.hour.start} – {today.hour.end}</span>
                        : tp("shop.hoursClosed")}
                    </span>
                  ) : null}
                  {area ? <span><MapPin aria-hidden="true" />{area}</span> : null}
                </div>
              )}
            </div>
          </div>
          <div className="shop-mall-actions">
            {primary?.href && PrimaryIcon ? (
              <a className="shop-mall-btn is-primary" href={primary.href} {...externalProps(primary.external)}>
                <PrimaryIcon />{tp(`shop.contact.actions.${CHANNEL_ACTION[primary.key]}`)}
              </a>
            ) : null}
            <Link className="shop-mall-btn is-outline" to={shopPoliciesPath(slug)}>{tp("shop.tabs.policies")}</Link>
          </div>
        </div>
      </section>

      <div className={`shop-contact recommend-width${hasAside ? " has-aside" : ""}`}>
        <section className="shop-contact-panel shop-contact-main" aria-labelledby="shop-contact-channels">
          <header className="shop-section-head">
            <h2 id="shop-contact-channels">{tp("shop.contact.channelsTitle")}</h2>
          </header>
          {channels.length === 0 ? (
            <div className="shop-contact-empty">
              <span className="shop-contact-icon" aria-hidden="true"><Headset /></span>
              <h3>{tp("shop.contact.channelsPending")}</h3>
              <p>{tp("shop.contact.emptyHint")}</p>
              <div className="shop-contact-actions">
                <Link className="shop-contact-action" to={shopProductsPath(slug)}>{tp("shop.tabs.products")}</Link>
                <Link className="shop-contact-action" to={shopPoliciesPath(slug)}>{tp("shop.tabs.policies")}</Link>
              </div>
            </div>
          ) : (
            <ul className="shop-contact-grid">
              {channels.map((channel) => {
                const Icon = CHANNEL_ICONS[channel.key];
                const channelLabel = tp(`shop.contact.channels.${channel.key}`);
                const copied = copiedKey === channel.key;
                return (
                  <li key={channel.key} className="shop-contact-card" data-channel={channel.key}>
                    <span className="shop-contact-icon" aria-hidden="true"><Icon /></span>
                    <div className="shop-contact-body">
                      <span className="shop-contact-label">{channelLabel}</span>
                      <span className={`shop-contact-value${channel.mono ? " num" : ""}`} title={channel.value}>
                        {channel.display}
                      </span>
                    </div>
                    {(channel.href || channel.copy) && (
                      <div className="shop-contact-actions">
                        {channel.href ? (
                          <a className="shop-contact-action" href={channel.href} {...externalProps(channel.external)}>
                            {tp(`shop.contact.actions.${CHANNEL_ACTION[channel.key]}`)}
                            {channel.external ? <ExternalLink aria-hidden="true" /> : null}
                          </a>
                        ) : null}
                        {channel.copy ? (
                          <button
                            type="button"
                            className={`shop-contact-copy${copied ? " is-copied" : ""}`}
                            aria-label={tp("shop.contact.copyAria", { label: channelLabel })}
                            title={tp("shop.contact.copy")}
                            onClick={() => copyText(channel.key, channel.copy!)}
                          >
                            {copied ? <Check /> : <Copy />}
                          </button>
                        ) : null}
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        {hasAside && (
          <aside className="shop-contact-aside">
            {hours ? (
              <section className="shop-contact-panel" aria-labelledby="shop-contact-hours">
                <header className="shop-section-head">
                  <h2 id="shop-contact-hours"><Clock aria-hidden="true" />{tp("shop.hoursTitle")}</h2>
                  {statusBadge}
                </header>
                <ul className="shop-contact-hours">
                  {DAY_KEYS.map((day, index) => {
                    const item = hours[index];
                    const isToday = today?.dayIndex === index;
                    const cls = [isToday ? "is-today" : "", item.open ? "" : "is-closed"].filter(Boolean).join(" ");
                    return (
                      <li key={day} className={cls || undefined} aria-current={isToday ? "date" : undefined}>
                        <span>{tp(`shop.hours.${day}`)}</span>
                        {item.open
                          ? <span className="num">{item.start} – {item.end}</span>
                          : <span>{tp("shop.hoursClosed")}</span>}
                      </li>
                    );
                  })}
                </ul>
              </section>
            ) : null}
            {address ? (
              <section className="shop-contact-panel" aria-labelledby="shop-contact-address">
                <header className="shop-section-head">
                  <h2 id="shop-contact-address"><MapPin aria-hidden="true" />{tp("shop.contact.addressTitle")}</h2>
                </header>
                <address className="shop-contact-address">{address}</address>
                <div className="shop-contact-actions">
                  <a
                    className="shop-contact-action"
                    href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {tp("shop.contact.openMap")}<ExternalLink aria-hidden="true" />
                  </a>
                  <button
                    type="button"
                    className={`shop-contact-copy${copiedKey === "address" ? " is-copied" : ""}`}
                    aria-label={tp("shop.contact.copyAria", { label: tp("shop.contact.addressTitle") })}
                    title={tp("shop.contact.copy")}
                    onClick={() => copyText("address", address)}
                  >
                    {copiedKey === "address" ? <Check /> : <Copy />}
                  </button>
                </div>
              </section>
            ) : null}
          </aside>
        )}
      </div>
    </>
  );
}
