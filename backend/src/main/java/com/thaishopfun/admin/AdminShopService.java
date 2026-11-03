package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminPlatformDtos.*;
import com.thaishopfun.audit.PlatformAuditService;
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
    private final PlatformAuditService platformAudit;

    public AdminShopService(NamedParameterJdbcTemplate jdbc, ShopRepository shops, ShopSuspensionService suspensions,
                            ShopReinstatementService reinstatements, AdminShopUsageService usage, AdminRefs refs,
                            PlatformAuditService platformAudit) {
        this.jdbc = jdbc;
        this.shops = shops;
        this.suspensions = suspensions;
        this.reinstatements = reinstatements;
        this.usage = usage;
        this.refs = refs;
        this.platformAudit = platformAudit;
    }

    public record Filter(String q, String status, UUID merchantId, UUID ownerUserId, String category, String province,
                         String suspendReasonCode, LocalDate from, LocalDate to, String plan) {}

    @Transactional
    public Page<ShopRow> list(Filter filter, String sort, String dir, int page, int size, UUID actorId) {
        auditContactSearchIfNeeded(filter.q(), actorId);
        var normalized = withNormalizedPlan(filter);
        var safeSize = AdminRefs.size(size);
        var safePage = AdminRefs.page(page);
        var p = new MapSqlParameterSource();
        var where = where(normalized, p);
        var total = Optional.ofNullable(jdbc.queryForObject(
            "SELECT COUNT(*) FROM shops s LEFT JOIN owners o ON o.id = s.owner_id LEFT JOIN users u ON u.id = s.owner_user_id WHERE " + where, p, Long.class)).orElse(0L);
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
                rs.getString("last_name"), AdminContactMask.email(rs.getString("email")), AdminContactMask.phone(rs.getString("phone")), rs.getString("user_status")),
            rs.getLong("members"), rs.getLong("products"), rs.getLong("orders"),
            AdminRefs.instant(rs, "created_date_time"), AdminRefs.instant(rs, "published_at"), AdminRefs.instant(rs, "suspended_at")));
        return AdminRefs.page(rows, safePage, safeSize, total);
    }

    private void auditContactSearchIfNeeded(String q, UUID actorId) {
        AdminContactSearch.auditIfNeeded(platformAudit, actorId, q, "PLATFORM_SHOP_READ", "SHOP");
    }

    /** SQL WHERE for shops (+ owners/users joins). Contact match via {@link AdminContactSearch}. */
    static String where(Filter f, MapSqlParameterSource p) {
        var sql = new StringBuilder("s.deleted_at IS NULL");
        if (f.q() != null && !f.q().isBlank()) {
            var raw = f.q().trim();
            p.addValue("q", AdminRefs.like(raw)).addValue("raw", raw);
            sql.append(" AND (LOWER(s.name) LIKE :q OR LOWER(COALESCE(s.name_en, '')) LIKE :q OR LOWER(s.slug) LIKE :q")
                .append(" OR CAST(s.shop_no AS VARCHAR(20)) = :raw");
            AdminContactSearch.appendMatchers(sql, p, raw, List.of("u.phone", "o.phone"), List.of("u.email", "o.email"));
            sql.append(")");
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
        if (f.plan() != null && !f.plan().isBlank()) {
            if (AdminShopUsageService.NO_PLAN.equals(f.plan())) {
                sql.append(" AND s.plan_code IS NULL");
            } else {
                p.addValue("plan", f.plan());
                sql.append(" AND s.plan_code = :plan");
            }
        }
        return sql.toString();
    }

    /** Validate / normalize {@code filter.plan()} (blank → null; invalid → 400 PLAN_INVALID). */
    private static Filter withNormalizedPlan(Filter filter) {
        var plan = AdminShopUsageService.normalizePlan(filter.plan());
        if (Objects.equals(plan, filter.plan())) return filter;
        return new Filter(filter.q(), filter.status(), filter.merchantId(), filter.ownerUserId(), filter.category(),
            filter.province(), filter.suspendReasonCode(), filter.from(), filter.to(), plan);
    }

    @Transactional(readOnly = true)
    public List<AdminShopUsageService.ShopKey> listKeys(Filter filter, String sort, String dir) {
        var p = new MapSqlParameterSource();
        var where = where(filter, p);
        var order = SORTS.getOrDefault(sort == null ? "createdAt" : sort, "s.created_date_time") + " " + AdminRefs.direction(dir) + ", s.shop_no DESC";
        return jdbc.query("""
            SELECT s.id, s.shop_no, s.name, s.status, s.slug, s.plan_code
              FROM shops s LEFT JOIN owners o ON o.id = s.owner_id LEFT JOIN users u ON u.id = s.owner_user_id
             WHERE %s ORDER BY %s
            """.formatted(where, order), p, (rs, i) -> mapKey(rs));
    }

    private List<AdminShopUsageService.ShopKey> listKeysPage(Filter filter, String sort, String dir, int page, int size) {
        var p = new MapSqlParameterSource();
        var where = where(filter, p);
        var order = SORTS.getOrDefault(sort == null ? "createdAt" : sort, "s.created_date_time") + " " + AdminRefs.direction(dir) + ", s.shop_no DESC";
        p.addValue("limit", size).addValue("offset", (long) page * size);
        return jdbc.query("""
            SELECT s.id, s.shop_no, s.name, s.status, s.slug, s.plan_code
              FROM shops s LEFT JOIN owners o ON o.id = s.owner_id LEFT JOIN users u ON u.id = s.owner_user_id
             WHERE %s ORDER BY %s LIMIT :limit OFFSET :offset
            """.formatted(where, order), p, (rs, i) -> mapKey(rs));
    }

    private long countKeys(Filter filter) {
        var p = new MapSqlParameterSource();
        var where = where(filter, p);
        return Optional.ofNullable(jdbc.queryForObject(
            "SELECT COUNT(*) FROM shops s LEFT JOIN owners o ON o.id = s.owner_id LEFT JOIN users u ON u.id = s.owner_user_id WHERE " + where, p, Long.class)).orElse(0L);
    }

    private static AdminShopUsageService.ShopKey mapKey(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AdminShopUsageService.ShopKey(
            rs.getObject("id", UUID.class), rs.getLong("shop_no"), rs.getString("name"),
            AdminRefs.apiShopStatus(rs.getString("status")), rs.getString("slug"), rs.getString("plan_code"));
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
        var normalized = withNormalizedPlan(filter);
        var safeSize = AdminRefs.size(size);
        var safePage = AdminRefs.page(page);
        var total = countKeys(normalized);
        var pageKeys = listKeysPage(normalized, sort, dir, safePage, safeSize);
        var map = usage.usage(pageKeys);
        return AdminRefs.page(pageKeys.stream().map(k -> map.get(k.id())).toList(), safePage, safeSize, total);
    }

    /**
     * Aggregates for usage summary cards. Validates {@code plan} then aggregates every shop matching q/status
     * (plan filter is not applied to summary totals — cards show the full breakdown).
     */
    @Transactional(readOnly = true)
    public List<PlanUsageSummary> usageSummaries(Filter filter) {
        var normalized = withNormalizedPlan(filter);
        var base = new Filter(normalized.q(), normalized.status(), normalized.merchantId(), normalized.ownerUserId(),
            normalized.category(), normalized.province(), normalized.suspendReasonCode(), normalized.from(), normalized.to(), null);
        var p = new MapSqlParameterSource();
        var whereSql = where(base, p);
        return usage.summarizeSql(whereSql, p);
    }

    @Transactional
    public ShopActionResponse suspend(Long shopId, ReasonRequest request, UUID actorId) {
        var shop = require(shopId);
        var code = SuspensionReasonCodes.resolveSuspend(request.reasonCode(), ShopSuspensionService.DEFAULT_SUSPEND_CODE);
        var changed = suspensions.suspendShop(shop, actorId, code, request.reason().trim(), true);
        return new ShopActionResponse(changed, detail(shop));
    }

    @Transactional
    public ShopActionResponse reactivate(Long shopId, ReasonRequest request, UUID actorId) {
        var shop = require(shopId);
        var code = SuspensionReasonCodes.resolveRestore(request.reasonCode(), ShopSuspensionService.DEFAULT_RESTORE_CODE);
        var changed = suspensions.reactivateShop(shop, actorId, code, request.reason().trim(), true);
        return new ShopActionResponse(changed, detail(shop));
    }

    @Transactional
    public ShopActionResponse assignPlan(Long shopId, String planCode, UUID actorId) {
        var shop = require(shopId);
        var next = usage.requirePlanOrClear(planCode);
        var before = shop.getPlanCode();
        var changed = !Objects.equals(before, next);
        if (changed) {
            shop.assignPlanCode(next);
            shops.save(shop);
            platformAudit.record(PlatformAuditService.Entry.platform(actorId, "SHOP_PLAN_CHANGED", "SHOP", shop.getUuid())
                .shop(shop.getUuid())
                .permission("PLATFORM_SHOP_SUSPEND")
                .change(Map.of("planCode", before == null ? "" : before), Map.of("planCode", next == null ? "" : next)));
        }
        return new ShopActionResponse(changed, detail(shop));
    }

    private Shop require(Long shopId) {
        return shops.findByShopId(shopId).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
    }

    private static AdminShopUsageService.ShopKey key(Shop shop) {
        return new AdminShopUsageService.ShopKey(shop.getUuid(), shop.getId(), shop.getName(),
            AdminRefs.apiShopStatus(shop.getStatus().name()), shop.getSlug(), shop.getPlanCode());
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
            AdminContactMask.email(account.getEmail()), AdminContactMask.phone(account.getPhone()), account.getStatus().name());
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
