package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderFinancialSnapshotRepository extends JpaRepository<OrderFinancialSnapshot, UUID> {
    Optional<OrderFinancialSnapshot> findByOrderId(UUID orderId);
}
