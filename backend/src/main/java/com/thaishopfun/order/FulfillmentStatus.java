package com.thaishopfun.order;

/** V1 {@code orders.fulfillment_status} / ck_orders_fulfillment_status. */
public enum FulfillmentStatus {
    UNFULFILLED, PARTIAL, FULFILLED, RETURNED;

    public static FulfillmentStatus parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return FulfillmentStatus.valueOf(raw.trim().toUpperCase());
    }
}
