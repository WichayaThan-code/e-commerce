package com.thaishopfun.merchant.plan;

import com.thaishopfun.admin.AdminShopUsageService;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Single place for shop plan quota enforcement. Counters match {@link AdminShopUsageService}
 * / {@link MerchantPlanUsageService} (products, rolling 30-day orders, storage bytes).
 * Blank or unknown {@code plan_code} resolves as FREE for enforcement.
 */
@Service
public class PlanQuotaService {
    private static final Logger log = LoggerFactory.getLogger(PlanQuotaService.class);
    public static final String DEFAULT_PLAN = "FREE";
    /** Buyer-facing order block — no technical codes. */
    public static final String BUYER_ORDER_FULL_MESSAGE =
        "ร้านนี้รับคำสั่งซื้อครบตามจำนวนในเดือนนี้แล้ว";

    private final ShopRepository shops;
    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plainJdbc;

    public PlanQuotaService(ShopRepository shops, NamedParameterJdbcTemplate jdbc, JdbcTemplate plainJdbc) {
        this.shops = shops;
        this.jdbc = jdbc;
        this.plainJdbc = plainJdbc;
    }

    /** Assign FREE on brand-new shops only when plan_code is still empty. Never overwrites. */
    public void assignFreeIfMissing(Shop shop) {
        if (shop == null) return;
        var current = shop.getPlanCode();
        if (current != null && !current.isBlank()) return;
        if (!isPlanActive(DEFAULT_PLAN)) {
            log.error("Cannot assign FREE plan to shop {}: FREE plan missing or inactive", shop.getId());
            return;
        }
        shop.assignPlanCode(DEFAULT_PLAN);
    }

    /**
     * Quota for published products only (DRAFT/ARCHIVED unlimited).
     * Locks the shop row ({@code SELECT … FOR UPDATE}) so concurrent publishes cannot race past the limit.
     * Must run inside the same transaction as the status change to PUBLISHED.
     */
    @Transactional
    public void assertCanPublishProducts(Long shopNo, int additional) {
        if (additional <= 0) return;
        var shopUuid = lockShop(shopNo);
        var shop = requireShop(shopNo);
        var plan = resolveEffectivePlan(shop.getPlanCode());
        Long limit = plan.limitProducts();
        if (limit == null) return;
        long used = countPublishedProducts(shopUuid);
        if (used + additional > limit) {
            long remaining = Math.max(limit - used, 0L);
            throw exceeded(
                "PRODUCT_QUOTA_EXCEEDED",
                "แพ็กเกจ " + plan.name() + " เผยแพร่สินค้าได้สูงสุด " + limit
                    + " รายการ (ตอนนี้เผยแพร่อยู่ " + used + "/" + limit
                    + ") เผยแพร่ได้อีก " + remaining
                    + " รายการ — กรุณาเลิกเผยแพร่สินค้าอื่นก่อน หรืออัปเกรดแพ็กเกจ"
            );
        }
    }

    /** Published, non-deleted product mothers — same counter as enforcement. */
    @Transactional(readOnly = true)
    public long countPublishedProducts(UUID shopId) {
        Long n = plainJdbc.queryForObject(
            "SELECT COUNT(*) FROM products WHERE shop_id = ? AND deleted_at IS NULL AND status = 'PUBLISHED'",
            Long.class, shopId);
        return n == null ? 0L : n;
    }

    @Transactional(readOnly = true)
    public long countPublishedProducts(Long shopNo) {
        var shop = requireShop(shopNo);
        return countPublishedProducts(shop.getUuid());
    }

    @Transactional(readOnly = true)
    public void assertCanUploadBytes(Long shopNo, long bytes) {
        if (bytes <= 0) return;
        var shop = requireShop(shopNo);
        var plan = resolveEffectivePlan(shop.getPlanCode());
        Long limit = plan.limitStorageBytes();
        if (limit == null) return;
        long used = countTrackedBytes(shop.getUuid());
        if (used + bytes > limit) {
            throw exceeded(
                "STORAGE_QUOTA_EXCEEDED",
                "พื้นที่ไฟล์ของร้านเต็มแล้ว (ใช้ไป " + formatBytes(used) + " จาก " + formatBytes(limit)
                    + ") — ลบไฟล์ที่ไม่ใช้ หรืออัปเกรดแพ็กเกจ"
            );
        }
    }

    /** Same shop UUID overload used by payment/media uploads. */
    @Transactional(readOnly = true)
    public void assertCanUploadBytes(UUID shopUuid, long bytes) {
        if (bytes <= 0 || shopUuid == null) return;
        var shop = shops.findById(shopUuid).orElse(null);
        if (shop == null) return;
        assertCanUploadBytes(shop.getId(), bytes);
    }

    /**
     * Order quota uses the same rolling {@link MerchantPlanUsageService#ORDERS_ROLLING_DAYS}-day window
     * as plan-usage (not calendar month). Blocks the buyer with a plain Thai message and notifies the shop.
     */
    @Transactional(readOnly = true)
    public void assertCanAcceptOrder(Long shopNo) {
        var shop = requireShop(shopNo);
        assertCanAcceptOrder(shop);
    }

    @Transactional(readOnly = true)
    public void assertCanAcceptOrder(Shop shop) {
        var plan = resolveEffectivePlan(shop.getPlanCode());
        Long limit = plan.limitOrdersPerMonth();
        if (limit == null) return;
        long used = countOrdersRolling(shop.getUuid());
        if (used >= limit) {
            notifyOrderQuota(shop, used, limit);
            throw new AuthException(HttpStatus.CONFLICT, "ORDER_QUOTA_EXCEEDED", BUYER_ORDER_FULL_MESSAGE);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanAcceptOrdersForShops(List<UUID> shopUuids) {
        if (shopUuids == null || shopUuids.isEmpty()) return;
        for (var id : shopUuids) {
            if (id == null) continue;
            shops.findById(id).ifPresent(this::assertCanAcceptOrder);
        }
    }

    @Transactional(readOnly = true)
    public void assertCheckoutForBuyer(UUID buyerId) {
        List<UUID> shopIds = plainJdbc.query(
            """
            SELECT DISTINCT c.shop_id FROM carts c
              JOIN cart_items i ON i.cart_id = c.id
             WHERE c.buyer_user_id = ? AND i.is_selected = TRUE
            """,
            (rs, i) -> rs.getObject("shop_id", UUID.class),
            buyerId
        );
        assertCanAcceptOrdersForShops(shopIds);
    }

    @Transactional(readOnly = true)
    public EffectivePlan resolveEffectivePlan(String planCode) {
        var code = (planCode == null || planCode.isBlank()) ? DEFAULT_PLAN : planCode.trim().toUpperCase(Locale.ROOT);
        var row = loadPlan(code);
        if (row == null) {
            log.warn("Plan {} not found — treating as FREE for quota", code);
            row = loadPlan(DEFAULT_PLAN);
        }
        if (row == null) {
            // Absolute fallback: FREE limits from V62 seed if catalog is broken.
            return new EffectivePlan(DEFAULT_PLAN, "Free", 5L, 50L, 2L, 104857600L);
        }
        return row;
    }

    public boolean isPlanActive(String code) {
        if (code == null || code.isBlank()) return false;
        Long n = plainJdbc.queryForObject(
            "SELECT COUNT(*) FROM plans WHERE code = ? AND active = TRUE", Long.class, code.trim().toUpperCase(Locale.ROOT));
        return n != null && n > 0;
    }

    private Shop requireShop(Long shopNo) {
        return shops.findByShopId(shopNo)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้าน"));
    }

    /** Serialize publish quota checks against concurrent writers for this shop. */
    private UUID lockShop(Long shopNo) {
        UUID id = plainJdbc.query(
            "SELECT id FROM shops WHERE shop_no = ? FOR UPDATE",
            rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
            shopNo
        );
        if (id == null) throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้าน");
        return id;
    }

    private long countOrdersRolling(UUID shopId) {
        var since = Timestamp.from(Instant.now().minus(Duration.ofDays(MerchantPlanUsageService.ORDERS_ROLLING_DAYS)));
        Long n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE shop_id = :id AND placed_at >= :since",
            new MapSqlParameterSource("id", shopId).addValue("since", since),
            Long.class
        );
        return n == null ? 0L : n;
    }

    private long countTrackedBytes(UUID shopId) {
        Long media = plainJdbc.queryForObject(
            "SELECT COALESCE(SUM(file_size), 0) FROM media_files WHERE shop_id = ? AND status <> 'DELETED'",
            Long.class, shopId);
        Long docs = plainJdbc.queryForObject("""
            SELECT COALESCE(SUM(d.file_size), 0)
              FROM shops s JOIN merchant_documents d ON d.application_id = s.application_id
             WHERE s.id = ?
            """, Long.class, shopId);
        return (media == null ? 0L : media) + (docs == null ? 0L : docs);
    }

    private EffectivePlan loadPlan(String code) {
        var rows = plainJdbc.query("""
            SELECT code, name, limit_products, limit_orders_per_month, limit_staff, limit_storage_bytes
              FROM plans WHERE code = ?
            """, (rs, i) -> new EffectivePlan(
            rs.getString("code"),
            rs.getString("name"),
            longOrNull(rs, "limit_products"),
            longOrNull(rs, "limit_orders_per_month"),
            longOrNull(rs, "limit_staff"),
            longOrNull(rs, "limit_storage_bytes")
        ), code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void notifyOrderQuota(Shop shop, long used, long limit) {
        // Merchant inbox type for order quota is not in the lifecycle catalog yet — log for ops / later wiring.
        log.warn("ORDER_QUOTA_EXCEEDED shopNo={} used={}/{} (rolling {}d)",
            shop.getId(), used, limit, MerchantPlanUsageService.ORDERS_ROLLING_DAYS);
    }

    private static AuthException exceeded(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    private static Long longOrNull(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Object v = rs.getObject(col);
        return v == null ? null : rs.getLong(col);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes / 1024.0;
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return (value >= 100 ? String.format(Locale.ROOT, "%.0f", value) : String.format(Locale.ROOT, "%.1f", value))
            + " " + units[unit];
    }

    public record EffectivePlan(
        String code, String name,
        Long limitProducts, Long limitOrdersPerMonth, Long limitStaff, Long limitStorageBytes
    ) {}
}
