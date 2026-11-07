package com.thaishopfun.customer;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Aggregates accepted shop orders (+ CRM rows without accepted orders) for /merchant/customers.
 * Read-only; does not mutate order or CRM tables.
 */
@Service
public class CustomerInsightsService {
    static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    static final BigDecimal VIP_SPENT = new BigDecimal("10000");
    static final int VIP_ORDERS = 10;
    static final int REPEAT_ORDERS = 2;
    static final int INACTIVE_DAYS = 90;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTS = Set.of("totalSpent", "orderCount", "lastOrderAt", "name");
    private static final Set<String> SEGMENTS = Set.of("VIP", "REPEAT", "GENERAL");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");

    private static final int EXPORT_MAX_ROWS = 10_000;
    private static final int SXSSF_THRESHOLD = 2_000;

    private final ShopRepository shops;
    private final ShopCustomerAddressRepository addresses;
    private final AuthorizationService authorization;
    private final NamedParameterJdbcTemplate jdbc;
    private final CustomerInsightKeys keys;

    public CustomerInsightsService(ShopRepository shops, ShopCustomerAddressRepository addresses,
                                   AuthorizationService authorization, JdbcTemplate jdbcTemplate,
                                   CustomerInsightKeys keys) {
        this.shops = shops;
        this.addresses = addresses;
        this.authorization = authorization;
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.keys = keys;
    }

    @Transactional(readOnly = true)
    public CustomerInsightsDtos.InsightsPage list(UUID userId, Long shopNo, String q, String channel, String status,
                                                  String segment, String joinedFrom, String joinedTo, String sort,
                                                  String direction, Integer page, Integer size) {
        var shop = requireShop(userId, shopNo);
        var pii = canReadPii(userId, shopNo);
        var filters = parseFilters(channel, status, segment, joinedFrom, joinedTo, sort, direction, page, size);
        var prepared = prepareFiltered(shop.getUuid(), pii, q, filters);
        var stats = computeStats(prepared.forStats());
        var filtered = prepared.filtered();
        long total = filtered.size();
        int from = Math.min(filters.page * filters.size, filtered.size());
        int to = Math.min(from + filters.size, filtered.size());
        var items = filtered.subList(from, to).stream().map(InsightRow::insight).toList();
        int totalPages = filters.size == 0 ? 0 : (int) Math.ceil(total / (double) filters.size);
        return new CustomerInsightsDtos.InsightsPage(items, filters.page, filters.size, total, totalPages, stats);
    }

    @Transactional(readOnly = true)
    public byte[] exportWorkbook(UUID userId, Long shopNo, String q, String channel, String status, String segment,
                                 String joinedFrom, String joinedTo, String sort, String direction, Locale locale) {
        var shop = requireShop(userId, shopNo);
        var pii = canReadPii(userId, shopNo);
        var filters = parseFilters(channel, status, segment, joinedFrom, joinedTo, sort, direction, 0, MAX_PAGE_SIZE);
        var prepared = prepareFiltered(shop.getUuid(), pii, q, filters);
        if (prepared.filtered().size() > EXPORT_MAX_ROWS) {
            throw invalid("export", "EXPORT_TOO_LARGE", "ส่งออกได้สูงสุด " + EXPORT_MAX_ROWS + " แถว — ลดตัวกรองแล้วลองใหม่");
        }
        var rows = prepared.filtered();
        String[] headers = exportHeaders(locale);
        try (var book = new com.thaishopfun.common.excel.MerchantExcelWorkbook()) {
            var meta = new java.util.LinkedHashMap<String, String>();
            meta.put("Shop", shop.getName() != null ? shop.getName() : String.valueOf(shopNo));
            meta.put("Timezone", "Asia/Bangkok");
            meta.put("Generated at", java.time.Instant.now().toString());
            meta.put("PII", pii ? "full" : "masked");
            meta.put("Rows", String.valueOf(rows.size()));
            book.addMetaSheet("Meta", meta);
            var columns = java.util.List.of(
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(headers[0]),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(headers[1]),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(headers[2]),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(headers[3]),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(headers[4]),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.text(headers[5]),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.integer(headers[6]),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.money(headers[7]),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.datetime(headers[8]),
                com.thaishopfun.common.excel.MerchantExcelWorkbook.Column.datetime(headers[9])
            );
            var data = new java.util.ArrayList<java.util.List<Object>>(rows.size());
            for (var pack : rows) {
                var insight = pack.insight();
                var row = new java.util.ArrayList<Object>(10);
                row.add(nullToEmpty(insight.displayName()));
                row.add(nullToEmpty(insight.phone()));
                row.add(nullToEmpty(insight.email()));
                row.add(nullToEmpty(insight.channel()));
                row.add(nullToEmpty(insight.status()));
                row.add(nullToEmpty(insight.segment()));
                row.add(insight.orderCount());
                row.add(insight.totalSpent() == null ? BigDecimal.ZERO : insight.totalSpent());
                row.add(insight.lastOrderAt());
                row.add(insight.joinedAt());
                data.add(row);
            }
            book.addTableSheet("customers", "Customers", columns, data);
            return book.toBytes();
        }
    }

    private static String[] exportHeaders(Locale locale) {
        String lang = locale == null ? "th" : locale.getLanguage().toLowerCase(Locale.ROOT);
        return switch (lang) {
            case "en" -> new String[]{"Name", "Phone", "Email", "Channel", "Status", "Segment", "Orders", "Total spent", "Last order", "Joined"};
            case "zh" -> new String[]{"姓名", "电话", "邮箱", "渠道", "状态", "分组", "订单数", "消费总额", "最近订单", "加入日期"};
            default -> new String[]{"ชื่อ", "เบอร์โทร", "อีเมล", "ช่องทาง", "สถานะ", "กลุ่ม", "จำนวนออเดอร์", "ยอดใช้จ่าย", "ออเดอร์ล่าสุด", "วันที่เข้าร่วม"};
        };
    }

    private static void writeExportText(Row row, int col, String value) {
        if (value == null) {
            row.createCell(col).setBlank();
            return;
        }
        row.createCell(col).setCellValue(guardFormula(value));
    }

    private static void writeExportDate(Row row, int col, Instant at, CellStyle style) {
        var cell = row.createCell(col);
        if (at == null) {
            cell.setBlank();
            return;
        }
        cell.setCellValue(java.util.Date.from(at));
        cell.setCellStyle(style);
    }

    /** Prefix apostrophe when the value could be interpreted as a spreadsheet formula. */
    static String guardFormula(String value) {
        if (value.isEmpty()) return value;
        char c = value.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return "'" + value;
        }
        return value;
    }

    @Transactional(readOnly = true)
    public List<String> channels(UUID userId, Long shopNo) {
        var shop = requireShop(userId, shopNo);
        var params = new MapSqlParameterSource("shopId", shop.getUuid());
        var set = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        jdbc.query("""
            SELECT DISTINCT o.channel FROM orders o
             WHERE o.shop_id = :shopId AND o.merchant_confirmed_at IS NOT NULL
            """, params, rs -> { set.add(rs.getString(1)); });
        jdbc.query("""
            SELECT DISTINCT CAST(sc.source AS VARCHAR(40)) FROM shop_customers sc
             WHERE sc.shop_id = :shopId AND sc.deleted_at IS NULL AND sc.status <> 'MERGED'
            """, params, rs -> { set.add(rs.getString(1)); });
        return List.copyOf(set);
    }

    @Transactional(readOnly = true)
    public CustomerInsightsDtos.InsightDetail get(UUID userId, Long shopNo, String key) {
        var shop = requireShop(userId, shopNo);
        var pii = canReadPii(userId, shopNo);
        var found = findInsightRow(shop.getUuid(), key, pii);
        var insight = found.insight();
        List<CustomerDtos.AddressResponse> addressList = null;
        if (pii && insight.shopCustomerId() != null) {
            addressList = addresses.findByCustomerIdAndDeletedAtIsNullOrderByCreatedDateTimeAscIdAsc(insight.shopCustomerId()).stream()
                .map(a -> new CustomerDtos.AddressResponse(a.getId(), a.getLabel(), a.getRecipientName(), a.getPhone(), a.getAddressLine1(),
                    a.getAddressLine2(), a.getSubdistrict(), a.getDistrict(), a.getProvince(), a.getPostalCode(), a.getCountryCode(),
                    a.isDefaultShipping(), a.isDefaultBilling()))
                .toList();
        }
        var channels = channelCounts(shop.getUuid(), found.gkey());
        return new CustomerInsightsDtos.InsightDetail(insight, addressList, channels);
    }

    @Transactional(readOnly = true)
    public CustomerInsightsDtos.InsightOrdersPage orders(UUID userId, Long shopNo, String key, Integer page, Integer size) {
        var shop = requireShop(userId, shopNo);
        if (authorization.checkShop(userId, shopNo, "ORDER_READ") != AuthorizationService.ShopDecision.GRANTED) {
            throw new AuthException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "คุณไม่มีสิทธิ์ทำรายการนี้ (ต้องมีสิทธิ์ ORDER_READ)");
        }
        var found = findInsightRow(shop.getUuid(), key, canReadPii(userId, shopNo));
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null ? 20 : size;
        if (s < 1 || s > MAX_PAGE_SIZE) throw invalid("size", "SIZE_INVALID", "ขนาดหน้าต้องอยู่ระหว่าง 1–100");
        var gkey = found.gkey();
        var params = paramsForKey(shop.getUuid(), gkey);
        Long total = jdbc.queryForObject("""
            SELECT COUNT(*) FROM orders o
             WHERE o.shop_id = :shopId AND o.merchant_confirmed_at IS NOT NULL
               AND """ + keyPredicate("o") + """
            """, params, Long.class);
        long totalVal = total == null ? 0 : total;
        params.addValue("limit", s).addValue("offset", p * s);
        var items = jdbc.query("""
            SELECT o.id, o.order_number, o.status, o.payment_status, o.grand_total, o.currency, o.placed_at
              FROM orders o
             WHERE o.shop_id = :shopId AND o.merchant_confirmed_at IS NOT NULL
               AND """ + keyPredicate("o") + """
             ORDER BY o.placed_at DESC NULLS LAST, o.id
             LIMIT :limit OFFSET :offset
            """, params, (rs, i) -> new CustomerInsightsDtos.InsightOrderItem(
            rs.getObject("id", UUID.class),
            rs.getString("order_number"),
            rs.getString("status"),
            rs.getString("payment_status"),
            rs.getBigDecimal("grand_total"),
            rs.getString("currency"),
            toInstant(rs.getTimestamp("placed_at"))
        ));
        int totalPages = s == 0 ? 0 : (int) Math.ceil(totalVal / (double) s);
        return new CustomerInsightsDtos.InsightOrdersPage(items, p, s, totalVal, totalPages);
    }

    private InsightRow findInsightRow(UUID shopUuid, String key, boolean pii) {
        var resolved = keys.resolve(shopUuid, key).orElseThrow(CustomerInsightsService::customerNotFound);
        var gkey = CustomerInsightKeys.toGkey(resolved);
        return loadInsightRows(shopUuid, pii).stream()
            .filter(row -> gkey.equals(row.gkey()))
            .findFirst()
            .orElseThrow(CustomerInsightsService::customerNotFound);
    }

    private PreparedInsights prepareFiltered(UUID shopUuid, boolean pii, String q, Filters filters) {
        var all = loadInsightRows(shopUuid, pii);
        var orderNumbers = loadOrderNumbersByKey(shopUuid);
        var gkeyByKey = all.stream().collect(Collectors.toMap(r -> r.insight().key(), InsightRow::gkey, (a, b) -> a));
        var forStats = applyFilters(all, null, filters, orderNumbers, gkeyByKey, pii);
        var filtered = sortRows(applyFilters(all, q, filters, orderNumbers, gkeyByKey, pii), filters.sort, filters.asc);
        return new PreparedInsights(all, forStats, filtered, orderNumbers, gkeyByKey);
    }

    private List<InsightRow> loadInsightRows(UUID shopUuid, boolean pii) {
        var aggs = loadOrderAggs(shopUuid);
        var params = new MapSqlParameterSource("shopId", shopUuid);
        var crm = jdbc.query("""
            SELECT id, display_name, phone, email, CAST(source AS VARCHAR(40)) AS source,
                   CAST(status AS VARCHAR(20)) AS status, created_date_time
              FROM shop_customers
             WHERE shop_id = :shopId AND deleted_at IS NULL AND status <> 'MERGED'
            """, params, (rs, i) -> new CrmRow(
            rs.getObject("id", UUID.class),
            rs.getString("display_name"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("source"),
            rs.getString("status"),
            toInstant(rs.getTimestamp("created_date_time"))
        ));
        var crmById = crm.stream().collect(Collectors.toMap(CrmRow::id, c -> c, (a, b) -> a));
        var result = new ArrayList<InsightRow>();
        var seenCrm = new HashSet<UUID>();
        for (var agg : aggs) {
            CrmRow c = agg.shopCustomerId() == null ? null : crmById.get(agg.shopCustomerId());
            if (c != null) seenCrm.add(c.id());
            result.add(toInsightRow(shopUuid, agg, c, pii));
        }
        for (var c : crm) {
            if (seenCrm.contains(c.id())) continue;
            var empty = new RawAgg(c.id().toString(), c.id(), 0, BigDecimal.ZERO, "THB", null, null, c.source(), null, null);
            result.add(toInsightRow(shopUuid, empty, c, pii));
        }
        return result;
    }

    private List<RawAgg> loadOrderAggs(UUID shopUuid) {
        var params = new MapSqlParameterSource("shopId", shopUuid);
        var aggs = jdbc.query("""
            SELECT CASE WHEN o.shop_customer_id IS NOT NULL THEN CAST(o.shop_customer_id AS VARCHAR(36))
                        ELSE CONCAT('u:', CAST(o.buyer_user_id AS VARCHAR(36))) END AS gkey,
                   o.shop_customer_id AS shop_customer_id,
                   COUNT(*) AS order_count,
                   COALESCE(SUM(CASE WHEN o.status IN ('CANCELLED', 'REFUNDED') THEN 0 ELSE o.grand_total END), 0) AS total_spent,
                   MAX(o.currency) AS currency,
                   MIN(o.placed_at) AS first_order_at,
                   MAX(o.placed_at) AS last_order_at
              FROM orders o
             WHERE o.shop_id = :shopId
               AND o.merchant_confirmed_at IS NOT NULL
               AND (o.shop_customer_id IS NOT NULL OR o.buyer_user_id IS NOT NULL)
             GROUP BY CASE WHEN o.shop_customer_id IS NOT NULL THEN CAST(o.shop_customer_id AS VARCHAR(36))
                           ELSE CONCAT('u:', CAST(o.buyer_user_id AS VARCHAR(36))) END,
                      o.shop_customer_id
            """, params, (rs, i) -> new RawAgg(
            rs.getString("gkey"),
            rs.getObject("shop_customer_id", UUID.class),
            rs.getLong("order_count"),
            rs.getBigDecimal("total_spent"),
            rs.getString("currency"),
            toInstant(rs.getTimestamp("first_order_at")),
            toInstant(rs.getTimestamp("last_order_at")),
            null, null, null
        ));
        var latest = new HashMap<String, String[]>();
        jdbc.query("""
            SELECT CASE WHEN o.shop_customer_id IS NOT NULL THEN CAST(o.shop_customer_id AS VARCHAR(36))
                        ELSE CONCAT('u:', CAST(o.buyer_user_id AS VARCHAR(36))) END AS gkey,
                   o.channel, oa.recipient_name, oa.phone, o.placed_at, o.id
              FROM orders o
              LEFT JOIN order_addresses oa ON oa.order_id = o.id AND oa.address_type = 'SHIPPING'
             WHERE o.shop_id = :shopId AND o.merchant_confirmed_at IS NOT NULL
             ORDER BY o.placed_at DESC NULLS LAST, o.id DESC
            """, params, rs -> {
            var gkey = rs.getString("gkey");
            if (gkey != null && !latest.containsKey(gkey)) {
                latest.put(gkey, new String[]{rs.getString("channel"), rs.getString("recipient_name"), rs.getString("phone")});
            }
        });
        return aggs.stream().map(a -> {
            var info = latest.get(a.gkey());
            if (info == null) return a;
            return new RawAgg(a.gkey(), a.shopCustomerId(), a.orderCount(), a.totalSpent(), a.currency(),
                a.firstOrderAt(), a.lastOrderAt(), info[0], info[1], info[2]);
        }).toList();
    }

    private Map<String, List<String>> loadOrderNumbersByKey(UUID shopUuid) {
        var params = new MapSqlParameterSource("shopId", shopUuid);
        var map = new HashMap<String, List<String>>();
        jdbc.query("""
            SELECT CASE WHEN o.shop_customer_id IS NOT NULL THEN CAST(o.shop_customer_id AS VARCHAR(36))
                        ELSE CONCAT('u:', CAST(o.buyer_user_id AS VARCHAR(36))) END AS gkey,
                   o.order_number
              FROM orders o
             WHERE o.shop_id = :shopId AND o.merchant_confirmed_at IS NOT NULL
            """, params, rs -> {
            map.computeIfAbsent(rs.getString("gkey"), k -> new ArrayList<>()).add(rs.getString("order_number"));
        });
        return map;
    }

    private List<CustomerInsightsDtos.ChannelCount> channelCounts(UUID shopUuid, String gkey) {
        var params = paramsForKey(shopUuid, gkey);
        return jdbc.query("""
            SELECT o.channel, COUNT(*) AS n FROM orders o
             WHERE o.shop_id = :shopId AND o.merchant_confirmed_at IS NOT NULL
               AND """ + keyPredicate("o") + """
             GROUP BY o.channel ORDER BY n DESC, o.channel
            """, params, (rs, i) -> new CustomerInsightsDtos.ChannelCount(rs.getString("channel"), rs.getLong("n")));
    }

    private InsightRow toInsightRow(UUID shopUuid, RawAgg agg, CrmRow crm, boolean pii) {
        String displayName = crm != null && crm.displayName() != null && !crm.displayName().isBlank()
            ? crm.displayName()
            : (agg.recipientName() == null || agg.recipientName().isBlank() ? "ลูกค้า" : agg.recipientName());
        String phone = crm != null && crm.phone() != null ? crm.phone() : agg.phone();
        String email = crm != null ? crm.email() : null;
        String channel = agg.channel() != null ? agg.channel() : (crm != null ? crm.source() : null);
        Instant joinedAt = crm != null && crm.createdAt() != null ? crm.createdAt() : agg.firstOrderAt();
        String segment = segmentOf(agg.totalSpent(), agg.orderCount());
        String status = statusOf(crm, agg.lastOrderAt());
        if (!pii) {
            displayName = CustomerService.maskName(displayName);
            phone = CustomerService.maskPhone(phone);
            email = CustomerService.maskEmail(email);
        }
        var insight = new CustomerInsightsDtos.CustomerInsight(
            encodePublicKey(shopUuid, agg.gkey()),
            agg.shopCustomerId(),
            displayName,
            phone,
            email,
            !pii,
            channel,
            agg.orderCount(),
            agg.totalSpent() == null ? BigDecimal.ZERO : agg.totalSpent(),
            agg.currency() == null ? "THB" : agg.currency(),
            agg.firstOrderAt(),
            agg.lastOrderAt(),
            joinedAt,
            segment,
            status
        );
        return new InsightRow(agg.gkey(), insight);
    }

    public static String segmentOf(BigDecimal spent, long orderCount) {
        var total = spent == null ? BigDecimal.ZERO : spent;
        if (total.compareTo(VIP_SPENT) >= 0 || orderCount >= VIP_ORDERS) return "VIP";
        if (orderCount >= REPEAT_ORDERS) return "REPEAT";
        return "GENERAL";
    }

    static String statusOf(CrmRow crm, Instant lastOrderAt) {
        if (crm != null && "ARCHIVED".equalsIgnoreCase(crm.status())) return "INACTIVE";
        if (lastOrderAt != null) {
            var cutoff = Instant.now().minusSeconds(INACTIVE_DAYS * 24L * 3600L);
            if (lastOrderAt.isBefore(cutoff)) return "INACTIVE";
        }
        return "ACTIVE";
    }

    private List<InsightRow> applyFilters(
        List<InsightRow> rows, String q, Filters filters,
        Map<String, List<String>> orderNumbers, Map<String, String> gkeyByKey, boolean pii
    ) {
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        // LIKE escape: treat % and _ as literals in contains check by normalizing
        if (needle != null) {
            needle = needle.replace("\\", "");
        }
        String finalNeedle = needle;
        return rows.stream().filter(pack -> {
            var row = pack.insight();
            if (filters.channel != null && !filters.channel.equalsIgnoreCase(nullToEmpty(row.channel()))) return false;
            if (filters.status != null && !filters.status.equalsIgnoreCase(row.status())) return false;
            if (filters.segment != null && !filters.segment.equalsIgnoreCase(row.segment())) return false;
            if (filters.joinedFrom != null || filters.joinedTo != null) {
                if (row.joinedAt() == null) return false;
                var day = LocalDate.ofInstant(row.joinedAt(), BANGKOK);
                if (filters.joinedFrom != null && day.isBefore(filters.joinedFrom)) return false;
                if (filters.joinedTo != null && day.isAfter(filters.joinedTo)) return false;
            }
            if (finalNeedle != null) {
                if (nullToEmpty(row.displayName()).toLowerCase(Locale.ROOT).contains(finalNeedle)) return true;
                if (pii && nullToEmpty(row.phone()).toLowerCase(Locale.ROOT).contains(finalNeedle)) return true;
                if (pii && nullToEmpty(row.email()).toLowerCase(Locale.ROOT).contains(finalNeedle)) return true;
                var nums = orderNumbers.getOrDefault(gkeyByKey.getOrDefault(row.key(), pack.gkey()), List.of());
                return nums.stream().anyMatch(n -> n != null && n.toLowerCase(Locale.ROOT).contains(finalNeedle));
            }
            return true;
        }).toList();
    }

    private List<InsightRow> sortRows(List<InsightRow> rows, String sort, boolean asc) {
        Comparator<InsightRow> cmp = switch (sort) {
            case "totalSpent" -> Comparator.comparing(r -> r.insight().totalSpent() == null ? BigDecimal.ZERO : r.insight().totalSpent());
            case "orderCount" -> Comparator.comparingLong(r -> r.insight().orderCount());
            case "name" -> Comparator.comparing(r -> nullToEmpty(r.insight().displayName()), String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(r -> r.insight().lastOrderAt() == null ? Instant.EPOCH : r.insight().lastOrderAt());
        };
        if (!asc) cmp = cmp.reversed();
        cmp = cmp.thenComparing(r -> r.insight().key());
        return rows.stream().sorted(cmp).toList();
    }

    private CustomerInsightsDtos.InsightsStats computeStats(List<InsightRow> rows) {
        long total = rows.size();
        var month = YearMonth.now(BANGKOK);
        long newThisMonth = rows.stream().filter(r -> {
            if (r.insight().joinedAt() == null) return false;
            return YearMonth.from(LocalDate.ofInstant(r.insight().joinedAt(), BANGKOK)).equals(month);
        }).count();
        long vip = rows.stream().filter(r -> "VIP".equals(r.insight().segment())).count();
        long repeat = rows.stream().filter(r -> r.insight().orderCount() >= REPEAT_ORDERS).count();
        BigDecimal revenue = rows.stream()
            .map(r -> r.insight().totalSpent() == null ? BigDecimal.ZERO : r.insight().totalSpent())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long orders = rows.stream().mapToLong(r -> r.insight().orderCount()).sum();
        BigDecimal aov = orders == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);
        return new CustomerInsightsDtos.InsightsStats(total, newThisMonth, repeat, vip, revenue, aov);
    }

    private Filters parseFilters(String channel, String status, String segment, String joinedFrom, String joinedTo,
                                 String sort, String direction, Integer page, Integer size) {
        String sortKey = sort == null || sort.isBlank() ? "lastOrderAt" : sort.trim();
        if (!SORTS.contains(sortKey)) throw invalid("sort", "SORT_INVALID", "รูปแบบการเรียงไม่ถูกต้อง");
        boolean asc = direction != null && direction.equalsIgnoreCase("asc");
        String statusVal = blank(status) ? null : status.trim().toUpperCase(Locale.ROOT);
        if (statusVal != null && !STATUSES.contains(statusVal)) throw invalid("status", "STATUS_INVALID", "สถานะไม่ถูกต้อง (ACTIVE|INACTIVE)");
        String segmentVal = blank(segment) ? null : segment.trim().toUpperCase(Locale.ROOT);
        if (segmentVal != null && !SEGMENTS.contains(segmentVal)) throw invalid("segment", "SEGMENT_INVALID", "กลุ่มลูกค้าไม่ถูกต้อง (VIP|REPEAT|GENERAL)");
        LocalDate from = parseDay(joinedFrom, "joinedFrom");
        LocalDate to = parseDay(joinedTo, "joinedTo");
        if (from != null && to != null && from.isAfter(to)) {
            throw invalid("joinedFrom", "DATE_RANGE_INVALID", "วันที่เริ่มต้นต้องไม่หลังวันที่สิ้นสุด");
        }
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null ? 20 : size;
        if (s < 1 || s > MAX_PAGE_SIZE) throw invalid("size", "SIZE_INVALID", "ขนาดหน้าต้องอยู่ระหว่าง 1–100");
        String channelVal = blank(channel) ? null : channel.trim();
        return new Filters(channelVal, statusVal, segmentVal, from, to, sortKey, asc, p, s);
    }

    private LocalDate parseDay(String raw, String field) {
        if (blank(raw)) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw invalid(field, "DATE_RANGE_INVALID", "รูปแบบวันที่ต้องเป็น YYYY-MM-DD");
        }
    }

    private MapSqlParameterSource paramsForKey(UUID shopUuid, String gkey) {
        var params = new MapSqlParameterSource("shopId", shopUuid);
        if (gkey.startsWith("u:")) {
            params.addValue("buyerId", UUID.fromString(gkey.substring(2)));
            params.addValue("customerId", null);
            params.addValue("mode", "buyer");
        } else {
            params.addValue("customerId", UUID.fromString(gkey));
            params.addValue("buyerId", null);
            params.addValue("mode", "customer");
        }
        return params;
    }

    private static String keyPredicate(String alias) {
        return "( (:mode = 'customer' AND " + alias + ".shop_customer_id = :customerId)"
            + " OR (:mode = 'buyer' AND " + alias + ".shop_customer_id IS NULL AND " + alias + ".buyer_user_id = :buyerId) )";
    }

    private String encodePublicKey(UUID shopUuid, String gkey) {
        if (gkey.startsWith("u:")) {
            return keys.encode(shopUuid, null, UUID.fromString(gkey.substring(2)));
        }
        return keys.encode(shopUuid, UUID.fromString(gkey), null);
    }

    private Shop requireShop(UUID userId, Long shopNo) {
        var shop = shops.findByShopId(shopNo).orElseThrow(CustomerInsightsService::shopNotFound);
        if (userId == null) throw shopNotFound();
        var grants = authorization.grants(userId);
        if (!grants.superAdmin() && !grants.shopRoles().containsKey(shop.getId())) throw shopNotFound();
        return shop;
    }

    private boolean canReadPii(UUID userId, Long shopNo) {
        return authorization.checkShop(userId, shopNo, CustomerService.PII_PERMISSION) == AuthorizationService.ShopDecision.GRANTED;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ValidationException invalid(String field, String code, String message) {
        return new ValidationException(code, List.of(new FieldViolation(field, code, message)));
    }

    private static AuthException shopNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า");
    }

    private static AuthException customerNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "ไม่พบลูกค้า");
    }

    private record Filters(String channel, String status, String segment, LocalDate joinedFrom, LocalDate joinedTo,
                           String sort, boolean asc, int page, int size) {}

    private record InsightRow(String gkey, CustomerInsightsDtos.CustomerInsight insight) {}

    private record PreparedInsights(List<InsightRow> all, List<InsightRow> forStats, List<InsightRow> filtered,
                                    Map<String, List<String>> orderNumbers, Map<String, String> gkeyByKey) {}

    private record RawAgg(String gkey, UUID shopCustomerId, long orderCount, BigDecimal totalSpent, String currency,
                          Instant firstOrderAt, Instant lastOrderAt, String channel, String recipientName, String phone) {}

    record CrmRow(UUID id, String displayName, String phone, String email, String source, String status, Instant createdAt) {}
}
