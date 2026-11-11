package com.thaishopfun.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Maps {@code plans} (V47 + V62). Catalog of shop subscription packages; {@code shops.plan_code} references {@link #code}.
 * Null limit columns mean unlimited.
 */
@Entity
@Table(name = "plans")
public class ShopPlan {
    @Id
    @Column(length = 50)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "limit_products")
    private Long limitProducts;
    @Column(name = "limit_orders_per_month")
    private Long limitOrdersPerMonth;
    @Column(name = "limit_staff")
    private Long limitStaff;
    @Column(name = "limit_storage_bytes")
    private Long limitStorageBytes;
    @Column(name = "price_monthly", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceMonthly = BigDecimal.ZERO;
    @Column(name = "price_yearly", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceYearly = BigDecimal.ZERO;
    @Column(length = 255)
    private String description;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShopPlan() {}

    public ShopPlan(String code, String name, Long limitProducts, Long limitOrdersPerMonth, Long limitStaff,
                    Long limitStorageBytes) {
        this(code, name, limitProducts, limitOrdersPerMonth, limitStaff, limitStorageBytes,
            BigDecimal.ZERO, BigDecimal.ZERO, null, 0);
    }

    public ShopPlan(String code, String name, Long limitProducts, Long limitOrdersPerMonth, Long limitStaff,
                    Long limitStorageBytes, BigDecimal priceMonthly, BigDecimal priceYearly,
                    String description, int sortOrder) {
        var now = Instant.now();
        this.code = code;
        this.name = name;
        this.limitProducts = limitProducts;
        this.limitOrdersPerMonth = limitOrdersPerMonth;
        this.limitStaff = limitStaff;
        this.limitStorageBytes = limitStorageBytes;
        this.priceMonthly = priceMonthly == null ? BigDecimal.ZERO : priceMonthly;
        this.priceYearly = priceYearly == null ? BigDecimal.ZERO : priceYearly;
        this.description = description;
        this.sortOrder = sortOrder;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public Long getLimitProducts() { return limitProducts; }
    public Long getLimitOrdersPerMonth() { return limitOrdersPerMonth; }
    public Long getLimitStaff() { return limitStaff; }
    public Long getLimitStorageBytes() { return limitStorageBytes; }
    public BigDecimal getPriceMonthly() { return priceMonthly; }
    public BigDecimal getPriceYearly() { return priceYearly; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
}
