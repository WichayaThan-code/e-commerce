package com.thaishopfun.merchant.notification;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MerchantNotificationTypesTest {
    @Test
    void dedupeKeysIncludeEventTimeSoRepeatTransitionsStayDistinct() {
        Instant first = Instant.ofEpochMilli(1_700_000_000_000L);
        Instant second = Instant.ofEpochMilli(1_700_000_000_200L);
        assertEquals(
            "SHOP_APPROVED:shop:12:1700000000000",
            MerchantNotificationTypes.shopApprovedDedupe(12, first)
        );
        assertNotEquals(
            MerchantNotificationTypes.shopSuspendedDedupe(12, first),
            MerchantNotificationTypes.shopRestoredDedupe(12, first)
        );
        assertNotEquals(
            MerchantNotificationTypes.shopApprovedDedupe(12, first),
            MerchantNotificationTypes.shopApprovedDedupe(12, second)
        );
    }

    @Test
    void eventInstantTruncatesToMillisForStableIdentity() {
        Instant nanos = Instant.ofEpochSecond(1_700_000_000L, 123_456_789L);
        assertEquals(1_700_000_000_123L, MerchantNotificationTypes.eventEpochMilli(nanos));
        assertEquals(
            MerchantNotificationTypes.shopRestoredDedupe(9, nanos),
            MerchantNotificationTypes.shopRestoredDedupe(9, Instant.ofEpochMilli(1_700_000_000_123L))
        );
    }

    @Test
    void retentionIsSevenRollingDays() {
        assertEquals(7 * 24, MerchantLifecycleNotificationWriter.RETENTION_HOURS);
    }
}
