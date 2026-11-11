package com.thaishopfun.help;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Help center topic (category of FAQs), mapped onto V50 {@code help_topics}. */
@Entity
@Table(name = "help_topics")
public class HelpTopic {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 80) private String slug;
    @Column(nullable = false, length = 64) private String icon;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "title_th", nullable = false, length = 200) private String titleTh;
    @Column(name = "title_en", length = 200) private String titleEn;
    @Column(name = "title_zh", length = 200) private String titleZh;
    @Column(name = "description_th", nullable = false, columnDefinition = "text") private String descriptionTh;
    @Column(name = "description_en", columnDefinition = "text") private String descriptionEn;
    @Column(name = "description_zh", columnDefinition = "text") private String descriptionZh;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by") private UUID updatedBy;

    protected HelpTopic() {}

    public HelpTopic(UUID actorId) {
        var now = Instant.now();
        this.id = UUID.randomUUID();
        this.createdAt = now;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
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
    public String getDescriptionTh() { return descriptionTh; }
    public void setDescriptionTh(String descriptionTh) { this.descriptionTh = descriptionTh; }
    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }
    public String getDescriptionZh() { return descriptionZh; }
    public void setDescriptionZh(String descriptionZh) { this.descriptionZh = descriptionZh; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }

    public void touch(UUID actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
