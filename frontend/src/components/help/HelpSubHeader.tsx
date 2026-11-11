import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ChevronRight, Search } from "lucide-react";

export type HelpCrumb = { label: string; to?: string };

/** Full-bleed help banner aligned with the Help home look: centered title, search, breadcrumb. */
export default function HelpSubHeader({ crumbs }: { crumbs: HelpCrumb[] }) {
  const { t } = useTranslation("public");
  const navigate = useNavigate();
  const [query, setQuery] = useState("");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const value = query.trim();
    navigate(value ? `/help?q=${encodeURIComponent(value)}` : "/help");
  };

  return (
    <section className="help-banner help-banner--sub" aria-labelledby="help-sub-title">
      <div className="help-banner-inner">
        <nav className="help-breadcrumb help-breadcrumb--center" aria-label={t("helpPage.breadcrumb")}>
          <ol>
            {crumbs.map((crumb, index) => (
              <li key={`${crumb.label}-${index}`}>
                {crumb.to ? <Link to={crumb.to}>{crumb.label}</Link> : <span aria-current="page">{crumb.label}</span>}
                {index < crumbs.length - 1 ? <ChevronRight aria-hidden="true" /> : null}
              </li>
            ))}
          </ol>
        </nav>
        <h1 id="help-sub-title">{t("helpPage.hero.title")}</h1>
        <form className="help-banner-search" onSubmit={submit} role="search">
          <Search aria-hidden="true" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t("helpPage.hero.searchPlaceholder")}
            aria-label={t("helpPage.hero.searchLabel")}
            autoComplete="off"
            enterKeyHint="search"
          />
          <button type="submit">{t("helpPage.hero.searchAction")}</button>
        </form>
      </div>
    </section>
  );
}
