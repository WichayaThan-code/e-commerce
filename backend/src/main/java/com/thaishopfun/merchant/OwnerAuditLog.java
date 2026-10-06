package com.thaishopfun.merchant;

import com.thaishopfun.auth.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Sensitive owner-field changes (citizen id / bank account of an owner that already has an approved shop).
 * Stored in the V1 append-only business audit table {@code audit_logs} instead of a separate table:
 * actor = the user, resource = OWNER/{owner id}, reason_code = changed field.
 */
@Entity
@Table(name = "audit_logs")
public class OwnerAuditLog {
    public static final String ACTION = "OWNER_SENSITIVE_FIELD_CHANGED";
    public static final String RESOURCE_TYPE = "OWNER";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "actor_id") private UUID actorId;
    @Column(name = "actor_type", nullable = false, length = 30) private String actorType;
    @Column(nullable = false, length = 80) private String action;
    @Column(name = "resource_type", nullable = false, length = 40) private String resourceType;
    @Column(name = "resource_id") private UUID resourceId;
    @Column(nullable = false, length = 20) private String outcome;
    @Column(name = "reason_code", length = 50) private String fieldName;
    @Column(name = "auth_context", length = 20) private String authContext;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;

    protected OwnerAuditLog() {}

    public OwnerAuditLog(Owner owner, UserAccount user, String fieldName) {
        var now = Instant.now();
        this.occurredAt = now;
        this.actorId = user.getId();
        this.actorType = "USER";
        this.action = ACTION;
        this.resourceType = RESOURCE_TYPE;
        this.resourceId = owner.getId();
        this.outcome = "SUCCESS";
        this.fieldName = fieldName;
        this.authContext = "ACCOUNT";
        this.createdDateTime = now;
        this.createdBy = user.getId();
        this.modifiedDateTime = now;
    }

    public Long getId() { return id; }
    public UUID getResourceId() { return resourceId; }
    public String getFieldName() { return fieldName; }
}
