package com.thaishopfun.promotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, UUID> {
    boolean existsByPromotionId(UUID promotionId);

    /** Rows of [promotionId, count, sum(discount_amount)]. */
    @Query("select u.promotionId, count(u), coalesce(sum(u.discountAmount), 0) from PromotionUsage u where u.promotionId in :ids group by u.promotionId")
    List<Object[]> totalsByPromotion(@Param("ids") Collection<UUID> ids);
}
