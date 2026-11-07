package com.thaishopfun.admin.notification;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin header inbox. Class gate is PLATFORM_SHOP_READ (same baseline as /api/admin/dashboard).
 * Rows are further filtered by each event's required_permission inside the service
 * (APPLICATION_READ / SHOP_RESTORE / SHOP_SUSPEND / SHOP_READ).
 */
@RestController
@RequestMapping("/api/admin/notifications")
@RequirePermission("PLATFORM_SHOP_READ")
public class AdminNotificationController {
    private final AdminNotificationService notifications;

    public AdminNotificationController(AdminNotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public AdminNotificationDtos.Page list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer size
    ) {
        return notifications.list(RequestUser.id(jwt), cursor, size);
    }

    @GetMapping("/unread-count")
    public AdminNotificationDtos.UnreadCount unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return notifications.unreadCount(RequestUser.id(jwt));
    }

    @PostMapping("/{id}/read")
    public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        notifications.markRead(RequestUser.id(jwt), id);
    }

    @PostMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notifications.markAllRead(RequestUser.id(jwt));
    }
}
