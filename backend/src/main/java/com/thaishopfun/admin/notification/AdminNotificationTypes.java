package com.thaishopfun.admin.notification;

/** Stable admin inbox event type codes (also used as i18n keys on the frontend). */
public final class AdminNotificationTypes {
    public static final String SHOP_APPLICATION_SUBMITTED = "SHOP_APPLICATION_SUBMITTED";
    public static final String SHOP_REINSTATEMENT_REQUESTED = "SHOP_REINSTATEMENT_REQUESTED";
    public static final String REFUND_DISPUTED = "REFUND_DISPUTED";
    public static final String SHOP_PLAN_PURCHASE_PENDING_REVIEW = "SHOP_PLAN_PURCHASE_PENDING_REVIEW";

    public static final String ENTITY_SHOP = "SHOP";
    public static final String ENTITY_ORDER = "ORDER";
    public static final String ENTITY_PLAN_SUBSCRIPTION = "SHOP_PLAN_SUBSCRIPTION";

    private AdminNotificationTypes() {}

    public static String applicationDedupe(long shopId) {
        return SHOP_APPLICATION_SUBMITTED + ":shop:" + shopId;
    }

    public static String reinstatementDedupe(java.util.UUID requestId) {
        return SHOP_REINSTATEMENT_REQUESTED + ":request:" + requestId;
    }

    public static String refundDisputedDedupe(java.util.UUID refundId) {
        return REFUND_DISPUTED + ":refund:" + refundId;
    }

    public static String planPurchasePendingDedupe(java.util.UUID subscriptionId) {
        return SHOP_PLAN_PURCHASE_PENDING_REVIEW + ":sub:" + subscriptionId;
    }
}
