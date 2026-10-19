package com.thaishopfun.cart;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * V1 {@code carts}: one ACTIVE cart per owner per shop (marketplace: the buyer's "cart" is the set of these rows).
 * The owner is either a signed-in buyer ({@code buyer_user_id}) or a guest ({@code guest_token_hash}, V24).
 */
@Entity
@Table(name = "carts")
public class Cart extends AuditedEntity {
    public static final String ACTIVE = "ACTIVE";
    public static final String ABANDONED = "ABANDONED";

    @Id private UUID id;
    @Column(name = "buyer_user_id") private UUID buyerUserId;
    @Column(name = "guest_token_hash", length = 64) private String guestTokenHash;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(nullable = false, length = 30) private String status;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 3) private String currency;
    @Column(name = "coupon_code", length = 64) private String couponCode;
    @Column(name = "shipping_method_id") private UUID shippingMethodId;
    @Column(name = "expires_at") private Instant expiresAt;

    protected Cart() {}

    public Cart(UUID buyerUserId, String guestTokenHash, UUID shopId) {
        this.id = UUID.randomUUID();
        this.buyerUserId = buyerUserId;
        this.guestTokenHash = buyerUserId == null ? guestTokenHash : null;
        this.shopId = shopId;
        this.status = ACTIVE;
        this.currency = "THB";
    }

    public UUID getId() { return id; }
    public UUID getBuyerUserId() { return buyerUserId; }
    public String getGuestTokenHash() { return guestTokenHash; }
    public UUID getShopId() { return shopId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrency() { return currency; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public UUID getShippingMethodId() { return shippingMethodId; }
    public void setShippingMethodId(UUID shippingMethodId) { this.shippingMethodId = shippingMethodId; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
