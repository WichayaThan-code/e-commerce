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

    /**
     * Race-safe rotation: only one caller can flip revoked_at from NULL for a given token.
     * Returns 1 for the winner and 0 for anyone who lost the race (token already revoked/rotated).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE RefreshToken r SET r.revokedAt = :now, r.modifiedDateTime = :now
        WHERE r.id = :id AND r.revokedAt IS NULL
        """)
    int revokeIfActive(@Param("id") UUID id, @Param("now") Instant now);
}
