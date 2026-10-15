package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminDirectoryDtos.UserDetail;
import com.thaishopfun.auth.AuthDtos;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.AuthService;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class AdminUserManagementService {
    private final NamedParameterJdbcTemplate jdbc;
    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuthService auth;
    private final PasswordEncoder passwords;
    private final AuthorizationService authorization;
    private final AdminUserDirectoryService directory;
    private final AdminRoleManagementService roles;

    public AdminUserManagementService(NamedParameterJdbcTemplate jdbc, UserAccountRepository users,
                                      RefreshTokenRepository refreshTokens, AuthService auth,
                                      PasswordEncoder passwords, AuthorizationService authorization, AdminUserDirectoryService directory,
                                      AdminRoleManagementService roles) {
        this.jdbc = jdbc;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.auth = auth;
        this.passwords = passwords;
        this.authorization = authorization;
        this.directory = directory;
        this.roles = roles;
    }

    @Transactional
    public UserDetail create(AdminManagementDtos.CreateUserRequest request, UUID actorId) {
        var email = request.email().trim().toLowerCase();
        var phone = normalizePhone(request.phone());
        if (users.existsByEmailIgnoreCase(email)) throw conflict("EMAIL_EXISTS", "อีเมลนี้ถูกใช้งานแล้ว");
        if (users.existsByPhone(phone)) throw conflict("PHONE_EXISTS", "เบอร์โทรศัพท์นี้ถูกใช้งานแล้ว");
        var randomPassword = UUID.randomUUID() + UUID.randomUUID().toString();
        var user = users.saveAndFlush(new UserAccount(
            request.firstName().trim(), request.lastName().trim(), email, phone, passwords.encode(randomPassword)));
        audit(actorId, "ACCOUNT_CREATED_BY_ADMIN", user.getId(), "SUCCESS", "ADMIN_CREATE");
        auth.forgotPassword(new AuthDtos.ForgotPasswordRequest(email));
        return directory.detail(user.getId());
    }

    @Transactional
    public UserDetail update(UUID userId, AdminManagementDtos.UpdateUserRequest request, UUID actorId) {
        var user = requireUser(userId);
        if (!user.getPhone().equals(normalizePhone(request.phone())) && users.existsByPhone(normalizePhone(request.phone()))) {
            throw conflict("PHONE_EXISTS", "เบอร์โทรศัพท์นี้ถูกใช้งานแล้ว");
        }
        jdbc.update("""
            UPDATE users
               SET first_name = :firstName, last_name = :lastName, phone = :phone, modified_date_time = :now
             WHERE id = :id AND deleted_at IS NULL
            """, new MapSqlParameterSource()
            .addValue("firstName", request.firstName().trim())
            .addValue("lastName", request.lastName().trim())
            .addValue("phone", normalizePhone(request.phone()))
            .addValue("now", Timestamp.from(Instant.now()))
            .addValue("id", userId));
        audit(actorId, "ACCOUNT_UPDATED_BY_ADMIN", userId, "SUCCESS", "ADMIN_UPDATE");
        return directory.detail(userId);
    }

    @Transactional
    public UserDetail assignRole(UUID userId, String roleCode, UUID actorId) {
        requireMutableTarget(userId, actorId);
        roles.assign(userId, roleCode, actorId);
        return directory.detail(userId);
    }

    @Transactional
    public UserDetail setStatus(UUID userId, String status, UUID actorId) {
        requireMutableTarget(userId, actorId);
        var user = requireUser(userId);
        if (user.getStatus().name().equals(status)) return directory.detail(userId);
        var now = Instant.now();
        jdbc.update("""
            UPDATE users SET status = :status, permission_version = permission_version + 1, modified_date_time = :now
             WHERE id = :id AND deleted_at IS NULL
            """, new MapSqlParameterSource().addValue("status", status).addValue("now", Timestamp.from(now)).addValue("id", userId));
        refreshTokens.revokeAllActiveForUser(userId, now);
        audit(actorId, status.equals("SUSPENDED") ? "ACCOUNT_SUSPENDED" : "ACCOUNT_RESTORED", userId, "SUCCESS", "ADMIN_STATUS");
        return directory.detail(userId);
    }

    @Transactional
    public void delete(UUID userId, UUID actorId) {
        requireMutableTarget(userId, actorId);
        requireUser(userId);
        var ownsActiveShop = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM shops s
                 WHERE s.owner_user_id = :id
                   AND s.status NOT IN ('CLOSED', 'REJECTED')
                   AND s.deleted_at IS NULL
            )
            """, new MapSqlParameterSource("id", userId), Boolean.class));
        if (ownsActiveShop) {
            throw conflict("SHOP_OWNERSHIP_REQUIRED", "ต้องโอนหรือปิดร้านก่อนลบบัญชีเจ้าของร้าน");
        }
        var now = Instant.now();
        jdbc.update("""
            UPDATE users
               SET deleted_at = :now, status = 'SUSPENDED',
                   permission_version = permission_version + 1, modified_date_time = :now
             WHERE id = :id AND deleted_at IS NULL
            """, new MapSqlParameterSource().addValue("id", userId).addValue("now", Timestamp.from(now)));
        refreshTokens.revokeAllActiveForUser(userId, now);
        roles.revokeActiveRole(userId, actorId, "ACCOUNT_DELETED");
        audit(actorId, "ACCOUNT_DELETED", userId, "SUCCESS", "ADMIN_DELETE");
    }

    private void requireMutableTarget(UUID userId, UUID actorId) {
        if (userId.equals(actorId)) throw conflict("SELF_ADMIN_ACTION", "ไม่สามารถระงับ ลบ หรือเปลี่ยนบทบาทบัญชีที่กำลังใช้งานได้");
        if (authorization.isSuperAdmin(userId)) throw conflict("SUPER_ADMIN_PROTECTED", "ไม่สามารถเปลี่ยนแปลงบัญชีผู้ดูแลระบบหลักได้");
    }

    private UserAccount requireUser(UUID userId) {
        return users.findById(userId)
            .filter(user -> !user.isDeleted())
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ไม่พบผู้ใช้งาน"));
    }

    private void audit(UUID actorId, String action, UUID resourceId, String outcome, String reasonCode) {
        var now = Instant.now();
        jdbc.update("""
            INSERT INTO audit_logs (occurred_at, actor_id, actor_type, action, resource_type, resource_id,
                                    outcome, reason_code, auth_context, policy_version, created_date_time,
                                    modified_date_time)
            VALUES (:now, :actor, 'USER', :action, 'USER', :resource, :outcome, :reason, 'PLATFORM', '1.0',
                    :now, :now)
            """, new MapSqlParameterSource()
            .addValue("now", Timestamp.from(now)).addValue("actor", actorId).addValue("action", action)
            .addValue("resource", resourceId).addValue("outcome", outcome).addValue("reason", reasonCode));
    }

    private static String normalizePhone(String phone) { return phone.trim().replaceAll("[ -]", ""); }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }
}
