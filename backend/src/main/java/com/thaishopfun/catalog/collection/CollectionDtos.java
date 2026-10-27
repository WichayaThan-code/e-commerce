package com.thaishopfun.catalog.collection;

import com.thaishopfun.catalog.CatalogDtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** V41 shop collections — request / response shapes. Public shapes never carry owner contact or buyer ids. */
public final class CollectionDtos {
    private CollectionDtos() {}

    public record CollectionWriteRequest(String slug, String nameTh, String nameEn, String description, UUID coverMediaId, Boolean published) {}

    public record OrderRequest(List<UUID> ids) {}

    public record ProductIdsRequest(List<UUID> productIds) {}

    public record MerchantCollectionResponse(
        UUID id, String slug, String nameTh, String nameEn, String description, UUID coverMediaId, String coverUrl,
        int sortOrder, boolean published, long productCount, Instant createdDateTime, Instant modifiedDateTime
    ) {}

    /** A product row in the merchant editor (drafts / hidden products are listed too, flagged by status). */
    public record MerchantCollectionProduct(UUID productId, String slug, String nameTh, String nameEn, String status, BigDecimal basePrice, int sortOrder) {}

    public record MerchantCollectionDetail(MerchantCollectionResponse collection, List<MerchantCollectionProduct> products) {}

    public record PublicCollectionResponse(String slug, String name, String nameTh, String nameEn, String description, String coverUrl, long productCount) {}

    public record PublicCollectionDetail(PublicCollectionResponse collection, CatalogDtos.PageResponse<CatalogDtos.PublicProductResponse> products) {}
}
