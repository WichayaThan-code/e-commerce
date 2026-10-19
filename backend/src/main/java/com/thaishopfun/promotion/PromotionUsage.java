package com.thaishopfun.promotion;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of V1 promotion_usages (written by checkout later — coupon redemption is not implemented yet).
 * Used for the merchant usage counters and to block deleting a promotion that was already used.
 */
@Entity(name = "MerchantPromotionUsage")
@Immutable
@Table(name = "promotion_usages")
public class PromotionUsage {
    @Id private UUID id;
    @Column(name = "promotion_id", nullable = false) private UUID promotionId;
    @Column(name = "coupon_id") private UUID couponId;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "buyer_user_id") private UUID buyerUserId;
    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2) private BigDecimal discountAmount;
    @Column(name = "used_at", nullable = false) private Instant usedAt;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;
    @Column(name = "modified_by") private UUID modifiedBy;

    protected PromotionUsage() {}

    public UUID getId() { return id; }
    public UUID getPromotionId() { return promotionId; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
}
