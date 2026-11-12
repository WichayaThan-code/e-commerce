package com.thaishopfun.deal;

import com.thaishopfun.catalog.CatalogDtos;
import com.thaishopfun.promotion.PromotionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DealDtos {
    private DealDtos() {}

    public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages, Instant endsAt, String title) {
        public PageResponse(List<T> items, int page, int size, long total, Instant endsAt, String title) {
            this(items, page, size, total, size <= 0 ? 0 : (int) ((total + size - 1) / size), endsAt, title);
        }
    }

    /** Public product card for /deals (same shape as best-sellers + campaignEndsAt). */
    public record DealProductResponse(
        int rank,
        long soldCount,
        UUID id,
        String slug,
        String nameTh,
        String nameEn,
        String nameZh,
        Long categoryId,
        String brand,
        BigDecimal basePrice,
        BigDecimal compareAtPrice,
        String currency,
        List<CatalogDtos.PublicSkuResponse> skus,
        List<CatalogDtos.ImageResponse> images,
        String shopSlug,
        String shopName,
        String shopNameEn,
        BigDecimal rating,
        Long reviewCount,
        Instant campaignEndsAt
    ) {
        static DealProductResponse from(CatalogDtos.PublicBestSellerResponse row, Instant endsAt) {
            return new DealProductResponse(
                row.rank(), row.soldCount(), row.id(), row.slug(), row.nameTh(), row.nameEn(), row.nameZh(),
                row.categoryId(), row.brand(), row.basePrice(), row.compareAtPrice(), row.currency(),
                row.skus(), row.images(), row.shopSlug(), row.shopName(), row.shopNameEn(),
                row.rating(), row.reviewCount(), endsAt);
        }
    }

    public record DealCouponResponse(
        String code,
        String name,
        PromotionType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Instant startsAt,
        Instant endsAt,
        Long shopId,
        String shopSlug,
        String shopName,
        Boolean collected
    ) {}

    public record CampaignWriteRequest(
        String kind,
        String titleTh,
        String titleEn,
        String titleZh,
        String startsAt,
        String endsAt,
        Boolean active,
        Integer sortOrder
    ) {}

    public record CampaignProductIdsRequest(List<UUID> productIds) {}

    public record AdminCampaignProduct(
        UUID productId,
        String slug,
        String nameTh,
        String shopSlug,
        String shopName,
        BigDecimal basePrice,
        BigDecimal compareAtPrice,
        int sortOrder
    ) {}

    public record AdminCampaignResponse(
        UUID id,
        DealCampaignKind kind,
        String titleTh,
        String titleEn,
        String titleZh,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        int sortOrder,
        long productCount,
        Instant createdAt,
        Instant updatedAt,
        List<AdminCampaignProduct> products
    ) {}
}
