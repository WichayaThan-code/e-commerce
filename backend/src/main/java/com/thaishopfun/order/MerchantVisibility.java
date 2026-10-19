package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;

/**
 * Business rule (owner decision 2026-10-16): until the shop ACCEPTS an order, the seller sees only the order id, order
 * number, created date/time and that it awaits acceptance (plus the accept / decline actions). Amounts, financial
 * snapshot, payments, slips, refunds, items and counts, buyer / customer data, address and timeline details stay
 * hidden. Acceptance = V1 {@code orders.merchant_confirmed_at} (+ {@code items_revealed_at},
 * {@code merchant_order_confirmations} CONFIRM row, {@code order_item_reveal_logs}). Buyers and admins are not affected.
 * <p>Single switch point: every merchant read / write path asks {@link #restricted} or calls {@link #requireAccepted}.
 */
public final class MerchantVisibility {
    public static final String NOT_ACCEPTED = "ORDER_NOT_ACCEPTED";

    private MerchantVisibility() {}

    /** True while a shop member looks at an order the shop has not accepted yet. */
    public static boolean restricted(Order order, OrderActor actor) {
        return actor.type() == OrderActorType.MERCHANT && order.getMerchantConfirmedAt() == null;
    }

    public static boolean itemsHidden(Order order, OrderActor actor) {
        return restricted(order, actor);
    }

    public static boolean moneyHidden(Order order, OrderActor actor) {
        return restricted(order, actor);
    }

    /** 409 ORDER_NOT_ACCEPTED for merchant payment / slip / refund endpoints on an order not accepted yet. */
    public static void requireAccepted(Order order, OrderActor actor) {
        if (restricted(order, actor)) throw notAccepted();
    }

    public static AuthException notAccepted() {
        return new AuthException(HttpStatus.CONFLICT, NOT_ACCEPTED, "กรุณากดรับออเดอร์ก่อน จึงจะดูรายละเอียดหรือดำเนินการได้");
    }
}
