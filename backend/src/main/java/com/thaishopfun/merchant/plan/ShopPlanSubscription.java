package com.thaishopfun.merchant.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop_plan_subscriptions")
public class ShopPlanSubscription {
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String ACTIVE = "ACTIVE";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";
    public static final String MONTHLY = "MONTHLY";
    public static final String YEARLY = "YEARLY";

    @Id
    private UUID id;
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;
    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;
    @Column(name = "billing_cycle", nullable = false, length = 10)
    private String billingCycle;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "THB";
    @Column(nullable = false, length = 30)
    private String status;
    @Column(name = "starts_at")
    private Instant startsAt;
    @Column(name = "ends_at")
    private Instant endsAt;
    @Column(name = "payment_proof_file_id")
    private UUID paymentProofFileId;
    @Column(name = "reject_reason", columnDefinition = "text")
    private String rejectReason;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShopPlanSubscription() {}

    public ShopPlanSubscription(UUID shopId, String planCode, String billingCycle, BigDecimal amount, UUID createdBy) {
        var now = Instant.now();
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.planCode = planCode;
        this.billingCycle = billingCycle;
        this.amount = amount;
        this.currency = "THB";
        this.status = PENDING_PAYMENT;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void attachProof(UUID mediaFileId) {
        this.paymentProofFileId = mediaFileId;
        this.status = PENDING_REVIEW;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void approve(UUID reviewer, Instant startsAt, Instant endsAt) {
        this.status = ACTIVE;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.rejectReason = null;
        this.updatedAt = this.reviewedAt;
    }

    public void reject(UUID reviewer, String reason) {
        this.status = REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.rejectReason = reason;
        this.updatedAt = this.reviewedAt;
    }

    public void expire() {
        this.status = EXPIRED;
        this.updatedAt = Instant.now();
    }

    public boolean isOpen() {
        return PENDING_PAYMENT.equals(status) || PENDING_REVIEW.equals(status);
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public String getPlanCode() { return planCode; }
    public String getBillingCycle() { return billingCycle; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public UUID getPaymentProofFileId() { return paymentProofFileId; }
    public String getRejectReason() { return rejectReason; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
