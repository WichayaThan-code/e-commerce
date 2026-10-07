package com.thaishopfun.auth.authz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only mapping of V1 {@code auth_roles} (role catalog; rows come from db/seed/seed_reference_data.sql).
 * Mapped so that {@code ddl-auto: validate} checks the table and the H2 test schema contains it.
 */
@Entity
@Immutable
@Table(name = "auth_roles", uniqueConstraints = @UniqueConstraint(name = "uq_auth_roles_code", columnNames = "code"))
public class AuthRole {
    @Id private UUID id;
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 20) private String context;
    @Column(name = "name_th", length = 150) private String nameTh;
    @Column(name = "description_th", length = 500) private String descriptionTh;
    @Column(name = "is_assignable", nullable = false) private boolean assignable;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "policy_version", nullable = false, length = 20) private String policyVersion;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;

    protected AuthRole() {}

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getContext() { return context; }
    public String getNameTh() { return nameTh; }
    public boolean isAssignable() { return assignable; }
    public int getSortOrder() { return sortOrder; }
}
