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
        refreshAvailable();
    }
    public int getReservedQty() { return reservedQty; }
    public void setReservedQty(int reservedQty) {
        this.reservedQty = reservedQty;
        refreshAvailable();
    }
    public int getAvailable() {
        return Math.max(0, onHand - reservedQty - safetyQty());
    }
    public Integer getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(Integer lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
        refreshAvailable();
    }
    public int getVersion() { return version; }

    private int safetyQty() {
        return lowStockThreshold == null ? 0 : Math.max(0, lowStockThreshold);
    }

    private void refreshAvailable() {
        this.available = getAvailable();
    }
}
