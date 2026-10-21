package com.thaishopfun.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository("couponCollectionRepository")
public interface CouponCollectionRepository extends JpaRepository<CouponCollection, UUID> {
    boolean existsByCouponIdAndUserId(UUID couponId, UUID userId);

    Optional<CouponCollection> findByCouponIdAndUserId(UUID couponId, UUID userId);
}
