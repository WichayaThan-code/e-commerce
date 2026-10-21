package com.thaishopfun.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, UUID> {
    long countByPromotionId(UUID promotionId);
    long countByPromotionIdAndBuyerUserId(UUID promotionId, UUID buyerUserId);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(u.discountAmount), 0) from PromotionUsage u where u.promotionId = :promotionId")
    java.math.BigDecimal sumDiscountByPromotionId(@org.springframework.data.repository.query.Param("promotionId") UUID promotionId);
}
