package com.thaishopfun.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Full view of one V1 {@code audit_logs} row (plus the V31 columns reason / ip_address / user_agent). Written by
 * {@link PlatformAuditService} for business-level admin writes and read by the admin audit-log API. The table is
 * append-only: this entity is never updated.
 */
@Entity
@Table(name = "audit_logs")
public class PlatformAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "actor_id") private UUID actorId;
    @Column(name = "actor_type", nullable = false, length = 30) private String actorType;
    @Column(name = "shop_id") private UUID shopId;
    @Column(nullable = false, length = 80) private String action;
    @Column(name = "resource_type", nullable = false, length = 40) private String resourceType;
    @Column(name = "resource_id") private UUID resourceId;
    @Column(length = 80) private String permission;
    @Column(nullable = false, length = 20) private String outcome;
    @Column(name = "reason_code", length = 50) private String reasonCode;
    @Column(columnDefinition = "text") private String reason;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "before_data") private Map<String, Object> beforeData;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "after_data") private Map<String, Object> afterData;
    @Column(name = "request_id", length = 64) private String requestId;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(name = "user_agent", length = 500) private String userAgent;
    @Column(name = "policy_version", length = 20) private String policyVersion;
    @Column(name = "auth_context", length = 20) private String authContext;
    @Column(name = "service_name", length = 80) private String serviceName;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;

    protected PlatformAuditLog() {}

    PlatformAuditLog(PlatformAuditService.Entry entry, String requestId, String ipAddress, String userAgent) {
        var now = Instant.now();
        this.occurredAt = now;
        this.actorId = entry.actorId();
        this.actorType = entry.actorId() == null ? "SYSTEM" : "USER";
        this.shopId = entry.shopId();
        this.action = entry.action();
        this.resourceType = entry.resourceType();
        this.resourceId = entry.resourceId();
        this.permission = entry.permission();
        this.outcome = entry.outcome() == null ? "SUCCESS" : entry.outcome();
        this.reasonCode = entry.reasonCode();
        this.reason = entry.reason();
        this.beforeData = entry.before();
        this.afterData = entry.after();
        this.requestId = requestId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.policyVersion = entry.policyVersion() == null ? "1.0" : entry.policyVersion();
        this.authContext = entry.authContext() == null ? "PLATFORM" : entry.authContext();
        this.createdDateTime = now;
        this.createdBy = entry.actorId();
        this.modifiedDateTime = now;
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getActorId() { return actorId; }
    public String getActorType() { return actorType; }
    public UUID getShopId() { return shopId; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public String getPermission() { return permission; }
    public String getOutcome() { return outcome; }
    public String getReasonCode() { return reasonCode; }
    public String getReason() { return reason; }
    public Map<String, Object> getBeforeData() { return beforeData; }
    public Map<String, Object> getAfterData() { return afterData; }
    public String getRequestId() { return requestId; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getPolicyVersion() { return policyVersion; }
    public String getAuthContext() { return authContext; }
    public String getServiceName() { return serviceName; }
}
