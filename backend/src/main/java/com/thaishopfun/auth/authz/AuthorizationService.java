package com.thaishopfun.auth.authz;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Central permission decisions. Role assignments are read live from the database (one indexed query per request,
 * memoized for the rest of that request); the role to permission mapping comes from {@link PermissionMatrix}.
 * <ul>
 *   <li>account / platform level: implicit account-context roles (BUYER, APPLICANT) + active platform_roles rows</li>
 *   <li>shop level: the role of the caller's ACTIVE shop_members row in that shop</li>
 * </ul>
 * Because nothing about a user's roles is cached across requests, changing shop_members / platform_roles (or
 * suspending the account, which the JWT "pv" validator rejects) takes effect on the very next request, even for
 * access tokens issued earlier.
 * <p>
 * <b>Super admin:</b> an active {@code platform_roles} row with role {@link #SUPER_ADMIN_ROLE} (PLATFORM_ADMIN) holds
 * every permission: all account/platform codes and all store codes in every existing shop, independent of the
 * explicit rows in auth_role_permissions. This is decided here rather than by mapping rows, so it also covers codes
 * added later and survives re-running the generated seed (which rewrites auth_role_permissions). Everyone else stays
 * deny-by-default.
 */
@Service
public class AuthorizationService {
    private static final String REQUEST_CACHE_PREFIX = AuthorizationService.class.getName() + ".grants.";
    /** Platform role that is granted every permission (see class comment). */
    public static final String SUPER_ADMIN_ROLE = PlatformRole.PLATFORM_ADMIN;

    private final JdbcTemplate jdbc;
    private final PermissionMatrix matrix;

    public AuthorizationService(JdbcTemplate jdbc, PermissionMatrix matrix) {
        this.jdbc = jdbc;
        this.matrix = matrix;
    }

    /** Role assignments of one user at the time of the request. shopRoles: shops.shop_no to shop_members.role. */
    public record Grants(UUID userId, Set<String> platformRoles, NavigableMap<Long, String> shopRoles) {
        public boolean superAdmin() { return platformRoles.contains(SUPER_ADMIN_ROLE); }
    }

    public enum ShopDecision { GRANTED, DENIED, NOT_MEMBER }

    public record ShopPermissions(Long shopId, String role, List<String> permissions) {}

    /** What the current user may do, as exposed to the frontend through /api/auth/me. */
    public record EffectivePermissions(List<String> roles, List<String> permissions, List<ShopPermissions> shops, boolean superAdmin) {}

    /** True when the user holds an active PLATFORM_ADMIN platform role (all permissions everywhere). */
    public boolean isSuperAdmin(UUID userId) {
        return grants(userId).superAdmin();
    }

    /** Account/platform-level permission check (never consults shop memberships). */
    public boolean hasPermission(UUID userId, String permission) {
        var snapshot = matrix.current();
        // Fast path: granted to every signed-in account (BUYER / APPLICANT), no query needed.
        if (snapshot.signedInPermissions().contains(permission)) return true;
        var grants = grants(userId);
        if (grants.superAdmin()) return true;
        for (var role : grants.platformRoles()) {
            if (snapshot.permissionsOf(role).contains(permission)) return true;
        }
        return false;
    }

    /** Shop-level permission check on the shop with this public number (shops.shop_no). */
    public ShopDecision checkShop(UUID userId, Long shopNo, String permission) {
        if (shopNo == null) return ShopDecision.NOT_MEMBER;
        var grants = grants(userId);
        // a super admin may act in any shop that exists; a missing shop is still 404 like for everyone else
        if (grants.superAdmin()) return shopExists(shopNo) ? ShopDecision.GRANTED : ShopDecision.NOT_MEMBER;
        var role = grants.shopRoles().get(shopNo);
        if (role == null) return ShopDecision.NOT_MEMBER;
        return matrix.permissionsOf(role).contains(permission) ? ShopDecision.GRANTED : ShopDecision.DENIED;
    }

    /**
     * Member shops (shop numbers) in which the user holds the permission. Lists stay limited to the user's own
     * memberships; a super admin holds the permission in all of them and reaches other shops by shopId.
     */
    public Set<Long> shopsWithPermission(UUID userId, String permission) {
        var snapshot = matrix.current();
        var grants = grants(userId);
        var result = new TreeSet<Long>();
        grants.shopRoles().forEach((shopNo, role) -> {
            if (grants.superAdmin() || snapshot.permissionsOf(role).contains(permission)) result.add(shopNo);
        });
        return Collections.unmodifiableSet(result);
    }

    /**
     * The shop used when a shop-scoped endpoint is called without shopId: the membership with the highest shop
     * number, i.e. the newest shop (services pick the same one via ShopRepository#findMemberShops).
     */
    public Optional<Long> defaultShop(UUID userId) {
        var shops = grants(userId).shopRoles();
        return shops.isEmpty() ? Optional.empty() : Optional.of(shops.lastKey());
    }

    public EffectivePermissions effectivePermissions(UUID userId) {
        var snapshot = matrix.current();
        var grants = loadGrants(userId);
        var roles = new TreeSet<>(snapshot.signedInRoles());
        var permissions = new TreeSet<>(snapshot.signedInPermissions());
        for (var role : grants.platformRoles()) {
            roles.add(role);
            permissions.addAll(snapshot.permissionsOf(role));
        }
        var superAdmin = grants.superAdmin();
        var storeCodes = new TreeSet<String>();
        snapshot.scopeByPermission().forEach((code, scope) -> {
            if (!superAdmin) return;
            if ("STORE".equals(scope)) storeCodes.add(code); else permissions.add(code);
        });
        var shops = new ArrayList<ShopPermissions>();
        grants.shopRoles().forEach((shopNo, role) -> shops.add(new ShopPermissions(shopNo, role,
            List.copyOf(superAdmin ? storeCodes : new TreeSet<>(snapshot.permissionsOf(role))))));
        return new EffectivePermissions(List.copyOf(roles), List.copyOf(permissions), List.copyOf(shops), superAdmin);
    }

    private boolean shopExists(Long shopNo) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM shops WHERE shop_no = ?)", Boolean.class, shopNo));
    }

    /** Grants of the user, loaded once per HTTP request. */
    public Grants grants(UUID userId) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) return loadGrants(userId);
        var key = REQUEST_CACHE_PREFIX + userId;
        var cached = attributes.getAttribute(key, RequestAttributes.SCOPE_REQUEST);
        if (cached instanceof Grants grants) return grants;
        var loaded = loadGrants(userId);
        attributes.setAttribute(key, loaded, RequestAttributes.SCOPE_REQUEST);
        return loaded;
    }

    /** Single round trip: active platform roles + active shop memberships of the user. */
    Grants loadGrants(UUID userId) {
        var platformRoles = new TreeSet<String>();
        var shopRoles = new TreeMap<Long, String>();
        jdbc.query("""
            SELECT 'PLATFORM' AS source, pr.role AS role_code, CAST(NULL AS BIGINT) AS shop_no
              FROM platform_roles pr
             WHERE pr.user_id = ? AND pr.revoked_at IS NULL
            UNION ALL
            SELECT 'STORE' AS source, sm.role AS role_code, s.shop_no AS shop_no
              FROM shop_members sm
              JOIN shops s ON s.id = sm.shop_id
             WHERE sm.user_id = ? AND sm.status = 'ACTIVE' AND sm.removed_at IS NULL
            """, rs -> {
            var role = rs.getString("role_code");
            if ("PLATFORM".equals(rs.getString("source"))) {
                platformRoles.add(role);
            } else {
                var shopNo = rs.getLong("shop_no");
                if (!rs.wasNull()) shopRoles.put(shopNo, role);
            }
        }, userId, userId);
        return new Grants(userId, Collections.unmodifiableSet(platformRoles), Collections.unmodifiableNavigableMap(shopRoles));
    }
}
