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
    @Column(name = "joined_at", nullable = false) private Instant joinedAt;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;

    protected ShopMember() {}

    public static ShopMember storeOwner(UUID shopId, UUID userId) {
        var member = new ShopMember();
        var now = Instant.now();
        member.id = UUID.randomUUID();
        member.shopId = shopId;
        member.userId = userId;
        member.role = "STORE_OWNER";
        member.status = "ACTIVE";
        member.joinedAt = now;
        member.createdDateTime = now;
        member.modifiedDateTime = now;
        return member;
    }

    public UUID getShopId() { return shopId; }
    public UUID getUserId() { return userId; }
    public String getRole() { return role; }
}
