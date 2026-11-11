package com.thaishopfun.help;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class HelpDtos {
    private HelpDtos() {}

    // ---- Public (already localized) ----
    public record TopicView(UUID id, String slug, String icon, String title, String description, long faqCount) {}
    public record FaqView(UUID id, String topicSlug, String audience, String question, String answer,
                          int helpfulYes, int helpfulNo, Instant lastReviewedAt) {}
    /** A topic together with its active FAQs (GET /api/help/topics/{slug}). */
    public record TopicDetailView(UUID id, String slug, String icon, String title, String description, List<FaqView> faqs) {}
    public record GuideView(UUID id, String slug, String title, String summary, String url) {}

    // ---- Admin (all languages, all statuses) ----
    public record AdminTopic(UUID id, String slug, String icon, int sortOrder, boolean active,
                             String titleTh, String titleEn, String titleZh,
                             String descriptionTh, String descriptionEn, String descriptionZh,
                             int faqCount, Instant createdAt, Instant updatedAt, UUID updatedBy) {}

    public record AdminFaq(UUID id, UUID topicId, String topicSlug, int sortOrder, boolean active, String audience,
                           String questionTh, String questionEn, String questionZh,
                           String answerTh, String answerEn, String answerZh,
                           int helpfulYes, int helpfulNo, Instant lastReviewedAt,
                           Instant createdAt, Instant updatedAt, UUID updatedBy) {}

    public record AdminGuide(UUID id, String slug, int sortOrder, boolean active,
                             String titleTh, String titleEn, String titleZh,
                             String summaryTh, String summaryEn, String summaryZh, String url,
                             Instant createdAt, Instant updatedAt, UUID updatedBy) {}

    // ---- Write requests (sortOrder / active optional: new rows are appended and active, updates keep the old value) ----
    public record TopicRequest(String slug, String icon, Integer sortOrder, Boolean active,
                               String titleTh, String titleEn, String titleZh,
                               String descriptionTh, String descriptionEn, String descriptionZh) {}

    public record FaqRequest(UUID topicId, Integer sortOrder, Boolean active, String audience,
                             String questionTh, String questionEn, String questionZh,
                             String answerTh, String answerEn, String answerZh) {}

    public record GuideRequest(String slug, Integer sortOrder, Boolean active,
                               String titleTh, String titleEn, String titleZh,
                               String summaryTh, String summaryEn, String summaryZh, String url) {}

    /**
     * helpful is required; sessionKey identifies an anonymous browser (ignored when signed in).
     * reasonCode is required when helpful=false (HARD_TO_UNDERSTAND|INCORRECT_OR_INCOMPLETE|DID_NOT_SOLVE|BAD_LINKS|POLICY_FRICTION).
     */
    public record FeedbackRequest(Boolean helpful, String sessionKey, String reasonCode) {}
    /** counted=true for a new vote or a changed vote; false when the same vote is submitted again. */
    public record FeedbackResult(UUID faqId, boolean counted, int helpfulYes, int helpfulNo) {}

    public record ActiveRequest(Boolean active) {}
    public record ReorderRequest(List<UUID> ids) {}

    /** Public URL for an uploaded help inline image. */
    public record HelpImageUpload(UUID id, String url) {}
}
