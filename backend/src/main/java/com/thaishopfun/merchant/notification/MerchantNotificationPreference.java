package com.thaishopfun.merchant.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "merchant_notification_preferences")
@IdClass(MerchantNotificationPreference.Pk.class)
public class MerchantNotificationPreference {
    @Id
    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Matches Flyway V57 jsonb column; service reads/writes via JDBC as text. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences_json", nullable = false, columnDefinition = "jsonb")
    private String preferencesJson = "{}";

    @Column(nullable = false)
    private long version = 1L;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MerchantNotificationPreference() {}

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
