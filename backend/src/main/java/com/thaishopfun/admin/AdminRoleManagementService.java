package com.thaishopfun.admin;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.auth.authz.PermissionMatrix;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminRoleManagementService {
    static final Set<String> ROLE_ADMIN_PERMISSIONS = Set.of(
        "PLATFORM_ROLE_CREATE", "PLATFORM_ROLE_UPDATE", "PLATFORM_ROLE_DELETE", "PLATFORM_ACCOUNT_ROLE_ASSIGN");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authorization;
    private final PermissionMatrix matrix;

    public AdminRoleManagementService(NamedParameterJdbcTemplate jdbc, AuthorizationService authorization, PermissionMatrix matrix) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.matrix = matrix;
    }

    @Transactional
    public String create(AdminManagementDtos.RoleRequest request, UUID actorId) {
        requireSuperAdmin(actorId);
        var context = request.context() == null ? "PLATFORM" : request.context();
        var permissions = validatePermissions(request.permissions(), context);
        var code = (context.equals("STORE") ? "STORE_CUSTOM_" : "CUSTOM_")
            + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        var id = UUID.randomUUID();
        var now = Instant.now();
        jdbc.update("""
            INSERT INTO auth_roles (id, code, context, name_th, description_th, is_assignable, sort_order, policy_version,
                                    created_date_time, modified_date_time)
            VALUES (:id, :code, :context, :name, :description, TRUE,
                    (SELECT COALESCE(MAX(sort_order), 100) + 1 FROM auth_roles), 'CUSTOM', :now, :now)
            """, new MapSqlParameterSource().addValue("id", id).addValue("code", code)
            .addValue("context", context)
            .addValue("name", request.nameTh().trim()).addValue("description", trimToNull(request.descriptionTh()))
            .addValue("now", Timestamp.from(now)).addValue("actor", actorId));
        replacePermissions(id, code, permissions, actorId, now);
        audit(actorId, "ROLE_CREATED", id, code);
        afterCommitInvalidate();
        return code;
    }

    @Transactional
    public void update(String code, AdminManagementDtos.RoleRequest request, UUID actorId) {
        requireSuperAdmin(actorId);
        var role = requireCustomRole(code);
        var context = request.context() == null ? role.context : request.context();
        if (!role.context.equals(context)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "ROLE_CONTEXT_IMMUTABLE", "ไม่สามารถเปลี่ยนประเภทของบทบาทได้");
        }
        var permissions = validatePermissions(request.permissions(), context);
        var now = Instant.now();
        jdbc.update("""
            UPDATE auth_roles
               SET name_th = :name, description_th = :description, is_assignable = TRUE,
                   modified_date_time = :now
             WHERE code = :code
            """, new MapSqlParameterSource().addValue("name", request.nameTh().trim())
            .addValue("description", trimToNull(request.descriptionTh())).addValue("now", Timestamp.from(now))
            .addValue("code", code));
        replacePermissions(role.id, code, permissions, actorId, now);
        audit(actorId, "ROLE_UPDATED", role.id, code);
        afterCommitInvalidate();
    }

    @Transactional
    public void delete(String code, UUID actorId) {
        requireSuperAdmin(actorId);
        var role = requireCustomRole(code);
        var now = Instant.now();
        if ("PLATFORM".equals(role.context)) {
            jdbc.update("""
                UPDATE platform_roles
                   SET revoked_at = :now, revoked_by = :actor, revoke_reason = 'ROLE_DELETED',
                       modified_date_time = :now
                 WHERE role = :code AND revoked_at IS NULL
                """, new MapSqlParameterSource().addValue("now", Timestamp.from(now)).addValue("actor", actorId).addValue("code", code));
            jdbc.update("""
                UPDATE users SET permission_version = permission_version + 1, modified_date_time = :now
                 WHERE id IN (SELECT user_id FROM platform_roles WHERE role = :code AND revoked_at = :now)
                """, new MapSqlParameterSource().addValue("now", Timestamp.from(now)).addValue("code", code));
        } else {
            jdbc.update("""
                UPDATE shop_members
                   SET status = 'INACTIVE', removed_at = :now, modified_date_time = :now
                 WHERE role = :code AND status = 'ACTIVE' AND removed_at IS NULL
                """, new MapSqlParameterSource().addValue("now", Timestamp.from(now)).addValue("code", code));
        }
        jdbc.update("DELETE FROM auth_role_permissions WHERE role_id = :id", new MapSqlParameterSource("id", role.id));
        jdbc.update("""
            UPDATE auth_roles SET is_assignable = FALSE, modified_date_time = :now
             WHERE id = :id
            """, new MapSqlParameterSource().addValue("now", Timestamp.from(now)).addValue("id", role.id));
        audit(actorId, "ROLE_DELETED", role.id, code);
        afterCommitInvalidate();
    }

    @Transactional
    public void assign(UUID userId, String code, UUID actorId) {
        requireSuperAdmin(actorId);
        var params = new MapSqlParameterSource("userId", userId);
        var target = jdbc.query("""
            SELECT id FROM users WHERE id = :userId AND deleted_at IS NULL AND status = 'ACTIVE'
            """, params, (rs, row) -> rs.getObject("id", UUID.class));
        if (target.isEmpty()) throw new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ไม่พบผู้ใช้งาน");
        if (userId.equals(actorId)) throw conflict("SELF_ADMIN_ACTION", "ไม่สามารถเปลี่ยนบทบาทบัญชีที่กำลังใช้งานได้");
        if (authorization.isSuperAdmin(userId)) throw conflict("SUPER_ADMIN_PROTECTED", "ไม่สามารถเปลี่ยนแปลงบทบาทผู้ดูแลระบบหลักได้");

        String nextRole = code == null ? "" : code.trim();
        if (!nextRole.isEmpty()) {
            if (AuthorizationService.SUPER_ADMIN_ROLE.equals(nextRole)) {
                throw conflict("SUPER_ADMIN_PROTECTED", "ไม่สามารถแต่งตั้งผู้ดูแลระบบหลักผ่านหน้าจอนี้ได้");
            }
            var role = requireCustomRole(nextRole);
            if (!role.assignable) throw conflict("ROLE_NOT_ASSIGNABLE", "บทบาทนี้ไม่สามารถกำหนดให้ผู้ใช้ได้");
        }

        var current = jdbc.query("""
            SELECT role FROM platform_roles WHERE user_id = :userId AND revoked_at IS NULL
            """, params, (rs, row) -> rs.getString("role"));
        if (current.size() == 1 && current.get(0).equals(nextRole)) return;
        var now = Instant.now();
        revokeActiveRole(userId, actorId, "ROLE_CHANGED");
        if (!nextRole.isEmpty()) {
            jdbc.update("""
                INSERT INTO platform_roles (id, user_id, role, granted_by, granted_at, grant_reason,
                                            created_date_time, modified_date_time)
                VALUES (:id, :userId, :role, :actor, :now, 'ADMIN_ASSIGNMENT', :now, :now)
                """, new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("userId", userId)
                .addValue("role", nextRole).addValue("actor", actorId).addValue("now", Timestamp.from(now)));
        }
        jdbc.update("""
            UPDATE users SET permission_version = permission_version + 1, modified_date_time = :now WHERE id = :userId
            """, new MapSqlParameterSource().addValue("now", Timestamp.from(now)).addValue("userId", userId));
        audit(actorId, nextRole.isEmpty() ? "PLATFORM_ROLE_REVOKED" : "PLATFORM_ROLE_GRANTED",
            userId, nextRole.isEmpty() ? "NONE" : nextRole);
    }

    @Transactional
    void revokeActiveRole(UUID userId, UUID actorId, String reason) {
        var now = Instant.now();
        jdbc.update("""
             UPDATE platform_roles
                SET revoked_at = :now, revoked_by = :actor, revoke_reason = :reason,
                    modified_date_time = :now
             WHERE user_id = :userId AND revoked_at IS NULL
             """, new MapSqlParameterSource().addValue("now", Timestamp.from(now)).addValue("actor", actorId)
             .addValue("reason", reason).addValue("userId", userId));
    }

    private void replacePermissions(UUID roleId, String code, Set<String> permissions, UUID actorId, Instant now) {
        jdbc.update("DELETE FROM auth_role_permissions WHERE role_id = :roleId", new MapSqlParameterSource("roleId", roleId));
        var batch = permissions.stream().map(permission -> new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID()).addValue("roleId", roleId).addValue("code", permission)
            .addValue("now", Timestamp.from(now)).addValue("actor", actorId)).toArray(MapSqlParameterSource[]::new);
        for (var params : batch) {
            jdbc.update("""
                INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version, created_date_time,
                                                   modified_date_time)
                SELECT :id, :roleId, id, 'CUSTOM', :now, :now
                  FROM auth_permissions WHERE code = :code AND is_active = TRUE
                """, params);
        }
        audit(actorId, "ROLE_PERMISSIONS_UPDATED", roleId, code);
    }

    private Set<String> validatePermissions(List<String> submitted, String context) {
        var codes = submitted.stream().map(String::trim).collect(Collectors.toCollection(LinkedHashSet::new));
        if (codes.size() != submitted.size() || codes.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_ROLE_PERMISSIONS", "เลือกสิทธิ์อย่างน้อยหนึ่งรายการและห้ามเลือกรายการซ้ำ");
        }
        if ("PLATFORM".equals(context) && codes.stream().anyMatch(ROLE_ADMIN_PERMISSIONS::contains)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "PROTECTED_PERMISSION", "สิทธิ์จัดการบทบาทและแต่งตั้งผู้ดูแลระบบมอบให้บทบาทกำหนดเองไม่ได้");
        }
        var allowed = jdbc.query("""
            SELECT code FROM auth_permissions WHERE scope = :scope AND is_active = TRUE AND code IN (:codes)
            """, new MapSqlParameterSource().addValue("scope", "STORE".equals(context) ? "STORE" : "PLATFORM")
            .addValue("codes", codes), (rs, row) -> rs.getString("code"));
        if (allowed.size() != codes.size()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_ROLE_PERMISSIONS", "เลือกได้เฉพาะสิทธิ์แพลตฟอร์มที่เปิดใช้งาน");
        }
        return codes;
    }

    private RoleRecord requireCustomRole(String code) {
        var roles = jdbc.query("""
            SELECT id, context, is_assignable FROM auth_roles
             WHERE code = :code AND context IN ('PLATFORM', 'STORE') AND policy_version = 'CUSTOM'
            """, new MapSqlParameterSource("code", code), (rs, row) ->
            new RoleRecord(rs.getObject("id", UUID.class), rs.getString("context"), rs.getBoolean("is_assignable")));
        if (roles.isEmpty()) throw new AuthException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "ไม่พบบทบาทที่กำหนดเอง");
        return roles.get(0);
    }

    private void requireSuperAdmin(UUID actorId) {
        if (!authorization.isSuperAdmin(actorId)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "SUPER_ADMIN_REQUIRED", "ต้องใช้บัญชีผู้ดูแลระบบหลักเพื่อจัดการบทบาทแพลตฟอร์ม");
        }
    }

    private void audit(UUID actorId, String action, UUID resourceId, String code) {
        var now = Instant.now();
        jdbc.update("""
            INSERT INTO audit_logs (occurred_at, actor_id, actor_type, action, resource_type, resource_id,
                                    outcome, reason_code, auth_context, policy_version, created_date_time,
                                    modified_date_time)
            VALUES (:now, :actor, 'USER', :action, 'ROLE', :resource, 'SUCCESS', :reason, 'PLATFORM', 'CUSTOM',
                    :now, :now)
            """, new MapSqlParameterSource().addValue("now", Timestamp.from(now)).addValue("actor", actorId)
            .addValue("action", action).addValue("resource", resourceId).addValue("reason", code));
    }

    private void afterCommitInvalidate() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { matrix.invalidate(); }
        });
    }

    private static String trimToNull(String text) {
        if (text == null || text.isBlank()) return null;
        return text.trim();
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    private record RoleRecord(UUID id, String context, boolean assignable) {}
}
