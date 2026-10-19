package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * V1 {@code refunds} + V26 workflow columns. The platform never moves money: a refund is a recorded request that the
 * shop approves, pays back itself and proves with a slip. Several refunds per order are allowed (partial), but the
 * sum of refunded amounts never exceeds the captured payment.
 * REQUESTED → APPROVED → PROOF_UPLOADED → COMPLETED (buyer confirms) | DISPUTED (buyer disputes; shop re-uploads)
 * REQUESTED/APPROVED → REJECTED.
 */
@Entity
@Table(name = "refunds", uniqueConstraints = @UniqueConstraint(name = "uq_refunds_idempotency_key", columnNames = "idempotency_key"))
public class Refund extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "payment_id") private UUID paymentId;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 3) private String currency = "THB";
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "idempotency_key", nullable = false, length = 120) private String idempotencyKey;
    @Column(name = "initiated_by") private UUID initiatedBy;
    @Column(length = 500) private String reason;
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "requester_type", length = 20) private String requesterType;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "rejected_by") private UUID rejectedBy;
    @Column(name = "rejected_at") private Instant rejectedAt;
    @Column(name = "reject_reason", length = 500) private String rejectReason;
    @Column(name = "restock") private Boolean restock;
    @Column(name = "proof_media_file_id") private UUID proofMediaFileId;
    @Column(name = "proof_reference", length = 120) private String proofReference;
    @Column(name = "proof_transferred_at") private Instant proofTransferredAt;
    @Column(name = "proof_uploaded_by") private UUID proofUploadedBy;
    @Column(name = "proof_uploaded_at") private Instant proofUploadedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @ColumnDefault("'NONE'") @Column(name = "buyer_ack_status", nullable = false, length = 20) private String buyerAckStatus = "NONE";
    @Column(name = "buyer_ack_at") private Instant buyerAckAt;
    @Column(name = "buyer_ack_note", length = 500) private String buyerAckNote;

    protected Refund() {}

    public Refund(UUID orderId, UUID paymentId, UUID shopId, BigDecimal amount, RefundStatus status, int sequence,
                  UUID requestedBy, OrderActorType requesterType, String reason) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.shopId = shopId;
        this.amount = amount;
        this.status = status.name();
        this.idempotencyKey = "order:" + orderId + ":refund:" + sequence;
        this.initiatedBy = requestedBy;
        this.requestedBy = requestedBy;
        this.requesterType = requesterType.name();
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getShopId() { return shopId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public RefundStatus getStatus() { return RefundStatus.valueOf(status); }
    public void setStatus(RefundStatus status) { this.status = status.name(); }
    public String getReason() { return reason; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getRequesterType() { return requesterType; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void approve(UUID by, Instant at) { this.approvedBy = by; this.approvedAt = at; this.status = RefundStatus.APPROVED.name(); }
    public UUID getRejectedBy() { return rejectedBy; }
    public Instant getRejectedAt() { return rejectedAt; }
    public String getRejectReason() { return rejectReason; }
    public void reject(UUID by, Instant at, String reason) { this.rejectedBy = by; this.rejectedAt = at; this.rejectReason = reason; this.status = RefundStatus.REJECTED.name(); }
    public Boolean getRestock() { return restock; }
    public void setRestock(Boolean restock) { this.restock = restock; }
    public UUID getProofMediaFileId() { return proofMediaFileId; }
    public String getProofReference() { return proofReference; }
    public Instant getProofTransferredAt() { return proofTransferredAt; }
    public UUID getProofUploadedBy() { return proofUploadedBy; }
    public Instant getProofUploadedAt() { return proofUploadedAt; }
    public void proof(UUID mediaFileId, String reference, Instant transferredAt, UUID by, Instant at) {
        this.proofMediaFileId = mediaFileId;
        this.proofReference = reference;
        this.proofTransferredAt = transferredAt;
        this.proofUploadedBy = by;
        this.proofUploadedAt = at;
        this.status = RefundStatus.PROOF_UPLOADED.name();
        this.buyerAckStatus = "NONE";
        this.buyerAckAt = null;
        this.buyerAckNote = null;
    }
    public Instant getCompletedAt() { return completedAt; }
    public String getBuyerAckStatus() { return buyerAckStatus; }
    public Instant getBuyerAckAt() { return buyerAckAt; }
    public String getBuyerAckNote() { return buyerAckNote; }
    public void complete(Instant at, boolean byBuyer, String note) {
        this.status = RefundStatus.COMPLETED.name();
        this.completedAt = at;
        if (byBuyer) { this.buyerAckStatus = "CONFIRMED"; this.buyerAckAt = at; this.buyerAckNote = note; }
    }
    public void dispute(Instant at, String note) {
        this.status = RefundStatus.DISPUTED.name();
        this.buyerAckStatus = "DISPUTED";
        this.buyerAckAt = at;
        this.buyerAckNote = note;
    }
}
