package com.thaishopfun.cart;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** V1 {@code cart_items}: UNIQUE(cart_id, sku_id) for normal lines (adding the same SKU again increments the line); V43: configured lines are unique per (cart_id, sku_id, config_hash). */
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
    /** V43: configurator snapshot (null = normal line). */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) @Column(name = "configuration") private java.util.Map<String, Object> configuration;
    /** V43: configurator hash (null = normal line); part of the line identity. */
    @Column(name = "config_hash", length = 64) private String configHash;

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
    public java.util.Map<String, Object> getConfiguration() { return configuration; }
    public void setConfiguration(java.util.Map<String, Object> configuration) { this.configuration = configuration; }
    public String getConfigHash() { return configHash; }
    public void setConfigHash(String configHash) { this.configHash = configHash; }
}
