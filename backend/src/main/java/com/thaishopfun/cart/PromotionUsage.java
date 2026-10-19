package com.thaishopfun.cart;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * V1 {@code promotion_usages}: one row per order that used a promotion. Written by order creation (not built yet);
 * the cart reads it to enforce usage limits (total and per buyer).
 */
@Entity
@Table(name = "promotion_usages")
public class PromotionUsage extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "promotion_id", nullable = false) private UUID promotionId;
    @Column(name = "coupon_id") private UUID couponId;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "buyer_user_id") private UUID buyerUserId;
    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2) private BigDecimal discountAmount;
    @Column(name = "used_at", nullable = false) private Instant usedAt;

    protected PromotionUsage() {}

    public PromotionUsage(UUID promotionId, UUID couponId, UUID orderId, UUID buyerUserId, BigDecimal discountAmount) {
        this.id = UUID.randomUUID();
        this.promotionId = promotionId;
        this.couponId = couponId;
        this.orderId = orderId;
        this.buyerUserId = buyerUserId;
        this.discountAmount = discountAmount;
        this.usedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPromotionId() { return promotionId; }
    public UUID getCouponId() { return couponId; }
    public UUID getOrderId() { return orderId; }
    public UUID getBuyerUserId() { return buyerUserId; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public Instant getUsedAt() { return usedAt; }
}
