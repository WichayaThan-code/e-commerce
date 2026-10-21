package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminPlatformDtos.*;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.Owner;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.ShopSuspensionService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Platform admin view of merchants = KYC owners (V2 {@code owners}, one per applicant / citizen id; a merchant owns
 * one or more shops through shops.owner_id) and merchant suspend / reactivate (V31 owners.status). Citizen id and
 * bank account are never returned.
 */
@Service
public class AdminMerchantService {
    static final Set<String> STATUSES = Set.of(Owner.STATUS_ACTIVE, Owner.STATUS_SUSPENDED);
    private static final Map<String, String> SORTS = Map.of(
        "createdAt", "o.created_date_time", "name", "LOWER(o.first_name)", "status", "o.status", "shops", "shop_count");

    private final NamedParameterJdbcTemplate jdbc;
    private final OwnerRepository owners;
    private final ShopRepository shops;
    private final ShopSuspensionService suspensions;
    private final AdminRefs refs;

    public AdminMerchantService(NamedParameterJdbcTemplate jdbc, OwnerRepository owners, ShopRepository shops,
                                ShopSuspensionService suspensions, AdminRefs refs) {
        this.jdbc = jdbc;
        this.owners = owners;
        this.shops = shops;
        this.suspensions = suspensions;
        this.refs = refs;
    }

    public record Filter(String q, String status, String ownerType, String shopStatus, UUID userId, LocalDate from, LocalDate to) {}

    @Transactional(readOnly = true)
    public Page<MerchantRow> list(Filter filter, String sort, String dir, int page, int size) {
        var safeSize = AdminRefs.size(size);
        var safePage = AdminRefs.page(page);
        var p = new MapSqlParameterSource();
        var where = where(filter, p);
        var total = Optional.ofNullable(jdbc.queryForObject(
            "SELECT COUNT(*) FROM owners o JOIN users u ON u.id = o.user_id WHERE " + where, p, Long.class)).orElse(0L);
        var order = SORTS.getOrDefault(sort == null ? "createdAt" : sort, "o.created_date_time") + " " + AdminRefs.direction(dir) + ", o.id";
        p.addValue("limit", safeSize).addValue("offset", (long) safePage * safeSize);
        var rows = jdbc.query("""
            SELECT o.id, o.owner_type, o.title, o.first_name AS o_first, o.last_name AS o_last, o.email AS o_email, o.phone AS o_phone,
                   o.province, o.status, o.created_date_time, o.suspended_at,
                   u.id AS user_id, u.first_name, u.last_name, u.email, u.phone, u.status AS user_status,
                   (SELECT COUNT(*) FROM shops s WHERE s.owner_id = o.id AND s.deleted_at IS NULL) AS shop_count,
                   (SELECT COUNT(*) FROM shops s WHERE s.owner_id = o.id AND s.deleted_at IS NULL AND s.status = 'ACTIVE') AS approved,
                   (SELECT COUNT(*) FROM shops s WHERE s.owner_id = o.id AND s.deleted_at IS NULL AND s.status = 'SUSPENDED') AS suspended,
                   (SELECT COUNT(*) FROM shops s WHERE s.owner_id = o.id AND s.deleted_at IS NULL AND s.status = 'PENDING_REVIEW') AS pending
              FROM owners o JOIN users u ON u.id = o.user_id
             WHERE %s ORDER BY %s LIMIT :limit OFFSET :offset
            """.formatted(where, order), p, (rs, i) -> new MerchantRow(
            rs.getObject("id", UUID.class), rs.getString("owner_type"),
            AdminShopService.merchantName(rs.getString("title"), rs.getString("o_first"), rs.getString("o_last")),
            rs.getString("o_email"), rs.getString("o_phone"), rs.getString("province"), rs.getString("status"),
            new UserRef(rs.getObject("user_id", UUID.class), rs.getString("first_name"), rs.getString("last_name"),
                rs.getString("email"), rs.getString("phone"), rs.getString("user_status")),
            rs.getLong("shop_count"), rs.getLong("approved"), rs.getLong("suspended"), rs.getLong("pending"),
            AdminRefs.instant(rs, "created_date_time"), AdminRefs.instant(rs, "suspended_at")));
        return AdminRefs.page(rows, safePage, safeSize, total);
    }

    private static String where(Filter f, MapSqlParameterSource p) {
        var sql = new StringBuilder("1 = 1");
        if (f.q() != null && !f.q().isBlank()) {
            p.addValue("q", AdminRefs.like(f.q()));
            sql.append(" AND (LOWER(o.first_name) LIKE :q OR LOWER(o.last_name) LIKE :q OR LOWER(o.email) LIKE :q OR o.phone LIKE :q")
                .append(" OR LOWER(u.email) LIKE :q OR EXISTS (SELECT 1 FROM shops s WHERE s.owner_id = o.id AND LOWER(s.name) LIKE :q))");
        }
        if (f.status() != null && !f.status().isBlank()) {
            var status = f.status().trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) throw AdminRefs.invalid("status", "สถานะผู้ขายต้องเป็น ACTIVE หรือ SUSPENDED");
            p.addValue("status", status);
            sql.append(" AND o.status = :status");
        }
        if (f.ownerType() != null && !f.ownerType().isBlank()) {
            p.addValue("ownerType", f.ownerType().trim().toUpperCase(Locale.ROOT));
            sql.append(" AND o.owner_type = :ownerType");
        }
        if (f.shopStatus() != null && !f.shopStatus().isBlank()) {
            p.addValue("shopStatuses", AdminRefs.storedShopStatuses(f.shopStatus()));
            sql.append(" AND EXISTS (SELECT 1 FROM shops s WHERE s.owner_id = o.id AND s.status IN (:shopStatuses))");
        }
        if (f.userId() != null) { p.addValue("userId", f.userId()); sql.append(" AND o.user_id = :userId"); }
        if (f.from() != null) { p.addValue("from", AdminRefs.startOf(f.from())); sql.append(" AND o.created_date_time >= :from"); }
        if (f.to() != null) { p.addValue("to", AdminRefs.endOf(f.to())); sql.append(" AND o.created_date_time < :to"); }
        return sql.toString();
    }

    @Transactional(readOnly = true)
    public MerchantDetail detail(UUID merchantId) {
        return detail(require(merchantId));
    }

    @Transactional
    public MerchantActionResponse suspend(UUID merchantId, ReasonRequest request, UUID actorId) {
        var owner = require(merchantId);
        var affected = suspensions.suspendMerchant(owner, actorId, AdminShopService.code(request, ShopSuspensionService.DEFAULT_SUSPEND_CODE), request.reason().trim());
        return new MerchantActionResponse(affected != null, detail(owner), affected == null ? List.of() : affected);
    }

    @Transactional
    public MerchantActionResponse reactivate(UUID merchantId, ReasonRequest request, UUID actorId) {
        var owner = require(merchantId);
        var affected = suspensions.reactivateMerchant(owner, actorId, AdminShopService.code(request, ShopSuspensionService.DEFAULT_RESTORE_CODE), request.reason().trim());
        return new MerchantActionResponse(affected != null, detail(owner), affected == null ? List.of() : affected);
    }

    private Owner require(UUID merchantId) {
        return owners.findById(merchantId).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND", "ไม่พบผู้ขายนี้"));
    }

    private MerchantDetail detail(Owner owner) {
        var ownShops = shops.findByOwner_IdOrderByShopIdAsc(owner.getId());
        var refsList = ownShops.stream().map(s -> new MerchantShopRef(s.getId(), s.getName(), s.getSlug(), s.getStatus().name(),
            s.getStatus() == com.thaishopfun.merchant.ShopStatus.SUSPENDED ? s.getSuspendReasonCode() : null, s.getCreatedAt())).toList();
        var ids = ownShops.stream().map(com.thaishopfun.merchant.Shop::getUuid).toList();
        long products = 0, orders = 0, staff = 0;
        if (!ids.isEmpty()) {
            var p = new MapSqlParameterSource("ids", ids);
            products = count("SELECT COUNT(*) FROM products WHERE shop_id IN (:ids) AND deleted_at IS NULL", p);
            orders = count("SELECT COUNT(*) FROM orders WHERE shop_id IN (:ids)", p);
            staff = count("SELECT COUNT(DISTINCT user_id) FROM shop_members WHERE shop_id IN (:ids) AND status = 'ACTIVE' AND removed_at IS NULL", p);
        }
        var approved = ownShops.stream().filter(s -> s.getStatus() == com.thaishopfun.merchant.ShopStatus.APPROVED).count();
        var suspended = ownShops.stream().filter(s -> s.getStatus() == com.thaishopfun.merchant.ShopStatus.SUSPENDED).count();
        var names = refs.users(Arrays.asList(owner.getSuspendedBy(), owner.getRestoredBy(), owner.getUser().getId()));
        var suspension = new Suspension(owner.getStatus(), owner.getSuspendedAt(), owner.getSuspendedBy(),
            AdminRefs.name(names.get(owner.getSuspendedBy())), owner.getSuspendReasonCode(), owner.getSuspendReason(),
            owner.getRestoredAt(), owner.getRestoredBy(), AdminRefs.name(names.get(owner.getRestoredBy())));
        return new MerchantDetail(owner.getId(), owner.getOwnerType() == null ? null : owner.getOwnerType().name(), owner.getTitle(),
            owner.getFirstName(), owner.getLastName(), owner.getEmail(), owner.getPhone(), owner.getProvince(), owner.getDistrict(),
            owner.getSensitiveReview(), owner.getStatus(), suspension, names.get(owner.getUser().getId()), refsList,
            new MerchantCounts(ownShops.size(), approved, suspended, products, orders, staff), owner.getCreatedAt(), owner.getUpdatedAt());
    }

    private long count(String sql, MapSqlParameterSource p) {
        return Optional.ofNullable(jdbc.queryForObject(sql, p, Long.class)).orElse(0L);
    }
}
