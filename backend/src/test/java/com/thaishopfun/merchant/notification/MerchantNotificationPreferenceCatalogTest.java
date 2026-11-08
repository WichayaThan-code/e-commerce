package com.thaishopfun.merchant.notification;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MerchantNotificationPreferenceCatalogTest {

    @Test
    void defaultsEnableExistingBellTypes() {
        var enabled = MerchantNotificationPreferenceCatalog.enabledEventTypes(
            MerchantNotificationPreferenceCatalog.defaultsInApp());
        assertTrue(enabled.contains("ORDER_NEW"));
        assertTrue(enabled.contains("PAYMENT_SLIP_PENDING"));
        assertTrue(enabled.contains("REFUND_REQUESTED"));
        assertTrue(enabled.contains("LOW_STOCK"));
        assertTrue(enabled.contains("PRODUCT_REVIEW_NEW"));
        assertTrue(enabled.contains("SHIPMENT_EXCEPTION"));
        assertTrue(enabled.contains(MerchantNotificationTypes.SHOP_SUSPENDED));
        assertTrue(enabled.contains(MerchantNotificationTypes.SHOP_MEMBER_ROLE_CHANGED));
        assertTrue(enabled.contains(MerchantNotificationTypes.SHOP_PLAN_CHANGED));
    }

    @Test
    void optionalOffHidesTypesButMandatoryStays() {
        Map<String, Boolean> prefs = new HashMap<>(MerchantNotificationPreferenceCatalog.defaultsInApp());
        prefs.put("ORDERS", false);
        prefs.put("SHOP_STATUS", false); // client tamper — catalog still forces mandatory on via enabledEventTypes
        var enabled = MerchantNotificationPreferenceCatalog.enabledEventTypes(prefs);
        assertFalse(enabled.contains("ORDER_NEW"));
        assertTrue(enabled.contains(MerchantNotificationTypes.SHOP_SUSPENDED));
        assertTrue(enabled.contains(MerchantNotificationTypes.SHOP_RESTORED));
    }

    @Test
    void unknownCategoryCodeRejectedByLookup() {
        assertTrue(MerchantNotificationPreferenceCatalog.byCode("PAYOUTS").isEmpty());
        assertTrue(MerchantNotificationPreferenceCatalog.byEventType("WALLET_WITHDRAW").isEmpty());
    }
}
