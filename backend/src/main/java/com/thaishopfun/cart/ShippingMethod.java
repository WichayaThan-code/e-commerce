package com.thaishopfun.cart;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * V1 {@code shipping_methods}: a shop's shipping rule. fee_type FLAT (fee_amount), FREE (0) or WEIGHT
 * (fee_amount for the first 1 kg + extra_fee_per_kg for each started kg above, V24). min_order_amount is the
 * free-shipping threshold (selected subtotal at or above it ships free).
 */
@Entity
@Table(name = "shipping_methods")
public class ShippingMethod extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "carrier_code", length = 50) private String carrierCode;
    @Column(name = "fee_type", nullable = false, length = 30) private String feeType;
    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 2) private BigDecimal feeAmount;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 3) private String currency;
    @Column(name = "min_order_amount", precision = 18, scale = 2) private BigDecimal minOrderAmount;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "estimated_days_min") private Integer estimatedDaysMin;
    @Column(name = "estimated_days_max") private Integer estimatedDaysMax;
    @Column(name = "extra_fee_per_kg", precision = 18, scale = 2) private BigDecimal extraFeePerKg;

    protected ShippingMethod() {}

    public ShippingMethod(UUID shopId) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.currency = "THB";
        this.feeType = "FLAT";
        this.feeAmount = BigDecimal.ZERO;
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCarrierCode() { return carrierCode; }
    public void setCarrierCode(String carrierCode) { this.carrierCode = carrierCode; }
    public String getFeeType() { return feeType; }
    public void setFeeType(String feeType) { this.feeType = feeType; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }
    public String getCurrency() { return currency; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Integer getEstimatedDaysMin() { return estimatedDaysMin; }
    public void setEstimatedDaysMin(Integer estimatedDaysMin) { this.estimatedDaysMin = estimatedDaysMin; }
    public Integer getEstimatedDaysMax() { return estimatedDaysMax; }
    public void setEstimatedDaysMax(Integer estimatedDaysMax) { this.estimatedDaysMax = estimatedDaysMax; }
    public BigDecimal getExtraFeePerKg() { return extraFeePerKg; }
    public void setExtraFeePerKg(BigDecimal extraFeePerKg) { this.extraFeePerKg = extraFeePerKg; }
}
