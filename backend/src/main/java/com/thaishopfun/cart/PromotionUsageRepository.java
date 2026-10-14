package com.thaishopfun.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, UUID> {
    long countByPromotionId(UUID promotionId);
    long countByPromotionIdAndBuyerUserId(UUID promotionId, UUID buyerUserId);
}
