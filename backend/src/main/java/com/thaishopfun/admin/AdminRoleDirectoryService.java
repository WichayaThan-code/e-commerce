package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminDirectoryDtos.*;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Role catalog (auth_roles) with real holder counts and the permission mapping (auth_role_permissions x auth_permissions).
 * <ul>
 *   <li>PLATFORM roles are counted from un-revoked platform_roles rows, STORE roles from ACTIVE shop_members rows;</li>
 *   <li>ACCOUNT / APPLICANT roles (BUYER, APPLICANT) are implicit for every active account, so their count is the
 *       number of ACTIVE users;</li>
 *   <li>PUBLIC roles (GUEST) belong to anonymous visitors: no account holds them, the count is null.</li>
 * </ul>
 * auth_roles has no status column; a role is reported active when at least one active permission is mapped to it
 * (PLATFORM_ADMIN is always active: it is the super admin and holds every permission in code).
 */
@Service
public class AdminRoleDirectoryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AdminCatalog catalog;
    private final AdminUserDirectoryService users;

    public AdminRoleDirectoryService(NamedParameterJdbcTemplate jdbc, AdminCatalog catalog, AdminUserDirectoryService users) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<RoleSummary> list() {
        var roles = catalog.roles();
        var holders = users.roleHolderCounts();
        var mapped = mappedPermissionCounts();
        var activeUsers = Optional.ofNullable(jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND status = 'ACTIVE'", new MapSqlParameterSource(), Long.class)).orElse(0L);
        return roles.values().stream().map(role -> summary(role, holders, mapped, activeUsers)).toList();
    }

    @Transactional(readOnly = true)
    public RoleDetail detail(String code) {
        var roles = catalog.roles();
        var role = roles.get(code);
        if (role == null) throw new AuthException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "ไม่พบบทบาท " + code);
        var activeUsers = Optional.ofNullable(jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND status = 'ACTIVE'", new MapSqlParameterSource(), Long.class)).orElse(0L);
        var summary = summary(role, users.roleHolderCounts(), mappedPermissionCounts(), activeUsers);
        var groups = new LinkedHashMap<String, List<PermissionInfo>>();
        jdbc.query("""
            SELECT p.code, p.module, p.scope, p.description_th, p.is_active
              FROM auth_role_permissions rp
              JOIN auth_roles r ON r.id = rp.role_id
              JOIN auth_permissions p ON p.id = rp.permission_id
             WHERE r.code = :code
             ORDER BY p.module, p.code
            """, new MapSqlParameterSource("code", code), rs -> {
            groups.computeIfAbsent(rs.getString("module"), k -> new ArrayList<>()).add(new PermissionInfo(
                rs.getString("code"), rs.getString("module"), rs.getString("scope"), rs.getString("description_th"), rs.getBoolean("is_active")));
        });
        var catalogSize = Optional.ofNullable(jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth_permissions WHERE is_active = TRUE", new MapSqlParameterSource(), Integer.class)).orElse(0);
        return new RoleDetail(summary, groups.entrySet().stream().map(e -> new PermissionGroup(e.getKey(), List.copyOf(e.getValue()))).toList(), catalogSize);
    }

    /** Recent role activity: platform role grants/revocations, shop role assignments/removals and role-related audit_logs. */
    @Transactional(readOnly = true)
    public List<ActivityEvent> activity(int limit) {
        var max = Math.min(Math.max(limit, 1), 100);
        var roles = catalog.roles();
        var params = new MapSqlParameterSource("limit", max);
        var events = new ArrayList<ActivityEvent>();
        events.addAll(users.platformRoleEvents("1 = 1", params, roles));
        events.addAll(users.membershipEvents("1 = 1", params, roles));
        events.addAll(users.auditEvents("""
            (UPPER(a.action) LIKE '%ROLE%' OR UPPER(a.action) LIKE '%PERMISSION%' OR UPPER(a.action) LIKE '%MEMBER%'
             OR a.resource_type IN ('ROLE', 'AUTH_ROLE', 'AUTH_ROLE_PERMISSION', 'AUTH_PERMISSION', 'PLATFORM_ROLE', 'SHOP_MEMBER'))
            """, params));
        return AdminUserDirectoryService.newestFirst(events, max);
    }

    private Map<String, Integer> mappedPermissionCounts() {
        var counts = new HashMap<String, Integer>();
        jdbc.query("""
            SELECT r.code, COUNT(p.id) AS n
              FROM auth_role_permissions rp
              JOIN auth_roles r ON r.id = rp.role_id
              JOIN auth_permissions p ON p.id = rp.permission_id AND p.is_active = TRUE
             GROUP BY r.code
            """, new MapSqlParameterSource(), rs -> { counts.put(rs.getString("code"), rs.getInt("n")); });
        return counts;
    }

    private static RoleSummary summary(AdminCatalog.RoleRow role, Map<String, Long> holders, Map<String, Integer> mapped, long activeUsers) {
        String assignment;
        Long count;
        if (AdminCatalog.isPlatform(role)) { assignment = "PLATFORM_ROLES"; count = holders.getOrDefault(role.code(), 0L); }
        else if (AdminCatalog.isStore(role)) { assignment = "SHOP_MEMBERS"; count = holders.getOrDefault(role.code(), 0L); }
        else if (AdminCatalog.isImplicit(role)) { assignment = "IMPLICIT"; count = activeUsers; }
        else { assignment = "PUBLIC"; count = null; }
        var superAdmin = AuthorizationService.SUPER_ADMIN_ROLE.equals(role.code());
        var permissionCount = mapped.getOrDefault(role.code(), 0);
        return new RoleSummary(role.code(), role.nameTh(), role.descriptionTh(), role.context(), role.assignable(), role.sortOrder(),
            role.policyVersion(), assignment, count, permissionCount, superAdmin || permissionCount > 0, superAdmin);
    }
}
