package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminDirectoryDtos.*;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.UserRole;
import com.thaishopfun.auth.authz.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Admin user directory, read straight from users + platform_roles + shop_members + shops (+ audit_logs for history).
 * Role rules match AuthorizationService: an admin holds an un-revoked platform_roles row, a shop role is an ACTIVE
 * shop_members row (removed_at IS NULL), and every account implicitly holds the ACCOUNT/APPLICANT roles. Soft-deleted
 * accounts (users.deleted_at) are excluded everywhere.
 * <p>Queries are portable SQL (PostgreSQL and the H2 test database): one COUNT + one page query, then two batched
 * look-ups (IN list) for the roles and shops of the rows on that page, so the cost does not grow with the page size.
 */
@Service
public class AdminUserDirectoryService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_EXPORT_ROWS = 10_000;
    static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED");
    static final Map<String, String> STATUS_LABELS = Map.of("ACTIVE", "ใช้งาน", "SUSPENDED", "ระงับ");
    private static final Map<String, String> SORTS = Map.of(
        "createdAt", "u.created_date_time",
        "name", "u.first_name",
        "email", "u.email",
        "lastLoginAt", "u.last_login_at",
        "status", "u.status");

    private static final String ACTIVE_ADMIN = "EXISTS (SELECT 1 FROM platform_roles p WHERE p.user_id = u.id AND p.revoked_at IS NULL)";
    private static final String ACTIVE_MEMBER = "EXISTS (SELECT 1 FROM shop_members m WHERE m.user_id = u.id AND m.status = 'ACTIVE' AND m.removed_at IS NULL)";

    private final NamedParameterJdbcTemplate jdbc;
    private final AdminCatalog catalog;
    private final AuthorizationService authorization;

    public AdminUserDirectoryService(NamedParameterJdbcTemplate jdbc, AdminCatalog catalog, AuthorizationService authorization) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.authorization = authorization;
    }

    public record Filter(String q, String role, String status, LocalDate from, LocalDate to) {}

    // ------------------------------------------------------------------------------------------------ list

    @Transactional(readOnly = true)
    public UserPage list(Filter filter, String sort, String direction, int page, int size) {
        var roles = catalog.roles();
        var params = new MapSqlParameterSource();
        var where = where(filter, roles, params);
        var safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var safePage = Math.max(page, 0);
        var total = Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM users u WHERE " + where, params, Long.class)).orElse(0L);
        var rows = total == 0 ? List.<UserRow>of() : rows(where, params, roles, orderBy(sort, direction), safeSize, (long) safePage * safeSize);
        var pages = (int) ((total + safeSize - 1) / safeSize);
        return new UserPage(rows, safePage, safeSize, total, pages);
    }

    /** UTF-8 CSV (with BOM so Excel shows Thai) of every row matching the filter, newest first, capped at MAX_EXPORT_ROWS. */
    @Transactional(readOnly = true)
    public byte[] exportCsv(Filter filter, String sort, String direction) {
        var roles = catalog.roles();
        var params = new MapSqlParameterSource();
        var where = where(filter, roles, params);
        var rows = rows(where, params, roles, orderBy(sort, direction), MAX_EXPORT_ROWS, 0);
        var format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZONE);
        var csv = new StringBuilder("\uFEFF");
        csv.append(csvLine(List.of("ชื่อ", "นามสกุล", "อีเมล", "เบอร์โทร", "บทบาท", "บทบาททั้งหมด", "ร้านค้า", "สถานะ", "วันที่สมัคร", "เข้าสู่ระบบล่าสุด")));
        for (var row : rows) {
            csv.append(csvLine(List.of(
                row.firstName(), row.lastName(), row.email(), row.phone(),
                row.primaryRole() == null ? "" : nullToEmpty(row.primaryRole().nameTh()),
                String.join(" ", row.roleCodes()),
                String.join(", ", row.shops().stream().map(ShopRef::shopName).distinct().toList()),
                STATUS_LABELS.getOrDefault(row.status(), row.status()),
                row.createdAt() == null ? "" : format.format(row.createdAt()),
                row.lastLoginAt() == null ? "" : format.format(row.lastLoginAt()))));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String where(Filter filter, Map<String, AdminCatalog.RoleRow> roles, MapSqlParameterSource params) {
        var clauses = new ArrayList<String>();
        clauses.add("u.deleted_at IS NULL");
        var q = filter.q() == null ? "" : filter.q().trim();
        if (!q.isEmpty()) {
            if (q.length() > 100) throw invalid("คำค้นหายาวเกินไป");
            params.addValue("q", "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%");
            var text = new StringBuilder("(LOWER(u.first_name || ' ' || u.last_name) LIKE :q OR LOWER(u.email) LIKE :q OR LOWER(u.phone) LIKE :q")
                .append(" OR EXISTS (SELECT 1 FROM shop_members sm JOIN shops s ON s.id = sm.shop_id WHERE sm.user_id = u.id")
                .append(" AND sm.status = 'ACTIVE' AND sm.removed_at IS NULL AND (LOWER(s.name) LIKE :q OR LOWER(s.name_en) LIKE :q))");
            var digits = q.replaceAll("\\D", "");
            if (digits.length() >= 3) {
                params.addValue("qDigits", "%" + digits + "%");
                text.append(" OR REPLACE(REPLACE(u.phone, '-', ''), ' ', '') LIKE :qDigits");
            }
            clauses.add(text.append(")").toString());
        }
        var role = filter.role() == null ? "" : filter.role().trim();
        if (!role.isEmpty() && !"ALL".equalsIgnoreCase(role)) {
            if (AdminDirectoryDtos.GENERAL.equals(role)) {
                clauses.add("NOT " + ACTIVE_ADMIN + " AND NOT " + ACTIVE_MEMBER);
            } else {
                var row = roles.get(role);
                if (row == null || !(AdminCatalog.isPlatform(row) || AdminCatalog.isStore(row))) throw invalid("ไม่รู้จักบทบาท " + role);
                params.addValue("role", role);
                clauses.add(AdminCatalog.isPlatform(row)
                    ? "EXISTS (SELECT 1 FROM platform_roles p WHERE p.user_id = u.id AND p.revoked_at IS NULL AND p.role = :role)"
                    : "EXISTS (SELECT 1 FROM shop_members m WHERE m.user_id = u.id AND m.status = 'ACTIVE' AND m.removed_at IS NULL AND m.role = :role)");
            }
        }
        var status = filter.status() == null ? "" : filter.status().trim();
        if (!status.isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            if (!STATUSES.contains(status)) throw invalid("ไม่รู้จักสถานะ " + status);
            params.addValue("status", status);
            clauses.add("u.status = :status");
        }
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) throw invalid("วันที่เริ่มต้องไม่หลังวันที่สิ้นสุด");
        if (filter.from() != null) {
            params.addValue("from", Timestamp.from(filter.from().atStartOfDay(ZONE).toInstant()));
            clauses.add("u.created_date_time >= :from");
        }
        if (filter.to() != null) {
            params.addValue("toExclusive", Timestamp.from(filter.to().plusDays(1).atStartOfDay(ZONE).toInstant()));
            clauses.add("u.created_date_time < :toExclusive");
        }
        return String.join(" AND ", clauses);
    }

    private static String orderBy(String sort, String direction) {
        var column = SORTS.getOrDefault(sort == null ? "" : sort, SORTS.get("createdAt"));
        var dir = "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
        var nulls = column.equals("u.last_login_at") ? " NULLS LAST" : "";
        var tie = column.equals("u.first_name") ? ", u.last_name " + dir : "";
        return column + " " + dir + nulls + tie + ", u.id " + dir;
    }

    private List<UserRow> rows(String where, MapSqlParameterSource params, Map<String, AdminCatalog.RoleRow> roles, String orderBy, int limit, long offset) {
        params.addValue("limit", limit).addValue("offset", offset);
        var base = jdbc.query("""
            SELECT u.id, u.first_name, u.last_name, u.email, u.phone, u.status, u.created_date_time, u.last_login_at
              FROM users u WHERE %s ORDER BY %s LIMIT :limit OFFSET :offset
            """.formatted(where, orderBy), params, (rs, i) -> new BaseUser(
            rs.getObject("id", UUID.class), rs.getString("first_name"), rs.getString("last_name"), rs.getString("email"),
            rs.getString("phone"), rs.getString("status"), instant(rs, "created_date_time"), instant(rs, "last_login_at")));
        if (base.isEmpty()) return List.of();
        var grants = grantsOf(base.stream().map(BaseUser::id).toList(), roles);
        return base.stream().map(user -> {
            var grant = grants.getOrDefault(user.id(), Grant.EMPTY);
            return new UserRow(user.id(), user.firstName(), user.lastName(), user.email(), user.phone(), user.status(), accountKind(grant),
                primaryRole(grant, roles), roleCodes(grant, roles), grant.shops(), grant.platformRoles().contains(AuthorizationService.SUPER_ADMIN_ROLE),
                user.createdAt(), user.lastLoginAt());
        }).toList();
    }

    private record BaseUser(UUID id, String firstName, String lastName, String email, String phone, String status, Instant createdAt, Instant lastLoginAt) {}

    /** CUSTOMER / MERCHANT / ADMIN derived from the role tables (users.role was dropped in V18); same rule as /api/auth/me. */
    private static String accountKind(Grant grant) {
        if (grant == null) return UserRole.CUSTOMER.name();
        return UserRole.derive(!grant.platformRoles().isEmpty(), !grant.shops().isEmpty()).name();
    }

    private record Grant(Set<String> platformRoles, List<ShopRef> shops) {
        static final Grant EMPTY = new Grant(Set.of(), List.of());
    }

    /** Active platform roles and shop memberships of several users in two queries. */
    private Map<UUID, Grant> grantsOf(Collection<UUID> userIds, Map<String, AdminCatalog.RoleRow> roles) {
        var platform = new HashMap<UUID, Set<String>>();
        var shops = new HashMap<UUID, List<ShopRef>>();
        var params = new MapSqlParameterSource("ids", userIds);
        jdbc.query("SELECT user_id, role FROM platform_roles WHERE revoked_at IS NULL AND user_id IN (:ids)", params, rs -> {
            platform.computeIfAbsent(rs.getObject("user_id", UUID.class), k -> new TreeSet<>()).add(rs.getString("role"));
        });
        jdbc.query("""
            SELECT m.user_id, m.role, s.shop_no, s.name, s.status
              FROM shop_members m JOIN shops s ON s.id = m.shop_id
             WHERE m.status = 'ACTIVE' AND m.removed_at IS NULL AND m.user_id IN (:ids)
             ORDER BY s.shop_no
            """, params, rs -> {
            var role = rs.getString("role");
            var roleRow = roles.get(role);
            shops.computeIfAbsent(rs.getObject("user_id", UUID.class), k -> new ArrayList<>()).add(new ShopRef(
                rs.getLong("shop_no"), rs.getString("name"), shopStatus(rs.getString("status")), role, roleRow == null ? role : roleRow.nameTh()));
        });
        var result = new HashMap<UUID, Grant>();
        for (var id : userIds) result.put(id, new Grant(platform.getOrDefault(id, Set.of()), shops.getOrDefault(id, List.of())));
        return result;
    }

    /** Platform role first, then the shop role with the lowest auth_roles.sort_order (STORE_OWNER), else BUYER. */
    private static RoleRef primaryRole(Grant grant, Map<String, AdminCatalog.RoleRow> roles) {
        for (var code : grant.platformRoles()) {
            var row = roles.get(code);
            return row == null ? new RoleRef(code, code, "PLATFORM") : row.ref();
        }
        var best = grant.shops().stream().map(ShopRef::role).distinct()
            .min(Comparator.comparingInt(code -> roles.containsKey(code) ? roles.get(code).sortOrder() : Integer.MAX_VALUE));
        if (best.isPresent()) {
            var row = roles.get(best.get());
            return row == null ? new RoleRef(best.get(), best.get(), "STORE") : row.ref();
        }
        return roles.values().stream().filter(AdminCatalog::isImplicit).filter(row -> "ACCOUNT".equals(row.context())).findFirst()
            .map(AdminCatalog.RoleRow::ref).orElse(new RoleRef("BUYER", "ผู้ซื้อ", "ACCOUNT"));
    }

    private static List<String> roleCodes(Grant grant, Map<String, AdminCatalog.RoleRow> roles) {
        var codes = new LinkedHashSet<String>(grant.platformRoles());
        grant.shops().stream().map(ShopRef::role)
            .sorted(Comparator.comparingInt(code -> roles.containsKey(code) ? roles.get(code).sortOrder() : Integer.MAX_VALUE))
            .forEach(codes::add);
        return List.copyOf(codes);
    }

    // ------------------------------------------------------------------------------------------------ stats

    @Transactional(readOnly = true)
    public UserStats stats() {
        var now = Instant.now();
        var monthStart = ZonedDateTime.now(ZONE).withDayOfMonth(1).toLocalDate().atStartOfDay(ZONE).toInstant();
        var current = jdbc.queryForMap("""
            SELECT
              (SELECT COUNT(*) FROM users u WHERE u.deleted_at IS NULL) AS total,
              (SELECT COUNT(*) FROM users u WHERE u.deleted_at IS NULL AND %1$s) AS admins,
              (SELECT COUNT(*) FROM users u WHERE u.deleted_at IS NULL AND EXISTS (SELECT 1 FROM shop_members m
                  WHERE m.user_id = u.id AND m.role = 'STORE_OWNER' AND m.status = 'ACTIVE' AND m.removed_at IS NULL)) AS owners,
              (SELECT COUNT(*) FROM users u WHERE u.deleted_at IS NULL AND NOT %1$s AND NOT %2$s) AS general_users,
              (SELECT COUNT(*) FROM users u WHERE u.deleted_at IS NULL AND u.status = 'ACTIVE') AS active_users,
              (SELECT COUNT(*) FROM users u WHERE u.deleted_at IS NULL AND u.status = 'SUSPENDED') AS suspended_users,
              (SELECT COUNT(*) FROM auth_roles) AS role_count
            """.formatted(ACTIVE_ADMIN, ACTIVE_MEMBER), new MapSqlParameterSource());
        // the same numbers as they stood at the start of this month (Asia/Bangkok), from the grant/join/remove timestamps
        var alive = "u.created_date_time < :t AND (u.deleted_at IS NULL OR u.deleted_at >= :t)";
        var adminAt = "EXISTS (SELECT 1 FROM platform_roles p WHERE p.user_id = u.id AND p.granted_at < :t AND (p.revoked_at IS NULL OR p.revoked_at >= :t))";
        var memberAt = "EXISTS (SELECT 1 FROM shop_members m WHERE m.user_id = u.id AND m.joined_at < :t AND (m.removed_at IS NULL OR m.removed_at >= :t) AND (m.status = 'ACTIVE' OR m.removed_at IS NOT NULL)%s)";
        var previous = jdbc.queryForMap("""
            SELECT
              (SELECT COUNT(*) FROM users u WHERE %1$s) AS total,
              (SELECT COUNT(*) FROM users u WHERE %1$s AND %2$s) AS admins,
              (SELECT COUNT(*) FROM users u WHERE %1$s AND %3$s) AS owners,
              (SELECT COUNT(*) FROM users u WHERE %1$s AND NOT %2$s AND NOT %4$s) AS general_users
            """.formatted(alive, adminAt, memberAt.formatted(" AND m.role = 'STORE_OWNER'"), memberAt.formatted("")),
            new MapSqlParameterSource("t", Timestamp.from(monthStart)));
        return new UserStats(
            metric(current, previous, "total"), metric(current, previous, "admins"),
            metric(current, previous, "owners"), metric(current, previous, "general_users"),
            number(current, "active_users"), number(current, "suspended_users"), (int) number(current, "role_count"),
            monthStart, now);
    }

    @Transactional(readOnly = true)
    public UserFilterOptions filterOptions() {
        var roles = catalog.roles();
        var counts = roleHolderCounts();
        var options = new ArrayList<FilterOption>();
        for (var role : roles.values()) {
            if (AdminCatalog.isPlatform(role) || AdminCatalog.isStore(role)) {
                options.add(new FilterOption(role.code(), role.nameTh() == null ? role.code() : role.nameTh(), counts.getOrDefault(role.code(), 0L)));
            }
        }
        var general = Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM users u WHERE u.deleted_at IS NULL AND NOT " + ACTIVE_ADMIN + " AND NOT " + ACTIVE_MEMBER,
            new MapSqlParameterSource(), Long.class)).orElse(0L);
        var buyer = roles.values().stream().filter(role -> "ACCOUNT".equals(role.context())).findFirst().map(AdminCatalog.RoleRow::nameTh).orElse("ผู้ซื้อ");
        options.add(new FilterOption(AdminDirectoryDtos.GENERAL, "ผู้ใช้งานทั่วไป (" + buyer + ")", general));
        var statuses = new ArrayList<FilterOption>();
        jdbc.query("SELECT u.status, COUNT(*) AS n FROM users u WHERE u.deleted_at IS NULL GROUP BY u.status", new MapSqlParameterSource(), rs -> {
            statuses.add(new FilterOption(rs.getString("status"), STATUS_LABELS.getOrDefault(rs.getString("status"), rs.getString("status")), rs.getLong("n")));
        });
        for (var status : List.of("ACTIVE", "SUSPENDED")) {
            if (statuses.stream().noneMatch(option -> option.value().equals(status))) statuses.add(new FilterOption(status, STATUS_LABELS.get(status), 0));
        }
        statuses.sort(Comparator.comparing(option -> option.value().equals("ACTIVE") ? 0 : 1));
        return new UserFilterOptions(options, statuses);
    }

    /** Distinct non-deleted holders per explicitly granted role (platform_roles + active shop_members). */
    Map<String, Long> roleHolderCounts() {
        var counts = new HashMap<String, Long>();
        var none = new MapSqlParameterSource();
        jdbc.query("""
            SELECT p.role, COUNT(DISTINCT p.user_id) AS n FROM platform_roles p JOIN users u ON u.id = p.user_id
             WHERE p.revoked_at IS NULL AND u.deleted_at IS NULL GROUP BY p.role
            """, none, rs -> { counts.merge(rs.getString("role"), rs.getLong("n"), Long::sum); });
        jdbc.query("""
            SELECT m.role, COUNT(DISTINCT m.user_id) AS n FROM shop_members m JOIN users u ON u.id = m.user_id
             WHERE m.status = 'ACTIVE' AND m.removed_at IS NULL AND u.deleted_at IS NULL GROUP BY m.role
            """, none, rs -> { counts.merge(rs.getString("role"), rs.getLong("n"), Long::sum); });
        return counts;
    }

    // ------------------------------------------------------------------------------------------------ detail

    @Transactional(readOnly = true)
    public UserDetail detail(UUID userId) {
        var users = jdbc.query("""
            SELECT id, first_name, last_name, email, phone, status, created_date_time, last_login_at, email_verified_at, phone_verified_at
              FROM users WHERE id = :id AND deleted_at IS NULL
            """, new MapSqlParameterSource("id", userId), (rs, i) -> new Object[]{
            rs.getString("first_name"), rs.getString("last_name"), rs.getString("email"), rs.getString("phone"), rs.getString("status"),
            null, instant(rs, "created_date_time"), instant(rs, "last_login_at"), instant(rs, "email_verified_at"), instant(rs, "phone_verified_at")});
        if (users.isEmpty()) throw new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ไม่พบผู้ใช้งาน");
        var u = users.get(0);
        var roles = catalog.roles();
        var permissions = catalog.permissions();
        var effective = authorization.effectivePermissions(userId);
        var grant = grantsOf(List.of(userId), roles).get(userId);

        var joined = new HashMap<Long, Instant>();
        jdbc.query("""
            SELECT s.shop_no, m.joined_at FROM shop_members m JOIN shops s ON s.id = m.shop_id
             WHERE m.user_id = :id AND m.status = 'ACTIVE' AND m.removed_at IS NULL
            """, new MapSqlParameterSource("id", userId), rs -> { joined.put(rs.getLong("shop_no"), instant(rs, "joined_at")); });
        var byShop = new HashMap<Long, List<String>>();
        for (var shop : effective.shops()) byShop.put(shop.shopId(), shop.permissions());
        var shops = grant.shops().stream().map(shop -> new ShopAccess(shop.shopId(), shop.shopName(), shop.shopStatus(), shop.role(), shop.roleNameTh(),
            joined.get(shop.shopId()), describe(byShop.getOrDefault(shop.shopId(), List.of()), permissions))).toList();

        var roleRefs = effective.roles().stream()
            .map(code -> roles.containsKey(code) ? roles.get(code).ref() : new RoleRef(code, code, null))
            .sorted(Comparator.comparingInt(ref -> roles.containsKey(ref.code()) ? roles.get(ref.code()).sortOrder() : Integer.MAX_VALUE))
            .toList();
        var shopRoleRefs = grant.shops().stream().map(ShopRef::role).distinct()
            .map(code -> roles.containsKey(code) ? roles.get(code).ref() : new RoleRef(code, code, "STORE")).toList();
        var allRoles = new ArrayList<RoleRef>(roleRefs);
        shopRoleRefs.forEach(ref -> { if (allRoles.stream().noneMatch(r -> r.code().equals(ref.code()))) allRoles.add(ref); });
        allRoles.sort(Comparator.comparingInt(ref -> roles.containsKey(ref.code()) ? -roles.get(ref.code()).sortOrder() : 0));

        return new UserDetail(userId, (String) u[0], (String) u[1], (String) u[2], (String) u[3], (String) u[4], accountKind(grant),
            effective.superAdmin(), (Instant) u[6], (Instant) u[7], (Instant) u[8], (Instant) u[9],
            primaryRole(grant, roles), List.copyOf(allRoles), describe(effective.permissions(), permissions), shops, counts(userId));
    }

    private UserCounts counts(UUID userId) {
        var p = new MapSqlParameterSource("id", userId).addValue("now", java.sql.Timestamp.from(Instant.now()));
        return jdbc.queryForObject("""
            SELECT (SELECT COUNT(*) FROM shops s WHERE s.owner_user_id = :id) AS owned,
                   (SELECT COUNT(*) FROM shop_members m WHERE m.user_id = :id AND m.status = 'ACTIVE' AND m.removed_at IS NULL) AS memberships,
                   (SELECT COUNT(*) FROM owners o WHERE o.user_id = :id) AS merchants,
                   (SELECT COUNT(*) FROM orders o WHERE o.buyer_user_id = :id) AS orders,
                   (SELECT COUNT(*) FROM refresh_tokens t WHERE t.user_id = :id AND t.revoked_at IS NULL AND t.expires_at > :now) AS sessions
            """, p, (rs, i) -> new UserCounts(rs.getLong("owned"), rs.getLong("memberships"), rs.getLong("merchants"),
                rs.getLong("orders"), rs.getLong("sessions")));
    }

    private static List<PermissionInfo> describe(Collection<String> codes, Map<String, PermissionInfo> catalog) {
        return codes.stream().map(code -> catalog.getOrDefault(code, new PermissionInfo(code, null, null, null, true)))
            .sorted(Comparator.comparing((PermissionInfo p) -> p.module() == null ? "~" : p.module()).thenComparing(PermissionInfo::code))
            .toList();
    }

    // ------------------------------------------------------------------------------------------------ history

    /** Account timeline: sign-up / verification / last login, role grants, shop memberships, applications and audit_logs. */
    @Transactional(readOnly = true)
    public List<ActivityEvent> history(UUID userId, int limit) {
        var max = Math.min(Math.max(limit, 1), 200);
        var params = new MapSqlParameterSource("id", userId).addValue("limit", max);
        var users = jdbc.query("""
            SELECT first_name, last_name, created_date_time, last_login_at, email_verified_at, phone_verified_at FROM users WHERE id = :id AND deleted_at IS NULL
            """, params, (rs, i) -> new Object[]{fullName(rs.getString("first_name"), rs.getString("last_name")),
            instant(rs, "created_date_time"), instant(rs, "last_login_at"), instant(rs, "email_verified_at"), instant(rs, "phone_verified_at")});
        if (users.isEmpty()) throw new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ไม่พบผู้ใช้งาน");
        var u = users.get(0);
        var name = (String) u[0];
        var roles = catalog.roles();
        var events = new ArrayList<ActivityEvent>();
        add(events, (Instant) u[1], "ACCOUNT_CREATED", "สมัครสมาชิก", name, null, null, null, null, "users");
        add(events, (Instant) u[3], "EMAIL_VERIFIED", "ยืนยันอีเมล", name, null, null, null, null, "users");
        add(events, (Instant) u[4], "PHONE_VERIFIED", "ยืนยันเบอร์โทร", name, null, null, null, null, "users");
        add(events, (Instant) u[2], "LAST_LOGIN", "เข้าสู่ระบบล่าสุด", name, null, null, null, null, "users");
        events.addAll(platformRoleEvents("p.user_id = :id", params, roles));
        events.addAll(membershipEvents("m.user_id = :id", params, roles));
        jdbc.query("""
            SELECT a.submitted_at, a.shop_name, a.status FROM merchant_applications a
             WHERE a.user_id = :id AND a.submitted_at IS NOT NULL ORDER BY a.submitted_at DESC LIMIT :limit
            """, params, rs -> {
            add(events, instant(rs, "submitted_at"), "APPLICATION_SUBMITTED", "ส่งใบสมัครเปิดร้าน (สถานะปัจจุบัน " + rs.getString("status") + ")",
                name, null, null, null, rs.getString("shop_name"), "merchant_applications");
        });
        events.addAll(auditEvents("(a.actor_id = :id OR a.resource_id = :id)", params));
        return newestFirst(events, max);
    }

    // ------------------------------------------------------------------------------------------------ shared feeds

    List<ActivityEvent> platformRoleEvents(String condition, MapSqlParameterSource params, Map<String, AdminCatalog.RoleRow> roles) {
        var events = new ArrayList<ActivityEvent>();
        jdbc.query("""
            SELECT p.role, p.granted_at, p.revoked_at, su.first_name AS s_first, su.last_name AS s_last,
                   gu.first_name AS g_first, gu.last_name AS g_last, ru.first_name AS r_first, ru.last_name AS r_last
              FROM platform_roles p
              JOIN users su ON su.id = p.user_id
              LEFT JOIN users gu ON gu.id = p.granted_by
              LEFT JOIN users ru ON ru.id = p.revoked_by
             WHERE %s
             ORDER BY p.granted_at DESC LIMIT :limit
            """.formatted(condition), params, rs -> {
            var code = rs.getString("role");
            var label = roles.containsKey(code) ? roles.get(code).nameTh() : code;
            var subject = fullName(rs.getString("s_first"), rs.getString("s_last"));
            add(events, instant(rs, "granted_at"), "PLATFORM_ROLE_GRANTED", "ได้รับบทบาท “" + label + "”", subject,
                fullName(rs.getString("g_first"), rs.getString("g_last")), code, label, null, "platform_roles");
            add(events, instant(rs, "revoked_at"), "PLATFORM_ROLE_REVOKED", "ถูกเพิกถอนบทบาท “" + label + "”", subject,
                fullName(rs.getString("r_first"), rs.getString("r_last")), code, label, null, "platform_roles");
        });
        return events;
    }

    List<ActivityEvent> membershipEvents(String condition, MapSqlParameterSource params, Map<String, AdminCatalog.RoleRow> roles) {
        var events = new ArrayList<ActivityEvent>();
        jdbc.query("""
            SELECT m.role, m.joined_at, m.removed_at, s.name AS shop_name, u.first_name, u.last_name
              FROM shop_members m
              JOIN shops s ON s.id = m.shop_id
              JOIN users u ON u.id = m.user_id
             WHERE %s
             ORDER BY m.joined_at DESC LIMIT :limit
            """.formatted(condition), params, rs -> {
            var code = rs.getString("role");
            var label = roles.containsKey(code) ? roles.get(code).nameTh() : code;
            var subject = fullName(rs.getString("first_name"), rs.getString("last_name"));
            var shop = rs.getString("shop_name");
            add(events, instant(rs, "joined_at"), "SHOP_ROLE_ADDED", "เพิ่มในบทบาท “" + label + "” ของร้าน " + shop, subject, null, code, label, shop, "shop_members");
            add(events, instant(rs, "removed_at"), "SHOP_ROLE_REMOVED", "ถูกนำออกจากบทบาท “" + label + "” ของร้าน " + shop, subject, null, code, label, shop, "shop_members");
        });
        return events;
    }

    List<ActivityEvent> auditEvents(String condition, MapSqlParameterSource params) {
        var events = new ArrayList<ActivityEvent>();
        jdbc.query("""
            SELECT a.occurred_at, a.action, a.resource_type, a.outcome, a.reason_code, u.first_name, u.last_name
              FROM audit_logs a LEFT JOIN users u ON u.id = a.actor_id
             WHERE %s
             ORDER BY a.occurred_at DESC LIMIT :limit
            """.formatted(condition), params, rs -> {
            var actor = fullName(rs.getString("first_name"), rs.getString("last_name"));
            var title = rs.getString("action") + " · " + rs.getString("resource_type")
                + ("SUCCESS".equals(rs.getString("outcome")) ? "" : " (" + rs.getString("outcome") + ")");
            add(events, instant(rs, "occurred_at"), "AUDIT_" + rs.getString("action"), title, actor, actor, null, null, null, "audit_logs");
        });
        return events;
    }

    static List<ActivityEvent> newestFirst(List<ActivityEvent> events, int limit) {
        return events.stream().sorted(Comparator.comparing(ActivityEvent::at).reversed()).limit(limit).toList();
    }

    private static void add(List<ActivityEvent> events, Instant at, String type, String title, String subject, String actor,
                            String roleCode, String roleName, String shop, String source) {
        if (at != null) events.add(new ActivityEvent(at, type, title, subject, actor, roleCode, roleName, shop, source));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static Metric metric(Map<String, Object> current, Map<String, Object> previous, String key) {
        var now = number(current, key);
        var before = number(previous, key);
        Double change = before == 0 ? null
            : BigDecimal.valueOf((now - before) * 100.0 / before).setScale(1, RoundingMode.HALF_UP).doubleValue();
        return new Metric(now, before, change);
    }

    private static long number(Map<String, Object> row, String key) {
        var value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase(Locale.ROOT));
        return value == null ? 0 : ((Number) value).longValue();
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static String fullName(String first, String last) {
        var name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return name.isEmpty() ? null : name;
    }

    private static String shopStatus(String stored) { return "ACTIVE".equals(stored) ? "APPROVED" : stored; }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    private static String csvLine(List<String> values) {
        var line = new StringJoiner(",", "", "\r\n");
        for (var raw : values) {
            var value = raw == null ? "" : raw;
            if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) value = "'" + value;
            line.add("\"" + value.replace("\"", "\"\"") + "\"");
        }
        return line.toString();
    }

    private static AuthException invalid(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", message);
    }
}
