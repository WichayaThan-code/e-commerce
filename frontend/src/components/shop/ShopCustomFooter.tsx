import type { CSSProperties } from "react";
import { Mail, MapPin, Phone } from "lucide-react";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import type { PublicShop } from "../../services/api";
import { localizedText } from "../../theme/blockContent";
import type { ThemeFooter } from "../../theme/shopTheme";
import { chromeBackground, shopContactLines, shopSocialLinks } from "./chromeShop";
import { ChromeLink, SOCIAL_ICONS } from "./ShopCustomHeader";

type ShopCustomFooterProps = {
  shop: PublicShop;
  footer: ThemeFooter;
};

/**
 * AG5 CUSTOM footer (rendered by ShopThemeShell after the sections). Contacts come from `shopContactLines` /
 * `shopSocialLinks`, which read PublicShop only (the shop's public phone / email / address and public channels).
 */
export default function ShopCustomFooter({ shop, footer }: ShopCustomFooterProps) {
  const { tp, localeTag } = usePublicTranslation();
  const background = chromeBackground(footer);
  const contacts = footer.showContacts ? shopContactLines(shop) : {};
  const hasContacts = Boolean(contacts.phone || contacts.email || contacts.address);
  const social = footer.showSocial ? shopSocialLinks(shop) : [];
  const about = footer.about ? localizedText(footer.about, localeTag) : "";
  const columns = footer.columns.filter((column) => column.links.length > 0 || column.title);
  const empty = !about && columns.length === 0 && !hasContacts && social.length === 0 && !footer.copyright;
  if (empty) return null;

  return (
    <div className={`shop-custom-footer ${background.className}`} style={background.style as CSSProperties}>
      <div className="shop-chrome-footer-inner recommend-width">
        {about || hasContacts || social.length > 0 ? (
          <section className="shop-chrome-footer-brand">
            <h2>{shop.name}</h2>
            {about ? <p className="shop-chrome-about">{about}</p> : null}
            {hasContacts ? (
              <ul className="shop-chrome-contacts" aria-label={tp("shop.chrome.contactsAria")}>
                {contacts.phone ? (
                  <li><Phone aria-hidden="true" /><a href={`tel:${contacts.phone.replace(/[^\d+]/g, "")}`}>{contacts.phone}</a></li>
                ) : null}
                {contacts.email ? (
                  <li><Mail aria-hidden="true" /><a href={`mailto:${contacts.email}`}>{contacts.email}</a></li>
                ) : null}
                {contacts.address ? <li><MapPin aria-hidden="true" /><span>{contacts.address}</span></li> : null}
              </ul>
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
          </section>
        ) : null}
        {columns.map((column, index) => (
          <nav key={index} className="shop-chrome-footer-col" aria-label={column.title ? localizedText(column.title, localeTag) : tp("shop.chrome.footerAria")}>
            {column.title ? <h3>{localizedText(column.title, localeTag)}</h3> : null}
            <ul>
              {column.links.map((item, linkIndex) => (
                <li key={`${linkIndex}-${item.link}`}>
                  <ChromeLink link={item.link}>{localizedText(item.label, localeTag)}</ChromeLink>
                </li>
              ))}
            </ul>
          </nav>
        ))}
      </div>
      {footer.copyright ? (
        <p className="shop-chrome-copyright recommend-width">{footer.copyright}</p>
      ) : null}
    </div>
  );
}
