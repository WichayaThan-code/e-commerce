package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminDirectoryDtos.ShopOption;
import com.thaishopfun.admin.AdminDirectoryDtos.ShopRoleMember;
import com.thaishopfun.audit.PlatformAuditService;
import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminShopRoleAssignmentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PlatformAuditService platformAudit;

    public AdminShopRoleAssignmentService(NamedParameterJdbcTemplate jdbc, PlatformAuditService platformAudit) {
        this.jdbc = jdbc;
        this.platformAudit = platformAudit;
    }

    @Transactional(readOnly = true)
    public List<ShopOption> shops() {
        return jdbc.query("""
            SELECT shop_no, name, status
              FROM shops
             WHERE deleted_at IS NULL AND status IN ('ACTIVE', 'APPROVED', 'SUSPENDED')
             ORDER BY name, shop_no
            """, new MapSqlParameterSource(), (rs, row) ->
            new ShopOption(rs.getLong("shop_no"), rs.getString("name"), rs.getString("status")));
    }

    @Transactional(readOnly = true)
    public List<ShopRoleMember> members(long shopNo) {
        requireShop(shopNo);
        return jdbc.query("""
            SELECT u.id AS user_id, u.first_name, u.last_name, u.email, m.role, r.name_th AS role_name,
                   m.status, m.removed_at, m.joined_at
              FROM shop_members m
              JOIN shops s ON s.id = m.shop_id
              JOIN users u ON u.id = m.user_id
              LEFT JOIN auth_roles r ON r.code = m.role
             WHERE s.shop_no = :shopNo AND u.deleted_at IS NULL
             ORDER BY CASE WHEN m.status = 'ACTIVE' AND m.removed_at IS NULL THEN 0 ELSE 1 END,
                      u.first_name, u.last_name, u.email
            """, new MapSqlParameterSource("shopNo", shopNo), (rs, row) ->
            new ShopRoleMember(rs.getObject("user_id", UUID.class), rs.getString("first_name"), rs.getString("last_name"),
                rs.getString("email"), rs.getString("role"), rs.getString("role_name"),
                "ACTIVE".equals(rs.getString("status")) && rs.getTimestamp("removed_at") == null,
                rs.getTimestamp("joined_at") == null ? null : rs.getTimestamp("joined_at").toInstant()));
    }

    @Transactional
    public ShopRoleMember assign(long shopNo, UUID userId, String roleCode, UUID actorId) {
        if (userId.equals(actorId)) throw conflict("SELF_ADMIN_ACTION", "ไม่สามารถกำหนดบทบาทร้านค้าให้บัญชีที่กำลังใช้งานได้");
        var shop = requireShop(shopNo);
        if (!"ACTIVE".equals(shop.status) && !"APPROVED".equals(shop.status) && !"SUSPENDED".equals(shop.status)) {
            throw conflict("SHOP_NOT_ACTIVE", "กำหนดสมาชิกได้เฉพาะร้านที่อนุมัติแล้วหรือถูกระงับ");
        }
        var userExists = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM users WHERE id = :userId AND deleted_at IS NULL AND status = 'ACTIVE')
            """, new MapSqlParameterSource("userId", userId), Boolean.class));
        if (!userExists) throw new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ไม่พบบัญชีผู้ใช้ที่ใช้งานได้");
        if (shop.ownerUserId.equals(userId)) throw conflict("SHOP_OWNER_PROTECTED", "ไม่สามารถเปลี่ยนบทบาทของเจ้าของร้านได้");

        var role = jdbc.query("""
            SELECT context, is_assignable FROM auth_roles WHERE code = :code
            """, new MapSqlParameterSource("code", roleCode), (rs, row) ->
            new RoleAvailability(rs.getString("context"), rs.getBoolean("is_assignable")));
        if (role.isEmpty() || !"STORE".equals(role.get(0).context) || !role.get(0).assignable
            || "STORE_OWNER".equals(roleCode)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "ROLE_NOT_ASSIGNABLE", "เลือกได้เฉพาะบทบาทร้านค้าที่เปิดใช้งาน");
        }

        var existing = jdbc.query("""
            SELECT id, role, status, removed_at FROM shop_members
             WHERE shop_id = :shopId AND user_id = :userId
            """, new MapSqlParameterSource().addValue("shopId", shop.id).addValue("userId", userId),
            (rs, row) -> new ExistingMember(rs.getObject("id", UUID.class), rs.getString("role"),
                rs.getString("status"), rs.getTimestamp("removed_at")));
        var now = Timestamp.from(Instant.now());
        UUID memberId;
        if (existing.isEmpty()) {
            memberId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO shop_members (id, shop_id, user_id, role, status, joined_at,
                                          created_date_time, modified_date_time)
                VALUES (:id, :shopId, :userId, :role, 'ACTIVE', :now, :now, :now)
                """, new MapSqlParameterSource().addValue("id", memberId).addValue("shopId", shop.id)
                .addValue("userId", userId).addValue("role", roleCode).addValue("now", now));
        } else {
            var current = existing.get(0);
            memberId = current.id;
            if ("ACTIVE".equals(current.status) && current.removedAt == null && roleCode.equals(current.role)) {
                return member(shopNo, userId);
            }
            jdbc.update("""
                UPDATE shop_members
                   SET role = :role, status = 'ACTIVE',
                       joined_at = :now, removed_at = NULL, modified_date_time = :now
                 WHERE id = :id
                """, new MapSqlParameterSource().addValue("role", roleCode).addValue("now", now).addValue("id", memberId));
        }
        audit(actorId, memberId, "SHOP_MEMBER_ROLE_ASSIGNED", roleCode, now);
        return member(shopNo, userId);
    }

    @Transactional
    public void remove(long shopNo, UUID userId, UUID actorId) {
        if (userId.equals(actorId)) throw conflict("SELF_ADMIN_ACTION", "ไม่สามารถนำบัญชีที่กำลังใช้งานออกจากร้านได้");
        var shop = requireShop(shopNo);
        if (shop.ownerUserId.equals(userId)) throw conflict("SHOP_OWNER_PROTECTED", "ไม่สามารถนำเจ้าของร้านออกจากร้านได้");
        var rows = jdbc.query("""
            SELECT id, status, removed_at FROM shop_members WHERE shop_id = :shopId AND user_id = :userId
            """, new MapSqlParameterSource().addValue("shopId", shop.id).addValue("userId", userId),
            (rs, row) -> new ExistingMember(rs.getObject("id", UUID.class), null, rs.getString("status"), rs.getTimestamp("removed_at")));
        if (rows.isEmpty()) throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_MEMBER_NOT_FOUND", "ไม่พบสมาชิกของร้านนี้");
        var member = rows.get(0);
        if (!"ACTIVE".equals(member.status) || member.removedAt != null) return;
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
            UPDATE shop_members
               SET status = 'INACTIVE', removed_at = :now, modified_date_time = :now
             WHERE id = :id
            """, new MapSqlParameterSource().addValue("now", now).addValue("id", member.id));
        audit(actorId, member.id, "SHOP_MEMBER_REMOVED", "ADMIN_REMOVAL", now);
    }

    private ShopRoleMember member(long shopNo, UUID userId) {
        return jdbc.query("""
            SELECT u.id AS user_id, u.first_name, u.last_name, u.email, m.role, r.name_th AS role_name,
                   m.status, m.removed_at, m.joined_at
              FROM shop_members m
              JOIN shops s ON s.id = m.shop_id
              JOIN users u ON u.id = m.user_id
              LEFT JOIN auth_roles r ON r.code = m.role
             WHERE s.shop_no = :shopNo AND u.id = :userId
            """, new MapSqlParameterSource().addValue("shopNo", shopNo).addValue("userId", userId), rs -> {
            if (!rs.next()) throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_MEMBER_NOT_FOUND", "ไม่พบสมาชิกของร้านนี้");
            return new ShopRoleMember(rs.getObject("user_id", UUID.class), rs.getString("first_name"), rs.getString("last_name"),
                rs.getString("email"), rs.getString("role"), rs.getString("role_name"),
                "ACTIVE".equals(rs.getString("status")) && rs.getTimestamp("removed_at") == null,
                rs.getTimestamp("joined_at") == null ? null : rs.getTimestamp("joined_at").toInstant());
        });
    }

    private ShopRecord requireShop(long shopNo) {
        var rows = jdbc.query("""
            SELECT id, owner_user_id, status FROM shops WHERE shop_no = :shopNo AND deleted_at IS NULL
            """, new MapSqlParameterSource("shopNo", shopNo), (rs, row) ->
            new ShopRecord(rs.getObject("id", UUID.class), rs.getObject("owner_user_id", UUID.class), rs.getString("status")));
        if (rows.isEmpty()) throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า");
        return rows.get(0);
    }

    private void audit(UUID actorId, UUID memberId, String action, String reason, Timestamp now) {
        // same row as before (SHOP_MEMBER / PLATFORM / policy CUSTOM), now with IP + user agent through the central writer
        platformAudit.record(PlatformAuditService.Entry.platform(actorId, action, "SHOP_MEMBER", memberId).reason(reason, null).policy("CUSTOM"));
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    private record ShopRecord(UUID id, UUID ownerUserId, String status) {}
    private record RoleAvailability(String context, boolean assignable) {}
    private record ExistingMember(UUID id, String role, String status, java.sql.Timestamp removedAt) {}
}
