package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.order.carrier.CarrierDeliveryVerifier;
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
 * already in returns changed=false without side effects. Shipment side effects also write the shipment timeline
 * (shipment_tracking_events) and SHIP validates carrier / tracking number via {@link CarrierService}.
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
        ADMIN(null),
        /** merchant acceptance; target from OrderStateMachine.acceptTarget (null = status unchanged) */
        ACCEPT(null);

        final OrderStatus target;
        Action(OrderStatus target) { this.target = target; }
    }

    private static final Set<OrderStatus> PROCESS_TARGETS = Set.of(OrderStatus.READY_TO_PACK, OrderStatus.PACKING, OrderStatus.PACKED);

    public record Result(boolean changed, OrderStatus from, OrderStatus to, Order order) {}

    /**
     * Internal options for callers inside the backend (payment / refund services), never from a request body.
     * receivedAmount: captured amount when it differs from the order total (shop accepted a mismatch);
     * skipAmountCheck: the caller already validated the amount; event: timeline event name override;
     * shipmentEvent: note / location / event time / source for the shipment timeline entry (shipment API, carrier updates).
     */
    public record Internal(BigDecimal receivedAmount, boolean skipAmountCheck, String event, ShipmentTimeline.Detail shipmentEvent) {
        public Internal(BigDecimal receivedAmount, boolean skipAmountCheck, String event) {
            this(receivedAmount, skipAmountCheck, event, null);
        }

        public static Internal none() { return new Internal(null, false, null, null); }

        public static Internal shipment(ShipmentTimeline.Detail detail) { return new Internal(null, false, null, detail); }

        ShipmentTimeline.Detail detail() { return shipmentEvent == null ? ShipmentTimeline.Detail.none() : shipmentEvent; }
    }

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final OrderInventoryService inventory;
    private final ShipmentRepository shipments;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final OrderPromotionUsageRepository usages;
    private final OrderMoneyLedger ledger;
    private final OrderItemRevealLogRepository revealLogs;
    private final PaymentProofRepository proofs;
    private final MerchantOrderConfirmationRepository confirmations;
    private final CarrierService carriers;
    private final ShipmentTimeline timeline;
    private final CarrierDeliveryVerifier carrierDelivery;

    public OrderTransitionService(OrderRepository orders, OrderStatusHistoryRepository history, OrderInventoryService inventory,
                                  ShipmentRepository shipments, PaymentRepository payments, RefundRepository refunds,
                                  OrderPromotionUsageRepository usages, OrderMoneyLedger ledger, OrderItemRevealLogRepository revealLogs,
                                  PaymentProofRepository proofs, MerchantOrderConfirmationRepository confirmations,
                                  CarrierService carriers, ShipmentTimeline timeline, CarrierDeliveryVerifier carrierDelivery) {
        this.carriers = carriers;
        this.timeline = timeline;
        this.carrierDelivery = carrierDelivery;
        this.confirmations = confirmations;
        this.ledger = ledger;
        this.revealLogs = revealLogs;
        this.proofs = proofs;
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
        return transition(orderId, actor, action, body, Internal.none());
    }

    @Transactional
    public Result transition(UUID orderId, OrderActor actor, Action requested, OrderDtos.TransitionRequest body, Internal internal) {
        var action = requested;
        var opts = internal == null ? Internal.none() : internal;
        var req = body == null ? OrderDtos.TransitionRequest.empty() : body;
        var order = orders.lockById(orderId).filter(actor::canSee)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "ไม่พบออเดอร์"));
        if (req.expectedVersion() != null && req.expectedVersion() != order.getRowVersion()) {
            throw new AuthException(HttpStatus.CONFLICT, "ORDER_VERSION_CONFLICT",
                "ออเดอร์ถูกแก้ไขไปแล้ว กรุณาโหลดใหม่ (version ปัจจุบัน " + order.getRowVersion() + ")");
        }
        var from = order.getStatus();
        var facts = OrderStateMachine.Facts.of(order);
        boolean accepted = order.getMerchantConfirmedAt() != null;
        if (actor.type() == OrderActorType.MERCHANT && !accepted && action != Action.ACCEPT && action != Action.CONFIRM
            && !(action == Action.CANCEL && from == OrderStatus.PENDING_PAYMENT)
            && !(action.target != null && action.target == from)) { // repeated no-op stays a no-op
            // MerchantVisibility: nothing but accept / decline (unpaid) before the shop has accepted the order
            throw MerchantVisibility.notAccepted();
        }
        if (action == Action.ACCEPT) {
            if (accepted) return new Result(false, from, from, order);
            if (!OrderStateMachine.canAccept(from, actor.type())) {
                throw new InvalidStatusTransitionException(from, null,
                    OrderStateMachine.allowedTargets(from, actor.type(), parse(order.getStatusBeforeRefund()), facts), "INVALID_STATUS_TRANSITION");
            }
            if (OrderStateMachine.acceptTarget(from, facts) == null) return acceptInPlace(order, actor, req);
            action = Action.CONFIRM; // PAID_AWAITING_MERCHANT_CONFIRM (or unpaid COD): accept = the CONFIRM edge
        }
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
                edge = OrderStateMachine.require(from, to, actor.type(), facts);
            } catch (InvalidStatusTransitionException exception) {
                throw invalid(order, to, actor, exception.getCode()); // keeps COD_ORDER / COD_NOT_COLLECTED / …
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
        CarrierService.Checked checkedTracking = null;
        Shipment existingShipment = null;
        Instant shippedAt = null;
        if (trackingRequired) {
            // carrier / tracking set earlier (PUT …/shipment/tracking) or the order's shipping method carrier are defaults
            existingShipment = shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId()).orElse(null);
            if (carrier == null && existingShipment != null) carrier = existingShipment.getCarrierCode();
            if (tracking == null && existingShipment != null) tracking = existingShipment.getTrackingNumber();
            if (carrier == null) carrier = carriers.methodCarrier(order.getShippingMethodId());
            if (carrier != null && carrier.length() > 50) violations.add(new FieldViolation("carrierCode", "MAX_LENGTH", "ชื่อขนส่งยาวเกิน 50 ตัวอักษร"));
            else checkedTracking = carriers.check(order.getShopId(), carrier, tracking, violations);
            shippedAt = ShipmentTimeline.parseTime(req.shippedAt(), "shippedAt", order.getPlacedAt(), violations);
        }
        if (to == OrderStatus.DELIVERED) {
            var current = shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId()).orElse(null);
            if (current != null && ShipmentStatus.RETURNED.name().equals(current.getStatus())) {
                throw new ShipmentTransitionException("SHIPMENT_RETURNED", "พัสดุถูกตีกลับแล้ว ไม่สามารถตั้งเป็นจัดส่งสำเร็จได้",
                    ShipmentStatus.RETURNED, ShipmentStatus.DELIVERED, List.of());
            }
            // Merchant/admin must match carrier delivery status; SYSTEM / carrier webhook-poll skip inside the verifier
            carrierDelivery.requireDelivered(current, actor, opts.detail());
        }
        String provider = null;
        if (to == OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM) {
            provider = clean(req.provider()) == null ? "BANK_TRANSFER" : clean(req.provider()).toUpperCase();
            if (!PAYMENT_PROVIDERS.contains(provider)) {
                violations.add(new FieldViolation("provider", "INVALID", "ช่องทางชำระเงินต้องเป็น " + String.join(", ", PAYMENT_PROVIDERS.stream().sorted().toList())));
            }
            if (!opts.skipAmountCheck() && req.amount() != null && req.amount().setScale(2, RoundingMode.HALF_UP).compareTo(order.getGrandTotal()) != 0) {
                violations.add(new FieldViolation("amount", "AMOUNT_MISMATCH", "ยอดชำระต้องเท่ากับยอดออเดอร์ ฿" + order.getGrandTotal()));
            }
            var ref = clean(req.paymentReference());
            if (ref != null && ref.length() > 120) violations.add(new FieldViolation("paymentReference", "MAX_LENGTH", "เลขอ้างอิงยาวเกิน 120 ตัวอักษร"));
        }
        if (to == OrderStatus.REFUND_REQUESTED && req.amount() != null && req.amount().signum() <= 0) {
            violations.add(new FieldViolation("amount", "INVALID", "ยอดคืนเงินต้องมากกว่า 0"));
        }
        if (!violations.isEmpty()) throw new ValidationException(violations);
        if (checkedTracking != null) {
            carriers.requireUnique(checkedTracking, existingShipment == null ? null : existingShipment.getId());
            carrier = checkedTracking.carrier().getCode();
            tracking = checkedTracking.trackingNumber();
        }
        var reference = to == OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM ? clean(req.paymentReference()) : null;
        if (reference != null && (payments.existsByProviderAndProviderPaymentId(provider, reference)
            || proofs.findLiveByReference(order.getShopId(), reference).stream().anyMatch(p -> !p.getOrderId().equals(order.getId())))) {
            throw new AuthException(HttpStatus.CONFLICT, "PAYMENT_REFERENCE_DUPLICATE", "เลขอ้างอิงการชำระเงินนี้ถูกใช้กับออเดอร์อื่นแล้ว");
        }
        if (to == OrderStatus.CANCELLED && from == OrderStatus.PENDING_PAYMENT && actor.type() != OrderActorType.ADMIN
            && ledger.hasPendingReview(order.getId())) {
            throw new AuthException(HttpStatus.CONFLICT, "PAYMENT_UNDER_REVIEW",
                "มีสลิปชำระเงินรอร้านตรวจสอบ — ร้านต้องยืนยันหรือปฏิเสธสลิปก่อนยกเลิก");
        }

        var metadata = new LinkedHashMap<String, Object>();
        if (reject) {
            // back to where the order was; no forward side effects (stock / shipment untouched)
            order.setStatusBeforeRefund(null);
            if (opts.event() == null) ledger.rejectOpen(order, actor, reason);
            metadata.put("event", opts.event() == null ? "REFUND_REJECTED" : opts.event());
        } else {
            apply(order, from, to, actor, req, opts, reason, carrier, tracking, provider, shippedAt, metadata);
        }
        order.setStatus(to);
        order.stamp(actor.userId());
        orders.saveAndFlush(order); // bumps row_version; a stale concurrent writer fails here

        var entry = new OrderStatusHistory(order.getId(), from, to, actor.userId(), actor.type(), reason);
        entry.setMetadataJson(OrderJson.write(metadata));
        entry.stamp(actor.userId());
        history.save(entry);
        if (to == OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM && order.getMerchantConfirmedAt() != null) {
            return autoConfirm(order, actor, from);
        }
        ledger.snapshot(order);
        return new Result(true, from, to, order);
    }

    /** Accept without a status change (PENDING_PAYMENT, REFUND_REQUESTED, CANCELLED, REFUNDED): flag + audit + timeline. */
    private Result acceptInPlace(Order order, OrderActor actor, OrderDtos.TransitionRequest req) {
        var reason = clean(req.reason());
        if (reason != null && reason.length() > MAX_REASON) {
            throw new ValidationException(List.of(new FieldViolation("reason", "MAX_LENGTH", "เหตุผลยาวเกิน " + MAX_REASON + " ตัวอักษร")));
        }
        var status = order.getStatus();
        recordAcceptance(order, actor, Instant.now(), reason);
        order.stamp(actor.userId());
        orders.saveAndFlush(order); // bumps row_version; a stale concurrent writer fails here
        var entry = new OrderStatusHistory(order.getId(), status, status, actor.userId(), actor.type(), reason);
        entry.setMetadataJson(OrderJson.write(Map.of("event", "ORDER_ACCEPTED")));
        entry.stamp(actor.userId());
        history.save(entry);
        ledger.snapshot(order);
        return new Result(true, status, status, order);
    }

    /** Paid order the shop had already accepted: PAID_AWAITING_MERCHANT_CONFIRM → MERCHANT_CONFIRMED in the same transaction. */
    private Result autoConfirm(Order order, OrderActor actor, OrderStatus originalFrom) {
        var paid = OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM;
        OrderStateMachine.require(paid, OrderStatus.MERCHANT_CONFIRMED, actor.type());
        order.setMerchantConfirmStatus("CONFIRMED");
        order.setStatus(OrderStatus.MERCHANT_CONFIRMED);
        order.stamp(actor.userId());
        orders.saveAndFlush(order);
        var entry = new OrderStatusHistory(order.getId(), paid, OrderStatus.MERCHANT_CONFIRMED, actor.userId(), actor.type(), null);
        entry.setMetadataJson(OrderJson.write(Map.of("event", "AUTO_CONFIRMED_ACCEPTED_BEFORE_PAYMENT")));
        entry.stamp(actor.userId());
        history.save(entry);
        ledger.snapshot(order);
        return new Result(true, originalFrom, OrderStatus.MERCHANT_CONFIRMED, order);
    }

    /** V1 acceptance: merchant_confirmed_at / items_revealed_at, merchant_order_confirmations CONFIRM, order_item_reveal_logs. */
    private void recordAcceptance(Order order, OrderActor actor, Instant now, String note) {
        order.setMerchantConfirmStatus("CONFIRMED");
        order.setMerchantConfirmedAt(now);
        order.setItemsRevealedAt(now);
        if (actor.userId() != null) {
            confirmations.save(new MerchantOrderConfirmation(order.getId(), order.getShopId(), actor.userId(), MerchantOrderConfirmation.CONFIRM, now, note));
        }
        reveal(order, actor, now);
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
                       Internal opts, String reason, String carrier, String tracking, String provider, Instant shippedAt,
                       Map<String, Object> metadata) {
        var now = Instant.now();
        var detail = opts.detail();
        switch (to) {
            case PAID_AWAITING_MERCHANT_CONFIRM -> {
                order.setPaymentStatus(PaymentStatus.PAID);
                order.setPaidAt(now);
                if (order.getMerchantConfirmedAt() == null) order.setMerchantConfirmStatus("AWAITING");
                ledger.capture(order, actor, provider, clean(req.paymentReference()), opts.receivedAmount(), reason, metadata);
                inventory.markPaid(order);
            }
            case MERCHANT_CONFIRMED -> {
                order.setMerchantConfirmStatus("CONFIRMED");
                if (order.getMerchantConfirmedAt() == null) recordAcceptance(order, actor, now, reason);
                if (from == OrderStatus.PENDING_PAYMENT && order.isCod()) {
                    // COD accepted: nothing to verify; the reservation is held for shipping like a paid order (payment stays UNPAID)
                    inventory.markPaid(order);
                    metadata.put("event", "COD_ACCEPTED");
                    metadata.put("paymentMethod", PaymentMethod.COD.name());
                }
            }
            case READY_TO_PACK, PACKING, PACKED -> {
                var previous = shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId()).map(Shipment::getStatus).orElse(null);
                var shipment = shipment(order, actor);
                shipment.setStatus(to.name());
                if (to == OrderStatus.PACKED) shipment.setPackedAt(now, actor.userId());
                timeline.statusChanged(shipment, previous, actor, reason, detail);
                ensureConfirmed(order, actor, now);
                metadata.put("event", "FULFILLMENT_" + to.name());
            }
            case DISPATCHED -> {
                var previous = shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId()).map(Shipment::getStatus).orElse(null);
                var shipment = shipment(order, actor);
                var shipped = shippedAt == null ? now : shippedAt;
                shipment.setStatus("DISPATCHED");
                shipment.setCarrierCode(carrier);
                shipment.setTrackingNumber(tracking);
                if (shipment.getPackedAt() == null) shipment.setPackedAt(shipped, actor.userId());
                shipment.setDispatchedAt(shipped, actor.userId());
                timeline.record(shipment, ShipmentTrackingEvent.STATUS_CHANGED, previous, actor, reason,
                    new ShipmentTimeline.Detail(detail.source(), detail.note() == null ? carrier + " " + tracking : detail.note(),
                        detail.location(), shipped, detail.carrierStatusCode(), detail.externalEventId(), detail.safePayload()));
                if (shippedAt != null) metadata.put("shippedAt", shippedAt.toString());
                ensureConfirmed(order, actor, now);
                order.setFulfillmentStatus(FulfillmentStatus.FULFILLED);
                inventory.commit(order, actor.type(), actor.userId());
                metadata.put("event", "SHIPPED");
                metadata.put("carrierCode", carrier);
                metadata.put("trackingNumber", tracking);
            }
            case DELIVERED -> {
                shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId()).ifPresent(shipment -> {
                    var previous = shipment.getStatus();
                    shipment.setStatus("DELIVERED");
                    shipment.setDeliveredAt(detail.eventAt() == null ? now : detail.eventAt());
                    shipment.stamp(actor.userId());
                    timeline.statusChanged(shipment, previous, actor, reason, detail);
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
                if (actor.type() == OrderActorType.MERCHANT && order.getMerchantConfirmedAt() == null) {
                    // shop declines an order it never accepted
                    order.setMerchantConfirmStatus("REJECTED");
                    if (actor.userId() != null) {
                        confirmations.save(new MerchantOrderConfirmation(order.getId(), order.getShopId(), actor.userId(),
                            MerchantOrderConfirmation.REJECT, now, reason));
                    }
                }
                int released = inventory.release(order, actor.type(), actor.userId(), "ยกเลิกออเดอร์ " + order.getOrderNumber());
                boolean failedDelivery = from == OrderStatus.DISPATCHED; // COD only (state machine): parcel refused / returned unpaid
                shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId()).ifPresent(shipment -> {
                    var previous = shipment.getStatus();
                    shipment.setStatus(failedDelivery ? "RETURNED" : "CANCELLED");
                    if (failedDelivery) {
                        var at = detail.eventAt() == null ? now : detail.eventAt();
                        shipment.setReturnedAt(at);
                        if (shipment.getFailedAt() == null) shipment.setFailed(at, reason);
                    }
                    shipment.stamp(actor.userId());
                    timeline.statusChanged(shipment, previous, actor, reason, detail);
                });
                if (failedDelivery) {
                    metadata.put("restockedQuantity", inventory.restockReturned(order, actor.type(), actor.userId(),
                        "พัสดุ COD ส่งไม่สำเร็จ/ตีกลับ ออเดอร์ " + order.getOrderNumber()));
                    order.setFulfillmentStatus(FulfillmentStatus.RETURNED);
                    metadata.put("deliveryFailed", true);
                }
                if (from == OrderStatus.PENDING_PAYMENT) {
                    ledger.cancelPendingPayment(order, actor, reason == null ? "ยกเลิกออเดอร์" : reason);
                    if (order.getPaymentStatus() == PaymentStatus.PENDING || order.getPaymentStatus() == PaymentStatus.FAILED) {
                        order.setPaymentStatus(PaymentStatus.UNPAID);
                    }
                } else {
                    // the shop owes the money back: an APPROVED refund awaiting the shop's transfer proof
                    ledger.openForCancellation(order, actor, reason == null ? "ยกเลิกออเดอร์" : reason, metadata);
                }
                int coupons = usages.deleteByOrderId(order.getId());
                metadata.put("event", failedDelivery ? "DELIVERY_FAILED" : "CANCELLED");
                metadata.put("releasedQuantity", released);
                if (coupons > 0) metadata.put("couponUsageReleased", true);
            }
            case REFUND_REQUESTED -> {
                order.setStatusBeforeRefund(from.name());
                ledger.openRequest(order, actor, req.amount(), reason, metadata);
                metadata.put("event", "REFUND_REQUESTED");
                metadata.put("previousStatus", from.name());
            }
            case REFUNDED -> {
                ledger.requireFullyRefunded(order);
                ledger.syncPaymentStatus(order, actor);
                order.setStatusBeforeRefund(null);
                if (!order.isStockCommitted()) {
                    metadata.put("releasedQuantity", inventory.release(order, actor.type(), actor.userId(), "คืนเงินออเดอร์ " + order.getOrderNumber()));
                } else {
                    boolean restock = req.restock() == null || req.restock();
                    metadata.put("restock", restock);
                    if (restock) {
                        metadata.put("restockedQuantity", inventory.restockAfterRefund(order, actor.type(), actor.userId(), "คืนสินค้าออเดอร์ " + order.getOrderNumber()));
                        order.setFulfillmentStatus(FulfillmentStatus.RETURNED);
                        shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId()).ifPresent(shipment -> {
                            var previous = shipment.getStatus();
                            shipment.setStatus("RETURNED");
                            if (shipment.getReturnedAt() == null) shipment.setReturnedAt(now);
                            shipment.stamp(actor.userId());
                            timeline.statusChanged(shipment, previous, actor, reason == null ? "คืนสินค้าหลังคืนเงิน" : reason, detail);
                        });
                    }
                }
                metadata.put("event", "REFUNDED");
            }
            default -> { }
        }
    }

    private void ensureConfirmed(Order order, OrderActor actor, Instant now) {
        if (order.getMerchantConfirmedAt() == null) recordAcceptance(order, actor, now, null);
    }

    /** V1 order_item_reveal_logs: line items become visible to the seller at merchant confirm. */
    private void reveal(Order order, OrderActor actor, Instant now) {
        var reason = switch (actor.type()) {
            case ADMIN -> "ADMIN_OVERRIDE";
            case SYSTEM -> "SYSTEM";
            default -> "MERCHANT_CONFIRM";
        };
        revealLogs.save(new OrderItemRevealLog(order.getId(), order.getShopId(), actor.userId(), reason, now));
    }

    private Shipment shipment(Order order, OrderActor actor) {
        var shipment = shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId())
            .orElseGet(() -> new Shipment(order.getId(), order.getShopId(), order.getShippingMethodId()));
        shipment.stamp(actor.userId());
        return shipments.save(shipment);
    }

    private InvalidStatusTransitionException invalid(Order order, OrderStatus to, OrderActor actor) {
        return invalid(order, to, actor, "INVALID_STATUS_TRANSITION");
    }

    private InvalidStatusTransitionException invalid(Order order, OrderStatus to, OrderActor actor, String code) {
        return new InvalidStatusTransitionException(order.getStatus(), to,
            OrderStateMachine.allowedTargets(order.getStatus(), actor.type(), parse(order.getStatusBeforeRefund()), OrderStateMachine.Facts.of(order)),
            code == null ? "INVALID_STATUS_TRANSITION" : code);
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
