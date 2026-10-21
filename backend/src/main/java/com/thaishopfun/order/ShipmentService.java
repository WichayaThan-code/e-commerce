package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shipment API on top of the order state machine. Every write locks the order row (same lock as order transitions),
 * checks expectedVersion (shipments.row_version) / expectedOrderVersion, and honours MerchantVisibility (nothing
 * before the shop accepted the order). Order-level effects always go through {@link OrderTransitionService}:
 * ship = SHIP (→ DISPATCHED), shipment DELIVERED = DELIVER (→ DELIVERED), RETURNED on an uncollected COD order =
 * CANCEL (failed-delivery path: restock once, fulfillment RETURNED). In-flight statuses and a prepaid return only move
 * the shipment (+ an order timeline entry); a prepaid return is refunded through the refund flow.
 */
@Service
public class ShipmentService {
    static final int MAX_TEXT = 500;
    static final int MAX_LOCATION = 255;
    static final int MAX_CARRIER_STATUS = 80;
    private static final Set<OrderStatus> PRE_DISPATCH = EnumSet.of(OrderStatus.MERCHANT_CONFIRMED, OrderStatus.READY_TO_PACK,
        OrderStatus.PACKING, OrderStatus.PACKED);

    public record Filters(String status, String carrierCode, String trackingNumber, String from, String to, Integer page, Integer size) {}

    /** One parsed carrier update (from a CarrierTrackingProvider). status null / unknown = timeline entry only. */
    public record CarrierUpdate(String externalEventId, String trackingNumber, String status, String carrierStatusCode, String description,
                                String location, Instant eventAt, Map<String, Object> safePayload) {}

    public enum CarrierOutcome { APPLIED, RECORDED, DUPLICATE, UNMATCHED }

    private final OrderRepository orders;
    private final ShipmentRepository shipments;
    private final ShipmentTrackingEventRepository events;
    private final CarrierRepository carrierRepository;
    private final CarrierService carriers;
    private final ShipmentTimeline timeline;
    private final OrderTransitionService transitions;
    private final OrderMoneyLedger ledger;

    public ShipmentService(OrderRepository orders, ShipmentRepository shipments, ShipmentTrackingEventRepository events,
                           CarrierRepository carrierRepository, CarrierService carriers, ShipmentTimeline timeline,
                           OrderTransitionService transitions, OrderMoneyLedger ledger) {
        this.orders = orders;
        this.shipments = shipments;
        this.events = events;
        this.carrierRepository = carrierRepository;
        this.carriers = carriers;
        this.timeline = timeline;
        this.transitions = transitions;
        this.ledger = ledger;
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public ShipmentDtos.ShipmentDetail get(OrderActor actor, UUID orderId) {
        var order = load(actor, orderId, false);
        return detail(order, shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(orderId).orElseThrow(ShipmentService::notFound), actor);
    }

    /** Buyer lookup by carrier + tracking number: only the buyer's own orders (anything else = 404). */
    @Transactional(readOnly = true)
    public ShipmentDtos.ShipmentDetail lookup(OrderActor buyer, String carrierCode, String trackingNumber) {
        var violations = new ArrayList<FieldViolation>();
        var code = CarrierService.normalizeCode(carrierCode);
        var tracking = CarrierService.normalizeTracking(trackingNumber);
        if (code == null) violations.add(new FieldViolation("carrierCode", "REQUIRED", "กรุณาระบุบริษัทขนส่ง"));
        if (tracking == null) violations.add(new FieldViolation("trackingNumber", "REQUIRED", "กรุณาระบุเลขพัสดุ"));
        if (!violations.isEmpty()) throw new ValidationException(violations);
        for (var shipment : shipments.findLiveByTracking(code, tracking)) {
            var order = orders.findById(shipment.getOrderId()).filter(buyer::canSee).orElse(null);
            if (order != null) return detail(order, shipment, buyer);
        }
        throw notFound();
    }

    @Transactional(readOnly = true)
    public OrderDtos.PageResponse<ShipmentDtos.ShipmentSummary> list(OrderActor actor, UUID shopFilter, Filters filters) {
        var f = filters == null ? new Filters(null, null, null, null, null, null, null) : filters;
        var violations = new ArrayList<FieldViolation>();
        ShipmentStatus status = null;
        if (f.status() != null && !f.status().isBlank()) {
            try {
                status = ShipmentStatus.parse(f.status());
            } catch (IllegalArgumentException exception) {
                violations.add(new FieldViolation("status", "INVALID", "ไม่รู้จักสถานะการจัดส่ง " + f.status()));
            }
        }
        var from = date(f.from(), "from", violations);
        var to = date(f.to(), "to", violations);
        if (from != null && to != null && from.isAfter(to)) violations.add(new FieldViolation("from", "RANGE", "วันที่เริ่มต้องไม่เกินวันที่สิ้นสุด"));
        int page = f.page() == null ? 0 : f.page();
        int size = f.size() == null ? 20 : f.size();
        if (page < 0) violations.add(new FieldViolation("page", "MIN", "page ต้องไม่ติดลบ"));
        if (size < 1 || size > OrderQueryService.MAX_PAGE_SIZE) {
            violations.add(new FieldViolation("size", "RANGE", "size ต้องอยู่ระหว่าง 1 ถึง " + OrderQueryService.MAX_PAGE_SIZE));
        }
        if (!violations.isEmpty()) throw new ValidationException(violations);
        var code = CarrierService.normalizeCode(f.carrierCode());
        var tracking = CarrierService.normalizeTracking(f.trackingNumber());
        var statusFilter = status;
        boolean merchant = actor.type() == OrderActorType.MERCHANT;
        Specification<Shipment> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (merchant) {
                predicates.add(cb.equal(root.get("shopId"), actor.shopUuid()));
                // MerchantVisibility: only orders the shop accepted
                var accepted = query.subquery(UUID.class);
                var o = accepted.from(Order.class);
                accepted.select(o.get("id")).where(cb.equal(o.get("id"), root.get("orderId")), cb.isNotNull(o.get("merchantConfirmedAt")));
                predicates.add(cb.exists(accepted));
            } else if (shopFilter != null) {
                predicates.add(cb.equal(root.get("shopId"), shopFilter));
            }
            if (statusFilter != null) predicates.add(cb.equal(root.get("status"), statusFilter.name()));
            if (code != null) predicates.add(cb.equal(root.get("carrierCode"), code));
            if (tracking != null) {
                var like = "%" + tracking.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.like(cb.upper(root.get("trackingNumber")), like, '\\'));
            }
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("dispatchedAt"), from.atStartOfDay(ShipmentTimeline.BANGKOK).toInstant()));
            if (to != null) predicates.add(cb.lessThan(root.get("dispatchedAt"), to.plusDays(1).atStartOfDay(ShipmentTimeline.BANGKOK).toInstant()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var sort = Sort.by(Sort.Direction.DESC, "createdDateTime").and(Sort.by(Sort.Direction.ASC, "id"));
        var result = shipments.findAll(spec, PageRequest.of(page, size, sort));
        Map<UUID, Order> byId = orders.findAllById(result.getContent().stream().map(Shipment::getOrderId).distinct().toList()).stream()
            .collect(Collectors.toMap(Order::getId, Function.identity()));
        var items = result.getContent().stream().map(s -> {
            var order = byId.get(s.getOrderId());
            return new ShipmentDtos.ShipmentSummary(s.getId(), s.getOrderId(), order == null ? null : order.getOrderNumber(),
                order == null ? null : order.getStatus().name(), s.getStatus(), s.getCarrierCode(), s.getTrackingNumber(),
                s.getDispatchedAt(), s.getDeliveredAt(), s.getLastTrackingEventAt(), s.getRowVersion());
        }).toList();
        return new OrderDtos.PageResponse<>(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    // ---------------------------------------------------------------- writes

    /** Ship = order SHIP transition (creates / updates the shipment, DISPATCHED, stock committed). Idempotent. */
    @Transactional
    public ShipmentDtos.ActionResponse ship(OrderActor actor, UUID orderId, ShipmentDtos.CreateShipmentRequest body) {
        var req = body == null ? new ShipmentDtos.CreateShipmentRequest(null, null, null, null, null) : body;
        var order = load(actor, orderId, true);
        var note = OrderTransitionService.clean(req.note());
        if (note != null && note.length() > MAX_TEXT) {
            throw new ValidationException(List.of(new FieldViolation("note", "MAX_LENGTH", "หมายเหตุยาวเกิน " + MAX_TEXT + " ตัวอักษร")));
        }
        var before = shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(orderId).map(Shipment::getStatus).orElse(null);
        var orderFrom = order.getStatus();
        var result = transitions.transition(orderId, actor, OrderTransitionService.Action.SHIP,
            new OrderDtos.TransitionRequest(null, req.carrierCode(), req.trackingNumber(), null, null, null, null, null,
                req.expectedOrderVersion(), req.shippedAt()),
            OrderTransitionService.Internal.shipment(new ShipmentTimeline.Detail(null, note, null, null, null, null, null)));
        var shipment = shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(orderId).orElseThrow(ShipmentService::notFound);
        return new ShipmentDtos.ActionResponse(result.changed(), before, shipment.getStatus(), orderFrom.name(), result.order().getStatus().name(),
            detail(result.order(), shipment, actor));
    }

    /**
     * Set / correct carrier + tracking number before the parcel is delivered (SHIPMENT_UPDATE: "ก่อน Delivered").
     * Before dispatch (accepted order) it may create the READY_TO_PACK shipment so ship can reuse the number.
     */
    @Transactional
    public ShipmentDtos.ActionResponse updateTracking(OrderActor actor, UUID orderId, ShipmentDtos.TrackingRequest body) {
        var req = body == null ? new ShipmentDtos.TrackingRequest(null, null, null, null) : body;
        var order = load(actor, orderId, true);
        var existing = shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(orderId).orElse(null);
        if (existing != null) checkVersion(existing, req.expectedVersion());
        var status = existing == null ? null : existing.statusEnum();
        boolean editable = existing == null ? PRE_DISPATCH.contains(order.getStatus())
            : (status.inFlight() || (PRE_DISPATCH.contains(order.getStatus()) && !status.terminal()));
        if (!editable) {
            throw new ShipmentTransitionException("SHIPMENT_TRACKING_LOCKED",
                "แก้ขนส่ง/เลขพัสดุได้หลังร้านรับออเดอร์และก่อนจัดส่งสำเร็จเท่านั้น", status, null, List.of());
        }
        var violations = new ArrayList<FieldViolation>();
        var reason = reason(req.reason(), actor.type() == OrderActorType.ADMIN, violations);
        var carrierInput = req.carrierCode() == null && existing != null ? existing.getCarrierCode() : req.carrierCode();
        var checked = carriers.check(order.getShopId(), carrierInput, req.trackingNumber(), violations);
        if (!violations.isEmpty()) throw new ValidationException(violations);
        var orderFrom = order.getStatus().name();
        if (existing != null && checked.carrier().getCode().equals(existing.getCarrierCode())
            && checked.trackingNumber().equals(existing.getTrackingNumber())) {
            return new ShipmentDtos.ActionResponse(false, existing.getStatus(), existing.getStatus(), orderFrom, orderFrom, detail(order, existing, actor));
        }
        carriers.requireUnique(checked, existing == null ? null : existing.getId());
        var shipment = existing != null ? existing : new Shipment(order.getId(), order.getShopId(), order.getShippingMethodId());
        var previousCarrier = shipment.getCarrierCode();
        var previousTracking = shipment.getTrackingNumber();
        shipment.setCarrierCode(checked.carrier().getCode());
        shipment.setTrackingNumber(checked.trackingNumber());
        shipment.stamp(actor.userId());
        shipment = shipments.save(shipment);
        var note = (previousTracking == null ? "" : previousCarrier + " " + previousTracking + " → ") + shipment.getCarrierCode() + " " + shipment.getTrackingNumber();
        timeline.record(shipment, ShipmentTrackingEvent.TRACKING_UPDATED, shipment.getStatus(), actor, reason,
            new ShipmentTimeline.Detail(null, note, null, null, null, null, null));
        var meta = new LinkedHashMap<String, Object>();
        meta.put("carrierCode", shipment.getCarrierCode());
        meta.put("trackingNumber", shipment.getTrackingNumber());
        if (previousTracking != null) {
            meta.put("previousCarrierCode", previousCarrier);
            meta.put("previousTrackingNumber", previousTracking);
        }
        ledger.orderEvent(order, actor, "SHIPMENT_TRACKING_UPDATED", reason, meta);
        shipments.saveAndFlush(shipment);
        return new ShipmentDtos.ActionResponse(true, status == null ? null : status.name(), shipment.getStatus(), orderFrom, orderFrom,
            detail(order, shipment, actor));
    }

    /**
     * Status change (forced = the endpoint's status, or body.status) or, with no / the same status, a timeline entry
     * (note / location / carrierStatusCode). Same status without content = changed=false.
     */
    @Transactional
    public ShipmentDtos.ActionResponse update(OrderActor actor, UUID orderId, ShipmentStatus forced, ShipmentDtos.ShipmentUpdateRequest body) {
        var req = body == null ? new ShipmentDtos.ShipmentUpdateRequest(null, null, null, null, null, null, null, null, null) : body;
        var order = load(actor, orderId, true);
        checkOrderVersion(order, req.expectedOrderVersion());
        var shipment = shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(orderId).orElseThrow(ShipmentService::notFound);
        checkVersion(shipment, req.expectedVersion());
        var violations = new ArrayList<FieldViolation>();
        var target = forced;
        if (target == null && req.status() != null && !req.status().isBlank()) {
            try {
                target = ShipmentStatus.parse(req.status());
            } catch (IllegalArgumentException exception) {
                violations.add(new FieldViolation("status", "INVALID", "ไม่รู้จักสถานะการจัดส่ง " + req.status()));
            }
        }
        if (target != null && !ShipmentStateMachine.API_TARGETS.contains(target)) {
            violations.add(new FieldViolation("status", "INVALID", "status ต้องเป็น IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED หรือ RETURNED"
                + " (แพ็ก/ส่งออก/ยกเลิก ใช้ endpoint ของออเดอร์)"));
            target = null;
        }
        var from = shipment.statusEnum();
        boolean statusChange = target != null && target != from;
        var reason = reason(req.reason(), statusChange && (actor.type() == OrderActorType.ADMIN || ShipmentStateMachine.REASON_REQUIRED.contains(target)),
            violations);
        var detail = detail(req, order, violations);
        boolean hasContent = detail.note() != null || detail.location() != null || detail.carrierStatusCode() != null;
        if (!statusChange && target == null && forced == null && !hasContent && violations.isEmpty()) {
            violations.add(new FieldViolation("note", "REQUIRED", "กรุณาระบุ status หรือ note / location"));
        }
        if (Boolean.TRUE.equals(req.returned()) && target != ShipmentStatus.DELIVERY_FAILED) {
            violations.add(new FieldViolation("returned", "INVALID", "returned ใช้กับสถานะ DELIVERY_FAILED เท่านั้น"));
        }
        if (!violations.isEmpty()) throw new ValidationException(violations);
        var orderFrom = order.getStatus().name();
        if (!statusChange) {
            if (!hasContent) { // repeated status (a reason alone is not a new timeline entry)
                return new ShipmentDtos.ActionResponse(false, from.name(), from.name(), orderFrom, orderFrom, detail(order, shipment, actor));
            }
            if (from == ShipmentStatus.CANCELLED) {
                throw new ShipmentTransitionException("SHIPMENT_CLOSED", "การจัดส่งถูกยกเลิกแล้ว", from, from, List.of());
            }
            timeline.record(shipment, ShipmentTrackingEvent.NOTE, from.name(), actor, reason, detail);
            shipment.stamp(actor.userId());
            shipments.saveAndFlush(shipment);
            return new ShipmentDtos.ActionResponse(true, from.name(), from.name(), orderFrom, orderFrom, detail(order, shipment, actor));
        }
        ShipmentStateMachine.require(from, target, actor.type());
        move(order, shipment, from, target, actor, reason, detail, req.expectedOrderVersion());
        if (target == ShipmentStatus.DELIVERY_FAILED && Boolean.TRUE.equals(req.returned())) {
            ShipmentStateMachine.require(ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURNED, actor.type());
            move(order, shipment, ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURNED, actor, reason, detail, null);
        }
        return new ShipmentDtos.ActionResponse(true, from.name(), shipment.getStatus(), orderFrom, order.getStatus().name(), detail(order, shipment, actor));
    }

    /** One carrier update as SYSTEM (own transaction per update; see CarrierWebhookService). */
    @Transactional
    public CarrierOutcome applyCarrierUpdate(String carrierCode, CarrierUpdate update) {
        var code = CarrierService.normalizeCode(carrierCode);
        var tracking = CarrierService.normalizeTracking(update.trackingNumber());
        if (code == null || tracking == null) return CarrierOutcome.UNMATCHED;
        var match = shipments.findLiveByTracking(code, tracking).stream().findFirst().orElse(null);
        if (match == null) return CarrierOutcome.UNMATCHED;
        var actor = OrderActor.system();
        var order = orders.lockById(match.getOrderId()).orElse(null);
        if (order == null) return CarrierOutcome.UNMATCHED;
        var shipment = shipments.findById(match.getId()).orElseThrow();
        var externalId = OrderTransitionService.clean(update.externalEventId());
        if (externalId != null && externalId.length() > 120) externalId = externalId.substring(0, 120);
        if (externalId != null && events.existsByShipmentIdAndExternalEventId(shipment.getId(), externalId)) return CarrierOutcome.DUPLICATE;
        var detail = new ShipmentTimeline.Detail("CARRIER_WEBHOOK", cut(update.description(), MAX_TEXT), cut(update.location(), MAX_LOCATION),
            update.eventAt() != null && update.eventAt().isAfter(Instant.now().plus(ShipmentTimeline.CLOCK_SKEW)) ? Instant.now() : update.eventAt(),
            cut(update.carrierStatusCode(), MAX_CARRIER_STATUS), externalId, update.safePayload());
        ShipmentStatus target = null;
        try {
            if (update.status() != null) target = ShipmentStatus.parse(update.status());
        } catch (IllegalArgumentException ignored) {
            target = null;
        }
        var from = shipment.statusEnum();
        if (target != null && target != from && ShipmentStateMachine.allowed(from, target, actor.type()) && syncPossible(order, target)) {
            var reason = detail.note() != null ? detail.note() : "อัปเดตจากขนส่ง " + code + (detail.carrierStatusCode() == null ? "" : " (" + detail.carrierStatusCode() + ")");
            move(order, shipment, from, target, actor, cut(reason, MAX_TEXT), detail, null);
            return CarrierOutcome.APPLIED;
        }
        timeline.record(shipment, ShipmentTrackingEvent.CARRIER_UPDATE, from.name(), actor, null, detail);
        shipment.stamp(null);
        shipments.saveAndFlush(shipment);
        return CarrierOutcome.RECORDED;
    }

    // ---------------------------------------------------------------- internals

    private void move(Order order, Shipment shipment, ShipmentStatus from, ShipmentStatus target, OrderActor actor, String reason,
                      ShipmentTimeline.Detail detail, Integer expectedOrderVersion) {
        var at = detail.eventAt() == null ? Instant.now() : detail.eventAt();
        switch (target) {
            case IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERY_FAILED -> {
                requireOrderInFlight(order, from, target);
                shipment.setStatus(target.name());
                if (target != ShipmentStatus.DELIVERY_FAILED && shipment.getPickedUpAt() == null) shipment.setPickedUpAt(at);
                if (target == ShipmentStatus.DELIVERY_FAILED) shipment.setFailed(at, reason);
                shipment.stamp(actor.userId());
                timeline.record(shipment, ShipmentTrackingEvent.STATUS_CHANGED, from.name(), actor, reason, detail);
                ledger.orderEvent(order, actor, "SHIPMENT_" + target.name(), reason, eventMeta(shipment, detail));
                shipments.saveAndFlush(shipment);
            }
            case DELIVERED -> transitions.transition(order.getId(), actor, OrderTransitionService.Action.DELIVER,
                new OrderDtos.TransitionRequest(reason, null, null, null, null, null, null, null, expectedOrderVersion),
                OrderTransitionService.Internal.shipment(detail));
            case RETURNED -> {
                requireOrderInFlight(order, from, target);
                if (order.isCod() && !OrderStateMachine.Facts.of(order).codCollected() && order.getStatus() == OrderStatus.DISPATCHED) {
                    // COD never paid: the existing failed-delivery cancel (restock once, fulfillment RETURNED, no refund)
                    transitions.transition(order.getId(), actor, OrderTransitionService.Action.CANCEL,
                        new OrderDtos.TransitionRequest(reason, null, null, null, null, null, null, null, expectedOrderVersion),
                        OrderTransitionService.Internal.shipment(detail));
                } else {
                    // prepaid: parcel back at the shop; money + restock go through the refund flow (restock on REFUNDED)
                    shipment.setStatus(ShipmentStatus.RETURNED.name());
                    shipment.setReturnedAt(at);
                    if (shipment.getFailedAt() == null) shipment.setFailed(at, reason);
                    shipment.stamp(actor.userId());
                    timeline.record(shipment, ShipmentTrackingEvent.STATUS_CHANGED, from.name(), actor, reason, detail);
                    var meta = eventMeta(shipment, detail);
                    meta.put("refundRequired", order.getStatus() != OrderStatus.REFUND_REQUESTED);
                    ledger.orderEvent(order, actor, "SHIPMENT_RETURNED", reason, meta);
                    shipments.saveAndFlush(shipment);
                }
            }
            default -> throw new IllegalStateException("not an API target: " + target);
        }
    }

    /** In-flight shipment changes need the order out for delivery (DISPATCHED, or a refund request opened while DISPATCHED). */
    private static void requireOrderInFlight(Order order, ShipmentStatus from, ShipmentStatus to) {
        if (!orderInFlight(order)) {
            throw new ShipmentTransitionException("ORDER_NOT_DISPATCHED",
                "ออเดอร์ไม่ได้อยู่ระหว่างจัดส่ง (สถานะ " + order.getStatus() + ")", from, to, List.of());
        }
    }

    private static boolean orderInFlight(Order order) {
        return order.getStatus() == OrderStatus.DISPATCHED
            || (order.getStatus() == OrderStatus.REFUND_REQUESTED && OrderStatus.DISPATCHED.name().equals(order.getStatusBeforeRefund()));
    }

    private static boolean syncPossible(Order order, ShipmentStatus target) {
        return target == ShipmentStatus.DELIVERED ? order.getStatus() == OrderStatus.DISPATCHED : orderInFlight(order);
    }

    private static Map<String, Object> eventMeta(Shipment shipment, ShipmentTimeline.Detail detail) {
        var meta = new LinkedHashMap<String, Object>();
        meta.put("shipmentStatus", shipment.getStatus());
        if (detail.location() != null) meta.put("location", detail.location());
        if (detail.carrierStatusCode() != null) meta.put("carrierStatusCode", detail.carrierStatusCode());
        if (detail.source() != null) meta.put("source", detail.source());
        return meta;
    }

    private Order load(OrderActor actor, UUID orderId, boolean lock) {
        var found = lock ? orders.lockById(orderId) : orders.findById(orderId);
        var order = found.filter(actor::canSee).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "ไม่พบออเดอร์"));
        MerchantVisibility.requireAccepted(order, actor);
        return order;
    }

    private static void checkVersion(Shipment shipment, Integer expected) {
        if (expected != null && expected != shipment.getRowVersion()) {
            throw new AuthException(HttpStatus.CONFLICT, "SHIPMENT_VERSION_CONFLICT",
                "การจัดส่งถูกแก้ไขไปแล้ว กรุณาโหลดใหม่ (version ปัจจุบัน " + shipment.getRowVersion() + ")");
        }
    }

    private static void checkOrderVersion(Order order, Integer expected) {
        if (expected != null && expected != order.getRowVersion()) {
            throw new AuthException(HttpStatus.CONFLICT, "ORDER_VERSION_CONFLICT",
                "ออเดอร์ถูกแก้ไขไปแล้ว กรุณาโหลดใหม่ (version ปัจจุบัน " + order.getRowVersion() + ")");
        }
    }

    private static String reason(String raw, boolean required, List<FieldViolation> violations) {
        var reason = OrderTransitionService.clean(raw);
        if (required && reason == null) violations.add(new FieldViolation("reason", "REQUIRED", "กรุณาระบุเหตุผล"));
        if (reason != null && reason.length() > MAX_TEXT) violations.add(new FieldViolation("reason", "MAX_LENGTH", "เหตุผลยาวเกิน " + MAX_TEXT + " ตัวอักษร"));
        return reason;
    }

    private static ShipmentTimeline.Detail detail(ShipmentDtos.ShipmentUpdateRequest req, Order order, List<FieldViolation> violations) {
        var note = OrderTransitionService.clean(req.note());
        var location = OrderTransitionService.clean(req.location());
        var carrierStatus = OrderTransitionService.clean(req.carrierStatusCode());
        if (note != null && note.length() > MAX_TEXT) violations.add(new FieldViolation("note", "MAX_LENGTH", "หมายเหตุยาวเกิน " + MAX_TEXT + " ตัวอักษร"));
        if (location != null && location.length() > MAX_LOCATION) violations.add(new FieldViolation("location", "MAX_LENGTH", "สถานที่ยาวเกิน " + MAX_LOCATION + " ตัวอักษร"));
        if (carrierStatus != null && carrierStatus.length() > MAX_CARRIER_STATUS) {
            violations.add(new FieldViolation("carrierStatusCode", "MAX_LENGTH", "รหัสสถานะขนส่งยาวเกิน " + MAX_CARRIER_STATUS + " ตัวอักษร"));
        }
        var at = ShipmentTimeline.parseTime(req.eventAt(), "eventAt", order.getPlacedAt(), violations);
        return new ShipmentTimeline.Detail(null, note, location, at, carrierStatus, null, null);
    }

    ShipmentDtos.ShipmentDetail detail(Order order, Shipment s, OrderActor actor) {
        boolean staff = actor.type() != OrderActorType.BUYER;
        var carrier = s.getCarrierCode() == null ? null : carrierRepository.findById(s.getCarrierCode()).orElse(null);
        var timelineViews = events.findByShipmentIdOrderByEventAtAscIdAsc(s.getId()).stream().map(e -> new ShipmentDtos.TrackingEventView(
            e.getId(), e.getEventType(), e.getSource(), e.getActorType(),
            staff || OrderActorType.BUYER.name().equals(e.getActorType()) ? e.getActorUserId() : null,
            e.getFromStatus(), e.getNormalizedStatus(), e.getCarrierStatusCode(), e.getDescription(), e.getLocation(), e.getEventAt(),
            e.getCreatedDateTime())).toList();
        var allowed = staff ? ShipmentStateMachine.allowedTargets(s.statusEnum(), actor.type()) : List.<String>of();
        return new ShipmentDtos.ShipmentDetail(s.getId(), s.getOrderId(), order.getOrderNumber(), order.getStatus().name(), order.getRowVersion(),
            order.getPaymentMethod() == null ? null : order.getPaymentMethod().name(), s.getStatus(), s.getCarrierCode(),
            carrier == null ? null : carrier.getNameTh(), s.getTrackingNumber(), carrier == null ? null : carrier.trackingUrl(s.getTrackingNumber()),
            s.getPackedAt(), s.getDispatchedAt(), s.getPickedUpAt(), s.getDeliveredAt(), s.getFailedAt(), s.getFailureReason(), s.getReturnedAt(),
            s.getLastTrackingEventAt(), staff ? s.getPackedBy() : null, staff ? s.getDispatchedBy() : null, s.getRowVersion(), allowed, timelineViews);
    }

    private static LocalDate date(String raw, String field, List<FieldViolation> violations) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            violations.add(new FieldViolation(field, "INVALID", field + " ต้องเป็นวันที่ yyyy-MM-dd"));
            return null;
        }
    }

    private static String cut(String value, int max) {
        var clean = OrderTransitionService.clean(value);
        return clean == null || clean.length() <= max ? clean : clean.substring(0, max);
    }

    static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND", "ยังไม่มีข้อมูลการจัดส่งของออเดอร์นี้");
    }
}
