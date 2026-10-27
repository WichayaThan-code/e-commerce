package com.thaishopfun.merchant.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "merchant_notification_reads")
@IdClass(MerchantNotificationRead.Pk.class)
public class MerchantNotificationRead {
    @Id
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "item_key", nullable = false, length = 120)
    private String itemKey;

    @Column(name = "read_at", nullable = false)
    private Instant readAt = Instant.now();

    protected MerchantNotificationRead() {}

    public static final class Pk implements Serializable {
        private UUID shopId;
        private UUID userId;
        private String itemKey;

        public Pk() {}

        public Pk(UUID shopId, UUID userId, String itemKey) {
            this.shopId = shopId;
            this.userId = userId;
            this.itemKey = itemKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(shopId, pk.shopId)
                && Objects.equals(userId, pk.userId)
                && Objects.equals(itemKey, pk.itemKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shopId, userId, itemKey);
        }
    }
}
