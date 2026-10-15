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

/** V1 {@code payment_webhook_events}: gateway webhook inbox, UNIQUE(provider, event_id) makes deliveries idempotent. */
@Entity
@Table(name = "payment_webhook_events", uniqueConstraints = @UniqueConstraint(name = "uq_payment_webhook_events_provider_event_id", columnNames = {"provider", "event_id"}))
public class PaymentWebhookEvent extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "shop_id") private UUID shopId;
    @Column(nullable = false, length = 50) private String provider;
    @Column(name = "event_id", nullable = false, length = 120) private String eventId;
    @Column(name = "event_type", nullable = false, length = 80) private String eventType;
    @Column(name = "payment_id") private UUID paymentId;
    @Column(name = "order_id") private UUID orderId;
    @ColumnDefault("false") @Column(name = "signature_valid", nullable = false) private boolean signatureValid;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "payload_safe") private Map<String, Object> payloadSafe;
    @Column(name = "processed_at") private Instant processedAt;
    @ColumnDefault("'RECEIVED'") @Column(name = "process_status", nullable = false, length = 30) private String processStatus = "RECEIVED";

    protected PaymentWebhookEvent() {}

    public PaymentWebhookEvent(String provider, String eventId, String eventType, UUID orderId, Map<String, Object> payloadSafe) {
        this.id = UUID.randomUUID();
        this.provider = provider;
        this.eventId = eventId;
        this.eventType = eventType;
        this.orderId = orderId;
        this.signatureValid = true;
        this.payloadSafe = payloadSafe;
    }

    public void processed(String status, UUID shopId, UUID paymentId, Instant at) {
        this.processStatus = status;
        this.shopId = shopId;
        this.paymentId = paymentId;
        this.processedAt = at;
    }

    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public UUID getOrderId() { return orderId; }
    public String getProcessStatus() { return processStatus; }
}
