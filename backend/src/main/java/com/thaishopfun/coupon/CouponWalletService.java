package com.thaishopfun.coupon;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Buyer coupon wallet. Rules:
 * <ul>
 *   <li>collect: signed-in buyer only; shop must be live (status ACTIVE = APPROVED); the code must be public
 *       (promotions.is_public), coupon + promotion active, not ended and not exhausted (usage_limit_total).
 *       Upcoming codes may be collected. Collecting twice is idempotent. There is no cap on collects.</li>
 *   <li>A public code must be collected before CartService applies it (COUPON_NOT_COLLECTED). A private code typed in the
 *       cart is auto-collected on a successful apply ({@link #collectQuietly}).</li>
 *   <li>Redeem limits stay on promotions.usage_limit_total / usage_limit_per_buyer, counted from promotion_usages.</li>
 * </ul>
 * Plain SQL is used for the coupon/promotion lookups because two JPA entities map those tables (cart.* and promotion.*)
 * and only promotion.Promotion maps is_public.
 */
@Service
public class CouponWalletService {
    private static final String COUPON_ROW = """
        select c.id as coupon_id, c.code, c.is_active as coupon_active, p.id as promotion_id, p.is_active as promotion_active,
               p.is_public, p.name, p.promo_type, p."value" as promo_value, p.min_order_amount, p.max_discount_amount,
               p.starts_at, p.ends_at, p.usage_limit_total, p.usage_limit_per_buyer,
               (select count(*) from promotion_usages u where u.promotion_id = p.id) as used_total,
               s.shop_no, s.name as shop_name, s.slug as shop_slug
          from coupons c
          join promotions p on p.id = c.promotion_id
          join shops s on s.id = c.shop_id
        """;

    private final CouponCollectionRepository collections;
    private final JdbcTemplate jdbc;

    public CouponWalletService(CouponCollectionRepository collections, JdbcTemplate jdbc) {
        this.collections = collections;
        this.jdbc = jdbc;
    }

    @Transactional
    public CouponWalletDtos.CollectResponse collect(UUID buyerId, CouponWalletDtos.CollectRequest request) {
        if (request == null || request.shopId() == null || request.code() == null || request.code().isBlank()) {
            var violations = new ArrayList<FieldViolation>();
            if (request == null || request.shopId() == null) violations.add(new FieldViolation("shopId", "REQUIRED", "ต้องระบุร้านค้า"));
            if (request == null || request.code() == null || request.code().isBlank()) violations.add(new FieldViolation("code", "REQUIRED", "ต้องระบุโค้ด"));
            throw new ValidationException(violations);
        }
        var code = request.code().trim().toUpperCase();
        var shopLive = jdbc.queryForList("select status from shops where shop_no = ?", String.class, request.shopId());
        if (shopLive.isEmpty() || !"ACTIVE".equals(shopLive.get(0))) {
            throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้");
        }
        var rows = jdbc.query(COUPON_ROW + " where s.shop_no = ? and c.code = ?", this::row, request.shopId(), code);
        // private codes are not collectable from the storefront: same 404 as an unknown code (no existence leak)
        if (rows.isEmpty() || !rows.get(0).isPublic()) {
            throw new AuthException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "ไม่พบโค้ดคูปองนี้");
        }
        var row = rows.get(0);
        var existing = collections.findByCouponIdAndUserId(row.couponId(), buyerId);
        if (existing.isPresent()) {
            return new CouponWalletDtos.CollectResponse(code, row.shopNo(), true, existing.get().getCollectedAt());
        }
        var now = Instant.now();
        if (!row.couponActive() || !row.promotionActive()) throw conflict("COUPON_INACTIVE", "คูปองนี้ถูกปิดการใช้งาน");
        if (row.endsAt() != null && !now.isBefore(row.endsAt())) throw conflict("COUPON_EXPIRED", "คูปองหมดอายุแล้ว");
        if (row.usageLimitTotal() != null && row.usedTotal() >= row.usageLimitTotal()) {
            throw conflict("COUPON_USAGE_LIMIT_REACHED", "คูปองถูกใช้ครบจำนวนแล้ว");
        }
        try {
            var saved = collections.saveAndFlush(new CouponCollection(row.couponId(), buyerId, now));
            return new CouponWalletDtos.CollectResponse(code, row.shopNo(), false, saved.getCollectedAt());
        } catch (DataIntegrityViolationException race) {
            // a parallel request collected first: still idempotent
            var winner = collections.findByCouponIdAndUserId(row.couponId(), buyerId).orElseThrow(() -> race);
            return new CouponWalletDtos.CollectResponse(code, row.shopNo(), true, winner.getCollectedAt());
        }
    }

    @Transactional(readOnly = true)
    public List<CouponWalletDtos.CollectedCoupon> list(UUID buyerId, Long shopId) {
        var sql = COUPON_ROW.replace("from coupons c", "from coupon_collections cc join coupons c on c.id = cc.coupon_id")
            .replace("s.shop_no, s.name", "cc.collected_at, (select count(*) from promotion_usages m where m.promotion_id = p.id and m.buyer_user_id = cc.user_id) as used_mine, s.shop_no, s.name")
            + " where cc.user_id = ?" + (shopId == null ? "" : " and s.shop_no = ?") + " order by cc.collected_at desc";
        var now = Instant.now();
        var args = shopId == null ? new Object[]{buyerId} : new Object[]{buyerId, shopId};
        return jdbc.query(sql, (rs, i) -> {
            var row = row(rs, i);
            var collectedAt = instant(rs, "collected_at");
            long mine = rs.getLong("used_mine");
            String status;
            if (!row.couponActive() || !row.promotionActive()) status = "INACTIVE";
            else if (row.endsAt() != null && !now.isBefore(row.endsAt())) status = "EXPIRED";
            else if (row.usageLimitPerBuyer() != null && mine >= row.usageLimitPerBuyer()) status = "USED";
            else if (row.usageLimitTotal() != null && row.usedTotal() >= row.usageLimitTotal()) status = "EXHAUSTED";
            else if (row.startsAt() != null && now.isBefore(row.startsAt())) status = "UPCOMING";
            else status = "USABLE";
            return new CouponWalletDtos.CollectedCoupon(row.shopNo(), row.shopName(), row.shopSlug(), row.code(), row.name(), row.type(),
                row.value(), row.minOrderAmount(), row.maxDiscountAmount(), row.startsAt(), row.endsAt(), collectedAt, status);
        }, args);
    }

    /** Used by CartService.applyCoupon / pricing. */
    @Transactional(readOnly = true)
    public boolean isCollected(UUID couponId, UUID buyerId) {
        return buyerId != null && collections.existsByCouponIdAndUserId(couponId, buyerId);
    }

    @Transactional(readOnly = true)
    public boolean isPublic(UUID promotionId) {
        var flags = jdbc.queryForList("select is_public from promotions where id = ?", Boolean.class, promotionId);
        return flags.isEmpty() || !Boolean.FALSE.equals(flags.get(0));
    }

    /** Auto-collect a private code the buyer typed (idempotent; joins the caller's transaction). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void collectQuietly(UUID couponId, UUID buyerId) {
        if (collections.existsByCouponIdAndUserId(couponId, buyerId)) return;
        collections.save(new CouponCollection(couponId, buyerId, Instant.now()));
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    private record Row(UUID couponId, String code, boolean couponActive, UUID promotionId, boolean promotionActive, boolean isPublic,
                       String name, String type, BigDecimal value, BigDecimal minOrderAmount, BigDecimal maxDiscountAmount,
                       Instant startsAt, Instant endsAt, Integer usageLimitTotal, Integer usageLimitPerBuyer, long usedTotal,
                       Long shopNo, String shopName, String shopSlug) {}

    private Row row(ResultSet rs, int i) throws SQLException {
        var isPublic = rs.getObject("is_public");
        return new Row(rs.getObject("coupon_id", UUID.class), rs.getString("code"), rs.getBoolean("coupon_active"),
            rs.getObject("promotion_id", UUID.class), rs.getBoolean("promotion_active"), isPublic == null || Boolean.TRUE.equals(isPublic),
            rs.getString("name"), rs.getString("promo_type"), rs.getBigDecimal("promo_value"), rs.getBigDecimal("min_order_amount"),
            rs.getBigDecimal("max_discount_amount"), instant(rs, "starts_at"), instant(rs, "ends_at"),
            (Integer) rs.getObject("usage_limit_total"), (Integer) rs.getObject("usage_limit_per_buyer"),
            rs.getLong("used_total"), rs.getLong("shop_no"), rs.getString("shop_name"), rs.getString("shop_slug"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column);
        if (value == null) return null;
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof java.time.LocalDateTime ldt) return ldt.atZone(java.time.ZoneOffset.UTC).toInstant();
        throw new SQLException("Unsupported timestamp type for " + column + ": " + value.getClass());
    }
}
