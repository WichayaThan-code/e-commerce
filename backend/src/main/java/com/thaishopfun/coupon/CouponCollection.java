package com.thaishopfun.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** A buyer's collected shop coupon (V31 coupon_collections). Immutable once created. */
@Entity
@Table(name = "coupon_collections",
    uniqueConstraints = @UniqueConstraint(name = "uq_coupon_collections_coupon_user", columnNames = {"coupon_id", "user_id"}))
public class CouponCollection {
    @Id private UUID id;
    @Column(name = "coupon_id", nullable = false) private UUID couponId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "collected_at", nullable = false) private Instant collectedAt;

    protected CouponCollection() {}

    public CouponCollection(UUID couponId, UUID userId, Instant collectedAt) {
        this.id = UUID.randomUUID();
        this.couponId = couponId;
        this.userId = userId;
        this.collectedAt = collectedAt;
    }

    public UUID getId() { return id; }
    public UUID getCouponId() { return couponId; }
    public UUID getUserId() { return userId; }
    public Instant getCollectedAt() { return collectedAt; }
}
