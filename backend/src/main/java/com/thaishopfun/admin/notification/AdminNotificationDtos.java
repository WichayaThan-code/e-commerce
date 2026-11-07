package com.thaishopfun.admin.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminNotificationDtos {
    private AdminNotificationDtos() {}

    public record Item(
        UUID id,
        String type,
        String entityType,
        String entityId,
        Long shopId,
        String shopName,
        String href,
        boolean unread,
        Instant createdAt
    ) {}

    public record Page(List<Item> items, String nextCursor, long unreadCount) {}

    public record UnreadCount(long count) {}
}
