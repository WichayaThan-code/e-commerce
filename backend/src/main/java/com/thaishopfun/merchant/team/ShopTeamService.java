package com.thaishopfun.merchant.team;

import com.thaishopfun.audit.PlatformAuditService;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PermissionMatrix;
import com.thaishopfun.customer.ContactNormalizer;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopStatus;
import com.thaishopfun.merchant.notification.MerchantLifecycleNotificationWriter;
import com.thaishopfun.common.notification.NotificationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ShopTeamService {
    private static final Set<String> INVITE_ROLES = Set.of(
        "STORE_ADMIN", "STORE_MANAGER", "STORE_SALES", "STORE_WAREHOUSE", "STORE_VIEWER"
    );
    /** Permissions treated as sensitive in previews (finance / PII / export / team). */
    private static final Set<String> SENSITIVE_PERMISSIONS = Set.of(
        "CUSTOMER_PII_READ", "CUSTOMER_PII_EXPORT", "FINANCE_READ", "FINANCE_EXPORT", "FINANCE_WITHDRAW",
        "FINANCE_INVOICE_MANAGE", "USER_INVITE", "USER_ROLE_UPDATE", "USER_REMOVE", "ORDER_EXPORT", "REPORT_EXPORT"
    );
    private static final Set<String> OWNER_ONLY_ROLES = Set.of("STORE_ADMIN");

    private final JdbcTemplate jdbc;
    private final PermissionMatrix matrix;
    private final UserAccountRepository users;
    private final MerchantLifecycleNotificationWriter notifications;
    private final PlatformAuditService platformAudit;
    private final ShopTeamProperties properties;
    private final NotificationProperties notificationProperties;
    private final SecureRandom random = new SecureRandom();

    public ShopTeamService(
        JdbcTemplate jdbc,
        PermissionMatrix matrix,
        UserAccountRepository users,
        MerchantLifecycleNotificationWriter notifications,
        PlatformAuditService platformAudit,
        ShopTeamProperties properties,
        NotificationProperties notificationProperties
    ) {
        this.jdbc = jdbc;
        this.matrix = matrix;
        this.users = users;
        this.notifications = notifications;
        this.platformAudit = platformAudit;
        this.properties = properties;
        this.notificationProperties = notificationProperties;
    }

    @Transactional(readOnly = true)
    public ShopTeamDtos.MemberPage listMembers(Shop shop, String q, String role, String status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        var params = new ArrayList<Object>();
        var where = new StringBuilder(" WHERE m.shop_id = ? AND u.deleted_at IS NULL ");
        params.add(shop.getUuid());
        if (role != null && !role.isBlank()) {
            where.append(" AND m.role = ? ");
            params.add(role.trim().toUpperCase(Locale.ROOT));
        }
        if ("ACTIVE".equalsIgnoreCase(status)) {
            where.append(" AND m.status = 'ACTIVE' AND m.removed_at IS NULL ");
        } else if ("INACTIVE".equalsIgnoreCase(status)) {
            where.append(" AND (m.status <> 'ACTIVE' OR m.removed_at IS NOT NULL) ");
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (LOWER(u.email) LIKE ? OR LOWER(COALESCE(u.first_name,'')) LIKE ? OR LOWER(COALESCE(u.last_name,'')) LIKE ?) ");
            var like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            params.add(like); params.add(like); params.add(like);
        }
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shop_members m JOIN users u ON u.id = m.user_id " + where,
            Long.class, params.toArray()
        );
        long totalItems = total == null ? 0 : total;
        Long active = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM shop_members m JOIN users u ON u.id = m.user_id
             WHERE m.shop_id = ? AND u.deleted_at IS NULL AND m.status = 'ACTIVE' AND m.removed_at IS NULL
            """,
            Long.class, shop.getUuid()
        );
        var listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(safePage * safeSize);
        var items = jdbc.query(
            """
            SELECT u.id AS user_id, u.first_name, u.last_name, u.email, m.role, r.name_th AS role_name,
                   m.status, m.removed_at, m.joined_at
              FROM shop_members m
              JOIN users u ON u.id = m.user_id
              LEFT JOIN auth_roles r ON r.code = m.role
            """ + where + """
             ORDER BY CASE WHEN m.status = 'ACTIVE' AND m.removed_at IS NULL THEN 0 ELSE 1 END,
                      CASE WHEN m.joined_at IS NULL THEN 1 ELSE 0 END, m.joined_at DESC, u.email
             LIMIT ? OFFSET ?
            """,
            (rs, i) -> {
                boolean activeRow = "ACTIVE".equals(rs.getString("status")) && rs.getTimestamp("removed_at") == null;
                return new ShopTeamDtos.MemberSummary(
                    rs.getObject("user_id", UUID.class),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    rs.getString("role"),
                    rs.getString("role_name"),
                    activeRow ? "ACTIVE" : "INACTIVE",
                    rs.getTimestamp("joined_at") == null ? null : rs.getTimestamp("joined_at").toInstant(),
                    activeRow
                );
            },
            listParams.toArray()
        );
        int pages = safeSize == 0 ? 0 : (int) Math.ceil(totalItems / (double) safeSize);
        return new ShopTeamDtos.MemberPage(items, new ShopTeamDtos.PageMeta(safePage, safeSize, totalItems, pages), active == null ? 0 : active);
    }

    @Transactional(readOnly = true)
    public ShopTeamDtos.MemberDetail memberDetail(Shop shop, UUID userId) {
        var rows = jdbc.query(
            """
            SELECT u.id AS user_id, u.first_name, u.last_name, u.email, m.role, r.name_th AS role_name,
                   m.status, m.joined_at, m.removed_at, m.id AS member_id
              FROM shop_members m
              JOIN users u ON u.id = m.user_id
              LEFT JOIN auth_roles r ON r.code = m.role
             WHERE m.shop_id = ? AND m.user_id = ? AND u.deleted_at IS NULL
            """,
            (rs, i) -> Map.entry(
                new ShopTeamDtos.MemberDetail(
                    rs.getObject("user_id", UUID.class),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    rs.getString("role"),
                    rs.getString("role_name"),
                    "ACTIVE".equals(rs.getString("status")) && rs.getTimestamp("removed_at") == null ? "ACTIVE" : "INACTIVE",
                    rs.getTimestamp("joined_at") == null ? null : rs.getTimestamp("joined_at").toInstant(),
                    rs.getTimestamp("removed_at") == null ? null : rs.getTimestamp("removed_at").toInstant(),
                    List.copyOf(matrix.permissionsOf(rs.getString("role"))),
                    groupPermissions(matrix.permissionsOf(rs.getString("role"))),
                    List.of()
                ),
                rs.getObject("member_id", UUID.class)
            ),
            shop.getUuid(), userId
        );
        if (rows.isEmpty()) throw notFound("SHOP_MEMBER_NOT_FOUND", "ไม่พบสมาชิกของร้านนี้");
        var detail = rows.get(0).getKey();
        var memberId = rows.get(0).getValue();
        var events = jdbc.query(
            """
            SELECT action, reason_code, occurred_at
              FROM audit_logs
             WHERE resource_type = 'SHOP_MEMBER' AND resource_id = ?
             ORDER BY occurred_at DESC
             LIMIT 50
            """,
            (rs, i) -> new ShopTeamDtos.MemberEvent(
                rs.getString("action"),
                rs.getString("reason_code"),
                rs.getTimestamp("occurred_at") == null ? null : rs.getTimestamp("occurred_at").toInstant()
            ),
            memberId
        );
        return new ShopTeamDtos.MemberDetail(
            detail.userId(), detail.firstName(), detail.lastName(), detail.email(), detail.role(), detail.roleName(),
            detail.status(), detail.joinedAt(), detail.removedAt(), detail.permissions(), detail.permissionGroups(), events
        );
    }

    @Transactional(readOnly = true)
    public ShopTeamDtos.InvitationPage listInvitations(Shop shop, String q, String status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        // Do not UPDATE here — this method is readOnly. Lazy EXPIRED is applied via effectiveStatus().
        var nowTs = java.sql.Timestamp.from(Instant.now());
        var params = new ArrayList<Object>();
        var where = new StringBuilder(" WHERE i.shop_id = ? ");
        params.add(shop.getUuid());
        if (status != null && !status.isBlank()) {
            String st = status.trim().toUpperCase(Locale.ROOT);
            if ("PENDING".equals(st)) {
                where.append(" AND i.status = 'PENDING' AND i.expires_at > ? ");
                params.add(nowTs);
            } else if ("EXPIRED".equals(st)) {
                where.append(" AND (i.status = 'EXPIRED' OR (i.status = 'PENDING' AND i.expires_at <= ?)) ");
                params.add(nowTs);
            } else {
                where.append(" AND i.status = ? ");
                params.add(st);
            }
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND LOWER(i.email) LIKE ? ");
            params.add("%" + q.trim().toLowerCase(Locale.ROOT) + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM shop_invitations i " + where, Long.class, params.toArray());
        Long pending = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shop_invitations WHERE shop_id = ? AND status = 'PENDING' AND expires_at > ?",
            Long.class, shop.getUuid(), nowTs
        );
        var listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(safePage * safeSize);
        var items = jdbc.query(
            """
            SELECT i.id, i.email, i.role, r.name_th AS role_name, i.status, i.expires_at, i.created_date_time,
                   i.accepted_at, COALESCE(u.first_name || ' ' || u.last_name, u.email) AS invited_by_name
              FROM shop_invitations i
              LEFT JOIN auth_roles r ON r.code = i.role
              LEFT JOIN users u ON u.id = i.invited_by
            """ + where + """
             ORDER BY i.created_date_time DESC
             LIMIT ? OFFSET ?
            """,
            (rs, i) -> {
                Instant exp = rs.getTimestamp("expires_at").toInstant();
                String st = effectiveStatus(rs.getString("status"), exp);
                return new ShopTeamDtos.InvitationSummary(
                    rs.getObject("id", UUID.class),
                    rs.getString("email"),
                    rs.getString("role"),
                    rs.getString("role_name"),
                    st,
                    exp,
                    rs.getTimestamp("created_date_time").toInstant(),
                    rs.getTimestamp("accepted_at") == null ? null : rs.getTimestamp("accepted_at").toInstant(),
                    rs.getString("invited_by_name"),
                    "PENDING".equals(st)
                );
            },
            listParams.toArray()
        );
        int pages = (int) Math.ceil((total == null ? 0 : total) / (double) safeSize);
        return new ShopTeamDtos.InvitationPage(
            items,
            new ShopTeamDtos.PageMeta(safePage, safeSize, total == null ? 0 : total, pages),
            pending == null ? 0 : pending
        );
    }

    @Transactional(readOnly = true)
    public ShopTeamDtos.RoleCatalog roleCatalog(Shop shop, UUID actorId) {
        String actorRole = requireActorRole(shop.getUuid(), actorId);
        var options = new ArrayList<ShopTeamDtos.RoleOption>();
        for (String code : List.of("STORE_ADMIN", "STORE_MANAGER", "STORE_SALES", "STORE_WAREHOUSE", "STORE_VIEWER")) {
            var perms = matrix.permissionsOf(code);
            var names = roleNames(code);
            boolean assignable = canAssign(actorRole, code);
            options.add(new ShopTeamDtos.RoleOption(
                code, names[0], names[1], assignable, List.copyOf(perms), groupPermissions(perms), OWNER_ONLY_ROLES.contains(code)
            ));
        }
        return new ShopTeamDtos.RoleCatalog(options, actorRole, staffQuota(shop));
    }

    @Transactional
    public ShopTeamDtos.InvitationCreated createInvite(Shop shop, ShopTeamDtos.CreateInviteRequest body, UUID actorId) {
        String actorRole = requireActorRole(shop.getUuid(), actorId);
        String email = ContactNormalizer.email(body == null ? null : body.email());
        if (!ContactNormalizer.isValidEmail(email)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "อีเมลไม่ถูกต้อง", "email");
        }
        String role = body.role() == null ? "" : body.role().trim().toUpperCase(Locale.ROOT);
        if (!INVITE_ROLES.contains(role)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "ROLE_NOT_ASSIGNABLE", "บทบาทนี้เชิญไม่ได้", "role");
        }
        assertCanAssign(actorRole, role);
        assertShopOperable(shop);
        expireStale(shop.getUuid());
        assertStaffQuota(shop);

        var hourAgo = java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Integer recent = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shop_invitations WHERE shop_id = ? AND created_date_time > ?",
            Integer.class, shop.getUuid(), hourAgo
        );
        if (recent != null && recent >= properties.inviteCreatePerHour()) {
            throw conflict("INVITE_RATE_LIMITED", "สร้างคำเชิญถี่เกินไป กรุณารอสักครู่");
        }

        Long memberHits = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM shop_members m JOIN users u ON u.id = m.user_id
             WHERE m.shop_id = ? AND LOWER(u.email) = ? AND m.status = 'ACTIVE' AND m.removed_at IS NULL
            """,
            Long.class, shop.getUuid(), email
        );
        if (memberHits != null && memberHits > 0) {
            throw conflict("ALREADY_MEMBER", "อีเมลนี้เป็นสมาชิกร้านอยู่แล้ว");
        }
        var nowTs = java.sql.Timestamp.from(Instant.now());
        Long pendingHits = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shop_invitations WHERE shop_id = ? AND LOWER(email) = ? AND status = 'PENDING' AND expires_at > ?",
            Long.class, shop.getUuid(), email, nowTs
        );
        if (pendingHits != null && pendingHits > 0) {
            throw conflict("INVITE_PENDING_EXISTS", "มีคำเชิญที่รอการตอบรับสำหรับอีเมลนี้อยู่แล้ว");
        }

        var raw = newToken();
        var id = UUID.randomUUID();
        var expires = Instant.now().plus(properties.inviteTtlDays(), ChronoUnit.DAYS);
        var expiresTs = java.sql.Timestamp.from(expires);
        jdbc.update(
            """
            INSERT INTO shop_invitations
              (id, shop_id, email, role, token_hash, status, invited_by, expires_at, created_by, created_date_time, modified_date_time)
            VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)
            """,
            id, shop.getUuid(), email, role, hash(raw), actorId, expiresTs, actorId, nowTs, nowTs
        );
        return createdResponse(id, email, role, expires, raw, false);
    }

    @Transactional
    public ShopTeamDtos.InvitationCreated resendInvite(Shop shop, UUID invitationId, UUID actorId) {
        String actorRole = requireActorRole(shop.getUuid(), actorId);
        expireStale(shop.getUuid());
        var row = requirePendingInvite(shop.getUuid(), invitationId);
        assertCanAssign(actorRole, row.role());
        Instant lastTouch = row.modifiedAt() == null ? row.createdAt() : row.modifiedAt();
        if (lastTouch != null && lastTouch.plusSeconds(properties.resendCooldownSeconds()).isAfter(Instant.now())) {
            throw conflict("INVITE_RESEND_COOLDOWN", "รอสักครู่ก่อนส่งลิงก์ใหม่");
        }
        var raw = newToken();
        var expires = Instant.now().plus(properties.inviteTtlDays(), ChronoUnit.DAYS);
        var nowTs = java.sql.Timestamp.from(Instant.now());
        jdbc.update(
            """
            UPDATE shop_invitations
               SET token_hash = ?, expires_at = ?, modified_by = ?, modified_date_time = ?
             WHERE id = ? AND shop_id = ? AND status = 'PENDING'
            """,
            hash(raw), java.sql.Timestamp.from(expires), actorId, nowTs, invitationId, shop.getUuid()
        );
        return createdResponse(invitationId, row.email(), row.role(), expires, raw, false);
    }

    @Transactional
    public void revokeInvite(Shop shop, UUID invitationId, UUID actorId) {
        requireActorRole(shop.getUuid(), actorId);
        expireStale(shop.getUuid());
        var nowTs = java.sql.Timestamp.from(Instant.now());
        int updated = jdbc.update(
            """
            UPDATE shop_invitations
               SET status = 'REVOKED', revoked_at = ?, revoked_by = ?, modified_by = ?, modified_date_time = ?
             WHERE id = ? AND shop_id = ? AND status = 'PENDING'
            """,
            nowTs, actorId, actorId, nowTs, invitationId, shop.getUuid()
        );
        if (updated == 0) throw conflict("INVITE_NOT_PENDING", "คำเชิญนี้เพิกถอนไม่ได้แล้ว");
    }

    @Transactional
    public ShopTeamDtos.MemberSummary changeRole(Shop shop, UUID targetUserId, ShopTeamDtos.ChangeRoleRequest body, UUID actorId) {
        if (targetUserId.equals(actorId)) throw conflict("SELF_ROLE_CHANGE", "ไม่สามารถเปลี่ยนบทบาทของตนเองได้");
        String actorRole = requireActorRole(shop.getUuid(), actorId);
        String role = body == null || body.role() == null ? "" : body.role().trim().toUpperCase(Locale.ROOT);
        if (!INVITE_ROLES.contains(role)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "ROLE_NOT_ASSIGNABLE", "บทบาทนี้กำหนดไม่ได้", "role");
        }
        assertCanAssign(actorRole, role);
        assertShopOperable(shop);
        if (isShopOwner(shop, targetUserId)) {
            throw conflict("SHOP_OWNER_PROTECTED", "ไม่สามารถเปลี่ยนบทบาทของเจ้าของร้านได้");
        }
        var existing = jdbc.query(
            "SELECT id, role, status, removed_at FROM shop_members WHERE shop_id = ? AND user_id = ?",
            (rs, i) -> new MemberRow(rs.getObject("id", UUID.class), rs.getString("role"), rs.getString("status"), rs.getTimestamp("removed_at") == null ? null : rs.getTimestamp("removed_at").toInstant()),
            shop.getUuid(), targetUserId
        );
        if (existing.isEmpty() || !"ACTIVE".equals(existing.get(0).status()) || existing.get(0).removedAt() != null) {
            throw notFound("SHOP_MEMBER_NOT_FOUND", "ไม่พบสมาชิกที่ใช้งานอยู่");
        }
        var current = existing.get(0);
        if (role.equals(current.role())) return toSummary(shop, targetUserId);
        // Actor cannot change someone whose current role they could not assign (e.g. admin changing another admin).
        if (!canAssign(actorRole, current.role()) && !"STORE_OWNER".equals(actorRole)) {
            throw conflict("ROLE_ESCALATION_DENIED", "ไม่มีสิทธิ์เปลี่ยนบทบาทของสมาชิกที่มีอำนาจเท่าหรือสูงกว่า");
        }
        jdbc.update(
            """
            UPDATE shop_members
               SET role = ?, permission_version = permission_version + 1, modified_by = ?, modified_date_time = NOW()
             WHERE id = ?
            """,
            role, actorId, current.id()
        );
        writeAudit(actorId, shop.getUuid(), current.id(), "SHOP_MEMBER_ROLE_ASSIGNED", current.role() + "->" + role);
        notifications.publishShopMemberRoleChanged(shop.getUuid(), shop.getId(), current.id(), role, Instant.now());
        return toSummary(shop, targetUserId);
    }

    @Transactional
    public void removeMember(Shop shop, UUID targetUserId, ShopTeamDtos.RemoveMemberRequest body, UUID actorId) {
        if (targetUserId.equals(actorId)) throw conflict("SELF_REMOVE", "ไม่สามารถนำตนเองออกจากร้านได้");
        String actorRole = requireActorRole(shop.getUuid(), actorId);
        assertShopOperable(shop);
        if (isShopOwner(shop, targetUserId)) {
            throw conflict("SHOP_OWNER_PROTECTED", "ไม่สามารถนำเจ้าของร้านออกจากร้านได้");
        }
        var existing = jdbc.query(
            "SELECT id, role, status, removed_at FROM shop_members WHERE shop_id = ? AND user_id = ?",
            (rs, i) -> new MemberRow(rs.getObject("id", UUID.class), rs.getString("role"), rs.getString("status"), rs.getTimestamp("removed_at") == null ? null : rs.getTimestamp("removed_at").toInstant()),
            shop.getUuid(), targetUserId
        );
        if (existing.isEmpty()) throw notFound("SHOP_MEMBER_NOT_FOUND", "ไม่พบสมาชิกของร้านนี้");
        var current = existing.get(0);
        if (!"ACTIVE".equals(current.status()) || current.removedAt() != null) return;
        if ("STORE_ADMIN".equals(current.role()) && !"STORE_OWNER".equals(actorRole)) {
            throw conflict("ROLE_ESCALATION_DENIED", "มีเพียงเจ้าของร้านที่นำผู้ดูแลออกได้");
        }
        String reason = body == null || body.reason() == null || body.reason().isBlank() ? "MERCHANT_REMOVAL" : body.reason().trim();
        if (reason.length() > 50) reason = reason.substring(0, 50);
        jdbc.update(
            """
            UPDATE shop_members
               SET status = 'INACTIVE', removed_at = NOW(), removed_by = ?, remove_reason_code = ?,
                   permission_version = permission_version + 1, modified_by = ?, modified_date_time = NOW()
             WHERE id = ?
            """,
            actorId, reason, actorId, current.id()
        );
        writeAudit(actorId, shop.getUuid(), current.id(), "SHOP_MEMBER_REMOVED", reason);
        notifications.publishShopMemberRemoved(shop.getUuid(), shop.getId(), current.id(), Instant.now());
    }

    @Transactional(readOnly = true)
    public ShopTeamDtos.InvitePreview previewInvite(String rawToken, UUID callerId) {
        var row = findByToken(rawToken);
        if (row == null) throw notFound("INVITE_NOT_FOUND", "ไม่พบคำเชิญหรือลิงก์ไม่ถูกต้อง");
        Instant exp = row.expiresAt();
        String status = effectiveStatus(row.status(), exp);
        if ("PENDING".equals(row.status()) && exp.isBefore(Instant.now())) {
            // Lazy expire for display; write in separate tx optional — preview stays read-only.
            status = "EXPIRED";
        }
        var shopName = jdbc.query(
            "SELECT name FROM shops WHERE id = ?",
            (rs, i) -> rs.getString("name"),
            row.shopId()
        );
        var inviter = jdbc.query(
            "SELECT COALESCE(first_name || ' ' || last_name, email) AS name FROM users WHERE id = ?",
            (rs, i) -> rs.getString("name"),
            row.invitedBy()
        );
        var names = roleNames(row.role());
        var perms = matrix.permissionsOf(row.role());
        boolean authed = callerId != null;
        boolean verified = false;
        boolean emailMatch = false;
        String conflict = null;
        if (authed) {
            var user = users.findById(callerId).orElse(null);
            if (user == null || user.getDeletedAt() != null) {
                conflict = "USER_INACTIVE";
            } else {
                verified = user.getEmailVerifiedAt() != null;
                emailMatch = row.email().equalsIgnoreCase(user.getEmail());
                if (!verified) conflict = "EMAIL_NOT_VERIFIED";
                else if (!emailMatch) conflict = "EMAIL_MISMATCH";
                else if (!"PENDING".equals(status)) conflict = "INVITE_" + status;
                else {
                    Long hits = jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM shop_members
                         WHERE shop_id = ? AND user_id = ? AND status = 'ACTIVE' AND removed_at IS NULL
                        """,
                        Long.class, row.shopId(), callerId
                    );
                    if (hits != null && hits > 0) conflict = "ALREADY_MEMBER";
                }
            }
        }
        return new ShopTeamDtos.InvitePreview(
            shopName.isEmpty() ? "" : shopName.get(0),
            inviter.isEmpty() ? "" : inviter.get(0),
            row.role(),
            names[0],
            status,
            exp,
            List.copyOf(perms),
            groupPermissions(perms),
            emailMatch,
            authed,
            verified,
            conflict
        );
    }

    @Transactional
    public ShopTeamDtos.MemberSummary acceptInvite(String rawToken, UUID callerId) {
        if (callerId == null) throw new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "ต้องเข้าสู่ระบบก่อนยอมรับคำเชิญ");
        var row = findByToken(rawToken);
        if (row == null) throw notFound("INVITE_NOT_FOUND", "ไม่พบคำเชิญหรือลิงก์ไม่ถูกต้อง");
        if (!"PENDING".equals(row.status()) || row.expiresAt().isBefore(Instant.now())) {
            if ("PENDING".equals(row.status())) {
                jdbc.update(
                    "UPDATE shop_invitations SET status = 'EXPIRED', modified_date_time = ? WHERE id = ? AND status = 'PENDING'",
                    java.sql.Timestamp.from(Instant.now()), row.id()
                );
            }
            throw conflict("INVITE_" + effectiveStatus(row.status(), row.expiresAt()), "คำเชิญนี้ใช้ไม่ได้แล้ว");
        }
        var user = users.findById(callerId).orElseThrow(() -> notFound("USER_NOT_FOUND", "ไม่พบบัญชีผู้ใช้"));
        if (user.getDeletedAt() != null || !"ACTIVE".equalsIgnoreCase(String.valueOf(user.getStatus()))) {
            throw conflict("USER_INACTIVE", "บัญชีนี้ใช้งานไม่ได้");
        }
        if (user.getEmailVerifiedAt() == null) throw conflict("EMAIL_NOT_VERIFIED", "ต้องยืนยันอีเมลก่อนยอมรับคำเชิญ");
        if (!row.email().equalsIgnoreCase(user.getEmail())) {
            throw conflict("EMAIL_MISMATCH", "อีเมลที่เข้าสู่ระบบไม่ตรงกับผู้รับเชิญ");
        }
        // Re-check inviter still may assign this role (owner/admin policy at accept time).
        String inviterRole = actorRoleOrNull(row.shopId(), row.invitedBy());
        if (inviterRole == null || !canAssign(inviterRole, row.role())) {
            throw conflict("INVITE_AUTHORITY_CHANGED", "ผู้เชิญไม่มีอำนาจมอบบทบาทนี้แล้ว กรุณาขอคำเชิญใหม่");
        }
        var shopRows = jdbc.query(
            "SELECT shop_no, name, status, owner_user_id FROM shops WHERE id = ? AND deleted_at IS NULL",
            (rs, i) -> new ShopSnap(rs.getLong("shop_no"), rs.getString("name"), rs.getString("status"), rs.getObject("owner_user_id", UUID.class)),
            row.shopId()
        );
        if (shopRows.isEmpty()) throw notFound("SHOP_NOT_FOUND", "ไม่พบร้านค้า");
        var shopSnap = shopRows.get(0);
        // V1 stores operating shops as ACTIVE (JPA maps APPROVED <-> ACTIVE via ShopStatusConverter).
        if (!"ACTIVE".equals(shopSnap.status()) && !"APPROVED".equals(shopSnap.status()) && !"SUSPENDED".equals(shopSnap.status())) {
            throw conflict("SHOP_NOT_ACTIVE", "ร้านยังไม่พร้อมรับสมาชิก");
        }
        if (callerId.equals(shopSnap.ownerUserId())) {
            throw conflict("SHOP_OWNER_PROTECTED", "เจ้าของร้านไม่ต้องยอมรับคำเชิญ");
        }

        var existing = jdbc.query(
            "SELECT id, role, status, removed_at FROM shop_members WHERE shop_id = ? AND user_id = ?",
            (rs, i) -> new MemberRow(rs.getObject("id", UUID.class), rs.getString("role"), rs.getString("status"), rs.getTimestamp("removed_at") == null ? null : rs.getTimestamp("removed_at").toInstant()),
            row.shopId(), callerId
        );
        UUID memberId;
        var nowTs = java.sql.Timestamp.from(Instant.now());
        if (existing.isEmpty()) {
            memberId = UUID.randomUUID();
            jdbc.update(
                """
                INSERT INTO shop_members
                  (id, shop_id, user_id, role, status, invited_by, joined_at, created_by, created_date_time, modified_date_time, permission_version)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, 0)
                """,
                memberId, row.shopId(), callerId, row.role(), row.invitedBy(), nowTs, callerId, nowTs, nowTs
            );
        } else {
            var cur = existing.get(0);
            if ("ACTIVE".equals(cur.status()) && cur.removedAt() == null) {
                throw conflict("ALREADY_MEMBER", "คุณเป็นสมาชิกร้านนี้อยู่แล้ว");
            }
            memberId = cur.id();
            jdbc.update(
                """
                UPDATE shop_members
                   SET role = ?, status = 'ACTIVE', removed_at = NULL, removed_by = NULL, remove_reason_code = NULL,
                       invited_by = ?, joined_at = ?, permission_version = permission_version + 1,
                       modified_by = ?, modified_date_time = ?
                 WHERE id = ?
                """,
                row.role(), row.invitedBy(), nowTs, callerId, nowTs, memberId
            );
        }
        int claimed = jdbc.update(
            """
            UPDATE shop_invitations
               SET status = 'ACCEPTED', accepted_at = ?, accepted_user_id = ?, modified_by = ?, modified_date_time = ?
             WHERE id = ? AND status = 'PENDING' AND expires_at > ?
            """,
            nowTs, callerId, callerId, nowTs, row.id(), nowTs
        );
        if (claimed == 0) throw conflict("INVITE_ALREADY_USED", "คำเชิญนี้ถูกใช้หรือเพิกถอนแล้ว");
        writeAudit(callerId, row.shopId(), memberId, "SHOP_MEMBER_ROLE_ASSIGNED", "INVITE_ACCEPTED:" + row.role());
        notifications.publishShopMemberRoleChanged(row.shopId(), shopSnap.shopNo(), memberId, row.role(), Instant.now());
        return toSummaryByShopId(row.shopId(), callerId);
    }

    private ShopTeamDtos.InvitationCreated createdResponse(
        UUID id, String email, String role, Instant expires, String rawToken, boolean emailQueued
    ) {
        String base = notificationProperties.frontendUrl() == null ? "http://localhost:5173" : notificationProperties.frontendUrl().replaceAll("/$", "");
        String path = properties.invitePath().startsWith("/") ? properties.invitePath() : "/" + properties.invitePath();
        String url = base + path + "/" + rawToken;
        return new ShopTeamDtos.InvitationCreated(
            id, email, role, "PENDING", expires, url, emailQueued,
            emailQueued ? "queued" : "ลิงก์พร้อมแล้ว ยังไม่ได้ส่งอีเมล — คัดลอกลิงก์ให้ผู้รับด้วยตนเอง"
        );
    }

    private void expireStale(UUID shopId) {
        var nowTs = java.sql.Timestamp.from(Instant.now());
        jdbc.update(
            "UPDATE shop_invitations SET status = 'EXPIRED', modified_date_time = ? WHERE shop_id = ? AND status = 'PENDING' AND expires_at <= ?",
            nowTs, shopId, nowTs
        );
    }

    private InviteRow requirePendingInvite(UUID shopId, UUID id) {
        var rows = jdbc.query(
            """
            SELECT id, email, role, status, expires_at, invited_by, created_date_time, modified_date_time, shop_id, token_hash
              FROM shop_invitations WHERE id = ? AND shop_id = ?
            """,
            (rs, i) -> mapInvite(rs),
            id, shopId
        );
        if (rows.isEmpty()) throw notFound("INVITE_NOT_FOUND", "ไม่พบคำเชิญ");
        var row = rows.get(0);
        String st = effectiveStatus(row.status(), row.expiresAt());
        if (!"PENDING".equals(st)) throw conflict("INVITE_NOT_PENDING", "คำเชิญนี้ไม่ใช่สถานะรอตอบรับ");
        return row;
    }

    private InviteRow findByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() < 20) return null;
        var rows = jdbc.query(
            """
            SELECT id, email, role, status, expires_at, invited_by, created_date_time, modified_date_time, shop_id, token_hash
              FROM shop_invitations WHERE token_hash = ?
            """,
            (rs, i) -> mapInvite(rs),
            hash(rawToken.trim())
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private InviteRow mapInvite(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new InviteRow(
            rs.getObject("id", UUID.class),
            rs.getObject("shop_id", UUID.class),
            rs.getString("email"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getObject("invited_by", UUID.class),
            rs.getTimestamp("created_date_time") == null ? null : rs.getTimestamp("created_date_time").toInstant(),
            rs.getTimestamp("modified_date_time") == null ? null : rs.getTimestamp("modified_date_time").toInstant()
        );
    }

    private String requireActorRole(UUID shopId, UUID actorId) {
        String role = actorRoleOrNull(shopId, actorId);
        if (role == null) throw new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "ไม่มีสิทธิ์ในร้านนี้");
        return role;
    }

    private String actorRoleOrNull(UUID shopId, UUID actorId) {
        var rows = jdbc.query(
            """
            SELECT role FROM shop_members
             WHERE shop_id = ? AND user_id = ? AND status = 'ACTIVE' AND removed_at IS NULL
            """,
            (rs, i) -> rs.getString("role"),
            shopId, actorId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean canAssign(String actorRole, String targetRole) {
        if (!INVITE_ROLES.contains(targetRole)) return false;
        if ("STORE_OWNER".equals(actorRole)) return true;
        if ("STORE_ADMIN".equals(actorRole)) return !OWNER_ONLY_ROLES.contains(targetRole);
        return false;
    }

    private void assertCanAssign(String actorRole, String targetRole) {
        if (!canAssign(actorRole, targetRole)) {
            throw conflict("ROLE_ESCALATION_DENIED", "ไม่สามารถมอบบทบาทที่สูงกว่าหรือเท่ากับอำนาจของตนเองได้");
        }
    }

    private void assertShopOperable(Shop shop) {
        ShopStatus st = shop.getStatus();
        if (st != ShopStatus.APPROVED && st != ShopStatus.SUSPENDED) {
            throw conflict("SHOP_NOT_ACTIVE", "ร้านยังไม่พร้อมจัดการสมาชิก");
        }
    }

    private ShopTeamDtos.StaffQuota staffQuota(Shop shop) {
        long used = countStaffSlots(shop.getUuid());
        String planCode = shop.getPlanCode();
        if (planCode == null || planCode.isBlank()) {
            return new ShopTeamDtos.StaffQuota(
                null, used, false,
                "ร้านยังไม่มี plan_code — ไม่บังคับโควตาสมาชิกจากตาราง plans (ไม่ตีความว่า unlimited)"
            );
        }
        Long limit = jdbc.query(
            "SELECT limit_staff FROM plans WHERE code = ? AND active = TRUE",
            (rs, i) -> {
                Object v = rs.getObject("limit_staff");
                return v == null ? null : rs.getLong("limit_staff");
            },
            planCode
        ).stream().findFirst().orElse(null);
        if (limit == null) {
            return new ShopTeamDtos.StaffQuota(
                null, used, false,
                "แพ็กเกจ " + planCode + " ไม่กำหนด limit_staff หรือไม่พบแพ็กเกจ — ไม่บังคับโควตา"
            );
        }
        return new ShopTeamDtos.StaffQuota(limit, used, true, null);
    }

    private void assertStaffQuota(Shop shop) {
        var quota = staffQuota(shop);
        if (!quota.quotaEnforced() || quota.staffLimit() == null) return;
        if (quota.staffUsed() >= quota.staffLimit()) {
            throw conflict("MEMBER_QUOTA_EXCEEDED", "จำนวนสมาชิกและคำเชิญถึงโควตาแพ็กเกจแล้ว (" + quota.staffUsed() + "/" + quota.staffLimit() + ")");
        }
    }

    private long countStaffSlots(UUID shopId) {
        Long active = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shop_members WHERE shop_id = ? AND status = 'ACTIVE' AND removed_at IS NULL",
            Long.class, shopId
        );
        Long pending = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shop_invitations WHERE shop_id = ? AND status = 'PENDING' AND expires_at > ?",
            Long.class, shopId, java.sql.Timestamp.from(Instant.now())
        );
        return (active == null ? 0 : active) + (pending == null ? 0 : pending);
    }

    private static boolean isShopOwner(Shop shop, UUID userId) {
        return shop.getUser() != null && userId.equals(shop.getUser().getId());
    }

    private ShopTeamDtos.MemberSummary toSummary(Shop shop, UUID userId) {
        return toSummaryByShopId(shop.getUuid(), userId);
    }

    private ShopTeamDtos.MemberSummary toSummaryByShopId(UUID shopId, UUID userId) {
        var rows = jdbc.query(
            """
            SELECT u.id AS user_id, u.first_name, u.last_name, u.email, m.role, r.name_th AS role_name,
                   m.status, m.removed_at, m.joined_at
              FROM shop_members m
              JOIN users u ON u.id = m.user_id
              LEFT JOIN auth_roles r ON r.code = m.role
             WHERE m.shop_id = ? AND m.user_id = ?
            """,
            (rs, i) -> {
                boolean active = "ACTIVE".equals(rs.getString("status")) && rs.getTimestamp("removed_at") == null;
                return new ShopTeamDtos.MemberSummary(
                    rs.getObject("user_id", UUID.class),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    rs.getString("role"),
                    rs.getString("role_name"),
                    active ? "ACTIVE" : "INACTIVE",
                    rs.getTimestamp("joined_at") == null ? null : rs.getTimestamp("joined_at").toInstant(),
                    active
                );
            },
            shopId, userId
        );
        if (rows.isEmpty()) throw notFound("SHOP_MEMBER_NOT_FOUND", "ไม่พบสมาชิกของร้านนี้");
        return rows.get(0);
    }

    private void writeAudit(UUID actorId, UUID shopId, UUID memberId, String action, String reason) {
        platformAudit.record(
            PlatformAuditService.Entry.platform(actorId, action, "SHOP_MEMBER", memberId)
                .shop(shopId)
                .reason(reason, null)
                .policy("2.0")
        );
    }

    private List<ShopTeamDtos.PermissionGroup> groupPermissions(Set<String> permissions) {
        var grouped = new LinkedHashMap<String, List<ShopTeamDtos.PermissionItem>>();
        permissions.stream().sorted().forEach(code -> {
            String module = code.contains("_") ? code.substring(0, code.indexOf('_')) : "OTHER";
            grouped.computeIfAbsent(module, k -> new ArrayList<>()).add(
                new ShopTeamDtos.PermissionItem(code, code, SENSITIVE_PERMISSIONS.contains(code))
            );
        });
        return grouped.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .map(e -> new ShopTeamDtos.PermissionGroup(e.getKey(), e.getValue()))
            .toList();
    }

    private String[] roleNames(String code) {
        // auth_roles has name_th only in the catalog mapping (no name_en column).
        var rows = jdbc.query(
            "SELECT name_th FROM auth_roles WHERE code = ?",
            (rs, i) -> {
                String th = rs.getString("name_th");
                return new String[]{th == null || th.isBlank() ? code : th, code};
            },
            code
        );
        return rows.isEmpty() ? new String[]{code, code} : rows.get(0);
    }

    private static String effectiveStatus(String status, Instant expiresAt) {
        if ("PENDING".equals(status) && expiresAt != null && !expiresAt.isAfter(Instant.now())) return "EXPIRED";
        return status;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    private static AuthException notFound(String code, String message) {
        return new AuthException(HttpStatus.NOT_FOUND, code, message);
    }

    private record InviteRow(
        UUID id, UUID shopId, String email, String role, String status, Instant expiresAt,
        UUID invitedBy, Instant createdAt, Instant modifiedAt
    ) {}
    private record MemberRow(UUID id, String role, String status, Instant removedAt) {}
    private record ShopSnap(long shopNo, String name, String status, UUID ownerUserId) {}
}
