package com.thaishopfun.merchant.notification;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Derive-on-read merchant notification feed for the header bell.
 * Membership gate is SHOP_READ; the service further requires ORDER_READ / CUSTOMER_READ / PRODUCT_READ per type.
 */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/notifications")
@RequireShopPermission("SHOP_READ")
public class MerchantNotificationController {
    private final MerchantNotificationService notifications;

    public MerchantNotificationController(MerchantNotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public MerchantNotificationDtos.Page list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer size) {
        return notifications.list(RequestUser.id(jwt), shopId, cursor, size);
    }

    @GetMapping("/unread-count")
    public MerchantNotificationDtos.UnreadCount unreadCount(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId) {
        return notifications.unreadCount(RequestUser.id(jwt), shopId);
    }

    @PostMapping("/read-all")
    public void readAll(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId) {
        notifications.markAllRead(RequestUser.id(jwt), shopId);
    }

    @PostMapping("/{key}/read")
    public void readOne(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                        @PathVariable("key") String key) {
        notifications.markRead(RequestUser.id(jwt), shopId, key);
    }
}
