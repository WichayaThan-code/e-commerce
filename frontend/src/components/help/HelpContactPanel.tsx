import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ChevronRight, Mail, MessageCircle, Phone } from "lucide-react";
import { useAuth } from "../../contexts/AuthContext";

/** Contact box + quick links. Links point only at routes that exist in the app. */
export default function HelpContactPanel() {
  const { t } = useTranslation("public");
  const { user } = useAuth();
  const [notice, setNotice] = useState("");

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(""), 2200);
    return () => window.clearTimeout(timer);
  }, [notice]);

  return (
    <section className="contact-panel" id="help-contact" aria-labelledby="help-contact-title">
      <div className="help-section-title">
        <div>
          <h2 id="help-contact-title">{t("helpPage.contact.title")}</h2>
          <p>{t("helpPage.contact.subtitle")}</p>
        </div>
      </div>
      <div className="contact-grid">
        <article>
          <MessageCircle />
          <div>
            <b>{t("helpPage.contact.chat.title")}</b>
            <small>{t("helpPage.contact.chat.hours")}</small>
            <button type="button" onClick={() => setNotice(t("helpPage.contact.chat.opening"))}>
              {t("helpPage.contact.chat.action")}
            </button>
          </div>
        </article>
        <article className="line">
          <b className="line-logo">LINE</b>
          <div>
            <b>{t("helpPage.contact.line.title")}</b>
            <small>@thaishopfun</small>
            <button type="button" onClick={() => setNotice(t("helpPage.contact.line.opening"))}>
              {t("helpPage.contact.line.action")}
            </button>
          </div>
        </article>
        <article>
          <Mail />
          <div>
            <b>{t("helpPage.contact.email.title")}</b>
            <small>support@thaishopfun.com</small>
            <a href="mailto:support@thaishopfun.com">{t("helpPage.contact.email.action")}</a>
          </div>
        </article>
        <article>
          <Phone />
          <div>
            <b>{t("helpPage.contact.phone.title")}</b>
            <small>
              02-123-4567
              <br />
              {t("helpPage.contact.phone.hours")}
            </small>
            <a href="tel:021234567">{t("helpPage.contact.phone.action")}</a>
          </div>
        </article>
      </div>
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
      {notice && (
        <div className="help-toast" role="status">
          {notice}
        </div>
      )}
    </section>
  );
}
