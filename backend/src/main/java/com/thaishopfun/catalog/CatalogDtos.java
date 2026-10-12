package com.thaishopfun.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record ProductWriteRequest(
        String nameTh,
        String nameEn,
        String nameZh,
        String descriptionTh,
        String descriptionEn,
        String descriptionZh,
        Long categoryId,
        String slug,
        String productType,
        String brand,
        BigDecimal basePrice,
        BigDecimal compareAtPrice,
        String currency,
        Integer weightGrams,
        List<SkuWriteRequest> skus,
        List<OptionGroup> optionSchema,
        Boolean confirmRemoveStocked
    ) {}

    public record OptionGroup(String name, List<String> values) {}

    public record SkuWriteRequest(
        UUID id,
        String skuCode,
        String barcode,
        String optionLabel,
        Map<String, String> optionValues,
        BigDecimal price,
        BigDecimal costPrice,
        BigDecimal compareAtPrice,
        Integer weightGrams,
        Integer initialStock,
        UUID imageId,
        Boolean active
    ) {}

    public record ImageOrderRequest(List<UUID> imageIds, UUID primaryImageId) {}

    public record StockAdjustRequest(String entryType, int quantityDelta, String reason, int version, String idempotencyKey) {}

    public record BulkStockItem(UUID skuId, String entryType, int quantityDelta, int version, String reason) {}

    public record BulkStockRequest(String idempotencyKey, List<BulkStockItem> items) {}

    public record SkuCodeCheck(List<String> taken) {}

    public record PageResponse<T>(List<T> items, int page, int size, long total) {}

    public record ProductImportError(int row, String message) {}

    public record ProductImportResult(int created, int failed, List<ProductImportError> errors, int skuCreated) {}

    public record ProductImportAccepted(UUID jobId, int total) {}

    public record ProductImportConflict(String code, String message, UUID jobId) {}

    public record ProductImportJobView(
        UUID jobId,
        Long shopId,
        UUID userId,
        String status,
        int total,
        int processed,
        int created,
        int skuCreated,
        int failed,
        List<ProductImportError> errors,
        Integer currentRow,
        Instant startedAt,
        Instant finishedAt
    ) {}

    public record TranslationMeta(String origin, String status) {}

    public record RevertAutoTranslationRequest(String field, String locale) {}

    public record MerchantProductResponse(
        UUID id,
        Long shopId,
        String slug,
        String nameTh,
        String nameEn,
        String nameZh,
        String descriptionTh,
        String descriptionEn,
        String descriptionZh,
        ProductStatus status,
        ProductType productType,
        Long categoryId,
        String brand,
        BigDecimal basePrice,
        BigDecimal compareAtPrice,
        String currency,
        Integer weightGrams,
        Instant publishedAt,
        Instant archivedAt,
        Instant modifiedAt,
        List<MerchantSkuResponse> skus,
        List<ImageResponse> images,
        TranslationMeta nameEnMeta,
        TranslationMeta nameZhMeta,
        TranslationMeta descriptionEnMeta,
        TranslationMeta descriptionZhMeta,
        List<OptionGroup> optionSchema
    ) {}

    public record MerchantSkuResponse(
        UUID id,
        String skuCode,
        String barcode,
        String optionLabel,
        Map<String, String> optionValues,
        BigDecimal price,
        BigDecimal costPrice,
        BigDecimal compareAtPrice,
        String currency,
        Integer weightGrams,
        boolean active,
        int onHand,
        int reservedQty,
        int available,
        Integer lowStockThreshold,
        int version,
        UUID imageId
    ) {}

    public record ImageResponse(UUID id, String url, int sortOrder, boolean primary, String altText) {}

    public record PublicProductResponse(
        UUID id,
        String slug,
        String nameTh,
        String nameEn,
        String nameZh,
        String descriptionTh,
        String descriptionEn,
        String descriptionZh,
        Long categoryId,
        String brand,
        BigDecimal basePrice,
        BigDecimal compareAtPrice,
        String currency,
        List<PublicSkuResponse> skus,
        List<ImageResponse> images,
        List<OptionGroup> optionSchema
    ) {}

    public record PublicSkuResponse(
        UUID id,
        String skuCode,
        String optionLabel,
        Map<String, String> optionValues,
        BigDecimal price,
        BigDecimal compareAtPrice,
        String currency,
        int available,
        UUID imageId
    ) {}

    public record StockResponse(UUID skuId, String entryType, int onHand, int available, int version) {}

    public record LedgerEntryResponse(
        Long id,
        UUID skuId,
        String skuCode,
        String entryType,
        int quantityDelta,
        int onHandBefore,
        int onHandAfter,
        String reason,
        Instant createdAt
    ) {}
}
