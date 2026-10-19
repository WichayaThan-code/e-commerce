package com.thaishopfun.promotion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PromotionDtos {
    private PromotionDtos() {}

    /**
     * Create/update body. Dates are ISO-8601: with an offset ("2026-11-11T00:00:00+07:00") or local
     * ("2026-11-11T00:00", read as Asia/Bangkok). FREE_SHIPPING ignores {@code value} (stored as 0).
     */
    public record PromotionWriteRequest(
        String name,
        String code,
        String type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        String startsAt,
        String endsAt,
        Integer usageLimitTotal,
        Integer usageLimitPerBuyer,
        Boolean showOnStorefront,
        Boolean active
    ) {}

    public record PromotionResponse(
        UUID id,
        String code,
        String name,
        PromotionType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Instant startsAt,
        Instant endsAt,
        Integer usageLimitTotal,
        Integer usageLimitPerBuyer,
        boolean showOnStorefront,
        boolean active,
        PromotionStatus status,
        long usedCount,
        BigDecimal discountTotal,
        Instant createdAt,
        Instant updatedAt
    ) {}

    /** Whole-shop counters (independent of the list filters) for the KPI cards and tab badges. */
    public record PromotionSummary(
        long total, long active, long upcoming, long paused, long expired,
        long percent, long fixedAmount, long freeShipping,
        long usageCount, BigDecimal discountTotal
    ) {}

    public record PromotionListResponse(
        List<PromotionResponse> items, int page, int size, long total, int totalPages, PromotionSummary summary
    ) {}

    /** Storefront card: no usage counters, limits or internal flags. */
    public record PublicPromotionResponse(
        String code,
        String name,
        PromotionType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Instant startsAt,
        Instant endsAt
    ) {}
}
