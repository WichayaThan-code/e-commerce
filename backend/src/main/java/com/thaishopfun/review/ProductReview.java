package com.thaishopfun.review;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

/** Maps the V1 {@code product_reviews} table (column {@code body} = review comment). */
@Entity
@Table(name = "product_reviews", uniqueConstraints = @UniqueConstraint(name = "uq_product_reviews_order_item_id", columnNames = "order_item_id"))
public class ProductReview {
    public static final String PUBLISHED = "PUBLISHED";
    public static final String HIDDEN = "HIDDEN";

    @Id private UUID id;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "order_id") private UUID orderId;
    @Column(name = "order_item_id") private UUID orderItemId;
    @Column(name = "buyer_user_id", nullable = false) private UUID buyerUserId;
    @Column(nullable = false) private short rating;
    @Column(length = 150) private String title;
    @Column(columnDefinition = "text") private String body;
    @ColumnDefault("'PUBLISHED'") @Column(nullable = false, length = 30) private String status = PUBLISHED;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;
    @Column(name = "modified_by") private UUID modifiedBy;

    protected ProductReview() {}

    public ProductReview(UUID shopId, UUID productId, UUID orderId, UUID orderItemId, UUID buyerUserId, int rating, String body) {
        var now = Instant.now();
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.productId = productId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.buyerUserId = buyerUserId;
        this.rating = (short) rating;
        this.body = body;
        this.createdDateTime = now;
        this.modifiedDateTime = now;
        this.createdBy = buyerUserId;
        this.modifiedBy = buyerUserId;
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public UUID getProductId() { return productId; }
    public UUID getOrderItemId() { return orderItemId; }
    public UUID getBuyerUserId() { return buyerUserId; }
    public int getRating() { return rating; }
    public String getBody() { return body; }
    public String getStatus() { return status; }
    public Instant getCreatedDateTime() { return createdDateTime; }
}
