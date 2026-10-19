package com.thaishopfun.cart;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** V1 {@code cart_items}: UNIQUE(cart_id, sku_id), so adding the same SKU again increments the line. */
@Entity
@Table(name = "cart_items")
public class CartItem extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "cart_id", nullable = false) private UUID cartId;
    @Column(name = "sku_id", nullable = false) private UUID skuId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(nullable = false) private int quantity;
    /** Unit price when the line was added (or when the buyer last accepted a price change). */
    @Column(name = "unit_price_snapshot", precision = 18, scale = 2) private BigDecimal unitPriceSnapshot;
    @Column(name = "is_selected", nullable = false) private boolean selected = true;

    protected CartItem() {}

    public CartItem(UUID cartId, UUID skuId, UUID productId, int quantity, BigDecimal unitPriceSnapshot) {
        this.id = UUID.randomUUID();
        this.cartId = cartId;
        this.skuId = skuId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.selected = true;
    }

    public UUID getId() { return id; }
    public UUID getCartId() { return cartId; }
    public void setCartId(UUID cartId) { this.cartId = cartId; }
    public UUID getSkuId() { return skuId; }
    public void setSkuId(UUID skuId) { this.skuId = skuId; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public void setUnitPriceSnapshot(BigDecimal unitPriceSnapshot) { this.unitPriceSnapshot = unitPriceSnapshot; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}
