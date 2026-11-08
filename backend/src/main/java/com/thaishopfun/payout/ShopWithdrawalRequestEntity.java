package com.thaishopfun.payout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "shop_withdrawal_requests",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_shop_withdrawal_idempotency", columnNames = {"shop_id", "idempotency_key"}),
        @UniqueConstraint(name = "uq_shop_withdrawal_public_id", columnNames = "public_id"),
        @UniqueConstraint(name = "uq_shop_withdrawal_external_transfer_key", columnNames = "external_transfer_key")
    }
)
class ShopWithdrawalRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "THB";

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "account_provider", nullable = false, length = 32)
    private String accountProvider;

    @Column(name = "account_bank_name", length = 120)
    private String accountBankName;

    @Column(name = "account_name", length = 255)
    private String accountName;

    @Column(name = "account_number_masked", nullable = false, length = 64)
    private String accountNumberMasked;

    @Column(name = "account_number_last4", length = 8)
    private String accountNumberLast4;

    @Column(name = "account_promptpay_masked", length = 64)
    private String accountPromptpayMasked;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reject_reason", columnDefinition = "text")
    private String rejectReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "transfer_reference", length = 128)
    private String transferReference;

    @Column(name = "transfer_amount", precision = 18, scale = 2)
    private BigDecimal transferAmount;

    @Column(name = "transfer_date")
    private LocalDate transferDate;

    @Column(name = "external_transfer_key", length = 128)
    private String externalTransferKey;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(nullable = false)
    private long version;
}
