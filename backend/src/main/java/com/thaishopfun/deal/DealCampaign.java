package com.thaishopfun.deal;

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
@Table(name = "deal_campaigns")
public class DealCampaign extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DealCampaignKind kind;

    @Column(name = "title_th", nullable = false, length = 150)
    private String titleTh;

    @Column(name = "title_en", length = 150)
    private String titleEn;

    @Column(name = "title_zh", length = 150)
    private String titleZh;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected DealCampaign() {}

    public DealCampaign(DealCampaignKind kind) {
        this.kind = kind;
    }

    public void apply(String titleTh, String titleEn, String titleZh, Instant startsAt, Instant endsAt,
                      boolean active, int sortOrder, UUID actorId) {
        this.titleTh = titleTh;
        this.titleEn = titleEn;
        this.titleZh = titleZh;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.active = active;
        this.sortOrder = sortOrder;
        stamp(actorId);
    }

    public boolean isLive(Instant now) {
        if (!active) return false;
        if (startsAt != null && startsAt.isAfter(now)) return false;
        return endsAt == null || endsAt.isAfter(now);
    }

    public UUID getId() { return id; }
    public DealCampaignKind getKind() { return kind; }
    public void setKind(DealCampaignKind kind) { this.kind = kind; }
    public String getTitleTh() { return titleTh; }
    public String getTitleEn() { return titleEn; }
    public String getTitleZh() { return titleZh; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
}
