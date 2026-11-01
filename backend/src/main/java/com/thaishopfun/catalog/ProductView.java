package com.thaishopfun.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One product page view (V44 product_views). Viewer is a user id or a hashed anonymous session, never a raw session id. */
@Entity
@Table(name = "product_views")
public class ProductView {
    @Id private UUID id;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "session_hash", length = 64) private String sessionHash;
    @Column(name = "viewed_at", nullable = false) private Instant viewedAt;

    protected ProductView() {}

    public ProductView(UUID productId, UUID userId, String sessionHash, Instant viewedAt) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.userId = userId;
        this.sessionHash = sessionHash;
        this.viewedAt = viewedAt;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public UUID getUserId() { return userId; }
    public String getSessionHash() { return sessionHash; }
    public Instant getViewedAt() { return viewedAt; }
}
