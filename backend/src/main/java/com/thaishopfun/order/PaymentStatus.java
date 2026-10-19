package com.thaishopfun.order;

/** V1 {@code orders.payment_status} / ck_orders_payment_status. */
public enum PaymentStatus {
    UNPAID, PENDING, PAID, FAILED, REFUNDED, PARTIALLY_REFUNDED;

    public static PaymentStatus parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return PaymentStatus.valueOf(raw.trim().toUpperCase());
    }
}
