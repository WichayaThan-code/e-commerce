import { useMemo } from "react";
import { Link, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ChevronRight, CircleHelp, SearchX } from "lucide-react";
import EmptyState from "../components/feedback/EmptyState";
import { Skeleton } from "../components/feedback/Skeleton";
import HelpContactPanel from "../components/help/HelpContactPanel";
import HelpRichText from "../components/help/HelpRichText";
import HelpSubHeader from "../components/help/HelpSubHeader";
import { HelpError, Lines, TONES, iconFor, sortTopics, splitAnswer, useHelpData } from "../components/help/helpShared";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import { useLanguage } from "../contexts/LanguageContext";
import { helpApi, type HelpTopic, type HelpTopicDetail } from "../services/api";
import "../styles/help.css";

export default function HelpTopicPage() {
  const { t } = useTranslation("public");
  const { locale } = useLanguage();
  const { slug = "" } = useParams();

  const topic = useHelpData<HelpTopicDetail | null>(() => helpApi.topicBySlug(slug, locale), null, true, [slug, locale]);
  const topics = useHelpData<HelpTopic[]>(() => helpApi.topics(locale), [], true, [locale]);

  useDocumentMeta({
    title: topic.data ? `${topic.data.title} · ${t("helpPage.hero.title")}` : t("helpPage.hero.title"),
    description: topic.data?.description,
  });

  const others = useMemo(() => sortTopics(topics.data).filter((item) => item.slug !== slug), [topics.data, slug]);
  const loading = topic.loading && !topic.data;
  const notFound = topic.error && topic.status === 404;
  const Icon = topic.data ? iconFor(topic.data.icon, 0) : CircleHelp;

  return (
    <main className="help-page help-page--detail">
      <HelpSubHeader
        crumbs={[
          { label: t("helpPage.breadcrumbHome"), to: "/help" },
          { label: topic.data?.title ?? t("helpPage.topic.crumb") },
        ]}
      />
      <div className="help-detail-width help-detail">
        <section className="help-detail-main" aria-busy={loading}>
          {loading ? (
            <>
              <Skeleton className="help-skeleton" height={48} radius={10} />
              <div className="help-faq-skeleton faq-list">
                {Array.from({ length: 5 }, (_, index) => (
                  <Skeleton key={index} className="help-skeleton help-skeleton-faq" radius={0} />
                ))}
              </div>
            </>
          ) : notFound ? (
            <EmptyState
              icon={SearchX}
              title={t("helpPage.topic.notFound")}
              action={{ label: t("helpPage.backToHelp"), to: "/help" }}
            />
          ) : topic.error ? (
            <HelpError message={t("helpPage.topic.error")} retryLabel={t("helpPage.retry")} onRetry={topic.retry} />
          ) : topic.data ? (
            <>
              <header className="help-detail-head">
                <span className={`help-detail-icon ${TONES[0]}`}>
                  <Icon />
                </span>
                <div>
                  <h1>{topic.data.title}</h1>
                  {topic.data.description && <HelpRichText html={topic.data.description} className="help-topic-desc" />}
                  <small>{t("helpPage.topic.faqCount", { count: topic.data.faqs.length })}</small>
                </div>
              </header>
              {topic.data.faqs.length === 0 ? (
                <EmptyState
                  icon={CircleHelp}
                  title={t("helpPage.topic.empty")}
                  action={{ label: t("helpPage.backToHelp"), to: "/help" }}
                />
              ) : (
                <ul className="help-faq-links">
                  {topic.data.faqs.map((faq) => (
                    <li key={faq.id}>
                      <Link to={`/help/faqs/${faq.id}`}>
                        <span>
                          <b>{faq.question}</b>
                          <small>{splitAnswer(faq.answer).lead}</small>
                        </span>
                        <ChevronRight aria-hidden="true" />
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </>
          ) : null}
        </section>

        <aside className="help-detail-side" aria-label={t("helpPage.topic.otherTopics")}>
          <h2>{t("helpPage.topic.otherTopics")}</h2>
          {topics.loading && topics.data.length === 0 ? (
            <div aria-busy="true">
              {Array.from({ length: 5 }, (_, index) => (
                <Skeleton key={index} className="help-skeleton help-skeleton-side" radius={8} />
              ))}
            </div>
          ) : topics.error ? (
            <HelpError className="is-compact" message={t("helpPage.topics.error")} retryLabel={t("helpPage.retry")} onRetry={topics.retry} />
          ) : (
            <ul>
              {others.map((item, index) => {
                const OtherIcon = iconFor(item.icon, index);
                return (
                  <li key={item.id}>
                    <Link to={`/help/topics/${item.slug}`}>
                      <OtherIcon />
                      <span>
                        <Lines text={item.title} />
                      </span>
                      <ChevronRight aria-hidden="true" />
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
        </aside>
      </div>
      <div className="help-detail-width help-detail-contact">
        <HelpContactPanel variant="home" />
      </div>
    </main>
  );
}
