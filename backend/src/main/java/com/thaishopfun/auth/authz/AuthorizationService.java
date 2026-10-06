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
 */
@Service
public class AuthorizationService {
    private static final String REQUEST_CACHE_PREFIX = AuthorizationService.class.getName() + ".grants.";

    private final JdbcTemplate jdbc;
    private final PermissionMatrix matrix;

    public AuthorizationService(JdbcTemplate jdbc, PermissionMatrix matrix) {
        this.jdbc = jdbc;
        this.matrix = matrix;
    }

    /** Role assignments of one user at the time of the request. shopRoles: shops.shop_no to shop_members.role. */
    public record Grants(UUID userId, Set<String> platformRoles, NavigableMap<Long, String> shopRoles) {}

    public enum ShopDecision { GRANTED, DENIED, NOT_MEMBER }

    public record ShopPermissions(Long shopId, String role, List<String> permissions) {}

    /** What the current user may do, as exposed to the frontend through /api/auth/me. */
    public record EffectivePermissions(List<String> roles, List<String> permissions, List<ShopPermissions> shops) {}

    /** Account/platform-level permission check (never consults shop memberships). */
    public boolean hasPermission(UUID userId, String permission) {
        var snapshot = matrix.current();
        // Fast path: granted to every signed-in account (BUYER / APPLICANT), no query needed.
        if (snapshot.signedInPermissions().contains(permission)) return true;
        for (var role : grants(userId).platformRoles()) {
            if (snapshot.permissionsOf(role).contains(permission)) return true;
        }
        return false;
    }

    /** Shop-level permission check on the shop with this public number (shops.shop_no). */
    public ShopDecision checkShop(UUID userId, Long shopNo, String permission) {
        if (shopNo == null) return ShopDecision.NOT_MEMBER;
        var role = grants(userId).shopRoles().get(shopNo);
        if (role == null) return ShopDecision.NOT_MEMBER;
        return matrix.permissionsOf(role).contains(permission) ? ShopDecision.GRANTED : ShopDecision.DENIED;
    }

    /** Shops (shop numbers) in which the user holds the permission. */
    public Set<Long> shopsWithPermission(UUID userId, String permission) {
        var snapshot = matrix.current();
        var result = new TreeSet<Long>();
        grants(userId).shopRoles().forEach((shopNo, role) -> {
            if (snapshot.permissionsOf(role).contains(permission)) result.add(shopNo);
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
        var shops = new ArrayList<ShopPermissions>();
        grants.shopRoles().forEach((shopNo, role) ->
            shops.add(new ShopPermissions(shopNo, role, List.copyOf(new TreeSet<>(snapshot.permissionsOf(role))))));
        return new EffectivePermissions(List.copyOf(roles), List.copyOf(permissions), List.copyOf(shops));
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
