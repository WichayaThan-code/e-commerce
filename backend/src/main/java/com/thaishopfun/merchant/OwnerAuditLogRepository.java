package com.thaishopfun.merchant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OwnerAuditLogRepository extends JpaRepository<OwnerAuditLog, UUID> {
}
