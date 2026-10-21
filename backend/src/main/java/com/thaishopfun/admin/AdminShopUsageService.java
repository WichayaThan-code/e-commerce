package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminPlatformDtos.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Plans and usage for the platform admin. The schema (V1-V31) has no plan / subscription / usage table, so:
 * <ul>
 *   <li>the plan catalog is empty (source NONE), every shop has planCode null and all limits null (= not limited);</li>
 *   <li>usage is computed live from the real tables: products / product_skus / product_images, orders, shop_members,
 *       shop_customers, coupons, media_files (payment slips, packing / delivery photos ...) and merchant_documents.
 *       Product image / logo bytes are stored on disk only (product_images has no size column), so they are counted,
 *       not measured.</li>
 * </ul>
 * One grouped query per table for a whole page of shops.
 */
@Service
public class AdminShopUsageService {
    static final List<String> OPEN_ORDER_EXCLUDED = List.of("COMPLETED", "CANCELLED", "REFUNDED");
    private static final String STORAGE_NOTE = "trackedBytes = media_files + merchant_documents; product images / logo / cover are files on disk without a size column (productImages = count only)";

    private final NamedParameterJdbcTemplate jdbc;

    public AdminShopUsageService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PlanCatalog plans() {
        return new PlanCatalog("NONE", List.of(),
            "ฐานข้อมูลยังไม่มีตาราง plan / subscription / usage (V1-V31): ยังไม่มีแพ็กเกจและไม่มีการจำกัดการใช้งาน; usage คำนวณจากข้อมูลจริง");
    }

    /** Shop identity used for the usage rows. */
    public record ShopKey(UUID id, Long shopNo, String name, String status) {}

    @Transactional(readOnly = true)
    public Map<UUID, ShopUsage> usage(List<ShopKey> keys) {
        var result = new LinkedHashMap<UUID, ShopUsage>();
        if (keys.isEmpty()) return result;
        var ids = keys.stream().map(ShopKey::id).toList();
        var p = new MapSqlParameterSource("ids", ids)
            .addValue("since", Timestamp.from(Instant.now().minus(Duration.ofDays(30))))
            .addValue("open", OPEN_ORDER_EXCLUDED);

        var products = new HashMap<UUID, long[]>(); // total, published, draft, archived, skus, images
        jdbc.query("""
            SELECT shop_id, status, COUNT(*) AS n FROM products WHERE shop_id IN (:ids) AND deleted_at IS NULL GROUP BY shop_id, status
            """, p, rs -> {
            var row = products.computeIfAbsent(rs.getObject("shop_id", UUID.class), k -> new long[6]);
            var n = rs.getLong("n");
            row[0] += n;
            switch (String.valueOf(rs.getString("status"))) {
                case "PUBLISHED" -> row[1] += n;
                case "DRAFT" -> row[2] += n;
                case "ARCHIVED" -> row[3] += n;
                default -> { }
            }
        });
        jdbc.query("""
            SELECT shop_id, COUNT(*) AS n FROM product_skus WHERE shop_id IN (:ids) AND deleted_at IS NULL AND is_active = TRUE GROUP BY shop_id
            """, p, rs -> { products.computeIfAbsent(rs.getObject("shop_id", UUID.class), k -> new long[6])[4] = rs.getLong("n"); });
        jdbc.query("""
            SELECT pr.shop_id, COUNT(*) AS n FROM product_images pi JOIN products pr ON pr.id = pi.product_id
             WHERE pr.shop_id IN (:ids) AND pr.deleted_at IS NULL GROUP BY pr.shop_id
            """, p, rs -> { products.computeIfAbsent(rs.getObject("shop_id", UUID.class), k -> new long[6])[5] = rs.getLong("n"); });

        var orders = new HashMap<UUID, Object[]>();
        jdbc.query("""
            SELECT shop_id, COUNT(*) AS total,
                   SUM(CASE WHEN placed_at >= :since THEN 1 ELSE 0 END) AS recent,
                   SUM(CASE WHEN status NOT IN (:open) THEN 1 ELSE 0 END) AS open_orders,
                   SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
                   SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled,
                   SUM(CASE WHEN payment_status IN ('PAID', 'PARTIALLY_REFUNDED') THEN grand_total ELSE 0 END) AS paid
              FROM orders WHERE shop_id IN (:ids) GROUP BY shop_id
            """, p, rs -> {
            orders.put(rs.getObject("shop_id", UUID.class), new Object[]{rs.getLong("total"), rs.getLong("recent"), rs.getLong("open_orders"),
                rs.getLong("completed"), rs.getLong("cancelled"), rs.getBigDecimal("paid")});
        });

        var staff = new HashMap<UUID, TreeMap<String, Long>>();
        jdbc.query("""
            SELECT shop_id, role, COUNT(*) AS n FROM shop_members WHERE shop_id IN (:ids) AND status = 'ACTIVE' AND removed_at IS NULL GROUP BY shop_id, role
            """, p, rs -> { staff.computeIfAbsent(rs.getObject("shop_id", UUID.class), k -> new TreeMap<>()).put(rs.getString("role"), rs.getLong("n")); });

        var customers = counts("SELECT shop_id, COUNT(*) AS n FROM shop_customers WHERE shop_id IN (:ids) AND deleted_at IS NULL AND status = 'ACTIVE' GROUP BY shop_id", p);
        var coupons = counts("SELECT shop_id, COUNT(*) AS n FROM coupons WHERE shop_id IN (:ids) AND is_active = TRUE GROUP BY shop_id", p);

        var media = new HashMap<UUID, long[]>();
        jdbc.query("""
            SELECT shop_id, COUNT(*) AS n, COALESCE(SUM(file_size), 0) AS bytes FROM media_files
             WHERE shop_id IN (:ids) AND status <> 'DELETED' GROUP BY shop_id
            """, p, rs -> { media.put(rs.getObject("shop_id", UUID.class), new long[]{rs.getLong("n"), rs.getLong("bytes")}); });
        var docs = new HashMap<UUID, long[]>();
        jdbc.query("""
            SELECT s.id AS shop_id, COUNT(d.id) AS n, COALESCE(SUM(d.file_size), 0) AS bytes
              FROM shops s JOIN merchant_documents d ON d.application_id = s.application_id
             WHERE s.id IN (:ids) GROUP BY s.id
            """, p, rs -> { docs.put(rs.getObject("shop_id", UUID.class), new long[]{rs.getLong("n"), rs.getLong("bytes")}); });

        var now = Instant.now();
        var noLimits = new PlanLimits(null, null, null, null);
        for (var key : keys) {
            var pr = products.getOrDefault(key.id(), new long[6]);
            var or = orders.getOrDefault(key.id(), new Object[]{0L, 0L, 0L, 0L, 0L, BigDecimal.ZERO});
            var st = staff.getOrDefault(key.id(), new TreeMap<>());
            var md = media.getOrDefault(key.id(), new long[2]);
            var dc = docs.getOrDefault(key.id(), new long[2]);
            var paid = or[5] == null ? BigDecimal.ZERO : ((BigDecimal) or[5]).setScale(2, java.math.RoundingMode.HALF_UP);
            result.put(key.id(), new ShopUsage(key.shopNo(), key.name(), key.status(), null, noLimits,
                new ProductUsage(pr[0], pr[1], pr[2], pr[3], pr[4], pr[5]),
                new OrderUsage((long) or[0], (long) or[1], (long) or[2], (long) or[3], (long) or[4], paid),
                new StaffUsage(st.values().stream().mapToLong(Long::longValue).sum(), Collections.unmodifiableMap(st)),
                customers.getOrDefault(key.id(), 0L), coupons.getOrDefault(key.id(), 0L),
                new StorageUsage(md[0], md[1], dc[0], dc[1], pr[5], md[1] + dc[1], STORAGE_NOTE), now));
        }
        return result;
    }

    private Map<UUID, Long> counts(String sql, MapSqlParameterSource p) {
        var map = new HashMap<UUID, Long>();
        jdbc.query(sql, p, rs -> { map.put(rs.getObject("shop_id", UUID.class), rs.getLong("n")); });
        return map;
    }
}
