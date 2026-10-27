package com.thaishopfun.catalog.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** V41 {@code shop_collection_products}: one ordered product of a collection (PK collection_id + product_id). */
@Entity
@Table(name = "shop_collection_products")
public class ShopCollectionProduct {
    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "collection_id", nullable = false)
        private UUID collectionId;
        @Column(name = "product_id", nullable = false)
        private UUID productId;

        protected Key() {}

        public Key(UUID collectionId, UUID productId) {
            this.collectionId = collectionId;
            this.productId = productId;
        }

        public UUID getCollectionId() { return collectionId; }
        public UUID getProductId() { return productId; }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(collectionId, key.collectionId) && Objects.equals(productId, key.productId);
        }

        @Override
        public int hashCode() { return Objects.hash(collectionId, productId); }
    }

    @EmbeddedId
    private Key id;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_date_time", nullable = false, updatable = false)
    private Instant createdDateTime;

    protected ShopCollectionProduct() {}

    public ShopCollectionProduct(UUID collectionId, UUID productId, int sortOrder) {
        this.id = new Key(collectionId, productId);
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void onCreate() {
        if (createdDateTime == null) createdDateTime = Instant.now();
    }

    public void moveTo(int sortOrder) { this.sortOrder = sortOrder; }

    public Key getId() { return id; }
    public UUID getProductId() { return id.getProductId(); }
    public int getSortOrder() { return sortOrder; }
}
