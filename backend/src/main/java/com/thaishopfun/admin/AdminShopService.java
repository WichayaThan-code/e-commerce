package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminPlatformDtos.*;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopReinstatementService;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.ShopSuspensionService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/** Platform admin view of all shops (list / detail incl. members, counts, usage) and shop suspend / reactivate. */
@Service
public class AdminShopService {
    private static final Map<String, String> SORTS = Map.of(
        "createdAt", "s.created_date_time", "name", "LOWER(s.name)", "shopId", "s.shop_no", "status", "s.status",
        "products", "products", "orders", "orders");

    private final NamedParameterJdbcTemplate jdbc;
    private final ShopRepository shops;
    private final ShopSuspensionService suspensions;
    private final ShopReinstatementService reinstatements;
    private final AdminShopUsageService usage;
    private final AdminRefs refs;

    public AdminShopService(NamedParameterJdbcTemplate jdbc, ShopRepository shops, ShopSuspensionService suspensions,
                            ShopReinstatementService reinstatements, AdminShopUsageService usage, AdminRefs refs) {
        this.jdbc = jdbc;
        this.shops = shops;
        this.suspensions = suspensions;
        this.reinstatements = reinstatements;
        this.usage = usage;
        this.refs = refs;
    }

    public record Filter(String q, String status, UUID merchantId, UUID ownerUserId, String category, String province,
                         String suspendReasonCode, LocalDate from, LocalDate to) {}

    @Transactional(readOnly = true)
    public Page<ShopRow> list(Filter filter, String sort, String dir, int page, int size) {
        var safeSize = AdminRefs.size(size);
        var safePage = AdminRefs.page(page);
        var p = new MapSqlParameterSource();
        var where = where(filter, p);
        var total = Optional.ofNullable(jdbc.queryForObject(
            "SELECT COUNT(*) FROM shops s LEFT JOIN users u ON u.id = s.owner_user_id WHERE " + where, p, Long.class)).orElse(0L);
        var order = SORTS.getOrDefault(sort == null ? "createdAt" : sort, "s.created_date_time") + " " + AdminRefs.direction(dir) + ", s.shop_no DESC";
        p.addValue("limit", safeSize).addValue("offset", (long) safePage * safeSize);
        var rows = jdbc.query("""
            SELECT s.id, s.shop_no, s.name, s.name_en, s.slug, s.status, s.category, s.province, s.created_date_time,
                   s.published_at, s.suspended_at, o.id AS merchant_id, o.title, o.first_name AS o_first, o.last_name AS o_last,
                   o.status AS merchant_status, u.id AS user_id, u.first_name, u.last_name, u.email, u.phone, u.status AS user_status,
                   (SELECT COUNT(*) FROM shop_members m WHERE m.shop_id = s.id AND m.status = 'ACTIVE' AND m.removed_at IS NULL) AS members,
                   (SELECT COUNT(*) FROM products pr WHERE pr.shop_id = s.id AND pr.deleted_at IS NULL) AS products,
                   (SELECT COUNT(*) FROM orders x WHERE x.shop_id = s.id) AS orders
              FROM shops s LEFT JOIN owners o ON o.id = s.owner_id LEFT JOIN users u ON u.id = s.owner_user_id
             WHERE %s ORDER BY %s LIMIT :limit OFFSET :offset
            """.formatted(where, order), p, (rs, i) -> new ShopRow(
            rs.getLong("shop_no"), rs.getString("name"), rs.getString("name_en"), rs.getString("slug"),
            AdminRefs.apiShopStatus(rs.getString("status")), rs.getString("category"), rs.getString("province"),
            rs.getObject("merchant_id", UUID.class), merchantName(rs.getString("title"), rs.getString("o_first"), rs.getString("o_last")),
            rs.getString("merchant_status"),
            rs.getObject("user_id", UUID.class) == null ? null : new UserRef(rs.getObject("user_id", UUID.class), rs.getString("first_name"),
                rs.getString("last_name"), rs.getString("email"), rs.getString("phone"), rs.getString("user_status")),
            rs.getLong("members"), rs.getLong("products"), rs.getLong("orders"),
            AdminRefs.instant(rs, "created_date_time"), AdminRefs.instant(rs, "published_at"), AdminRefs.instant(rs, "suspended_at")));
        return AdminRefs.page(rows, safePage, safeSize, total);
    }

    private static String where(Filter f, MapSqlParameterSource p) {
        var sql = new StringBuilder("s.deleted_at IS NULL");
        if (f.q() != null && !f.q().isBlank()) {
            p.addValue("q", AdminRefs.like(f.q())).addValue("raw", f.q().trim());
            sql.append(" AND (LOWER(s.name) LIKE :q OR LOWER(COALESCE(s.name_en, '')) LIKE :q OR LOWER(s.slug) LIKE :q")
                .append(" OR LOWER(COALESCE(u.email, '')) LIKE :q OR CAST(s.shop_no AS VARCHAR(20)) = :raw)");
        }
        if (f.status() != null && !f.status().isBlank()) {
            p.addValue("statuses", AdminRefs.storedShopStatuses(f.status()));
            sql.append(" AND s.status IN (:statuses)");
        }
        if (f.merchantId() != null) { p.addValue("merchantId", f.merchantId()); sql.append(" AND s.owner_id = :merchantId"); }
        if (f.ownerUserId() != null) { p.addValue("ownerUserId", f.ownerUserId()); sql.append(" AND s.owner_user_id = :ownerUserId"); }
        if (f.category() != null && !f.category().isBlank()) { p.addValue("category", f.category().trim()); sql.append(" AND s.category = :category"); }
        if (f.province() != null && !f.province().isBlank()) { p.addValue("province", f.province().trim()); sql.append(" AND s.province = :province"); }
        if (f.suspendReasonCode() != null && !f.suspendReasonCode().isBlank()) {
            p.addValue("src", f.suspendReasonCode().trim().toUpperCase(Locale.ROOT));
            sql.append(" AND s.status = 'SUSPENDED' AND s.suspend_reason_code = :src");
        }
        if (f.from() != null) { p.addValue("from", AdminRefs.startOf(f.from())); sql.append(" AND s.created_date_time >= :from"); }
        if (f.to() != null) { p.addValue("to", AdminRefs.endOf(f.to())); sql.append(" AND s.created_date_time < :to"); }
        return sql.toString();
    }

    @Transactional(readOnly = true)
    public ShopDetail detail(Long shopId) {
        return detail(require(shopId));
    }

    @Transactional(readOnly = true)
    public ShopUsage usage(Long shopId) {
        var shop = require(shopId);
        return usage.usage(List.of(key(shop))).get(shop.getUuid());
    }

    @Transactional(readOnly = true)
    public Page<ShopUsage> usagePage(Filter filter, String sort, String dir, int page, int size) {
        var rows = list(filter, sort, dir, page, size);
        var keys = new ArrayList<AdminShopUsageService.ShopKey>();
        for (var row : rows.items()) {
            shops.findByShopId(row.shopId()).ifPresent(shop -> keys.add(key(shop)));
        }
        var map = usage.usage(keys);
        return AdminRefs.page(keys.stream().map(k -> map.get(k.id())).toList(), rows.page(), rows.size(), rows.totalItems());
    }

    @Transactional
    public ShopActionResponse suspend(Long shopId, ReasonRequest request, UUID actorId) {
        var shop = require(shopId);
        var changed = suspensions.suspendShop(shop, actorId, code(request, ShopSuspensionService.DEFAULT_SUSPEND_CODE), request.reason().trim(), true);
        return new ShopActionResponse(changed, detail(shop));
    }

    @Transactional
    public ShopActionResponse reactivate(Long shopId, ReasonRequest request, UUID actorId) {
        var shop = require(shopId);
        var changed = suspensions.reactivateShop(shop, actorId, code(request, ShopSuspensionService.DEFAULT_RESTORE_CODE), request.reason().trim(), true);
        return new ShopActionResponse(changed, detail(shop));
    }

    static String code(ReasonRequest request, String fallback) {
        return request.reasonCode() == null || request.reasonCode().isBlank() ? fallback : request.reasonCode().trim();
    }

    private Shop require(Long shopId) {
        return shops.findByShopId(shopId).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
    }

    private static AdminShopUsageService.ShopKey key(Shop shop) {
        return new AdminShopUsageService.ShopKey(shop.getUuid(), shop.getId(), shop.getName(), shop.getStatus().name());
    }

    ShopDetail detail(Shop shop) {
        var members = jdbc.query("""
            SELECT m.user_id, u.first_name, u.last_name, u.email, m.role, r.name_th, m.status, m.joined_at, m.removed_at
              FROM shop_members m JOIN users u ON u.id = m.user_id LEFT JOIN auth_roles r ON r.code = m.role
             WHERE m.shop_id = :shop
             ORDER BY CASE WHEN m.status = 'ACTIVE' AND m.removed_at IS NULL THEN 0 ELSE 1 END, m.joined_at
            """, new MapSqlParameterSource("shop", shop.getUuid()), (rs, i) -> new ShopMemberView(
            rs.getObject("user_id", UUID.class), rs.getString("first_name"), rs.getString("last_name"), rs.getString("email"),
            rs.getString("role"), rs.getString("name_th"),
            rs.getTimestamp("removed_at") != null ? "REMOVED" : rs.getString("status"),
            AdminRefs.instant(rs, "joined_at"), AdminRefs.instant(rs, "removed_at")));
        var owner = shop.getOwner();
        var account = shop.getUser();
        var names = refs.users(Arrays.asList(shop.getSuspendedBy(), shop.getRestoredBy()));
        var suspension = new Suspension(shop.getStatus().name(), shop.getSuspendedAt(), shop.getSuspendedBy(),
            AdminRefs.name(names.get(shop.getSuspendedBy())), shop.getSuspendReasonCode(), shop.getSuspendReason(),
            shop.getRestoredAt(), shop.getRestoredBy(), AdminRefs.name(names.get(shop.getRestoredBy())));
        var userRef = account == null ? null : new UserRef(account.getId(), account.getFirstName(), account.getLastName(),
            account.getEmail(), account.getPhone(), account.getStatus().name());
        return new ShopDetail(shop.getId(), shop.getName(), shop.getNameEn(), shop.getSlug(), shop.getStatus().name(), shop.getCategory(),
            shop.getDescription(), shop.getAddress(), shop.getProvince(), shop.getDistrict(), shop.getSubdistrict(), shop.getPostalCode(),
            owner == null ? null : owner.getId(),
            owner == null ? null : merchantName(owner.getTitle(), owner.getFirstName(), owner.getLastName()),
            owner == null ? null : owner.getStatus(), userRef, suspension, reinstatements.pendingMessage(shop.getId()),
            members, usage.usage(List.of(key(shop))).get(shop.getUuid()), shop.getCreatedAt(), shop.getUpdatedAt(), shop.getPublishedAt());
    }

    static String merchantName(String title, String first, String last) {
        if (first == null && last == null) return null;
        return (Objects.toString(title, "") + " " + Objects.toString(first, "") + " " + Objects.toString(last, "")).trim().replaceAll(" +", " ");
    }
}
