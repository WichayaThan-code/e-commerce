package com.thaishopfun.merchant.notification;

import com.thaishopfun.merchant.Shop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.thaishopfun.merchant.notification.MerchantNotificationTypes.*;

/**
 * Writes durable merchant lifecycle inbox rows in the same transaction as the shop status change.
 * Cleanup removes only {@code merchant_notifications} older than 7 days — never business/audit rows or admin inbox.
 *
 * <p>Shop lifecycle identity is {@code (shop_id, type, created_at)} where {@code created_at} is the business
 * event time (published_at / suspended_at / restored_at), truncated to millis. Dedupe keys must derive from
 * that same Instant so live publish, reconcile, and backfill cannot create two rows for one transition.
 */
@Service
public class MerchantLifecycleNotificationWriter {
    private static final Logger log = LoggerFactory.getLogger(MerchantLifecycleNotificationWriter.class);
    /** Rolling retention for merchant bell (hours). */
    public static final int RETENTION_HOURS = 7 * 24;

    private final NamedParameterJdbcTemplate jdbc;

    public MerchantLifecycleNotificationWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void publishShopApproved(Shop shop) {
        Instant at = eventInstant(shop.getPublishedAt() != null ? shop.getPublishedAt() : Instant.now());
        insert(shop.getUuid(), shop.getId(), SHOP_APPROVED, ENTITY_SHOP, String.valueOf(shop.getId()),
            shopApprovedDedupe(shop.getId(), at), "SHOP_READ", at);
    }

    @Transactional
    public void publishShopRejected(Shop shop) {
        Instant at = eventInstant(shop.getUpdatedAt() != null ? shop.getUpdatedAt() : Instant.now());
        insert(shop.getUuid(), shop.getId(), SHOP_REJECTED, ENTITY_SHOP, String.valueOf(shop.getId()),
            shopRejectedDedupe(shop.getId(), at), "SHOP_READ", at);
    }

    @Transactional
    public void publishShopSuspended(Shop shop) {
        Instant at = eventInstant(shop.getSuspendedAt() != null ? shop.getSuspendedAt() : Instant.now());
        insert(shop.getUuid(), shop.getId(), SHOP_SUSPENDED, ENTITY_SHOP, String.valueOf(shop.getId()),
            shopSuspendedDedupe(shop.getId(), at), "SHOP_READ", at);
    }

    @Transactional
    public void publishShopRestored(Shop shop) {
        Instant at = eventInstant(shop.getRestoredAt() != null ? shop.getRestoredAt() : Instant.now());
        insert(shop.getUuid(), shop.getId(), SHOP_RESTORED, ENTITY_SHOP, String.valueOf(shop.getId()),
            shopRestoredDedupe(shop.getId(), at), "SHOP_READ", at);
    }

    @Transactional
    public void publishShopPlanChanged(Shop shop, String planCode, Instant at) {
        Instant when = eventInstant(at);
        String code = planCode == null || planCode.isBlank() ? "" : planCode.trim();
        insert(shop.getUuid(), shop.getId(), SHOP_PLAN_CHANGED, ENTITY_PLAN, code.isEmpty() ? "_" : code,
            shopPlanChangedDedupe(shop.getId(), when, code), "SHOP_READ", when);
    }

    @Transactional
    public void publishShopMemberRoleChanged(UUID shopUuid, long shopNo, UUID memberId, String roleCode, Instant at) {
        Instant when = eventInstant(at);
        String role = roleCode == null ? "" : roleCode.trim();
        insert(shopUuid, shopNo, SHOP_MEMBER_ROLE_CHANGED, ENTITY_MEMBER, role.isEmpty() ? "_" : role,
            shopMemberRoleDedupe(memberId, when, role), "USER_ROLE_UPDATE", when);
    }

    @Transactional
    public void publishShopMemberRemoved(UUID shopUuid, long shopNo, UUID memberId, Instant at) {
        Instant when = eventInstant(at);
        insert(shopUuid, shopNo, SHOP_MEMBER_REMOVED, ENTITY_MEMBER, memberId.toString(),
            shopMemberRemovedDedupe(memberId, when), "USER_ROLE_UPDATE", when);
    }

    private void insert(
        UUID shopUuid,
        Long shopNo,
        String type,
        String entityType,
        String entityId,
        String dedupeKey,
        String requiredPermission,
        Instant createdAt
    ) {
        if (shopUuid == null || shopNo == null || dedupeKey == null || dedupeKey.isBlank()) return;
        Instant at = eventInstant(createdAt);
        var params = new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("shopId", shopUuid)
            .addValue("type", type)
            .addValue("entityType", entityType)
            .addValue("entityId", entityId == null ? "" : entityId)
            .addValue("dedupeKey", dedupeKey)
            .addValue("requiredPermission", requiredPermission)
            .addValue("createdAt", Timestamp.from(at));

        // Same business transition may already exist under another dedupe_key (legacy backfill / float millis).
        if (isShopLifecycleType(type) && existsLifecycleEvent(shopUuid, type, at)) return;
        if (existsDedupe(dedupeKey)) return;

        try {
            jdbc.update("""
                INSERT INTO merchant_notifications
                    (id, shop_id, type, entity_type, entity_id, dedupe_key, required_permission, created_at)
                VALUES
                    (:id, :shopId, :type, :entityType, :entityId, :dedupeKey, :requiredPermission, :createdAt)
                """, params);
        } catch (DataIntegrityViolationException ignored) {
            // concurrent publish with same dedupe_key or (shop_id, type, created_at)
        }
    }

    private boolean existsDedupe(String dedupeKey) {
        Integer existing = jdbc.query(
            "SELECT 1 FROM merchant_notifications WHERE dedupe_key = :dedupeKey",
            new MapSqlParameterSource("dedupeKey", dedupeKey),
            rs -> rs.next() ? 1 : null
        );
        return existing != null;
    }

    private boolean existsLifecycleEvent(UUID shopUuid, String type, Instant createdAt) {
        Integer existing = jdbc.query("""
            SELECT 1 FROM merchant_notifications
             WHERE shop_id = :shopId AND type = :type AND created_at = :createdAt
            """, new MapSqlParameterSource()
                .addValue("shopId", shopUuid)
                .addValue("type", type)
                .addValue("createdAt", Timestamp.from(eventInstant(createdAt))),
            rs -> rs.next() ? 1 : null
        );
        return existing != null;
    }

    /**
     * Heals durable rows for shop lifecycle timestamps still within the 7-day window when a prior
     * process changed status without a write hook. Skips when a row already exists for the same
     * (shop, type, event time), even if the legacy dedupe_key format differs.
     */
    @Scheduled(fixedDelayString = "${app.merchant-notifications.reconcile-delay-ms:3600000}")
    @Transactional
    public void reconcileLifecycleFromShops() {
        Instant cutoff = Instant.now().minus(RETENTION_HOURS, ChronoUnit.HOURS);
        var rows = jdbc.query("""
            SELECT id, shop_no, status, published_at, suspended_at, restored_at
              FROM shops
             WHERE (published_at IS NOT NULL AND published_at >= :cutoff)
                OR (suspended_at IS NOT NULL AND suspended_at >= :cutoff)
                OR (restored_at IS NOT NULL AND restored_at >= :cutoff)
            """, new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)), (rs, i) -> new Object[]{
            rs.getObject("id", UUID.class),
            rs.getLong("shop_no"),
            rs.getString("status"),
            rs.getTimestamp("published_at"),
            rs.getTimestamp("suspended_at"),
            rs.getTimestamp("restored_at")
        });
        int written = 0;
        for (var row : rows) {
            UUID shopUuid = (UUID) row[0];
            long shopNo = (Long) row[1];
            String status = (String) row[2];
            Timestamp publishedAt = (Timestamp) row[3];
            Timestamp suspendedAt = (Timestamp) row[4];
            Timestamp restoredAt = (Timestamp) row[5];
            if (publishedAt != null && !publishedAt.toInstant().isBefore(cutoff)
                && ("APPROVED".equals(status) || "ACTIVE".equals(status) || "SUSPENDED".equals(status))) {
                Instant at = eventInstant(publishedAt.toInstant());
                if (!existsLifecycleEvent(shopUuid, SHOP_APPROVED, at)) {
                    insert(shopUuid, shopNo, SHOP_APPROVED, ENTITY_SHOP, String.valueOf(shopNo),
                        shopApprovedDedupe(shopNo, at), "SHOP_READ", at);
                    written++;
                }
            }
            if (suspendedAt != null && !suspendedAt.toInstant().isBefore(cutoff) && "SUSPENDED".equals(status)) {
                Instant at = eventInstant(suspendedAt.toInstant());
                if (!existsLifecycleEvent(shopUuid, SHOP_SUSPENDED, at)) {
                    insert(shopUuid, shopNo, SHOP_SUSPENDED, ENTITY_SHOP, String.valueOf(shopNo),
                        shopSuspendedDedupe(shopNo, at), "SHOP_READ", at);
                    written++;
                }
            }
            if (restoredAt != null && !restoredAt.toInstant().isBefore(cutoff)
                && ("APPROVED".equals(status) || "ACTIVE".equals(status))) {
                Instant at = eventInstant(restoredAt.toInstant());
                if (!existsLifecycleEvent(shopUuid, SHOP_RESTORED, at)) {
                    insert(shopUuid, shopNo, SHOP_RESTORED, ENTITY_SHOP, String.valueOf(shopNo),
                        shopRestoredDedupe(shopNo, at), "SHOP_READ", at);
                    written++;
                }
            }
        }
        if (written > 0) {
            log.info("Reconciled {} merchant lifecycle notification row(s) from shop timestamps", written);
        }
    }

    /** Hourly purge of expired durable merchant inbox rows only. */
    @Scheduled(fixedDelayString = "${app.merchant-notifications.cleanup-delay-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        Instant cutoff = Instant.now().minus(RETENTION_HOURS, ChronoUnit.HOURS);
        int deleted = jdbc.update(
            "DELETE FROM merchant_notifications WHERE created_at < :cutoff",
            new MapSqlParameterSource("cutoff", Timestamp.from(cutoff))
        );
        if (deleted > 0) {
            log.info("Purged {} expired merchant_notifications (older than {}h)", deleted, RETENTION_HOURS);
        }
    }
}
