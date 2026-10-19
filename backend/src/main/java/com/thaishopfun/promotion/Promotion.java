package com.thaishopfun.promotion;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Shop-wide promotion (V1 table promotions + V27 is_public). One coupon code per promotion through the merchant API. */
@Entity
@Table(name = "promotions")
public class Promotion extends AuditedEntity {
    @Id private UUID id;
    /** shops.id (uuid). Plain column (no association), like ShopCustomer, so shop clean-ups in tests are unaffected. */
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(nullable = false, length = 150) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "promo_type", nullable = false, length = 30) private PromotionType type;
    /** Backtick-quoted: VALUE is a reserved word in H2 (tests); Hibernate quotes it per dialect ("value" on PostgreSQL). */
    @Column(name = "`value`", nullable = false, precision = 18, scale = 2) private BigDecimal value;
    @Column(name = "min_order_amount", precision = 18, scale = 2) private BigDecimal minOrderAmount;
    @Column(name = "max_discount_amount", precision = 18, scale = 2) private BigDecimal maxDiscountAmount;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at") private Instant endsAt;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "usage_limit_total") private Integer usageLimitTotal;
    @Column(name = "usage_limit_per_buyer") private Integer usageLimitPerBuyer;
    @Column(name = "is_public", nullable = false) private boolean publicVisible = true;

    protected Promotion() {}

    public Promotion(UUID shopId) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
    }

    public PromotionStatus status(Instant now) { return PromotionStatus.of(active, startsAt, endsAt, now); }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public PromotionType getType() { return type; }
    public void setType(PromotionType type) { this.type = type; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public BigDecimal getMaxDiscountAmount() { return maxDiscountAmount; }
    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) { this.maxDiscountAmount = maxDiscountAmount; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Integer getUsageLimitTotal() { return usageLimitTotal; }
    public void setUsageLimitTotal(Integer usageLimitTotal) { this.usageLimitTotal = usageLimitTotal; }
    public Integer getUsageLimitPerBuyer() { return usageLimitPerBuyer; }
    public void setUsageLimitPerBuyer(Integer usageLimitPerBuyer) { this.usageLimitPerBuyer = usageLimitPerBuyer; }
    public boolean isPublicVisible() { return publicVisible; }
    public void setPublicVisible(boolean publicVisible) { this.publicVisible = publicVisible; }
}
