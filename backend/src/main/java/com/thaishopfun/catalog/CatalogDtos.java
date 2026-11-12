package com.thaishopfun.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {}

    /**
     * Create/update body. The annotations carry validation codes (see common.ValidationMessages); ProductService
     * runs them programmatically together with the business rules, so the API, the batch endpoint and the Excel
     * import all return every field problem at once in the same format.
     */
    public record ProductWriteRequest(
        @NotBlank(message = "NAME_REQUIRED") @Size(max = 255, message = "NAME_TOO_LONG") String nameTh,
        @Size(max = 255, message = "NAME_TOO_LONG") String nameEn,
        @Size(max = 255, message = "NAME_TOO_LONG") String nameZh,
        String descriptionTh,
        String descriptionEn,
        String descriptionZh,
        Long categoryId,
        @Size(max = 150, message = "SLUG_INVALID") String slug,
        String productType,
        @Size(max = 100, message = "BRAND_TOO_LONG") String brand,
        @DecimalMin(value = "0.01", message = "PRICE_INVALID") BigDecimal basePrice,
        @DecimalMin(value = "0.01", message = "COMPARE_PRICE_INVALID") BigDecimal compareAtPrice,
        String currency,
        @PositiveOrZero(message = "WEIGHT_INVALID") @Max(value = 1_000_000, message = "WEIGHT_INVALID") Integer weightGrams,
        @Size(max = 100, message = "SKU_LIMIT") List<@Valid SkuWriteRequest> skus,
        /**
         * Option groups of a VARIABLE product (1-3). Each SKU then picks exactly one value per group through
         * {@code optionValues} ({"Color":"Black","Size":"S"}). When null, the groups are derived from the SKUs'
         * optionValues (the current form sends only those).
         */
        List<OptionGroupWriteRequest> optionGroups,
        /** Team frontend (VariantEditor) shape: [{name, values:[...]}]. Used when optionGroups is null. */
        List<OptionGroup> optionSchema,
        /** Removing SKUs that still have on-hand stock needs this flag (stock is written off through the ledger). */
        Boolean confirmRemoveStocked
    ) {
        public ProductWriteRequest(String nameTh, String nameEn, String nameZh, String descriptionTh, String descriptionEn,
                                   String descriptionZh, Long categoryId, String slug, String productType, String brand,
                                   BigDecimal basePrice, BigDecimal compareAtPrice, String currency, Integer weightGrams,
                                   List<SkuWriteRequest> skus) {
            this(nameTh, nameEn, nameZh, descriptionTh, descriptionEn, descriptionZh, categoryId, slug, productType, brand,
                basePrice, compareAtPrice, currency, weightGrams, skus, null, null, null);
        }

        public ProductWriteRequest(String nameTh, String nameEn, String nameZh, String descriptionTh, String descriptionEn,
                                   String descriptionZh, Long categoryId, String slug, String productType, String brand,
                                   BigDecimal basePrice, BigDecimal compareAtPrice, String currency, Integer weightGrams,
                                   List<SkuWriteRequest> skus, List<OptionGroupWriteRequest> optionGroups) {
            this(nameTh, nameEn, nameZh, descriptionTh, descriptionEn, descriptionZh, categoryId, slug, productType, brand,
                basePrice, compareAtPrice, currency, weightGrams, skus, optionGroups, null, null);
        }

        public ProductWriteRequest(String nameTh, String nameEn, String nameZh, String descriptionTh, String descriptionEn,
                                   String descriptionZh, Long categoryId, String slug, String productType, String brand,
                                   BigDecimal basePrice, BigDecimal compareAtPrice, String currency, Integer weightGrams,
                                   List<SkuWriteRequest> skus, List<OptionGroup> optionSchema, Boolean confirmRemoveStocked) {
            this(nameTh, nameEn, nameZh, descriptionTh, descriptionEn, descriptionZh, categoryId, slug, productType, brand,
                basePrice, compareAtPrice, currency, weightGrams, skus, null, optionSchema, confirmRemoveStocked);
        }
    }

    /** id keeps an existing group (rename allowed); without id an existing group with the same name is reused. */
    public record OptionGroupWriteRequest(UUID id, String name, List<OptionValueWriteRequest> values) {}

    public record OptionValueWriteRequest(UUID id, String value) {}

    public record VariantCombinationRequest(String skuPrefix, List<OptionGroupWriteRequest> optionGroups) {}

    public record VariantCombination(String optionLabel, Map<String, String> optionValues, String skuCode) {}

    public record OptionGroup(String name, List<String> values) {}

    public record SkuWriteRequest(
        UUID id,
        @NotBlank(message = "SKU_CODE_REQUIRED") @Size(max = 64, message = "SKU_CODE_TOO_LONG") String skuCode,
        @Size(max = 64, message = "BARCODE_INVALID") @Pattern(regexp = "^[A-Z0-9-]*$", message = "BARCODE_INVALID") String barcode,
        @Size(max = 255, message = "OPTION_LABEL_TOO_LONG") String optionLabel,
        Map<String, String> optionValues,
        @NotNull(message = "PRICE_REQUIRED") @DecimalMin(value = "0.01", message = "PRICE_INVALID") BigDecimal price,
        @DecimalMin(value = "0.00", message = "COST_PRICE_INVALID") BigDecimal costPrice,
        @DecimalMin(value = "0.01", message = "COMPARE_PRICE_INVALID") BigDecimal compareAtPrice,
        @PositiveOrZero(message = "WEIGHT_INVALID") @Max(value = 1_000_000, message = "WEIGHT_INVALID") Integer weightGrams,
        @PositiveOrZero(message = "STOCK_INVALID") @Max(value = 1_000_000, message = "STOCK_INVALID") Integer initialStock,
        /** Variant image: id of one of this product's images (upload first, then save). */
        UUID imageId,
        /** null keeps the current active flag (new SKUs are active). */
        Boolean active,
        /** null keeps the current safety stock (new levels start at 0). Must be >= 0 when sent. */
        @PositiveOrZero(message = "SAFETY_STOCK_INVALID") @Max(value = 1_000_000, message = "SAFETY_STOCK_INVALID") Integer safetyStock
    ) {
        public SkuWriteRequest(UUID id, String skuCode, String barcode, String optionLabel, Map<String, String> optionValues,
                               BigDecimal price, BigDecimal costPrice, BigDecimal compareAtPrice, Integer weightGrams, Integer initialStock) {
            this(id, skuCode, barcode, optionLabel, optionValues, price, costPrice, compareAtPrice, weightGrams, initialStock, null, null, null);
        }

        public SkuWriteRequest(UUID id, String skuCode, String barcode, String optionLabel, Map<String, String> optionValues,
                               BigDecimal price, BigDecimal costPrice, BigDecimal compareAtPrice, Integer weightGrams, Integer initialStock, UUID imageId) {
            this(id, skuCode, barcode, optionLabel, optionValues, price, costPrice, compareAtPrice, weightGrams, initialStock, imageId, null, null);
        }

        public SkuWriteRequest(UUID id, String skuCode, String barcode, String optionLabel, Map<String, String> optionValues,
                               BigDecimal price, BigDecimal costPrice, BigDecimal compareAtPrice, Integer weightGrams, Integer initialStock, UUID imageId, Boolean active) {
            this(id, skuCode, barcode, optionLabel, optionValues, price, costPrice, compareAtPrice, weightGrams, initialStock, imageId, active, null);
        }
    }

    public record ImageOrderRequest(List<UUID> imageIds, UUID primaryImageId) {}

    public record StockAdjustRequest(String entryType, int quantityDelta, String reason, int version, String idempotencyKey) {}

    /** page is 0-based; totalPages is derived from total/size so clients don't have to compute it. */
    public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages) {
        public PageResponse(List<T> items, int page, int size, long total) {
            this(items, page, size, total, size <= 0 ? 0 : (int) ((total + size - 1) / size));
        }
    }

    public record BrandOption(String name, long productCount) {}

    public record BulkStockItem(UUID skuId, String entryType, int quantityDelta, int version, String reason) {}

    public record BulkStockRequest(String idempotencyKey, List<BulkStockItem> items) {}

    public record SkuCodeCheck(List<String> taken) {}

    public record ProductImportError(int row, String message, String productKey, String skuCode) {
        public ProductImportError(int row, String message) {
            this(row, message, null, null);
        }
    }

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

    /** Live translation preview for the product form (not persisted). */
    public record TranslationPreviewRequest(
        java.util.Map<String, String> fields,
        java.util.List<String> locales
    ) {}

    public record TranslationPreviewResponse(java.util.Map<String, java.util.Map<String, String>> translations) {}

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
        List<OptionGroupResponse> optionGroups,
        List<OptionGroup> optionSchema,
        int variantCount,
        int totalStock,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        int lowStockCount
    ) {}

    public record OptionGroupResponse(UUID id, String name, int sortOrder, List<OptionValueResponse> values) {}

    public record OptionValueResponse(UUID id, String value, int sortOrder) {}

    public record SkuOptionResponse(UUID groupId, String groupName, UUID valueId, String value) {}

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
        int safetyStock,
        int version,
        UUID imageId,
        List<SkuOptionResponse> options
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
        List<OptionGroupResponse> optionGroups,
        List<OptionGroup> optionSchema,
        Long soldCount,
        Instant createdAt
    ) {
        /** Arity before createdAt existed; createdAt stays null. */
        public PublicProductResponse(UUID id, String slug, String nameTh, String nameEn, String nameZh, String descriptionTh,
                                     String descriptionEn, String descriptionZh, Long categoryId, String brand, BigDecimal basePrice,
                                     BigDecimal compareAtPrice, String currency, List<PublicSkuResponse> skus, List<ImageResponse> images,
                                     List<OptionGroupResponse> optionGroups, List<OptionGroup> optionSchema, Long soldCount) {
            this(id, slug, nameTh, nameEn, nameZh, descriptionTh, descriptionEn, descriptionZh, categoryId, brand, basePrice,
                compareAtPrice, currency, skus, images, optionGroups, optionSchema, soldCount, null);
        }

        /** Arity used before soldCount existed; soldCount stays null (not computed for this view). */
        public PublicProductResponse(UUID id, String slug, String nameTh, String nameEn, String nameZh, String descriptionTh,
                                     String descriptionEn, String descriptionZh, Long categoryId, String brand, BigDecimal basePrice,
                                     BigDecimal compareAtPrice, String currency, List<PublicSkuResponse> skus, List<ImageResponse> images,
                                     List<OptionGroupResponse> optionGroups, List<OptionGroup> optionSchema) {
            this(id, slug, nameTh, nameEn, nameZh, descriptionTh, descriptionEn, descriptionZh, categoryId, brand, basePrice,
                compareAtPrice, currency, skus, images, optionGroups, optionSchema, null, null);
        }

        public PublicProductResponse withCreatedAt(Instant at) {
            return new PublicProductResponse(id, slug, nameTh, nameEn, nameZh, descriptionTh, descriptionEn, descriptionZh, categoryId,
                brand, basePrice, compareAtPrice, currency, skus, images, optionGroups, optionSchema, soldCount, at);
        }

        public PublicProductResponse withSoldCount(long sold) {
            return new PublicProductResponse(id, slug, nameTh, nameEn, nameZh, descriptionTh, descriptionEn, descriptionZh, categoryId,
                brand, basePrice, compareAtPrice, currency, skus, images, optionGroups, optionSchema, sold, createdAt);
        }
    }

    public record PublicSkuResponse(
        UUID id,
        String skuCode,
        String optionLabel,
        Map<String, String> optionValues,
        BigDecimal price,
        BigDecimal compareAtPrice,
        String currency,
        int available,
        UUID imageId,
        List<SkuOptionResponse> options
    ) {}

    public record StockResponse(UUID skuId, String entryType, int onHand, int available, int safetyStock, int version) {}

    public record LedgerEntryResponse(
        Long id,
        UUID skuId,
        String skuCode,
        String entryType,
        int quantityDelta,
        int onHandBefore,
        int onHandAfter,
        String reason,
        Instant createdAt,
        String actorName,
        int reservedBefore,
        int reservedAfter
    ) {}

    /** Marketplace search (GET /api/search): product card data plus the minimum shop reference needed to link it. */
    public record PublicSearchShopRef(String slug, String name, String nameEn) {}
    public record PublicSearchProductResponse(PublicProductResponse product, PublicSearchShopRef shop) {}
    public record PublicBrandResponse(String name, long productCount) {}

    /**
     * Marketplace best-seller row (GET /api/products/best-sellers). Public product fields + ranking data. Contains no buyer
     * or order data: soldCount is an aggregate quantity only. rating / reviewCount are always null (no review system yet).
     */
    public record PublicBestSellerResponse(
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
        List<PublicSkuResponse> skus,
        List<ImageResponse> images,
        String shopSlug,
        String shopName,
        String shopNameEn,
        BigDecimal rating,
        Long reviewCount
    ) {}
}
