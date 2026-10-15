package com.thaishopfun.catalog;

import jakarta.persistence.Column;
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

/** One value of an option group, e.g. "Black" in "Color". */
@Entity
@Table(name = "product_option_values", uniqueConstraints = @UniqueConstraint(name = "uq_product_option_values_value", columnNames = {"option_group_id", "option_value"}))
public class ProductOptionValue extends AuditedEntity {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_group_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProductOptionGroup group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;
    @Column(name = "option_value", nullable = false, length = 50)
    private String value;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProductOptionValue() {}

    public ProductOptionValue(ProductOptionGroup group, String value, int sortOrder) {
        this.id = UUID.randomUUID();
        this.group = group;
        this.product = group.getProduct();
        this.value = value;
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public ProductOptionGroup getGroup() { return group; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
