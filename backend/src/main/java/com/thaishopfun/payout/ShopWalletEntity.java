package com.thaishopfun.payout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Projection of shop_wallets — ledger is source of truth; this row is updated atomically with ledger inserts. */
@Entity
@Table(name = "shop_wallets")
class ShopWalletEntity {
    @Id
    @Column(name = "shop_id")
    private UUID shopId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "THB";

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal available = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal reserved = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
