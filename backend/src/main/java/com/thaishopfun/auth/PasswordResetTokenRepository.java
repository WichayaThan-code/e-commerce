package com.thaishopfun.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE PasswordResetTokenEntity t SET t.usedAt = :now, t.modifiedDateTime = :now
        WHERE t.userId = :userId AND t.usedAt IS NULL
        """)
    int invalidateActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}