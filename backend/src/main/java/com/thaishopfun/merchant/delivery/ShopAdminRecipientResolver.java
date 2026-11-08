package com.thaishopfun.merchant.delivery;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Active platform admins who can review shop applications ({@code PLATFORM_ADMIN} or a platform role
 * that maps {@code PLATFORM_APPLICATION_READ}).
 */
@Component
public class ShopAdminRecipientResolver {
    public record Recipient(String email, String name) {}

    private final NamedParameterJdbcTemplate jdbc;

    public ShopAdminRecipientResolver(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Recipient> applicationReviewers() {
        var rows = jdbc.query("""
            SELECT DISTINCT LOWER(TRIM(u.email)) AS email, u.first_name, u.last_name
              FROM users u
              JOIN platform_roles pr ON pr.user_id = u.id AND pr.revoked_at IS NULL
             WHERE u.deleted_at IS NULL
               AND COALESCE(u.status, 'ACTIVE') = 'ACTIVE'
               AND u.email IS NOT NULL
               AND TRIM(u.email) <> ''
               AND (
                    pr.role = 'PLATFORM_ADMIN'
                    OR EXISTS (
                        SELECT 1
                          FROM auth_roles r
                          JOIN auth_role_permissions rp ON rp.role_id = r.id
                          JOIN auth_permissions p ON p.id = rp.permission_id AND p.is_active = TRUE
                         WHERE r.code = pr.role
                           AND p.code = 'PLATFORM_APPLICATION_READ'
                    )
               )
            """, new MapSqlParameterSource(), (rs, i) -> {
            var email = rs.getString("email");
            var first = rs.getString("first_name");
            var last = rs.getString("last_name");
            var name = ((first == null ? "" : first.trim()) + " " + (last == null ? "" : last.trim())).trim();
            return new Recipient(email, name.isBlank() ? null : name);
        });

        var unique = new LinkedHashMap<String, Recipient>();
        for (var row : rows) {
            if (row.email() == null || row.email().isBlank()) continue;
            unique.putIfAbsent(row.email().toLowerCase(Locale.ROOT), row);
        }
        return new ArrayList<>(unique.values());
    }
}
