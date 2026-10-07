package com.thaishopfun.auth.authz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * In-memory copy of the Role x Permission matrix stored in the database
 * ({@code auth_roles}, {@code auth_permissions}, {@code auth_role_permissions}).
 * <p>The catalog only changes through migrations / seed_reference_data.sql, so it is cached for
 * {@code app.security.permission-cache-seconds} (default 60) and then reloaded with two small queries; call
 * {@link #invalidate()} to force an immediate reload. Who holds which role (platform_roles, shop_members) is NOT
 * cached here: {@link AuthorizationService} reads it live on each request, so role changes apply immediately.
 * An empty catalog is never cached and means every permission check is denied (deny by default).
 */
@Component
public class PermissionMatrix {
    private static final Logger log = LoggerFactory.getLogger(PermissionMatrix.class);

    /**
     * Role contexts (auth_roles.context) that every signed-in, active account holds implicitly: ACCOUNT (BUYER) and
     * APPLICANT (anyone may apply to open a shop; services restrict it to the caller's own application).
     * STORE roles come from shop_members, PLATFORM roles from platform_roles, PUBLIC (GUEST) is for anonymous
     * visitors and is served by {@link PublicEndpoint} endpoints.
     */
    static final Set<String> SIGNED_IN_CONTEXTS = Set.of("ACCOUNT", "APPLICANT");

    private final JdbcTemplate jdbc;
    private final Duration ttl;
    private final Object lock = new Object();
    private volatile Snapshot snapshot;

    public PermissionMatrix(JdbcTemplate jdbc, @Value("${app.security.permission-cache-seconds:60}") long cacheSeconds) {
        this.jdbc = jdbc;
        this.ttl = Duration.ofSeconds(Math.max(0, cacheSeconds));
    }

    /** Immutable view of the matrix. */
    public record Snapshot(
        Map<String, Set<String>> permissionsByRole,
        Map<String, String> contextByRole,
        Map<String, String> scopeByPermission,
        Set<String> signedInRoles,
        Set<String> signedInPermissions,
        int mappingCount,
        long loadedAtNanos
    ) {
        public Set<String> permissionsOf(String role) {
            return role == null ? Set.of() : permissionsByRole.getOrDefault(role, Set.of());
        }

        public boolean isEmpty() { return mappingCount == 0; }
    }

    public Snapshot current() {
        var current = snapshot;
        if (current != null && !current.isEmpty() && System.nanoTime() - current.loadedAtNanos() < ttl.toNanos()) {
            return current;
        }
        synchronized (lock) {
            current = snapshot;
            if (current != null && !current.isEmpty() && System.nanoTime() - current.loadedAtNanos() < ttl.toNanos()) {
                return current;
            }
            try {
                snapshot = load();
            } catch (RuntimeException exception) {
                if (current == null) throw exception;
                log.error("Reloading the permission matrix failed; keeping the previous copy: {}", exception.getMessage());
                return current;
            }
            return snapshot;
        }
    }

    /** Forces the next {@link #current()} call to reload from the database. */
    public void invalidate() {
        synchronized (lock) { snapshot = null; }
    }

    /** Permissions of the role (empty for unknown roles). */
    public Set<String> permissionsOf(String role) { return current().permissionsOf(role); }

    /** Permissions every signed-in account holds through its implicit account-context roles (BUYER, APPLICANT ...). */
    public Set<String> signedInPermissions() { return current().signedInPermissions(); }

    /** Account-context role codes every signed-in account holds. */
    public Set<String> signedInRoles() { return current().signedInRoles(); }

    /** auth_permissions.scope of an active permission, or null when the code is unknown / inactive. */
    public String scopeOf(String permission) { return current().scopeByPermission().get(permission); }

    @EventListener(ApplicationReadyEvent.class)
    void warmUp() {
        try {
            var loaded = current();
            if (loaded.isEmpty()) {
                log.error("auth_role_permissions ว่าง: ทุก API ที่ต้องใช้สิทธิ์จะถูกปฏิเสธ (403). รัน backend/src/main/resources/db/seed/seed_reference_data.sql กับฐานข้อมูลก่อน");
            } else {
                log.info("Permission matrix loaded: {} roles, {} permissions, {} mappings",
                    loaded.contextByRole().size(), loaded.scopeByPermission().size(), loaded.mappingCount());
            }
        } catch (RuntimeException exception) {
            log.error("Cannot load the permission matrix: {}", exception.getMessage());
        }
    }

    private Snapshot load() {
        var contextByRole = new HashMap<String, String>();
        var permissionsByRole = new HashMap<String, Set<String>>();
        var scopeByPermission = new HashMap<String, String>();
        jdbc.query("SELECT code, scope FROM auth_permissions WHERE is_active = TRUE",
            rs -> { scopeByPermission.put(rs.getString("code"), rs.getString("scope")); });
        var mappings = new int[1];
        jdbc.query("""
            SELECT r.code AS role_code, r.context AS role_context, p.code AS permission_code
            FROM auth_roles r
            LEFT JOIN auth_role_permissions rp ON rp.role_id = r.id
            LEFT JOIN auth_permissions p ON p.id = rp.permission_id AND p.is_active = TRUE
            """, rs -> {
            var role = rs.getString("role_code");
            contextByRole.put(role, rs.getString("role_context"));
            var permissions = permissionsByRole.computeIfAbsent(role, key -> new TreeSet<>());
            var permission = rs.getString("permission_code");
            if (permission != null) {
                permissions.add(permission);
                mappings[0]++;
            }
        });
        var signedInRoles = new TreeSet<String>();
        var signedInPermissions = new TreeSet<String>();
        contextByRole.forEach((role, context) -> {
            if (SIGNED_IN_CONTEXTS.contains(context)) {
                signedInRoles.add(role);
                signedInPermissions.addAll(permissionsByRole.getOrDefault(role, Set.of()));
            }
        });
        var frozen = new HashMap<String, Set<String>>();
        permissionsByRole.forEach((role, permissions) -> frozen.put(role, Collections.unmodifiableSet(permissions)));
        return new Snapshot(
            Map.copyOf(frozen),
            Map.copyOf(contextByRole),
            Map.copyOf(scopeByPermission),
            Collections.unmodifiableSet(new HashSet<>(signedInRoles)),
            Collections.unmodifiableSet(new HashSet<>(signedInPermissions)),
            mappings[0],
            System.nanoTime()
        );
    }
}
