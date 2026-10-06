package com.thaishopfun.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE RefreshToken r SET r.revokedAt = :now, r.modifiedDateTime = :now
        WHERE r.user.id = :userId AND r.revokedAt IS NULL
        """)
    int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}