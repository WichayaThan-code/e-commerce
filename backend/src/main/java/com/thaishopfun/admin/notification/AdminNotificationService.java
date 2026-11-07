package com.thaishopfun.admin.notification;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.thaishopfun.admin.notification.AdminNotificationTypes.*;

@Service
public class AdminNotificationService {
    private static final List<String> INBOX_PERMISSIONS = List.of(
        "PLATFORM_APPLICATION_READ",
        "PLATFORM_SHOP_RESTORE",
        "PLATFORM_SHOP_SUSPEND",
        "PLATFORM_SHOP_READ"
    );

    private final AdminNotificationRepository notifications;
    private final AdminNotificationReadRepository reads;
    private final AuthorizationService authorization;

    public AdminNotificationService(
        AdminNotificationRepository notifications,
        AdminNotificationReadRepository reads,
        AuthorizationService authorization
    ) {
        this.notifications = notifications;
        this.reads = reads;
        this.authorization = authorization;
    }

    /** Persist one inbox event in the same transaction as the business write (rolls back together). */
    @Transactional
    public void publish(
        String type,
        String entityType,
        String entityId,
        Long shopId,
        String shopName,
        String dedupeKey,
        String requiredPermission
    ) {
        if (dedupeKey == null || dedupeKey.isBlank()) return;
        if (notifications.existsByDedupeKey(dedupeKey)) return;
        var safeName = shopName == null || shopName.isBlank() ? null : shopName.trim();
        if (safeName != null && safeName.length() > 255) safeName = safeName.substring(0, 255);
        try {
            notifications.save(new AdminNotification(
                type, entityType, entityId, shopId, safeName, dedupeKey, requiredPermission
            ));
        } catch (DataIntegrityViolationException ignored) {
            // concurrent submit with same dedupe_key — keep one row
        }
    }

    @Transactional
    public void publishShopApplication(long shopId, String shopName) {
        publish(
            SHOP_APPLICATION_SUBMITTED,
            ENTITY_SHOP,
            String.valueOf(shopId),
            shopId,
            shopName,
            applicationDedupe(shopId),
            "PLATFORM_APPLICATION_READ"
        );
    }

    @Transactional
    public void publishReinstatement(UUID requestId, long shopId, String shopName) {
        publish(
            SHOP_REINSTATEMENT_REQUESTED,
            ENTITY_SHOP,
            String.valueOf(shopId),
            shopId,
            shopName,
            reinstatementDedupe(requestId),
            "PLATFORM_SHOP_RESTORE"
        );
    }

    @Transactional
    public void publishRefundDisputed(UUID orderId, UUID refundId, Long shopId, String shopName) {
        publish(
            REFUND_DISPUTED,
            ENTITY_ORDER,
            orderId.toString(),
            shopId,
            shopName,
            refundDisputedDedupe(refundId),
            "PLATFORM_SHOP_SUSPEND"
        );
    }

    @Transactional(readOnly = true)
    public AdminNotificationDtos.UnreadCount unreadCount(UUID userId) {
        var permissions = visiblePermissions(userId);
        if (permissions.isEmpty()) return new AdminNotificationDtos.UnreadCount(0);
        return new AdminNotificationDtos.UnreadCount(notifications.countUnreadForUser(userId, permissions));
    }

    @Transactional(readOnly = true)
    public AdminNotificationDtos.Page list(UUID userId, String cursor, Integer size) {
        requireInboxAccess(userId);
        var permissions = visiblePermissions(userId);
        if (permissions.isEmpty()) return new AdminNotificationDtos.Page(List.of(), null, 0);

        int limit = size == null ? 20 : Math.max(1, Math.min(size, 50));
        var pageable = PageRequest.of(0, limit + 1);
        // Split first/next page so PostgreSQL never sees a typed-null Instant in "? is null"
        // (JPQL :cursorAt expands to several SQL placeholders; $5 then has no type → SQLState 42P18).
        List<AdminNotification> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = notifications.pageFirstForPermissions(permissions, pageable);
        } else {
            var decoded = requireCursor(cursor);
            rows = notifications.pageAfterForPermissions(permissions, decoded.at(), decoded.id(), pageable);
        }

        boolean hasMore = rows.size() > limit;
        if (hasMore) rows = rows.subList(0, limit);

        var ids = rows.stream().map(AdminNotification::getId).toList();
        var readIds = ids.isEmpty()
            ? Set.<UUID>of()
            : new HashSet<>(reads.findReadIds(userId, ids));

        var items = new ArrayList<AdminNotificationDtos.Item>(rows.size());
        for (var row : rows) {
            items.add(toItem(row, !readIds.contains(row.getId())));
        }

        String next = null;
        if (hasMore && !rows.isEmpty()) {
            var last = rows.get(rows.size() - 1);
            next = encodeCursor(last.getCreatedAt(), last.getId());
        }

        long unread = notifications.countUnreadForUser(userId, permissions);
        return new AdminNotificationDtos.Page(items, next, unread);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        requireInboxAccess(userId);
        var note = notifications.findById(notificationId)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "ไม่พบการแจ้งเตือน"));
        if (!canSee(userId, note.getRequiredPermission())) {
            throw new AuthException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "ไม่พบการแจ้งเตือน");
        }
        if (reads.existsByNotificationIdAndUserId(notificationId, userId)) return;
        try {
            reads.save(new AdminNotificationRead(notificationId, userId));
        } catch (DataIntegrityViolationException ignored) {
            // concurrent mark-read
        }
    }

    @Transactional
    public void markAllRead(UUID userId) {
        requireInboxAccess(userId);
        var permissions = visiblePermissions(userId);
        if (permissions.isEmpty()) return;
        for (var id : reads.findUnreadIds(userId, permissions)) {
            if (reads.existsByNotificationIdAndUserId(id, userId)) continue;
            try {
                reads.save(new AdminNotificationRead(id, userId));
            } catch (DataIntegrityViolationException ignored) {
                // concurrent
            }
        }
    }

    private void requireInboxAccess(UUID userId) {
        if (visiblePermissions(userId).isEmpty()) {
            throw new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "ไม่มีสิทธิ์ดูการแจ้งเตือน");
        }
    }

    private Set<String> visiblePermissions(UUID userId) {
        if (authorization.isSuperAdmin(userId)) {
            return new LinkedHashSet<>(INBOX_PERMISSIONS);
        }
        var allowed = new LinkedHashSet<String>();
        for (var code : INBOX_PERMISSIONS) {
            if (authorization.hasPermission(userId, code)) allowed.add(code);
        }
        return allowed;
    }

    private boolean canSee(UUID userId, String requiredPermission) {
        return authorization.isSuperAdmin(userId) || authorization.hasPermission(userId, requiredPermission);
    }

    private static AdminNotificationDtos.Item toItem(AdminNotification row, boolean unread) {
        return new AdminNotificationDtos.Item(
            row.getId(),
            row.getType(),
            row.getEntityType(),
            row.getEntityId(),
            row.getShopId(),
            row.getShopName(),
            hrefFor(row),
            unread,
            row.getCreatedAt()
        );
    }

    /** Internal admin routes only — never trust client-supplied URLs. */
    static String hrefFor(AdminNotification row) {
        return switch (row.getType()) {
            case SHOP_APPLICATION_SUBMITTED -> row.getShopId() == null
                ? "/admin/store-applications"
                : "/admin/store-applications?shop=" + row.getShopId();
            case SHOP_REINSTATEMENT_REQUESTED -> row.getShopId() == null
                ? "/admin/stores"
                : "/admin/stores?shopId=" + row.getShopId();
            case REFUND_DISPUTED -> "/admin/orders?orderId=" + row.getEntityId();
            default -> "/admin";
        };
    }

    private record Cursor(Instant at, UUID id) {}

    private static Cursor requireCursor(String cursor) {
        try {
            var raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            var parts = raw.split("\\|", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw invalidCursor();
            }
            return new Cursor(parseCursorInstant(parts[0]), UUID.fromString(parts[1]));
        } catch (AuthException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalidCursor();
        }
    }

    private static AuthException invalidCursor() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "cursor ไม่ถูกต้อง", "cursor");
    }

    /** ISO-8601 Instant (nanos preserved). Legacy epoch-millis form still accepted for in-flight clients. */
    static String encodeCursor(Instant at, UUID id) {
        var raw = at.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Instant parseCursorInstant(String raw) {
        try {
            return Instant.parse(raw);
        } catch (RuntimeException ignored) {
            return Instant.ofEpochMilli(Long.parseLong(raw));
        }
    }
}
