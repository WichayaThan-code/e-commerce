package com.thaishopfun.customer;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Customer events in the V1 append-only audit table audit_logs (resource CUSTOMER, shop-scoped). */
@Entity(name = "CustomerAuditLog")
@Table(name = "audit_logs")
public class CustomerAuditLog {
    public static final String RESOURCE_TYPE = "CUSTOMER";

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
    @Column(name = "auth_context", length = 20) private String authContext;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "after_data") private Map<String, Object> afterData;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;

    protected CustomerAuditLog() {}

    public CustomerAuditLog(UUID actorId, UUID shopId, String action, String permission, UUID customerId, Map<String, Object> data) {
        var now = Instant.now();
        this.occurredAt = now;
        this.actorId = actorId;
        this.actorType = "USER";
        this.shopId = shopId;
        this.action = action;
        this.resourceType = RESOURCE_TYPE;
        this.resourceId = customerId;
        this.permission = permission;
        this.outcome = "SUCCESS";
        this.authContext = "SHOP";
        this.afterData = data;
        this.createdDateTime = now;
        this.createdBy = actorId;
        this.modifiedDateTime = now;
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public UUID getResourceId() { return resourceId; }
    public Map<String, Object> getAfterData() { return afterData; }
}
