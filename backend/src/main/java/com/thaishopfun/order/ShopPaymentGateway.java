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

/**
 * V1 {@code shop_payment_gateways}: the shop's own receiving channels (UNIQUE shop_id + provider). For manual transfer
 * (BANK_TRANSFER / PROMPTPAY) config_encrypted holds the account JSON encrypted with SensitiveDataCipher; never logged.
 */
@Entity
@Table(name = "shop_payment_gateways", uniqueConstraints = @UniqueConstraint(name = "uq_shop_payment_gateways_shop_id_provider", columnNames = {"shop_id", "provider"}))
public class ShopPaymentGateway extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(nullable = false, length = 50) private String provider;
    @Column(name = "display_name", nullable = false, length = 100) private String displayName;
    @ColumnDefault("false") @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "config_encrypted", columnDefinition = "text") private String configEncrypted;
    @Column(name = "webhook_secret_encrypted", columnDefinition = "text") private String webhookSecretEncrypted;

    protected ShopPaymentGateway() {}

    public ShopPaymentGateway(UUID shopId, String provider) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.provider = provider;
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public String getProvider() { return provider; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getConfigEncrypted() { return configEncrypted; }
    public void setConfigEncrypted(String configEncrypted) { this.configEncrypted = configEncrypted; }
}
