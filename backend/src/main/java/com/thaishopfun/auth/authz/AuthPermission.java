package com.thaishopfun.auth.authz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Read-only mapping of V1 {@code auth_permissions} (permission catalog, seeded by seed_reference_data.sql). */
@Entity
@Immutable
@Table(name = "auth_permissions", uniqueConstraints = @UniqueConstraint(name = "uq_auth_permissions_code", columnNames = "code"))
public class AuthPermission {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 40) private String module;
    @Column(nullable = false, length = 20) private String scope;
    @Column(name = "description_th", length = 500) private String descriptionTh;
    @Column(name = "doc_section", length = 20) private String docSection;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "policy_version", nullable = false, length = 20) private String policyVersion;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;

    protected AuthPermission() {}

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getModule() { return module; }
    public String getScope() { return scope; }
    public boolean isActive() { return active; }
}
