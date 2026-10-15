package com.thaishopfun.order;

/**
 * What a seller may see of an order before pressing confirm. Follows the V1 schema design:
 * <ul>
 *   <li>order_items: "seller UI ห้าม SELECT จนกว่า merchant_confirmed_at" — line items (names, SKUs, images, unit prices,
 *       cost) are withheld until the shop confirms; order_item_reveal_logs records the reveal.</li>
 *   <li>order_financial_snapshots: "ยอดเงิน denorm สำหรับ seller list" — totals, item count / quantity, payment status
 *       and province / district / postal hints are visible before confirm.</li>
 *   <li>payment_proofs.verified_by: the shop verifies the transfer slip (needs amount + slip) before it confirms.</li>
 * </ul>
 * Buyers and platform admins always see everything. Full address follows the same rule plus SHIPMENT_ADDRESS_READ.
 * Single switch point if the business rule changes.
 */
public final class MerchantVisibility {
    private MerchantVisibility() {}

    public static boolean itemsHidden(Order order, OrderActor actor) {
        return actor.type() == OrderActorType.MERCHANT && order.getMerchantConfirmedAt() == null;
    }

    /** Money (totals, payments, slips, refunds) is visible to the shop at every stage — required to verify transfers. */
    public static boolean moneyHidden(Order order, OrderActor actor) {
        return false;
    }
}
