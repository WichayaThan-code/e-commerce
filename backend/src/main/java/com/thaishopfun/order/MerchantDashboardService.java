package com.thaishopfun.order;

import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.customer.CustomerInsightKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only merchant dashboard KPIs (A1 + A11 + A12). Days are Asia/Bangkok calendar days, counted by orders.placed_at.
 * Period: range=today|7d|30d|1y, or from/to (inclusive, max 3 years), or legacy date=YYYY-MM-DD; default today.
 * sales = sum(grand_total) of period orders with payment_status PAID and status not CANCELLED/REFUNDED.
 * previous* = same-length period right before. pendingOrders = current state (any date),
 * PAID_AWAITING_MERCHANT_CONFIRM .. PACKED. today* fields mirror the period values (kept for A1 callers).
 * A12 insights: sales series (day <=31 days, week <=186 days, else month), payment-method split, top 5 products
 * and top 5 customers, all over the same paid rule. Payment method = provider of a CAPTURED/AUTHORIZED payment,
 * else orders.payment_method. Customers group by buyer_user_id, else SHIPPING recipient_name (guest).
 */
@Service
public class MerchantDashboardService {
    static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    static final int MAX_YEARS = 3;
    static final String PAID_RULE = "o.payment_status = 'PAID' AND o.status NOT IN ('CANCELLED','REFUNDED')";
    static final List<String> PENDING_STATUSES = List.of(
        OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM.name(), OrderStatus.MERCHANT_CONFIRMED.name(),
        OrderStatus.READY_TO_PACK.name(), OrderStatus.PACKING.name(), OrderStatus.PACKED.name());

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CustomerInsightKeys customerKeys;

    @Autowired
    public MerchantDashboardService(JdbcTemplate jdbc, CustomerInsightKeys customerKeys) {
        this(jdbc, Clock.systemUTC(), customerKeys);
    }

    MerchantDashboardService(JdbcTemplate jdbc, Clock clock, CustomerInsightKeys customerKeys) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.customerKeys = customerKeys;
    }

    public record Summary(String range, String from, String to, int days, String date, String timezone, String currency,
                          BigDecimal sales, long paidOrderCount, long orderCount,
                          String previousFrom, String previousTo, BigDecimal previousSales, long previousOrderCount,
                          BigDecimal todaySales, long todayPaidOrderCount, long todayOrderCount, long pendingOrders) {}

    public record SeriesPoint(String start, String end, BigDecimal sales, long orders) {}
    public record MethodShare(String method, long orders, BigDecimal sales) {}
    public record TopProduct(String productId, String name, String imageUrl, long quantity, BigDecimal sales) {}
    /** Opaque customerKey only — never exposes phone, email, address or raw buyer user id. */
    public record TopCustomer(String customerKey, String displayName, long orderCount, BigDecimal totalAmount, BigDecimal sharePercent) {}
    public record Insights(String range, String from, String to, int days, String timezone, String currency, String bucket,
                           BigDecimal sales, long paidOrderCount, List<SeriesPoint> series,
                           List<MethodShare> paymentMethods, List<TopProduct> topProducts, List<TopCustomer> topCustomers) {}

    private record Totals(long count, long paid, BigDecimal sales) {}
    private record Period(String range, LocalDate from, LocalDate to, int days, String rawDate) {}

    @Transactional(readOnly = true)
    public Summary summary(UUID shopUuid, String rawDate) {
        return summary(shopUuid, rawDate, null, null, null);
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID shopUuid, String rawDate, String rawRange, String rawFrom, String rawTo) {
        Period p = period(rawDate, rawRange, rawFrom, rawTo);
        LocalDate from = p.from(), to = p.to();
        int days = p.days();
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1L);
        Totals cur = totals(shopUuid, from, to);
        Totals prev = totals(shopUuid, prevFrom, prevTo);
        String in = String.join(",", PENDING_STATUSES.stream().map(s -> "'" + s + "'").toList());
        Long pending = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE shop_id = ? AND status IN (" + in + ")",
            Long.class, shopUuid);
        return new Summary(p.range(), from.toString(), to.toString(), days, from.equals(to) ? from.toString() : null,
            BANGKOK.getId(), "THB", cur.sales(), cur.paid(), cur.count(),
            prevFrom.toString(), prevTo.toString(), prev.sales(), prev.count(),
            cur.sales(), cur.paid(), cur.count(), pending == null ? 0 : pending);
    }

    @Transactional(readOnly = true)
    public Insights insights(Long shopId, UUID shopUuid, String rawDate, String rawRange, String rawFrom, String rawTo) {
        Period p = period(rawDate, rawRange, rawFrom, rawTo);
        var start = Timestamp.from(p.from().atStartOfDay(BANGKOK).toInstant());
        var end = Timestamp.from(p.to().plusDays(1).atStartOfDay(BANGKOK).toInstant());
        String bucket = p.days() <= 31 ? "day" : p.days() <= 186 ? "week" : "month";

        // series: buckets keyed by their first Bangkok day; gaps filled with zero
        Map<LocalDate, SeriesPoint> buckets = new LinkedHashMap<>();
        for (LocalDate d = bucketStart(p.from(), bucket); !d.isAfter(p.to()); d = next(d, bucket)) {
            LocalDate s = d.isBefore(p.from()) ? p.from() : d;
            LocalDate e = next(d, bucket).minusDays(1);
            if (e.isAfter(p.to())) e = p.to();
            buckets.put(d, new SeriesPoint(s.toString(), e.toString(), BigDecimal.ZERO.setScale(2), 0));
        }
        // bucketed in Java (portable across PostgreSQL / H2); one row per paid order
        jdbc.query("""
            SELECT o.placed_at AS placed, o.grand_total AS total
              FROM orders o
             WHERE o.shop_id = ? AND o.placed_at >= ? AND o.placed_at < ? AND %s
            """.formatted(PAID_RULE), rs -> {
            LocalDate day = rs.getTimestamp("placed").toInstant().atZone(BANGKOK).toLocalDate();
            LocalDate b = bucketStart(day, bucket);
            SeriesPoint old = buckets.get(b);
            if (old != null) buckets.put(b, new SeriesPoint(old.start(), old.end(), money(old.sales().add(money(rs.getBigDecimal("total")))), old.orders() + 1));
        }, shopUuid, start, end);

        List<MethodShare> methods = jdbc.query("""
            SELECT x.method AS method, COUNT(*) AS cnt, COALESCE(SUM(x.total), 0) AS sales
              FROM (SELECT COALESCE(CAST((SELECT MAX(p.provider) FROM payments p
                                      WHERE p.order_id = o.id AND p.status IN ('CAPTURED','AUTHORIZED')) AS VARCHAR(50)),
                                    CAST(o.payment_method AS VARCHAR(50))) AS method,
                           o.grand_total AS total
                      FROM orders o
                     WHERE o.shop_id = ? AND o.placed_at >= ? AND o.placed_at < ? AND %s) x
             GROUP BY x.method
             ORDER BY COALESCE(SUM(x.total), 0) DESC, x.method
            """.formatted(PAID_RULE),
            (rs, i) -> new MethodShare(rs.getString("method"), rs.getLong("cnt"), money(rs.getBigDecimal("sales"))),
            shopUuid, start, end);

        List<TopProduct> top = jdbc.query("""
            SELECT MAX(CAST(oi.product_id AS VARCHAR)) AS pid, MAX(oi.product_name_snapshot) AS name, MAX(oi.image_url_snapshot) AS img,
                   COALESCE(SUM(oi.quantity), 0) AS qty, COALESCE(SUM(oi.line_total), 0) AS sales
              FROM order_items oi JOIN orders o ON o.id = oi.order_id
             WHERE o.shop_id = ? AND o.placed_at >= ? AND o.placed_at < ? AND %s
             GROUP BY COALESCE(CAST(oi.product_id AS VARCHAR), oi.product_name_snapshot)
             ORDER BY COALESCE(SUM(oi.line_total), 0) DESC, COALESCE(SUM(oi.quantity), 0) DESC, MAX(oi.product_name_snapshot)
             FETCH FIRST 5 ROWS ONLY
            """.formatted(PAID_RULE),
            (rs, i) -> new TopProduct(rs.getString("pid"), rs.getString("name"), rs.getString("img"), rs.getLong("qty"), money(rs.getBigDecimal("sales"))),
            shopUuid, start, end);

        top = top.stream().map(t -> new TopProduct(t.productId(), t.name(), topImage(shopId, shopUuid, t.productId(), t.imageUrl()), t.quantity(), t.sales())).toList();
        Totals cur = totals(shopUuid, p.from(), p.to());
        List<TopCustomer> customers = topCustomers(shopUuid, start, end, cur.sales());
        return new Insights(p.range(), p.from().toString(), p.to().toString(), p.days(), BANGKOK.getId(), "THB", bucket,
            cur.sales(), cur.paid(), new ArrayList<>(buckets.values()), methods, top, customers);
    }

    /**
     * Top 5 buyers by paid grand_total (then order count). Logged-in buyers group by {@code buyer_user_id};
     * guests group by lower(trim(SHIPPING recipient_name)). customerKey is opaque (c1… for buyers, n1… for guests).
     */
    private List<TopCustomer> topCustomers(UUID shopUuid, Timestamp start, Timestamp end, BigDecimal periodSales) {
        List<TopCustomer> rows = jdbc.query("""
            SELECT x.buyer_id AS buyer_id, x.guest_key AS guest_key,
                   MAX(x.display_name) AS display_name,
                   COUNT(*) AS orders, COALESCE(SUM(x.total), 0) AS sales
              FROM (
                    SELECT o.buyer_user_id AS buyer_id,
                           CASE WHEN o.buyer_user_id IS NOT NULL THEN NULL
                                ELSE LOWER(TRIM(COALESCE(ship.recipient_name, ''))) END AS guest_key,
                           COALESCE(NULLIF(TRIM(ship.recipient_name), ''), 'ลูกค้า') AS display_name,
                           o.grand_total AS total
                      FROM orders o
                      LEFT JOIN (
                            SELECT order_id, MAX(recipient_name) AS recipient_name
                              FROM order_addresses
                             WHERE address_type = 'SHIPPING'
                             GROUP BY order_id
                      ) ship ON ship.order_id = o.id
                     WHERE o.shop_id = ? AND o.placed_at >= ? AND o.placed_at < ? AND %s
                   ) x
             GROUP BY x.buyer_id, x.guest_key
             ORDER BY COALESCE(SUM(x.total), 0) DESC, COUNT(*) DESC, MAX(x.display_name)
             FETCH FIRST 5 ROWS ONLY
            """.formatted(PAID_RULE),
            (rs, i) -> {
                UUID buyerId = (UUID) rs.getObject("buyer_id");
                String guestKey = rs.getString("guest_key");
                String display = rs.getString("display_name");
                if (display == null || display.isBlank()) display = "ลูกค้า";
                long orders = rs.getLong("orders");
                BigDecimal sales = money(rs.getBigDecimal("sales"));
                BigDecimal share = periodSales == null || periodSales.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO.setScale(1)
                    : sales.multiply(BigDecimal.valueOf(100)).divide(periodSales, 1, RoundingMode.HALF_UP);
                String key = buyerId != null
                    ? customerKeys.encode(shopUuid, null, buyerId)
                    : guestNameKey(shopUuid, guestKey == null ? "" : guestKey);
                return new TopCustomer(key, display, orders, sales, share);
            },
            shopUuid, start, end);
        return rows;
    }

    /** Opaque guest key: n1 + url-safe SHA-256(shopUuid + NUL + normalizedName). Not reversible to PII. */
    static String guestNameKey(UUID shopUuid, String normalizedGuestKey) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(shopUuid.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(normalizedGuestKey.getBytes(StandardCharsets.UTF_8));
            return "n1" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
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

    private Period period(String rawDate, String rawRange, String rawFrom, String rawTo) {
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
            from = to = notBlank(rawDate) ? parse(rawDate, "date") : today;
            range = notBlank(rawDate) ? "date" : "today";
        }
        int days = (int) ChronoUnit.DAYS.between(from, to) + 1;
        return new Period(range, from, to, days, rawDate);
    }

    private Totals totals(UUID shopUuid, LocalDate from, LocalDate to) {
        var start = Timestamp.from(from.atStartOfDay(BANGKOK).toInstant());
        var end = Timestamp.from(to.plusDays(1).atStartOfDay(BANGKOK).toInstant());
        var row = jdbc.queryForMap("""
            SELECT COUNT(*) AS cnt,
                   COUNT(*) FILTER (WHERE payment_status = 'PAID' AND status NOT IN ('CANCELLED','REFUNDED')) AS paid_cnt,
                   COALESCE(SUM(grand_total) FILTER (WHERE payment_status = 'PAID' AND status NOT IN ('CANCELLED','REFUNDED')), 0) AS sales
              FROM orders
             WHERE shop_id = ? AND placed_at >= ? AND placed_at < ?
            """, shopUuid, start, end);
        BigDecimal sales = row.get("sales") instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(row.get("sales")));
        return new Totals(((Number) row.get("cnt")).longValue(), ((Number) row.get("paid_cnt")).longValue(),
            sales.setScale(2, RoundingMode.HALF_UP));
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

    /**
     * Top-5 image: the product's current primary image (merchant-scoped URL, same as the products list),
     * else the order item's snapshot, else null (UI shows a placeholder).
     */
    private String topImage(Long shopId, UUID shopUuid, String productId, String snapshot) {
        if (productId != null && shopId != null) {
            try {
                UUID pid = UUID.fromString(productId);
                List<String> ids = jdbc.queryForList("""
                    SELECT CAST(pi.id AS VARCHAR(64)) FROM product_images pi JOIN products p ON p.id = pi.product_id
                     WHERE pi.product_id = ? AND p.shop_id = ?
                     ORDER BY pi.is_primary DESC, pi.sort_order, pi.created_date_time
                     FETCH FIRST 1 ROWS ONLY
                    """, String.class, pid, shopUuid);
                if (!ids.isEmpty()) return "/api/merchant/shops/" + shopId + "/products/" + pid + "/images/" + ids.get(0);
            } catch (IllegalArgumentException ignored) { /* not a UUID: fall back to snapshot */ }
        }
        return snapshot == null || snapshot.isBlank() ? null : snapshot;
    }
}
