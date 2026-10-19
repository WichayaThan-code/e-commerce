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

/** V26 {@code payment_status_history}: append-only status log (who, actor type, reason, from → to). */
@Entity
@Table(name = "payment_status_history")
public class PaymentStatusHistory extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "proof_id") private UUID proofId;
    @Column(name = "from_status", length = 30) private String fromStatus;
    @Column(name = "to_status", nullable = false, length = 30) private String toStatus;
    @Column(nullable = false, length = 60) private String event;
    @Column(name = "actor_type", nullable = false, length = 20) private String actorType;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Column(length = 500) private String reason;
    @Column(name = "metadata_json", columnDefinition = "text") private String metadataJson;

    protected PaymentStatusHistory() {}

    public PaymentStatusHistory(UUID paymentId, UUID orderId, UUID shopId, String fromStatus, String toStatus, String event,
                  OrderActor actor, String reason, String metadataJson) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.shopId = shopId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.event = event;
        this.actorType = actor.type().name();
        this.actorUserId = actor.userId();
        this.reason = reason;
        this.metadataJson = metadataJson;
        stamp(actor.userId());
    }

    public Long getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getOrderId() { return orderId; }
    public UUID getProofId() { return proofId; }
    public void setProofId(UUID proofId) { this.proofId = proofId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getEvent() { return event; }
    public String getActorType() { return actorType; }
    public UUID getActorUserId() { return actorUserId; }
    public String getReason() { return reason; }
    public String getMetadataJson() { return metadataJson; }
}
