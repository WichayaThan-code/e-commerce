package com.thaishopfun.merchant.theme;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop_theme_media")
public class ShopThemeMedia extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ShopThemeMediaKind kind;

    @Column(name = "storage_path", nullable = false, columnDefinition = "text")
    private String storagePath;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    private Integer width;
    private Integer height;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ShopThemeMedia() {}

    public ShopThemeMedia(
        UUID shopId, ShopThemeMediaKind kind, String storagePath, String originalFilename,
        String mimeType, long sizeBytes, Integer width, Integer height, String sha256, UUID actorId
    ) {
        this.shopId = shopId;
        this.kind = kind;
        this.storagePath = storagePath;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.sha256 = sha256;
        stamp(actorId);
    }

    public void softDelete(UUID actorId) {
        this.deletedAt = Instant.now();
        stamp(actorId);
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public ShopThemeMediaKind getKind() { return kind; }
    public String getStoragePath() { return storagePath; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public String getSha256() { return sha256; }
    public Instant getDeletedAt() { return deletedAt; }
}
