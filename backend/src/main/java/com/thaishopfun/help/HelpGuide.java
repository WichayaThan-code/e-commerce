package com.thaishopfun.help;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Help center guide (link card), mapped onto V50 {@code help_guides}. */
@Entity
@Table(name = "help_guides")
public class HelpGuide {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 80) private String slug;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "title_th", nullable = false, length = 200) private String titleTh;
    @Column(name = "title_en", length = 200) private String titleEn;
    @Column(name = "title_zh", length = 200) private String titleZh;
    @Column(name = "summary_th", nullable = false, length = 500) private String summaryTh;
    @Column(name = "summary_en", length = 500) private String summaryEn;
    @Column(name = "summary_zh", length = 500) private String summaryZh;
    @Column(length = 500) private String url;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;

    protected HelpGuide() {}

    public HelpGuide(UUID actorId) {
        var now = Instant.now();
        this.id = UUID.randomUUID();
        this.createdAt = now;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getTitleTh() { return titleTh; }
    public void setTitleTh(String titleTh) { this.titleTh = titleTh; }
    public String getTitleEn() { return titleEn; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }
    public String getTitleZh() { return titleZh; }
    public void setTitleZh(String titleZh) { this.titleZh = titleZh; }
    public String getSummaryTh() { return summaryTh; }
    public void setSummaryTh(String summaryTh) { this.summaryTh = summaryTh; }
    public String getSummaryEn() { return summaryEn; }
    public void setSummaryEn(String summaryEn) { this.summaryEn = summaryEn; }
    public String getSummaryZh() { return summaryZh; }
    public void setSummaryZh(String summaryZh) { this.summaryZh = summaryZh; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }

    public void touch(UUID actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
