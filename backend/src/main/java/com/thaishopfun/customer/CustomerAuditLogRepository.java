package com.thaishopfun.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerAuditLogRepository extends JpaRepository<CustomerAuditLog, Long> {
    List<CustomerAuditLog> findByResourceTypeAndResourceIdOrderByIdAsc(String resourceType, UUID resourceId);
}
