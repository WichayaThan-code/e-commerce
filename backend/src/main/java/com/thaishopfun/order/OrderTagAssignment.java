package com.thaishopfun.order;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_tag_assignments")
@IdClass(OrderTagAssignmentId.class)
public class OrderTagAssignment {
    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Id
    @Column(name = "tag_id", nullable = false)
    private UUID tagId;
    @Column(name = "created_date_time", nullable = false, updatable = false)
    private Instant createdDateTime;
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected OrderTagAssignment() {}

    public OrderTagAssignment(UUID orderId, UUID tagId, UUID createdBy) {
        this.orderId = orderId;
        this.tagId = tagId;
        this.createdBy = createdBy;
        this.createdDateTime = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdDateTime == null) createdDateTime = Instant.now();
    }

    public UUID getOrderId() { return orderId; }
    public UUID getTagId() { return tagId; }
    public Instant getCreatedDateTime() { return createdDateTime; }
    public UUID getCreatedBy() { return createdBy; }
}
