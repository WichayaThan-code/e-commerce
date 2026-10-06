package com.thaishopfun.auth.authz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformRoleRepository extends JpaRepository<PlatformRole, UUID> {
    boolean existsByUserIdAndRevokedAtIsNull(UUID userId);
    Optional<PlatformRole> findFirstByUserIdAndRevokedAtIsNull(UUID userId);
}
