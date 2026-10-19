package com.thaishopfun.order;

import com.thaishopfun.cart.PromotionUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderPromotionUsageRepository extends JpaRepository<PromotionUsage, UUID> {
    List<PromotionUsage> findByOrderId(UUID orderId);

    @Modifying(flushAutomatically = true)
    @Query("delete from PromotionUsage u where u.orderId = :orderId")
    int deleteByOrderId(@Param("orderId") UUID orderId);
}
