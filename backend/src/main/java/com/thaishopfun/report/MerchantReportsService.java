package com.thaishopfun.report;

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
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Merchant reports (read-only). One {@link #overview} powers every tab; {@link ReportCsvWriter} renders the export.
 *
 * <h3>Metric contract</h3>
 * <ul>
 *   <li><b>Date base</b>: {@code orders.placed_at} in Asia/Bangkok for sales / products / categories / channels / orders /
 *       customers; inclusive days [from 00:00, to+1 00:00). Refunds use {@code refunds.completed_at} (same window).</li>
 *   <li><b>Paid rule</b> (same as the dashboard): {@code payment_status = 'PAID' AND status NOT IN ('CANCELLED','REFUNDED')}.</li>
 *   <li><b>gmv</b> = SUM(orders.grand_total) of paid orders (order level: includes shipping / tax / discount effects).</li>
 *   <li><b>itemSales</b> = SUM(order_items.line_total) of paid orders (product level; NOT equal to gmv).</li>
 *   <li><b>paidOrderCount</b> = COUNT of paid orders (KPI). <b>allOrderCount</b> = every order placed in the period.</li>
 *   <li><b>refundedAmount</b> = SUM(refunds.amount) with status COMPLETED and completed_at in the period. Reported separately;
 *       it is NOT subtracted from gmv. REQUESTED / APPROVED / PROOF_UPLOADED refunds are not counted.</li>
 *   <li><b>estimatedFee</b> = gmv * 5% (HALF_UP, 2 dp) and <b>estimatedNet</b> = gmv - estimatedFee. The platform does not store a
 *       per-order fee: these are estimates, not profit and not a settled payout.</li>
 *   <li><b>Customers</b> (paid orders only): logged-in buyers are keyed by buyer_user_id; guests by lower(trim(SHIPPING
 *       recipient_name)) (limitation: two different people with the same name are one guest); guests without a name are never
 *       merged (one customer per order). uniqueCustomers = distinct keys with a paid order in the period; newCustomers = keys whose
 *       FIRST paid order ever (shop-scoped) is in the period; returningCustomers = keys active in the period with a paid order
 *       before the period start. Keys never leave the database: no names, phones, emails or user ids are returned.</li>
 *   <li><b>Growth</b> = (current - previous) / previous * 100 (1 dp) against the previous equal-length period; null when the
 *       previous value is 0 (UI shows an em dash).</li>
 *   <li><b>Categories</b>: order_items -> products.category_id -> hub_categories (the "product categories" of the catalog);
 *       missing product / category = UNCATEGORIZED. Top 5 categories by itemSales, the rest merged into OTHER.</li>
 *   <li><b>Channels</b>: from orders.channel only (paid orders). Null / blank / unknown value = UNKNOWN.</li>
 *   <li>Series buckets: day (&lt;= 31 days), week (&lt;= 186 days, Monday based), month; gaps are zero-filled.</li>
 *   <li><b>Promotions</b>: redemptions from {@code promotion_usages.used_at} (shop via promotions.shop_id);
 *       discountTotal = SUM(discount_amount). Paid orders with a coupon use {@code orders.placed_at} + non-blank coupon_code.
 *       attachRatePct = paidOrdersWithCoupon / paidOrderCount * 100. No view→checkout conversion funnel.</li>
 *   <li><b>Shipping fees</b>: SUM(orders.shipping_fee) of paid orders by {@code placed_at}. freeShippingOrderCount = paid with fee 0.
 *       Carrier mix from {@code shipping_carrier_code_snapshot} (blank = UNKNOWN).</li>
 *   <li><b>Shipment ops</b>: counts by event timestamps on {@code shipments} (dispatched_at / delivered_at / failed_at / returned_at)
 *       in the same Bangkok window — not mixed with fee date base without labeling.</li>
 * </ul>
 */
@Service
public class MerchantReportsService {
    public static final String PAID_RULE = "o.payment_status = 'PAID' AND o.status NOT IN ('CANCELLED','REFUNDED')";
    public static final BigDecimal FEE_RATE = new BigDecimal("0.05");
    static final Set<String> CHANNELS = Set.of("STOREFRONT", "SHOPEE", "TIKTOK", "LAZADA", "LINE_OA", "MANUAL", "OMS_IMPORT");
    static final String UNCATEGORIZED = "UNCATEGORIZED";
    static final String OTHER = "OTHER";
    static final String UNKNOWN = "UNKNOWN";
    private static final int TOP_CATEGORIES = 5;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public MerchantReportsService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    MerchantReportsService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Kpis(BigDecimal gmv, BigDecimal previousGmv, BigDecimal gmvGrowthPct,
                       BigDecimal itemSales, BigDecimal previousItemSales, BigDecimal itemSalesGrowthPct,
                       long paidOrderCount, long previousPaidOrderCount, BigDecimal paidOrderGrowthPct,
                       long allOrderCount, long previousAllOrderCount, BigDecimal allOrderGrowthPct,
                       BigDecimal refundedAmount, BigDecimal previousRefundedAmount, BigDecimal refundedGrowthPct, long refundCount,
                       BigDecimal estimatedFee, BigDecimal previousEstimatedFee, BigDecimal estimatedFeeGrowthPct,
                       BigDecimal estimatedNet, BigDecimal previousEstimatedNet, BigDecimal estimatedNetGrowthPct) {}

    public record SeriesPoint(String start, String end, BigDecimal gmv, long orders, BigDecimal estimatedFee, BigDecimal estimatedNet) {}

    /** key = hub category slug, UNCATEGORIZED or OTHER; names are null for the two synthetic keys (UI localizes them). */
    public record CategoryShare(String key, String nameTh, String nameEn, String nameZh, BigDecimal itemSales, long quantity, BigDecimal sharePercent) {}

    /** channel = STOREFRONT | SHOPEE | TIKTOK | LAZADA | LINE_OA | MANUAL | OMS_IMPORT | UNKNOWN. */
    public record ChannelShare(String channel, long orders, BigDecimal gmv, BigDecimal sharePercent) {}

    public record TopProduct(String productId, String name, String imageUrl, long quantity, BigDecimal itemSales) {}

    public record CustomerSummary(long uniqueCustomers, long previousUniqueCustomers, BigDecimal uniqueGrowthPct,
                                  long newCustomers, long previousNewCustomers, BigDecimal newGrowthPct,
                                  long returningCustomers, long previousReturningCustomers, BigDecimal returningGrowthPct,
                                  BigDecimal averageOrdersPerCustomer, BigDecimal previousAverageOrdersPerCustomer, BigDecimal averageOrdersGrowthPct,
                                  BigDecimal averageOrderValue, BigDecimal previousAverageOrderValue, BigDecimal averageOrderValueGrowthPct) {}

    /** Redemptions by used_at; coupon attach by placed_at on paid orders. */
    public record PromotionSummary(long redemptionCount, long previousRedemptionCount, BigDecimal redemptionGrowthPct,
                                   BigDecimal discountTotal, BigDecimal previousDiscountTotal, BigDecimal discountGrowthPct,
                                   long paidOrdersWithCoupon, long previousPaidOrdersWithCoupon, BigDecimal couponOrderGrowthPct,
                                   BigDecimal attachRatePct, BigDecimal previousAttachRatePct, BigDecimal attachRateGrowthPct) {}

    /** promoType = PERCENT | FIXED_AMOUNT | FREE_SHIPPING. */
    public record PromoTypeShare(String promoType, long redemptions, BigDecimal discountTotal, BigDecimal sharePercent) {}

    public record TopCoupon(String code, String promoType, long redemptions, BigDecimal discountTotal) {}

    /** Shipping fee metrics by orders.placed_at (paid). */
    public record ShippingSummary(BigDecimal shippingFeeCollected, BigDecimal previousShippingFeeCollected, BigDecimal shippingFeeGrowthPct,
                                  long freeShippingOrderCount, long previousFreeShippingOrderCount, BigDecimal freeShippingGrowthPct,
                                  BigDecimal averageShippingFee, BigDecimal previousAverageShippingFee, BigDecimal averageShippingFeeGrowthPct,
                                  long paidOrderCount) {}

    /** carrier = snapshot code or UNKNOWN. */
    public record CarrierShare(String carrier, long orders, BigDecimal shippingFee, BigDecimal sharePercent) {}

    /** Shipment event counts by their own timestamps (not placed_at). */
    public record ShipmentOps(long dispatchedCount, long previousDispatchedCount, BigDecimal dispatchedGrowthPct,
                              long deliveredCount, long previousDeliveredCount, BigDecimal deliveredGrowthPct,
                              long failedCount, long previousFailedCount, BigDecimal failedGrowthPct,
                              long returnedCount, long previousReturnedCount, BigDecimal returnedGrowthPct) {}

    public record Overview(String range, String from, String to, int days, String timezone, String currency, String bucket,
                           String previousFrom, String previousTo, BigDecimal feeEstimateRate,
                           Kpis kpis, List<SeriesPoint> series, List<CategoryShare> categories, List<ChannelShare> channels,
                           List<TopProduct> topProducts, CustomerSummary customerSummary, List<String> adviceFlags,
                           PromotionSummary promotionSummary, List<PromoTypeShare> promoTypes, List<TopCoupon> topCoupons,
                           ShippingSummary shippingSummary, List<CarrierShare> carriers, ShipmentOps shipmentOps) {}

    private record Totals(long all, long paid, BigDecimal gmv) {}
    private record Customers(long unique, long fresh, long returning) {}
    private record Refunds(long count, BigDecimal amount) {}
    private record PromoTotals(long redemptions, BigDecimal discount) {}
    private record ShipFeeTotals(BigDecimal fee, long freeCount, long paidCount) {}
    private record ShipOpsTotals(long dispatched, long delivered, long failed, long returned) {}

    public ReportPeriod period(String range, String from, String to) {
        return ReportPeriod.parse(clock, range, from, to);
    }

    @Transactional(readOnly = true)
    public Overview overview(Long shopNo, UUID shop, String rawRange, String rawFrom, String rawTo) {
        return overview(shopNo, shop, period(rawRange, rawFrom, rawTo));
    }

    @Transactional(readOnly = true)
    public Overview overview(Long shopNo, UUID shop, ReportPeriod p) {
        ReportPeriod prev = p.previous();
        Timestamp start = p.start(), end = p.end(), pStart = prev.start(), pEnd = prev.end();
        String bucket = p.days() <= 31 ? "day" : p.days() <= 186 ? "week" : "month";

        Totals cur = totals(shop, start, end), old = totals(shop, pStart, pEnd);
        BigDecimal itemSales = itemSales(shop, start, end), prevItemSales = itemSales(shop, pStart, pEnd);
        Refunds refunds = refunds(shop, start, end), prevRefunds = refunds(shop, pStart, pEnd);
        BigDecimal fee = fee(cur.gmv()), prevFee = fee(old.gmv());
        BigDecimal net = cur.gmv().subtract(fee), prevNet = old.gmv().subtract(prevFee);

        Kpis kpis = new Kpis(cur.gmv(), old.gmv(), growth(cur.gmv(), old.gmv()),
            itemSales, prevItemSales, growth(itemSales, prevItemSales),
            cur.paid(), old.paid(), growth(cur.paid(), old.paid()),
            cur.all(), old.all(), growth(cur.all(), old.all()),
            refunds.amount(), prevRefunds.amount(), growth(refunds.amount(), prevRefunds.amount()), refunds.count(),
            fee, prevFee, growth(fee, prevFee), net, prevNet, growth(net, prevNet));

        Customers c = customers(shop, start, end), pc = customers(shop, pStart, pEnd);
        BigDecimal avgOrders = ratio(BigDecimal.valueOf(cur.paid()), c.unique()), prevAvgOrders = ratio(BigDecimal.valueOf(old.paid()), pc.unique());
        BigDecimal aov = ratio(cur.gmv(), cur.paid()), prevAov = ratio(old.gmv(), old.paid());
        CustomerSummary customerSummary = new CustomerSummary(c.unique(), pc.unique(), growth(c.unique(), pc.unique()),
            c.fresh(), pc.fresh(), growth(c.fresh(), pc.fresh()),
            c.returning(), pc.returning(), growth(c.returning(), pc.returning()),
            avgOrders, prevAvgOrders, growth(avgOrders, prevAvgOrders), aov, prevAov, growth(aov, prevAov));

        PromoTotals promo = promoTotals(shop, start, end), prevPromo = promoTotals(shop, pStart, pEnd);
        long couponOrders = paidOrdersWithCoupon(shop, start, end), prevCouponOrders = paidOrdersWithCoupon(shop, pStart, pEnd);
        BigDecimal attach = percent(BigDecimal.valueOf(couponOrders), BigDecimal.valueOf(cur.paid()));
        BigDecimal prevAttach = percent(BigDecimal.valueOf(prevCouponOrders), BigDecimal.valueOf(old.paid()));
        PromotionSummary promotionSummary = new PromotionSummary(
            promo.redemptions(), prevPromo.redemptions(), growth(promo.redemptions(), prevPromo.redemptions()),
            promo.discount(), prevPromo.discount(), growth(promo.discount(), prevPromo.discount()),
            couponOrders, prevCouponOrders, growth(couponOrders, prevCouponOrders),
            attach, prevAttach, growth(attach, prevAttach));

        ShipFeeTotals ship = shipFees(shop, start, end), prevShip = shipFees(shop, pStart, pEnd);
        BigDecimal avgShip = ratio(ship.fee(), ship.paidCount()), prevAvgShip = ratio(prevShip.fee(), prevShip.paidCount());
        ShippingSummary shippingSummary = new ShippingSummary(
            ship.fee(), prevShip.fee(), growth(ship.fee(), prevShip.fee()),
            ship.freeCount(), prevShip.freeCount(), growth(ship.freeCount(), prevShip.freeCount()),
            avgShip, prevAvgShip, growth(avgShip, prevAvgShip),
            ship.paidCount());
        ShipOpsTotals ops = shipmentOps(shop, start, end), prevOps = shipmentOps(shop, pStart, pEnd);
        ShipmentOps shipmentOps = new ShipmentOps(
            ops.dispatched(), prevOps.dispatched(), growth(ops.dispatched(), prevOps.dispatched()),
            ops.delivered(), prevOps.delivered(), growth(ops.delivered(), prevOps.delivered()),
            ops.failed(), prevOps.failed(), growth(ops.failed(), prevOps.failed()),
            ops.returned(), prevOps.returned(), growth(ops.returned(), prevOps.returned()));

        return new Overview(p.range(), p.from().toString(), p.to().toString(), p.days(), ReportPeriod.BANGKOK.getId(), "THB", bucket,
            prev.from().toString(), prev.to().toString(), FEE_RATE,
            kpis, series(shop, p, start, end, bucket), categories(shop, start, end), channels(shop, start, end),
            topProducts(shopNo, shop, start, end), customerSummary, advice(kpis, customerSummary),
            promotionSummary, promoTypes(shop, start, end), topCoupons(shop, start, end),
            shippingSummary, carriers(shop, start, end), shipmentOps);
    }

    // ------------------------------------------------------------------ queries

    private Totals totals(UUID shop, Timestamp start, Timestamp end) {
        var row = jdbc.queryForMap("""
            SELECT COUNT(*) AS cnt,
                   COUNT(*) FILTER (WHERE payment_status = 'PAID' AND status NOT IN ('CANCELLED','REFUNDED')) AS paid_cnt,
                   COALESCE(SUM(grand_total) FILTER (WHERE payment_status = 'PAID' AND status NOT IN ('CANCELLED','REFUNDED')), 0) AS gmv
              FROM orders
             WHERE shop_id = ? AND placed_at >= ? AND placed_at < ?
            """, shop, start, end);
        return new Totals(((Number) row.get("cnt")).longValue(), ((Number) row.get("paid_cnt")).longValue(), money(row.get("gmv")));
    }

    /** Items of paid orders only; order_items are reached through the already filtered orders, so no row multiplies grand_total. */
    private BigDecimal itemSales(UUID shop, Timestamp start, Timestamp end) {
        Object v = jdbc.queryForObject("""
            SELECT COALESCE(SUM(oi.line_total), 0)
              FROM order_items oi JOIN orders o ON o.id = oi.order_id
             WHERE o.shop_id = ? AND o.placed_at >= ? AND o.placed_at < ? AND %s
            """.formatted(PAID_RULE), Object.class, shop, start, end);
        return money(v);
    }

    /** Completed refunds by completed_at; refunds table only (never joined with items). */
    private Refunds refunds(UUID shop, Timestamp start, Timestamp end) {
        var row = jdbc.queryForMap("""
            SELECT COUNT(*) AS cnt, COALESCE(SUM(amount), 0) AS amt
              FROM refunds
             WHERE shop_id = ? AND status = 'COMPLETED' AND completed_at >= ? AND completed_at < ?
            """, shop, start, end);
        return new Refunds(((Number) row.get("cnt")).longValue(), money(row.get("amt")));
    }

    private List<SeriesPoint> series(UUID shop, ReportPeriod p, Timestamp start, Timestamp end, String bucket) {
        Map<LocalDate, BigDecimal> gmv = new LinkedHashMap<>();
        Map<LocalDate, long[]> orders = new LinkedHashMap<>();
        Map<LocalDate, LocalDate[]> bounds = new LinkedHashMap<>();
        for (LocalDate d = bucketStart(p.from(), bucket); !d.isAfter(p.to()); d = next(d, bucket)) {
            LocalDate s = d.isBefore(p.from()) ? p.from() : d;
            LocalDate e = next(d, bucket).minusDays(1);
            if (e.isAfter(p.to())) e = p.to();
            gmv.put(d, BigDecimal.ZERO.setScale(2));
            orders.put(d, new long[1]);
            bounds.put(d, new LocalDate[]{s, e});
        }
        // bucketed in Java (portable across PostgreSQL / H2); one row per paid order
        jdbc.query("""
            SELECT o.placed_at AS placed, o.grand_total AS total
              FROM orders o
             WHERE o.shop_id = ? AND o.placed_at >= ? AND o.placed_at < ? AND %s
            """.formatted(PAID_RULE), rs -> {
            LocalDate day = rs.getTimestamp("placed").toInstant().atZone(ReportPeriod.BANGKOK).toLocalDate();
            LocalDate b = bucketStart(day, bucket);
            if (gmv.containsKey(b)) {
                gmv.put(b, money(gmv.get(b).add(money(rs.getBigDecimal("total")))));
                orders.get(b)[0]++;
            }
        }, shop, start, end);
        List<SeriesPoint> out = new ArrayList<>();
        for (var e : gmv.entrySet()) {
            BigDecimal g = e.getValue(), fee = fee(g);
            LocalDate[] b = bounds.get(e.getKey());
            out.add(new SeriesPoint(b[0].toString(), b[1].toString(), g, orders.get(e.getKey())[0], fee, g.subtract(fee)));
        }
        return out;
    }

    private List<CategoryShare> categories(UUID shop, Timestamp start, Timestamp end) {
        record Row(String key, String th, String en, String zh, BigDecimal sales, long qty) {}
        List<Row> rows = jdbc.query("""
            SELECT MAX(hc.slug) AS slug, MAX(hc.name_th) AS name_th, MAX(hc.name_en) AS name_en, MAX(hc.name_zh) AS name_zh,
                   COALESCE(SUM(oi.line_total), 0) AS sales, COALESCE(SUM(oi.quantity), 0) AS qty
              FROM order_items oi
              JOIN orders o ON o.id = oi.order_id
              LEFT JOIN products p ON p.id = oi.product_id AND p.shop_id = o.shop_id
              LEFT JOIN hub_categories hc ON hc.id = p.category_id
             WHERE o.shop_id = ? AND o.placed_at >= ? AND o.placed_at < ? AND %s
             GROUP BY hc.id
            """.formatted(PAID_RULE), (rs, i) -> {
            String slug = rs.getString("slug");
            return slug == null
                ? new Row(UNCATEGORIZED, null, null, null, money(rs.getBigDecimal("sales")), rs.getLong("qty"))
                : new Row(slug, rs.getString("name_th"), rs.getString("name_en"), rs.getString("name_zh"), money(rs.getBigDecimal("sales")), rs.getLong("qty"));
        }, shop, start, end);
        rows = new ArrayList<>(rows);
        rows.sort(Comparator.comparing(Row::sales).reversed().thenComparing(Row::key));
        BigDecimal total = rows.stream().map(Row::sales).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategoryShare> out = new ArrayList<>();
        BigDecimal otherSales = BigDecimal.ZERO;
        long otherQty = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (i < TOP_CATEGORIES) out.add(new CategoryShare(r.key(), r.th(), r.en(), r.zh(), r.sales(), r.qty(), percent(r.sales(), total)));
            else { otherSales = otherSales.add(r.sales()); otherQty += r.qty(); }
        }
        if (rows.size() > TOP_CATEGORIES) out.add(new CategoryShare(OTHER, null, null, null, money(otherSales), otherQty, percent(otherSales, total)));
        return out;
    }

    /** Channel comes from orders alone (no join), so an order is counted once. */
    private List<ChannelShare> channels(UUID shop, Timestamp start, Timestamp end) {
        Map<String, BigDecimal> gmv = new LinkedHashMap<>();
        Map<String, Long> cnt = new LinkedHashMap<>();
        jdbc.query("""
            SELECT COALESCE(o.channel, '') AS ch, COUNT(*) AS cnt, COALESCE(SUM(o.grand_total), 0) AS gmv
              FROM orders o
             WHERE o.shop_id = ? AND o.placed_at >= ? AND o.placed_at < ? AND %s
             GROUP BY COALESCE(o.channel, '')
            """.formatted(PAID_RULE), rs -> {
            String ch = rs.getString("ch") == null ? "" : rs.getString("ch").trim().toUpperCase();
            if (!CHANNELS.contains(ch)) ch = UNKNOWN;
            gmv.merge(ch, money(rs.getBigDecimal("gmv")), BigDecimal::add);
            cnt.merge(ch, rs.getLong("cnt"), Long::sum);
        }, shop, start, end);
        BigDecimal total = gmv.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return gmv.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .map(e -> new ChannelShare(e.getKey(), cnt.get(e.getKey()), e.getValue(), percent(e.getValue(), total)))
            .toList();
    }

    private List<TopProduct> topProducts(Long shopNo, UUID shop, Timestamp start, Timestamp end) {
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
            shop, start, end);
        return top.stream().map(t -> new TopProduct(t.productId(), t.name(), productImage(shopNo, shop, t.productId(), t.imageUrl()), t.quantity(), t.itemSales())).toList();
    }

    /** Customer keys stay inside SQL (see class comment); only counts are returned. History before the period is scanned for "first paid order". */
    private Customers customers(UUID shop, Timestamp start, Timestamp end) {
        var row = jdbc.queryForMap("""
            SELECT COUNT(*) AS uniq,
                   COUNT(CASE WHEN c.first_at >= ? THEN 1 END) AS fresh,
                   COUNT(CASE WHEN c.first_at < ? THEN 1 END) AS returning_cnt
              FROM (SELECT x.ckey AS ckey, MIN(x.placed) AS first_at
                      FROM (SELECT o.placed_at AS placed,
                                   CASE WHEN o.buyer_user_id IS NOT NULL THEN CONCAT('u:', CAST(o.buyer_user_id AS VARCHAR))
                                        WHEN COALESCE(TRIM(ship.recipient_name), '') <> '' THEN CONCAT('g:', LOWER(TRIM(ship.recipient_name)))
                                        ELSE CONCAT('o:', CAST(o.id AS VARCHAR)) END AS ckey
                              FROM orders o
                              LEFT JOIN (SELECT order_id, MAX(recipient_name) AS recipient_name
                                           FROM order_addresses WHERE address_type = 'SHIPPING' GROUP BY order_id) ship ON ship.order_id = o.id
                             WHERE o.shop_id = ? AND o.placed_at < ? AND %s) x
                     GROUP BY x.ckey
                    HAVING MAX(x.placed) >= ?) c
            """.formatted(PAID_RULE), start, start, shop, end, start);
        return new Customers(((Number) row.get("uniq")).longValue(), ((Number) row.get("fresh")).longValue(), ((Number) row.get("returning_cnt")).longValue());
    }

    private PromoTotals promoTotals(UUID shop, Timestamp start, Timestamp end) {
        var row = jdbc.queryForMap("""
            SELECT COUNT(*) AS cnt, COALESCE(SUM(pu.discount_amount), 0) AS discount
              FROM promotion_usages pu
              JOIN promotions p ON p.id = pu.promotion_id
             WHERE p.shop_id = ? AND pu.used_at >= ? AND pu.used_at < ?
            """, shop, start, end);
        return new PromoTotals(((Number) row.get("cnt")).longValue(), money(row.get("discount")));
    }

    private long paidOrdersWithCoupon(UUID shop, Timestamp start, Timestamp end) {
        Long n = jdbc.queryForObject("""
            SELECT COUNT(*)
              FROM orders o
             WHERE o.shop_id = ? AND o.placed_at >= ? AND o.placed_at < ? AND %s
               AND o.coupon_code IS NOT NULL AND TRIM(o.coupon_code) <> ''
            """.formatted(PAID_RULE), Long.class, shop, start, end);
        return n == null ? 0L : n;
    }

    private List<PromoTypeShare> promoTypes(UUID shop, Timestamp start, Timestamp end) {
        Map<String, BigDecimal> discount = new LinkedHashMap<>();
        Map<String, Long> cnt = new LinkedHashMap<>();
        jdbc.query("""
            SELECT p.promo_type AS typ, COUNT(*) AS cnt, COALESCE(SUM(pu.discount_amount), 0) AS discount
              FROM promotion_usages pu
              JOIN promotions p ON p.id = pu.promotion_id
             WHERE p.shop_id = ? AND pu.used_at >= ? AND pu.used_at < ?
             GROUP BY p.promo_type
            """, rs -> {
            String typ = rs.getString("typ") == null ? UNKNOWN : rs.getString("typ").trim().toUpperCase();
            discount.merge(typ, money(rs.getBigDecimal("discount")), BigDecimal::add);
            cnt.merge(typ, rs.getLong("cnt"), Long::sum);
        }, shop, start, end);
        BigDecimal total = discount.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return discount.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .map(e -> new PromoTypeShare(e.getKey(), cnt.get(e.getKey()), e.getValue(), percent(e.getValue(), total)))
            .toList();
    }

    private List<TopCoupon> topCoupons(UUID shop, Timestamp start, Timestamp end) {
        return jdbc.query("""
            SELECT COALESCE(c.code, p.name, '—') AS code, p.promo_type AS typ,
                   COUNT(*) AS cnt, COALESCE(SUM(pu.discount_amount), 0) AS discount
              FROM promotion_usages pu
              JOIN promotions p ON p.id = pu.promotion_id
              LEFT JOIN coupons c ON c.id = pu.coupon_id
             WHERE p.shop_id = ? AND pu.used_at >= ? AND pu.used_at < ?
             GROUP BY COALESCE(c.code, p.name, '—'), p.promo_type
             ORDER BY COALESCE(SUM(pu.discount_amount), 0) DESC, COUNT(*) DESC, COALESCE(c.code, p.name, '—')
             FETCH FIRST 5 ROWS ONLY
            """, (rs, i) -> new TopCoupon(rs.getString("code"), rs.getString("typ"), rs.getLong("cnt"), money(rs.getBigDecimal("discount"))),
            shop, start, end);
    }

    private ShipFeeTotals shipFees(UUID shop, Timestamp start, Timestamp end) {
        var row = jdbc.queryForMap("""
            SELECT COALESCE(SUM(o.shipping_fee), 0) AS fee,
                   COUNT(*) FILTER (WHERE o.shipping_fee = 0) AS free_cnt,
                   COUNT(*) AS paid_cnt
              FROM orders o
             WHERE o.shop_id = ? AND o.placed_at >= ? AND o.placed_at < ? AND %s
            """.formatted(PAID_RULE), shop, start, end);
        return new ShipFeeTotals(money(row.get("fee")), ((Number) row.get("free_cnt")).longValue(), ((Number) row.get("paid_cnt")).longValue());
    }

    private List<CarrierShare> carriers(UUID shop, Timestamp start, Timestamp end) {
        Map<String, BigDecimal> fee = new LinkedHashMap<>();
        Map<String, Long> cnt = new LinkedHashMap<>();
        jdbc.query("""
            SELECT COALESCE(o.shipping_carrier_code_snapshot, '') AS carrier,
                   COUNT(*) AS cnt, COALESCE(SUM(o.shipping_fee), 0) AS fee
              FROM orders o
             WHERE o.shop_id = ? AND o.placed_at >= ? AND o.placed_at < ? AND %s
             GROUP BY COALESCE(o.shipping_carrier_code_snapshot, '')
            """.formatted(PAID_RULE), rs -> {
            String code = rs.getString("carrier") == null ? "" : rs.getString("carrier").trim().toUpperCase();
            if (code.isBlank()) code = UNKNOWN;
            fee.merge(code, money(rs.getBigDecimal("fee")), BigDecimal::add);
            cnt.merge(code, rs.getLong("cnt"), Long::sum);
        }, shop, start, end);
        BigDecimal total = fee.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return fee.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .map(e -> new CarrierShare(e.getKey(), cnt.get(e.getKey()), e.getValue(), percent(e.getValue(), total)))
            .toList();
    }

    private ShipOpsTotals shipmentOps(UUID shop, Timestamp start, Timestamp end) {
        var row = jdbc.queryForMap("""
            SELECT COUNT(*) FILTER (WHERE dispatched_at >= ? AND dispatched_at < ?) AS dispatched,
                   COUNT(*) FILTER (WHERE delivered_at >= ? AND delivered_at < ?) AS delivered,
                   COUNT(*) FILTER (WHERE failed_at >= ? AND failed_at < ?) AS failed,
                   COUNT(*) FILTER (WHERE returned_at >= ? AND returned_at < ?) AS returned
              FROM shipments
             WHERE shop_id = ?
            """, start, end, start, end, start, end, start, end, shop);
        return new ShipOpsTotals(
            ((Number) row.get("dispatched")).longValue(),
            ((Number) row.get("delivered")).longValue(),
            ((Number) row.get("failed")).longValue(),
            ((Number) row.get("returned")).longValue());
    }

    /** Rule-based flags only (no invented numbers); the UI turns them into text and quotes real growth values. */
    private static List<String> advice(Kpis k, CustomerSummary c) {
        List<String> flags = new ArrayList<>();
        if (k.paidOrderCount() == 0) {
            flags.add("NO_SALES");
            return flags;
        }
        if (k.gmvGrowthPct() == null) flags.add("NO_BASELINE");
        else if (k.gmvGrowthPct().signum() > 0) flags.add("GMV_UP");
        else if (k.gmvGrowthPct().signum() < 0) flags.add("GMV_DOWN");
        else flags.add("GMV_FLAT");
        if (c.newCustomers() == 0) flags.add("NO_NEW_CUSTOMERS");
        else if (c.newGrowthPct() != null && c.newGrowthPct().signum() < 0) flags.add("NEW_CUSTOMERS_DOWN");
        if (k.refundCount() > 0) flags.add("REFUNDS_PRESENT");
        return flags;
    }

    // ------------------------------------------------------------------ helpers

    static BigDecimal fee(BigDecimal gmv) { return gmv.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP); }

    /** % change vs previous, 1 dp; null when the previous base is 0. */
    static BigDecimal growth(BigDecimal cur, BigDecimal prev) {
        if (cur == null || prev == null || prev.signum() == 0) return null;
        return cur.subtract(prev).multiply(BigDecimal.valueOf(100)).divide(prev, 1, RoundingMode.HALF_UP);
    }

    static BigDecimal growth(long cur, long prev) { return growth(BigDecimal.valueOf(cur), BigDecimal.valueOf(prev)); }

    private static BigDecimal ratio(BigDecimal numerator, long denominator) {
        return denominator <= 0 ? BigDecimal.ZERO.setScale(2) : numerator.divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal total) {
        return total == null || total.signum() <= 0 ? BigDecimal.ZERO.setScale(1)
            : part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    static BigDecimal money(Object v) {
        BigDecimal b = v == null ? BigDecimal.ZERO : v instanceof BigDecimal d ? d : new BigDecimal(String.valueOf(v));
        return b.setScale(2, RoundingMode.HALF_UP);
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

    /** Current primary product image (merchant-scoped URL), else the order item snapshot, else null (UI shows a placeholder). */
    private String productImage(Long shopNo, UUID shop, String productId, String snapshot) {
        if (productId != null && shopNo != null) {
            try {
                UUID pid = UUID.fromString(productId);
                List<String> ids = jdbc.queryForList("""
                    SELECT CAST(pi.id AS VARCHAR(64)) FROM product_images pi JOIN products p ON p.id = pi.product_id
                     WHERE pi.product_id = ? AND p.shop_id = ?
                     ORDER BY pi.is_primary DESC, pi.sort_order, pi.created_date_time
                     FETCH FIRST 1 ROWS ONLY
                    """, String.class, pid, shop);
                if (!ids.isEmpty()) return "/api/merchant/shops/" + shopNo + "/products/" + pid + "/images/" + ids.get(0);
            } catch (IllegalArgumentException ignored) { /* not a UUID: fall back to the snapshot */ }
        }
        return snapshot == null || snapshot.isBlank() ? null : snapshot;
    }
}
