package com.thaishopfun.review;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReviewDtos {
    private ReviewDtos() {}

    /** POST /api/me/reviews body. */
    public record CreateReviewRequest(UUID orderItemId, Integer rating, String comment) {}

    /** Public review row: masked reviewer name only (never user id, email or phone). */
    public record ReviewView(UUID id, int rating, String comment, String reviewerName, Instant createdAt) {}

    public record ReviewPage(List<ReviewView> items, int page, int size, long total, BigDecimal ratingAvg, long reviewCount) {}

    public record Aggregate(BigDecimal ratingAvg, long reviewCount) {}
}
