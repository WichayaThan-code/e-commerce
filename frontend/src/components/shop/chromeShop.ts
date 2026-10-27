import { mediaUrl, type PublicShop } from "../../services/api";
import { gradientCss, type ShopBlockBackground, type ThemeGradient } from "../../theme/shopTheme";

const THEME_MEDIA_PATH = /^\/api\/shops\/[A-Za-z0-9_-]+\/theme-media\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * The only media URLs chrome ever renders: the API-derived `/api/shops/{slug}/theme-media/{uuid}` path (never a
 * merchant string), resolved against the API origin. Anything else returns null.
 */
export function themeMediaSrc(url: string | undefined | null): string | null {
  const value = url?.trim();
  if (!value || !THEME_MEDIA_PATH.test(value)) return null;
  return mediaUrl(value);
}

export type ChromeBackgroundInput = {
  background: ShopBlockBackground;
  gradient?: ThemeGradient;
  backgroundUrl?: string;
  overlay?: number;
};

/** Class + inline CSS variables for a header / footer background token (structured values only, never merchant CSS). */
export function chromeBackground(input: ChromeBackgroundInput): { className: string; style: Record<string, string> } {
  const style: Record<string, string> = {};
  let kind = input.background.toLowerCase();
  if (input.background === "GRADIENT") {
    const gradient = gradientCss(input.gradient);
    if (gradient) style["--shop-chrome-gradient"] = gradient;
  }
  if (input.background === "IMAGE") {
    const src = themeMediaSrc(input.backgroundUrl);
    if (src) {
      style["--shop-chrome-image"] = `url("${src}")`;
      style["--shop-chrome-overlay"] = String(Math.max(0, Math.min(80, input.overlay ?? 0)) / 100);
    } else {
      kind = "none";
    }
  }
  return { className: `shop-chrome-bg-${kind}`, style };
}

/**
 * AG5 header / footer data taken from PublicShop ONLY. `PublicShop.phone` / `email` are the shop's public contact
 * (publicPhone / publicEmail on the backend, never the owner's private phone / email), `address*` is the shop address.
 * Nothing else about the owner exists on PublicShop, so the footer cannot leak it.
 */
export type ShopContactLines = { phone?: string; email?: string; address?: string };

export function shopContactLines(shop: PublicShop): ShopContactLines {
  const phone = shop.phone?.trim();
  const email = shop.email?.trim();
  const address = [shop.address, shop.subdistrict, shop.district, shop.province, shop.postalCode]
    .map((part) => part?.trim())
    .filter(Boolean)
    .join(" ");
  return {
    ...(phone ? { phone } : {}),
    ...(email && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? { email } : {}),
    ...(address ? { address } : {}),
  };
}

export type ShopSocialKey = "facebook" | "instagram" | "tiktok" | "line" | "website";
export type ShopSocialLink = { key: ShopSocialKey; href: string };

const HTTP_RE = /^https?:\/\//i;
const SCHEME_RE = /^[a-z][a-z0-9+.-]*:/i;
const DOMAINS: Record<"facebook" | "instagram" | "tiktok", RegExp> = {
  facebook: /^([\w-]+\.)*(facebook|fb)\.com$/i,
  instagram: /^([\w-]+\.)*instagram\.com$/i,
  tiktok: /^([\w-]+\.)*tiktok\.com$/i,
};
const BASES = {
  facebook: "https://www.facebook.com/",
  instagram: "https://www.instagram.com/",
  tiktok: "https://www.tiktok.com/@",
} as const;

/** Always https and (for the three networks) pinned to the network's own domain; anything else is dropped. */
function networkHref(key: keyof typeof DOMAINS, raw?: string | null): string | null {
  const value = raw?.trim();
  if (!value) return null;
  if (HTTP_RE.test(value) || /^([\w-]+\.)+(com)(\/|$)/i.test(value)) {
    try {
      const url = new URL(HTTP_RE.test(value) ? value : `https://${value}`);
      return DOMAINS[key].test(url.hostname) ? `https://${url.hostname}${url.pathname}${url.search}` : null;
    } catch {
      return null;
    }
  }
  const handle = value.replace(/^@/, "");
  if (!handle || SCHEME_RE.test(handle) || /[\s/\\]/.test(handle)) return null;
  return `${BASES[key]}${encodeURIComponent(handle)}`;
}

function lineHref(raw?: string | null): string | null {
  const value = raw?.trim().replace(/\s+/g, "");
  if (!value) return null;
  if (/^https:\/\/(line\.me|lin\.ee)\//i.test(value)) return value;
  if (SCHEME_RE.test(value) || value.includes("/")) return null;
  return value.startsWith("@")
    ? `https://line.me/R/ti/p/${encodeURIComponent(value)}`
    : `https://line.me/ti/p/~${encodeURIComponent(value)}`;
}

function websiteHref(raw?: string | null): string | null {
  const value = raw?.trim();
  if (!value) return null;
  if (HTTP_RE.test(value)) {
    try {
      const url = new URL(value);
      return url.protocol === "https:" || url.protocol === "http:" ? url.href : null;
    } catch {
      return null;
    }
  }
  if (SCHEME_RE.test(value) && !/^[\w-]+(\.[\w-]+)+(:\d+)?(\/|$)/.test(value)) return null;
  return /^[\w-]+(\.[\w-]+)+(:\d+)?(\/|$)/.test(value) ? `https://${value}` : null;
}

/** The shop's own public channels (PublicShop only) as safe outbound links. */
export function shopSocialLinks(shop: PublicShop): ShopSocialLink[] {
  const out: ShopSocialLink[] = [];
  const add = (key: ShopSocialKey, href: string | null) => {
    if (href) out.push({ key, href });
  };
  add("line", lineHref(shop.lineId));
  add("facebook", networkHref("facebook", shop.facebookUrl));
  add("instagram", networkHref("instagram", shop.instagramUrl));
  add("tiktok", networkHref("tiktok", shop.tiktokUrl));
  add("website", websiteHref(shop.website));
  return out;
}
