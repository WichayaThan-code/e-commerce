import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ChevronRight, Mail, MessageCircle, Phone } from "lucide-react";
import { useAuth } from "../../contexts/AuthContext";
import { platformContactApi, type SupportContactDayHours, type SupportContactPublic } from "../../services/api";
import { formatSupportHours } from "../../utils/supportHoursFormat";

const defaultChatHours = (): SupportContactDayHours[] =>
  Array.from({ length: 7 }, (_, day) => ({ day, open: true, start: "08:00", end: "22:00" }));
const defaultPhoneHours = (): SupportContactDayHours[] =>
  Array.from({ length: 7 }, (_, day) => ({ day, open: day < 5, start: day < 5 ? "08:00" : "09:00", end: "18:00" }));

const FALLBACK: SupportContactPublic = {
  chat: { url: null, hours: defaultChatHours() },
  line: { id: "@thaishopfun", url: null },
  email: { address: "support@thaishopfun.com" },
  phone: { number: "02-123-4567", hours: defaultPhoneHours() },
};

/** LINE OA "@id" → line.me/R/ti/p/%40id ; personal id → line.me/ti/p/~id ; URLs as-is. */
function lineHref(raw: string): string | undefined {
  const v = raw.trim().replace(/\s+/g, "");
  if (!v) return undefined;
  if (/^https?:\/\//i.test(v)) return v;
  if (/^(line\.me|lin\.ee)\//i.test(v)) return `https://${v}`;
  if (/^[a-z][a-z0-9+.-]*:/i.test(v)) return undefined;
  return v.startsWith("@")
    ? `https://line.me/R/ti/p/${encodeURIComponent(v)}`
    : `https://line.me/ti/p/~${encodeURIComponent(v)}`;
}

function telHref(raw: string) {
  const digits = raw.replace(/\D/g, "");
  return digits ? `tel:${digits}` : undefined;
}

/** Contact box + quick links. Links point only at routes/channels that exist. */
export default function HelpContactPanel() {
  const { t } = useTranslation("public");
  const { user } = useAuth();
  const [contact, setContact] = useState<SupportContactPublic>(FALLBACK);

  useEffect(() => {
    let alive = true;
    platformContactApi
      .get()
      .then((data) => {
        if (!alive) return;
        setContact({
          chat: data.chat ?? undefined,
          line: data.line ?? undefined,
          email: data.email ?? undefined,
          phone: data.phone ?? undefined,
        });
      })
      .catch(() => {
        if (alive) setContact(FALLBACK);
      });
    return () => {
      alive = false;
    };
  }, []);

  const dayLabels = useMemo(
    () => [0, 1, 2, 3, 4, 5, 6].map((day) => t(`helpPage.contact.dayShort.${day}`)),
    [t],
  );

  const cards = useMemo(() => {
    const items: Array<{
      key: "chat" | "line" | "email" | "phone";
      className?: string;
      icon: ReactNode;
      title: string;
      detail: ReactNode;
      action?: { href: string; label: string; external?: boolean };
    }> = [];

    if (contact.chat) {
      const chatUrl = contact.chat.url?.trim() || "";
      const hoursText = formatSupportHours(contact.chat.hours, dayLabels) || t("helpPage.contact.chat.hours");
      items.push({
        key: "chat",
        className: "chat",
        icon: <MessageCircle aria-hidden="true" />,
        title: t("helpPage.contact.chat.title"),
        detail: <small className="contact-hours">{hoursText}</small>,
        action: chatUrl
          ? { href: chatUrl, label: t("helpPage.contact.chat.action"), external: true }
          : undefined,
      });
    }

    if (contact.line) {
      const lineValue = (contact.line.url || contact.line.id || "").trim();
      const href = lineHref(lineValue);
      items.push({
        key: "line",
        className: "line",
        icon: <b className="line-logo" aria-hidden="true">LINE</b>,
        title: t("helpPage.contact.line.title"),
        detail: <small>{contact.line.id?.trim() || contact.line.url?.trim() || ""}</small>,
        action: href ? { href, label: t("helpPage.contact.line.action"), external: true } : undefined,
      });
    }

    if (contact.email?.address?.trim()) {
      const address = contact.email.address.trim();
      items.push({
        key: "email",
        className: "email",
        icon: <Mail aria-hidden="true" />,
        title: t("helpPage.contact.email.title"),
        detail: <small>{address}</small>,
        action: { href: `mailto:${address}`, label: t("helpPage.contact.email.action") },
      });
    }

    if (contact.phone?.number?.trim()) {
      const number = contact.phone.number.trim();
      const hoursText = formatSupportHours(contact.phone.hours, dayLabels) || t("helpPage.contact.phone.hours");
      const href = telHref(number);
      items.push({
        key: "phone",
        className: "phone",
        icon: <Phone aria-hidden="true" />,
        title: t("helpPage.contact.phone.title"),
        detail: (
          <small className="contact-hours">
            {number}
            <br />
            {hoursText}
          </small>
        ),
        action: href ? { href, label: t("helpPage.contact.phone.action") } : undefined,
      });
    }

    return items;
  }, [contact, dayLabels, t]);

  return (
    <section className="contact-panel" id="help-contact" aria-labelledby="help-contact-title">
      <div className="help-section-title">
        <div>
          <h2 id="help-contact-title">{t("helpPage.contact.title")}</h2>
          <p>{t("helpPage.contact.subtitle")}</p>
        </div>
      </div>
      {cards.length > 0 ? (
        <div className="contact-grid" data-count={cards.length}>
          {cards.map((card) => (
            <article key={card.key} className={card.className}>
              {card.icon}
              <div>
                <b>{card.title}</b>
                {card.detail}
                {card.action ? (
                  <a
                    href={card.action.href}
                    target={card.action.external ? "_blank" : undefined}
                    rel={card.action.external ? "noreferrer" : undefined}
                  >
                    {card.action.label}
                  </a>
                ) : null}
              </div>
            </article>
          ))}
        </div>
      ) : null}
      <nav className="help-quick-links" aria-label={t("helpPage.quickLinks.title")}>
        <h3>{t("helpPage.quickLinks.title")}</h3>
        <Link to="/track-order">
          {t("helpPage.quickLinks.track")}
          <ChevronRight aria-hidden="true" />
        </Link>
        {user && (
          <Link to="/account/orders">
            {t("helpPage.quickLinks.orders")}
            <ChevronRight aria-hidden="true" />
          </Link>
        )}
        <Link to="/shops">
          {t("helpPage.quickLinks.shops")}
          <ChevronRight aria-hidden="true" />
        </Link>
        <Link to="/articles">
          {t("helpPage.quickLinks.articles")}
          <ChevronRight aria-hidden="true" />
        </Link>
      </nav>
    </section>
  );
}
