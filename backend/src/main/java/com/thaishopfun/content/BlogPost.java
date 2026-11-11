package com.thaishopfun.content;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Article, mapped onto V1 {@code blog_posts} + V51 columns. {@code title / excerpt / body_md} are the Thai content;
 * English / Chinese live in the {@code *_en / *_zh} columns. {@code tags} and {@code related_product_ids} are native
 * array columns (TEXT[] / UUID[]).
 */
@Entity
@Table(name = "blog_posts")
public class BlogPost extends AuditedEntity {
    public static final String DRAFT = "DRAFT", PUBLISHED = "PUBLISHED", ARCHIVED = "ARCHIVED";

    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private BlogCategory category;
    @Column(name = "author_id") private UUID authorId;
    @Column(nullable = false, unique = true, length = 150) private String slug;
    @Column(nullable = false, length = 255) private String title;
    @Column(length = 500) private String excerpt;
    @Column(name = "body_md", nullable = false, columnDefinition = "text") private String bodyMd;
    @Column(name = "title_en", length = 255) private String titleEn;
    @Column(name = "title_zh", length = 255) private String titleZh;
    @Column(name = "excerpt_en", length = 500) private String excerptEn;
    @Column(name = "excerpt_zh", length = 500) private String excerptZh;
    @Column(name = "body_md_en", columnDefinition = "text") private String bodyMdEn;
    @Column(name = "body_md_zh", columnDefinition = "text") private String bodyMdZh;
    @Column(name = "cover_media_id") private UUID coverMediaId;
    @Column(nullable = false, length = 30) private String status = DRAFT;
    /** ALL | CUSTOMER | MERCHANT (CHECK constraint in V51). */
    @Column(nullable = false, length = 16) private String audience = "ALL";
    @Column(name = "is_featured", nullable = false) private boolean featured;
    @Column(name = "reading_minutes", nullable = false) private int readingMinutes = 3;
    @Column(name = "view_count", nullable = false) private long viewCount;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false)
    private List<String> tags = new ArrayList<>();
    @Column(name = "meta_title", length = 120) private String metaTitle;
    @Column(name = "meta_description", length = 200) private String metaDescription;
    @Column(name = "related_shop_id") private Long relatedShopId;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "related_product_ids", nullable = false)
    private List<UUID> relatedProductIds = new ArrayList<>();
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected BlogPost() {}

    public BlogPost(UUID actorId) {
        this.id = UUID.randomUUID();
        this.authorId = actorId;
        stamp(actorId);
    }

    public UUID getId() { return id; }
    public BlogCategory getCategory() { return category; }
    public void setCategory(BlogCategory category) { this.category = category; }
    public UUID getAuthorId() { return authorId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
    public String getBodyMd() { return bodyMd; }
    public void setBodyMd(String bodyMd) { this.bodyMd = bodyMd; }
    public String getTitleEn() { return titleEn; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }
    public String getTitleZh() { return titleZh; }
    public void setTitleZh(String titleZh) { this.titleZh = titleZh; }
    public String getExcerptEn() { return excerptEn; }
    public void setExcerptEn(String excerptEn) { this.excerptEn = excerptEn; }
    public String getExcerptZh() { return excerptZh; }
    public void setExcerptZh(String excerptZh) { this.excerptZh = excerptZh; }
    public String getBodyMdEn() { return bodyMdEn; }
    public void setBodyMdEn(String bodyMdEn) { this.bodyMdEn = bodyMdEn; }
    public String getBodyMdZh() { return bodyMdZh; }
    public void setBodyMdZh(String bodyMdZh) { this.bodyMdZh = bodyMdZh; }
    public UUID getCoverMediaId() { return coverMediaId; }
    public void setCoverMediaId(UUID coverMediaId) { this.coverMediaId = coverMediaId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public int getReadingMinutes() { return readingMinutes; }
    public void setReadingMinutes(int readingMinutes) { this.readingMinutes = readingMinutes; }
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public List<String> getTags() { return tags == null ? List.of() : tags; }
    public void setTags(List<String> tags) { this.tags = new ArrayList<>(tags); }
    public String getMetaTitle() { return metaTitle; }
    public void setMetaTitle(String metaTitle) { this.metaTitle = metaTitle; }
    public String getMetaDescription() { return metaDescription; }
    public void setMetaDescription(String metaDescription) { this.metaDescription = metaDescription; }
    public Long getRelatedShopId() { return relatedShopId; }
    public void setRelatedShopId(Long relatedShopId) { this.relatedShopId = relatedShopId; }
    public List<UUID> getRelatedProductIds() { return relatedProductIds == null ? List.of() : relatedProductIds; }
    public void setRelatedProductIds(List<UUID> ids) { this.relatedProductIds = new ArrayList<>(ids); }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
