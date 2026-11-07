package com.thaishopfun.admin.notification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_notifications")
public class AdminNotification {
    @Id
    private UUID id;
    @Column(nullable = false, length = 64)
    private String type;
    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;
    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;
    @Column(name = "shop_id")
    private Long shopId;
    @Column(name = "shop_name", length = 255)
    private String shopName;
    @Column(name = "dedupe_key", nullable = false, length = 191, unique = true)
    private String dedupeKey;
    @Column(name = "required_permission", nullable = false, length = 64)
    private String requiredPermission;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AdminNotification() {}

    public AdminNotification(
        String type,
        String entityType,
        String entityId,
        Long shopId,
        String shopName,
        String dedupeKey,
        String requiredPermission
    ) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.entityType = entityType;
        this.entityId = entityId;
        this.shopId = shopId;
        this.shopName = shopName;
        this.dedupeKey = dedupeKey;
        this.requiredPermission = requiredPermission;
        this.createdAt = Instant.now();
    }

    /** Backfill / tests: preserve source timestamp. */
    public AdminNotification(
        String type,
        String entityType,
        String entityId,
        Long shopId,
        String shopName,
        String dedupeKey,
        String requiredPermission,
        Instant createdAt
    ) {
        this(type, entityType, entityId, shopId, shopName, dedupeKey, requiredPermission);
        if (createdAt != null) this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getType() { return type; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public Long getShopId() { return shopId; }
    public String getShopName() { return shopName; }
    public String getDedupeKey() { return dedupeKey; }
    public String getRequiredPermission() { return requiredPermission; }
    public Instant getCreatedAt() { return createdAt; }
}
