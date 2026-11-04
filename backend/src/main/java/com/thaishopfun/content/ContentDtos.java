package com.thaishopfun.content;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ContentDtos {
    private ContentDtos() {}

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}

    // ---- Public (already localized) ----
    public record CategoryRef(UUID id, String slug, String name) {}
    public record CategoryView(UUID id, String slug, String name, String description, long articleCount) {}

    public record ArticleCard(UUID id, String slug, String title, String excerpt, CategoryRef category, String coverUrl,
                              int readingMinutes, List<String> tags, String audience, boolean featured, Instant publishedAt) {}

    public record RelatedShop(Long id, String slug, String name) {}
    public record RelatedProduct(UUID id, String slug, String name, String shopSlug) {}

    /** author is the display name only (never e-mail / phone). */
    public record ArticleDetail(UUID id, String slug, String title, String excerpt, String bodyMd, CategoryRef category,
                                String coverUrl, int readingMinutes, List<String> tags, String audience, boolean featured,
                                Instant publishedAt, String author, String metaTitle, String metaDescription,
                                List<ArticleCard> related, RelatedShop relatedShop, List<RelatedProduct> relatedProducts) {}

    // ---- Admin (all languages, all statuses) ----
    public record AdminCategory(UUID id, String slug, int sortOrder, boolean active,
                                String nameTh, String nameEn, String nameZh,
                                String descriptionTh, String descriptionEn, String descriptionZh,
                                long articleCount, Instant createdAt, Instant updatedAt) {}

    public record AdminArticleSummary(UUID id, String slug, String status, String audience, boolean featured,
                                      UUID categoryId, String categorySlug, String titleTh, String titleEn, String titleZh,
                                      String coverUrl, int readingMinutes, List<String> tags,
                                      Instant publishedAt, Instant createdAt, Instant updatedAt) {}

    public record AdminArticle(UUID id, String slug, String status, String audience, boolean featured,
                               UUID categoryId, String categorySlug,
                               String titleTh, String titleEn, String titleZh,
                               String excerptTh, String excerptEn, String excerptZh,
                               String bodyMdTh, String bodyMdEn, String bodyMdZh,
                               String coverUrl, int readingMinutes, List<String> tags,
                               String metaTitle, String metaDescription,
                               Long relatedShopId, List<UUID> relatedProductIds,
                               UUID authorId, String authorName,
                               Instant publishedAt, Instant createdAt, Instant updatedAt) {}

    // ---- Write requests (optional fields: null on update keeps audience/featured, clears optional text) ----
    public record ArticleRequest(String slug, UUID categoryId, String audience, Boolean featured, Integer readingMinutes,
                                 String titleTh, String titleEn, String titleZh,
                                 String excerptTh, String excerptEn, String excerptZh,
                                 String bodyMdTh, String bodyMdEn, String bodyMdZh,
                                 List<String> tags, String metaTitle, String metaDescription,
                                 Long relatedShopId, List<UUID> relatedProductIds) {}

    /** publishedAt is only used with PUBLISHED (schedule in the future, or backdate); default = now on first publish. */
    public record StatusRequest(String status, Instant publishedAt) {}
    public record FeaturedRequest(Boolean featured) {}

    public record CategoryRequest(String slug, Integer sortOrder, Boolean active,
                                  String nameTh, String nameEn, String nameZh,
                                  String descriptionTh, String descriptionEn, String descriptionZh) {}

    public record ActiveRequest(Boolean active) {}
    public record ReorderRequest(List<UUID> ids) {}
}
