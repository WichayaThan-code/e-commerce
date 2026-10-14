package com.thaishopfun.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    Optional<Coupon> findByShopIdAndCode(UUID shopId, String code);
    Optional<Coupon> findByIdAndShopId(UUID id, UUID shopId);
    List<Coupon> findByShopIdOrderByCreatedDateTimeDesc(UUID shopId);
    boolean existsByShopIdAndCodeAndIdNot(UUID shopId, String code, UUID id);
}
