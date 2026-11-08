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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantShopAuditIntegrationTests {
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
        try { jdbc.update("DELETE FROM shop_invitations"); } catch (Exception ignored) { }
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
    void securityActivityScopedToShopAndPermission() throws Exception {
        var shopA = openShop("audit-a@example.com", "0815900001", "ร้านออดิทเอ", "AuditA", "1234567890901");
        var shopB = openShop("audit-b@example.com", "0815900002", "ร้านออดิทบี", "AuditB", "1234567890902");

        var created = perform(post(base(shopA) + "/team/invitations").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"audit-viewer@example.com","role":"STORE_VIEWER"}
            """), shopA.token).andExpect(status().isOk()).andReturn();
        String inviteToken = extractToken(JsonPath.read(created.getResponse().getContentAsString(), "$.inviteUrl"));
        var viewer = registerVerified("audit-viewer@example.com", "0815900013");
        perform(post("/api/invitations/" + inviteToken + "/accept"), viewer.token).andExpect(status().isOk());

        perform(patch(base(shopA) + "/team/members/" + viewer.userId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"STORE_SALES\"}"), shopA.token)
            .andExpect(status().isOk());

        // Owner has AUDIT_READ — sees role assignment for shop A only
        var listed = perform(get(base(shopA) + "/security-activity"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.action=='SHOP_MEMBER_ROLE_ASSIGNED')]", not(empty())))
            .andExpect(jsonPath("$.supportedActions", hasItem("SHOP_MEMBER_ROLE_ASSIGNED")))
            .andExpect(jsonPath("$.items[*].actorName", everyItem(not(containsString("@")))))
            .andReturn();
        java.util.List<Number> eventIds = JsonPath.read(listed.getResponse().getContentAsString(),
            "$.items[?(@.action=='SHOP_MEMBER_ROLE_ASSIGNED')].id");
        Number eventId = eventIds.get(0);

        perform(get(base(shopA) + "/security-activity/" + eventId), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.action").value("SHOP_MEMBER_ROLE_ASSIGNED"))
            .andExpect(jsonPath("$.ipAddress").doesNotExist())
            .andExpect(jsonPath("$.userAgent").doesNotExist())
            .andExpect(jsonPath("$.actor.email").doesNotExist())
            .andExpect(jsonPath("$.before.password").doesNotExist())
            .andExpect(jsonPath("$.after.password").doesNotExist())
            .andExpect(jsonPath("$.after.token").doesNotExist());

        // Cross-shop: shop B owner cannot read shop A event id
        perform(get(base(shopB) + "/security-activity/" + eventId), shopB.token)
            .andExpect(status().isNotFound());

        // Viewer lacks AUDIT_READ
        perform(get(base(shopA) + "/security-activity"), viewer.token)
            .andExpect(status().isForbidden());

        // Unsupported action filter rejected
        perform(get(base(shopA) + "/security-activity?action=CONTACT_REVEALED"), shopA.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_ACTION"));

        // Shop B list does not leak shop A events
        perform(get(base(shopB) + "/security-activity"), shopB.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.id==" + eventId + ")]", empty()));
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
            var approver = users.save(UserAccount.systemAccount("audit-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"audit-approver@example.com","password":"Password1"}
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
