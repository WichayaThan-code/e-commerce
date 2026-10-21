package com.thaishopfun.promotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Repository("merchantCouponRepository")
public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    List<Coupon> findByPromotionIdInOrderByCreatedDateTimeAsc(Collection<UUID> promotionIds);
    List<Coupon> findByPromotionIdOrderByCreatedDateTimeAsc(UUID promotionId);
    void deleteByPromotionId(UUID promotionId);

    @Query("select count(c) > 0 from MerchantCoupon c where c.shopId = :shopId and lower(c.code) = lower(:code) and c.promotionId <> :excludePromotionId")
    boolean codeTaken(@Param("shopId") UUID shopId, @Param("code") String code, @Param("excludePromotionId") UUID excludePromotionId);
}
