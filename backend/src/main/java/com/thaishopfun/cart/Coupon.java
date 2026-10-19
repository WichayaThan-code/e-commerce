package com.thaishopfun.cart;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** V1 {@code coupons}: a code (UNIQUE per shop, stored upper-case) that applies a {@link Promotion}. */
@Entity
@Table(name = "coupons")
public class Coupon extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "promotion_id", nullable = false) private UUID promotionId;
    @Column(nullable = false, length = 64) private String code;
    @Column(name = "is_active", nullable = false) private boolean active = true;

    protected Coupon() {}

    public Coupon(UUID shopId, UUID promotionId, String code) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.promotionId = promotionId;
        this.code = code;
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public UUID getPromotionId() { return promotionId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
