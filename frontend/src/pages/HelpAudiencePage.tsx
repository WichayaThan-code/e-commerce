import { useMemo } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ChevronRight, CircleHelp } from "lucide-react";
import EmptyState from "../components/feedback/EmptyState";
import { Skeleton } from "../components/feedback/Skeleton";
import HelpContactPanel from "../components/help/HelpContactPanel";
import HelpRichText from "../components/help/HelpRichText";
import HelpSubHeader from "../components/help/HelpSubHeader";
import { HelpError, Lines, TONES, iconFor, sortTopics, splitAnswer, useHelpData } from "../components/help/helpShared";
import { useLanguage } from "../contexts/LanguageContext";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import { helpApi, type HelpFaq, type HelpTopic } from "../services/api";
import "../styles/help.css";

type Audience = "CUSTOMER" | "MERCHANT";

type TopicGroup = { topic: HelpTopic; faqs: HelpFaq[] };

function audienceFromPath(pathname: string): Audience {
  return pathname.endsWith("/seller") ? "MERCHANT" : "CUSTOMER";
}

/** Audience help hub: topic detail layout filtered to buyer or seller FAQs. */
export default function HelpAudiencePage() {
  const { t } = useTranslation("public");
  const { locale } = useLanguage();
  const { pathname } = useLocation();
  const [searchParams] = useSearchParams();
  const audience = audienceFromPath(pathname);
  const basePath = audience === "MERCHANT" ? "/help/seller" : "/help/buyer";

  const topics = useHelpData<HelpTopic[]>(() => helpApi.topics(locale), [], true, [locale]);
  const faqs = useHelpData<HelpFaq[]>(
    () => helpApi.faqs({ lang: locale, audience }),
    [],
    true,
    [locale, audience],
  );

  const pageTitle =
    audience === "MERCHANT" ? t("helpPage.audience.sellerPageTitle") : t("helpPage.audience.buyerPageTitle");
  const crumbLabel = audience === "MERCHANT" ? t("helpPage.audience.seller") : t("helpPage.audience.buyer");

  const groups = useMemo<TopicGroup[]>(() => {
    if (faqs.data.length === 0 || topics.data.length === 0) return [];
    const bySlug = new Map<string, HelpFaq[]>();
    for (const faq of faqs.data) {
      const slug = faq.topicSlug || "";
      const rows = bySlug.get(slug);
      if (rows) rows.push(faq);
      else bySlug.set(slug, [faq]);
    }
    const ordered: TopicGroup[] = [];
    for (const topic of sortTopics(topics.data)) {
      const rows = bySlug.get(topic.slug);
      if (!rows?.length) continue;
      ordered.push({ topic, faqs: rows });
      bySlug.delete(topic.slug);
    }
    return ordered;
  }, [faqs.data, topics.data]);

  const requestedSlug = (searchParams.get("topic") ?? "").trim();
  const activeGroup =
    groups.find((group) => group.topic.slug === requestedSlug) ?? groups[0] ?? null;

  useDocumentMeta({
    title: activeGroup
      ? `${activeGroup.topic.title} · ${pageTitle} · ${t("helpPage.hero.title")}`
      : `${pageTitle} · ${t("helpPage.hero.title")}`,
    description: activeGroup?.topic.description ?? pageTitle,
  });

  const loading =
    (faqs.loading && faqs.data.length === 0) || (topics.loading && topics.data.length === 0);
  const TopicIcon = activeGroup ? iconFor(activeGroup.topic.icon, 0) : CircleHelp;

  return (
    <main className="help-page help-page--detail">
      <HelpSubHeader
        crumbs={[
          { label: t("helpPage.breadcrumbHome"), to: "/help" },
          { label: crumbLabel, to: basePath },
          ...(activeGroup ? [{ label: activeGroup.topic.title }] : []),
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
          ) : faqs.error || topics.error ? (
            <HelpError
              message={t("helpPage.faq.error")}
              retryLabel={t("helpPage.retry")}
              onRetry={() => {
                faqs.retry();
                topics.retry();
              }}
            />
          ) : !activeGroup ? (
            <EmptyState
              icon={CircleHelp}
              title={t("helpPage.audience.emptyAudience")}
              action={{ label: t("helpPage.backToHelp"), to: "/help" }}
            />
          ) : (
            <>
              <header className="help-detail-head">
                <span className={`help-detail-icon ${TONES[0]}`}>
                  <TopicIcon />
                </span>
                <div>
                  <h1>{activeGroup.topic.title}</h1>
                  {activeGroup.topic.description ? (
                    <HelpRichText html={activeGroup.topic.description} className="help-topic-desc" />
                  ) : null}
                  <small>{t("helpPage.topic.faqCount", { count: activeGroup.faqs.length })}</small>
                </div>
              </header>
              <ul className="help-faq-links">
                {activeGroup.faqs.map((faq) => (
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
            </>
          )}
        </section>

        <aside className="help-detail-side" aria-label={t("helpPage.topic.otherTopics")}>
          <h2>{t("helpPage.topic.otherTopics")}</h2>
          {loading ? (
            <div aria-busy="true">
              {Array.from({ length: 5 }, (_, index) => (
                <Skeleton key={index} className="help-skeleton help-skeleton-side" radius={8} />
              ))}
            </div>
          ) : topics.error ? (
            <HelpError
              className="is-compact"
              message={t("helpPage.topics.error")}
              retryLabel={t("helpPage.retry")}
              onRetry={topics.retry}
            />
          ) : (
            <ul>
              {groups.map((group, index) => {
                const OtherIcon = iconFor(group.topic.icon, index);
                const active = group.topic.slug === activeGroup?.topic.slug;
                return (
                  <li key={group.topic.id}>
                    <Link
                      to={`${basePath}?topic=${encodeURIComponent(group.topic.slug)}`}
                      className={active ? "is-active" : undefined}
                      aria-current={active ? "page" : undefined}
                    >
                      <OtherIcon />
                      <span>
                        <Lines text={group.topic.title} />
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
