package com.thaishopfun.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps {@code plans} (V47). Catalog of shop subscription packages; {@code shops.plan_code} references {@link #code}.
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
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShopPlan() {}

    public ShopPlan(String code, String name, Long limitProducts, Long limitOrdersPerMonth, Long limitStaff,
                    Long limitStorageBytes) {
        var now = Instant.now();
        this.code = code;
        this.name = name;
        this.limitProducts = limitProducts;
        this.limitOrdersPerMonth = limitOrdersPerMonth;
        this.limitStaff = limitStaff;
        this.limitStorageBytes = limitStorageBytes;
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
    public boolean isActive() { return active; }
}
