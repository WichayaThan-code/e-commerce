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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_skus", uniqueConstraints = @UniqueConstraint(name = "uq_product_skus_shop_code", columnNames = {"shop_id", "sku_code"}))
public class ProductSku extends AuditedEntity {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Shop shop;
    @Column(name = "sku_code", nullable = false, length = 64)
    private String skuCode;
    @Column(length = 64)
    private String barcode;
    @Column(name = "option_label", length = 255)
    private String optionLabel;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_values", nullable = false)
    private String optionValues;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;
    @Column(name = "cost_price", precision = 18, scale = 2)
    private BigDecimal costPrice;
    @Column(name = "compare_at_price", precision = 18, scale = 2)
    private BigDecimal compareAtPrice;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "weight_grams")
    private Integer weightGrams;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ProductSku() {}

    public ProductSku(Product product, String skuCode, BigDecimal price) {
        this.id = UUID.randomUUID();
        this.product = product;
        this.shop = product.getShop();
        this.skuCode = skuCode;
        this.price = price;
        this.optionValues = "{}";
        this.currency = "THB";
        this.active = true;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public Shop getShop() { return shop; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getOptionLabel() { return optionLabel; }
    public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }
    public String getOptionValues() { return optionValues; }
    public void setOptionValues(String optionValues) { this.optionValues = optionValues; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public BigDecimal getCompareAtPrice() { return compareAtPrice; }
    public void setCompareAtPrice(BigDecimal compareAtPrice) { this.compareAtPrice = compareAtPrice; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Integer getWeightGrams() { return weightGrams; }
    public void setWeightGrams(Integer weightGrams) { this.weightGrams = weightGrams; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
