package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "from_status", length = 40) private String fromStatus;
    @Column(name = "to_status", nullable = false, length = 40) private String toStatus;
    @Column(name = "changed_by") private UUID changedBy;
    @Column(name = "changed_by_type", length = 30) private String changedByType;
    @Column(length = 500) private String reason;
    @Column(name = "request_id", length = 64) private String requestId;
    @Column(name = "metadata_json", columnDefinition = "text") private String metadataJson;

    protected OrderStatusHistory() {}

    public OrderStatusHistory(UUID orderId, OrderStatus from, OrderStatus to, UUID actorId, OrderActorType actorType, String reason) {
        this.orderId = orderId;
        this.fromStatus = from == null ? null : from.name();
        this.toStatus = to.name();
        this.changedBy = actorId;
        this.changedByType = actorType == null ? null : actorType.name();
        this.reason = reason;
    }

    public Long getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public UUID getChangedBy() { return changedBy; }
    public String getChangedByType() { return changedByType; }
    public String getReason() { return reason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
