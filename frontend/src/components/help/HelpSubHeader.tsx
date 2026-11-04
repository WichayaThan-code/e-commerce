import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ChevronRight, Search } from "lucide-react";

export type HelpCrumb = { label: string; to?: string };

/** Compact help hero (CSS atmosphere, no artwork) with a search box that hands off to /help?q= and a breadcrumb. */
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
    <section className="help-hero help-hero--compact">
      <div className="help-width help-subhero">
        <nav className="help-breadcrumb" aria-label={t("helpPage.breadcrumb")}>
          <ol>
            {crumbs.map((crumb, index) => (
              <li key={`${crumb.label}-${index}`}>
                {crumb.to ? <Link to={crumb.to}>{crumb.label}</Link> : <span aria-current="page">{crumb.label}</span>}
                {index < crumbs.length - 1 && <ChevronRight aria-hidden="true" />}
              </li>
            ))}
          </ol>
        </nav>
        <form className="help-subsearch" onSubmit={submit} role="search">
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
