package com.thaishopfun.merchant.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping of V1 {@code shop_invitations} so Hibernate create-drop (test profile) creates the table.
 * Runtime writes go through {@link ShopTeamService} JDBC.
 */
@Entity
@Table(
    name = "shop_invitations",
    uniqueConstraints = @UniqueConstraint(name = "uq_shop_invitations_token_hash", columnNames = "token_hash")
)
public class ShopInvitationEntity {
    @Id
    private UUID id;
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;
    @Column(nullable = false, length = 255)
    private String email;
    @Column(nullable = false, length = 40)
    private String role;
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @Column(nullable = false, length = 30)
    private String status = "PENDING";
    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "accepted_at")
    private Instant acceptedAt;
    @Column(name = "accepted_user_id")
    private UUID acceptedUserId;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "revoked_by")
    private UUID revokedBy;
    @Column(name = "created_date_time", nullable = false)
    private Instant createdDateTime;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "modified_date_time", nullable = false)
    private Instant modifiedDateTime;
    @Column(name = "modified_by")
    private UUID modifiedBy;

    protected ShopInvitationEntity() {}
}
