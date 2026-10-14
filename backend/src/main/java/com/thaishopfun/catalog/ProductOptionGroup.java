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

/** Option group of a variant product, e.g. "Color" or "Size" (1-3 per product). */
@Entity
@Table(name = "product_option_groups", uniqueConstraints = @UniqueConstraint(name = "uq_product_option_groups_name", columnNames = {"product_id", "name"}))
public class ProductOptionGroup extends AuditedEntity {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProductOptionGroup() {}

    public ProductOptionGroup(Product product, String name, int sortOrder) {
        this.id = UUID.randomUUID();
        this.product = product;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
