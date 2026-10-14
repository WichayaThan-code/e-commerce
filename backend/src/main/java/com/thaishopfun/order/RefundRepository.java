package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByOrderId(UUID orderId);
}
