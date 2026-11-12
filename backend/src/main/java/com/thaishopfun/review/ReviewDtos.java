package com.thaishopfun.review;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReviewDtos {
    private ReviewDtos() {}

    /** POST /api/me/reviews body. {@code imageIds} optional, max 5 previously uploaded REVIEW_IMAGE media. */
    public record CreateReviewRequest(UUID orderItemId, Integer rating, String comment, List<UUID> imageIds) {}

    public record ReviewImageView(UUID id, String url) {}

    /** Public review row: masked reviewer name only (never user id, email or phone). */
    public record ReviewView(
        UUID id,
        int rating,
        String comment,
        String reviewerName,
        Instant createdAt,
        String optionLabel,
        List<ReviewImageView> images
    ) {}

    /** Chip counts for storefront filters (overall published reviews of the product). */
    public record ReviewFilterCounts(
        long all,
        long withPhotos,
        long star1,
        long star2,
        long star3,
        long star4,
        long star5
    ) {}

    public record ReviewPage(
        List<ReviewView> items,
        int page,
        int size,
        long total,
        BigDecimal ratingAvg,
        long reviewCount,
        ReviewFilterCounts filters
    ) {}

    public record Aggregate(BigDecimal ratingAvg, long reviewCount) {}

    /** GET /api/me/reviews/status?orderId= — order item ids the buyer already reviewed. */
    public record ReviewStatusResponse(UUID orderId, List<UUID> reviewedOrderItemIds) {}

    public record ReviewImageUpload(UUID id, String url) {}
}
