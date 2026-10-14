package com.thaishopfun.auth;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * users.role was dropped (Flyway V18). Everything role-related now comes from the role tables:
 * platform_roles (admin), shop_members (merchant) and auth_roles / auth_role_permissions (codes, permissions).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsersRoleRemovalIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired UserDetailsService userDetails;
    @Autowired PasswordEncoder passwords;
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

    @Test void usersTableHasNoRoleColumn() {
        // H2 builds the schema from the entities (create-drop), so this proves UserAccount no longer maps users.role
        assertEquals(0, jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = 'USERS' AND UPPER(COLUMN_NAME) = 'ROLE'
            """, Integer.class));
    }

    @Test void meRoleAndJwtRolesClaimComeFromTheRoleTables() throws Exception {
        var buyer = register("plain@example.com", "0830000001");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(buyer.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.superAdmin").value(false))
            .andExpect(jsonPath("$.shopPermissions.length()").value(0));
        assertEquals(List.of(), rolesClaim(buyer.token));

        // shop_members: the STORE_OWNER membership created at submit makes the user a merchant (no users column)
        long shopNo = openShop(buyer, "ร้านจากตาราง", "Table Shop", "3000000000001");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(buyer.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("MERCHANT"))
            .andExpect(jsonPath("$.superAdmin").value(false))
            .andExpect(jsonPath("$.shopPermissions[0].shopId").value(shopNo))
            .andExpect(jsonPath("$.shopPermissions[0].role").value("STORE_OWNER"));
        assertEquals(List.of("STORE_OWNER"), rolesClaim(login("plain@example.com")));
        assertEquals(List.of("ROLE_STORE_OWNER"), authorities("plain@example.com"));

        // platform_roles: admin only while an active row exists
        var admin = register("table-admin@example.com", "0830000002");
        platformRoles.save(PlatformRole.grant(admin.id, PlatformRole.PLATFORM_ADMIN, null, "test"));
        var adminToken = login("table-admin@example.com");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.superAdmin").value(true))
            .andExpect(jsonPath("$.roleCodes", hasItem("PLATFORM_ADMIN")));
        assertEquals(List.of("PLATFORM_ADMIN"), rolesClaim(adminToken));
        assertEquals(List.of("ROLE_PLATFORM_ADMIN"), authorities("table-admin@example.com"));
        mvc.perform(get("/api/admin/store-applications").header("Authorization", bearer(adminToken))).andExpect(status().isOk());

        // revoking the row in the table removes admin at once, even for the already issued token
        jdbc.update("UPDATE platform_roles SET revoked_at = ? WHERE user_id = ?", Timestamp.from(Instant.now()), admin.id);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.superAdmin").value(false))
            .andExpect(jsonPath("$.roleCodes", not(hasItem("PLATFORM_ADMIN"))));
        mvc.perform(get("/api/admin/store-applications").header("Authorization", bearer(adminToken))).andExpect(status().isForbidden());
    }

    @Test void adminIsGrantedOnlyByPlatformRoles() throws Exception {
        // the old "System Administrator" factory no longer carries any role: without a platform_roles row it is a customer
        users.save(UserAccount.systemAccount("no-grant@example.com", passwords.encode("Password1")));
        var token = login("no-grant@example.com");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.superAdmin").value(false));
        assertEquals(List.of(), rolesClaim(token));
        mvc.perform(get("/api/admin/store-applications").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(token))).andExpect(status().isForbidden());

        // a pending shop is visible to its owner and to platform staff (platform_roles), not to that account
        var owner = register("preview-owner@example.com", "0830000003");
        long shopNo = openShop(owner, "ร้านรอตรวจ", "Preview Shop", "3000000000002");
        var slug = shops.findByShopId(shopNo).orElseThrow().getSlug();
        mvc.perform(get("/api/shops/" + slug).header("Authorization", bearer(token))).andExpect(status().isNotFound());
        mvc.perform(get("/api/shops/" + slug)).andExpect(status().isNotFound());
        mvc.perform(get("/api/shops/" + slug).header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        var staff = register("preview-staff@example.com", "0830000004");
        platformRoles.save(PlatformRole.grant(staff.id, PlatformRole.PLATFORM_ADMIN, null, "test"));
        mvc.perform(get("/api/shops/" + slug).header("Authorization", bearer(staff.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test void merchantComesFromActiveShopMembership() throws Exception {
        var owner = register("member-owner@example.com", "0830000005");
        long shopNo = openShop(owner, "ร้านทีม", "Team Shop", "3000000000003");
        var viewer = register("member-viewer@example.com", "0830000006");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(viewer.token))).andExpect(jsonPath("$.role").value("CUSTOMER"));

        var shopUuid = shops.findByShopId(shopNo).orElseThrow().getUuid();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO shop_members (id, shop_id, user_id, role, status, joined_at, created_date_time, modified_date_time) VALUES (?, ?, ?, 'STORE_VIEWER', 'ACTIVE', ?, ?, ?)",
            UUID.randomUUID(), shopUuid, viewer.id, now, now, now);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(viewer.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("MERCHANT"))
            .andExpect(jsonPath("$.shopPermissions[0].role").value("STORE_VIEWER"));
        assertEquals(List.of("STORE_VIEWER"), rolesClaim(login("member-viewer@example.com")));
        mvc.perform(get("/api/shops/" + shops.findByShopId(shopNo).orElseThrow().getSlug()).header("Authorization", bearer(viewer.token)))
            .andExpect(status().isOk());

        jdbc.update("UPDATE shop_members SET status = 'INACTIVE', removed_at = ? WHERE user_id = ?", now, viewer.id);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(viewer.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test void approvalDoesNotWriteToUsers() throws Exception {
        var owner = register("approve-owner@example.com", "0830000007");
        long shopNo = openShop(owner, "ร้านอนุมัติ", "Approve Shop", "3000000000004");
        var before = jdbc.queryForObject("SELECT modified_date_time FROM users WHERE id = ?", Timestamp.class, owner.id);
        var admin = register("approve-admin@example.com", "0830000008");
        platformRoles.save(PlatformRole.grant(admin.id, PlatformRole.PLATFORM_ADMIN, null, "test"));
        mvc.perform(post("/api/admin/store-applications/" + shopNo + "/approve").header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertEquals(before, jdbc.queryForObject("SELECT modified_date_time FROM users WHERE id = ?", Timestamp.class, owner.id));
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(owner.token))).andExpect(jsonPath("$.role").value("MERCHANT"));
    }

    @Test void bootstrapGrantsAdminOnlyToTheAccountItCreates() throws Exception {
        var config = new AdminBootstrapConfig();
        config.bootstrapAdmin(users, platformRoles, passwords, "Boot-Admin@example.com", "Password1").run(null);
        var created = users.findByEmailIgnoreCaseOrPhone("boot-admin@example.com", "boot-admin@example.com").orElseThrow();
        assertTrue(platformRoles.existsByUserIdAndRevokedAtIsNull(created.getId()));
        // running again is a no-op
        config.bootstrapAdmin(users, platformRoles, passwords, "boot-admin@example.com", "Password1").run(null);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM platform_roles WHERE user_id = ?", Integer.class, created.getId()));

        // an existing account without a platform role is never promoted
        var existing = register("existing-user@example.com", "0830000009");
        config.bootstrapAdmin(users, platformRoles, passwords, "existing-user@example.com", "Password1").run(null);
        assertFalse(platformRoles.existsByUserIdAndRevokedAtIsNull(existing.id));
    }

    // ---------------------------------------------------------------- helpers

    record Account(UUID id, String token) {}

    private List<String> rolesClaim(String token) {
        return jwtDecoder.decode(token).getClaimAsStringList("roles");
    }

    private List<String> authorities(String identity) {
        return userDetails.loadUserByUsername(identity).getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
    }

    private Account register(String email, String phone) throws Exception {
        var body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
              {"firstName":"ทดสอบ","lastName":"บทบาท","email":"%s","phone":"%s","password":"Password1"}
              """.formatted(email, phone)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Account(UUID.fromString(JsonPath.read(body, "$.user.id")), JsonPath.read(body, "$.accessToken"));
    }

    private String login(String identity) throws Exception {
        var body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
              {"identity":"%s","password":"Password1"}
              """.formatted(identity)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    /** Opens a shop through the real onboarding API (creates the STORE_OWNER membership) and returns its shop number. */
    private long openShop(Account owner, String shopName, String shopNameEn, String citizenId) throws Exception {
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
              """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"ownerType":"PERSON","title":"นาย","firstName":"ทดสอบ","lastName":"บทบาท","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"0899999999","email":"owner@example.com","houseNo":"1","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"ทดสอบ บทบาท"}
              """.formatted(citizenId))).andExpect(status().isOk());
        for (var type : List.of("IDENTITY", "ADDRESS")) {
            mvc.perform(multipart("/api/merchant-applications/draft/documents/" + type).file(png()).header("Authorization", bearer(owner.token)))
                .andExpect(status().isOk());
        }
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", bearer(owner.token))).andExpect(status().isOk());
        return shops.findAll().stream().filter(shop -> shop.getName().equals(shopName)).findFirst().orElseThrow().getId();
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "image.png", "image/png", new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
