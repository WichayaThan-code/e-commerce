package com.thaishopfun.order;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shipment-level status rules (V1 ck_shipments_status). The order state machine stays the single source of truth for
 * the order: pack / dispatch / cancel and DELIVERED / RETURNED (COD) are reached through OrderTransitionService, the
 * in-flight statuses (IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERY_FAILED) only move the shipment.
 * Actors: MERCHANT, ADMIN, SYSTEM (carrier updates). Buyers never change a shipment.
 */
public final class ShipmentStateMachine {
    /** Statuses the shipment API accepts as a target (the others come from order transitions). */
    public static final Set<ShipmentStatus> API_TARGETS = EnumSet.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY,
        ShipmentStatus.DELIVERED, ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURNED);
    /** Changes that need a reason. */
    public static final Set<ShipmentStatus> REASON_REQUIRED = EnumSet.of(ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURNED);
    static final Set<OrderActorType> ACTORS = EnumSet.of(OrderActorType.MERCHANT, OrderActorType.ADMIN, OrderActorType.SYSTEM);

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> EDGES = new EnumMap<>(ShipmentStatus.class);

    static {
        var inFlight = EnumSet.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED,
            ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURNED);
        EDGES.put(ShipmentStatus.DISPATCHED, inFlight);
        EDGES.put(ShipmentStatus.IN_TRANSIT, EnumSet.of(ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED, ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURNED));
        EDGES.put(ShipmentStatus.OUT_FOR_DELIVERY, EnumSet.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED, ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURNED));
        EDGES.put(ShipmentStatus.DELIVERY_FAILED, EnumSet.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED, ShipmentStatus.RETURNED));
        for (var s : List.of(ShipmentStatus.READY_TO_PACK, ShipmentStatus.PACKING, ShipmentStatus.PACKED, ShipmentStatus.DELIVERED,
            ShipmentStatus.CANCELLED, ShipmentStatus.RETURNED)) {
            EDGES.put(s, EnumSet.noneOf(ShipmentStatus.class)); // pre-dispatch: order endpoints; terminal: nothing
        }
    }

    private ShipmentStateMachine() {}

    public static boolean allowed(ShipmentStatus from, ShipmentStatus to, OrderActorType actor) {
        return actor != null && ACTORS.contains(actor) && EDGES.getOrDefault(from, Set.of()).contains(to);
    }

    public static List<String> allowedTargets(ShipmentStatus from, OrderActorType actor) {
        if (actor == null || !ACTORS.contains(actor)) return List.of();
        return EDGES.getOrDefault(from, Set.of()).stream().map(Enum::name).toList();
    }

    public static void require(ShipmentStatus from, ShipmentStatus to, OrderActorType actor) {
        if (!allowed(from, to, actor)) {
            throw new ShipmentTransitionException("SHIPMENT_INVALID_TRANSITION",
                "เปลี่ยนสถานะการจัดส่งจาก " + from + " เป็น " + to + " ไม่ได้", from, to, allowedTargets(from, actor));
        }
    }
}
