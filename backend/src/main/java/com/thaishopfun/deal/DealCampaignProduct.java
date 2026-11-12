package com.thaishopfun.deal;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "deal_campaign_products",
    uniqueConstraints = @UniqueConstraint(name = "uq_deal_campaign_products", columnNames = {"campaign_id", "product_id"}))
public class DealCampaignProduct extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected DealCampaignProduct() {}

    public DealCampaignProduct(UUID campaignId, UUID productId, int sortOrder, UUID actorId) {
        this.campaignId = campaignId;
        this.productId = productId;
        this.sortOrder = sortOrder;
        stamp(actorId);
    }

    public void moveTo(int sortOrder, UUID actorId) {
        this.sortOrder = sortOrder;
        stamp(actorId);
    }

    public UUID getId() { return id; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getProductId() { return productId; }
    public int getSortOrder() { return sortOrder; }
}
