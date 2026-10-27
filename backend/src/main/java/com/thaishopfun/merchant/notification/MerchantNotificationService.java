package com.thaishopfun.merchant.notification;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.customer.CustomerInsightKeys;
import com.thaishopfun.customer.CustomerService;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Base64;
import java.util.regex.Pattern;

@Service
public class MerchantNotificationService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final int UNREAD_CAP = 99;
    private static final int LOOKBACK_DAYS = 30;
    private static final int LOW_STOCK_DAYS = 7;
    private static final Pattern ITEM_KEY = Pattern.compile("^[A-Z_]{3,40}:.{1,80}$");

    private final ShopRepository shops;
    private final AuthorizationService authz;
    private final NamedParameterJdbcTemplate jdbc;
    private final CustomerInsightKeys customerKeys;

    public MerchantNotificationService(ShopRepository shops, AuthorizationService authz,
                                       NamedParameterJdbcTemplate jdbc, CustomerInsightKeys customerKeys) {
        this.shops = shops;
        this.authz = authz;
        this.jdbc = jdbc;
        this.customerKeys = customerKeys;
    }

    @Transactional(readOnly = true)
    public MerchantNotificationDtos.Page list(UUID userId, Long shopNo, String cursor, Integer size) {
        var ctx = open(userId, shopNo);
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
        var cursorPoint = decodeCursor(cursor);
        var rows = loadFeed(ctx, cursorPoint, pageSize + 1);
        String next = null;
        if (rows.size() > pageSize) {
            rows = rows.subList(0, pageSize);
            var last = rows.get(rows.size() - 1);
            next = encodeCursor(last.createdAt(), last.key());
        }
        var readState = loadReadState(ctx.shopUuid(), userId);
        var items = rows.stream().map(r -> toItem(ctx, r, readState)).toList();
        return new MerchantNotificationDtos.Page(items, next);
    }

    @Transactional(readOnly = true)
    public MerchantNotificationDtos.UnreadCount unreadCount(UUID userId, Long shopNo) {
        var ctx = open(userId, shopNo);
        var rows = loadFeed(ctx, null, UNREAD_CAP + 1);
        var readState = loadReadState(ctx.shopUuid(), userId);
        int unread = 0;
        for (var row : rows) {
            if (!isRead(row.createdAt(), row.key(), readState)) {
                unread++;
                if (unread >= UNREAD_CAP) break;
            }
        }
        return new MerchantNotificationDtos.UnreadCount(unread);
    }

    @Transactional
    public void markRead(UUID userId, Long shopNo, String key) {
        var ctx = open(userId, shopNo);
        if (key == null || !ITEM_KEY.matcher(key).matches()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "รหัสการแจ้งเตือนไม่ถูกต้อง");
        }
        jdbc.update("""
            INSERT INTO merchant_notification_reads (shop_id, user_id, item_key, read_at)
            SELECT :shopId, :userId, :key, now()
             WHERE NOT EXISTS (
               SELECT 1 FROM merchant_notification_reads
                WHERE shop_id = :shopId AND user_id = :userId AND item_key = :key
             )
            """, new MapSqlParameterSource()
            .addValue("shopId", ctx.shopUuid())
            .addValue("userId", userId)
            .addValue("key", key));
    }

    @Transactional
    public void markAllRead(UUID userId, Long shopNo) {
        var ctx = open(userId, shopNo);
        var params = new MapSqlParameterSource()
            .addValue("shopId", ctx.shopUuid())
            .addValue("userId", userId);
        int updated = jdbc.update("""
            UPDATE merchant_notification_cursor
               SET read_all_at = now()
             WHERE shop_id = :shopId AND user_id = :userId
            """, params);
        if (updated == 0) {
            jdbc.update("""
                INSERT INTO merchant_notification_cursor (shop_id, user_id, read_all_at)
                VALUES (:shopId, :userId, now())
                """, params);
        }
        jdbc.update("""
            DELETE FROM merchant_notification_reads
             WHERE shop_id = :shopId AND user_id = :userId
            """, params);
    }

    private Ctx open(UUID userId, Long shopNo) {
        var shop = shops.findByShopId(shopNo)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
        boolean order = granted(userId, shopNo, "ORDER_READ");
        boolean customer = granted(userId, shopNo, "CUSTOMER_READ");
        boolean product = granted(userId, shopNo, "PRODUCT_READ");
        if (!order && !customer && !product) {
            throw new AuthException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                "คุณไม่มีสิทธิ์ทำรายการนี้ (ต้องมีสิทธิ์ ORDER_READ / CUSTOMER_READ / PRODUCT_READ)");
        }
        boolean pii = granted(userId, shopNo, "CUSTOMER_PII_READ");
        return new Ctx(shop, order, customer, product, pii);
    }

    private boolean granted(UUID userId, Long shopNo, String permission) {
        return authz.checkShop(userId, shopNo, permission) == AuthorizationService.ShopDecision.GRANTED;
    }

    private List<FeedRow> loadFeed(Ctx ctx, CursorPoint after, int limit) {
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        Instant lowStockSince = Instant.now().minus(LOW_STOCK_DAYS, ChronoUnit.DAYS);
        var parts = new ArrayList<String>();
        var params = new MapSqlParameterSource()
            .addValue("shopId", ctx.shopUuid())
            .addValue("since", Timestamp.from(since))
            .addValue("lowStockSince", Timestamp.from(lowStockSince))
            .addValue("limit", limit);

        if (ctx.order()) {
            parts.add("""
                SELECT 'ORDER_NEW:' || o.order_number AS item_key, 'ORDER_NEW' AS item_type,
                       o.order_number AS order_no, o.grand_total AS amount, o.created_date_time AS created_at,
                       NULL::uuid AS shop_customer_id, NULL::uuid AS buyer_user_id, NULL::text AS customer_name,
                       NULL::text AS sku_code, NULL::text AS product_name, NULL::int AS available_qty
                  FROM orders o
                 WHERE o.shop_id = :shopId
                   AND o.merchant_confirmed_at IS NULL
                   AND o.status NOT IN ('CANCELLED', 'REFUNDED')
                   AND o.created_date_time >= :since
                """);
            parts.add("""
                SELECT 'ORDER_PAID:' || p.id::text, 'ORDER_PAID',
                       o.order_number, o.grand_total, COALESCE(p.captured_at, p.paid_at, p.created_date_time),
                       NULL::uuid, NULL::uuid, NULL::text, NULL::text, NULL::text, NULL::int
                  FROM payments p
                  JOIN orders o ON o.id = p.order_id
                 WHERE p.shop_id = :shopId AND o.shop_id = :shopId
                   AND p.status = 'CAPTURED'
                   AND o.merchant_confirmed_at IS NOT NULL
                   AND COALESCE(p.captured_at, p.paid_at, p.created_date_time) >= :since
                """);
            parts.add("""
                SELECT 'ORDER_PAID:' || h.id::text, 'ORDER_PAID',
                       o.order_number, o.grand_total, h.created_date_time,
                       NULL::uuid, NULL::uuid, NULL::text, NULL::text, NULL::text, NULL::int
                  FROM order_status_history h
                  JOIN orders o ON o.id = h.order_id
                 WHERE o.shop_id = :shopId
                   AND h.to_status = 'PAID'
                   AND h.from_status IS DISTINCT FROM h.to_status
                   AND o.merchant_confirmed_at IS NOT NULL
                   AND h.created_date_time >= :since
                """);
            parts.add("""
                SELECT 'ORDER_CANCELLED:' || h.id::text, 'ORDER_CANCELLED',
                       o.order_number, o.grand_total, h.created_date_time,
                       NULL::uuid, NULL::uuid, NULL::text, NULL::text, NULL::text, NULL::int
                  FROM order_status_history h
                  JOIN orders o ON o.id = h.order_id
                 WHERE o.shop_id = :shopId
                   AND h.to_status = 'CANCELLED'
                   AND h.from_status IS DISTINCT FROM h.to_status
                   AND h.created_date_time >= :since
                """);
            parts.add("""
                SELECT 'REFUND_REQUESTED:' || r.id::text, 'REFUND_REQUESTED',
                       o.order_number, r.amount, r.created_date_time,
                       NULL::uuid, NULL::uuid, NULL::text, NULL::text, NULL::text, NULL::int
                  FROM refunds r
                  JOIN orders o ON o.id = r.order_id
                 WHERE r.shop_id = :shopId
                   AND r.status = 'REQUESTED'
                   AND r.created_date_time >= :since
                """);
        }
        if (ctx.customer()) {
            parts.add("""
                SELECT 'CUSTOMER_NEW:' || o.order_number, 'CUSTOMER_NEW',
                       o.order_number, o.grand_total, o.merchant_confirmed_at,
                       o.shop_customer_id, o.buyer_user_id,
                       COALESCE(sc.display_name,
                         NULLIF(TRIM(BOTH FROM COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), '')),
                       NULL::text, NULL::text, NULL::int
                  FROM orders o
                  LEFT JOIN shop_customers sc ON sc.id = o.shop_customer_id
                  LEFT JOIN users u ON u.id = o.buyer_user_id
                 WHERE o.shop_id = :shopId
                   AND o.merchant_confirmed_at IS NOT NULL
                   AND o.merchant_confirmed_at >= :since
                   AND o.status <> 'CANCELLED'
                   AND (o.shop_customer_id IS NOT NULL OR o.buyer_user_id IS NOT NULL)
                   AND NOT EXISTS (
                     SELECT 1 FROM orders earlier
                      WHERE earlier.shop_id = o.shop_id
                        AND earlier.merchant_confirmed_at IS NOT NULL
                        AND (earlier.merchant_confirmed_at, earlier.id) < (o.merchant_confirmed_at, o.id)
                        AND (
                          (o.shop_customer_id IS NOT NULL AND earlier.shop_customer_id = o.shop_customer_id)
                          OR (o.shop_customer_id IS NULL AND o.buyer_user_id IS NOT NULL
                              AND earlier.shop_customer_id IS NULL AND earlier.buyer_user_id = o.buyer_user_id)
                        )
                   )
                """);
        }
        if (ctx.product()) {
            parts.add("""
                SELECT 'LOW_STOCK:' || l.id::text, 'LOW_STOCK',
                       NULL::text, NULL::numeric, l.modified_date_time,
                       NULL::uuid, NULL::uuid, NULL::text, s.sku_code,
                       COALESCE(NULLIF(p.name_th, ''), s.sku_code), l.available
                  FROM inventory_levels l
                  JOIN product_skus s ON s.id = l.sku_id
                  JOIN products p ON p.id = s.product_id
                 WHERE l.shop_id = :shopId
                   AND l.available <= COALESCE(l.low_stock_threshold, 5)
                   AND l.modified_date_time >= :lowStockSince
                """);
        }
        if (parts.isEmpty()) return List.of();

        String union = String.join(" UNION ALL ", parts);
        String cursorSql = "";
        if (after != null) {
            cursorSql = """
                 AND (created_at < :cursorAt
                      OR (created_at = :cursorAt AND item_key < :cursorKey))
                """;
            params.addValue("cursorAt", Timestamp.from(after.createdAt()));
            params.addValue("cursorKey", after.key());
        }
        String sql = """
            SELECT * FROM (
              %s
            ) feed
            WHERE 1=1
            %s
            ORDER BY created_at DESC, item_key DESC
            LIMIT :limit
            """.formatted(union, cursorSql);

        return jdbc.query(sql, params, (rs, i) -> new FeedRow(
            rs.getString("item_key"),
            rs.getString("item_type"),
            rs.getString("order_no"),
            rs.getBigDecimal("amount"),
            rs.getTimestamp("created_at").toInstant(),
            (UUID) rs.getObject("shop_customer_id"),
            (UUID) rs.getObject("buyer_user_id"),
            rs.getString("customer_name"),
            rs.getString("sku_code"),
            rs.getString("product_name"),
            rs.getObject("available_qty") == null ? null : rs.getInt("available_qty")
        ));
    }

    private MerchantNotificationDtos.Item toItem(Ctx ctx, FeedRow row, ReadState readState) {
        var params = new LinkedHashMap<String, Object>();
        String customerKey = null;
        if ("CUSTOMER_NEW".equals(row.type())) {
            String name = row.customerName();
            if (!ctx.pii()) name = CustomerService.maskName(name);
            if (name != null) params.put("customerName", name);
            if (row.shopCustomerId() != null) {
                customerKey = customerKeys.encode(ctx.shopUuid(), row.shopCustomerId(), null);
            } else if (row.buyerUserId() != null) {
                customerKey = customerKeys.encode(ctx.shopUuid(), null, row.buyerUserId());
            }
        }
        if (row.orderNo() != null) params.put("orderNo", row.orderNo());
        if (row.amount() != null) params.put("amount", row.amount());
        if (row.skuCode() != null) params.put("skuCode", row.skuCode());
        if (row.productName() != null) params.put("productName", row.productName());
        if (row.availableQty() != null) params.put("available", row.availableQty());
        return new MerchantNotificationDtos.Item(
            row.key(),
            row.type(),
            Map.copyOf(params),
            row.orderNo(),
            customerKey,
            row.amount(),
            row.createdAt(),
            isRead(row.createdAt(), row.key(), readState)
        );
    }

    private ReadState loadReadState(UUID shopUuid, UUID userId) {
        var params = new MapSqlParameterSource()
            .addValue("shopId", shopUuid)
            .addValue("userId", userId);
        Instant readAllAt = jdbc.query("""
            SELECT read_all_at FROM merchant_notification_cursor
             WHERE shop_id = :shopId AND user_id = :userId
            """, params, rs -> rs.next() ? rs.getTimestamp(1).toInstant() : Instant.EPOCH);
        if (readAllAt == null) readAllAt = Instant.EPOCH;
        var keys = new HashSet<>(jdbc.query("""
            SELECT item_key FROM merchant_notification_reads
             WHERE shop_id = :shopId AND user_id = :userId
            """, params, (rs, i) -> rs.getString(1)));
        return new ReadState(readAllAt, keys);
    }

    private static boolean isRead(Instant createdAt, String key, ReadState state) {
        return !createdAt.isAfter(state.readAllAt()) || state.keys().contains(key);
    }

    private static String encodeCursor(Instant createdAt, String key) {
        String raw = createdAt.toEpochMilli() + "|" + key;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static CursorPoint decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            int sep = raw.indexOf('|');
            if (sep <= 0) return null;
            long millis = Long.parseLong(raw.substring(0, sep));
            String key = raw.substring(sep + 1);
            if (key.isBlank()) return null;
            return new CursorPoint(Instant.ofEpochMilli(millis), key);
        } catch (RuntimeException ignored) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "cursor ไม่ถูกต้อง");
        }
    }

    private record Ctx(Shop shop, boolean order, boolean customer, boolean product, boolean pii) {
        UUID shopUuid() { return shop.getUuid(); }
    }

    private record FeedRow(
        String key, String type, String orderNo, BigDecimal amount, Instant createdAt,
        UUID shopCustomerId, UUID buyerUserId, String customerName,
        String skuCode, String productName, Integer availableQty
    ) {}

    private record ReadState(Instant readAllAt, Set<String> keys) {}

    private record CursorPoint(Instant createdAt, String key) {}
}
