package com.thaishopfun.admin.notification;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {
    @Mock AdminNotificationRepository notifications;
    @Mock AdminNotificationReadRepository reads;
    @Mock AuthorizationService authorization;

    private AdminNotificationService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminNotificationService(notifications, reads, authorization);
    }

    @Test
    void hrefMapsToExistingAdminRoutes() {
        var application = new AdminNotification(
            AdminNotificationTypes.SHOP_APPLICATION_SUBMITTED,
            AdminNotificationTypes.ENTITY_SHOP,
            "42",
            42L,
            "Demo Shop",
            AdminNotificationTypes.applicationDedupe(42),
            "PLATFORM_APPLICATION_READ"
        );
        assertEquals("/admin/store-applications?shop=42", AdminNotificationService.hrefFor(application));

        var requestId = UUID.randomUUID();
        var reinstatement = new AdminNotification(
            AdminNotificationTypes.SHOP_REINSTATEMENT_REQUESTED,
            AdminNotificationTypes.ENTITY_SHOP,
            "7",
            7L,
            "Suspended Shop",
            AdminNotificationTypes.reinstatementDedupe(requestId),
            "PLATFORM_SHOP_RESTORE"
        );
        assertEquals("/admin/stores?shopId=7", AdminNotificationService.hrefFor(reinstatement));

        var orderId = UUID.randomUUID();
        var refund = new AdminNotification(
            AdminNotificationTypes.REFUND_DISPUTED,
            AdminNotificationTypes.ENTITY_ORDER,
            orderId.toString(),
            9L,
            "Order Shop",
            AdminNotificationTypes.refundDisputedDedupe(UUID.randomUUID()),
            "PLATFORM_SHOP_SUSPEND"
        );
        assertEquals("/admin/orders?orderId=" + orderId, AdminNotificationService.hrefFor(refund));
    }

    @Test
    void dedupeKeysAreStableAndDistinctPerEvent() {
        assertEquals("SHOP_APPLICATION_SUBMITTED:shop:12", AdminNotificationTypes.applicationDedupe(12));
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        assertTrue(!AdminNotificationTypes.reinstatementDedupe(a).equals(AdminNotificationTypes.reinstatementDedupe(b)));
    }

    @Test
    void cursorRoundTripPreservesNanos() {
        var at = Instant.parse("2026-11-07T14:22:33.123456789Z");
        var id = UUID.randomUUID();
        var encoded = AdminNotificationService.encodeCursor(at, id);
        assertEquals(at, AdminNotificationService.parseCursorInstant(
            new String(java.util.Base64.getUrlDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8).split("\\|", 2)[0]
        ));
        when(authorization.isSuperAdmin(userId)).thenReturn(true);
        when(notifications.pageAfterForPermissions(any(), eq(at), eq(id), any())).thenReturn(List.of());
        when(notifications.countUnreadForUser(eq(userId), any())).thenReturn(0L);

        var page = service.list(userId, encoded, 10);
        assertTrue(page.items().isEmpty());
        assertNull(page.nextCursor());
        verify(notifications).pageAfterForPermissions(any(), eq(at), eq(id), any());
        verify(notifications, never()).pageFirstForPermissions(any(), any());
    }

    @Test
    void firstPageWithoutCursorUsesPageFirstQuery() {
        when(authorization.isSuperAdmin(userId)).thenReturn(true);
        when(notifications.pageFirstForPermissions(any(), any())).thenReturn(List.of());
        when(notifications.countUnreadForUser(eq(userId), any())).thenReturn(0L);

        var page = service.list(userId, null, 20);
        assertTrue(page.items().isEmpty());
        assertEquals(0, page.unreadCount());
        verify(notifications).pageFirstForPermissions(any(), any());
        verify(notifications, never()).pageAfterForPermissions(any(), any(), any(), any());
    }

    @Test
    void blankCursorUsesFirstPageQuery() {
        when(authorization.isSuperAdmin(userId)).thenReturn(true);
        when(notifications.pageFirstForPermissions(any(), any())).thenReturn(List.of());
        when(notifications.countUnreadForUser(eq(userId), any())).thenReturn(0L);

        service.list(userId, "   ", 5);
        verify(notifications).pageFirstForPermissions(any(), any());
        verify(notifications, never()).pageAfterForPermissions(any(), any(), any(), any());
    }

    @Test
    void malformedCursorReturnsBadRequest() {
        when(authorization.isSuperAdmin(userId)).thenReturn(true);

        var ex = assertThrows(AuthException.class, () -> service.list(userId, "not-a-cursor", 10));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_CURSOR", ex.getCode());
        assertEquals("cursor", ex.getField());
        verify(notifications, never()).pageFirstForPermissions(any(), any());
        verify(notifications, never()).pageAfterForPermissions(any(), any(), any(), any());
    }

    @Test
    void incompleteCursorReturnsBadRequest() {
        when(authorization.isSuperAdmin(userId)).thenReturn(true);
        var incomplete = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("2026-11-07T14:22:33Z|".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var ex = assertThrows(AuthException.class, () -> service.list(userId, incomplete, 10));
        assertEquals("INVALID_CURSOR", ex.getCode());
    }

    @Test
    void nextPageKeysetUsesCreatedAtAndIdAndBuildsNextCursor() {
        when(authorization.isSuperAdmin(userId)).thenReturn(true);
        var sharedAt = Instant.parse("2026-11-07T10:00:00.500Z");
        var a = note("a", sharedAt);
        var b = note("b", sharedAt);
        var c = note("c", Instant.parse("2026-11-07T09:59:00Z"));
        // size=2 → request 3 rows; hasMore true; next cursor from last returned (b)
        when(notifications.pageFirstForPermissions(any(), any())).thenReturn(List.of(a, b, c));
        when(reads.findReadIds(eq(userId), any())).thenReturn(List.of());
        when(notifications.countUnreadForUser(eq(userId), any())).thenReturn(3L);

        var page = service.list(userId, null, 2);
        assertEquals(2, page.items().size());
        assertEquals(3, page.unreadCount());
        assertEquals(AdminNotificationService.encodeCursor(b.getCreatedAt(), b.getId()), page.nextCursor());

        when(notifications.pageAfterForPermissions(any(), eq(b.getCreatedAt()), eq(b.getId()), any()))
            .thenReturn(List.of(c));
        when(reads.findReadIds(eq(userId), any())).thenReturn(List.of());

        var next = service.list(userId, page.nextCursor(), 2);
        assertEquals(1, next.items().size());
        assertEquals(c.getId(), next.items().get(0).id());
        assertNull(next.nextCursor());

        ArgumentCaptor<Instant> atCap = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<UUID> idCap = ArgumentCaptor.forClass(UUID.class);
        verify(notifications).pageAfterForPermissions(any(), atCap.capture(), idCap.capture(), any());
        assertEquals(b.getCreatedAt(), atCap.getValue());
        assertEquals(b.getId(), idCap.getValue());
    }

    @Test
    void emptyPermissionsReturnsEmptyWithoutQueryingInbox() {
        when(authorization.isSuperAdmin(userId)).thenReturn(false);
        when(authorization.hasPermission(eq(userId), any())).thenReturn(false);

        var ex = assertThrows(AuthException.class, () -> service.list(userId, null, 10));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(notifications, never()).pageFirstForPermissions(any(), any());
    }

    @Test
    void unreadCountWithNoPermissionsReturnsZeroWithoutEmptyIn() {
        when(authorization.isSuperAdmin(userId)).thenReturn(false);
        when(authorization.hasPermission(eq(userId), any())).thenReturn(false);

        assertEquals(0, service.unreadCount(userId).count());
        verify(notifications, never()).countUnreadForUser(any(), any());
    }

    @Test
    void markReadDoesNotChangeBusinessEntity() {
        when(authorization.isSuperAdmin(userId)).thenReturn(true);
        var note = note("shop", Instant.now());
        when(notifications.findById(note.getId())).thenReturn(java.util.Optional.of(note));
        when(reads.existsByNotificationIdAndUserId(note.getId(), userId)).thenReturn(false);

        service.markRead(userId, note.getId());
        verify(reads).save(any(AdminNotificationRead.class));
        // Only read-state write; notification row untouched
        verify(notifications, never()).save(any());
    }

    private static AdminNotification note(String suffix, Instant at) {
        return new AdminNotification(
            AdminNotificationTypes.SHOP_APPLICATION_SUBMITTED,
            AdminNotificationTypes.ENTITY_SHOP,
            "1",
            1L,
            "Shop " + suffix,
            "SHOP_APPLICATION_SUBMITTED:shop:test:" + suffix + ":" + UUID.randomUUID(),
            "PLATFORM_APPLICATION_READ",
            at
        );
    }
}
