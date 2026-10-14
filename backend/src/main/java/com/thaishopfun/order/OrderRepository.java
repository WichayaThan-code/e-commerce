package com.thaishopfun.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
    /** Row lock (SELECT … FOR UPDATE) for status transitions; @Version still guards stale writes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") UUID id);

    boolean existsByOrderNumber(String orderNumber);

    @Query("select o from Order o where o.buyerUserId = :buyer and o.idempotencyKey like :prefix order by o.placedAt asc, o.orderNumber asc")
    List<Order> findCheckoutReplay(@Param("buyer") UUID buyer, @Param("prefix") String prefix);

    @Query("select o.id from Order o where o.status = com.thaishopfun.order.OrderStatus.PENDING_PAYMENT and o.placedAt < :before")
    List<UUID> findUnpaidPlacedBefore(@Param("before") java.time.Instant before);
}
