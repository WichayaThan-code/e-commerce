package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** V1 {@code media_files}: payment and refund slips (purpose PAYMENT_SLIP / REFUND_PROOF), stored PRIVATE on local storage. */
@Entity
@Table(name = "media_files")
public class MediaFile extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "shop_id") private UUID shopId;
    @Column(name = "uploader_id") private UUID uploaderId;
    @Column(nullable = false, length = 40) private String purpose;
    @ColumnDefault("'LOCAL'") @Column(name = "storage_provider", nullable = false, length = 30) private String storageProvider = "LOCAL";
    @Column(name = "storage_key", nullable = false, length = 700) private String storageKey;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "file_size", nullable = false) private long fileSize;
    @Column(length = 64) private String sha256;
    @ColumnDefault("'PRIVATE'") @Column(nullable = false, length = 20) private String visibility = "PRIVATE";
    @ColumnDefault("'READY'") @Column(nullable = false, length = 30) private String status = "READY";
    @Column(name = "required_permission", length = 80) private String requiredPermission;

    protected MediaFile() {}

    public MediaFile(UUID shopId, UUID uploaderId, String purpose, String storageKey, String originalFilename, String contentType,
                     long fileSize, String sha256, String requiredPermission) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.uploaderId = uploaderId;
        this.purpose = purpose;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.requiredPermission = requiredPermission;
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public UUID getUploaderId() { return uploaderId; }
    public String getPurpose() { return purpose; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public String getSha256() { return sha256; }
    public String getVisibility() { return visibility; }
    /** Publicly viewable by anyone with the link (article covers); everything else stays PRIVATE. */
    public void makePublic() { this.visibility = "PUBLIC"; }
}
