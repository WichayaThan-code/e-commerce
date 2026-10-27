package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderBulkRequestRepository extends JpaRepository<OrderBulkRequest, UUID> {
    Optional<OrderBulkRequest> findByShopIdAndActorUserIdAndIdempotencyKey(UUID shopId, UUID actorUserId, String idempotencyKey);
}
