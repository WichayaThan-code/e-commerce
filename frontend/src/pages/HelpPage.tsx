import { useEffect, useId, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ChevronDown, CircleHelp, Search, SearchX, Store, UserRound } from "lucide-react";
import EmptyState from "../components/feedback/EmptyState";
import { Skeleton } from "../components/feedback/Skeleton";
import FaqAnswerPreview from "../components/help/FaqAnswerPreview";
import HelpContactPanel from "../components/help/HelpContactPanel";
import { HelpError, useHelpData } from "../components/help/helpShared";
import { useLanguage } from "../contexts/LanguageContext";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { helpApi, type HelpFaq, type HelpTopic } from "../services/api";
import "../styles/help.css";
import "../styles/shop-page.css";

const SUGGEST_COUNT = 6;
const SUGGEST_DEBOUNCE_MS = 250;
const HOME_FAQ_LIMIT = 10;

export default function HelpPage() {
  const { t } = useTranslation("public");
  const { locale } = useLanguage();
  const [searchParams, setSearchParams] = useSearchParams();
  const listboxId = useId();

  const initialQuery = (searchParams.get("q") ?? "").trim();
  const [query, setQuery] = useState(initialQuery);
  const [submittedQuery, setSubmittedQuery] = useState(initialQuery);
  const [openFaq, setOpenFaq] = useState<string | null>(null);

  const [suggestOpen, setSuggestOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [suggestions, setSuggestions] = useState<HelpFaq[]>([]);
  const [suggestLoading, setSuggestLoading] = useState(false);
  const typed = query.trim();
  const debouncedTyped = useDebouncedValue(typed, SUGGEST_DEBOUNCE_MS);
  const faqListRef = useRef<HTMLDivElement>(null);

  const searching = submittedQuery.length > 0;

  const topics = useHelpData<HelpTopic[]>(() => helpApi.topics(locale), [], true, [locale]);
  const faqs = useHelpData<HelpFaq[]>(
    () => helpApi.faqs({ lang: locale, q: submittedQuery || undefined }),
    [],
    true,
    [locale, submittedQuery],
  );
  const visibleFaqs = useMemo(
    () => (searching ? faqs.data : faqs.data.slice(0, HOME_FAQ_LIMIT)),
    [faqs.data, searching],
  );

  useEffect(() => {
    if (debouncedTyped.length < 2) {
      setSuggestions([]);
      setSuggestLoading(false);
      return;
    }
    let cancelled = false;
    setSuggestLoading(true);
    helpApi
      .faqs({ lang: locale, q: debouncedTyped })
      .then((rows) => {
        if (!cancelled) setSuggestions(rows.slice(0, SUGGEST_COUNT));
      })
      .catch(() => {
        if (!cancelled) setSuggestions([]);
      })
      .finally(() => {
        if (!cancelled) setSuggestLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [debouncedTyped, locale]);

  useEffect(() => setActiveIndex(-1), [suggestions]);
  useEffect(() => setOpenFaq(null), [locale, submittedQuery]);

  const topicTitleBySlug = useMemo(() => new Map(topics.data.map((topic) => [topic.slug, topic.title])), [topics.data]);
  const suggestVisible = suggestOpen && typed.length >= 2;

  const applySubmitted = (value: string) => {
    setSubmittedQuery(value);
    const next = new URLSearchParams(searchParams);
    if (value) next.set("q", value);
    else next.delete("q");
    setSearchParams(next, { replace: true });
  };

  const scrollToFaqs = () => {
    window.requestAnimationFrame(() => faqListRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
  };

  const pickSuggestion = (faq: HelpFaq) => {
    setSuggestOpen(false);
    setQuery(faq.question);
    applySubmitted(faq.question);
    setOpenFaq(faq.id);
    scrollToFaqs();
  };

  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    if (suggestVisible && activeIndex >= 0 && suggestions[activeIndex]) {
      pickSuggestion(suggestions[activeIndex]);
      return;
    }
    setSuggestOpen(false);
    applySubmitted(typed);
    scrollToFaqs();
  };

  const clearSearch = () => {
    setQuery("");
    applySubmitted("");
  };

  const onSearchKey = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Escape") {
      setSuggestOpen(false);
      setActiveIndex(-1);
      return;
    }
    if (!suggestVisible || suggestions.length === 0) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex((index) => (index + 1) % suggestions.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex((index) => (index <= 0 ? suggestions.length - 1 : index - 1));
    }
  };

  const onAccordionKey = (event: KeyboardEvent<HTMLDivElement>) => {
    const keys = ["ArrowDown", "ArrowUp", "Home", "End"];
    if (!keys.includes(event.key)) return;
    const triggers = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>("[data-faq-trigger]"));
    const current = triggers.findIndex((node) => node === document.activeElement);
    if (current === -1) return;
    event.preventDefault();
    const next =
      event.key === "Home"
        ? 0
        : event.key === "End"
          ? triggers.length - 1
          : event.key === "ArrowDown"
            ? (current + 1) % triggers.length
            : (current - 1 + triggers.length) % triggers.length;
    triggers[next]?.focus();
  };

  const optionId = (index: number) => `${listboxId}-opt-${index}`;

  const renderFaqArticle = (faq: HelpFaq) => {
    const open = openFaq === faq.id;
    return (
      <article key={faq.id} className={open ? "is-open" : undefined}>
        <h3>
          <button
            type="button"
            id={`faq-trigger-${faq.id}`}
            data-faq-trigger
            aria-expanded={open}
            aria-controls={`faq-panel-${faq.id}`}
            onClick={() => setOpenFaq(open ? null : faq.id)}
          >
            <span>
              {faq.question}
              {searching && topicTitleBySlug.get(faq.topicSlug) ? (
                <small>{topicTitleBySlug.get(faq.topicSlug)}</small>
              ) : null}
            </span>
            <ChevronDown aria-hidden="true" />
          </button>
        </h3>
        <div
          className="faq-panel-body"
          id={`faq-panel-${faq.id}`}
          role="region"
          aria-labelledby={`faq-trigger-${faq.id}`}
          hidden={!open}
        >
          {open ? <FaqAnswerPreview faqId={faq.id} html={faq.answer} /> : null}
        </div>
      </article>
    );
  };

  return (
    <main className={`help-page help-page--home${openFaq ? " is-reading" : ""}`.trim()}>
      <section className="help-banner" aria-labelledby="help-home-title">
        <div className="help-banner-inner">
          <h1 id="help-home-title">{t("helpPage.hero.title")}</h1>
          <form className="help-banner-search" onSubmit={submitSearch} role="search">
            <Search aria-hidden="true" />
            <input
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                setSuggestOpen(true);
              }}
              onFocus={() => setSuggestOpen(true)}
              onBlur={() => setSuggestOpen(false)}
              onKeyDown={onSearchKey}
              placeholder={t("helpPage.hero.searchPlaceholder")}
              aria-label={t("helpPage.hero.searchLabel")}
              role="combobox"
              aria-expanded={suggestVisible}
              aria-controls={listboxId}
              aria-autocomplete="list"
              aria-activedescendant={suggestVisible && activeIndex >= 0 ? optionId(activeIndex) : undefined}
              autoComplete="off"
              enterKeyHint="search"
            />
            <button type="submit">{t("helpPage.hero.searchAction")}</button>
            {suggestVisible ? (
              <ul className="help-typeahead" id={listboxId} role="listbox" aria-label={t("helpPage.hero.suggestions")}>
                {suggestions.map((faq, index) => (
                  <li
                    key={faq.id}
                    id={optionId(index)}
                    role="option"
                    aria-selected={index === activeIndex}
                    className={index === activeIndex ? "active" : ""}
                    onMouseDown={(event) => {
                      event.preventDefault();
                      pickSuggestion(faq);
                    }}
                    onMouseEnter={() => setActiveIndex(index)}
                  >
                    <Search aria-hidden="true" />
                    <span>
                      {faq.question}
                      {topicTitleBySlug.get(faq.topicSlug) ? <small>{topicTitleBySlug.get(faq.topicSlug)}</small> : null}
                    </span>
                  </li>
                ))}
                {suggestions.length === 0 ? (
                  <li className="help-typeahead-status" role="presentation">
                    {suggestLoading || debouncedTyped !== typed
                      ? t("helpPage.hero.searching")
                      : t("helpPage.hero.noSuggestions")}
                  </li>
                ) : null}
              </ul>
            ) : null}
          </form>
        </div>
      </section>

      <section className="help-home-width help-audience-cards" aria-label={t("helpPage.audience.label")}>
        <div className="help-audience-grid">
          <Link to="/help/buyer" className="help-audience-card">
            <UserRound aria-hidden="true" />
            <span>{t("helpPage.audience.buyer")}</span>
          </Link>
          <Link to="/help/seller" className="help-audience-card">
            <Store aria-hidden="true" />
            <span>{t("helpPage.audience.seller")}</span>
          </Link>
        </div>
      </section>

      <section className="help-home-width help-home-body" ref={faqListRef}>
        <section className="faq-panel faq-panel--plain" aria-labelledby="help-faq-title">
          <div className="help-section-title help-section-title--home">
            <div>
              <h2 id="help-faq-title">{t("helpPage.faq.title")}</h2>
              {searching && !faqs.loading && !faqs.error ? (
                <p role="status">{t("helpPage.faq.resultCount", { count: faqs.data.length })}</p>
              ) : null}
            </div>
            {searching ? (
              <button type="button" onClick={clearSearch}>
                {t("helpPage.faq.clearFilters")}
              </button>
            ) : null}
          </div>

          {faqs.loading && faqs.data.length === 0 ? (
            <div className="faq-list faq-list--plain help-faq-skeleton" aria-busy="true">
              {Array.from({ length: 6 }, (_, index) => (
                <Skeleton key={index} className="help-skeleton help-skeleton-faq" radius={0} />
              ))}
            </div>
          ) : faqs.error ? (
            <HelpError message={t("helpPage.faq.error")} retryLabel={t("helpPage.retry")} onRetry={faqs.retry} />
          ) : visibleFaqs.length === 0 ? (
            searching ? (
              <EmptyState
                variant="filtered"
                icon={SearchX}
                title={t("helpPage.faq.emptySearch", { query: submittedQuery })}
                action={{ label: t("helpPage.faq.clearFilters"), onClick: clearSearch }}
              />
            ) : (
              <EmptyState icon={CircleHelp} title={t("helpPage.faq.empty")} />
            )
          ) : (
            <div
              className={`faq-list faq-list--plain ${faqs.loading ? "is-refetching" : ""}`.trim()}
              aria-busy={faqs.loading}
              onKeyDown={onAccordionKey}
            >
              {visibleFaqs.map((faq) => renderFaqArticle(faq))}
            </div>
          )}
        </section>

        <HelpContactPanel variant="home" />
      </section>
    </main>
  );
}
