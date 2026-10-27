package com.thaishopfun.order;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class OrderTagAssignmentId implements Serializable {
    private UUID orderId;
    private UUID tagId;

    public OrderTagAssignmentId() {}

    public OrderTagAssignmentId(UUID orderId, UUID tagId) {
        this.orderId = orderId;
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderTagAssignmentId that)) return false;
        return Objects.equals(orderId, that.orderId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, tagId);
    }
}
