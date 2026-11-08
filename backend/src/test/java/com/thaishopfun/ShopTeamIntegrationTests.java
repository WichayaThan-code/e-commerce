package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopTeamIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final Pattern TOKEN_IN_URL = Pattern.compile("/invite/([a-f0-9]{64})$");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ShopMemberRepository shopMembers;
    @Autowired ShopReinstatementRequestRepository reinstatements;
    @Autowired ShopRepository shops;
    @Autowired MerchantDocumentRepository documents;
    @Autowired MerchantApplicationRepository applications;
    @Autowired OwnerAuditLogRepository ownerAudits;
    @Autowired OwnerRepository owners;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformRoleRepository platformRoles;

    private String adminToken;

    @BeforeEach
    void clean() {
        adminToken = null;
        wipe();
    }

    @AfterEach
    void after() {
        wipe();
    }

    private void wipe() {
        try { jdbc.update("DELETE FROM shop_invitations"); } catch (Exception ignored) { /* table may be empty/new */ }
        try { jdbc.update("DELETE FROM audit_logs"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM merchant_notification_reads"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM merchant_notification_cursor"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM merchant_notifications"); } catch (Exception ignored) { }
        reinstatements.deleteAll();
        shopMembers.deleteAll();
        shops.deleteAll();
        documents.deleteAll();
        applications.deleteAll();
        ownerAudits.deleteAll();
        owners.deleteAll();
        refreshTokens.deleteAll();
        platformRoles.deleteAll();
        users.deleteAll();
    }

    @Test
    void inviteAcceptRoleRemoveAndCrossShopIsolation() throws Exception {
        var shopA = openShop("team-a@example.com", "0815800001", "ร้านทีมเอ", "TeamA", "1234567890801");
        var shopB = openShop("team-b@example.com", "0815800002", "ร้านทีมบี", "TeamB", "1234567890802");

        perform(get(base(shopA) + "/team/roles"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[?(@.code=='STORE_VIEWER')].assignableByActor").value(hasItem(true)))
            .andExpect(jsonPath("$.roles[?(@.code=='STORE_ADMIN')].assignableByActor").value(hasItem(true)))
            .andExpect(jsonPath("$.roles[?(@.code=='STORE_VIEWER')].permissions", not(empty())))
            .andExpect(jsonPath("$.staffQuota.quotaEnforced").value(false));

        var created = perform(post(base(shopA) + "/team/invitations").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"team-invitee@example.com","role":"STORE_VIEWER"}
            """), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailQueued").value(false))
            .andExpect(jsonPath("$.deliveryNote", containsString("ยังไม่ได้ส่งอีเมล")))
            .andExpect(jsonPath("$.inviteUrl", containsString("/invite/")))
            .andReturn();
        String inviteUrl = JsonPath.read(created.getResponse().getContentAsString(), "$.inviteUrl");
        String token = extractToken(inviteUrl);
        String invitationId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // list never echoes token / inviteUrl
        perform(get(base(shopA) + "/team/invitations"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalPending").value(1))
            .andExpect(jsonPath("$.items[0].status").value("PENDING"))
            .andExpect(jsonPath("$.items[0].actionable").value(true))
            .andExpect(jsonPath("$.items[0].inviteUrl").doesNotExist())
            .andExpect(jsonPath("$.items[0].token").doesNotExist());

        // public preview does not accept
        mvc.perform(get("/api/invitations/" + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.role").value("STORE_VIEWER"))
            .andExpect(jsonPath("$.callerAuthenticated").value(false))
            .andExpect(jsonPath("$.permissions", hasItem("FINANCE_READ")));

        // wrong email account
        var wrong = registerVerified("team-wrong@example.com", "0815800011");
        perform(post("/api/invitations/" + token + "/accept"), wrong.token)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_MISMATCH"));

        // unverified email
        var unverified = register("team-unverified@example.com", "0815800012");
        jdbc.update("UPDATE users SET email_verified_at = NULL WHERE email = ?", "team-unverified@example.com");
        perform(post("/api/invitations/" + token + "/accept"), unverified)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        var invitee = registerVerified("team-invitee@example.com", "0815800013");
        perform(post("/api/invitations/" + token + "/accept"), invitee.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("STORE_VIEWER"))
            .andExpect(jsonPath("$.active").value(true));

        // reuse rejected
        perform(post("/api/invitations/" + token + "/accept"), invitee.token)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", anyOf(is("INVITE_ACCEPTED"), is("INVITE_ALREADY_USED"), is("ALREADY_MEMBER"))));

        // duplicate pending invite blocked
        perform(post(base(shopA) + "/team/invitations").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"team-invitee@example.com","role":"STORE_SALES"}
            """), shopA.token)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));

        // invitee can read shop A products as viewer, not manage users
        perform(get(base(shopA) + "/team/members"), invitee.token)
            .andExpect(status().isForbidden());
        perform(get(base(shopA) + "/products"), invitee.token)
            .andExpect(status().isOk());

        // cross-shop: invitee is not member of B
        perform(get(base(shopB) + "/products"), invitee.token)
            .andExpect(status().isNotFound());

        // change role + remove
        perform(patch(base(shopA) + "/team/members/" + invitee.userId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"STORE_SALES\"}"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("STORE_SALES"));

        perform(delete(base(shopA) + "/team/members/" + invitee.userId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"TEST_REMOVE\"}"), shopA.token)
            .andExpect(status().isOk());

        // revoked access while token still valid
        perform(get(base(shopA) + "/products"), invitee.token)
            .andExpect(status().isNotFound());

        // owner still works; shop B untouched
        perform(get(base(shopA) + "/products"), shopA.token).andExpect(status().isOk());
        perform(get(base(shopB) + "/products"), shopB.token).andExpect(status().isOk());

        // revoke path for a fresh invite
        var again = perform(post(base(shopA) + "/team/invitations").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"team-revoked@example.com","role":"STORE_WAREHOUSE"}
            """), shopA.token).andExpect(status().isOk()).andReturn();
        String revokeId = JsonPath.read(again.getResponse().getContentAsString(), "$.id");
        String revokeToken = extractToken(JsonPath.read(again.getResponse().getContentAsString(), "$.inviteUrl"));
        perform(post(base(shopA) + "/team/invitations/" + revokeId + "/revoke"), shopA.token)
            .andExpect(status().isOk());
        var revokedUser = registerVerified("team-revoked@example.com", "0815800014");
        perform(post("/api/invitations/" + revokeToken + "/accept"), revokedUser.token)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", startsWith("INVITE_")));

        // expired invite (lazy)
        var expired = perform(post(base(shopA) + "/team/invitations").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"team-expired@example.com","role":"STORE_VIEWER"}
            """), shopA.token).andExpect(status().isOk()).andReturn();
        String expiredToken = extractToken(JsonPath.read(expired.getResponse().getContentAsString(), "$.inviteUrl"));
        jdbc.update("UPDATE shop_invitations SET expires_at = ? WHERE id = ?",
            java.sql.Timestamp.from(java.time.Instant.now().minus(2, java.time.temporal.ChronoUnit.DAYS)),
            UUID.fromString(JsonPath.read(expired.getResponse().getContentAsString(), "$.id")));
        perform(get(base(shopA) + "/team/invitations"), shopA.token)
            .andExpect(jsonPath("$.items[?(@.email=='team-expired@example.com')].status").value(hasItem("EXPIRED")))
            .andExpect(jsonPath("$.items[?(@.email=='team-expired@example.com')].actionable").value(hasItem(false)));
        var expiredUser = registerVerified("team-expired@example.com", "0815800015");
        perform(post("/api/invitations/" + expiredToken + "/accept"), expiredUser.token)
            .andExpect(status().isConflict());

        // owner cannot remove / demote self (SELF_* checked before SHOP_OWNER_PROTECTED)
        UUID ownerId = users.findByEmailIgnoreCaseOrPhone("team-a@example.com", "0815800001").orElseThrow().getId();
        perform(delete(base(shopA) + "/team/members/" + ownerId).contentType(MediaType.APPLICATION_JSON)
                .content("{}"), shopA.token)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SELF_REMOVE"));

        perform(patch(base(shopA) + "/team/members/" + ownerId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"STORE_VIEWER\"}"), shopA.token)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", anyOf(is("SELF_ROLE_CHANGE"), is("SHOP_OWNER_PROTECTED"), is("ROLE_NOT_ASSIGNABLE"))));

        // another shop's owner cannot remove this shop's owner via crafted path
        perform(delete(base(shopA) + "/team/members/" + ownerId).contentType(MediaType.APPLICATION_JSON)
                .content("{}"), shopB.token)
            .andExpect(status().isNotFound());

        // privilege: invite STORE_OWNER rejected
        perform(post(base(shopA) + "/team/invitations").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"team-owner-role@example.com","role":"STORE_OWNER"}
            """), shopA.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNABLE"));

        assertNotNull(invitationId);
    }

    @Test
    void concurrentAcceptAndRevokeSingleWinner() throws Exception {
        var shop = openShop("team-race@example.com", "0815800020", "ร้านแข่ง", "Race", "1234567890820");
        var created = perform(post(base(shop) + "/team/invitations").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"team-race-user@example.com","role":"STORE_MANAGER"}
            """), shop.token).andExpect(status().isOk()).andReturn();
        String token = extractToken(JsonPath.read(created.getResponse().getContentAsString(), "$.inviteUrl"));
        String invitationId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        var invitee = registerVerified("team-race-user@example.com", "0815800021");

        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);
        var accepts = new AtomicInteger();
        var revokes = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> {
            try {
                start.await();
                var result = mvc.perform(post("/api/invitations/" + token + "/accept")
                    .header("Authorization", "Bearer " + invitee.token)).andReturn();
                if (result.getResponse().getStatus() == 200) accepts.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                done.countDown();
            }
        });
        pool.submit(() -> {
            try {
                start.await();
                var result = mvc.perform(post(base(shop) + "/team/invitations/" + invitationId + "/revoke")
                    .header("Authorization", "Bearer " + shop.token)).andReturn();
                if (result.getResponse().getStatus() == 200) revokes.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                done.countDown();
            }
        });
        start.countDown();
        assertTrue(done.await(15, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdownNow();
        // Exactly one of accept/revoke should succeed; the other conflicts — never both leave PENDING
        assertEquals(1, accepts.get() + revokes.get());
        String status = jdbc.queryForObject("SELECT status FROM shop_invitations WHERE id = ?", String.class, UUID.fromString(invitationId));
        assertThat(status, anyOf(is("ACCEPTED"), is("REVOKED")));
    }

    @Test
    void resendInvalidatesOldToken() throws Exception {
        var shop = openShop("team-resend@example.com", "0815800030", "ร้านส่งใหม่", "Resend", "1234567890830");
        var created = perform(post(base(shop) + "/team/invitations").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"team-resend-user@example.com","role":"STORE_SALES"}
            """), shop.token).andExpect(status().isOk()).andReturn();
        String oldToken = extractToken(JsonPath.read(created.getResponse().getContentAsString(), "$.inviteUrl"));
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        Thread.sleep(1100); // cooldown 1s in test profile
        var resent = perform(post(base(shop) + "/team/invitations/" + id + "/resend"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inviteUrl", containsString("/invite/")))
            .andReturn();
        String newToken = extractToken(JsonPath.read(resent.getResponse().getContentAsString(), "$.inviteUrl"));
        assertNotEquals(oldToken, newToken);
        var user = registerVerified("team-resend-user@example.com", "0815800031");
        perform(post("/api/invitations/" + oldToken + "/accept"), user.token)
            .andExpect(status().isNotFound());
        perform(post("/api/invitations/" + newToken + "/accept"), user.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("STORE_SALES"));
    }

    private static String extractToken(String inviteUrl) {
        Matcher m = TOKEN_IN_URL.matcher(inviteUrl);
        assertTrue(m.find(), "inviteUrl missing token path");
        return m.group(1);
    }

    private static String base(OpenShop shop) {
        return "/api/merchant/shops/" + shop.shopId;
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private OpenShop openShop(String email, String phone, String shopName, String shopNameEn, String citizenId) throws Exception {
        var seller = registerVerified(email, phone);
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", "Bearer " + seller.token).contentType(MediaType.APPLICATION_JSON).content("""
            {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
            """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", "Bearer " + seller.token).contentType(MediaType.APPLICATION_JSON).content("""
            {"ownerType":"PERSON","title":"นาย","firstName":"สมชาย","lastName":"ขายของ","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"%s","email":"%s","houseNo":"9","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมชาย ขายของ"}
            """.formatted(citizenId, phone, email))).andExpect(status().isOk());
        uploadDocument(seller.token, "IDENTITY");
        uploadDocument(seller.token, "ADDRESS");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + seller.token)).andExpect(status().isOk());
        var listed = mvc.perform(get("/api/merchant/shops").header("Authorization", "Bearer " + seller.token)).andExpect(status().isOk()).andReturn();
        Number shopId = JsonPath.read(listed.getResponse().getContentAsString(), "$[0].shopId");
        if (adminToken == null) {
            var approver = users.save(UserAccount.systemAccount("team-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"team-approver@example.com","password":"Password1"}
                """)).andExpect(status().isOk()).andReturn();
            adminToken = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
        }
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve").header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        return new OpenShop(seller.token, shopId.longValue(), seller.userId);
    }

    private SessionUser registerVerified(String email, String phone) throws Exception {
        String token = register(email, phone);
        jdbc.update("UPDATE users SET email_verified_at = NOW() WHERE email = ?", email);
        UUID id = users.findByEmailIgnoreCaseOrPhone(email, phone).orElseThrow().getId();
        return new SessionUser(token, id);
    }

    private String register(String email, String phone) throws Exception {
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"สมชาย","lastName":"ขายของ","email":"%s","phone":"%s","password":"Password1"}
            """.formatted(email, phone))).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
    }

    private void uploadDocument(String token, String type) throws Exception {
        var file = new MockMultipartFile("file", type.toLowerCase() + ".png", "image/png", PNG);
        mvc.perform(multipart("/api/merchant-applications/draft/documents/" + type).file(file).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private record OpenShop(String token, long shopId, UUID userId) {}
    private record SessionUser(String token, UUID userId) {}
}
