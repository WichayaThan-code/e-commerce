package com.thaishopfun.order;

/**
 * V1 {@code orders.status} (+ V25 {@code REFUND_REQUESTED}).
 * User-facing aliases: PAID≈PAID_AWAITING_MERCHANT_CONFIRM, PROCESSING≈MERCHANT_CONFIRMED…PACKED, SHIPPED≈DISPATCHED.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID_AWAITING_MERCHANT_CONFIRM,
    MERCHANT_CONFIRMED,
    READY_TO_PACK,
    PACKING,
    PACKED,
    DISPATCHED,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    REFUNDED,
    REFUND_REQUESTED;

    public boolean terminal() {
        return this == CANCELLED || this == REFUNDED;
    }

    public static OrderStatus parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return OrderStatus.valueOf(raw.trim().toUpperCase());
    }
}
