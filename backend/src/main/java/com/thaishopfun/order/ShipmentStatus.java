package com.thaishopfun.order;

import java.util.Locale;

/** V1 ck_shipments_status / ck_shipment_tracking_events_normalized_status values. */
public enum ShipmentStatus {
    READY_TO_PACK, PACKING, PACKED, DISPATCHED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED, CANCELLED, RETURNED;

    /** Accepts the V1 names plus the short alias FAILED (= DELIVERY_FAILED). */
    public static ShipmentStatus parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("status is null");
        var value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (value.equals("FAILED")) return DELIVERY_FAILED;
        return valueOf(value);
    }

    public boolean terminal() {
        return this == DELIVERED || this == CANCELLED || this == RETURNED;
    }

    /** On its way (after dispatch, before an outcome). */
    public boolean inFlight() {
        return this == DISPATCHED || this == IN_TRANSIT || this == OUT_FOR_DELIVERY || this == DELIVERY_FAILED;
    }
}
