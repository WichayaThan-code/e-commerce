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
    /** Rolling retention for the merchant bell (matches {@link MerchantLifecycleNotificationWriter#RETENTION_HOURS}). */
    private static final int LOOKBACK_HOURS = MerchantLifecycleNotificationWriter.RETENTION_HOURS;
    private static final Pattern ITEM_KEY = Pattern.compile("^[A-Z_]{3,40}:.{1,80}$");

    private final ShopRepository shops;
    private final AuthorizationService authz;
    private final NamedParameterJdbcTemplate jdbc;
    private final CustomerInsightKeys customerKeys;
    private final MerchantNotificationPreferenceService preferences;

    public MerchantNotificationService(ShopRepository shops, AuthorizationService authz,
                                       NamedParameterJdbcTemplate jdbc, CustomerInsightKeys customerKeys,
                                       MerchantNotificationPreferenceService preferences) {
        this.shops = shops;
        this.authz = authz;
        this.jdbc = jdbc;
        this.customerKeys = customerKeys;
        this.preferences = preferences;
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
        String type = key.substring(0, key.indexOf(':'));
        if (!ctx.enabledTypes().contains(type)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_VISIBLE",
                "ไม่พบการแจ้งเตือนนี้ในขอบเขตที่มองเห็นได้");
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

    /**
     * Marks only currently visible feed items as read. Does not advance {@code read_all_at}
     * so hidden (preference-off) categories keep their previous read/unread state.
     */
    @Transactional
    public void markAllRead(UUID userId, Long shopNo) {
        var ctx = open(userId, shopNo);
        CursorPoint after = null;
        while (true) {
            var rows = loadFeed(ctx, after, MAX_SIZE);
            if (rows.isEmpty()) break;
            for (var row : rows) {
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
                    .addValue("key", row.key()));
            }
            if (rows.size() < MAX_SIZE) break;
            var last = rows.get(rows.size() - 1);
            after = new CursorPoint(last.createdAt(), last.key());
        }
    }

    private Ctx open(UUID userId, Long shopNo) {
        var shop = shops.findByShopId(shopNo)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
        boolean shopRead = granted(userId, shopNo, "SHOP_READ");
        boolean order = granted(userId, shopNo, "ORDER_READ");
        boolean customer = granted(userId, shopNo, "CUSTOMER_READ");
        boolean product = granted(userId, shopNo, "PRODUCT_READ");
        boolean shipment = granted(userId, shopNo, "SHIPMENT_READ");
        boolean userRoleUpdate = granted(userId, shopNo, "USER_ROLE_UPDATE");
        if (!shopRead && !order && !customer && !product && !shipment && !userRoleUpdate) {
            throw new AuthException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                "คุณไม่มีสิทธิ์ทำรายการนี้ (ต้องมีสิทธิ์ SHOP_READ / ORDER_READ / CUSTOMER_READ / PRODUCT_READ / SHIPMENT_READ / USER_ROLE_UPDATE)");
        }
        boolean pii = granted(userId, shopNo, "CUSTOMER_PII_READ");
        Set<String> enabledTypes = preferences.enabledEventTypes(shop.getUuid(), userId);
        return new Ctx(shop, shopRead, order, customer, product, shipment, userRoleUpdate, pii, enabledTypes);
    }

    private boolean granted(UUID userId, Long shopNo, String permission) {
        return authz.checkShop(userId, shopNo, permission) == AuthorizationService.ShopDecision.GRANTED;
    }

    private List<FeedRow> loadFeed(Ctx ctx, CursorPoint after, int limit) {
        Instant since = Instant.now().minus(LOOKBACK_HOURS, ChronoUnit.HOURS);
        var parts = new ArrayList<String>();
        var params = new MapSqlParameterSource()
            .addValue("shopId", ctx.shopUuid())
            .addValue("since", Timestamp.from(since))
            .addValue("limit", limit);

        // Durable lifecycle / membership / plan events — filtered by required_permission + preferences.
        var lifecyclePerms = new ArrayList<String>();
        if (ctx.shopRead()) lifecyclePerms.add("SHOP_READ");
        if (ctx.userRoleUpdate()) lifecyclePerms.add("USER_ROLE_UPDATE");
        var lifecycleTypes = new ArrayList<String>();
        for (String type : List.of(
            MerchantNotificationTypes.SHOP_APPROVED,
            MerchantNotificationTypes.SHOP_REJECTED,
            MerchantNotificationTypes.SHOP_SUSPENDED,
            MerchantNotificationTypes.SHOP_RESTORED,
            MerchantNotificationTypes.SHOP_PLAN_CHANGED,
            MerchantNotificationTypes.SHOP_MEMBER_ROLE_CHANGED,
            MerchantNotificationTypes.SHOP_MEMBER_REMOVED
        )) {
            if (ctx.enabledTypes().contains(type)) lifecycleTypes.add(type);
        }
        if (!lifecyclePerms.isEmpty() && !lifecycleTypes.isEmpty()) {
            params.addValue("lifecyclePerms", lifecyclePerms);
            params.addValue("lifecycleTypes", lifecycleTypes);
            parts.add("""
                SELECT n.type || ':' || n.id::text AS item_key, n.type AS item_type,
                       NULL::text AS order_no, NULL::numeric AS amount, n.created_at AS created_at,
                       NULL::uuid AS shop_customer_id, NULL::uuid AS buyer_user_id, NULL::text AS customer_name,
                       NULL::text AS sku_code,
                       CASE WHEN n.type IN ('SHOP_PLAN_CHANGED', 'SHOP_MEMBER_ROLE_CHANGED')
                            THEN NULLIF(n.entity_id, '_') ELSE NULL END AS product_name,
                       NULL::int AS available_qty
                  FROM merchant_notifications n
                 WHERE n.shop_id = :shopId
                   AND n.created_at >= :since
                   AND n.required_permission IN (:lifecyclePerms)
                   AND n.type IN (:lifecycleTypes)
                """);
        }

        if (ctx.order()) {
            if (ctx.enabledTypes().contains("ORDER_NEW")) {
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
            }
            if (ctx.enabledTypes().contains("ORDER_PAID")) {
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
            }
            if (ctx.enabledTypes().contains("ORDER_CANCELLED")) {
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
            }
            if (ctx.enabledTypes().contains("REFUND_REQUESTED")) {
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
            if (ctx.enabledTypes().contains("REFUND_DISPUTED")) {
                parts.add("""
                    SELECT 'REFUND_DISPUTED:' || r.id::text, 'REFUND_DISPUTED',
                           o.order_number, r.amount, COALESCE(r.modified_date_time, r.created_date_time),
                           NULL::uuid, NULL::uuid, NULL::text, NULL::text, NULL::text, NULL::int
                      FROM refunds r
                      JOIN orders o ON o.id = r.order_id
                     WHERE r.shop_id = :shopId
                       AND r.status = 'DISPUTED'
                       AND COALESCE(r.modified_date_time, r.created_date_time) >= :since
                    """);
            }
            if (ctx.enabledTypes().contains("REFUND_COMPLETED")) {
                parts.add("""
                    SELECT 'REFUND_COMPLETED:' || r.id::text, 'REFUND_COMPLETED',
                           o.order_number, r.amount, COALESCE(r.modified_date_time, r.created_date_time),
                           NULL::uuid, NULL::uuid, NULL::text, NULL::text, NULL::text, NULL::int
                      FROM refunds r
                      JOIN orders o ON o.id = r.order_id
                     WHERE r.shop_id = :shopId
                       AND r.status = 'COMPLETED'
                       AND COALESCE(r.modified_date_time, r.created_date_time) >= :since
                    """);
            }
            if (ctx.enabledTypes().contains("PAYMENT_SLIP_PENDING")) {
                parts.add("""
                    SELECT 'PAYMENT_SLIP_PENDING:' || pp.id::text, 'PAYMENT_SLIP_PENDING',
                           o.order_number, COALESCE(pp.amount_declared, o.grand_total), pp.created_date_time,
                           NULL::uuid, NULL::uuid, NULL::text, NULL::text, NULL::text, NULL::int
                      FROM payment_proofs pp
                      JOIN orders o ON o.id = pp.order_id
                     WHERE pp.shop_id = :shopId
                       AND pp.status = 'PENDING_REVIEW'
                       AND pp.proof_type = 'SLIP'
                       AND pp.created_date_time >= :since
                    """);
            }
        }
        if (ctx.customer() && ctx.enabledTypes().contains("CUSTOMER_NEW")) {
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
            if (ctx.enabledTypes().contains("LOW_STOCK")) {
                parts.add("""
                    SELECT 'LOW_STOCK:' || l.id::text, 'LOW_STOCK',
                           NULL::text, NULL::numeric, l.modified_date_time,
                           NULL::uuid, NULL::uuid, NULL::text, s.sku_code,
                           COALESCE(NULLIF(p.name_th, ''), s.sku_code), l.available
                      FROM inventory_levels l
                      JOIN product_skus s ON s.id = l.sku_id
                      JOIN products p ON p.id = s.product_id
                     WHERE l.shop_id = :shopId
                       AND l.available > 0
                       AND l.available < COALESCE(l.low_stock_threshold, NULLIF(l.safety_stock, 0), 5)
                       AND l.modified_date_time >= :since
                    """);
            }
            if (ctx.enabledTypes().contains("PRODUCT_REVIEW_NEW")) {
                parts.add("""
                    SELECT 'PRODUCT_REVIEW_NEW:' || r.id::text, 'PRODUCT_REVIEW_NEW',
                           NULL::text, NULL::numeric, r.created_date_time,
                           NULL::uuid, NULL::uuid, NULL::text, NULL::text,
                           COALESCE(NULLIF(p.name_th, ''), p.slug), CAST(r.rating AS int)
                      FROM product_reviews r
                      JOIN products p ON p.id = r.product_id
                     WHERE r.shop_id = :shopId
                       AND r.status = 'PUBLISHED'
                       AND r.deleted_at IS NULL
                       AND r.created_date_time >= :since
                    """);
            }
        }
        if (ctx.shipment() && ctx.enabledTypes().contains("SHIPMENT_EXCEPTION")) {
            parts.add("""
                SELECT 'SHIPMENT_EXCEPTION:' || s.id::text, 'SHIPMENT_EXCEPTION',
                       o.order_number, o.grand_total, COALESCE(s.last_tracking_event_at, s.modified_date_time),
                       NULL::uuid, NULL::uuid, NULL::text, s.status, NULL::text, NULL::int
                  FROM shipments s
                  JOIN orders o ON o.id = s.order_id
                 WHERE s.shop_id = :shopId
                   AND s.status IN ('DELIVERY_FAILED', 'RETURNED')
                   AND COALESCE(s.last_tracking_event_at, s.modified_date_time) >= :since
                """);
        }
        if (parts.isEmpty()) return List.of();

        String union = String.join(" UNION ALL ", parts);
        // First page: no cursor predicate. Next page: non-null Instant/key only (avoid PG 42P18 on typed-null IS NULL).
        String sql;
        if (after == null) {
            sql = """
                SELECT * FROM (
                  %s
                ) feed
                ORDER BY created_at DESC, item_key DESC
                LIMIT :limit
                """.formatted(union);
        } else {
            params.addValue("cursorAt", Timestamp.from(after.createdAt()));
            params.addValue("cursorKey", after.key());
            sql = """
                SELECT * FROM (
                  %s
                ) feed
                WHERE created_at < :cursorAt
                   OR (created_at = :cursorAt AND item_key < :cursorKey)
                ORDER BY created_at DESC, item_key DESC
                LIMIT :limit
                """.formatted(union);
        }

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
        if (row.skuCode() != null) {
            if ("SHIPMENT_EXCEPTION".equals(row.type())) params.put("shipmentStatus", row.skuCode());
            else params.put("skuCode", row.skuCode());
        }
        if (row.productName() != null) {
            if ("SHOP_PLAN_CHANGED".equals(row.type())) {
                params.put("planCode", row.productName() == null || row.productName().isBlank() ? "—" : row.productName());
            } else if ("SHOP_MEMBER_ROLE_CHANGED".equals(row.type())) {
                params.put("role", row.productName() == null || row.productName().isBlank() ? "—" : row.productName());
            } else {
                params.put("productName", row.productName());
            }
        }
        if (row.availableQty() != null) {
            if ("PRODUCT_REVIEW_NEW".equals(row.type())) params.put("rating", row.availableQty());
            else params.put("available", row.availableQty());
        }
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
            if (sep <= 0) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "cursor ไม่ถูกต้อง", "cursor");
            }
            long millis = Long.parseLong(raw.substring(0, sep));
            String key = raw.substring(sep + 1);
            if (key.isBlank()) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "cursor ไม่ถูกต้อง", "cursor");
            }
            return new CursorPoint(Instant.ofEpochMilli(millis), key);
        } catch (AuthException ex) {
            throw ex;
        } catch (RuntimeException ignored) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "cursor ไม่ถูกต้อง", "cursor");
        }
    }

    private record Ctx(
        Shop shop,
        boolean shopRead,
        boolean order,
        boolean customer,
        boolean product,
        boolean shipment,
        boolean userRoleUpdate,
        boolean pii,
        Set<String> enabledTypes
    ) {
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
