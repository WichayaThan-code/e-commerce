package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** V1 {@code inventory_reservations}: one row per order line. ACTIVE → PAID_HOLD → COMMITTED, or → RELEASED. */
@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation extends AuditedEntity {
    public static final String ACTIVE = "ACTIVE";
    public static final String PAID_HOLD = "PAID_HOLD";
    public static final String COMMITTED = "COMMITTED";
    public static final String RELEASED = "RELEASED";

    @Id private UUID id;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "sku_id", nullable = false) private UUID skuId;
    @Column(name = "order_id") private UUID orderId;
    @Column(name = "order_item_id") private UUID orderItemId;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false, length = 30) private String status = ACTIVE;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "reserved_at", nullable = false) private Instant reservedAt;
    @Column(name = "paid_hold_at") private Instant paidHoldAt;
    @Column(name = "committed_at") private Instant committedAt;
    @Column(name = "released_at") private Instant releasedAt;

    protected InventoryReservation() {}

    public InventoryReservation(UUID shopId, UUID skuId, UUID orderId, UUID orderItemId, int quantity, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.skuId = skuId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.reservedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public UUID getSkuId() { return skuId; }
    public UUID getOrderId() { return orderId; }
    public UUID getOrderItemId() { return orderItemId; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isHeld() { return ACTIVE.equals(status) || PAID_HOLD.equals(status); }

    public void paidHold(Instant at) { this.status = PAID_HOLD; this.paidHoldAt = at; }
    public void commit(Instant at) { this.status = COMMITTED; this.committedAt = at; }
    public void release(Instant at) { this.status = RELEASED; this.releasedAt = at; }
}
