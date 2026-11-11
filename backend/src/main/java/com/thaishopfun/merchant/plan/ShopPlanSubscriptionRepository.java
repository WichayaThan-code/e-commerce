package com.thaishopfun.merchant.plan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopPlanSubscriptionRepository extends JpaRepository<ShopPlanSubscription, UUID> {
    List<ShopPlanSubscription> findByShopIdOrderByCreatedAtDesc(UUID shopId);

    Optional<ShopPlanSubscription> findByIdAndShopId(UUID id, UUID shopId);

    @Query("""
        select s from ShopPlanSubscription s
         where s.shopId = :shopId and s.status in :statuses
         order by s.createdAt desc
        """)
    List<ShopPlanSubscription> findOpen(@Param("shopId") UUID shopId, @Param("statuses") Collection<String> statuses);

    List<ShopPlanSubscription> findAllByOrderByCreatedAtDesc();

    List<ShopPlanSubscription> findByStatusOrderByCreatedAtDesc(String status);
}
