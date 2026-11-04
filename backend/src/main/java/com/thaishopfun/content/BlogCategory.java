package com.thaishopfun.content;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Article category, mapped onto V1 {@code blog_categories} + V51 columns (name_zh, descriptions, sort_order). */
@Entity
@Table(name = "blog_categories")
public class BlogCategory extends AuditedEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 100) private String slug;
    @Column(name = "name_th", nullable = false, length = 150) private String nameTh;
    @Column(name = "name_en", length = 150) private String nameEn;
    @Column(name = "name_zh", length = 150) private String nameZh;
    @Column(name = "description_th", length = 500) private String descriptionTh;
    @Column(name = "description_en", length = 500) private String descriptionEn;
    @Column(name = "description_zh", length = 500) private String descriptionZh;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "is_active", nullable = false) private boolean active = true;

    protected BlogCategory() {}

    public BlogCategory(UUID actorId) {
        this.id = UUID.randomUUID();
        stamp(actorId);
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getNameTh() { return nameTh; }
    public void setNameTh(String nameTh) { this.nameTh = nameTh; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getNameZh() { return nameZh; }
    public void setNameZh(String nameZh) { this.nameZh = nameZh; }
    public String getDescriptionTh() { return descriptionTh; }
    public void setDescriptionTh(String descriptionTh) { this.descriptionTh = descriptionTh; }
    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }
    public String getDescriptionZh() { return descriptionZh; }
    public void setDescriptionZh(String descriptionZh) { this.descriptionZh = descriptionZh; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
