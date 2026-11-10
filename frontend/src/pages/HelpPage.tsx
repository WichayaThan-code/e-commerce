import { useEffect, useId, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { BookOpen, ChevronDown, ChevronRight, CircleHelp, Search, SearchX, Store, UserRound } from "lucide-react";
import EmptyState from "../components/feedback/EmptyState";
import { Skeleton } from "../components/feedback/Skeleton";
import HelpContactPanel from "../components/help/HelpContactPanel";
import {
  HelpError,
  Lines,
  TONES,
  iconFor,
  sortTopics,
  splitAnswer,
  useHelpData,
} from "../components/help/helpShared";
import { useAuth } from "../contexts/AuthContext";
import { useLanguage } from "../contexts/LanguageContext";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { helpApi, type HelpFaq, type HelpGuide, type HelpTopic } from "../services/api";
import { accountKind } from "../services/permissions";
import helpReference from "../assets/images/references/help/help.png";
import "../styles/help.css";
import "../styles/shop-page.css";

type Audience = "CUSTOMER" | "MERCHANT";

const GUIDE_CROPS = ["guide-one", "guide-two", "guide-three"] as const;
const GUIDE_PREVIEW = 3;
const POPULAR_COUNT = 8;
const SUGGEST_COUNT = 6;
const SUGGEST_DEBOUNCE_MS = 250;

export default function HelpPage() {
  const { t } = useTranslation("public");
  const { locale } = useLanguage();
  const { user, loading: authLoading } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const listboxId = useId();

  const initialQuery = (searchParams.get("q") ?? "").trim();
  const [query, setQuery] = useState(initialQuery);
  const [submittedQuery, setSubmittedQuery] = useState(initialQuery);
  const [audience, setAudience] = useState<Audience>("CUSTOMER");
  const audienceTouched = useRef(false);
  const [openFaq, setOpenFaq] = useState<string | null>(null);
  const [showAllGuides, setShowAllGuides] = useState(false);
  const [showAllFaqs, setShowAllFaqs] = useState(false);

  // typeahead
  const [suggestOpen, setSuggestOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [suggestions, setSuggestions] = useState<HelpFaq[]>([]);
  const [suggestLoading, setSuggestLoading] = useState(false);
  const typed = query.trim();
  const debouncedTyped = useDebouncedValue(typed, SUGGEST_DEBOUNCE_MS);
  const faqListRef = useRef<HTMLDivElement>(null);

  // default audience follows the signed-in account until the visitor picks one
  useEffect(() => {
    if (authLoading || audienceTouched.current) return;
    setAudience(user && accountKind(user) === "MERCHANT" ? "MERCHANT" : "CUSTOMER");
  }, [authLoading, user]);

  const topics = useHelpData<HelpTopic[]>(() => helpApi.topics(locale), [], true, [locale]);
  const guides = useHelpData<HelpGuide[]>(() => helpApi.guides(locale), [], true, [locale]);
  const faqs = useHelpData<HelpFaq[]>(
    () => helpApi.faqs({ lang: locale, q: submittedQuery || undefined, audience }),
    [],
    !authLoading,
    [locale, submittedQuery, audience],
  );

  useEffect(() => {
    if (debouncedTyped.length < 2 || authLoading) {
      setSuggestions([]);
      setSuggestLoading(false);
      return;
    }
    let cancelled = false;
    setSuggestLoading(true);
    helpApi
      .faqs({ lang: locale, q: debouncedTyped, audience })
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
  }, [debouncedTyped, locale, audience, authLoading]);

  useEffect(() => setActiveIndex(-1), [suggestions]);
  useEffect(() => setOpenFaq(null), [locale, submittedQuery, audience]);

  const sortedTopics = useMemo(() => sortTopics(topics.data), [topics.data]);
  const topicTitleBySlug = useMemo(() => new Map(topics.data.map((topic) => [topic.slug, topic.title])), [topics.data]);
  const searching = submittedQuery.length > 0;
  const visibleFaqs = useMemo(() => {
    if (searching) return faqs.data;
    // no popularity signal other than real feedback: most helpful first, server order for ties
    const ranked = faqs.data
      .map((faq, index) => ({ faq, index }))
      .sort((a, b) => b.faq.helpfulYes - a.faq.helpfulYes || a.index - b.index)
      .map((entry) => entry.faq);
    return showAllFaqs ? ranked : ranked.slice(0, POPULAR_COUNT);
  }, [faqs.data, searching, showAllFaqs]);
  const visibleGuides = showAllGuides ? guides.data : guides.data.slice(0, GUIDE_PREVIEW);
  const suggestVisible = suggestOpen && typed.length >= 2;

  const applySubmitted = (value: string) => {
    setSubmittedQuery(value);
    setShowAllFaqs(false);
    const next = new URLSearchParams(searchParams);
    if (value) next.set("q", value);
    else next.delete("q");
    setSearchParams(next, { replace: true });
  };

  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    if (suggestVisible && activeIndex >= 0 && suggestions[activeIndex]) {
      navigate(`/help/faqs/${suggestions[activeIndex].id}`);
      return;
    }
    setSuggestOpen(false);
    applySubmitted(typed);
    window.requestAnimationFrame(() => faqListRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
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

  const chooseAudience = (next: Audience) => {
    audienceTouched.current = true;
    setAudience(next);
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

  return (
    <main className="help-page">
      <section className="help-hero" data-title={t("helpPage.hero.title")}>
        <div className="help-hero-plane">
          <img src={helpReference} alt={t("helpPage.hero.alt")} loading="eager" />
        </div>
        <form onSubmit={submitSearch} role="search">
          <input
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setSuggestOpen(true);
            }}
            onFocus={() => setSuggestOpen(true)}
            onBlur={() => setSuggestOpen(false)}
            onKeyDown={onSearchKey}
            aria-label={t("helpPage.hero.searchLabel")}
            role="combobox"
            aria-expanded={suggestVisible}
            aria-controls={listboxId}
            aria-autocomplete="list"
            aria-activedescendant={suggestVisible && activeIndex >= 0 ? optionId(activeIndex) : undefined}
            autoComplete="off"
            enterKeyHint="search"
          />
          <button type="submit" aria-label={t("helpPage.hero.searchLabel")} />
          {suggestVisible && (
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
                    navigate(`/help/faqs/${faq.id}`);
                  }}
                  onMouseEnter={() => setActiveIndex(index)}
                >
                  <Search aria-hidden="true" />
                  <span>
                    {faq.question}
                    {topicTitleBySlug.get(faq.topicSlug) && <small>{topicTitleBySlug.get(faq.topicSlug)}</small>}
                  </span>
                </li>
              ))}
              {suggestions.length === 0 && (
                <li className="help-typeahead-status" role="presentation">
                  {suggestLoading || debouncedTyped !== typed ? t("helpPage.hero.searching") : t("helpPage.hero.noSuggestions")}
                </li>
              )}
            </ul>
          )}
        </form>
      </section>

      <section className="help-width help-topics">
        <div className="help-topics-head">
          <h1>{t("helpPage.topics.title")}</h1>
          <div className="help-audience" role="group" aria-label={t("helpPage.audience.label")}>
            <button type="button" aria-pressed={audience === "CUSTOMER"} onClick={() => chooseAudience("CUSTOMER")}>
              <UserRound aria-hidden="true" />
              {t("helpPage.audience.buyer")}
            </button>
            <button type="button" aria-pressed={audience === "MERCHANT"} onClick={() => chooseAudience("MERCHANT")}>
              <Store aria-hidden="true" />
              {t("helpPage.audience.seller")}
            </button>
          </div>
        </div>
        {topics.loading && topics.data.length === 0 ? (
          <div className="help-topics-grid" aria-busy="true">
            {Array.from({ length: 8 }, (_, index) => (
              <Skeleton key={index} className="help-skeleton help-skeleton-topic" radius={12} />
            ))}
          </div>
        ) : topics.error ? (
          <HelpError message={t("helpPage.topics.error")} retryLabel={t("helpPage.retry")} onRetry={topics.retry} />
        ) : topics.data.length === 0 ? (
          <EmptyState icon={CircleHelp} title={t("helpPage.topics.empty")} />
        ) : (
          <div className="help-topics-grid">
            {sortedTopics.map((topic, index) => {
              const Icon = iconFor(topic.icon, index);
              return (
                <Link className={TONES[index % TONES.length]} key={topic.id} to={`/help/topics/${topic.slug}`}>
                  <Icon />
                  <b>
                    <Lines text={topic.title} />
                  </b>
                  {topic.description && (
                    <small>
                      <Lines text={topic.description} />
                    </small>
                  )}
                  <em>{t("helpPage.topics.faqCount", { count: topic.faqCount ?? 0 })}</em>
                </Link>
              );
            })}
          </div>
        )}
      </section>

      <section className="help-width help-columns">
        <section className="faq-panel" ref={faqListRef} aria-labelledby="help-faq-title">
          <div className="help-section-title">
            <div>
              <h2 id="help-faq-title">{t("helpPage.faq.title")}</h2>
              {searching && !faqs.loading && !faqs.error && (
                <p role="status">{t("helpPage.faq.resultCount", { count: faqs.data.length })}</p>
              )}
            </div>
            {searching ? (
              <button type="button" onClick={clearSearch}>
                {t("helpPage.faq.viewAll")}
              </button>
            ) : (
              faqs.data.length > POPULAR_COUNT && (
                <button type="button" onClick={() => setShowAllFaqs((value) => !value)}>
                  {showAllFaqs ? t("helpPage.guides.showLess") : t("helpPage.faq.viewAll")}
                </button>
              )
            )}
          </div>
          {faqs.loading && faqs.data.length === 0 ? (
            <div className="faq-list help-faq-skeleton" aria-busy="true">
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
              className={`faq-list ${faqs.loading ? "is-refetching" : ""}`.trim()}
              aria-busy={faqs.loading}
              onKeyDown={onAccordionKey}
            >
              {visibleFaqs.map((faq, index) => {
                const open = openFaq === faq.id;
                const { lead, rest } = splitAnswer(faq.answer);
                return (
                  <article key={faq.id}>
                    <h3>
                      <button
                        type="button"
                        id={`faq-trigger-${faq.id}`}
                        data-faq-trigger
                        aria-expanded={open}
                        aria-controls={`faq-panel-${faq.id}`}
                        onClick={() => setOpenFaq(open ? null : faq.id)}
                      >
                        <i>{index + 1}</i>
                        <span>
                          {faq.question}
                          {topicTitleBySlug.get(faq.topicSlug) && <small>{topicTitleBySlug.get(faq.topicSlug)}</small>}
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
                      {open && (
                        <>
                          <p className="faq-lead">{lead}</p>
                          {rest && <p>{rest}</p>}
                          <Link className="faq-more" to={`/help/faqs/${faq.id}`}>
                            {t("helpPage.faq.viewFull")}
                            <ChevronRight aria-hidden="true" />
                          </Link>
                        </>
                      )}
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>

        <section className="guide-panel">
          <div className="help-section-title">
            <h2>{t("helpPage.guides.title")}</h2>
            {guides.data.length > GUIDE_PREVIEW && (
              <button type="button" onClick={() => setShowAllGuides((value) => !value)}>
                {showAllGuides ? t("helpPage.guides.showLess") : t("helpPage.guides.viewAll")}
              </button>
            )}
          </div>
          {guides.loading && guides.data.length === 0 ? (
            <div aria-busy="true">
              {Array.from({ length: GUIDE_PREVIEW }, (_, index) => (
                <Skeleton key={index} className="help-skeleton help-skeleton-guide" radius={8} />
              ))}
            </div>
          ) : guides.error ? (
            <HelpError message={t("helpPage.guides.error")} retryLabel={t("helpPage.retry")} onRetry={guides.retry} />
          ) : guides.data.length === 0 ? (
            <EmptyState className="is-compact" icon={BookOpen} title={t("helpPage.guides.empty")} />
          ) : (
            visibleGuides.map((guide, index) => {
              const external = /^https?:\/\//i.test(guide.url);
              return (
                <a
                  className="guide-item"
                  key={guide.id}
                  href={guide.url}
                  {...(external ? { target: "_blank", rel: "noopener noreferrer" } : {})}
                >
                  <span className={`guide-photo ${GUIDE_CROPS[index % GUIDE_CROPS.length]}`}>
                    <img src={helpReference} alt="" loading="lazy" />
                  </span>
                  <span>
                    <b>{guide.title}</b>
                    {guide.summary && (
                      <small>
                        <Lines text={guide.summary} />
                      </small>
                    )}
                  </span>
                  <ChevronRight aria-hidden="true" />
                </a>
              );
            })
          )}
        </section>

        <HelpContactPanel />
      </section>
    </main>
  );
}
