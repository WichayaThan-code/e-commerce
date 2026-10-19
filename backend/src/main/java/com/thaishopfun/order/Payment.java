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
 * V1 {@code payments}: one row per payment attempt (attempt_no). Manual transfer: PENDING while the slip waits for the
 * shop, CAPTURED when the shop verifies it, FAILED when rejected (the buyer retries with a new attempt), REFUNDED once
 * the full captured amount has been refunded with proof. Gateways may create INTENT rows (see payment.gateway).
 */
@Entity
@Table(name = "payments", uniqueConstraints = {
    @UniqueConstraint(name = "uq_payments_idempotency_key", columnNames = "idempotency_key"),
    @UniqueConstraint(name = "uq_payments_provider_provider_payment_id", columnNames = {"provider", "provider_payment_id"})})
public class Payment extends AuditedEntity {
    public static final String PENDING = "PENDING";
    public static final String CAPTURED = "CAPTURED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";
    public static final String REFUNDED = "REFUNDED";

    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    /** OMISE | 2C2P | PROMPTPAY | BANK_TRANSFER | CUSTOM (ck_payments_provider). */
    @Column(nullable = false, length = 50) private String provider;
    /** Expected amount while pending; the amount actually received once captured. */
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 3) private String currency = "THB";
    /** INTENT | PENDING | AUTHORIZED | CAPTURED | FAILED | CANCELLED | REFUNDED (ck_payments_status). */
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "provider_payment_id", length = 120) private String providerPaymentId;
    @Column(name = "idempotency_key", nullable = false, length = 120) private String idempotencyKey;
    @Column(name = "intent_id", length = 120) private String intentId;
    @Column(name = "captured_at") private Instant capturedAt;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "attempt_no", nullable = false) private int attemptNo = 1;
    @Column(name = "failure_code", length = 64) private String failureCode;

    protected Payment() {}

    private Payment(UUID orderId, UUID shopId, String provider, BigDecimal amount, String status, int attemptNo) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.shopId = shopId;
        this.provider = provider;
        this.amount = amount;
        this.status = status;
        this.attemptNo = attemptNo;
        this.idempotencyKey = "order:" + orderId + ":payment:" + attemptNo;
    }

    /** Manual transfer waiting for the shop to check the slip. */
    public static Payment pending(UUID orderId, UUID shopId, String provider, BigDecimal expected, int attemptNo) {
        return new Payment(orderId, shopId, provider, expected, PENDING, attemptNo);
    }

    /** Captured immediately (shop marks paid without a slip, or a gateway callback). */
    public static Payment captured(UUID orderId, UUID shopId, String provider, BigDecimal amount, String reference, Instant at, int attemptNo) {
        var payment = new Payment(orderId, shopId, provider, amount, PENDING, attemptNo);
        payment.capture(amount, reference, at);
        return payment;
    }

    public void capture(BigDecimal received, String reference, Instant at) {
        this.status = CAPTURED;
        if (received != null) this.amount = received;
        if (reference != null && this.providerPaymentId == null) this.providerPaymentId = reference;
        this.capturedAt = at;
        this.paidAt = at;
        this.failureCode = null;
    }

    public void fail(String code) {
        this.status = FAILED;
        this.failureCode = code;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getShopId() { return shopId; }
    public String getProvider() { return provider; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getIntentId() { return intentId; }
    public void setIntentId(String intentId) { this.intentId = intentId; }
    public Instant getCapturedAt() { return capturedAt; }
    public Instant getPaidAt() { return paidAt; }
    public int getAttemptNo() { return attemptNo; }
    public String getFailureCode() { return failureCode; }
}
