package com.thaishopfun.admin;

import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B5: platform admin dashboard summary. Same Bangkok day / paid-order rules as {@code MerchantDashboardService},
 * but aggregated across every shop. Counts for shops/users are live inventory (shop counts ignore the date range;
 * {@code usersNewInPeriod} uses the selected period).
 */
@Service
public class AdminDashboardService {
    static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    static final int MAX_YEARS = 3;
    static final String PAID_RULE = "o.payment_status = 'PAID' AND o.status NOT IN ('CANCELLED','REFUNDED')";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public AdminDashboardService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    AdminDashboardService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record SeriesPoint(String start, String end, BigDecimal sales, long orders) {}
    public record TopShop(long shopId, String shopName, String slug, BigDecimal sales, long orderCount) {}
    public record Counts(long shopsTotal, long shopsPendingReview, long shopsApproved, long shopsRejected,
                         long shopsSuspended, long pendingApplications, long usersTotal, long usersNewInPeriod) {}
    /** No buyer user id / phone / email — only order + shop public fields. */
    public record RecentOrder(String orderNumber, long shopId, String shopName, BigDecimal grandTotal,
                              String status, String paymentStatus, String placedAt) {}
    /** Paid GMV share by payments.provider (fallback orders.payment_method). */
    public record PaymentMethodShare(String method, BigDecimal amount, long orderCount) {}
    /** New sign-ups in the selected period — masked email only, never raw id / phone / full email. */
    public record RecentUser(String displayName, String emailMasked, String role, String createdAt) {}
    public record Summary(String range, String from, String to, int days, String timezone, String currency,
                          String bucket, BigDecimal totalSales, long orderCount, long paidOrderCount,
                          String previousFrom, String previousTo, BigDecimal previousSales,
                          long previousOrderCount, long previousPaidOrderCount,
                          List<SeriesPoint> series, List<TopShop> topShops, Counts counts,
                          List<RecentOrder> recentOrders, List<PaymentMethodShare> paymentMethods,
                          List<RecentUser> recentUsers) {}

    private record Totals(long count, long paid, BigDecimal sales) {}
    private record Period(String range, LocalDate from, LocalDate to, int days) {}

    @Transactional(readOnly = true)
    public Summary summary(String rawRange, String rawFrom, String rawTo) {
        Period p = period(rawRange, rawFrom, rawTo);
        LocalDate from = p.from(), to = p.to();
        int days = p.days();
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1L);
        Totals cur = totals(from, to);
        Totals prev = totals(prevFrom, prevTo);
        String bucket = days <= 31 ? "day" : days <= 186 ? "week" : "month";
        return new Summary(p.range(), from.toString(), to.toString(), days, BANGKOK.getId(), "THB", bucket,
            cur.sales(), cur.count(), cur.paid(),
            prevFrom.toString(), prevTo.toString(), prev.sales(), prev.count(), prev.paid(),
            series(from, to, bucket), topShops(from, to), counts(from, to), recentOrders(),
            paymentMethods(from, to), recentUsers(from, to));
    }

    private List<SeriesPoint> series(LocalDate from, LocalDate to, String bucket) {
        var start = Timestamp.from(from.atStartOfDay(BANGKOK).toInstant());
        var end = Timestamp.from(to.plusDays(1).atStartOfDay(BANGKOK).toInstant());
        Map<LocalDate, SeriesPoint> buckets = new LinkedHashMap<>();
        for (LocalDate d = bucketStart(from, bucket); !d.isAfter(to); d = next(d, bucket)) {
            LocalDate s = d.isBefore(from) ? from : d;
            LocalDate e = next(d, bucket).minusDays(1);
            if (e.isAfter(to)) e = to;
            buckets.put(d, new SeriesPoint(s.toString(), e.toString(), BigDecimal.ZERO.setScale(2), 0));
        }
        jdbc.query("""
            SELECT o.placed_at AS placed, o.grand_total AS total
              FROM orders o
             WHERE o.placed_at >= ? AND o.placed_at < ? AND %s
            """.formatted(PAID_RULE), rs -> {
            LocalDate day = rs.getTimestamp("placed").toInstant().atZone(BANGKOK).toLocalDate();
            LocalDate b = bucketStart(day, bucket);
            SeriesPoint old = buckets.get(b);
            if (old != null) {
                buckets.put(b, new SeriesPoint(old.start(), old.end(),
                    money(old.sales().add(money(rs.getBigDecimal("total")))), old.orders() + 1));
            }
        }, start, end);
        return new ArrayList<>(buckets.values());
    }

    private List<TopShop> topShops(LocalDate from, LocalDate to) {
        var start = Timestamp.from(from.atStartOfDay(BANGKOK).toInstant());
        var end = Timestamp.from(to.plusDays(1).atStartOfDay(BANGKOK).toInstant());
        return jdbc.query("""
            SELECT s.shop_no AS shop_id, s.name AS shop_name, s.slug AS slug,
                   COUNT(*) AS orders, COALESCE(SUM(o.grand_total), 0) AS sales
              FROM orders o
              JOIN shops s ON s.id = o.shop_id
             WHERE o.placed_at >= ? AND o.placed_at < ? AND s.deleted_at IS NULL AND %s
             GROUP BY s.shop_no, s.name, s.slug
             ORDER BY COALESCE(SUM(o.grand_total), 0) DESC, COUNT(*) DESC, s.name
             FETCH FIRST 5 ROWS ONLY
            """.formatted(PAID_RULE),
            (rs, i) -> new TopShop(rs.getLong("shop_id"), rs.getString("shop_name"), rs.getString("slug"),
                money(rs.getBigDecimal("sales")), rs.getLong("orders")),
            start, end);
    }

    private Counts counts(LocalDate from, LocalDate to) {
        // DB stores operating shops as ACTIVE (API APPROVED); CLOSED maps to REJECTED — see ShopStatusConverter.
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS cnt FROM shops WHERE deleted_at IS NULL GROUP BY status", rs -> {
            byStatus.put(rs.getString("status"), rs.getLong("cnt"));
        });
        long pending = byStatus.getOrDefault("PENDING_REVIEW", 0L);
        long approved = byStatus.getOrDefault("ACTIVE", 0L) + byStatus.getOrDefault("APPROVED", 0L);
        long rejected = byStatus.getOrDefault("REJECTED", 0L) + byStatus.getOrDefault("CLOSED", 0L);
        long suspended = byStatus.getOrDefault("SUSPENDED", 0L);
        long shopsTotal = pending + approved + rejected + suspended;
        Long usersTotal = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL", Long.class);
        var start = Timestamp.from(from.atStartOfDay(BANGKOK).toInstant());
        var end = Timestamp.from(to.plusDays(1).atStartOfDay(BANGKOK).toInstant());
        Long usersNew = jdbc.queryForObject("""
            SELECT COUNT(*) FROM users
             WHERE deleted_at IS NULL AND created_date_time >= ? AND created_date_time < ?
            """, Long.class, start, end);
        return new Counts(shopsTotal, pending, approved, rejected, suspended, pending,
            usersTotal == null ? 0 : usersTotal, usersNew == null ? 0 : usersNew);
    }

    private List<RecentOrder> recentOrders() {
        return jdbc.query("""
            SELECT o.order_number AS order_number, s.shop_no AS shop_id, s.name AS shop_name,
                   o.grand_total AS grand_total, o.status AS status, o.payment_status AS payment_status,
                   o.placed_at AS placed_at
              FROM orders o
              JOIN shops s ON s.id = o.shop_id
             WHERE o.placed_at IS NOT NULL AND s.deleted_at IS NULL
             ORDER BY o.placed_at DESC
             FETCH FIRST 8 ROWS ONLY
            """, (rs, i) -> {
            Timestamp placed = rs.getTimestamp("placed_at");
            return new RecentOrder(
                rs.getString("order_number"),
                rs.getLong("shop_id"),
                rs.getString("shop_name"),
                money(rs.getBigDecimal("grand_total")),
                rs.getString("status"),
                rs.getString("payment_status"),
                placed == null ? null : placed.toInstant().toString());
        });
    }

    /**
     * Same paid rule as totalSales. Method = CAPTURED/AUTHORIZED payments.provider when present,
     * else orders.payment_method (BANK_TRANSFER / COD).
     */
    private List<PaymentMethodShare> paymentMethods(LocalDate from, LocalDate to) {
        var start = Timestamp.from(from.atStartOfDay(BANGKOK).toInstant());
        var end = Timestamp.from(to.plusDays(1).atStartOfDay(BANGKOK).toInstant());
        return jdbc.query("""
            SELECT x.method AS method, COUNT(*) AS orders, COALESCE(SUM(x.total), 0) AS amount
              FROM (SELECT COALESCE(CAST((SELECT MAX(p.provider) FROM payments p
                                      WHERE p.order_id = o.id AND p.status IN ('CAPTURED','AUTHORIZED')) AS VARCHAR(50)),
                                    CAST(o.payment_method AS VARCHAR(50))) AS method,
                           o.grand_total AS total
                      FROM orders o
                     WHERE o.placed_at >= ? AND o.placed_at < ? AND %s) x
             GROUP BY x.method
             ORDER BY COALESCE(SUM(x.total), 0) DESC, x.method
            """.formatted(PAID_RULE),
            (rs, i) -> new PaymentMethodShare(rs.getString("method"), money(rs.getBigDecimal("amount")),
                rs.getLong("orders")),
            start, end);
    }

    private List<RecentUser> recentUsers(LocalDate from, LocalDate to) {
        var start = Timestamp.from(from.atStartOfDay(BANGKOK).toInstant());
        var end = Timestamp.from(to.plusDays(1).atStartOfDay(BANGKOK).toInstant());
        return jdbc.query("""
            SELECT u.first_name AS first_name, u.last_name AS last_name, u.email AS email,
                   u.created_date_time AS created_at,
                   COALESCE(
                     (SELECT COALESCE(ar.name_th, pr.role)
                        FROM platform_roles pr
                        LEFT JOIN auth_roles ar ON ar.code = pr.role
                       WHERE pr.user_id = u.id AND pr.revoked_at IS NULL
                       ORDER BY pr.granted_at
                       FETCH FIRST 1 ROWS ONLY),
                     (SELECT COALESCE(ar.name_th, sm.role)
                        FROM shop_members sm
                        LEFT JOIN auth_roles ar ON ar.code = sm.role
                       WHERE sm.user_id = u.id AND sm.status = 'ACTIVE' AND sm.removed_at IS NULL
                       ORDER BY COALESCE(ar.sort_order, 9999)
                       FETCH FIRST 1 ROWS ONLY),
                     (SELECT COALESCE(ar.name_th, 'ผู้ซื้อ') FROM auth_roles ar WHERE ar.code = 'BUYER'
                       FETCH FIRST 1 ROWS ONLY),
                     'ลูกค้า'
                   ) AS role_label
              FROM users u
             WHERE u.deleted_at IS NULL AND u.created_date_time >= ? AND u.created_date_time < ?
             ORDER BY u.created_date_time DESC
             FETCH FIRST 5 ROWS ONLY
            """, (rs, i) -> {
            Timestamp created = rs.getTimestamp("created_at");
            String first = rs.getString("first_name");
            String last = rs.getString("last_name");
            String display = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
            if (display.isEmpty()) display = "ผู้ใช้";
            return new RecentUser(
                display,
                maskEmail(rs.getString("email")),
                rs.getString("role_label"),
                created == null ? null : created.toInstant().toString());
        }, start, end);
    }

    /** Matches CustomerService.maskEmail — never returns the full address. */
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "***";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private Totals totals(LocalDate from, LocalDate to) {
        var start = Timestamp.from(from.atStartOfDay(BANGKOK).toInstant());
        var end = Timestamp.from(to.plusDays(1).atStartOfDay(BANGKOK).toInstant());
        var row = jdbc.queryForMap("""
            SELECT COUNT(*) AS cnt,
                   COUNT(*) FILTER (WHERE payment_status = 'PAID' AND status NOT IN ('CANCELLED','REFUNDED')) AS paid_cnt,
                   COALESCE(SUM(grand_total) FILTER (WHERE payment_status = 'PAID' AND status NOT IN ('CANCELLED','REFUNDED')), 0) AS sales
              FROM orders
             WHERE placed_at >= ? AND placed_at < ?
            """, start, end);
        BigDecimal sales = row.get("sales") instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(row.get("sales")));
        return new Totals(((Number) row.get("cnt")).longValue(), ((Number) row.get("paid_cnt")).longValue(),
            sales.setScale(2, RoundingMode.HALF_UP));
    }

    private Period period(String rawRange, String rawFrom, String rawTo) {
        LocalDate today = LocalDate.now(clock.withZone(BANGKOK));
        String range;
        LocalDate from;
        LocalDate to;
        boolean hasFromTo = notBlank(rawFrom) || notBlank(rawTo);
        if (hasFromTo) {
            if (!notBlank(rawFrom) || !notBlank(rawTo)) throw invalid("from", "REQUIRED", "from and to are both required");
            from = parse(rawFrom, "from");
            to = parse(rawTo, "to");
            if (from.isAfter(to)) throw invalid("from", "RANGE", "from must not be after to");
            if (from.isBefore(to.minusYears(MAX_YEARS))) throw invalid("from", "RANGE", "range must not exceed " + MAX_YEARS + " years");
            range = "custom";
        } else if (notBlank(rawRange)) {
            range = rawRange.trim().toLowerCase();
            to = today;
            from = switch (range) {
                case "today" -> today;
                case "7d" -> today.minusDays(6);
                case "30d" -> today.minusDays(29);
                case "1y" -> today.minusYears(1).plusDays(1);
                default -> throw invalid("range", "INVALID", "range must be today, 7d, 30d or 1y");
            };
        } else {
            from = to = today;
            range = "today";
        }
        int days = (int) ChronoUnit.DAYS.between(from, to) + 1;
        return new Period(range, from, to, days);
    }

    private static LocalDate bucketStart(LocalDate d, String bucket) {
        return switch (bucket) {
            case "week" -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> d.withDayOfMonth(1);
            default -> d;
        };
    }

    private static LocalDate next(LocalDate d, String bucket) {
        return switch (bucket) {
            case "week" -> d.plusWeeks(1);
            case "month" -> d.plusMonths(1);
            default -> d.plusDays(1);
        };
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static ValidationException invalid(String field, String code, String message) {
        return new ValidationException(List.of(new FieldViolation(field, code, message)));
    }

    private static LocalDate parse(String raw, String field) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw invalid(field, "INVALID", field + " must be YYYY-MM-DD");
        }
    }
}
