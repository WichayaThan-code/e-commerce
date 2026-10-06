package com.thaishopfun.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Role and permission catalog rows (auth_roles / auth_permissions), read straight from the database. */
@Component
class AdminCatalog {
    record RoleRow(String code, String context, String nameTh, String descriptionTh, boolean assignable, int sortOrder, String policyVersion) {
        AdminDirectoryDtos.RoleRef ref() { return new AdminDirectoryDtos.RoleRef(code, nameTh, context); }
    }

    private final JdbcTemplate jdbc;

    AdminCatalog(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** All roles ordered by sort_order, keyed by code. */
    Map<String, RoleRow> roles() {
        var roles = new LinkedHashMap<String, RoleRow>();
        jdbc.query("""
            SELECT code, context, name_th, description_th, is_assignable, sort_order, policy_version
              FROM auth_roles ORDER BY sort_order, code
            """, rs -> {
            roles.put(rs.getString("code"), new RoleRow(rs.getString("code"), rs.getString("context"), rs.getString("name_th"),
                rs.getString("description_th"), rs.getBoolean("is_assignable"), rs.getInt("sort_order"), rs.getString("policy_version")));
        });
        return roles;
    }

    /** The whole permission catalog ordered by module and code, keyed by code. */
    Map<String, AdminDirectoryDtos.PermissionInfo> permissions() {
        var permissions = new LinkedHashMap<String, AdminDirectoryDtos.PermissionInfo>();
        jdbc.query("SELECT code, module, scope, description_th, is_active FROM auth_permissions ORDER BY module, code", rs -> {
            permissions.put(rs.getString("code"), new AdminDirectoryDtos.PermissionInfo(rs.getString("code"), rs.getString("module"),
                rs.getString("scope"), rs.getString("description_th"), rs.getBoolean("is_active")));
        });
        return permissions;
    }

    static boolean isPlatform(RoleRow role) { return "PLATFORM".equals(role.context()); }
    static boolean isStore(RoleRow role) { return "STORE".equals(role.context()); }
    /** ACCOUNT / APPLICANT roles are held implicitly by every signed-in account (same rule as PermissionMatrix). */
    static boolean isImplicit(RoleRow role) { return "ACCOUNT".equals(role.context()) || "APPLICANT".equals(role.context()); }
}
