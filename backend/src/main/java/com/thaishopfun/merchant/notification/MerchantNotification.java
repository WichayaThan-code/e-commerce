package com.thaishopfun.merchant.notification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Durable merchant lifecycle inbox row ({@code merchant_notifications}). */
@Entity
@Table(name = "merchant_notifications", uniqueConstraints = @UniqueConstraint(name = "uq_merchant_notifications_dedupe", columnNames = "dedupe_key"))
public class MerchantNotification {
    @Id
    private UUID id;
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;
    @Column(nullable = false, length = 64)
    private String type;
    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;
    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;
    @Column(name = "dedupe_key", nullable = false, length = 191, unique = true)
    private String dedupeKey;
    @Column(name = "required_permission", nullable = false, length = 64)
    private String requiredPermission;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MerchantNotification() {}

    public MerchantNotification(
        UUID shopId,
        String type,
        String entityType,
        String entityId,
        String dedupeKey,
        String requiredPermission,
        Instant createdAt
    ) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.type = type;
        this.entityType = entityType;
        this.entityId = entityId;
        this.dedupeKey = dedupeKey;
        this.requiredPermission = requiredPermission;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public String getType() { return type; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getDedupeKey() { return dedupeKey; }
    public String getRequiredPermission() { return requiredPermission; }
    public Instant getCreatedAt() { return createdAt; }
}
