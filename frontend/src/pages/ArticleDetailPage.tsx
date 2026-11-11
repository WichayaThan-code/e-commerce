import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  ArrowLeft,
  CalendarDays,
  ChevronRight,
  Copy,
  Eye,
  ListTree,
  ShoppingBag,
  Store,
  UserRound,
} from "lucide-react";
import EmptyState from "../components/feedback/EmptyState";
import { Skeleton } from "../components/feedback/Skeleton";
import ArticleCardView from "../components/articles/ArticleCardView";
import ArticleMarkdown, { type TocItem } from "../components/articles/ArticleMarkdown";
import { useAuth } from "../contexts/AuthContext";
import { useLanguage } from "../contexts/LanguageContext";
import { useAsyncData } from "../hooks/useAsyncData";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import { usePermissions } from "../hooks/usePermissions";
import { useOgMeta } from "../hooks/useOgMeta";
import {
  articleAdminApi,
  articleApi,
  mediaUrl,
  type AdminArticle,
  type AdminArticleCategory,
  type ArticleDetail,
} from "../services/api";
import { formatArticleDate } from "../utils/articleFormat";
import NotFoundPage from "./NotFoundPage";
import "../styles/blog.css";
import "../styles/shop-page.css";

type Locale = "th" | "en" | "zh";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Admin article (all languages, any status) → the public detail shape for the selected language (TH fallback). */
function toDetail(article: AdminArticle, categories: AdminArticleCategory[], locale: Locale): ArticleDetail {
  const pick = (th: string | null, en: string | null, zh: string | null) => ((locale === "en" ? en : locale === "zh" ? zh : th) || th || "").trim();
  const category = categories.find((item) => item.id === article.categoryId);
  const categoryName = category ? pick(category.nameTh, category.nameEn, category.nameZh) : article.categorySlug;
  return {
    id: article.id,
    slug: article.slug,
    title: pick(article.titleTh, article.titleEn, article.titleZh),
    excerpt: pick(article.excerptTh, article.excerptEn, article.excerptZh) || null,
    bodyMd: pick(article.bodyMdTh, article.bodyMdEn, article.bodyMdZh),
    category: article.categoryId && article.categorySlug ? { id: article.categoryId, slug: article.categorySlug, name: categoryName ?? article.categorySlug } : null,
    coverUrl: article.coverUrl,
    readingMinutes: article.readingMinutes,
    viewCount: article.viewCount ?? 0,
    tags: article.tags ?? [],
    audience: article.audience,
    featured: article.featured,
    publishedAt: article.publishedAt,
    author: article.authorName,
    metaTitle: article.metaTitle,
    metaDescription: article.metaDescription,
    related: [],
    relatedShop: null,
    relatedProducts: [],
  };
}

type Loaded = { article: ArticleDetail; previewStatus: string | null };

export default function ArticleDetailPage() {
  const { t } = useTranslation("public");
  const { locale, localeTag } = useLanguage();
  const { loading: authLoading } = useAuth();
  const { can } = usePermissions();
  const { slug = "" } = useParams();
  const [searchParams] = useSearchParams();

  const previewId = searchParams.get("id") ?? "";
  const wantsPreview = searchParams.get("preview") === "1" && UUID_PATTERN.test(previewId);
  const canPreview = wantsPreview && can("PLATFORM_CONTENT_READ");

  const state = useAsyncData<Loaded | null>(
    async () => {
      if (canPreview) {
        const [article, categories] = await Promise.all([
          articleAdminApi.get(previewId),
          articleAdminApi.categories().catch(() => [] as AdminArticleCategory[]),
        ]);
        return { article: toDetail(article, categories, locale), previewStatus: article.status };
      }
      return { article: await articleApi.bySlug(slug, locale), previewStatus: null };
    },
    null,
    !authLoading,
    [slug, locale, canPreview, previewId],
  );

  const [toc, setToc] = useState<TocItem[]>([]);
  const [notice, setNotice] = useState("");
  const noticeTimer = useRef<number | undefined>(undefined);
  const article = state.data?.article ?? null;
  const previewStatus = state.data?.previewStatus ?? null;

  const notify = useCallback((message: string) => {
    setNotice(message);
    window.clearTimeout(noticeTimer.current);
    noticeTimer.current = window.setTimeout(() => setNotice(""), 2400);
  }, []);
  useEffect(() => () => window.clearTimeout(noticeTimer.current), []);
  useEffect(() => setToc([]), [article?.id, locale]);

  const shareUrl = typeof window === "undefined" || !article ? "" : `${window.location.origin}/articles/${article.slug}`;
  const description = article ? article.metaDescription || article.excerpt || "" : "";
  useDocumentMeta({ title: article ? article.metaTitle || article.title : t("articles.metaTitle"), description });
  useOgMeta({
    title: article ? article.metaTitle || article.title : null,
    description,
    image: article?.coverUrl ? mediaUrl(article.coverUrl) : null,
    url: shareUrl || null,
  });

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(shareUrl);
      notify(t("articles.detail.copied"));
    } catch {
      const field = document.createElement("textarea");
      field.value = shareUrl;
      field.setAttribute("readonly", "");
      field.style.position = "fixed";
      field.style.opacity = "0";
      document.body.appendChild(field);
      field.select();
      const ok = document.execCommand("copy");
      field.remove();
      notify(ok ? t("articles.detail.copied") : t("articles.detail.copyFailed"));
    }
  };

  const shareLinks = useMemo(
    () => [
      { key: "line", label: "LINE", href: `https://social-plugins.line.me/lineit/share?url=${encodeURIComponent(shareUrl)}` },
      { key: "facebook", label: "Facebook", href: `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(shareUrl)}` },
    ],
    [shareUrl],
  );

  if (authLoading || (state.loading && !article)) {
    return (
      <div className="blog-page article-page">
        <div className="article-shell page-width" aria-busy="true">
          <Skeleton className="blog-skeleton-line" width="40%" height={14} radius={6} />
          <Skeleton className="blog-skeleton-line" height={40} radius={8} />
          <Skeleton className="blog-skeleton-line" width="60%" height={14} radius={6} />
          <Skeleton className="article-cover-skeleton" radius={12} />
          {Array.from({ length: 6 }, (_, index) => (
            <Skeleton key={index} className="blog-skeleton-line" height={14} radius={6} />
          ))}
        </div>
      </div>
    );
  }

  if (state.error && state.status === 404) return <NotFoundPage kind="page" />;

  if (state.error || !article) {
    return (
      <div className="blog-page article-page">
        <div className="article-shell page-width">
          <EmptyState
            variant="error"
            title={t("articles.detail.error")}
            actions={[
              { label: t("articles.retry"), onClick: state.retry },
              { label: t("articles.detail.backToList"), to: "/articles" },
            ]}
          />
        </div>
      </div>
    );
  }

  const date = formatArticleDate(article.publishedAt, localeTag);
  const ctaHref = article.relatedShop ? `/shop/${article.relatedShop.slug}` : "/";
  const hasRelatedCommerce = Boolean(article.relatedShop) || article.relatedProducts.length > 0;

  return (
    <div className="blog-page article-page">
      {previewStatus && (
        <div className="article-preview-banner" role="status">
          <Eye aria-hidden="true" />
          {t("articles.detail.previewBanner", { status: t(`articles.status.${previewStatus}`, { defaultValue: previewStatus }) })}
        </div>
      )}
      <div className="article-shell page-width">
        <nav className="article-breadcrumb" aria-label={t("articles.detail.breadcrumb")}>
          <ol>
            <li>
              <Link to="/">{t("articles.detail.home")}</Link>
              <ChevronRight aria-hidden="true" />
            </li>
            <li>
              <Link to="/articles">{t("articles.breadcrumbRoot")}</Link>
              <ChevronRight aria-hidden="true" />
            </li>
            {article.category && (
              <li>
                <Link to={`/articles?category=${encodeURIComponent(article.category.slug)}`}>{article.category.name}</Link>
                <ChevronRight aria-hidden="true" />
              </li>
            )}
            <li>
              <span aria-current="page">{article.title}</span>
            </li>
          </ol>
        </nav>

        <div className="article-layout">
          <article className="article-main">
            <header className="article-header">
              {article.category && <span className="blog-badge">{article.category.name}</span>}
              <h1>{article.title}</h1>
              {article.excerpt && <p className="article-excerpt">{article.excerpt}</p>}
              <p className="article-meta">
                {article.author && (
                  <span>
                    <UserRound aria-hidden="true" />
                    {article.author}
                  </span>
                )}
                {date && (
                  <span>
                    <CalendarDays aria-hidden="true" />
                    <time dateTime={article.publishedAt ?? undefined}>{date}</time>
                  </span>
                )}
                <span>
                  <Eye aria-hidden="true" />
                  {t("articles.views", { count: article.viewCount ?? 0 })}
                </span>
              </p>
            </header>

            {article.coverUrl && (
              <figure className="article-cover">
                <img src={mediaUrl(article.coverUrl)} alt={article.title} loading="lazy" decoding="async" />
              </figure>
            )}

            {toc.length > 0 && (
              <details className="article-toc-mobile">
                <summary>
                  <ListTree aria-hidden="true" />
                  {t("articles.detail.toc")}
                </summary>
                <ol>
                  {toc.map((item) => (
                    <li key={item.id} className={item.level === 3 ? "sub" : ""}>
                      <a href={`#${item.id}`}>{item.text}</a>
                    </li>
                  ))}
                </ol>
              </details>
            )}

            {article.bodyMd.trim() ? (
              <ArticleMarkdown markdown={article.bodyMd} onToc={setToc} />
            ) : (
              <p className="blog-side-empty">{t("articles.detail.noContent")}</p>
            )}

            {article.tags.length > 0 && (
              <ul className="article-tags" aria-label={t("articles.sidebar.tags")}>
                {article.tags.map((name) => (
                  <li key={name}>
                    <Link to={`/articles?tag=${encodeURIComponent(name)}`}>#{name}</Link>
                  </li>
                ))}
              </ul>
            )}

            <section className="article-share" aria-label={t("articles.detail.share")}>
              <b>{t("articles.detail.share")}</b>
              <button type="button" onClick={() => void copyLink()}>
                <Copy aria-hidden="true" />
                {t("articles.detail.copyLink")}
              </button>
              {shareLinks.map((item) => (
                <a key={item.key} href={item.href} target="_blank" rel="noopener noreferrer">
                  {item.label}
                </a>
              ))}
            </section>

            {hasRelatedCommerce && (
              <section className="article-commerce" aria-labelledby="article-commerce-title">
                <h2 id="article-commerce-title">{t("articles.detail.relatedShop")}</h2>
                <div className="article-commerce-grid">
                  {article.relatedShop && (
                    <Link to={`/shop/${article.relatedShop.slug}`} className="article-commerce-card">
                      <Store aria-hidden="true" />
                      <span>
                        <b>{article.relatedShop.name}</b>
                        <small>{t("articles.detail.visitShop")}</small>
                      </span>
                      <ChevronRight aria-hidden="true" />
                    </Link>
                  )}
                  {article.relatedProducts.map((product) => (
                    <Link key={product.id} to={`/shop/${product.shopSlug}/product/${product.slug}`} className="article-commerce-card">
                      <ShoppingBag aria-hidden="true" />
                      <span>
                        <b>{product.name}</b>
                        <small>{t("articles.detail.viewProduct")}</small>
                      </span>
                      <ChevronRight aria-hidden="true" />
                    </Link>
                  ))}
                </div>
              </section>
            )}

            <section className="article-cta">
              <div>
                <h2>{t("articles.detail.ctaTitle")}</h2>
                <p>{t("articles.detail.ctaBody")}</p>
              </div>
              <Link className="blog-primary-link" to={ctaHref}>
                {t("articles.detail.ctaAction")}
              </Link>
            </section>
          </article>

          <aside className="article-aside" aria-label={t("articles.detail.toc")}>
            {toc.length > 0 && (
              <nav className="article-toc" aria-label={t("articles.detail.toc")}>
                <h2>
                  <ListTree aria-hidden="true" />
                  {t("articles.detail.toc")}
                </h2>
                <ol>
                  {toc.map((item) => (
                    <li key={item.id} className={item.level === 3 ? "sub" : ""}>
                      <a href={`#${item.id}`}>{item.text}</a>
                    </li>
                  ))}
                </ol>
              </nav>
            )}
          </aside>
        </div>

        {article.related.length > 0 && (
          <section className="article-related" aria-labelledby="article-related-title">
            <h2 id="article-related-title">{t("articles.detail.related")}</h2>
            <div className="blog-grid">
              {article.related.map((item) => (
                <ArticleCardView key={item.id} article={item} />
              ))}
            </div>
          </section>
        )}

        <p className="article-back">
          <Link to="/articles">
            <ArrowLeft aria-hidden="true" />
            {t("articles.detail.backToList")}
          </Link>
        </p>
      </div>

      {notice && (
        <div className="blog-toast" role="status">
          {notice}
        </div>
      )}
    </div>
  );
}
