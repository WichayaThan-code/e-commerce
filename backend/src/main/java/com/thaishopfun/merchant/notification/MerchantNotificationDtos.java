package com.thaishopfun.merchant.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class MerchantNotificationDtos {
    private MerchantNotificationDtos() {}

    public record Page(List<Item> items, String nextCursor) {}

    /**
     * {@code titleParams} are interpolation keys for frontend i18n ({@code notifications.types.*}).
     * Never includes buyer user id.
     */
    public record Item(
        String key,
        String type,
        Map<String, Object> titleParams,
        String orderNo,
        String customerKey,
        BigDecimal amount,
        Instant createdAt,
        boolean read
    ) {}

    public record UnreadCount(int count) {}
}
