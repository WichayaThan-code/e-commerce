import { useState, type CSSProperties, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Facebook, Globe, Instagram, MessageCircle, Music2, Search, type LucideIcon } from "lucide-react";
import ShopLogo from "../ShopLogo";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import type { PublicShop } from "../../services/api";
import { shopPath, shopSearchPath } from "../../services/shopRoutes";
import { localizedText, safeBlockLink } from "../../theme/blockContent";
import type { ThemeHeader } from "../../theme/shopTheme";
import { chromeBackground, shopSocialLinks, type ShopSocialKey } from "./chromeShop";

export const SOCIAL_ICONS: Record<ShopSocialKey, LucideIcon> = {
  facebook: Facebook,
  instagram: Instagram,
  tiktok: Music2,
  line: MessageCircle,
  website: Globe,
};

/** Menu / footer / announcement link: internal path via the router, https in a new tab; anything else renders as plain text. */
export function ChromeLink({ link, className, children }: { link: string | undefined; className?: string; children: ReactNode }) {
  const target = safeBlockLink(link);
  if (!target) return <span className={className}>{children}</span>;
  if (target.external) {
    return <a className={className} href={target.href} target="_blank" rel="noopener noreferrer">{children}</a>;
  }
  return <Link className={className} to={target.href}>{children}</Link>;
}

type ShopCustomHeaderProps = {
  shop: PublicShop;
  header: ThemeHeader;
  /** Theme editor preview: no navigation. */
  preview?: boolean;
};

/**
 * AG5 CUSTOM header (rendered by ShopThemeShell only when the theme is CUSTOM and `config.header` exists).
 * The existing mall hero / sticky tabs below it are unchanged.
 */
export default function ShopCustomHeader({ shop, header, preview = false }: ShopCustomHeaderProps) {
  const { tp, localeTag } = usePublicTranslation();
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const background = chromeBackground(header);
  const social = header.showSocialIcons ? shopSocialLinks(shop) : [];
  const bar = header.announcementBar;
  const classes = [
    "shop-custom-header",
    `shop-chrome-layout-${header.layout === "LOGO_CENTER" ? "center" : "left"}`,
    background.className,
    header.sticky ? "is-sticky" : "",
  ].filter(Boolean).join(" ");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (preview || !query.trim()) return;
    navigate(shopSearchPath(shop.slug, query));
  };

  return (
    <div className={classes} style={background.style as CSSProperties} data-sticky={header.sticky ? "true" : "false"}>
      {bar ? (
        <div className="shop-chrome-announce" style={{ "--shop-chrome-announce-bg": bar.backgroundColor, "--shop-chrome-announce-fg": bar.textColor } as CSSProperties}>
          <ChromeLink link={bar.link} className="shop-chrome-announce-text">{localizedText(bar.text, localeTag)}</ChromeLink>
        </div>
      ) : null}
      <div className="shop-chrome-bar recommend-width">
        <Link className="shop-chrome-brand" to={shopPath(shop.slug)} aria-label={shop.name}>
          <span className="shop-chrome-logo"><ShopLogo src={shop.logoUrl} name={shop.name} /></span>
          <b>{shop.name}</b>
        </Link>
        {header.menu.length > 0 ? (
          <nav className="shop-chrome-menu" aria-label={tp("shop.chrome.menuAria")}>
            {header.menu.map((item, index) => (
              <ChromeLink key={`${index}-${item.link}`} link={item.link}>{localizedText(item.label, localeTag)}</ChromeLink>
            ))}
          </nav>
        ) : <span className="shop-chrome-spacer" />}
        {header.showSearch || social.length > 0 ? (
          <div className="shop-chrome-tools">
            {header.showSearch ? (
              <form className="shop-chrome-search" role="search" onSubmit={submit}>
                <input
                  type="search"
                  value={query}
                  maxLength={80}
                  placeholder={tp("shop.chrome.searchPlaceholder")}
                  aria-label={tp("shop.chrome.searchAria")}
                  onChange={(event) => setQuery(event.target.value)}
                />
                <button type="submit" aria-label={tp("shop.chrome.searchSubmit")}><Search /></button>
              </form>
            ) : null}
            {social.length > 0 ? (
              <ul className="shop-chrome-social" aria-label={tp("shop.chrome.socialAria")}>
                {social.map(({ key, href }) => {
                  const Icon = SOCIAL_ICONS[key];
                  return (
                    <li key={key}>
                      <a href={href} target="_blank" rel="noopener noreferrer" aria-label={tp(`shop.chrome.social.${key}`)}><Icon /></a>
                    </li>
                  );
                })}
              </ul>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  );
}
