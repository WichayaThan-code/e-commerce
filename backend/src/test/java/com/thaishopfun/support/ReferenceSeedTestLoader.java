package com.thaishopfun.support;

import com.thaishopfun.auth.authz.PermissionMatrix;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/**
 * Test profile only: loads the authz catalog from the real seed_reference_data.sql into the H2 test database
 * when the context starts (H2 tables are created by Hibernate from the authz entities). Idempotent: skipped when
 * auth_roles already has rows (several test contexts share one in-memory database).
 */
@Component
@Profile("test")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReferenceSeedTestLoader implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PermissionMatrix matrix;

    public ReferenceSeedTestLoader(JdbcTemplate jdbc, PermissionMatrix matrix) {
        this.jdbc = jdbc;
        this.matrix = matrix;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(jdbc);
        matrix.invalidate();
    }

    public static synchronized void seed(JdbcTemplate jdbc) {
        var existing = jdbc.queryForObject("SELECT COUNT(*) FROM auth_roles", Integer.class);
        if (existing != null && existing > 0) {
            seedPlans(jdbc);
            return;
        }
        var catalog = ReferenceSeedSql.load();
        var now = Timestamp.from(Instant.now());
        var roleIds = new HashMap<String, UUID>();
        var permissionIds = new HashMap<String, UUID>();
        insert(jdbc, "auth_roles", catalog.roles(), roleIds, now);
        insert(jdbc, "auth_permissions", catalog.permissions(), permissionIds, now);
        var batch = new ArrayList<Object[]>();
        for (var mapping : catalog.mappings()) {
            var roleId = roleIds.get((String) mapping.get(0));
            var permissionId = permissionIds.get((String) mapping.get(1));
            if (roleId == null || permissionId == null) throw new IllegalStateException("Seed mapping references unknown code: " + mapping);
            batch.add(new Object[]{UUID.randomUUID(), roleId, permissionId, "1.0", now, now});
        }
        jdbc.batchUpdate("INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, ?, ?)", batch);
        seedPlans(jdbc);
    }

    /** V47 plan catalog (same rows as migration seed) — Hibernate create-drop does not run Flyway. */
    private static void seedPlans(JdbcTemplate jdbc) {
        var existing = jdbc.queryForObject("SELECT COUNT(*) FROM plans", Integer.class);
        if (existing != null && existing > 0) return;
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
            INSERT INTO plans (code, name, price_monthly, price_yearly,
                limit_products, limit_orders_per_month, limit_staff, limit_storage_bytes,
                description, sort_order, active, created_at, updated_at)
            VALUES
              ('FREE',  'Free',  0,    0,    5,    50,   2,    104857600,   'ร้านเริ่มต้น',     1, TRUE, ?, ?),
              ('BASIC', 'Basic', 290,  2900, 50,   500,  5,    1073741824,  'ร้านที่เริ่มโต',    2, TRUE, ?, ?),
              ('PRO',   'Pro',   790,  7900, NULL, NULL, NULL, 10737418240, 'ร้านที่ขายจริงจัง', 3, TRUE, ?, ?)
            """, now, now, now, now, now, now);
    }

    private static void insert(JdbcTemplate jdbc, String table, ReferenceSeedSql.Table rows, HashMap<String, UUID> ids, Timestamp now) {
        var columns = String.join(", ", rows.columns());
        var placeholders = "?, ".repeat(rows.columns().size());
        var sql = "INSERT INTO " + table + " (id, " + columns + ", created_date_time, modified_date_time) VALUES (?, " + placeholders + "?, ?)";
        var codeIndex = rows.columns().indexOf("code");
        var batch = new ArrayList<Object[]>();
        for (var row : rows.rows()) {
            var id = UUID.randomUUID();
            ids.put((String) row.get(codeIndex), id);
            var values = new ArrayList<Object>();
            values.add(id);
            values.addAll(row);
            values.add(now);
            values.add(now);
            batch.add(values.toArray());
        }
        jdbc.batchUpdate(sql, batch);
    }
}
