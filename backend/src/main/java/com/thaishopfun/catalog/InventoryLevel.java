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
    @Column(name = "safety_stock", nullable = false)
    private int safetyStock;
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
        this.safetyStock = 0;
        this.available = 0;
    }

    public UUID getId() { return id; }
    public Shop getShop() { return shop; }
    public ProductSku getSku() { return sku; }
    public int getOnHand() { return onHand; }
    public void setOnHand(int onHand) {
        this.onHand = onHand;
        refreshAvailable();
    }
    public int getReservedQty() { return reservedQty; }
    public void setReservedQty(int reservedQty) {
        this.reservedQty = reservedQty;
        refreshAvailable();
    }

    /** Order checkout: hold stock for an order line (available -= qty, reserved += qty). */
    public void reserve(int qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty must be positive");
        if (qty > getAvailable()) throw new IllegalStateException("insufficient available stock");
        this.reservedQty += qty;
        refreshAvailable();
    }

    /** Order cancelled / refunded before dispatch: give a held quantity back (reserved -= qty, available += qty). */
    public void releaseReserved(int qty) {
        if (qty <= 0 || qty > reservedQty) throw new IllegalStateException("release exceeds reserved stock");
        this.reservedQty -= qty;
        refreshAvailable();
    }

    /** Order dispatched: the held quantity leaves the warehouse (on_hand -= qty, reserved -= qty; available unchanged). */
    public void commitReserved(int qty) {
        if (qty <= 0 || qty > reservedQty || qty > onHand) throw new IllegalStateException("commit exceeds reserved stock");
        this.reservedQty -= qty;
        this.onHand -= qty;
        refreshAvailable();
    }
    public int getSafetyStock() { return safetyStock; }
    public void setSafetyStock(int safetyStock) {
        this.safetyStock = safetyStock;
        refreshAvailable();
    }
    public int getAvailable() { return Math.max(0, onHand - reservedQty - safetyStock); }
    public Integer getLowStockThreshold() { return lowStockThreshold; }

    /**
     * Threshold for “ใกล้หมด” / LOW_STOCK: explicit low_stock_threshold, else Safety Stock when set, else 5.
     * Merchants configure Safety Stock in the product form; low_stock_threshold is often null.
     */
    public int effectiveLowStockThreshold() {
        if (lowStockThreshold != null) return lowStockThreshold;
        if (safetyStock > 0) return safetyStock;
        return 5;
    }

    /** Sellable units remain, strictly below {@link #effectiveLowStockThreshold()} (Safety Stock). */
    public boolean isLowStock() {
        int available = getAvailable();
        return available > 0 && available < effectiveLowStockThreshold();
    }

    private void refreshAvailable() {
        this.available = Math.max(0, onHand - reservedQty - safetyStock);
    }
    public int getVersion() { return version; }
}
