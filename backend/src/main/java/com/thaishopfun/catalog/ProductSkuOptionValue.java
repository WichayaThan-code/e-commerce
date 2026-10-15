package com.thaishopfun.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

/** Variant (SKU) to option value mapping: exactly one row per option group for each variant. */
@Entity
@Table(name = "product_sku_option_values", uniqueConstraints = @UniqueConstraint(name = "uq_product_sku_option_values_group", columnNames = {"sku_id", "option_group_id"}))
public class ProductSkuOptionValue extends AuditedEntity {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProductSku sku;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_group_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProductOptionGroup group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_value_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProductOptionValue value;

    protected ProductSkuOptionValue() {}

    public ProductSkuOptionValue(ProductSku sku, ProductOptionValue value) {
        this.id = UUID.randomUUID();
        this.sku = sku;
        this.group = value.getGroup();
        this.value = value;
    }

    public UUID getId() { return id; }
    public ProductSku getSku() { return sku; }
    public ProductOptionGroup getGroup() { return group; }
    public ProductOptionValue getValue() { return value; }
}
