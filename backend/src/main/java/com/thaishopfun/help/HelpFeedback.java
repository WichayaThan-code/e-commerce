package com.thaishopfun.help;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One "was this helpful?" vote on a FAQ, mapped onto V51 {@code help_feedback} (one per user, or per anonymous session). */
@Entity
@Table(name = "help_feedback")
public class HelpFeedback {
    @Id private UUID id;
    @Column(name = "faq_id", nullable = false) private UUID faqId;
    @Column(nullable = false) private boolean helpful;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "session_key", length = 64) private String sessionKey;
    @Column(name = "reason_code", length = 64) private String reasonCode;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected HelpFeedback() {}

    public HelpFeedback(UUID faqId, boolean helpful, UUID userId, String sessionKey, String reasonCode) {
        this.id = UUID.randomUUID();
        this.faqId = faqId;
        this.helpful = helpful;
        this.userId = userId;
        this.sessionKey = userId == null ? sessionKey : null;
        this.reasonCode = reasonCode;
        this.createdAt = Instant.now();
    }

    public void updateVote(boolean helpful, String reasonCode) {
        this.helpful = helpful;
        this.reasonCode = reasonCode;
    }

    public UUID getId() { return id; }
    public UUID getFaqId() { return faqId; }
    public boolean isHelpful() { return helpful; }
    public UUID getUserId() { return userId; }
    public String getSessionKey() { return sessionKey; }
    public String getReasonCode() { return reasonCode; }
    public Instant getCreatedAt() { return createdAt; }
}
