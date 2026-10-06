package com.thaishopfun.merchant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OwnerAuditLogRepository extends JpaRepository<OwnerAuditLog, Long> {
    List<OwnerAuditLog> findByResourceTypeAndResourceId(String resourceType, UUID resourceId);
}
