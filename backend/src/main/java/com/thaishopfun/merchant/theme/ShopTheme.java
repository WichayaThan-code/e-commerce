package com.thaishopfun.merchant.theme;

import com.thaishopfun.merchant.Shop;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "shop_themes")
public class ShopTheme {
    @Id
    @Column(name = "shop_id")
    private UUID shopId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "shop_id")
    private Shop shop;

    @Column(name = "template_key", nullable = false, length = 30)
    private String templateKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Column(name = "created_date_time", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "modified_date_time", nullable = false)
    private Instant updatedAt;

    @Column(name = "modified_by")
    private UUID updatedBy;

    protected ShopTheme() {}

    public ShopTheme(Shop shop, ShopThemeTemplate template, Map<String, Object> config, UUID actorId) {
        var now = Instant.now();
        this.shop = shop;
        this.shopId = shop.getUuid();
        this.templateKey = template.name();
        this.config = ThemePresets.deepCopy(config);
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void apply(ShopThemeTemplate template, Map<String, Object> config, UUID actorId) {
        this.templateKey = template.name();
        this.config = ThemePresets.deepCopy(config);
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    public UUID getShopId() { return shopId; }
    public Shop getShop() { return shop; }
    public String getTemplateKey() { return templateKey; }
    public Map<String, Object> getConfig() { return config; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
