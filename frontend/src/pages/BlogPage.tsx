// src/pages/BlogPage.tsx
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ArrowRight, CircleAlert, Clock, Mail, Search, SearchX, Tag, X } from "lucide-react";
import { Skeleton } from "../components/feedback/Skeleton";
import ArticleCardView from "../components/articles/ArticleCardView";
import { useLanguage } from "../contexts/LanguageContext";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import {
  articleApi,
  mediaUrl,
  type ArticleCard,
  type ArticleCategoryView,
} from "../services/api";
import { formatArticleDate } from "../utils/articleFormat";
import "../styles/blog.css";

const PAGE_SIZE = 9;
const LATEST_SIZE = 5;
const SEARCH_DEBOUNCE_MS = 300;
const MAX_TAGS = 12;

export default function BlogPage() {
  const { t } = useTranslation("public");
  const { locale, localeTag } = useLanguage();

  const [searchParams] = useSearchParams();
  const [category, setCategory] = useState(() => searchParams.get("category") ?? "");
  const [search, setSearch] = useState(() => searchParams.get("q") ?? "");
  const [tag, setTag] = useState(() => searchParams.get("tag") ?? "");
  const debouncedSearch = useDebouncedValue(search.trim(), SEARCH_DEBOUNCE_MS);

  const [categories, setCategories] = useState<ArticleCategoryView[]>([]);
  const [featured, setFeatured] = useState<ArticleCard | null>(null);
  const [featuredLoading, setFeaturedLoading] = useState(true);
  const [latest, setLatest] = useState<ArticleCard[]>([]);
  const [latestLoading, setLatestLoading] = useState(true);

  const [items, setItems] = useState<ArticleCard[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(false);
  const [moreError, setMoreError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const requestId = useRef(0);

  const [email, setEmail] = useState("");
  const [notice, setNotice] = useState("");
  const noticeTimer = useRef<number | undefined>(undefined);

  useDocumentMeta({ title: t("articles.metaTitle"), description: t("articles.metaDescription") });

  const notify = useCallback((message: string) => {
    setNotice(message);
    window.clearTimeout(noticeTimer.current);
    noticeTimer.current = window.setTimeout(() => setNotice(""), 2400);
  }, []);
  useEffect(() => () => window.clearTimeout(noticeTimer.current), []);

  // Featured + categories are independent of the main list so a list 500 still leaves the hero/tabs usable.
  useEffect(() => {
    let cancelled = false;
    setFeaturedLoading(true);
    articleApi
      .categories(locale)
      .then((rows) => {
        if (!cancelled) setCategories(rows);
      })
      .catch(() => {
        if (!cancelled) setCategories([]);
      });
    articleApi
      .featured(locale)
      .then((rows) => {
        if (!cancelled) {
          setFeatured(rows[0] ?? null);
          // Sidebar "latest" prefers featured cards so it does not depend on /api/articles succeeding.
          setLatest(rows.slice(0, LATEST_SIZE));
          setLatestLoading(false);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setFeatured(null);
          setLatest([]);
          setLatestLoading(false);
        }
      })
      .finally(() => {
        if (!cancelled) setFeaturedLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [locale]);

  // a category that no longer exists (e.g. after a language reload) must not leave an empty filtered list
  useEffect(() => {
    if (category && categories.length > 0 && !categories.some((item) => item.slug === category)) setCategory("");
  }, [category, categories]);

  // first page for the current filters — `attempt` forces a real re-fetch on retry
  useEffect(() => {
    const current = ++requestId.current;
    setLoading(true);
    setError(false);
    setMoreError(false);
    articleApi
      .list({ lang: locale, category: category || undefined, q: debouncedSearch || undefined, tag: tag || undefined, page: 0, size: PAGE_SIZE })
      .then((result) => {
        if (current !== requestId.current) return;
        setItems(result.items);
        setPage(result.page);
        setTotalPages(result.totalPages);
        setTotal(result.totalElements);
        setError(false);
      })
      .catch(() => {
        if (current !== requestId.current) return;
        setItems([]);
        setPage(0);
        setTotalPages(0);
        setTotal(0);
        setError(true);
      })
      .finally(() => {
        if (current === requestId.current) setLoading(false);
      });
  }, [locale, category, debouncedSearch, tag, attempt]);

  const retryList = () => {
    setError(false);
    setLoading(true);
    setAttempt((value) => value + 1);
  };

  const loadMore = () => {
    if (loadingMore || page + 1 >= totalPages) return;
    const current = requestId.current;
    setLoadingMore(true);
    setMoreError(false);
    articleApi
      .list({ lang: locale, category: category || undefined, q: debouncedSearch || undefined, tag: tag || undefined, page: page + 1, size: PAGE_SIZE })
      .then((result) => {
        if (current !== requestId.current) return;
        setItems((existing) => [...existing, ...result.items.filter((row) => !existing.some((item) => item.id === row.id))]);
        setPage(result.page);
        setTotalPages(result.totalPages);
        setMoreError(false);
      })
      .catch(() => {
        if (current === requestId.current) setMoreError(true);
      })
      .finally(() => {
        if (current === requestId.current) setLoadingMore(false);
      });
  };

  const tags = useMemo(() => {
    const counts = new Map<string, number>();
    for (const row of [...items, ...latest]) for (const name of row.tags) counts.set(name, (counts.get(name) ?? 0) + 1);
    return [...counts.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).slice(0, MAX_TAGS).map(([name]) => name);
  }, [items, latest]);

  const filtering = Boolean(category || debouncedSearch || tag);
  const clearFilters = () => {
    setCategory("");
    setSearch("");
    setTag("");
  };

  const subscribe = (event: FormEvent) => {
    event.preventDefault();
    if (!/^\S+@\S+\.\S+$/.test(email)) {
      notify(t("articles.newsletter.invalid"));
      return;
    }
    notify(t("articles.newsletter.success"));
    setEmail("");
  };

  const featuredDate = featured ? formatArticleDate(featured.publishedAt, localeTag) : "";
  const hasMore = page + 1 < totalPages;

  return (
    <div className="blog-page">
      <section className="blog-hero page-width" aria-labelledby="blog-hero-title">
        {featuredLoading ? (
          <Skeleton className="blog-hero-skeleton" radius={12} />
        ) : featured ? (
          <Link className="blog-hero-featured" to={`/articles/${featured.slug}`}>
            {featured.coverUrl && <img src={mediaUrl(featured.coverUrl)} alt={featured.title} loading="eager" decoding="async" />}
            <span className="blog-hero-copy">
              <span className="blog-badge">{t("articles.featured")}</span>
              <h1 id="blog-hero-title">{featured.title}</h1>
              {featured.excerpt && <p>{featured.excerpt}</p>}
              <span className="blog-hero-meta">
                {featured.category && <b>{featured.category.name}</b>}
                {featuredDate && <span>{featuredDate}</span>}
                <span>
                  <Clock aria-hidden="true" />
                  {t("articles.readingMinutes", { count: featured.readingMinutes })}
                </span>
              </span>
              <span className="blog-hero-cta">
                {t("articles.readMore")}
                <ArrowRight aria-hidden="true" />
              </span>
            </span>
          </Link>
        ) : (
          <div className="blog-hero-plain">
            <h1 id="blog-hero-title">{t("articles.heroTitle")}</h1>
            <p>{t("articles.heroSubtitle")}</p>
          </div>
        )}
      </section>

      <nav className="blog-categories page-width" aria-label={t("articles.categoriesLabel")}>
        <button type="button" className={category === "" ? "active" : ""} aria-pressed={category === ""} onClick={() => setCategory("")}>
          {t("articles.all")}
        </button>
        {categories.map((item) => (
          <button
            type="button"
            key={item.id}
            className={category === item.slug ? "active" : ""}
            aria-pressed={category === item.slug}
            onClick={() => setCategory(item.slug)}
          >
            {item.name}
            <small>{item.articleCount}</small>
          </button>
        ))}
      </nav>

      <section className="blog-layout page-width">
        <div className="blog-main">
          <div className="blog-title">
            <div>
              <h2>{featured ? t("articles.latestTitle") : t("articles.listTitle")}</h2>
              {!loading && !error && <p role="status">{t("articles.resultCount", { count: total })}</p>}
            </div>
            <label className="blog-search">
              <Search aria-hidden="true" />
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder={t("articles.searchPlaceholder")}
                aria-label={t("articles.searchLabel")}
              />
            </label>
          </div>

          {tag && (
            <p className="blog-active-tag">
              <Tag aria-hidden="true" />
              {tag}
              <button type="button" onClick={() => setTag("")} aria-label={t("articles.clearTag")}>
                <X aria-hidden="true" />
              </button>
            </p>
          )}

          {loading ? (
            <div className="blog-grid" aria-busy="true">
              {Array.from({ length: 6 }, (_, index) => (
                <div className="blog-card blog-card-skeleton" key={index}>
                  <Skeleton className="blog-skeleton-cover" radius={0} />
                  <div className="blog-card-body">
                    <Skeleton height={18} radius={6} />
                    <Skeleton height={12} radius={6} />
                    <Skeleton width="55%" height={12} radius={6} />
                  </div>
                </div>
              ))}
            </div>
          ) : error ? (
            <div className="blog-state blog-state-error" role="alert">
              <CircleAlert aria-hidden="true" />
              <p>{t("articles.error")}</p>
              <button type="button" onClick={retryList}>
                {t("articles.retry")}
              </button>
            </div>
          ) : items.length === 0 ? (
            <div className="blog-state" role="status">
              <SearchX aria-hidden="true" />
              <p>{filtering ? t("articles.emptyFiltered") : t("articles.empty")}</p>
              {filtering && (
                <button type="button" onClick={clearFilters}>
                  {t("articles.clearFilters")}
                </button>
              )}
            </div>
          ) : (
            <>
              <div className="blog-grid">
                {items.map((article) => (
                  <ArticleCardView key={article.id} article={article} />
                ))}
              </div>
              {moreError && (
                <p className="blog-more-error" role="alert">
                  <CircleAlert aria-hidden="true" />
                  {t("articles.loadMoreError")}
                </p>
              )}
              {(hasMore || moreError) && (
                <div className="blog-more">
                  <button type="button" onClick={loadMore} disabled={loadingMore}>
                    {loadingMore ? t("articles.loading") : moreError ? t("articles.retry") : t("articles.loadMore")}
                  </button>
                </div>
              )}
            </>
          )}
        </div>

        <aside className="blog-sidebar" aria-label={t("articles.sidebarLabel")}>
          <section className="blog-side-card">
            <h2>{t("articles.sidebar.latest")}</h2>
            {latestLoading ? (
              <div aria-busy="true">
                {Array.from({ length: 4 }, (_, index) => (
                  <Skeleton key={index} className="blog-skeleton-latest" radius={8} />
                ))}
              </div>
            ) : latest.length === 0 ? (
              <p className="blog-side-empty">{t("articles.sidebar.latestEmpty")}</p>
            ) : (
              <ol className="blog-latest">
                {latest.map((article) => (
                  <li key={article.id}>
                    <Link to={`/articles/${article.slug}`}>
                      <span className="blog-latest-thumb">
                        {article.coverUrl && <img src={mediaUrl(article.coverUrl)} alt={article.title} loading="lazy" decoding="async" />}
                      </span>
                      <span>
                        <b>{article.title}</b>
                        <small>{formatArticleDate(article.publishedAt, localeTag)}</small>
                      </span>
                    </Link>
                  </li>
                ))}
              </ol>
            )}
          </section>

          {tags.length > 0 && (
            <section className="blog-side-card">
              <h2>{t("articles.sidebar.tags")}</h2>
              <div className="blog-tags">
                {tags.map((name) => (
                  <button type="button" key={name} className={tag === name ? "active" : ""} aria-pressed={tag === name} onClick={() => setTag(tag === name ? "" : name)}>
                    #{name}
                  </button>
                ))}
              </div>
            </section>
          )}

          <form className="blog-side-card blog-newsletter" onSubmit={subscribe} noValidate>
            <h2>
              <Mail aria-hidden="true" /> {t("articles.newsletter.title")}
            </h2>
            <p>{t("articles.newsletter.body")}</p>
            <input
              type="email"
              aria-label={t("articles.newsletter.label")}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder={t("articles.newsletter.placeholder")}
              autoComplete="email"
            />
            <button type="submit">{t("articles.newsletter.action")}</button>
          </form>
        </aside>
      </section>

      {notice && (
        <div className="blog-toast" role="status">
          {notice}
        </div>
      )}
    </div>
  );
}
