package com.thaishopfun.admin.notification;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "admin_notification_reads")
@IdClass(AdminNotificationRead.Pk.class)
public class AdminNotificationRead {
    @Id
    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    protected AdminNotificationRead() {}

    public AdminNotificationRead(UUID notificationId, UUID userId) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.readAt = Instant.now();
    }

    public UUID getNotificationId() { return notificationId; }
    public UUID getUserId() { return userId; }
    public Instant getReadAt() { return readAt; }

    public static class Pk implements Serializable {
        private UUID notificationId;
        private UUID userId;

        public Pk() {}

        public Pk(UUID notificationId, UUID userId) {
            this.notificationId = notificationId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(notificationId, pk.notificationId) && Objects.equals(userId, pk.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(notificationId, userId);
        }
    }
}
