package com.thaishopfun.catalog;

import com.thaishopfun.merchant.Shop;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(name = "inventory_levels", uniqueConstraints = @UniqueConstraint(name = "uq_inventory_levels_sku", columnNames = "sku_id"))
public class InventoryLevel extends AuditedEntity {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Shop shop;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProductSku sku;
    @Column(name = "on_hand", nullable = false)
    private int onHand;
    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;
    @Column(nullable = false)
    private int available;
    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;
    @Version
    @Column(nullable = false)
    private int version;

    protected InventoryLevel() {}

    public InventoryLevel(ProductSku sku) {
        this.id = UUID.randomUUID();
        this.sku = sku;
        this.shop = sku.getShop();
        this.onHand = 0;
        this.reservedQty = 0;
        this.available = 0;
    }

    public UUID getId() { return id; }
    public Shop getShop() { return shop; }
    public ProductSku getSku() { return sku; }
    public int getOnHand() { return onHand; }
    public void setOnHand(int onHand) {
        this.onHand = onHand;
        this.available = onHand - reservedQty;
    }
    public int getReservedQty() { return reservedQty; }

    /** Order checkout: hold stock for an order line (available -= qty, reserved += qty). */
    public void reserve(int qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty must be positive");
        if (qty > available) throw new IllegalStateException("insufficient available stock");
        this.reservedQty += qty;
        this.available = onHand - reservedQty;
    }

    /** Order cancelled / refunded before dispatch: give a held quantity back (reserved -= qty, available += qty). */
    public void releaseReserved(int qty) {
        if (qty <= 0 || qty > reservedQty) throw new IllegalStateException("release exceeds reserved stock");
        this.reservedQty -= qty;
        this.available = onHand - reservedQty;
    }

    /** Order dispatched: the held quantity leaves the warehouse (on_hand -= qty, reserved -= qty; available unchanged). */
    public void commitReserved(int qty) {
        if (qty <= 0 || qty > reservedQty || qty > onHand) throw new IllegalStateException("commit exceeds reserved stock");
        this.reservedQty -= qty;
        this.onHand -= qty;
        this.available = onHand - reservedQty;
    }
    public int getAvailable() { return available; }
    public Integer getLowStockThreshold() { return lowStockThreshold; }
    public int getVersion() { return version; }
}
