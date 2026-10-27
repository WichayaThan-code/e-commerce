package com.thaishopfun.order;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "order_bulk_requests", uniqueConstraints = {
    @UniqueConstraint(name = "ux_order_bulk_requests", columnNames = {"shop_id", "actor_user_id", "idempotency_key"})
})
public class OrderBulkRequest {
    @Id private UUID id;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "actor_user_id", nullable = false) private UUID actorUserId;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @Column(nullable = false, length = 20) private String action;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> response;
    @Column(name = "created_date_time", nullable = false, updatable = false) private Instant createdDateTime;

    protected OrderBulkRequest() {}

    public OrderBulkRequest(UUID shopId, UUID actorUserId, String idempotencyKey, String action,
                            String requestHash, Map<String, Object> response) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.actorUserId = actorUserId;
        this.idempotencyKey = idempotencyKey;
        this.action = action;
        this.requestHash = requestHash;
        this.response = response;
        this.createdDateTime = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdDateTime == null) createdDateTime = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getAction() { return action; }
    public String getRequestHash() { return requestHash; }
    public Map<String, Object> getResponse() { return response; }
    public Instant getCreatedDateTime() { return createdDateTime; }
}
