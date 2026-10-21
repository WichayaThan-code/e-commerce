package com.thaishopfun.order;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for allowed {@link OrderStatus} transitions (aligned with V1 CHECK + V25 REFUND_REQUESTED),
 * per payment method (V29 COD). Side effects on payment / fulfillment / stock are applied by
 * {@link OrderTransitionService}, not here.
 * <p>BANK_TRANSFER (payment-first): PENDING_PAYMENT → PAID_AWAITING_MERCHANT_CONFIRM → MERCHANT_CONFIRMED → … → COMPLETED.
 * <p>COD: PENDING_PAYMENT (awaiting merchant acceptance, nothing to pay yet) → MERCHANT_CONFIRMED (accept) → pack / ship →
 * DELIVERED → cash collected (payment CAPTURED, status unchanged) → COMPLETED; a failed delivery / return goes
 * DISPATCHED → CANCELLED (reason, stock back). Refunds exist only after the cash was collected.
 */
public final class OrderStateMachine {
    /** What the edge needs to know about the order besides its status. */
    public record Facts(PaymentMethod method, boolean codCollected) {
        public static final Facts PREPAID = new Facts(PaymentMethod.BANK_TRANSFER, false);

        public static Facts of(Order order) {
            var method = order.getPaymentMethod() == null ? PaymentMethod.BANK_TRANSFER : order.getPaymentMethod();
            var paid = order.getPaymentStatus();
            boolean collected = method == PaymentMethod.COD && paid != null && paid != PaymentStatus.UNPAID
                && paid != PaymentStatus.PENDING && paid != PaymentStatus.FAILED;
            return new Facts(method, collected);
        }

        boolean cod() { return method == PaymentMethod.COD; }
    }

    /** Which orders an edge applies to; a failed condition answers 409 with {@link #failureCode}. */
    public enum Applies {
        ANY,
        /** payment-first orders only (mark paid / slip confirm) */
        PREPAID,
        /** COD orders only */
        COD,
        /** COD whose cash has not been collected (failed delivery) */
        COD_UNCOLLECTED,
        /** prepaid, or COD after the cash was collected (complete / refund request) */
        PAID;

        /** Error code when the edge exists for the actor but not for this order; null = plain INVALID_STATUS_TRANSITION. */
        public String failureCode(Facts facts) {
            return switch (this) {
                case PREPAID -> "COD_ORDER";
                case COD_UNCOLLECTED -> facts.cod() ? "COD_ALREADY_COLLECTED" : null;
                case PAID -> "COD_NOT_COLLECTED";
                default -> null;
            };
        }

        public boolean test(Facts facts) {
            return switch (this) {
                case ANY -> true;
                case PREPAID -> !facts.cod();
                case COD -> facts.cod();
                case COD_UNCOLLECTED -> facts.cod() && !facts.codCollected();
                case PAID -> !facts.cod() || facts.codCollected();
            };
        }
    }

    public record Transition(
        OrderStatus to,
        Set<OrderActorType> actors,
        boolean reasonRequired,
        /** When true, carrierCode + trackingNumber are required (ship / DISPATCHED). */
        boolean shippingRequired,
        Applies applies
    ) {}

    private static final Map<OrderStatus, List<Transition>> TABLE = new EnumMap<>(OrderStatus.class);

    static {
        var M = OrderActorType.MERCHANT;
        var A = OrderActorType.ADMIN;
        var B = OrderActorType.BUYER;
        var S = OrderActorType.SYSTEM;
        var PAID = Applies.PAID;

        // unpaid: pay or cancel (cancel releases the stock reservation). COD: the shop accepts straight to MERCHANT_CONFIRMED.
        allow(OrderStatus.PENDING_PAYMENT,
            t(OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM, actors(M, A, S), false, false, Applies.PREPAID),
            t(OrderStatus.MERCHANT_CONFIRMED, actors(M, A), false, false, Applies.COD),
            t(OrderStatus.CANCELLED, actors(B, M, A, S), true, false, Applies.ANY));

        // paid, before dispatch: merchant moves it forward or cancels (refund owed); buyer may only request a refund.
        // SYSTEM → MERCHANT_CONFIRMED only when the shop had already accepted the order before payment (auto step).
        allow(OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM,
            t(OrderStatus.MERCHANT_CONFIRMED, actors(M, A, S), false, false, Applies.ANY),
            t(OrderStatus.CANCELLED, actors(M, A, S), true, false, Applies.ANY),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false, PAID));
        allow(OrderStatus.MERCHANT_CONFIRMED,
            t(OrderStatus.READY_TO_PACK, actors(M, A), false, false, Applies.ANY),
            t(OrderStatus.PACKING, actors(M, A), false, false, Applies.ANY),
            t(OrderStatus.PACKED, actors(M, A), false, false, Applies.ANY),
            t(OrderStatus.DISPATCHED, actors(M, A), false, true, Applies.ANY),
            t(OrderStatus.CANCELLED, actors(M, A), true, false, Applies.ANY),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false, PAID));
        allow(OrderStatus.READY_TO_PACK,
            t(OrderStatus.PACKING, actors(M, A), false, false, Applies.ANY),
            t(OrderStatus.PACKED, actors(M, A), false, false, Applies.ANY),
            t(OrderStatus.DISPATCHED, actors(M, A), false, true, Applies.ANY),
            t(OrderStatus.CANCELLED, actors(M, A), true, false, Applies.ANY),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false, PAID));
        allow(OrderStatus.PACKING,
            t(OrderStatus.PACKED, actors(M, A), false, false, Applies.ANY),
            t(OrderStatus.DISPATCHED, actors(M, A), false, true, Applies.ANY),
            t(OrderStatus.CANCELLED, actors(M, A), true, false, Applies.ANY),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false, PAID));
        allow(OrderStatus.PACKED,
            t(OrderStatus.DISPATCHED, actors(M, A), false, true, Applies.ANY),
            t(OrderStatus.CANCELLED, actors(M, A), true, false, Applies.ANY),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false, PAID));

        // shipped: no cancel any more (except a COD parcel that could not be delivered / came back unpaid)
        allow(OrderStatus.DISPATCHED,
            t(OrderStatus.DELIVERED, actors(M, A, S), false, false, Applies.ANY),
            t(OrderStatus.CANCELLED, actors(M, A, S), true, false, Applies.COD_UNCOLLECTED),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false, PAID));
        allow(OrderStatus.DELIVERED,
            t(OrderStatus.COMPLETED, actors(B, A, S), false, false, PAID),
            t(OrderStatus.REFUND_REQUESTED, actors(B, A), true, false, PAID));

        // refund requested: approve → REFUNDED; reject → back to status_before_refund (see REJECT_REFUND_ACTORS)
        allow(OrderStatus.REFUND_REQUESTED,
            t(OrderStatus.REFUNDED, actors(M, A, S), false, false, Applies.ANY));

        // terminal
        allow(OrderStatus.COMPLETED);
        allow(OrderStatus.CANCELLED);
        allow(OrderStatus.REFUNDED);
    }

    /**
     * Merchant acceptance (orders.merchant_confirmed_at). Allowed once, from any status an un-accepted order can be in.
     * From PAID_AWAITING_MERCHANT_CONFIRM (and PENDING_PAYMENT of a COD order) it is the CONFIRM edge (→ MERCHANT_CONFIRMED);
     * elsewhere the status is unchanged (PENDING_PAYMENT prepaid: payment confirm then goes straight to MERCHANT_CONFIRMED;
     * REFUND_REQUESTED / CANCELLED / REFUNDED: the shop must see the order to settle a refund it owes).
     */
    public static final Set<OrderActorType> ACCEPT_ACTORS = Set.of(OrderActorType.MERCHANT, OrderActorType.ADMIN);
    private static final Set<OrderStatus> ACCEPT_FROM = EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM,
        OrderStatus.REFUND_REQUESTED, OrderStatus.CANCELLED, OrderStatus.REFUNDED);

    public static boolean canAccept(OrderStatus from, OrderActorType actor) {
        return from != null && ACCEPT_FROM.contains(from) && ACCEPT_ACTORS.contains(actor);
    }

    /** Status an accept moves a prepaid order to, or null when acceptance does not change the status. */
    public static OrderStatus acceptTarget(OrderStatus from) {
        return acceptTarget(from, Facts.PREPAID);
    }

    public static OrderStatus acceptTarget(OrderStatus from, Facts facts) {
        if (from == OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM) return OrderStatus.MERCHANT_CONFIRMED;
        if (from == OrderStatus.PENDING_PAYMENT && facts.cod()) return OrderStatus.MERCHANT_CONFIRMED;
        return null;
    }

    /**
     * COD cash collected (payment CAPTURED; order status unchanged): once delivered, by the shop, an admin, or the
     * system (carrier COD report). Not a status edge, so it lives here next to the table.
     */
    public static final Set<OrderActorType> COD_COLLECT_ACTORS = Set.of(OrderActorType.MERCHANT, OrderActorType.ADMIN, OrderActorType.SYSTEM);
    public static final Set<OrderStatus> COD_COLLECT_FROM = EnumSet.of(OrderStatus.DELIVERED);

    public static boolean canCollectCod(OrderStatus from, OrderActorType actor, Facts facts) {
        return facts.cod() && !facts.codCollected() && COD_COLLECT_FROM.contains(from) && COD_COLLECT_ACTORS.contains(actor);
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

    private static Transition t(OrderStatus to, Set<OrderActorType> actors, boolean reasonRequired, boolean shippingRequired, Applies applies) {
        return new Transition(to, actors, reasonRequired, shippingRequired, applies);
    }

    private static Set<OrderActorType> actors(OrderActorType... types) {
        return EnumSet.copyOf(List.of(types));
    }

    /** Edges of a prepaid (BANK_TRANSFER) order. */
    public static List<Transition> transitionsFrom(OrderStatus from) {
        return transitionsFrom(from, Facts.PREPAID);
    }

    /** Edges that apply to an order with these facts. */
    public static List<Transition> transitionsFrom(OrderStatus from, Facts facts) {
        return TABLE.getOrDefault(from, List.of()).stream().filter(t -> t.applies().test(facts)).toList();
    }

    public static Set<OrderStatus> allowedTargets(OrderStatus from) {
        return allowedTargets(from, Facts.PREPAID);
    }

    public static Set<OrderStatus> allowedTargets(OrderStatus from, Facts facts) {
        var set = new LinkedHashSet<OrderStatus>();
        for (var t : transitionsFrom(from, facts)) set.add(t.to());
        return Collections.unmodifiableSet(set);
    }

    public static Set<OrderStatus> allowedTargets(OrderStatus from, OrderActorType actor, OrderStatus statusBeforeRefund) {
        return allowedTargets(from, actor, statusBeforeRefund, Facts.PREPAID);
    }

    /** Targets this actor may move the order to from {@code from} (reject-refund target included for REFUND_REQUESTED). */
    public static Set<OrderStatus> allowedTargets(OrderStatus from, OrderActorType actor, OrderStatus statusBeforeRefund, Facts facts) {
        var set = new LinkedHashSet<OrderStatus>();
        for (var t : transitionsFrom(from, facts)) if (t.actors().contains(actor)) set.add(t.to());
        if (from == OrderStatus.REFUND_REQUESTED && REJECT_REFUND_ACTORS.contains(actor) && canReturnTo(statusBeforeRefund)) {
            set.add(statusBeforeRefund);
        }
        return Collections.unmodifiableSet(set);
    }

    public static Transition require(OrderStatus from, OrderStatus to, OrderActorType actor) {
        return require(from, to, actor, Facts.PREPAID);
    }

    /**
     * The edge from → to for this actor and order, or 409 {from, to, allowed[]}: INVALID_STATUS_TRANSITION, or the
     * payment-method code of the edge's condition (COD_ORDER, COD_NOT_COLLECTED, COD_ALREADY_COLLECTED)
     * when the edge exists for this actor but not for this kind of order.
     */
    public static Transition require(OrderStatus from, OrderStatus to, OrderActorType actor, Facts facts) {
        String code = null;
        for (var t : TABLE.getOrDefault(from, List.of())) {
            if (t.to() != to || !t.actors().contains(actor)) continue;
            if (t.applies().test(facts)) return t;
            if (code == null) code = t.applies().failureCode(facts);
        }
        throw new InvalidStatusTransitionException(from, to, allowedTargets(from, actor, null, facts),
            code == null ? "INVALID_STATUS_TRANSITION" : code);
    }
}
