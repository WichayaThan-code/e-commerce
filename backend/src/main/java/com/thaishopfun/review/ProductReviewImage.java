package com.thaishopfun.review;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Maps {@code product_review_images} — one public REVIEW_IMAGE media file on a product review. */
@Entity
@Table(name = "product_review_images",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_product_review_images_review_media", columnNames = {"review_id", "media_id"}),
        @UniqueConstraint(name = "uq_product_review_images_media", columnNames = "media_id")
    })
public class ProductReviewImage {
    @Id private UUID id;
    @Column(name = "review_id", nullable = false) private UUID reviewId;
    @Column(name = "media_id", nullable = false) private UUID mediaId;
    @Column(name = "sort_order", nullable = false) private short sortOrder;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;

    protected ProductReviewImage() {}

    public ProductReviewImage(UUID reviewId, UUID mediaId, int sortOrder) {
        this.id = UUID.randomUUID();
        this.reviewId = reviewId;
        this.mediaId = mediaId;
        this.sortOrder = (short) sortOrder;
        this.createdDateTime = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getReviewId() { return reviewId; }
    public UUID getMediaId() { return mediaId; }
    public int getSortOrder() { return sortOrder; }
}
