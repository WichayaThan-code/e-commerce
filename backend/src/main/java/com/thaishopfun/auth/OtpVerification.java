package com.thaishopfun.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_verifications")
public class OtpVerification {
    @Id private UUID id;
    @Column(name = "user_id") private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OtpChannel channel;
    @Column(name = "destination_masked", nullable = false) private String destinationMasked;
    @Column(name = "destination_hash", nullable = false, length = 64) private String destinationHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private OtpPurpose purpose;
    @Column(name = "code_hash", nullable = false) private String codeHash;
    /** SHA-256 of one-time email verify-link token (EMAIL_VERIFY only). */
    @Column(name = "verify_link_token_hash", length = 64) private String verifyLinkTokenHash;
    @Column(nullable = false) private int attempts;
    @Column(name = "max_attempts", nullable = false) private int maxAttempts;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OtpStatus status;
    @Column(name = "step_up_action", length = 80) private String stepUpAction;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;

    protected OtpVerification() {}

    public OtpVerification(UUID userId, OtpChannel channel, String destinationMasked, String destinationHash,
                           OtpPurpose purpose, String codeHash, Instant expiresAt, int maxAttempts) {
        var now = Instant.now();
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.channel = channel;
        this.destinationMasked = destinationMasked;
        this.destinationHash = destinationHash;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.attempts = 0;
        this.maxAttempts = maxAttempts;
        this.expiresAt = expiresAt;
        this.status = OtpStatus.PENDING;
        this.createdDateTime = now;
        this.modifiedDateTime = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public OtpChannel getChannel() { return channel; }
    public String getDestinationMasked() { return destinationMasked; }
    public String getDestinationHash() { return destinationHash; }
    public OtpPurpose getPurpose() { return purpose; }
    public String getCodeHash() { return codeHash; }
    public String getVerifyLinkTokenHash() { return verifyLinkTokenHash; }
    public void assignVerifyLinkTokenHash(String verifyLinkTokenHash) {
        this.verifyLinkTokenHash = verifyLinkTokenHash;
        touch();
    }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public OtpStatus getStatus() { return status; }
    public Instant getCreatedDateTime() { return createdDateTime; }

    public boolean isPending() { return status == OtpStatus.PENDING; }

    public void bindUser(UUID userId) { this.userId = userId; touch(); }

    public void recordFailedAttempt() {
        this.attempts += 1;
        if (this.attempts >= this.maxAttempts) {
            this.status = OtpStatus.FAILED;
        }
        touch();
    }

    public void markExpired() {
        this.status = OtpStatus.EXPIRED;
        touch();
    }

    public void markVerified() {
        this.verifiedAt = Instant.now();
        this.status = OtpStatus.VERIFIED;
        touch();
    }

    private void touch() { this.modifiedDateTime = Instant.now(); }
}