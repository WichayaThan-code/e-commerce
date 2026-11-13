package com.thaishopfun.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID> {
    boolean existsByOrderItemId(UUID orderItemId);

    @Query("select r.orderItemId from ProductReview r where r.orderId = :orderId and r.buyerUserId = :buyerId and r.deletedAt is null and r.orderItemId is not null")
    List<UUID> findReviewedOrderItemIds(@Param("orderId") UUID orderId, @Param("buyerId") UUID buyerId);
}
