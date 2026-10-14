package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** V1 {@code payments}: written when an order is marked paid (gateway integration is out of scope). */
@Entity
@Table(name = "payments", uniqueConstraints = {
    @UniqueConstraint(name = "uq_payments_idempotency_key", columnNames = "idempotency_key"),
    @UniqueConstraint(name = "uq_payments_provider_provider_payment_id", columnNames = {"provider", "provider_payment_id"})})
public class Payment extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    /** OMISE | 2C2P | PROMPTPAY | BANK_TRANSFER | CUSTOM (ck_payments_provider). */
    @Column(nullable = false, length = 50) private String provider;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 3) private String currency = "THB";
    /** INTENT | PENDING | AUTHORIZED | CAPTURED | FAILED | CANCELLED | REFUNDED (ck_payments_status). */
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "provider_payment_id", length = 120) private String providerPaymentId;
    @Column(name = "idempotency_key", nullable = false, length = 120) private String idempotencyKey;
    @Column(name = "captured_at") private Instant capturedAt;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "attempt_no", nullable = false) private int attemptNo = 1;

    protected Payment() {}

    public Payment(UUID orderId, UUID shopId, String provider, BigDecimal amount, String reference, Instant paidAt) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.shopId = shopId;
        this.provider = provider;
        this.amount = amount;
        this.status = "CAPTURED";
        this.providerPaymentId = reference;
        this.idempotencyKey = "order:" + orderId + ":payment";
        this.capturedAt = paidAt;
        this.paidAt = paidAt;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getProvider() { return provider; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public Instant getPaidAt() { return paidAt; }
}
