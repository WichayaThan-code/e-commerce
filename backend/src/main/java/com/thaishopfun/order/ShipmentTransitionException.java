package com.thaishopfun.order;

import java.util.List;

/** 409: shipment status change not allowed (from → to), with the statuses that are allowed from here. */
public class ShipmentTransitionException extends RuntimeException {
    private final String code;
    private final ShipmentStatus from;
    private final ShipmentStatus to;
    private final List<String> allowed;

    public ShipmentTransitionException(String code, String message, ShipmentStatus from, ShipmentStatus to, List<String> allowed) {
        super(message);
        this.code = code;
        this.from = from;
        this.to = to;
        this.allowed = allowed == null ? List.of() : allowed;
    }

    public String getCode() { return code; }
    public ShipmentStatus getFrom() { return from; }
    public ShipmentStatus getTo() { return to; }
    public List<String> getAllowed() { return allowed; }
}
