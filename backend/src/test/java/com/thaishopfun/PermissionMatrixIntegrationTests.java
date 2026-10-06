package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PermissionMatrix;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import com.thaishopfun.merchant.OwnerAuditLogRepository;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ShopMemberRepository;
import com.thaishopfun.merchant.ShopReinstatementRequestRepository;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.support.ReferenceSeedSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Permission Matrix enforcement end to end (MockMvc + H2). The role/permission catalog in the test database is
 * loaded from the real db/seed/seed_reference_data.sql (see ReferenceSeedTestLoader), never from fixtures.
 * <p>Status codes: 401 no/expired/invalid/revoked token; 403 PERMISSION_DENIED when the user (or their shop role)
 * lacks the permission; 404 SHOP_NOT_FOUND for a shop the user is not a member of (same as a non-existent shop).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermissionMatrixIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired PermissionMatrix matrix;
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

    private static final String HOURS = "{\"days\":[" + String.join(",", java.util.Collections.nCopies(7, "{\"open\":true,\"start\":\"09:00\",\"end\":\"18:00\"}")) + "]}";

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

    // ---------------------------------------------------------------- matrix comes from the database seed

    @Test void permissionMatrixIsLoadedFromTheSeededDatabaseTables() {
        var seed = ReferenceSeedSql.load();
        assertEquals(seed.roles().rows().size(), jdbc.queryForObject("SELECT COUNT(*) FROM auth_roles", Integer.class));
        assertEquals(seed.permissions().rows().size(), jdbc.queryForObject("SELECT COUNT(*) FROM auth_permissions", Integer.class));
        assertEquals(seed.mappings().size(), jdbc.queryForObject("SELECT COUNT(*) FROM auth_role_permissions", Integer.class));

        matrix.invalidate();
        var snapshot = matrix.current();
        assertEquals(seed.mappings().size(), snapshot.mappingCount());
        var expected = new HashMap<String, Set<String>>();
        for (var mapping : seed.mappings()) expected.computeIfAbsent((String) mapping.get(0), k -> new TreeSet<>()).add((String) mapping.get(1));
        for (var row : seed.roles().rows()) {
            var role = (String) row.get(0);
            assertEquals(expected.getOrDefault(role, Set.of()), new TreeSet<>(snapshot.permissionsOf(role)), "permissions of " + role);
        }
        // every signed-in account implicitly holds the ACCOUNT/APPLICANT context roles of the catalog
        assertEquals(Set.of("APPLICANT", "BUYER"), snapshot.signedInRoles());
    }

    // ---------------------------------------------------------------- (1) user without permission -> 403

    @Test void userWithoutPlatformPermissionGets403() throws Exception {
        var buyer = register("buyer@example.com", "0810000001");
        mvc.perform(get("/api/admin/store-applications").header("Authorization", bearer(buyer.token)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"))
            .andExpect(jsonPath("$.message").value(containsString("PLATFORM_APPLICATION_READ")));
        mvc.perform(post("/api/admin/store-applications/1/approve").header("Authorization", bearer(buyer.token)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(post("/api/admin/store-applications/bulk/suspend").header("Authorization", bearer(buyer.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"shopIds\":[1]}"))
            .andExpect(status().isForbidden());
        // account-level permissions every buyer has (BUYER / APPLICANT roles from the matrix) still work
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(buyer.token))).andExpect(status().isOk());
        mvc.perform(get("/api/merchant-applications/draft").header("Authorization", bearer(buyer.token))).andExpect(status().isOk());
    }

    @Test void shopMemberWhoseRoleLacksThePermissionGets403() throws Exception {
        var owner = register("owner@example.com", "0810000002");
        long shopId = openShop(owner, "ร้านเจ้าของ", "Owner Shop", "1000000000001");
        var viewer = register("viewer@example.com", "0810000003");
        addMember(shopId, viewer.id, "STORE_VIEWER");

        mvc.perform(get("/api/merchant/settings?shopId=" + shopId).header("Authorization", bearer(viewer.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.shopName").value("ร้านเจ้าของ"));
        mvc.perform(put("/api/merchant/settings/hours?shopId=" + shopId).header("Authorization", bearer(viewer.token))
                .contentType(MediaType.APPLICATION_JSON).content(HOURS))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"))
            .andExpect(jsonPath("$.message").value(containsString("SHOP_UPDATE")));
        mvc.perform(multipart("/api/merchant/settings/logo?shopId=" + shopId).file(png()).header("Authorization", bearer(viewer.token)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/merchant/reinstatement?shopId=" + shopId).header("Authorization", bearer(viewer.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"ขอเปิดร้านอีกครั้งด้วยครับ\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/merchant/shops").header("Authorization", bearer(viewer.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].shopId").value(shopId));
        // the owner (STORE_OWNER has SHOP_UPDATE in the matrix) may
        mvc.perform(put("/api/merchant/settings/hours?shopId=" + shopId).header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content(HOURS))
            .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- (2) another shop -> 404

    @Test void userCannotReachAnotherShopsResources() throws Exception {
        var alice = register("alice@example.com", "0810000004");
        long aliceShop = openShop(alice, "ร้านอลิซ", "Alice Shop", "1000000000002");
        var bob = register("bob@example.com", "0810000005");
        long bobShop = openShop(bob, "ร้านบ็อบ", "Bob Shop", "1000000000003");

        var asBob = bearer(bob.token);
        List<RequestBuilder> foreignShopCalls = List.of(
            get("/api/merchant/settings?shopId=" + aliceShop).header("Authorization", asBob),
            put("/api/merchant/settings?shopId=" + aliceShop).header("Authorization", asBob).contentType(MediaType.APPLICATION_JSON).content("{}"),
            put("/api/merchant/settings/hours?shopId=" + aliceShop).header("Authorization", asBob).contentType(MediaType.APPLICATION_JSON).content(HOURS),
            multipart("/api/merchant/settings/cover?shopId=" + aliceShop).file(png()).header("Authorization", asBob),
            multipart("/api/merchant/settings/logo?shopId=" + aliceShop).file(png()).header("Authorization", asBob),
            get("/api/merchant/reinstatement?shopId=" + aliceShop).header("Authorization", asBob),
            post("/api/merchant/reinstatement?shopId=" + aliceShop).header("Authorization", asBob).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"ขอเปิดร้านของคนอื่นหน่อย\"}"),
            get("/api/merchant/store-access?shopId=" + aliceShop).header("Authorization", asBob));
        for (var call : foreignShopCalls) {
            mvc.perform(call).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));
        }
        // indistinguishable from a shop that does not exist
        mvc.perform(get("/api/merchant/settings?shopId=987654").header("Authorization", bearer(bob.token)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));
        // bob only sees and edits his own shop; a default (no shopId) call resolves to his own shop
        mvc.perform(get("/api/merchant/shops").header("Authorization", bearer(bob.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].shopId").value(bobShop));
        mvc.perform(get("/api/merchant/settings").header("Authorization", bearer(bob.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.shopId").value(bobShop));
        // a platform admin is not a shop member either: merchant endpoints of a shop stay closed
        var admin = platformAdmin("admin@example.com", "0810000006");
        mvc.perform(get("/api/merchant/settings?shopId=" + aliceShop).header("Authorization", bearer(admin.token)))
            .andExpect(status().isNotFound());
        // alice's shop was not modified by bob's attempts
        mvc.perform(get("/api/merchant/settings?shopId=" + aliceShop).header("Authorization", bearer(alice.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.coverUrl").doesNotExist());
    }

    // ---------------------------------------------------------------- (3) expired / invalid token -> 401

    @Test void expiredInvalidOrMissingTokensGet401() throws Exception {
        var owner = register("expired@example.com", "0810000007");
        long shopId = openShop(owner, "ร้านหมดอายุ", "Expired Shop", "1000000000004");
        var expired = accessToken(jwtEncoder, owner.id, 0, Instant.now().minus(Duration.ofMinutes(20)), Instant.now().minus(Duration.ofMinutes(5)));

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(expired)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
            .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
        mvc.perform(get("/api/merchant/settings?shopId=" + shopId).header("Authorization", bearer(expired)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));

        var otherKey = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(
            "some-other-secret-that-is-at-least-thirty-two-characters".getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
            .algorithm(MacAlgorithm.HS256).build();
        var forged = accessToken(otherKey, owner.id, 0, Instant.now(), Instant.now().plus(Duration.ofMinutes(15)));
        mvc.perform(get("/api/merchant/settings?shopId=" + shopId).header("Authorization", bearer(forged)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        mvc.perform(get("/api/merchant/settings?shopId=" + shopId).header("Authorization", "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        mvc.perform(get("/api/merchant/settings?shopId=" + shopId))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/admin/store-applications"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        // public endpoints stay public
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
        mvc.perform(get("/api/shops")).andExpect(status().isOk());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- (4) role changed during the session

    @Test void shopRoleChangeAppliesToTheExistingAccessTokenImmediately() throws Exception {
        var owner = register("demoted@example.com", "0810000008");
        long shopId = openShop(owner, "ร้านเปลี่ยนบทบาท", "Role Shop", "1000000000005");
        mvc.perform(put("/api/merchant/settings/hours?shopId=" + shopId).header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content(HOURS))
            .andExpect(status().isOk());

        // demoted to STORE_VIEWER directly in the database while the access token is still valid
        jdbc.update("UPDATE shop_members SET role = 'STORE_VIEWER' WHERE user_id = ?", owner.id);
        mvc.perform(put("/api/merchant/settings/hours?shopId=" + shopId).header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content(HOURS))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(get("/api/merchant/settings?shopId=" + shopId).header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopPermissions[0].role").value("STORE_VIEWER"))
            .andExpect(jsonPath("$.shopPermissions[0].permissions", hasItem("SHOP_READ")))
            .andExpect(jsonPath("$.shopPermissions[0].permissions", not(hasItem("SHOP_UPDATE"))));

        // promoted back
        jdbc.update("UPDATE shop_members SET role = 'STORE_OWNER' WHERE user_id = ?", owner.id);
        mvc.perform(put("/api/merchant/settings/hours?shopId=" + shopId).header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content(HOURS))
            .andExpect(status().isOk());

        // removed from the shop: the shop disappears for this token
        jdbc.update("UPDATE shop_members SET status = 'INACTIVE', removed_at = ? WHERE user_id = ?", Timestamp.from(Instant.now()), owner.id);
        mvc.perform(get("/api/merchant/settings?shopId=" + shopId).header("Authorization", bearer(owner.token)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));
        mvc.perform(get("/api/merchant/shops").header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/merchant/store-access").header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hasShop").value(false));
    }

    @Test void platformRoleGrantAndRevokeApplyToTheExistingAccessTokenImmediately() throws Exception {
        var user = register("staff@example.com", "0810000009");
        mvc.perform(get("/api/admin/store-applications").header("Authorization", bearer(user.token))).andExpect(status().isForbidden());

        var grant = platformRoles.save(PlatformRole.grant(user.id, PlatformRole.PLATFORM_ADMIN, null, "test"));
        mvc.perform(get("/api/admin/store-applications").header("Authorization", bearer(user.token))).andExpect(status().isOk());

        jdbc.update("UPDATE platform_roles SET revoked_at = ? WHERE id = ?", Timestamp.from(Instant.now()), grant.getId());
        mvc.perform(get("/api/admin/store-applications").header("Authorization", bearer(user.token)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test void bumpingPermissionVersionRevokesExistingAccessTokens() throws Exception {
        var user = register("bumped@example.com", "0810000010");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(user.token))).andExpect(status().isOk());
        jdbc.update("UPDATE users SET permission_version = permission_version + 1 WHERE id = ?", user.id);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(user.token)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
        var relogin = login("bumped@example.com");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(relogin))).andExpect(status().isOk());
    }

    @Test void changingTheMatrixInTheDatabaseChangesDecisionsAfterReload() throws Exception {
        var owner = register("matrix@example.com", "0810000011");
        long shopId = openShop(owner, "ร้านแมทริกซ์", "Matrix Shop", "1000000000006");
        var mappingSql = "SELECT rp.id FROM auth_role_permissions rp JOIN auth_roles r ON r.id = rp.role_id JOIN auth_permissions p ON p.id = rp.permission_id WHERE r.code = 'STORE_OWNER' AND p.code = 'SHOP_UPDATE'";
        var mappingId = jdbc.queryForObject(mappingSql, UUID.class);
        var roleId = jdbc.queryForObject("SELECT id FROM auth_roles WHERE code = 'STORE_OWNER'", UUID.class);
        var permissionId = jdbc.queryForObject("SELECT id FROM auth_permissions WHERE code = 'SHOP_UPDATE'", UUID.class);
        try {
            jdbc.update("DELETE FROM auth_role_permissions WHERE id = ?", mappingId);
            matrix.invalidate();
            mvc.perform(put("/api/merchant/settings/hours?shopId=" + shopId).header("Authorization", bearer(owner.token))
                    .contentType(MediaType.APPLICATION_JSON).content(HOURS))
                .andExpect(status().isForbidden());
        } finally {
            jdbc.update("INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version, created_date_time, modified_date_time) VALUES (?, ?, ?, '1.0', ?, ?)",
                mappingId, roleId, permissionId, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
            matrix.invalidate();
        }
        mvc.perform(put("/api/merchant/settings/hours?shopId=" + shopId).header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content(HOURS))
            .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- effective permissions for the frontend

    @Test void meAndLoginExposeEffectivePermissions() throws Exception {
        var owner = register("me@example.com", "0810000012");
        long shopId = openShop(owner, "ร้านของฉัน", "My Shop", "1000000000007");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("me@example.com"))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.roleCodes", hasItems("BUYER", "APPLICANT")))
            .andExpect(jsonPath("$.permissions", hasItems("BUYER_PROFILE_READ", "SHOP_APPLICATION_CREATE")))
            .andExpect(jsonPath("$.permissions", not(hasItem("PLATFORM_APPLICATION_READ"))))
            .andExpect(jsonPath("$.shopPermissions.length()").value(1))
            .andExpect(jsonPath("$.shopPermissions[0].shopId").value(shopId))
            .andExpect(jsonPath("$.shopPermissions[0].role").value("STORE_OWNER"))
            .andExpect(jsonPath("$.shopPermissions[0].permissions", hasItems("SHOP_READ", "SHOP_UPDATE", "ORDER_CANCEL")));

        var admin = platformAdmin("me-admin@example.com", "0810000013");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"me-admin@example.com","password":"Password1"}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.roleCodes", hasItem("PLATFORM_ADMIN")))
            .andExpect(jsonPath("$.user.permissions", hasItems("PLATFORM_APPLICATION_READ", "PLATFORM_APPLICATION_REVIEW", "PLATFORM_SHOP_SUSPEND")))
            .andExpect(jsonPath("$.user.shopPermissions.length()").value(0));
        mvc.perform(get("/api/admin/store-applications").header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].shopId").value(shopId));
    }

    // ---------------------------------------------------------------- helpers

    record Account(UUID id, String token) {}

    private Account register(String email, String phone) throws Exception {
        var body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
              {"firstName":"ทดสอบ","lastName":"สิทธิ์","email":"%s","phone":"%s","password":"Password1"}
              """.formatted(email, phone)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Account(UUID.fromString(JsonPath.read(body, "$.user.id")), JsonPath.read(body, "$.accessToken"));
    }

    private String login(String email) throws Exception {
        var body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
              {"identity":"%s","password":"Password1"}
              """.formatted(email)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private Account platformAdmin(String email, String phone) throws Exception {
        var account = register(email, phone);
        platformRoles.save(PlatformRole.grant(account.id, PlatformRole.PLATFORM_ADMIN, null, "test"));
        return account;
    }

    /** Opens a shop through the real onboarding API (creates the STORE_OWNER membership) and returns its shop number. */
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

    /** Adds a team member directly in the database (there is no invitation API yet). */
    private void addMember(long shopNo, UUID userId, String role) {
        var shopUuid = shops.findByShopId(shopNo).orElseThrow().getUuid();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO shop_members (id, shop_id, user_id, role, status, joined_at, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)",
            UUID.randomUUID(), shopUuid, userId, role, now, now, now);
    }

    static String accessToken(JwtEncoder encoder, UUID userId, int permissionVersion, Instant issuedAt, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().issuer("thaishopfun").issuedAt(issuedAt).expiresAt(expiresAt)
            .subject(userId.toString()).claim("roles", List.of("CUSTOMER")).claim("pv", permissionVersion).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "image.png", "image/png", new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
