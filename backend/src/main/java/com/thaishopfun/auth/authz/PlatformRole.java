package com.thaishopfun.auth.authz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * V1 {@code platform_roles}: platform-level role grants (PLATFORM_ADMIN), separate from shop roles.
 * At most one active (revoked_at IS NULL) row per user (partial unique index uq_platform_roles_user_id_active).
 * This, not the deprecated users.role column, decides who is a platform admin.
 */
@Entity
@Table(name = "platform_roles")
public class PlatformRole {
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 40) private String role;
    @Column(name = "granted_by") private UUID grantedBy;
    @Column(name = "granted_at", nullable = false) private Instant grantedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "grant_reason", length = 500) private String grantReason;
    @Column(name = "revoked_by") private UUID revokedBy;
    @Column(name = "revoke_reason", length = 500) private String revokeReason;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;

    protected PlatformRole() {}

    public static PlatformRole grant(UUID userId, String role, UUID grantedBy, String reason) {
        var grant = new PlatformRole();
        var now = Instant.now();
        grant.id = UUID.randomUUID();
        grant.userId = userId;
        grant.role = role;
        grant.grantedBy = grantedBy;
        grant.grantedAt = now;
        grant.grantReason = reason;
        grant.createdDateTime = now;
        grant.modifiedDateTime = now;
        return grant;
    }

    public void revoke(UUID revokedBy, String reason) {
        if (revokedAt != null) return;
        this.revokedAt = Instant.now();
        this.revokedBy = revokedBy;
        this.revokeReason = reason;
        this.modifiedDateTime = revokedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getRole() { return role; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isActive() { return revokedAt == null; }
}
