package com.thaishopfun.help;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Help center FAQ entry, mapped onto V50 {@code help_faqs}. */
@Entity
@Table(name = "help_faqs")
public class HelpFaq {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private HelpTopic topic;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean active = true;
    /** One of {@link HelpAudience} names (CHECK constraint in V50). */
    @Column(nullable = false, length = 16) private String audience = HelpAudience.ALL.name();
    @Column(name = "question_th", nullable = false, length = 500) private String questionTh;
    @Column(name = "question_en", length = 500) private String questionEn;
    @Column(name = "question_zh", length = 500) private String questionZh;
    @Column(name = "answer_th", nullable = false, columnDefinition = "text") private String answerTh;
    @Column(name = "answer_en", columnDefinition = "text") private String answerEn;
    @Column(name = "answer_zh", columnDefinition = "text") private String answerZh;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;
    /** V51 helpful counters (maintained atomically by {@link HelpFaqRepository}, never edited through the admin form). */
    @Column(name = "helpful_yes", nullable = false, updatable = false) private int helpfulYes;
    @Column(name = "helpful_no", nullable = false, updatable = false) private int helpfulNo;
    @Column(name = "last_reviewed_at") private Instant lastReviewedAt;

    protected HelpFaq() {}

    public HelpFaq(UUID actorId) {
        var now = Instant.now();
        this.id = UUID.randomUUID();
        this.createdAt = now;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public HelpTopic getTopic() { return topic; }
    public void setTopic(HelpTopic topic) { this.topic = topic; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getAudience() { return audience; }
    public void setAudience(HelpAudience audience) { this.audience = audience.name(); }
    public String getQuestionTh() { return questionTh; }
    public void setQuestionTh(String questionTh) { this.questionTh = questionTh; }
    public String getQuestionEn() { return questionEn; }
    public void setQuestionEn(String questionEn) { this.questionEn = questionEn; }
    public String getQuestionZh() { return questionZh; }
    public void setQuestionZh(String questionZh) { this.questionZh = questionZh; }
    public String getAnswerTh() { return answerTh; }
    public void setAnswerTh(String answerTh) { this.answerTh = answerTh; }
    public String getAnswerEn() { return answerEn; }
    public void setAnswerEn(String answerEn) { this.answerEn = answerEn; }
    public String getAnswerZh() { return answerZh; }
    public void setAnswerZh(String answerZh) { this.answerZh = answerZh; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public int getHelpfulYes() { return helpfulYes; }
    public int getHelpfulNo() { return helpfulNo; }
    public Instant getLastReviewedAt() { return lastReviewedAt; }
    public void markReviewed(UUID actorId) {
        var now = Instant.now();
        this.lastReviewedAt = now;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void touch(UUID actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
