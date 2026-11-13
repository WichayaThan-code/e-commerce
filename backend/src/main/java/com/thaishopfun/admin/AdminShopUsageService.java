package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminPlatformDtos.*;
import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Plans and usage for the platform admin. Plan catalog and shop assignments live in DB ({@code plans},
 * {@code shops.plan_code}). Usage is computed live from products / orders / members / storage tables in
 * batched queries (one grouped query per table for a page of shops, or GROUP BY for summaries).
 */
@Service
public class AdminShopUsageService {
    static final List<String> OPEN_ORDER_EXCLUDED = List.of("COMPLETED", "CANCELLED", "REFUNDED");
    /** Sentinel used by the admin usage page / filter for shops with no plan. */
    public static final String NO_PLAN = "__NONE__";
    private static final Pattern PLAN_CODE = Pattern.compile("^[A-Z0-9_]{1,50}$");
    private static final String STORAGE_NOTE = "trackedBytes = media_files + merchant_documents; product images / logo / cover are files on disk without a size column (productImages = count only)";

    private final NamedParameterJdbcTemplate jdbc;
    /** Last {@link #usage(List)} batch size — for tests asserting no N+1. */
    private volatile int lastUsageBatchSize;

    public AdminShopUsageService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int lastUsageBatchSize() {
        return lastUsageBatchSize;
    }

    @Transactional(readOnly = true)
    public PlanCatalog plans() {
        var rows = jdbc.query("""
            SELECT code, name, limit_products, limit_orders_per_month, limit_staff, limit_storage_bytes,
                   price_monthly, price_yearly, description, sort_order
              FROM plans WHERE active = TRUE ORDER BY sort_order, code
            """, (rs, i) -> new Plan(rs.getString("code"), rs.getString("name"),
            new PlanLimits(longOrNull(rs, "limit_products"), longOrNull(rs, "limit_orders_per_month"),
                longOrNull(rs, "limit_staff"), longOrNull(rs, "limit_storage_bytes")),
            rs.getBigDecimal("price_monthly"), rs.getBigDecimal("price_yearly"), rs.getString("description"),
            rs.getInt("sort_order")));
        return new PlanCatalog("DB", rows, "แพ็กเกจจากตาราง plans; shops.plan_code = null หมายถึงยังไม่ได้กำหนดแพ็กเกจ");
    }

    /** Shop identity used for the usage rows (planCode from shops.plan_code). */
    public record ShopKey(UUID id, Long shopNo, String name, String status, String slug, String planCode) {}

    /**
     * Normalize an optional {@code plan} query param: blank → null (no filter); otherwise trim + upper case.
     * Allowed values: {@code __NONE__} or {@code ^[A-Z0-9_]{1,50}$}. Invalid → 400 {@code PLAN_INVALID}.
     */
    public static String normalizePlan(String plan) {
        if (plan == null || plan.isBlank()) return null;
        var normalized = plan.trim().toUpperCase(Locale.ROOT);
        if (NO_PLAN.equals(normalized) || PLAN_CODE.matcher(normalized).matches()) return normalized;
        throw new AuthException(HttpStatus.BAD_REQUEST, "PLAN_INVALID", "รหัสแพ็กเกจไม่ถูกต้อง");
    }

    /** Resolve plan limits; unknown code → 400 PLAN_INVALID. Blank/null clears. */
    @Transactional(readOnly = true)
    public String requirePlanOrClear(String planCode) {
        if (planCode == null || planCode.isBlank()) return null;
        var code = normalizePlan(planCode);
        if (NO_PLAN.equals(code)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "PLAN_INVALID", "รหัสแพ็กเกจไม่ถูกต้อง");
        }
        var n = Optional.ofNullable(jdbc.queryForObject(
            "SELECT COUNT(*) FROM plans WHERE code = :code AND active = TRUE",
            new MapSqlParameterSource("code", code), Long.class)).orElse(0L);
        if (n == 0) throw new AuthException(HttpStatus.BAD_REQUEST, "PLAN_INVALID", "ไม่พบแพ็กเกจนี้");
        return code;
    }

    @Transactional(readOnly = true)
    public Map<UUID, ShopUsage> usage(List<ShopKey> keys) {
        lastUsageBatchSize = keys.size();
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

        var limitsByCode = loadLimits(keys.stream().map(ShopKey::planCode).filter(Objects::nonNull).distinct().toList());
        var now = Instant.now();
        var noLimits = new PlanLimits(null, null, null, null);
        for (var key : keys) {
            var pr = products.getOrDefault(key.id(), new long[6]);
            var or = orders.getOrDefault(key.id(), new Object[]{0L, 0L, 0L, 0L, 0L, BigDecimal.ZERO});
            var st = staff.getOrDefault(key.id(), new TreeMap<>());
            var md = media.getOrDefault(key.id(), new long[2]);
            var dc = docs.getOrDefault(key.id(), new long[2]);
            var paid = or[5] == null ? BigDecimal.ZERO : ((BigDecimal) or[5]).setScale(2, java.math.RoundingMode.HALF_UP);
            var limits = key.planCode() == null ? noLimits : limitsByCode.getOrDefault(key.planCode(), noLimits);
            result.put(key.id(), new ShopUsage(key.shopNo(), key.name(), key.status(), key.planCode(), limits,
                new ProductUsage(pr[0], pr[1], pr[2], pr[3], pr[4], pr[5]),
                new OrderUsage((long) or[0], (long) or[1], (long) or[2], (long) or[3], (long) or[4], paid),
                new StaffUsage(st.values().stream().mapToLong(Long::longValue).sum(), Collections.unmodifiableMap(st)),
                customers.getOrDefault(key.id(), 0L), coupons.getOrDefault(key.id(), 0L),
                new StorageUsage(md[0], md[1], dc[0], dc[1], pr[5], md[1] + dc[1], STORAGE_NOTE), now, key.slug()));
        }
        return result;
    }

    /**
     * SQL GROUP BY plan_code over every shop matching the filter (q/status/…; plan filter already applied in {@code whereSql}).
     * Batch subqueries — no per-shop round trips.
     */
    @Transactional(readOnly = true)
    public List<PlanUsageSummary> summarizeSql(String whereSql, MapSqlParameterSource params) {
        var p = new MapSqlParameterSource(params.getValues())
            .addValue("since", Timestamp.from(Instant.now().minus(Duration.ofDays(30))));
        var rows = jdbc.query("""
            SELECT s.plan_code AS plan_code, pl.name AS plan_name,
                   COUNT(*) AS shop_count,
                   COALESCE(SUM(COALESCE(pr.cnt, 0)), 0) AS products,
                   COALESCE(SUM(COALESCE(st.cnt, 0)), 0) AS staff,
                   COALESCE(SUM(COALESCE(md.bytes, 0) + COALESCE(dc.bytes, 0)), 0) AS storage_bytes,
                   COALESCE(SUM(COALESCE(ord.recent, 0)), 0) AS orders_last_30
              FROM shops s
              LEFT JOIN users u ON u.id = s.owner_user_id
              LEFT JOIN plans pl ON pl.code = s.plan_code
              LEFT JOIN (
                   SELECT shop_id, COUNT(*) AS cnt FROM products
                    WHERE deleted_at IS NULL AND status = 'PUBLISHED' GROUP BY shop_id
              ) pr ON pr.shop_id = s.id
              LEFT JOIN (
                   SELECT shop_id, COUNT(*) AS cnt FROM shop_members
                    WHERE status = 'ACTIVE' AND removed_at IS NULL GROUP BY shop_id
              ) st ON st.shop_id = s.id
              LEFT JOIN (
                   SELECT shop_id, COALESCE(SUM(file_size), 0) AS bytes FROM media_files
                    WHERE status <> 'DELETED' GROUP BY shop_id
              ) md ON md.shop_id = s.id
              LEFT JOIN (
                   SELECT s2.id AS shop_id, COALESCE(SUM(d.file_size), 0) AS bytes
                     FROM shops s2 JOIN merchant_documents d ON d.application_id = s2.application_id
                    GROUP BY s2.id
              ) dc ON dc.shop_id = s.id
              LEFT JOIN (
                   SELECT shop_id, SUM(CASE WHEN placed_at >= :since THEN 1 ELSE 0 END) AS recent
                     FROM orders GROUP BY shop_id
              ) ord ON ord.shop_id = s.id
             WHERE %s
             GROUP BY s.plan_code, pl.name
             ORDER BY CASE WHEN s.plan_code IS NULL THEN 1 ELSE 0 END, pl.name, s.plan_code
            """.formatted(whereSql), p, (rs, i) -> new PlanUsageSummary(
            rs.getString("plan_code"), rs.getString("plan_name"), rs.getLong("shop_count"),
            rs.getLong("products"), rs.getLong("staff"), rs.getLong("storage_bytes"), rs.getLong("orders_last_30")));
        if (rows.isEmpty()) {
            return List.of(new PlanUsageSummary(null, null, 0, 0, 0, 0, 0));
        }
        return rows;
    }

    private Map<String, PlanLimits> loadLimits(List<String> codes) {
        var map = new HashMap<String, PlanLimits>();
        if (codes.isEmpty()) return map;
        jdbc.query("""
            SELECT code, limit_products, limit_orders_per_month, limit_staff, limit_storage_bytes
              FROM plans WHERE code IN (:codes)
            """, new MapSqlParameterSource("codes", codes), rs -> {
            map.put(rs.getString("code"), new PlanLimits(
                longOrNull(rs, "limit_products"), longOrNull(rs, "limit_orders_per_month"),
                longOrNull(rs, "limit_staff"), longOrNull(rs, "limit_storage_bytes")));
        });
        return map;
    }

    private Map<UUID, Long> counts(String sql, MapSqlParameterSource p) {
        var map = new HashMap<UUID, Long>();
        jdbc.query(sql, p, rs -> { map.put(rs.getObject("shop_id", UUID.class), rs.getLong("n")); });
        return map;
    }

    private static Long longOrNull(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        var v = rs.getObject(col);
        if (v == null) return null;
        return rs.getLong(col);
    }
}
