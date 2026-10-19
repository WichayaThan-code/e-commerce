package com.thaishopfun.order;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for allowed {@link OrderStatus} transitions (aligned with V1 CHECK + V25 REFUND_REQUESTED).
 * Side effects on payment / fulfillment / stock are applied by {@link OrderTransitionService}, not here.
 */
public final class OrderStateMachine {
    public record Transition(
        OrderStatus to,
        Set<OrderActorType> actors,
        boolean reasonRequired,
        /** When true, carrierCode + trackingNumber are required (ship / DISPATCHED). */
        boolean shippingRequired
    ) {}

    private static final Map<OrderStatus, List<Transition>> TABLE = new EnumMap<>(OrderStatus.class);

    static {
        var M = OrderActorType.MERCHANT;
        var A = OrderActorType.ADMIN;
        var B = OrderActorType.BUYER;
        var S = OrderActorType.SYSTEM;

        // unpaid: pay or cancel (cancel releases the stock reservation)
        allow(OrderStatus.PENDING_PAYMENT,
            t(OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM, actors(M, A, S), false, false),
            t(OrderStatus.CANCELLED, actors(B, M, A, S), true, false));

        // paid, before dispatch: merchant moves it forward or cancels (refund owed); buyer may only request a refund.
        // SYSTEM → MERCHANT_CONFIRMED only when the shop had already accepted the order before payment (auto step).
        allow(OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM,
            t(OrderStatus.MERCHANT_CONFIRMED, actors(M, A, S), false, false),
            t(OrderStatus.CANCELLED, actors(M, A, S), true, false),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false));
        allow(OrderStatus.MERCHANT_CONFIRMED,
            t(OrderStatus.READY_TO_PACK, actors(M, A), false, false),
            t(OrderStatus.PACKING, actors(M, A), false, false),
            t(OrderStatus.PACKED, actors(M, A), false, false),
            t(OrderStatus.DISPATCHED, actors(M, A), false, true),
            t(OrderStatus.CANCELLED, actors(M, A), true, false),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false));
        allow(OrderStatus.READY_TO_PACK,
            t(OrderStatus.PACKING, actors(M, A), false, false),
            t(OrderStatus.PACKED, actors(M, A), false, false),
            t(OrderStatus.DISPATCHED, actors(M, A), false, true),
            t(OrderStatus.CANCELLED, actors(M, A), true, false),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false));
        allow(OrderStatus.PACKING,
            t(OrderStatus.PACKED, actors(M, A), false, false),
            t(OrderStatus.DISPATCHED, actors(M, A), false, true),
            t(OrderStatus.CANCELLED, actors(M, A), true, false),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false));
        allow(OrderStatus.PACKED,
            t(OrderStatus.DISPATCHED, actors(M, A), false, true),
            t(OrderStatus.CANCELLED, actors(M, A), true, false),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false));

        // shipped: no cancel any more, only delivery or refund request
        allow(OrderStatus.DISPATCHED,
            t(OrderStatus.DELIVERED, actors(M, A, S), false, false),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false));
        allow(OrderStatus.DELIVERED,
            t(OrderStatus.COMPLETED, actors(B, A, S), false, false),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false));

        // refund requested: approve → REFUNDED; reject → back to status_before_refund (see REJECT_REFUND_ACTORS)
        allow(OrderStatus.REFUND_REQUESTED,
            t(OrderStatus.REFUNDED, actors(M, A, S), false, false));

        // terminal
        allow(OrderStatus.COMPLETED);
        allow(OrderStatus.CANCELLED);
        allow(OrderStatus.REFUNDED);
    }

    /**
     * Merchant acceptance (orders.merchant_confirmed_at). Allowed once, from any status an un-accepted order can be in.
     * From PAID_AWAITING_MERCHANT_CONFIRM it is the CONFIRM edge (→ MERCHANT_CONFIRMED); elsewhere the status is unchanged
     * (PENDING_PAYMENT: payment confirm then goes straight to MERCHANT_CONFIRMED; REFUND_REQUESTED / CANCELLED / REFUNDED:
     * the shop must see the order to settle a refund it owes).
     */
    public static final Set<OrderActorType> ACCEPT_ACTORS = Set.of(OrderActorType.MERCHANT, OrderActorType.ADMIN);
    private static final Set<OrderStatus> ACCEPT_FROM = EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM,
        OrderStatus.REFUND_REQUESTED, OrderStatus.CANCELLED, OrderStatus.REFUNDED);

    public static boolean canAccept(OrderStatus from, OrderActorType actor) {
        return from != null && ACCEPT_FROM.contains(from) && ACCEPT_ACTORS.contains(actor);
    }

    /** Status an accept moves the order to, or null when acceptance does not change the status. */
    public static OrderStatus acceptTarget(OrderStatus from) {
        return from == OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM ? OrderStatus.MERCHANT_CONFIRMED : null;
    }

    /** Who may reject a refund request (REFUND_REQUESTED → status_before_refund). Reason required. */
    public static final Set<OrderActorType> REJECT_REFUND_ACTORS = Set.of(OrderActorType.MERCHANT, OrderActorType.ADMIN);

    /** Statuses a refund request can return to on reject (= statuses that allow → REFUND_REQUESTED). */
    public static boolean canReturnTo(OrderStatus previous) {
        return previous != null && allowedTargets(previous).contains(OrderStatus.REFUND_REQUESTED);
    }

    private OrderStateMachine() {}

    private static void allow(OrderStatus from, Transition... transitions) {
        TABLE.put(from, List.of(transitions));
    }

    private static Transition t(OrderStatus to, Set<OrderActorType> actors, boolean reasonRequired, boolean shippingRequired) {
        return new Transition(to, actors, reasonRequired, shippingRequired);
    }

    private static Set<OrderActorType> actors(OrderActorType... types) {
        return EnumSet.copyOf(List.of(types));
    }

    public static List<Transition> transitionsFrom(OrderStatus from) {
        return TABLE.getOrDefault(from, List.of());
    }

    public static Set<OrderStatus> allowedTargets(OrderStatus from) {
        var set = new LinkedHashSet<OrderStatus>();
        for (var t : transitionsFrom(from)) set.add(t.to());
        return Collections.unmodifiableSet(set);
    }

    /** Targets this actor may move the order to from {@code from} (reject-refund target included for REFUND_REQUESTED). */
    public static Set<OrderStatus> allowedTargets(OrderStatus from, OrderActorType actor, OrderStatus statusBeforeRefund) {
        var set = new LinkedHashSet<OrderStatus>();
        for (var t : transitionsFrom(from)) if (t.actors().contains(actor)) set.add(t.to());
        if (from == OrderStatus.REFUND_REQUESTED && REJECT_REFUND_ACTORS.contains(actor) && canReturnTo(statusBeforeRefund)) {
            set.add(statusBeforeRefund);
        }
        return Collections.unmodifiableSet(set);
    }

    /** The edge from → to for this actor, or 409 INVALID_STATUS_TRANSITION {from, to, allowed[]}. */
    public static Transition require(OrderStatus from, OrderStatus to, OrderActorType actor) {
        for (var t : transitionsFrom(from)) {
            if (t.to() == to && t.actors().contains(actor)) return t;
        }
        throw new InvalidStatusTransitionException(from, to, allowedTargets(from, actor, null), "INVALID_STATUS_TRANSITION");
    }
}
