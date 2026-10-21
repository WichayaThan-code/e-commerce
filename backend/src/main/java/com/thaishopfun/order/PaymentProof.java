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

/** V1 {@code payment_proofs} (+ V26 bank_name, amount_mismatch): one row per slip submission. Resubmitting creates a new row. */
@Entity
@Table(name = "payment_proofs")
public class PaymentProof extends AuditedEntity {
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String VERIFIED = "VERIFIED";
    public static final String REJECTED = "REJECTED";

    @Id private UUID id;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "uploaded_by_user_id") private UUID uploadedByUserId;
    @Column(name = "media_file_id") private UUID mediaFileId;
    @ColumnDefault("'SLIP'") @Column(name = "proof_type", nullable = false, length = 30) private String proofType = "SLIP";
    @Column(name = "reference_no", length = 120) private String referenceNo;
    @Column(name = "amount_declared", precision = 18, scale = 2) private BigDecimal amountDeclared;
    @JdbcTypeCode(SqlTypes.CHAR) @ColumnDefault("'THB'") @Column(nullable = false, length = 3) private String currency = "THB";
    @Column(name = "transferred_at") private Instant transferredAt;
    @ColumnDefault("'PENDING_REVIEW'") @Column(nullable = false, length = 30) private String status = PENDING_REVIEW;
    @Column(name = "verified_by") private UUID verifiedBy;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "reject_reason", length = 500) private String rejectReason;
    @Column(name = "bank_name", length = 100) private String bankName;
    @ColumnDefault("false") @Column(name = "amount_mismatch", nullable = false) private boolean amountMismatch;

    protected PaymentProof() {}

    public PaymentProof(UUID paymentId, UUID orderId, UUID shopId, UUID uploadedBy, UUID mediaFileId, String referenceNo,
                        BigDecimal amountDeclared, Instant transferredAt, String bankName, boolean amountMismatch) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.shopId = shopId;
        this.uploadedByUserId = uploadedBy;
        this.mediaFileId = mediaFileId;
        this.referenceNo = referenceNo;
        this.amountDeclared = amountDeclared;
        this.transferredAt = transferredAt;
        this.bankName = bankName;
        this.amountMismatch = amountMismatch;
    }

    /** SLIP (default) | GATEWAY_RECEIPT | COD_RECEIPT (cash collected on delivery, V29). */
    public void setProofType(String proofType) { this.proofType = proofType; }
    public String getProofType() { return proofType; }
    public void verify(UUID by, Instant at) { this.status = VERIFIED; this.verifiedBy = by; this.verifiedAt = at; }
    public void reject(UUID by, Instant at, String reason) { this.status = REJECTED; this.verifiedBy = by; this.verifiedAt = at; this.rejectReason = reason; }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getOrderId() { return orderId; }
    public UUID getShopId() { return shopId; }
    public UUID getUploadedByUserId() { return uploadedByUserId; }
    public UUID getMediaFileId() { return mediaFileId; }
    public String getReferenceNo() { return referenceNo; }
    public BigDecimal getAmountDeclared() { return amountDeclared; }
    public Instant getTransferredAt() { return transferredAt; }
    public String getStatus() { return status; }
    public UUID getVerifiedBy() { return verifiedBy; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public String getRejectReason() { return rejectReason; }
    public String getBankName() { return bankName; }
    public boolean isAmountMismatch() { return amountMismatch; }
}
