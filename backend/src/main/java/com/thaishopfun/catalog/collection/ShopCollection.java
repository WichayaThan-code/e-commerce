package com.thaishopfun.catalog.collection;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** V41 {@code shop_collections}: a merchant-curated list of the shop's own products. */
@Entity
@Table(name = "shop_collections")
public class ShopCollection extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(name = "name_th", nullable = false, length = 120)
    private String nameTh;

    @Column(name = "name_en", length = 120)
    private String nameEn;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "cover_media_id")
    private UUID coverMediaId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_published", nullable = false)
    private boolean published;

    protected ShopCollection() {}

    public ShopCollection(UUID shopId, int sortOrder) {
        this.shopId = shopId;
        this.sortOrder = sortOrder;
    }

    public void apply(String slug, String nameTh, String nameEn, String description, UUID coverMediaId, boolean published, UUID actorId) {
        this.slug = slug;
        this.nameTh = nameTh;
        this.nameEn = nameEn;
        this.description = description;
        this.coverMediaId = coverMediaId;
        this.published = published;
        stamp(actorId);
    }

    public void moveTo(int sortOrder) { this.sortOrder = sortOrder; }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public String getSlug() { return slug; }
    public String getNameTh() { return nameTh; }
    public String getNameEn() { return nameEn; }
    public String getDescription() { return description; }
    public UUID getCoverMediaId() { return coverMediaId; }
    public int getSortOrder() { return sortOrder; }
    public boolean isPublished() { return published; }
}
