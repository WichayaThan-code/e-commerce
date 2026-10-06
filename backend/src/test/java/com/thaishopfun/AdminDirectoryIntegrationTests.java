package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import com.thaishopfun.merchant.OwnerAuditLogRepository;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ShopMemberRepository;
import com.thaishopfun.merchant.ShopReinstatementRequestRepository;
import com.thaishopfun.merchant.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Admin users / roles &amp; permissions read APIs: every number is checked against the database tables directly.
 * Role catalog and mappings come from the real seed (ReferenceSeedTestLoader).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDirectoryIntegrationTests {
    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository users;
    @Autowired PlatformRoleRepository platformRoles;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordResetTokenRepository passwordResetTokens;
    @Autowired OtpVerificationRepository otpVerifications;
    @Autowired MerchantApplicationRepository applications;
    @Autowired MerchantDocumentRepository documents;
    @Autowired ShopReinstatementRequestRepository reinstatements;
    @Autowired ShopMemberRepository shopMembers;
    @Autowired ShopRepository shops;
    @Autowired OwnerAuditLogRepository ownerAudits;
    @Autowired OwnerRepository owners;

    @BeforeEach void clean() {
        platformRoles.deleteAll();
        reinstatements.deleteAll();
        shopMembers.deleteAll();
        shops.deleteAll();
        documents.deleteAll();
        applications.deleteAll();
        ownerAudits.deleteAll();
        owners.deleteAll();
        otpVerifications.deleteAll();
        passwordResetTokens.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    // ---------------------------------------------------------------- access rules

    @Test void onlyPlatformAccountReadersReachTheDirectory() throws Exception {
        var buyer = register("plain@example.com", "0820000001", "ธรรมดา", "ผู้ใช้");
        var admin = platformAdmin("dir-admin@example.com", "0820000002");
        var adminId = admin.id;
        var endpoints = List.of("/api/admin/users", "/api/admin/users/stats", "/api/admin/users/filter-options", "/api/admin/users/export",
            "/api/admin/users/" + adminId, "/api/admin/users/" + adminId + "/history", "/api/admin/roles", "/api/admin/roles/STORE_OWNER", "/api/admin/roles/activity");
        for (var path : endpoints) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", bearer(buyer.token)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
            mvc.perform(get(path).header("Authorization", bearer(admin.token))).andExpect(status().isOk());
        }
        // a shop owner (store permissions only) is still not a platform account reader
        var owner = register("dir-owner@example.com", "0820000003", "เจ้าของ", "ร้าน");
        openShop(owner, "ร้านไดเรกทอรี", "Directory Shop", "2000000000001");
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(owner.token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/00000000-0000-0000-0000-000000000000").header("Authorization", bearer(admin.token)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- list: pagination, sorting, filters

    @Test void userListIsPaginatedAndSortedOnTheServer() throws Exception {
        var admin = platformAdmin("page-admin@example.com", "0820000010");
        for (int i = 1; i <= 11; i++) register("page%02d@example.com".formatted(i), "08300000%02d".formatted(i), "หน้า" + i, "ทดสอบ");
        // deterministic sign-up times: pageNN registered NN days ago
        for (int i = 1; i <= 11; i++) {
            jdbc.update("UPDATE users SET created_date_time = ? WHERE email = ?", Timestamp.from(Instant.now().minusSeconds(86_400L * i)), "page%02d@example.com".formatted(i));
        }
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL", Integer.class);
        assertEquals(12, total);

        var seen = new HashSet<String>();
        var emails = new ArrayList<String>();
        for (int page = 0; page < 3; page++) {
            var body = mvc.perform(get("/api/admin/users?size=5&page=" + page).header("Authorization", bearer(admin.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(12)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(page)).andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.items.length()").value(page < 2 ? 5 : 2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            List<String> pageEmails = JsonPath.read(body, "$.items[*].email");
            emails.addAll(pageEmails);
            pageEmails.forEach(email -> assertTrue(seen.add(email), "row repeated across pages: " + email));
        }
        // default sort: newest sign-up first (the admin registered "now", then page01 ... page11)
        assertEquals("page-admin@example.com", emails.get(0));
        assertEquals("page01@example.com", emails.get(1));
        assertEquals("page11@example.com", emails.get(11));
        mvc.perform(get("/api/admin/users?size=3&sort=createdAt&dir=asc").header("Authorization", bearer(admin.token)))
            .andExpect(jsonPath("$.items[0].email").value("page11@example.com"));
        mvc.perform(get("/api/admin/users?size=3&sort=email&dir=asc").header("Authorization", bearer(admin.token)))
            .andExpect(jsonPath("$.items[0].email").value("page-admin@example.com"));
        // page size is capped, beyond the last page is empty
        mvc.perform(get("/api/admin/users?size=100000").header("Authorization", bearer(admin.token)))
            .andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.items.length()").value(12));
        mvc.perform(get("/api/admin/users?size=5&page=9").header("Authorization", bearer(admin.token)))
            .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.totalItems").value(12));
    }

    @Test void userListFiltersOnTheServer() throws Exception {
        var admin = platformAdmin("filter-admin@example.com", "0820000020");
        var owner = register("somchai.owner@example.com", "0812345678", "สมชาย", "ใจดี");
        long shopId = openShop(owner, "ร้านสมชาย", "Somchai Shop", "2000000000002");
        var buyer = register("wichai@example.com", "0964447788", "วิชัย", "กล้าหาญ");
        var suspended = register("pracha@example.com", "0897654321", "ประชา", "มีทรัพย์");
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.id);
        var old = register("old@example.com", "0801112222", "เก่า", "แก่");
        jdbc.update("UPDATE users SET created_date_time = ? WHERE id = ?", Timestamp.from(LocalDate.of(2025, 3, 15).atTime(10, 0).atZone(BANGKOK).toInstant()), old.id);
        var as = bearer(admin.token);

        // free text: name, email, phone (with or without dashes) and shop name
        expectEmails(as, "q=สมชาย", "somchai.owner@example.com");
        expectEmails(as, "q=WICHAI@", "wichai@example.com");
        expectEmails(as, "q=081-234-5678", "somchai.owner@example.com");
        expectEmails(as, "q=0964447788", "wichai@example.com");
        expectEmails(as, "q=Somchai Shop", "somchai.owner@example.com");
        expectEmails(as, "q=_", new String[0]); // LIKE wildcards in the search text are literal
        // role (from platform_roles / shop_members), status and sign-up date range
        expectEmails(as, "role=PLATFORM_ADMIN", "filter-admin@example.com");
        expectEmails(as, "role=STORE_OWNER", "somchai.owner@example.com");
        expectEmails(as, "role=STORE_VIEWER", new String[0]);
        expectEmails(as, "role=GENERAL&sort=email&dir=asc", "old@example.com", "pracha@example.com", "wichai@example.com");
        expectEmails(as, "status=SUSPENDED", "pracha@example.com");
        expectEmails(as, "from=2025-03-15&to=2025-03-15", "old@example.com");
        expectEmails(as, "to=2025-12-31", "old@example.com");
        expectEmails(as, "role=GENERAL&status=ACTIVE&from=2026-01-01&sort=email&dir=asc", "wichai@example.com");
        // rows carry the derived role and shops
        mvc.perform(get("/api/admin/users?role=STORE_OWNER").header("Authorization", as))
            .andExpect(jsonPath("$.items[0].primaryRole.code").value("STORE_OWNER"))
            .andExpect(jsonPath("$.items[0].primaryRole.nameTh").value("เจ้าของร้าน"))
            .andExpect(jsonPath("$.items[0].shops[0].shopId").value(shopId))
            .andExpect(jsonPath("$.items[0].shops[0].shopName").value("ร้านสมชาย"))
            .andExpect(jsonPath("$.items[0].shops[0].shopStatus").value("PENDING_REVIEW"));
        mvc.perform(get("/api/admin/users?role=PLATFORM_ADMIN").header("Authorization", as))
            .andExpect(jsonPath("$.items[0].primaryRole.code").value("PLATFORM_ADMIN")).andExpect(jsonPath("$.items[0].superAdmin").value(true));
        mvc.perform(get("/api/admin/users?q=wichai").header("Authorization", as))
            .andExpect(jsonPath("$.items[0].primaryRole.code").value("BUYER")).andExpect(jsonPath("$.items[0].shops.length()").value(0));
        // invalid filters are rejected, not silently ignored
        mvc.perform(get("/api/admin/users?role=NOPE").header("Authorization", as)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FILTER"));
        mvc.perform(get("/api/admin/users?role=BUYER").header("Authorization", as)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users?status=DELETED").header("Authorization", as)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users?from=2026-02-01&to=2026-01-01").header("Authorization", as)).andExpect(status().isBadRequest());
        // soft-deleted accounts never appear
        jdbc.update("UPDATE users SET deleted_at = ? WHERE id = ?", Timestamp.from(Instant.now()), buyer.id);
        expectEmails(as, "q=wichai", new String[0]);
        // filter options come from auth_roles with live counts
        mvc.perform(get("/api/admin/users/filter-options").header("Authorization", as))
            .andExpect(jsonPath("$.roles[?(@.value=='STORE_OWNER')].count").value(hasItem(1)))
            .andExpect(jsonPath("$.roles[?(@.value=='PLATFORM_ADMIN')].count").value(hasItem(1)))
            .andExpect(jsonPath("$.roles[?(@.value=='GENERAL')].count").value(hasItem(2)))
            .andExpect(jsonPath("$.roles[?(@.value=='BUYER')]").isEmpty())
            .andExpect(jsonPath("$.statuses[?(@.value=='SUSPENDED')].count").value(hasItem(1)))
            .andExpect(jsonPath("$.statuses[?(@.value=='ACTIVE')].count").value(hasItem(3)));
        // export = the same filtered rows as CSV
        var csv = mvc.perform(get("/api/admin/users/export?role=STORE_OWNER").header("Authorization", as))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("users.csv")))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertEquals(2, csv.strip().split("\r\n").length, csv);
        assertTrue(csv.contains("somchai.owner@example.com") && csv.contains("ร้านสมชาย") && csv.contains("เจ้าของร้าน"));
    }

    // ---------------------------------------------------------------- stats

    @Test void statsMatchTheDatabase() throws Exception {
        var admin = platformAdmin("stats-admin@example.com", "0820000030");
        var owner = register("stats-owner@example.com", "0820000031", "เจ้าของ", "สถิติ");
        openShop(owner, "ร้านสถิติ", "Stats Shop", "2000000000003");
        var viewer = register("stats-viewer@example.com", "0820000032", "ผู้ดู", "สถิติ");
        addMember(owner, viewer.id, "STORE_VIEWER");
        register("stats-buyer1@example.com", "0820000033", "ผู้ซื้อ", "หนึ่ง");
        var suspended = register("stats-buyer2@example.com", "0820000034", "ผู้ซื้อ", "สอง");
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.id);

        // nothing existed before this month: no baseline -> changePercent null (UI shows '-')
        mvc.perform(get("/api/admin/users/stats").header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total.value").value(5)).andExpect(jsonPath("$.total.previous").value(0))
            .andExpect(jsonPath("$.total.changePercent").value(nullValue()))
            .andExpect(jsonPath("$.admins.value").value(1))
            .andExpect(jsonPath("$.storeOwners.value").value(1))
            .andExpect(jsonPath("$.generalUsers.value").value(2)) // viewer is a shop member, admin and owner are not general
            .andExpect(jsonPath("$.active").value(4)).andExpect(jsonPath("$.suspended").value(1))
            .andExpect(jsonPath("$.roleCount").value(jdbc.queryForObject("SELECT COUNT(*) FROM auth_roles", Integer.class)));

        // back-date two accounts (and the admin grant) to before this month -> real month-over-month change
        var monthStart = LocalDate.now(BANGKOK).withDayOfMonth(1).atStartOfDay(BANGKOK).toInstant();
        var lastMonth = Timestamp.from(monthStart.minusSeconds(86_400 * 3));
        jdbc.update("UPDATE users SET created_date_time = ? WHERE email IN ('stats-admin@example.com', 'stats-buyer1@example.com')", lastMonth);
        jdbc.update("UPDATE platform_roles SET granted_at = ? WHERE user_id = ?", lastMonth, admin.id);
        mvc.perform(get("/api/admin/users/stats").header("Authorization", bearer(admin.token)))
            .andExpect(jsonPath("$.total.value").value(5)).andExpect(jsonPath("$.total.previous").value(2))
            .andExpect(jsonPath("$.total.changePercent").value(150.0))
            .andExpect(jsonPath("$.admins.previous").value(1)).andExpect(jsonPath("$.admins.changePercent").value(0.0))
            .andExpect(jsonPath("$.storeOwners.previous").value(0)).andExpect(jsonPath("$.storeOwners.changePercent").value(nullValue()))
            .andExpect(jsonPath("$.generalUsers.previous").value(1)).andExpect(jsonPath("$.generalUsers.changePercent").value(100.0));
    }

    // ---------------------------------------------------------------- roles

    @Test void roleListAndCountsMatchTheDatabase() throws Exception {
        var admin = platformAdmin("roles-admin@example.com", "0820000040");
        var owner = register("roles-owner@example.com", "0820000041", "เจ้าของ", "บทบาท");
        openShop(owner, "ร้านบทบาท", "Roles Shop", "2000000000004");
        var viewer = register("roles-viewer@example.com", "0820000042", "ผู้ดู", "บทบาท");
        addMember(owner, viewer.id, "STORE_VIEWER");
        var suspended = register("roles-suspended@example.com", "0820000043", "ระงับ", "บทบาท");
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.id);

        var body = mvc.perform(get("/api/admin/roles").header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> roles = JsonPath.read(body, "$");
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM auth_roles", Integer.class), roles.size());
        assertEquals(10, roles.size());
        var activeUsers = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE status = 'ACTIVE' AND deleted_at IS NULL", Long.class);
        for (var role : roles) {
            var code = (String) role.get("code");
            var mapped = jdbc.queryForObject("""
                SELECT COUNT(*) FROM auth_role_permissions rp JOIN auth_roles r ON r.id = rp.role_id JOIN auth_permissions p ON p.id = rp.permission_id
                 WHERE r.code = ? AND p.is_active = TRUE""", Integer.class, code);
            assertEquals(mapped, ((Number) role.get("permissionCount")).intValue(), "permissions of " + code);
            assertEquals(jdbc.queryForObject("SELECT name_th FROM auth_roles WHERE code = ?", String.class, code), role.get("nameTh"));
            var context = (String) role.get("context");
            Long expected = switch (context) {
                case "PLATFORM" -> jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM platform_roles WHERE role = ? AND revoked_at IS NULL", Long.class, code);
                case "STORE" -> jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM shop_members WHERE role = ? AND status = 'ACTIVE' AND removed_at IS NULL", Long.class, code);
                case "ACCOUNT", "APPLICANT" -> activeUsers;
                default -> null;
            };
            assertEquals(expected, role.get("userCount") == null ? null : ((Number) role.get("userCount")).longValue(), "users of " + code);
        }
        mvc.perform(get("/api/admin/roles").header("Authorization", bearer(admin.token)))
            .andExpect(jsonPath("$[?(@.code=='PLATFORM_ADMIN')].userCount").value(hasItem(1)))
            .andExpect(jsonPath("$[?(@.code=='PLATFORM_ADMIN')].superAdmin").value(hasItem(true)))
            .andExpect(jsonPath("$[?(@.code=='STORE_OWNER')].userCount").value(hasItem(1)))
            .andExpect(jsonPath("$[?(@.code=='STORE_VIEWER')].userCount").value(hasItem(1)))
            .andExpect(jsonPath("$[?(@.code=='STORE_MANAGER')].userCount").value(hasItem(0)))
            .andExpect(jsonPath("$[?(@.code=='BUYER')].userCount").value(hasItem(3)))
            .andExpect(jsonPath("$[?(@.code=='GUEST')].assignment").value(hasItem("PUBLIC")))
            .andExpect(jsonPath("$[0].code").value("GUEST"));

        // role detail: the mapped permissions grouped by module, with the catalog descriptions
        var detail = mvc.perform(get("/api/admin/roles/STORE_VIEWER").header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role.code").value("STORE_VIEWER"))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> codes = JsonPath.read(detail, "$.groups[*].permissions[*].code");
        List<String> expected = jdbc.queryForList("""
            SELECT p.code FROM auth_role_permissions rp JOIN auth_roles r ON r.id = rp.role_id JOIN auth_permissions p ON p.id = rp.permission_id
             WHERE r.code = 'STORE_VIEWER' ORDER BY p.module, p.code""", String.class);
        assertEquals(expected, codes);
        assertTrue(codes.contains("SHOP_READ"));
        mvc.perform(get("/api/admin/roles/PLATFORM_ADMIN").header("Authorization", bearer(admin.token)))
            .andExpect(jsonPath("$.role.superAdmin").value(true))
            .andExpect(jsonPath("$.catalogPermissionCount").value(jdbc.queryForObject("SELECT COUNT(*) FROM auth_permissions WHERE is_active = TRUE", Integer.class)));
        mvc.perform(get("/api/admin/roles/NOPE").header("Authorization", bearer(admin.token)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));

        // recent role activity is built from platform_roles / shop_members (audit_logs has no role rows yet)
        mvc.perform(get("/api/admin/roles/activity?limit=10").header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].type", hasItems("PLATFORM_ROLE_GRANTED", "SHOP_ROLE_ADDED")))
            .andExpect(jsonPath("$[?(@.type=='SHOP_ROLE_ADDED' && @.roleCode=='STORE_VIEWER')].shopName").value(hasItem("ร้านบทบาท")));
    }

    // ---------------------------------------------------------------- detail and history

    @Test void userDetailShowsEffectiveRolesPermissionsAndHistory() throws Exception {
        var admin = platformAdmin("detail-admin@example.com", "0820000050");
        var owner = register("detail-owner@example.com", "0820000051", "เจ้าของ", "รายละเอียด");
        long shopId = openShop(owner, "ร้านรายละเอียด", "Detail Shop", "2000000000005");
        jdbc.update("UPDATE users SET last_login_at = ? WHERE id = ?", Timestamp.from(Instant.now()), owner.id);

        mvc.perform(get("/api/admin/users/" + owner.id).header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("detail-owner@example.com"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.accountRole").value("CUSTOMER"))
            .andExpect(jsonPath("$.superAdmin").value(false))
            .andExpect(jsonPath("$.primaryRole.code").value("STORE_OWNER"))
            .andExpect(jsonPath("$.roles[*].code", hasItems("STORE_OWNER", "BUYER", "APPLICANT")))
            .andExpect(jsonPath("$.accountPermissions[*].code", hasItem("BUYER_PROFILE_READ")))
            .andExpect(jsonPath("$.accountPermissions[*].code", not(hasItem("PLATFORM_ACCOUNT_READ"))))
            .andExpect(jsonPath("$.shops[0].shopId").value(shopId))
            .andExpect(jsonPath("$.shops[0].role").value("STORE_OWNER"))
            .andExpect(jsonPath("$.shops[0].joinedAt").exists())
            .andExpect(jsonPath("$.shops[0].permissions[*].code", hasItems("SHOP_READ", "SHOP_UPDATE")))
            .andExpect(jsonPath("$.lastLoginAt").exists());
        mvc.perform(get("/api/admin/users/" + admin.id).header("Authorization", bearer(admin.token)))
            .andExpect(jsonPath("$.superAdmin").value(true))
            .andExpect(jsonPath("$.primaryRole.code").value("PLATFORM_ADMIN"))
            .andExpect(jsonPath("$.accountPermissions[*].code", hasItems("PLATFORM_ACCOUNT_READ", "PLATFORM_AUDIT_READ")));

        mvc.perform(get("/api/admin/users/" + owner.id + "/history").header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].type", hasItems("ACCOUNT_CREATED", "LAST_LOGIN", "SHOP_ROLE_ADDED", "APPLICATION_SUBMITTED")))
            .andExpect(jsonPath("$[?(@.type=='APPLICATION_SUBMITTED')].shopName").value(hasItem("ร้านรายละเอียด")));
        mvc.perform(get("/api/admin/users/" + admin.id + "/history").header("Authorization", bearer(admin.token)))
            .andExpect(jsonPath("$[*].type", hasItem("PLATFORM_ROLE_GRANTED")));
    }

    // ---------------------------------------------------------------- helpers

    record Account(UUID id, String token) {}

    private void expectEmails(String auth, String query, String... emails) throws Exception {
        var body = mvc.perform(get("/api/admin/users?size=100&" + query).header("Authorization", auth))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> actual = JsonPath.read(body, "$.items[*].email");
        if (query.contains("sort=")) assertEquals(List.of(emails), actual, query);
        else assertEquals(new HashSet<>(List.of(emails)), new HashSet<>(actual), query);
        assertEquals(emails.length, ((Number) JsonPath.read(body, "$.totalItems")).intValue(), query);
    }

    private Account register(String email, String phone, String firstName, String lastName) throws Exception {
        var body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
              {"firstName":"%s","lastName":"%s","email":"%s","phone":"%s","password":"Password1"}
              """.formatted(firstName, lastName, email, phone)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Account(UUID.fromString(JsonPath.read(body, "$.user.id")), JsonPath.read(body, "$.accessToken"));
    }

    private Account platformAdmin(String email, String phone) throws Exception {
        var account = register(email, phone, "ผู้ดูแล", "ระบบ");
        platformRoles.save(PlatformRole.grant(account.id, PlatformRole.PLATFORM_ADMIN, null, "test"));
        return account;
    }

    private long openShop(Account owner, String shopName, String shopNameEn, String citizenId) throws Exception {
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
              """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"ownerType":"PERSON","title":"นาย","firstName":"ทดสอบ","lastName":"สิทธิ์","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"0899999999","email":"owner@example.com","houseNo":"1","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"ทดสอบ สิทธิ์"}
              """.formatted(citizenId))).andExpect(status().isOk());
        for (var type : List.of("IDENTITY", "ADDRESS")) {
            mvc.perform(multipart("/api/merchant-applications/draft/documents/" + type).file(png()).header("Authorization", bearer(owner.token)))
                .andExpect(status().isOk());
        }
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", bearer(owner.token))).andExpect(status().isOk());
        return shops.findAll().stream().filter(shop -> shop.getName().equals(shopName)).findFirst().orElseThrow().getId();
    }

    /** Adds a team member to the owner's shop directly in the database (there is no invitation API yet). */
    private void addMember(Account owner, UUID userId, String role) {
        var shopUuid = jdbc.queryForObject("SELECT shop_id FROM shop_members WHERE user_id = ? AND role = 'STORE_OWNER'", UUID.class, owner.id);
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO shop_members (id, shop_id, user_id, role, status, joined_at, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)",
            UUID.randomUUID(), shopUuid, userId, role, now, now, now);
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "image.png", "image/png", new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
