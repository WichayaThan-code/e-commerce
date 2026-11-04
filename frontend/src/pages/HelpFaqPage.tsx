import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { CalendarCheck, ChevronRight, CircleAlert, SearchX, ThumbsDown, ThumbsUp } from "lucide-react";
import { Skeleton } from "../components/feedback/Skeleton";
import HelpSubHeader from "../components/help/HelpSubHeader";
import { HelpError, helpSessionKey, splitAnswer, useHelpData } from "../components/help/helpShared";
import { useAuth } from "../contexts/AuthContext";
import { useLanguage } from "../contexts/LanguageContext";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import { helpApi, type HelpFaq, type HelpTopicDetail } from "../services/api";
import "../styles/help.css";

type Vote = "yes" | "no";
const voteKey = (id: string) => `tsf_help_vote_${id}`;

function readVote(id: string): Vote | null {
  try {
    const value = window.localStorage.getItem(voteKey(id));
    return value === "yes" || value === "no" ? value : null;
  } catch {
    return null;
  }
}

export default function HelpFaqPage() {
  const { t } = useTranslation("public");
  const { locale, localeTag } = useLanguage();
  const { user } = useAuth();
  const { id = "" } = useParams();

  const faq = useHelpData<HelpFaq | null>(() => helpApi.faqById(id, locale), null, true, [id, locale]);
  const topicSlug = faq.data?.topicSlug ?? "";
  const topic = useHelpData<HelpTopicDetail | null>(
    () => helpApi.topicBySlug(topicSlug, locale),
    null,
    Boolean(topicSlug),
    [topicSlug, locale],
  );

  const [vote, setVote] = useState<Vote | null>(null);
  const [counts, setCounts] = useState<{ yes: number; no: number } | null>(null);
  const [sending, setSending] = useState(false);
  const [feedbackMessage, setFeedbackMessage] = useState("");
  const [feedbackFailed, setFeedbackFailed] = useState(false);

  useEffect(() => {
    setVote(readVote(id));
    setCounts(null);
    setFeedbackMessage("");
    setFeedbackFailed(false);
  }, [id]);

  const faqData = faq.data;
  useDocumentMeta({
    title: faqData ? `${faqData.question} · ${t("helpPage.hero.title")}` : t("helpPage.hero.title"),
    description: faqData ? splitAnswer(faqData.answer).lead : undefined,
  });

  const related = useMemo(() => (topic.data?.faqs ?? []).filter((item) => item.id !== id).slice(0, 5), [topic.data, id]);
  const answer = faqData ? splitAnswer(faqData.answer) : null;
  const reviewed = useMemo(() => {
    if (!faqData?.lastReviewedAt) return "";
    const date = new Date(faqData.lastReviewedAt);
    if (Number.isNaN(date.getTime())) return "";
    return new Intl.DateTimeFormat(localeTag, { dateStyle: "long" }).format(date);
  }, [faqData?.lastReviewedAt, localeTag]);

  const send = async (helpful: boolean) => {
    if (sending || vote) return;
    setSending(true);
    setFeedbackFailed(false);
    try {
      const result = await helpApi.submitFeedback(id, helpful, helpSessionKey(), Boolean(user));
      const next: Vote = helpful ? "yes" : "no";
      setVote(next);
      setCounts({ yes: result.helpfulYes, no: result.helpfulNo });
      setFeedbackMessage(result.counted ? t("helpPage.faqDetail.thanks") : t("helpPage.faqDetail.alreadyVoted"));
      try {
        window.localStorage.setItem(voteKey(id), next);
      } catch {
        /* storage unavailable: the server still de-duplicates */
      }
    } catch {
      setFeedbackFailed(true);
      setFeedbackMessage(t("helpPage.faqDetail.feedbackError"));
    } finally {
      setSending(false);
    }
  };

  const loading = faq.loading && !faqData;
  const notFound = faq.error && faq.status === 404;
  const helpfulYes = counts?.yes ?? faqData?.helpfulYes ?? 0;

  return (
    <main className="help-page">
      <HelpSubHeader
        crumbs={[
          { label: t("helpPage.breadcrumbHome"), to: "/help" },
          ...(topic.data ? [{ label: topic.data.title, to: `/help/topics/${topic.data.slug}` }] : []),
          { label: t("helpPage.faqDetail.crumb") },
        ]}
      />
      <div className="help-width help-detail">
        <article className="help-detail-main help-answer" aria-busy={loading}>
          {loading ? (
            <>
              <Skeleton className="help-skeleton" height={40} radius={8} />
              <Skeleton className="help-skeleton" height={120} radius={8} />
            </>
          ) : notFound ? (
            <div className="help-empty" role="status">
              <SearchX aria-hidden="true" />
              <p>{t("helpPage.faqDetail.notFound")}</p>
              <Link className="help-link-button" to="/help">
                {t("helpPage.backToHelp")}
              </Link>
            </div>
          ) : faq.error ? (
            <HelpError message={t("helpPage.faqDetail.error")} retryLabel={t("helpPage.retry")} onRetry={faq.retry} />
          ) : faqData && answer ? (
            <>
              <h1>{faqData.question}</h1>
              {reviewed && (
                <p className="help-reviewed">
                  <CalendarCheck aria-hidden="true" />
                  {t("helpPage.faqDetail.lastReviewed", { date: reviewed })}
                </p>
              )}
              <div className="help-answer-body">
                <p className="faq-lead">{answer.lead}</p>
                {answer.rest && <p>{answer.rest}</p>}
              </div>

              <section className="help-feedback" aria-labelledby="help-feedback-title">
                <h2 id="help-feedback-title">{t("helpPage.faqDetail.helpfulTitle")}</h2>
                <div className="help-feedback-actions">
                  <button
                    type="button"
                    aria-pressed={vote === "yes"}
                    disabled={sending || vote !== null}
                    onClick={() => void send(true)}
                  >
                    <ThumbsUp aria-hidden="true" />
                    {t("helpPage.faqDetail.yes")}
                  </button>
                  <button
                    type="button"
                    aria-pressed={vote === "no"}
                    disabled={sending || vote !== null}
                    onClick={() => void send(false)}
                  >
                    <ThumbsDown aria-hidden="true" />
                    {t("helpPage.faqDetail.no")}
                  </button>
                </div>
                <p className={`help-feedback-status ${feedbackFailed ? "error" : ""}`.trim()} role="status">
                  {feedbackFailed && <CircleAlert aria-hidden="true" />}
                  {feedbackMessage}
                </p>
                {helpfulYes > 0 && <small>{t("helpPage.faqDetail.helpfulCount", { count: helpfulYes })}</small>}
              </section>

              <section className="help-cta" aria-labelledby="help-cta-title">
                <div>
                  <h2 id="help-cta-title">{t("helpPage.faqDetail.contactTitle")}</h2>
                  <p>{t("helpPage.faqDetail.contactBody")}</p>
                </div>
                <Link className="help-link-button" to="/help#help-contact">
                  {t("helpPage.faqDetail.contactAction")}
                </Link>
              </section>
            </>
          ) : null}
        </article>

        <aside className="help-detail-side" aria-label={t("helpPage.faqDetail.related")}>
          <h2>{t("helpPage.faqDetail.related")}</h2>
          {topicSlug && topic.loading && !topic.data ? (
            <div aria-busy="true">
              {Array.from({ length: 4 }, (_, index) => (
                <Skeleton key={index} className="help-skeleton help-skeleton-side" radius={8} />
              ))}
            </div>
          ) : related.length === 0 ? (
            <p className="help-side-empty">{t("helpPage.faqDetail.relatedEmpty")}</p>
          ) : (
            <ul>
              {related.map((item) => (
                <li key={item.id}>
                  <Link to={`/help/faqs/${item.id}`}>
                    <span>{item.question}</span>
                    <ChevronRight aria-hidden="true" />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </aside>
      </div>
    </main>
  );
}
