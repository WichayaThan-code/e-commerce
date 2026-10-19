package com.thaishopfun.promotion;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;

import java.util.UUID;

/** Coupon code of a promotion (V1 table coupons, UNIQUE (shop_id, code)). Codes are stored upper-case. */
@Entity
@Table(name = "coupons", uniqueConstraints = @UniqueConstraint(name = "uq_coupons_shop_id_code", columnNames = {"shop_id", "code"}))
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
