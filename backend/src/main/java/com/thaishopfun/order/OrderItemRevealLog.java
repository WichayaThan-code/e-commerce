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

/** V1 {@code order_item_reveal_logs}: audit of the moment line items became visible to the seller (merchant confirm). */
@Entity
@Table(name = "order_item_reveal_logs")
public class OrderItemRevealLog extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "revealed_by_user_id") private UUID revealedByUserId;
    @ColumnDefault("'MERCHANT_CONFIRM'") @Column(name = "reveal_reason", nullable = false, length = 40) private String revealReason;
    @Column(name = "revealed_at", nullable = false) private Instant revealedAt;

    protected OrderItemRevealLog() {}

    public OrderItemRevealLog(UUID orderId, UUID shopId, UUID revealedBy, String reason, Instant at) {
        this.orderId = orderId;
        this.shopId = shopId;
        this.revealedByUserId = revealedBy;
        this.revealReason = reason;
        this.revealedAt = at;
        stamp(revealedBy);
    }

    public Long getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getRevealReason() { return revealReason; }
}
