package com.thaishopfun.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID> {
    boolean existsByOrderItemId(UUID orderItemId);
}
