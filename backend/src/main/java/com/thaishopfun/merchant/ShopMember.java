package com.thaishopfun.merchant;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Minimal mapping of V1 {@code shop_members}. V1 enforces (deferred constraint trigger) that every shop that
 * is not CLOSED has exactly one ACTIVE STORE_OWNER member equal to shops.owner_user_id, so a membership row
 * is written in the same transaction that creates the shop.
 */
@Entity
@Table(name = "shop_members")
public class ShopMember {
    @Id private UUID id;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 40) private String role;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "permission_version", nullable = false) private int permissionVersion;
    @Column(name = "invited_by") private UUID invitedBy;
    @Column(name = "joined_at", nullable = false) private Instant joinedAt;
    @Column(name = "removed_at") private Instant removedAt;
    @Column(name = "removed_by") private UUID removedBy;
    @Column(name = "remove_reason_code", length = 50) private String removeReasonCode;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;
    @Column(name = "modified_by") private UUID modifiedBy;

    protected ShopMember() {}

    public static ShopMember storeOwner(UUID shopId, UUID userId) {
        var member = new ShopMember();
        var now = Instant.now();
        member.id = UUID.randomUUID();
        member.shopId = shopId;
        member.userId = userId;
        member.role = "STORE_OWNER";
        member.status = "ACTIVE";
        member.permissionVersion = 0;
        member.joinedAt = now;
        member.createdDateTime = now;
        member.modifiedDateTime = now;
        return member;
    }

    public UUID getShopId() { return shopId; }
    public UUID getUserId() { return userId; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public Instant getRemovedAt() { return removedAt; }
}
