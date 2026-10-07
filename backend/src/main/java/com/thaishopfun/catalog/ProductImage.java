package com.thaishopfun.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(name = "product_images")
public class ProductImage extends AuditedEntity {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;
    @Column(name = "storage_path", nullable = false, length = 700)
    private String storagePath;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(name = "is_primary", nullable = false)
    private boolean primaryImage;
    @Column(name = "alt_text", length = 255)
    private String altText;

    protected ProductImage() {}

    public ProductImage(Product product, String storagePath, int sortOrder, boolean primaryImage) {
        this.id = UUID.randomUUID();
        this.product = product;
        this.storagePath = storagePath;
        this.sortOrder = sortOrder;
        this.primaryImage = primaryImage;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public String getStoragePath() { return storagePath; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isPrimaryImage() { return primaryImage; }
    public void setPrimaryImage(boolean primaryImage) { this.primaryImage = primaryImage; }
    public String getAltText() { return altText; }
}
