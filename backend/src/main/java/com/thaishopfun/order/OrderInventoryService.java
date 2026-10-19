package com.thaishopfun.order;

import com.thaishopfun.cart.CartInventoryRepository;
import com.thaishopfun.catalog.InventoryLedgerEntry;
import com.thaishopfun.catalog.InventoryLedgerRepository;
import com.thaishopfun.catalog.InventoryLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stock movements of an order, each written once to inventory_ledger (idempotency key order-item + entry type)
 * and guarded by the reservation status:
 * <ul>
 *   <li>checkout: RESERVE (available -= q, reserved += q), reservation ACTIVE</li>
 *   <li>paid: reservation PAID_HOLD (no stock change)</li>
 *   <li>dispatched: COMMIT_SALE (on_hand -= q, reserved -= q), reservation COMMITTED</li>
 *   <li>cancel / refund before dispatch: RELEASE (reserved -= q, available += q), reservation RELEASED</li>
 *   <li>refund after dispatch with restock: RESTOCK_REFUND (on_hand += q)</li>
 * </ul>
 * ledger.quantity_delta = change of on_hand (0 for RESERVE / RELEASE; the reserved change is in reserved_qty_before/after).
 */
@Service
public class OrderInventoryService {
    private final CartInventoryRepository levels;
    private final InventoryLedgerRepository ledger;
    private final InventoryReservationRepository reservations;

    public OrderInventoryService(CartInventoryRepository levels, InventoryLedgerRepository ledger,
                                 InventoryReservationRepository reservations) {
        this.levels = levels;
        this.ledger = ledger;
        this.reservations = reservations;
    }

    /** Called inside checkout with the inventory rows already locked. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Order order, OrderItem item, InventoryLevel level, Instant expiresAt, UUID actorId) {
        int before = level.getReservedQty();
        int onHand = level.getOnHand();
        level.reserve(item.getQuantity());
        var reservation = reservations.save(new InventoryReservation(order.getShopId(), item.getSkuId(), order.getId(), item.getId(), item.getQuantity(), expiresAt));
        write(level, "RESERVE", 0, onHand, onHand, item, order, reservation.getId(), before, level.getReservedQty(), "BUYER", actorId,
            "จองสต็อกสำหรับออเดอร์ " + order.getOrderNumber());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markPaid(Order order) {
        var now = Instant.now();
        for (var reservation : reservations.findByOrderIdOrderBySkuIdAsc(order.getId())) {
            if (InventoryReservation.ACTIVE.equals(reservation.getStatus())) reservation.paidHold(now);
        }
    }

    /** Dispatch: held → sold. Safe to call twice (only held reservations move). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commit(Order order, OrderActorType actorType, UUID actorId) {
        var now = Instant.now();
        var list = reservations.findByOrderIdOrderBySkuIdAsc(order.getId());
        var locked = lock(list.stream().map(InventoryReservation::getSkuId).toList());
        for (var reservation : list) {
            if (!reservation.isHeld()) continue;
            var level = locked.get(reservation.getSkuId());
            if (level == null) continue;
            int before = level.getReservedQty();
            int onHand = level.getOnHand();
            level.commitReserved(reservation.getQuantity());
            reservation.commit(now);
            write(level, "COMMIT_SALE", -reservation.getQuantity(), onHand, level.getOnHand(), item(order, reservation), order,
                reservation.getId(), before, level.getReservedQty(), actorType.name(), actorId, "ส่งสินค้าออเดอร์ " + order.getOrderNumber());
        }
        order.setStockCommitted(true);
    }

    /** Cancel / refund before dispatch: give held stock back. Safe to call twice. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int release(Order order, OrderActorType actorType, UUID actorId, String reason) {
        var now = Instant.now();
        var list = reservations.findByOrderIdOrderBySkuIdAsc(order.getId());
        var locked = lock(list.stream().map(InventoryReservation::getSkuId).toList());
        int released = 0;
        for (var reservation : list) {
            if (!reservation.isHeld()) continue;
            var level = locked.get(reservation.getSkuId());
            reservation.release(now);
            if (level == null) continue;
            int before = level.getReservedQty();
            int onHand = level.getOnHand();
            level.releaseReserved(reservation.getQuantity());
            write(level, "RELEASE", 0, onHand, onHand, item(order, reservation), order, reservation.getId(), before,
                level.getReservedQty(), actorType.name(), actorId, reason == null ? "คืนสต็อกที่จองไว้" : reason);
            released += reservation.getQuantity();
        }
        return released;
    }

    /** Refund after dispatch: put the sold quantity back on hand. Only COMMITTED reservations, once per line (ledger key). */
    @Transactional(propagation = Propagation.MANDATORY)
    public int restockAfterRefund(Order order, OrderActorType actorType, UUID actorId, String reason) {
        var list = reservations.findByOrderIdOrderBySkuIdAsc(order.getId());
        var locked = lock(list.stream().map(InventoryReservation::getSkuId).toList());
        int restocked = 0;
        for (var reservation : list) {
            if (!InventoryReservation.COMMITTED.equals(reservation.getStatus())) continue;
            var key = key(reservation.getOrderItemId(), "RESTOCK_REFUND");
            if (ledger.findByIdempotencyKey(key).isPresent()) continue;
            var level = locked.get(reservation.getSkuId());
            if (level == null) continue;
            int onHand = level.getOnHand();
            level.setOnHand(onHand + reservation.getQuantity());
            write(level, "RESTOCK_REFUND", reservation.getQuantity(), onHand, level.getOnHand(), item(order, reservation), order,
                reservation.getId(), level.getReservedQty(), level.getReservedQty(), actorType.name(), actorId,
                reason == null ? "คืนสินค้าเข้าสต็อกหลังคืนเงิน" : reason);
            restocked += reservation.getQuantity();
        }
        return restocked;
    }

    private Map<UUID, InventoryLevel> lock(java.util.List<UUID> skuIds) {
        if (skuIds.isEmpty()) return Map.of();
        return levels.lockForCheckout(skuIds.stream().distinct().toList()).stream()
            .collect(Collectors.toMap(level -> level.getSku().getId(), Function.identity()));
    }

    private static OrderItem item(Order order, InventoryReservation reservation) {
        return order.getItems().stream().filter(i -> i.getId().equals(reservation.getOrderItemId())).findFirst().orElse(null);
    }

    static String key(UUID orderItemId, String type) {
        return "order-item:" + orderItemId + ":" + type;
    }

    private void write(InventoryLevel level, String type, int delta, int onHandBefore, int onHandAfter, OrderItem item, Order order,
                       UUID reservationId, int reservedBefore, int reservedAfter, String actorType, UUID actorId, String reason) {
        var sku = level.getSku();
        UUID itemId = item == null ? null : item.getId();
        var entry = new InventoryLedgerEntry(sku, type, delta, onHandBefore, onHandAfter, reservedBefore, reservedAfter,
            truncate(reason, 500), itemId == null ? null : key(itemId, type), actorId)
            .forOrder(order.getId().toString(), itemId, reservationId, reservedBefore, reservedAfter, actorType);
        ledger.save(entry);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
