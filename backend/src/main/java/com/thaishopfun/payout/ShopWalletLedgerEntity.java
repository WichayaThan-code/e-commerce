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
import java.util.UUID;

@Entity
@Table(name = "shop_wallet_ledger", uniqueConstraints = @UniqueConstraint(name = "uq_shop_wallet_ledger_idempotency", columnNames = {"shop_id", "idempotency_key"}))
class ShopWalletLedgerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "entry_type", nullable = false, length = 32)
    private String entryType;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "THB";

    @Column(name = "balance_available_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAvailableAfter;

    @Column(name = "balance_reserved_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceReservedAfter;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "source_id", length = 128)
    private String sourceId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
