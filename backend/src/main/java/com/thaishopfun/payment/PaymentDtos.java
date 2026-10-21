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

    // ---- payment methods / COD (V29) ----
    /** PUT …/payment-methods/cod. fee 0–9999.99 (default 0, added to the order total); maxOrderAmount null = no limit. */
    public record CodSettingsRequest(Boolean enabled, BigDecimal fee, BigDecimal maxOrderAmount) {}

    /**
     * method BANK_TRANSFER: always enabled (accountsConfigured = the shop has an active receiving account for slips).
     * method COD: enabled / fee / maxOrderAmount from the shop's COD setting.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentMethodView(String method, boolean enabled, BigDecimal fee, BigDecimal maxOrderAmount, Boolean accountsConfigured,
                                    Instant updatedAt) {}

    public record PaymentMethodsView(Long shopId, List<PaymentMethodView> methods) {}

    /**
     * Cash collected for a delivered COD order. amount required (must equal the order total unless acceptAmountMismatch=true
     * + reason); collectedAt ISO / local Bangkok time (default now); reference = receipt / carrier COD remittance no. (optional).
     */
    public record CodCollectRequest(BigDecimal amount, String collectedAt, String reference, String reason,
                                    Boolean acceptAmountMismatch, Integer expectedVersion) {}

    // ---- buyer payment page ----
    /* paymentMethod COD: no slip (canSubmitSlip=false, accounts empty); amountDue incl. codFee is paid in cash on delivery. */
    public record PaymentInfo(UUID orderId, String orderNumber, String orderStatus, String paymentStatus, BigDecimal amountDue,
                              String currency, boolean canSubmitSlip, List<PaymentAccountView> accounts, List<PaymentView> payments,
                              String paymentMethod, BigDecimal codFee) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentView(UUID id, String provider, String status, BigDecimal amount, String currency, int attemptNo,
                              Instant createdAt, Instant capturedAt, String failureCode, List<ProofView> proofs, List<StatusEntry> history) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProofView(UUID id, String status, String referenceNo, BigDecimal amountDeclared, boolean amountMismatch,
                            String bankName, Instant transferredAt, String fileUrl, String contentType, Instant uploadedAt,
                            Instant reviewedAt, String rejectReason,
                            /* SLIP | COD_RECEIPT (evidence of cash collected; transferredAt = collectedAt) */
                            String proofType) {}

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
