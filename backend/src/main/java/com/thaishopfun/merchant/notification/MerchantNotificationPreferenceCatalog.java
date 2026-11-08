package com.thaishopfun.merchant.notification;

import java.util.*;

/**
 * Server-owned category catalog for merchant in-app notification preferences.
 * Maps derive-on-read / durable event types to categories, defaults, and mandatory policy.
 */
public final class MerchantNotificationPreferenceCatalog {
    public static final String CHANNEL_IN_APP = "inApp";

    public enum Category {
        ORDERS(
            "ORDERS",
            false,
            true,
            "ORDER_READ",
            List.of("ORDER_NEW", "ORDER_PAID", "ORDER_CANCELLED"),
            "Optional order lifecycle alerts for the bell"
        ),
        PAYMENTS(
            "PAYMENTS",
            false,
            true,
            "ORDER_READ",
            List.of("PAYMENT_SLIP_PENDING"),
            "Payment slip review alerts"
        ),
        REFUNDS(
            "REFUNDS",
            false,
            true,
            "ORDER_READ",
            List.of("REFUND_REQUESTED", "REFUND_DISPUTED", "REFUND_COMPLETED"),
            "Refund request and dispute alerts"
        ),
        CUSTOMERS(
            "CUSTOMERS",
            false,
            true,
            "CUSTOMER_READ",
            List.of("CUSTOMER_NEW"),
            "First-order customer alerts"
        ),
        INVENTORY(
            "INVENTORY",
            false,
            true,
            "PRODUCT_READ",
            List.of("LOW_STOCK"),
            "Low-stock inventory alerts"
        ),
        REVIEWS(
            "REVIEWS",
            false,
            true,
            "PRODUCT_READ",
            List.of("PRODUCT_REVIEW_NEW"),
            "New product review alerts"
        ),
        SHIPPING(
            "SHIPPING",
            false,
            true,
            "SHIPMENT_READ",
            List.of("SHIPMENT_EXCEPTION"),
            "Shipment exception alerts"
        ),
        SHOP_STATUS(
            "SHOP_STATUS",
            true,
            true,
            "SHOP_READ",
            List.of(
                MerchantNotificationTypes.SHOP_APPROVED,
                MerchantNotificationTypes.SHOP_REJECTED,
                MerchantNotificationTypes.SHOP_SUSPENDED,
                MerchantNotificationTypes.SHOP_RESTORED
            ),
            "Mandatory shop approve/reject/suspend/restore status"
        ),
        TEAM(
            "TEAM",
            true,
            true,
            "USER_ROLE_UPDATE",
            List.of(
                MerchantNotificationTypes.SHOP_MEMBER_ROLE_CHANGED,
                MerchantNotificationTypes.SHOP_MEMBER_REMOVED
            ),
            "Mandatory role/membership security changes for users with USER_ROLE_UPDATE"
        ),
        PLAN(
            "PLAN",
            false,
            true,
            "SHOP_READ",
            List.of(MerchantNotificationTypes.SHOP_PLAN_CHANGED),
            "Shop plan change alerts"
        );

        private final String code;
        private final boolean mandatory;
        private final boolean defaultInApp;
        private final String viewerPermission;
        private final List<String> eventTypes;
        private final String policyReason;

        Category(String code, boolean mandatory, boolean defaultInApp, String viewerPermission,
                 List<String> eventTypes, String policyReason) {
            this.code = code;
            this.mandatory = mandatory;
            this.defaultInApp = defaultInApp;
            this.viewerPermission = viewerPermission;
            this.eventTypes = List.copyOf(eventTypes);
            this.policyReason = policyReason;
        }

        public String code() { return code; }
        public boolean mandatory() { return mandatory; }
        public boolean defaultInApp() { return defaultInApp; }
        public String viewerPermission() { return viewerPermission; }
        public List<String> eventTypes() { return eventTypes; }
        public String policyReason() { return policyReason; }
    }

    private static final Map<String, Category> BY_CODE;
    private static final Map<String, Category> BY_TYPE;

    static {
        var byCode = new LinkedHashMap<String, Category>();
        var byType = new HashMap<String, Category>();
        for (Category c : Category.values()) {
            byCode.put(c.code(), c);
            for (String type : c.eventTypes()) {
                byType.put(type, c);
            }
        }
        BY_CODE = Collections.unmodifiableMap(byCode);
        BY_TYPE = Collections.unmodifiableMap(byType);
    }

    private MerchantNotificationPreferenceCatalog() {}

    public static Optional<Category> byCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(BY_CODE.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    public static Optional<Category> byEventType(String type) {
        if (type == null) return Optional.empty();
        return Optional.ofNullable(BY_TYPE.get(type.trim()));
    }

    public static Collection<Category> all() {
        return BY_CODE.values();
    }

    /** Effective enabled event types given stored overrides (missing key = default). */
    public static Set<String> enabledEventTypes(Map<String, Boolean> inAppByCategory) {
        var enabled = new HashSet<String>();
        for (Category c : Category.values()) {
            boolean on = c.mandatory() || inAppByCategory.getOrDefault(c.code(), c.defaultInApp());
            if (on) enabled.addAll(c.eventTypes());
        }
        return Set.copyOf(enabled);
    }

    public static Map<String, Boolean> defaultsInApp() {
        var map = new LinkedHashMap<String, Boolean>();
        for (Category c : Category.values()) {
            map.put(c.code(), c.defaultInApp());
        }
        return map;
    }
}
