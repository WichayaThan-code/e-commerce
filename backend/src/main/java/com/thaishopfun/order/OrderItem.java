package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem extends AuditedEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "product_id") private UUID productId;
    @Column(name = "sku_id") private UUID skuId;
    @Column(name = "sku_code_snapshot", nullable = false, length = 64) private String skuCodeSnapshot;
    @Column(name = "product_name_snapshot", nullable = false, length = 255) private String productNameSnapshot;
    @Column(name = "option_label_snapshot", length = 255) private String optionLabelSnapshot;
    @Column(name = "barcode_snapshot", length = 64) private String barcodeSnapshot;
    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2) private BigDecimal unitPrice;
    @Column(name = "compare_at_price_snapshot", precision = 18, scale = 2) private BigDecimal compareAtPriceSnapshot;
    @Column(name = "cost_snapshot", precision = 18, scale = 2) private BigDecimal costSnapshot;
    @Column(nullable = false) private int quantity;
    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "line_total", nullable = false, precision = 18, scale = 2) private BigDecimal lineTotal;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 3) private String currency = "THB";
    @Column(name = "image_url_snapshot", length = 700) private String imageUrlSnapshot;
    /** V43: configurator snapshot copied from the cart line (null = normal item). */
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "configuration") private java.util.Map<String, Object> configuration;
    @Column(name = "config_hash", length = 64) private String configHash;

    protected OrderItem() {}

    public OrderItem(UUID shopId) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
    }

    void setOrder(Order order) { this.order = order; }
    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public UUID getShopId() { return shopId; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public UUID getSkuId() { return skuId; }
    public void setSkuId(UUID skuId) { this.skuId = skuId; }
    public String getSkuCodeSnapshot() { return skuCodeSnapshot; }
    public void setSkuCodeSnapshot(String skuCodeSnapshot) { this.skuCodeSnapshot = skuCodeSnapshot; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public void setProductNameSnapshot(String productNameSnapshot) { this.productNameSnapshot = productNameSnapshot; }
    public java.util.Map<String, Object> getConfiguration() { return configuration; }
    public void setConfiguration(java.util.Map<String, Object> configuration) { this.configuration = configuration; }
    public String getConfigHash() { return configHash; }
    public void setConfigHash(String configHash) { this.configHash = configHash; }
    public String getOptionLabelSnapshot() { return optionLabelSnapshot; }
    public void setOptionLabelSnapshot(String optionLabelSnapshot) { this.optionLabelSnapshot = optionLabelSnapshot; }
    public String getBarcodeSnapshot() { return barcodeSnapshot; }
    public void setBarcodeSnapshot(String barcodeSnapshot) { this.barcodeSnapshot = barcodeSnapshot; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getCompareAtPriceSnapshot() { return compareAtPriceSnapshot; }
    public void setCompareAtPriceSnapshot(BigDecimal compareAtPriceSnapshot) { this.compareAtPriceSnapshot = compareAtPriceSnapshot; }
    public BigDecimal getCostSnapshot() { return costSnapshot; }
    public void setCostSnapshot(BigDecimal costSnapshot) { this.costSnapshot = costSnapshot; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
    public String getCurrency() { return currency; }
    public String getImageUrlSnapshot() { return imageUrlSnapshot; }
    public void setImageUrlSnapshot(String imageUrlSnapshot) { this.imageUrlSnapshot = imageUrlSnapshot; }
}
