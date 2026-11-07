package com.thaishopfun.merchant.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Merchant inbox type codes (i18n keys on the frontend). */
public final class MerchantNotificationTypes {
    public static final String SHOP_APPROVED = "SHOP_APPROVED";
    public static final String SHOP_REJECTED = "SHOP_REJECTED";
    public static final String SHOP_SUSPENDED = "SHOP_SUSPENDED";
    public static final String SHOP_RESTORED = "SHOP_RESTORED";
    public static final String SHOP_PLAN_CHANGED = "SHOP_PLAN_CHANGED";
    public static final String SHOP_MEMBER_ROLE_CHANGED = "SHOP_MEMBER_ROLE_CHANGED";
    public static final String SHOP_MEMBER_REMOVED = "SHOP_MEMBER_REMOVED";

    public static final String ENTITY_SHOP = "SHOP";
    public static final String ENTITY_MEMBER = "SHOP_MEMBER";
    public static final String ENTITY_PLAN = "SHOP_PLAN";

    private MerchantNotificationTypes() {}

    /** Truncate to millis so Java, JDBC Timestamp, and SQL agree on the event identity. */
    public static Instant eventInstant(Instant at) {
        Instant source = at != null ? at : Instant.now();
        return source.truncatedTo(ChronoUnit.MILLIS);
    }

    public static long eventEpochMilli(Instant at) {
        return eventInstant(at).toEpochMilli();
    }

    public static boolean isShopLifecycleType(String type) {
        return SHOP_APPROVED.equals(type)
            || SHOP_REJECTED.equals(type)
            || SHOP_SUSPENDED.equals(type)
            || SHOP_RESTORED.equals(type);
    }

    public static String shopApprovedDedupe(long shopNo, Instant at) {
        return SHOP_APPROVED + ":shop:" + shopNo + ":" + eventEpochMilli(at);
    }

    public static String shopRejectedDedupe(long shopNo, Instant at) {
        return SHOP_REJECTED + ":shop:" + shopNo + ":" + eventEpochMilli(at);
    }

    public static String shopSuspendedDedupe(long shopNo, Instant at) {
        return SHOP_SUSPENDED + ":shop:" + shopNo + ":" + eventEpochMilli(at);
    }

    public static String shopRestoredDedupe(long shopNo, Instant at) {
        return SHOP_RESTORED + ":shop:" + shopNo + ":" + eventEpochMilli(at);
    }

    public static String shopPlanChangedDedupe(long shopNo, Instant at, String planCode) {
        String plan = planCode == null || planCode.isBlank() ? "_" : planCode.trim();
        return SHOP_PLAN_CHANGED + ":shop:" + shopNo + ":" + eventEpochMilli(at) + ":" + plan;
    }

    public static String shopMemberRoleDedupe(UUID memberId, Instant at, String roleCode) {
        return SHOP_MEMBER_ROLE_CHANGED + ":member:" + memberId + ":" + eventEpochMilli(at) + ":" + roleCode;
    }

    public static String shopMemberRemovedDedupe(UUID memberId, Instant at) {
        return SHOP_MEMBER_REMOVED + ":member:" + memberId + ":" + eventEpochMilli(at);
    }
}
