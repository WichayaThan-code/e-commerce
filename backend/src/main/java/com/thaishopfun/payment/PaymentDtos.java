package com.thaishopfun.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.thaishopfun.order.OrderDtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Payment / refund API shapes. Money in THB, 2 dp. Slip and refund-proof files are served through fileUrl (auth required). */
public final class PaymentDtos {
    private PaymentDtos() {}

    // ---- shop receiving accounts (shop_payment_gateways BANK_TRANSFER / PROMPTPAY) ----
    public record PaymentAccountRequest(String displayName, String bankName, String accountNumber, String accountName,
                                        String promptPayId, Boolean active) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentAccountView(String provider, String displayName, String bankName, String accountNumber, String accountName,
                                     String promptPayId, boolean active, boolean masked) {}

    // ---- buyer payment page ----
    public record PaymentInfo(UUID orderId, String orderNumber, String orderStatus, String paymentStatus, BigDecimal amountDue,
                              String currency, boolean canSubmitSlip, List<PaymentAccountView> accounts, List<PaymentView> payments) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentView(UUID id, String provider, String status, BigDecimal amount, String currency, int attemptNo,
                              Instant createdAt, Instant capturedAt, String failureCode, List<ProofView> proofs, List<StatusEntry> history) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProofView(UUID id, String status, String referenceNo, BigDecimal amountDeclared, boolean amountMismatch,
                            String bankName, Instant transferredAt, String fileUrl, String contentType, Instant uploadedAt,
                            Instant reviewedAt, String rejectReason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StatusEntry(Long id, String event, String fromStatus, String toStatus, String actorType, UUID actorUserId,
                              String reason, Map<String, Object> metadata, Instant at) {}

    /** confirm / reject slip. acceptAmountMismatch + reason: shop accepts a slip whose amount differs from the order total. */
    public record PaymentActionRequest(String reason, Boolean acceptAmountMismatch, Integer expectedVersion) {}

    public record PaymentActionResponse(boolean changed, PaymentView payment, OrderDtos.OrderDetail order) {}

    // ---- refunds ----
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RefundView(UUID id, String status, BigDecimal amount, String currency, String reason, String requesterType,
                             UUID requestedBy, Instant requestedAt, Instant approvedAt, Instant rejectedAt, String rejectReason,
                             Boolean restock, String proofReference, Instant proofTransferredAt, Instant proofUploadedAt,
                             String proofFileUrl, Instant completedAt, String buyerAckStatus, Instant buyerAckAt, String buyerAckNote,
                             List<StatusEntry> history) {}

    public record RefundSummary(UUID orderId, String orderStatus, String paymentStatus, BigDecimal paidAmount,
                                BigDecimal refundedAmount, BigDecimal refundableAmount, List<RefundView> refunds) {}

    /** approve: restock (after dispatch, default true). reject / dispute / admin resolve: reason. confirm-received: note. */
    public record RefundActionRequest(String reason, Boolean restock, String note) {}

    public record RefundActionResponse(boolean changed, RefundView refund, OrderDtos.OrderDetail order) {}

    // ---- webhook ----
    public record WebhookAck(boolean received, boolean duplicate, String status) {}
}
