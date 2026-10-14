package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/** V1 {@code refunds}: full refund recorded on REFUNDED or on cancelling a paid order. */
@Entity
@Table(name = "refunds", uniqueConstraints = @UniqueConstraint(name = "uq_refunds_idempotency_key", columnNames = "idempotency_key"))
public class Refund extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "payment_id") private UUID paymentId;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 3) private String currency = "THB";
    /** PENDING | SUCCEEDED | FAILED (ck_refunds_status). */
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "idempotency_key", nullable = false, length = 120) private String idempotencyKey;
    @Column(name = "initiated_by") private UUID initiatedBy;
    @Column(length = 500) private String reason;

    protected Refund() {}

    public Refund(UUID orderId, UUID paymentId, UUID shopId, BigDecimal amount, UUID initiatedBy, String reason) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.shopId = shopId;
        this.amount = amount;
        this.status = "SUCCEEDED";
        this.idempotencyKey = "order:" + orderId + ":refund";
        this.initiatedBy = initiatedBy;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
}
