package com.thaishopfun.merchant.policy;

import com.thaishopfun.merchant.Shop;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop_policies")
public class ShopPolicy {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 20)
    private ShopPolicyType type;

    @Column(length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_date_time", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "modified_date_time", nullable = false)
    private Instant updatedAt;

    @Column(name = "modified_by")
    private UUID updatedBy;

    protected ShopPolicy() {}

    public ShopPolicy(Shop shop, ShopPolicyType type, String title, String body, UUID actorId) {
        var now = Instant.now();
        this.id = UUID.randomUUID();
        this.shop = shop;
        this.type = type;
        this.title = title;
        this.body = body;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void update(String title, String body, UUID actorId) {
        this.title = title;
        this.body = body;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public Shop getShop() { return shop; }
    public ShopPolicyType getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
