package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.audit.PlatformAuditLog;
import com.thaishopfun.audit.PlatformAuditLogRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.merchant.ShopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * If inserting the business audit row fails, the suspend/reactivate status change must roll back with it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuspendAuditRollbackTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final String REASON = "{\"reason\":\"ละเมิดนโยบายการขายสินค้า\",\"reasonCode\":\"POLICY_VIOLATION\"}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired UserAccountRepository users;
    @Autowired PlatformRoleRepository platformRoles;
    @Autowired ShopRepository shops;
    @MockitoSpyBean PlatformAuditLogRepository auditLogs;

    private String adminToken;
    private String adminEmail;

    @AfterEach
    void clean() {
        reset(auditLogs);
        for (var t : new String[]{
            "shop_reinstatement_requests", "shop_members", "merchant_documents",
            "shops", "merchant_applications", "owners", "audit_logs", "platform_roles", "refresh_tokens"
        }) {
            jdbc.update("DELETE FROM " + t);
        }
        users.deleteAll();
        adminToken = null;
        adminEmail = null;
    }

    @Test
    void auditInsertFailureRollsBackShopSuspend() throws Exception {
        var shop = openShop("roll-a@example.com", "0816000111", "ร้านโรลแบ็ก", "RollShop", "1234567890611", true);
        assertEquals("ACTIVE", shopStatus(shop.shopId()));

        doThrow(new RuntimeException("forced audit failure")).when(auditLogs).save(any(PlatformAuditLog.class));
        try {
            assertTrue(performExpectingFailure(post("/api/admin/shops/" + shop.shopId() + "/suspend")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(REASON)));
        } finally {
            reset(auditLogs);
        }

        assertEquals("ACTIVE", shopStatus(shop.shopId()));
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action = 'SHOP_SUSPENDED'", Integer.class).intValue());
    }

    @Test
    void auditInsertFailureRollsBackUserSuspend() throws Exception {
        var shop = openShop("roll-u@example.com", "0816000112", "ร้านโรลยู", "RollUser", "1234567890612", true);
        var userId = userId(shop.token());
        assertEquals("ACTIVE", userStatus(userId));

        doThrow(new RuntimeException("forced audit failure")).when(auditLogs).save(any(PlatformAuditLog.class));
        try {
            assertTrue(performExpectingFailure(post("/api/admin/users/" + userId + "/suspend")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"ทดสอบ\"}")));
        } finally {
            reset(auditLogs);
        }

        assertEquals("ACTIVE", userStatus(userId));
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action = 'ACCOUNT_SUSPENDED' AND resource_id = ?",
            Integer.class, userId).intValue());
    }

    private boolean performExpectingFailure(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        try {
            var status = mvc.perform(request).andReturn().getResponse().getStatus();
            return status >= 500;
        } catch (Exception ex) {
            // MockMvc rethrows unhandled RuntimeException from the controller/service
            Throwable root = ex;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            return root.getMessage() != null && root.getMessage().contains("forced audit failure");
        }
    }

    private String shopStatus(long shopNo) {
        return jdbc.queryForObject("SELECT status FROM shops WHERE shop_no = ?", String.class, shopNo);
    }

    private String userStatus(UUID userId) {
        return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, userId);
    }

    private UUID userId(String token) throws Exception {
        var me = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(me, "$.id"));
    }

    private record OpenShop(String token, long shopId) {}

    private OpenShop openShop(String email, String phone, String shopName, String shopNameEn, String citizenId, boolean approve) throws Exception {
        var seller = token(email, phone);
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
            {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
            """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
            {"ownerType":"PERSON","title":"นาย","firstName":"สมชาย","lastName":"ขายของ","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"%s","email":"%s","houseNo":"9","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมชาย ขายของ"}
            """.formatted(citizenId, phone, email))).andExpect(status().isOk());
        var file = new MockMultipartFile("file", "identity.png", "image/png", PNG);
        mvc.perform(multipart("/api/merchant-applications/draft/documents/IDENTITY").file(file).header("Authorization", "Bearer " + seller)).andExpect(status().isOk());
        mvc.perform(multipart("/api/merchant-applications/draft/documents/ADDRESS").file(file).header("Authorization", "Bearer " + seller)).andExpect(status().isOk());
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + seller)).andExpect(status().isOk());
        var listed = mvc.perform(get("/api/merchant/shops").header("Authorization", "Bearer " + seller)).andExpect(status().isOk()).andReturn();
        Number shopId = JsonPath.read(listed.getResponse().getContentAsString(), "$[0].shopId");
        if (approve) {
            mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve").header("Authorization", "Bearer " + adminToken())).andExpect(status().isOk());
        }
        return new OpenShop(seller, shopId.longValue());
    }

    private String adminToken() throws Exception {
        if (adminToken == null) {
            adminEmail = "pla-admin-roll-" + UUID.randomUUID() + "@example.com";
            var approver = users.save(UserAccount.systemAccount(adminEmail, passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"%s","password":"Password1"}
                """.formatted(adminEmail))).andExpect(status().isOk()).andReturn();
            adminToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        }
        return adminToken;
    }

    private String token(String email, String phone) throws Exception {
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"สมชาย","lastName":"ผู้ซื้อ","email":"%s","phone":"%s","password":"Password1"}
            """.formatted(email, phone))).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
    }
}
