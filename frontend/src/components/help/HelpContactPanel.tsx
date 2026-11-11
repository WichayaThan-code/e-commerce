import { useTranslation } from "react-i18next";

const SUPPORT_EMAIL = "dev@klinksoft.co.th";

type Props = {
  variant?: "default" | "home";
};

/** Support-email contact block for the Help Center. */
export default function HelpContactPanel({ variant = "default" }: Props) {
  const { t } = useTranslation("public");

  return (
    <section
      className={`contact-panel${variant === "home" ? " contact-panel--home" : ""}`.trim()}
      id="help-contact"
      aria-labelledby="help-contact-title"
    >
      <h2 id="help-contact-title">{t("helpPage.contact.title")}</h2>
      <div className="help-contact-list">
        <div className="help-contact-row">
          <div>
            <span>{t("helpPage.contact.supportLabel") + " " + SUPPORT_EMAIL}</span>
          </div>
        </div>
      </div>
    </section>
  );
}
