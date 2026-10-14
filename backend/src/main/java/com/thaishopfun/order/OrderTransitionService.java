package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every order status change goes through {@link #transition}: row lock (SELECT … FOR UPDATE) + optional
 * expectedVersion check, validation against {@link OrderStateMachine}, side effects on payment / fulfillment / stock /
 * coupon usage, and one order_status_history row (actor, reason, metadata). Repeating a transition the order is
 * already in returns changed=false without side effects.
 */
@Service
public class OrderTransitionService {
    public static final Set<String> PAYMENT_PROVIDERS = Set.of("OMISE", "2C2P", "PROMPTPAY", "BANK_TRANSFER", "CUSTOM");
    static final int MAX_REASON = 500;

    /** Which button was pressed; REJECT_REFUND has no fixed target (goes back to status_before_refund). */
    public enum Action {
        MARK_PAID(OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM),
        CONFIRM(OrderStatus.MERCHANT_CONFIRMED),
        PROCESS(null),
        SHIP(OrderStatus.DISPATCHED),
        DELIVER(OrderStatus.DELIVERED),
        COMPLETE(OrderStatus.COMPLETED),
        CANCEL(OrderStatus.CANCELLED),
        REQUEST_REFUND(OrderStatus.REFUND_REQUESTED),
        APPROVE_REFUND(OrderStatus.REFUNDED),
        REJECT_REFUND(null),
        /** admin: request.status is the target */
        ADMIN(null);

        final OrderStatus target;
        Action(OrderStatus target) { this.target = target; }
    }

    private static final Set<OrderStatus> PROCESS_TARGETS = Set.of(OrderStatus.READY_TO_PACK, OrderStatus.PACKING, OrderStatus.PACKED);

    public record Result(boolean changed, OrderStatus from, OrderStatus to, Order order) {}

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final OrderInventoryService inventory;
    private final ShipmentRepository shipments;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final OrderPromotionUsageRepository usages;

    public OrderTransitionService(OrderRepository orders, OrderStatusHistoryRepository history, OrderInventoryService inventory,
                                  ShipmentRepository shipments, PaymentRepository payments, RefundRepository refunds,
                                  OrderPromotionUsageRepository usages) {
        this.orders = orders;
        this.history = history;
        this.inventory = inventory;
        this.shipments = shipments;
        this.payments = payments;
        this.refunds = refunds;
        this.usages = usages;
    }

    @Transactional
    public Result transition(UUID orderId, OrderActor actor, Action action, OrderDtos.TransitionRequest body) {
        var req = body == null ? OrderDtos.TransitionRequest.empty() : body;
        var order = orders.lockById(orderId).filter(actor::canSee)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "ไม่พบออเดอร์"));
        if (req.expectedVersion() != null && req.expectedVersion() != order.getRowVersion()) {
            throw new AuthException(HttpStatus.CONFLICT, "ORDER_VERSION_CONFLICT",
                "ออเดอร์ถูกแก้ไขไปแล้ว กรุณาโหลดใหม่ (version ปัจจุบัน " + order.getRowVersion() + ")");
        }
        var from = order.getStatus();
        var to = resolveTarget(order, actor, action, req);

        if (from == to) {
            return new Result(false, from, to, order); // repeated request: nothing to do
        }

        boolean reject = action == Action.REJECT_REFUND
            || (action == Action.ADMIN && from == OrderStatus.REFUND_REQUESTED && to != OrderStatus.REFUNDED);
        boolean reasonRequired;
        boolean trackingRequired;
        if (reject) {
            var previous = parse(order.getStatusBeforeRefund());
            if (from != OrderStatus.REFUND_REQUESTED || previous != to || !OrderStateMachine.REJECT_REFUND_ACTORS.contains(actor.type())) {
                throw invalid(order, to, actor);
            }
            reasonRequired = true;
            trackingRequired = false;
        } else {
            OrderStateMachine.Transition edge;
            try {
                edge = OrderStateMachine.require(from, to, actor.type());
            } catch (InvalidStatusTransitionException exception) {
                throw invalid(order, to, actor);
            }
            reasonRequired = edge.reasonRequired();
            trackingRequired = edge.shippingRequired();
        }
        if (actor.type() == OrderActorType.ADMIN) reasonRequired = true; // admin override always explains itself

        var violations = new ArrayList<FieldViolation>();
        var reason = clean(req.reason());
        if (reasonRequired && reason == null) violations.add(new FieldViolation("reason", "REQUIRED", "กรุณาระบุเหตุผล"));
        if (reason != null && reason.length() > MAX_REASON) violations.add(new FieldViolation("reason", "MAX_LENGTH", "เหตุผลยาวเกิน " + MAX_REASON + " ตัวอักษร"));
        var carrier = clean(req.carrierCode());
        var tracking = clean(req.trackingNumber());
        if (trackingRequired) {
            if (carrier == null) violations.add(new FieldViolation("carrierCode", "REQUIRED", "กรุณาระบุบริษัทขนส่ง"));
            else if (carrier.length() > 50) violations.add(new FieldViolation("carrierCode", "MAX_LENGTH", "ชื่อขนส่งยาวเกิน 50 ตัวอักษร"));
            if (tracking == null) violations.add(new FieldViolation("trackingNumber", "REQUIRED", "กรุณาระบุเลขพัสดุ"));
            else if (tracking.length() > 100) violations.add(new FieldViolation("trackingNumber", "MAX_LENGTH", "เลขพัสดุยาวเกิน 100 ตัวอักษร"));
        }
        String provider = null;
        if (to == OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM) {
            provider = clean(req.provider()) == null ? "BANK_TRANSFER" : clean(req.provider()).toUpperCase();
            if (!PAYMENT_PROVIDERS.contains(provider)) {
                violations.add(new FieldViolation("provider", "INVALID", "ช่องทางชำระเงินต้องเป็น " + String.join(", ", PAYMENT_PROVIDERS.stream().sorted().toList())));
            }
            if (req.amount() != null && req.amount().setScale(2, RoundingMode.HALF_UP).compareTo(order.getGrandTotal()) != 0) {
                violations.add(new FieldViolation("amount", "AMOUNT_MISMATCH", "ยอดชำระต้องเท่ากับยอดออเดอร์ ฿" + order.getGrandTotal()));
            }
            var ref = clean(req.paymentReference());
            if (ref != null && ref.length() > 120) violations.add(new FieldViolation("paymentReference", "MAX_LENGTH", "เลขอ้างอิงยาวเกิน 120 ตัวอักษร"));
        }
        if (!violations.isEmpty()) throw new ValidationException(violations);
        if (to == OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM && clean(req.paymentReference()) != null
            && payments.existsByProviderAndProviderPaymentId(provider, clean(req.paymentReference()))) {
            throw new AuthException(HttpStatus.CONFLICT, "PAYMENT_REFERENCE_DUPLICATE", "เลขอ้างอิงการชำระเงินนี้ถูกใช้กับออเดอร์อื่นแล้ว");
        }

        var metadata = new LinkedHashMap<String, Object>();
        if (reject) {
            // back to where the order was; no forward side effects (stock / shipment untouched)
            order.setStatusBeforeRefund(null);
            metadata.put("event", "REFUND_REJECTED");
        } else {
            apply(order, from, to, actor, req, reason, carrier, tracking, provider, metadata);
        }
        order.setStatus(to);
        order.stamp(actor.userId());
        orders.saveAndFlush(order); // bumps row_version; a stale concurrent writer fails here

        var entry = new OrderStatusHistory(order.getId(), from, to, actor.userId(), actor.type(), reason);
        entry.setMetadataJson(OrderJson.write(metadata));
        entry.stamp(actor.userId());
        history.save(entry);
        return new Result(true, from, to, order);
    }

    private OrderStatus resolveTarget(Order order, OrderActor actor, Action action, OrderDtos.TransitionRequest req) {
        return switch (action) {
            case PROCESS -> {
                var target = parseRequested(req.status(), "status");
                if (target == null || !PROCESS_TARGETS.contains(target)) {
                    throw new ValidationException(List.of(new FieldViolation("status", "INVALID", "status ต้องเป็น READY_TO_PACK, PACKING หรือ PACKED")));
                }
                yield target;
            }
            case REJECT_REFUND -> {
                var previous = parse(order.getStatusBeforeRefund());
                if (order.getStatus() != OrderStatus.REFUND_REQUESTED || previous == null) throw invalid(order, null, actor);
                yield previous;
            }
            case ADMIN -> {
                var target = parseRequested(req.status(), "status");
                if (target == null) throw new ValidationException(List.of(new FieldViolation("status", "REQUIRED", "กรุณาระบุสถานะปลายทาง")));
                yield target;
            }
            default -> action.target;
        };
    }

    private void apply(Order order, OrderStatus from, OrderStatus to, OrderActor actor, OrderDtos.TransitionRequest req,
                       String reason, String carrier, String tracking, String provider, Map<String, Object> metadata) {
        var now = Instant.now();
        switch (to) {
            case PAID_AWAITING_MERCHANT_CONFIRM -> {
                order.setPaymentStatus(PaymentStatus.PAID);
                order.setPaidAt(now);
                order.setMerchantConfirmStatus("AWAITING");
                var ref = clean(req.paymentReference());
                var payment = new Payment(order.getId(), order.getShopId(), provider, order.getGrandTotal(), ref, now);
                payment.stamp(actor.userId());
                payments.save(payment);
                inventory.markPaid(order);
                metadata.put("event", "PAYMENT_CAPTURED");
                metadata.put("provider", provider);
                metadata.put("amount", order.getGrandTotal());
                if (ref != null) metadata.put("paymentReference", ref);
            }
            case MERCHANT_CONFIRMED -> {
                order.setMerchantConfirmStatus("CONFIRMED");
                order.setMerchantConfirmedAt(now);
            }
            case READY_TO_PACK, PACKING, PACKED -> {
                var shipment = shipment(order, actor);
                shipment.setStatus(to.name());
                if (to == OrderStatus.PACKED) shipment.setPackedAt(now, actor.userId());
                ensureConfirmed(order, now);
                metadata.put("event", "FULFILLMENT_" + to.name());
            }
            case DISPATCHED -> {
                var shipment = shipment(order, actor);
                shipment.setStatus("DISPATCHED");
                shipment.setCarrierCode(carrier);
                shipment.setTrackingNumber(tracking);
                if (shipment.getPackedAt() == null) shipment.setPackedAt(now, actor.userId());
                shipment.setDispatchedAt(now, actor.userId());
                ensureConfirmed(order, now);
                order.setFulfillmentStatus(FulfillmentStatus.FULFILLED);
                inventory.commit(order, actor.type(), actor.userId());
                metadata.put("event", "SHIPPED");
                metadata.put("carrierCode", carrier);
                metadata.put("trackingNumber", tracking);
            }
            case DELIVERED -> {
                shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId()).ifPresent(shipment -> {
                    shipment.setStatus("DELIVERED");
                    shipment.setDeliveredAt(now);
                    shipment.stamp(actor.userId());
                });
                metadata.put("event", "DELIVERED");
            }
            case COMPLETED -> {
                order.setCompletedAt(now);
                order.setCompletionSource(switch (actor.type()) {
                    case BUYER -> "BUYER_CONFIRM";
                    case SYSTEM -> "AUTO";
                    default -> "ADMIN";
                });
            }
            case CANCELLED -> {
                order.setCancelledAt(now);
                order.setCancelReason(reason);
                if (from == OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM && actor.type() == OrderActorType.MERCHANT) {
                    order.setMerchantConfirmStatus("REJECTED");
                }
                int released = inventory.release(order, actor.type(), actor.userId(), "ยกเลิกออเดอร์ " + order.getOrderNumber());
                shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId()).ifPresent(shipment -> {
                    shipment.setStatus("CANCELLED");
                    shipment.stamp(actor.userId());
                });
                if (order.getPaymentStatus() == PaymentStatus.PAID) {
                    refundPayment(order, actor, reason == null ? "ยกเลิกออเดอร์" : reason, metadata);
                }
                int coupons = usages.deleteByOrderId(order.getId());
                metadata.put("event", "CANCELLED");
                metadata.put("releasedQuantity", released);
                if (coupons > 0) metadata.put("couponUsageReleased", true);
            }
            case REFUND_REQUESTED -> {
                order.setStatusBeforeRefund(from.name());
                metadata.put("event", "REFUND_REQUESTED");
                metadata.put("previousStatus", from.name());
            }
            case REFUNDED -> {
                refundPayment(order, actor, reason == null ? "อนุมัติคืนเงิน" : reason, metadata);
                if (!order.isStockCommitted()) {
                    metadata.put("releasedQuantity", inventory.release(order, actor.type(), actor.userId(), "คืนเงินออเดอร์ " + order.getOrderNumber()));
                } else {
                    boolean restock = req.restock() == null || req.restock();
                    metadata.put("restock", restock);
                    if (restock) {
                        metadata.put("restockedQuantity", inventory.restockAfterRefund(order, actor.type(), actor.userId(), "คืนสินค้าออเดอร์ " + order.getOrderNumber()));
                        order.setFulfillmentStatus(FulfillmentStatus.RETURNED);
                        shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId()).ifPresent(shipment -> {
                            shipment.setStatus("RETURNED");
                            shipment.stamp(actor.userId());
                        });
                    }
                }
                metadata.put("event", "REFUNDED");
            }
            default -> { }
        }
    }

    private void ensureConfirmed(Order order, Instant now) {
        if (order.getMerchantConfirmedAt() == null) {
            order.setMerchantConfirmStatus("CONFIRMED");
            order.setMerchantConfirmedAt(now);
        }
    }

    private void refundPayment(Order order, OrderActor actor, String reason, Map<String, Object> metadata) {
        if (order.getPaymentStatus() != PaymentStatus.PAID) return;
        var payment = payments.findByOrderId(order.getId()).stream().findFirst().orElse(null);
        if (payment != null) {
            payment.setStatus("REFUNDED");
            payment.stamp(actor.userId());
        }
        if (order.getGrandTotal().compareTo(BigDecimal.ZERO) > 0 && refunds.findByOrderId(order.getId()).isEmpty()) {
            var refund = new Refund(order.getId(), payment == null ? null : payment.getId(), order.getShopId(), order.getGrandTotal(), actor.userId(), reason);
            refund.stamp(actor.userId());
            refunds.save(refund);
            metadata.put("refundAmount", order.getGrandTotal());
        }
        order.setPaymentStatus(PaymentStatus.REFUNDED);
    }

    private Shipment shipment(Order order, OrderActor actor) {
        var shipment = shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId())
            .orElseGet(() -> new Shipment(order.getId(), order.getShopId(), order.getShippingMethodId()));
        shipment.stamp(actor.userId());
        return shipments.save(shipment);
    }

    private InvalidStatusTransitionException invalid(Order order, OrderStatus to, OrderActor actor) {
        return new InvalidStatusTransitionException(order.getStatus(), to,
            OrderStateMachine.allowedTargets(order.getStatus(), actor.type(), parse(order.getStatusBeforeRefund())), "INVALID_STATUS_TRANSITION");
    }

    static OrderStatus parse(String raw) {
        try {
            return OrderStatus.parse(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static OrderStatus parseRequested(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return OrderStatus.parse(raw);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(List.of(new FieldViolation(field, "INVALID", "ไม่รู้จักสถานะ " + raw)));
        }
    }

    static String clean(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
