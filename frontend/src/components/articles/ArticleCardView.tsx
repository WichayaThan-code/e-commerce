import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { CalendarDays, Clock, Newspaper } from "lucide-react";
import { useLanguage } from "../../contexts/LanguageContext";
import { mediaUrl, type ArticleCard } from "../../services/api";
import { formatArticleDate } from "../../utils/articleFormat";

export default function ArticleCardView({ article }: { article: ArticleCard }) {
  const { t } = useTranslation("public");
  const { localeTag } = useLanguage();
  const date = formatArticleDate(article.publishedAt, localeTag);
  return (
    <article className="blog-card">
      <Link className="blog-card-link" to={`/articles/${article.slug}`}>
        <span className="blog-card-cover">
          {article.coverUrl ? (
            <img src={mediaUrl(article.coverUrl)} alt={article.title} loading="lazy" decoding="async" />
          ) : (
            <span className="blog-cover-fallback" aria-hidden="true">
              <Newspaper />
            </span>
          )}
          {article.category && <span className="blog-badge">{article.category.name}</span>}
        </span>
        <span className="blog-card-body">
          <h2>{article.title}</h2>
          {article.excerpt && <p>{article.excerpt}</p>}
          <span className="blog-card-meta">
            {date && (
              <span>
                <CalendarDays aria-hidden="true" />
                {date}
              </span>
            )}
            <span>
              <Clock aria-hidden="true" />
              {t("articles.readingMinutes", { count: article.readingMinutes })}
            </span>
          </span>
        </span>
      </Link>
    </article>
  );
}
