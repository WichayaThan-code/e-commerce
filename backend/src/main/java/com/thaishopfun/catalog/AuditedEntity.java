package com.thaishopfun.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class AuditedEntity {
    @Column(name = "created_date_time", nullable = false, updatable = false)
    private Instant createdDateTime;
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
    @Column(name = "modified_date_time", nullable = false)
    private Instant modifiedDateTime;
    @Column(name = "modified_by")
    private UUID modifiedBy;

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        if (createdDateTime == null) createdDateTime = now;
        modifiedDateTime = now;
    }

    @PreUpdate
    void onUpdate() {
        modifiedDateTime = Instant.now();
    }

    public void stamp(UUID userId) {
        if (createdBy == null) createdBy = userId;
        modifiedBy = userId;
    }

    public Instant getCreatedDateTime() { return createdDateTime; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getModifiedDateTime() { return modifiedDateTime; }
    public UUID getModifiedBy() { return modifiedBy; }
}
