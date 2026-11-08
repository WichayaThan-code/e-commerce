package com.thaishopfun.merchant.notification;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Personal (user + shop) in-app notification preferences. SHOP_READ gates the route;
 * the service further checks category viewer permissions. Never accepts a target userId.
 */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/notification-preferences")
@RequireShopPermission("SHOP_READ")
public class MerchantNotificationPreferenceController {
    private final MerchantNotificationPreferenceService preferences;

    public MerchantNotificationPreferenceController(MerchantNotificationPreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public MerchantNotificationPreferenceDtos.PreferencesResponse get(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId
    ) {
        return preferences.get(RequestUser.id(jwt), shopId);
    }

    @PutMapping
    public MerchantNotificationPreferenceDtos.PreferencesResponse save(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @RequestBody MerchantNotificationPreferenceDtos.SaveRequest body
    ) {
        return preferences.save(RequestUser.id(jwt), shopId, body);
    }
}
