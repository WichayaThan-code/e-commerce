package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;

/** V30 {@code carriers}: platform master list of shipping carriers (seeded by the migration). */
@Entity
@Table(name = "carriers")
public class Carrier extends AuditedEntity {
    @Id @Column(length = 50) private String code;
    @Column(name = "name_th", nullable = false, length = 100) private String nameTh;
    @Column(name = "name_en", nullable = false, length = 100) private String nameEn;
    @Column(name = "tracking_pattern", length = 200) private String trackingPattern;
    @Column(name = "tracking_url_template", length = 500) private String trackingUrlTemplate;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    protected Carrier() {}

    public Carrier(String code, String nameTh, String nameEn, String trackingPattern, String trackingUrlTemplate, boolean active, int sortOrder) {
        this.code = code;
        this.nameTh = nameTh;
        this.nameEn = nameEn;
        this.trackingPattern = trackingPattern;
        this.trackingUrlTemplate = trackingUrlTemplate;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    public String getCode() { return code; }
    public String getNameTh() { return nameTh; }
    public String getNameEn() { return nameEn; }
    public String getTrackingPattern() { return trackingPattern; }
    public String getTrackingUrlTemplate() { return trackingUrlTemplate; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }

    /** Public tracking link for a tracking number, or null when the carrier has no template. */
    public String trackingUrl(String trackingNumber) {
        if (trackingUrlTemplate == null || trackingNumber == null) return null;
        return trackingUrlTemplate.replace("{tracking}", java.net.URLEncoder.encode(trackingNumber, java.nio.charset.StandardCharsets.UTF_8));
    }
}
