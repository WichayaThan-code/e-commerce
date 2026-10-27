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
@Table(name = "merchant_notification_cursor")
@IdClass(MerchantNotificationCursor.Pk.class)
public class MerchantNotificationCursor {
    @Id
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "read_all_at", nullable = false)
    private Instant readAllAt = Instant.EPOCH;

    protected MerchantNotificationCursor() {}

    public static final class Pk implements Serializable {
        private UUID shopId;
        private UUID userId;

        public Pk() {}

        public Pk(UUID shopId, UUID userId) {
            this.shopId = shopId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(shopId, pk.shopId) && Objects.equals(userId, pk.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shopId, userId);
        }
    }
}
