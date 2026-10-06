package com.thaishopfun.auth.authz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Read-only mapping of V1 {@code auth_role_permissions}: the Role x Permission matrix used for enforcement. */
@Entity
@Immutable
@Table(name = "auth_role_permissions",
    uniqueConstraints = @UniqueConstraint(name = "uq_auth_role_permissions_role_id_permission_id", columnNames = {"role_id", "permission_id"}))
public class AuthRolePermission {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "role_id", nullable = false) private AuthRole role;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "permission_id", nullable = false) private AuthPermission permission;
    @Column(name = "policy_version", nullable = false, length = 20) private String policyVersion;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;

    protected AuthRolePermission() {}

    public UUID getId() { return id; }
    public AuthRole getRole() { return role; }
    public AuthPermission getPermission() { return permission; }
}
