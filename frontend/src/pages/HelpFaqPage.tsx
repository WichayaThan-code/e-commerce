import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { CalendarCheck, ChevronRight, CircleAlert, SearchX, ThumbsDown, ThumbsUp } from "lucide-react";
import EmptyState from "../components/feedback/EmptyState";
import { Skeleton } from "../components/feedback/Skeleton";
import HelpFeedbackReasonDialog, { type HelpNotHelpfulReason } from "../components/help/HelpFeedbackReasonDialog";
import HelpRichText from "../components/help/HelpRichText";
import HelpSubHeader from "../components/help/HelpSubHeader";
import { HelpError, helpSessionKey, splitAnswer, useHelpData } from "../components/help/helpShared";
import { useAuth } from "../contexts/AuthContext";
import { useLanguage } from "../contexts/LanguageContext";
import { useDocumentMeta } from "../hooks/useDocumentMeta";
import { helpApi, type HelpFaq, type HelpTopicDetail } from "../services/api";
import "../styles/help.css";

type Vote = "yes" | "no";
const voteKey = (id: string) => `tsf_help_vote_${id}`;
const reasonKey = (id: string) => `tsf_help_vote_reason_${id}`;

function readVote(id: string): Vote | null {
  try {
    const value = window.localStorage.getItem(voteKey(id));
    return value === "yes" || value === "no" ? value : null;
  } catch {
    return null;
  }
}

function readReason(id: string): HelpNotHelpfulReason | null {
  try {
    const value = window.localStorage.getItem(reasonKey(id));
    return value ? (value as HelpNotHelpfulReason) : null;
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
  const [reasonCode, setReasonCode] = useState<HelpNotHelpfulReason | null>(null);
  const [counts, setCounts] = useState<{ yes: number; no: number } | null>(null);
  const [sending, setSending] = useState(false);
  const [feedbackMessage, setFeedbackMessage] = useState("");
  const [feedbackFailed, setFeedbackFailed] = useState(false);
  const [reasonOpen, setReasonOpen] = useState(false);

  useEffect(() => {
    setVote(readVote(id));
    setReasonCode(readReason(id));
    setCounts(null);
    setFeedbackMessage("");
    setFeedbackFailed(false);
    setReasonOpen(false);
  }, [id]);

  const faqData = faq.data;
  useDocumentMeta({
    title: faqData ? `${faqData.question} · ${t("helpPage.hero.title")}` : t("helpPage.hero.title"),
    description: faqData ? splitAnswer(faqData.answer).lead : undefined,
  });

  const related = useMemo(() => (topic.data?.faqs ?? []).filter((item) => item.id !== id).slice(0, 5), [topic.data, id]);
  const reviewed = useMemo(() => {
    if (!faqData?.lastReviewedAt) return "";
    const date = new Date(faqData.lastReviewedAt);
    if (Number.isNaN(date.getTime())) return "";
    return new Intl.DateTimeFormat(localeTag, { dateStyle: "long" }).format(date);
  }, [faqData?.lastReviewedAt, localeTag]);

  const persistLocal = (next: Vote, reason: HelpNotHelpfulReason | null) => {
    try {
      window.localStorage.setItem(voteKey(id), next);
      if (next === "no" && reason) window.localStorage.setItem(reasonKey(id), reason);
      else window.localStorage.removeItem(reasonKey(id));
    } catch {
      /* storage unavailable */
    }
  };

  const send = async (helpful: boolean, reason?: HelpNotHelpfulReason) => {
    if (sending) return;
    if (!helpful && !reason) return;
    setSending(true);
    setFeedbackFailed(false);
    try {
      const result = await helpApi.submitFeedback(id, helpful, helpSessionKey(), Boolean(user), reason);
      const next: Vote = helpful ? "yes" : "no";
      setVote(next);
      setReasonCode(helpful ? null : reason ?? null);
      setCounts({ yes: result.helpfulYes, no: result.helpfulNo });
      setFeedbackMessage(t("helpPage.faqDetail.thanks"));
      persistLocal(next, helpful ? null : reason ?? null);
      setReasonOpen(false);
    } catch {
      setFeedbackFailed(true);
      setFeedbackMessage(t("helpPage.faqDetail.feedbackError"));
    } finally {
      setSending(false);
    }
  };

  const onHelpful = () => {
    if (sending) return;
    void send(true);
  };

  const onNotHelpful = () => {
    if (sending) return;
    setReasonOpen(true);
  };

  const loading = faq.loading && !faqData;
  const notFound = faq.error && faq.status === 404;
  const helpfulYes = counts?.yes ?? faqData?.helpfulYes ?? 0;

  return (
    <main className="help-page help-page--detail">
      <HelpSubHeader
        crumbs={[
          { label: t("helpPage.breadcrumbHome"), to: "/help" },
          ...(topic.data ? [{ label: topic.data.title, to: `/help/topics/${topic.data.slug}` }] : []),
          { label: t("helpPage.faqDetail.crumb") },
        ]}
      />
      <div className="help-detail-width help-detail">
        <article className="help-detail-main help-answer" aria-busy={loading}>
          {loading ? (
            <>
              <Skeleton className="help-skeleton" height={40} radius={8} />
              <Skeleton className="help-skeleton" height={120} radius={8} />
            </>
          ) : notFound ? (
            <EmptyState
              icon={SearchX}
              title={t("helpPage.faqDetail.notFound")}
              action={{ label: t("helpPage.backToHelp"), to: "/help" }}
            />
          ) : faq.error ? (
            <HelpError message={t("helpPage.faqDetail.error")} retryLabel={t("helpPage.retry")} onRetry={faq.retry} />
          ) : faqData ? (
            <>
              <h1>{faqData.question}</h1>
              {reviewed && (
                <p className="help-reviewed">
                  <CalendarCheck aria-hidden="true" />
                  {t("helpPage.faqDetail.lastReviewed", { date: reviewed })}
                </p>
              )}
              <HelpRichText html={faqData.answer} className="help-answer-body" />

              <section className="help-feedback" aria-labelledby="help-feedback-title">
                <h2 id="help-feedback-title">{t("helpPage.faqDetail.helpfulTitle")}</h2>
                <div className="help-feedback-actions">
                  <button
                    type="button"
                    aria-pressed={vote === "yes"}
                    disabled={sending}
                    onClick={onHelpful}
                  >
                    <ThumbsUp aria-hidden="true" />
                    {t("helpPage.faqDetail.yes")}
                  </button>
                  <button
                    type="button"
                    aria-pressed={vote === "no"}
                    disabled={sending}
                    onClick={onNotHelpful}
                  >
                    <ThumbsDown aria-hidden="true" />
                    {t("helpPage.faqDetail.no")}
                  </button>
                </div>
                {vote === "no" && reasonCode ? (
                  <p className="help-feedback-reason" role="status">
                    {t(`helpPage.faqDetail.reasons.${reasonCode}`)}
                  </p>
                ) : null}
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

      {reasonOpen ? (
        <HelpFeedbackReasonDialog
          busy={sending}
          onCancel={() => setReasonOpen(false)}
          onSubmit={(code) => void send(false, code)}
        />
      ) : null}
    </main>
  );
}
