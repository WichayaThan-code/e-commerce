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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantPlanUsageIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

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
        syncPlanCatalog();
    }

    private void syncPlanCatalog() {
        jdbc.update("""
            UPDATE plans SET limit_products = 5, limit_orders_per_month = 50, limit_staff = 2, limit_storage_bytes = 104857600,
              price_monthly = 0, price_yearly = 0, description = 'ร้านเริ่มต้น', sort_order = 1 WHERE code = 'FREE'
            """);
        jdbc.update("""
            UPDATE plans SET limit_staff = NULL, limit_products = NULL, limit_orders_per_month = NULL WHERE code = 'PRO'
            """);
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
    void newShopGetsFreeAndBlankPlanCodeResolvesAsFree() throws Exception {
        var shop = openShop("plan-usage-none@example.com", "0815900001", "ร้านไม่มีแผน", "NoPlan", "1234567890901");
        var stored = jdbc.queryForObject("SELECT plan_code FROM shops WHERE shop_no = ?", String.class, shop.shopId);
        org.junit.jupiter.api.Assertions.assertEquals("FREE", stored);
        jdbc.update("UPDATE shops SET plan_code = NULL WHERE shop_no = ?", shop.shopId);
        perform(get(path(shop)), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopId").value(shop.shopId))
            .andExpect(jsonPath("$.plan.assignment").value("ASSIGNED"))
            .andExpect(jsonPath("$.plan.resolution").value("ACTIVE"))
            .andExpect(jsonPath("$.plan.code").value("FREE"))
            .andExpect(jsonPath("$.timezone").value("Asia/Bangkok"))
            .andExpect(jsonPath("$.displayPolicy.warnAtRatio").value(0.8))
            .andExpect(jsonPath("$.metrics[?(@.code=='PRODUCTS')].limit.kind").value(hasItem("FINITE")))
            .andExpect(jsonPath("$.metrics[?(@.code=='PRODUCTS')].limit.value").value(hasItem(5)))
            .andExpect(jsonPath("$.metrics[?(@.code=='ORDERS_ROLLING_30D')].period.kind").value(hasItem("ROLLING_DAYS")))
            .andExpect(jsonPath("$.metrics[?(@.code=='ORDERS_ROLLING_30D')].period.rollingDays").value(hasItem(30)))
            .andExpect(jsonPath("$.metrics[?(@.code=='STORAGE')].completeness").value(hasItem("PARTIAL")))
            .andExpect(jsonPath("$.catalog.length()").value(3))
            .andExpect(jsonPath("$.enforcement[?(@.metricCode=='STAFF')].level").value(hasItem("ENFORCED")))
            .andExpect(jsonPath("$.enforcement[?(@.metricCode=='PRODUCTS')].level").value(hasItem("ENFORCED")));
    }

    @Test
    void freePlanShowsFiniteLimitsAndRefreshIsReadOnly() throws Exception {
        var shop = openShop("plan-usage-free@example.com", "0815900002", "ร้านฟรี", "FreePlan", "1234567890902");

        var first = perform(get(path(shop)), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.assignment").value("ASSIGNED"))
            .andExpect(jsonPath("$.plan.resolution").value("ACTIVE"))
            .andExpect(jsonPath("$.plan.code").value("FREE"))
            .andExpect(jsonPath("$.metrics[?(@.code=='PRODUCTS')].limit.kind").value(hasItem("FINITE")))
            .andExpect(jsonPath("$.metrics[?(@.code=='PRODUCTS')].limit.value").value(hasItem(5)))
            .andExpect(jsonPath("$.metrics[?(@.code=='ORDERS_ROLLING_30D')].limit.value").value(hasItem(50)))
            .andExpect(jsonPath("$.metrics[?(@.code=='STAFF')].limit.value").value(hasItem(2)))
            .andExpect(jsonPath("$.metrics[?(@.code=='STORAGE')].limit.value").value(hasItem(104857600)))
            .andExpect(jsonPath("$.metrics[?(@.code=='STAFF')].used").value(hasItem(1)))
            .andReturn();

        String asOf = JsonPath.read(first.getResponse().getContentAsString(), "$.asOf");
        perform(get(path(shop)), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.asOf", not(emptyOrNullString())))
            .andExpect(jsonPath("$.plan.code").value("FREE"));
        assert asOf != null;
    }

    @Test
    void proUnlimitedProductsAndOrdersDoNotInventPercent() throws Exception {
        var shop = openShop("plan-usage-pro@example.com", "0815900003", "ร้านโปร", "ProPlan", "1234567890903");
        jdbc.update("UPDATE shops SET plan_code = ? WHERE shop_no = ?", "PRO", shop.shopId);
        perform(get(path(shop)), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.code").value("PRO"))
            .andExpect(jsonPath("$.metrics[?(@.code=='PRODUCTS')].limit.kind").value(hasItem("UNLIMITED")))
            .andExpect(jsonPath("$.metrics[?(@.code=='PRODUCTS')].status").value(hasItem("UNLIMITED")))
            .andExpect(jsonPath("$.metrics[?(@.code=='PRODUCTS')].usedRatio").value(hasItem(nullValue())))
            .andExpect(jsonPath("$.metrics[?(@.code=='ORDERS_ROLLING_30D')].limit.kind").value(hasItem("UNLIMITED")))
            .andExpect(jsonPath("$.metrics[?(@.code=='STAFF')].limit.kind").value(hasItem("UNLIMITED")))
            .andExpect(jsonPath("$.metrics[?(@.code=='STAFF')].status").value(hasItem("UNLIMITED")));
    }

    @Test
    void unknownPlanCodeIsNotUnlimitedAndCrossShopDenied() throws Exception {
        var a = openShop("plan-usage-a@example.com", "0815900004", "ร้านเอ", "PlanA", "1234567890904");
        var b = openShop("plan-usage-b@example.com", "0815900005", "ร้านบี", "PlanB", "1234567890905");
        // Insert retired/orphan-like code without FK violation: use real FK then delete active flag path —
        // unknown means code not in plans; temporarily drop FK check via inserting into shops only if FK allows.
        // Use a code that does not exist — FK may block. Prefer inactive plan row.
        Integer legacyCount = jdbc.queryForObject("SELECT COUNT(*) FROM plans WHERE code = ?", Integer.class, "LEGACY_X");
        if (legacyCount != null && legacyCount > 0) {
            jdbc.update("UPDATE plans SET active = FALSE, name = ?, limit_products = 10, limit_orders_per_month = 10, "
                + "limit_staff = 1, limit_storage_bytes = 1024, updated_at = CURRENT_TIMESTAMP WHERE code = ?",
                "Legacy X", "LEGACY_X");
        } else {
            jdbc.update("INSERT INTO plans (code, name, price_monthly, price_yearly, limit_products, limit_orders_per_month, "
                + "limit_staff, limit_storage_bytes, description, sort_order, active, created_at, updated_at) "
                + "VALUES ('LEGACY_X', 'Legacy X', 0, 0, 10, 10, 1, 1024, 'legacy', 99, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        }
        jdbc.update("UPDATE shops SET plan_code = ? WHERE shop_no = ?", "LEGACY_X", a.shopId);

        perform(get(path(a)), a.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.resolution").value("RETIRED"))
            .andExpect(jsonPath("$.metrics[?(@.code=='PRODUCTS')].limit.kind").value(hasItem("FINITE")))
            .andExpect(jsonPath("$.metrics[?(@.code=='PRODUCTS')].limit.value").value(hasItem(10)));

        perform(get(path(a)), b.token).andExpect(status().isNotFound());
        perform(get(path(b)), a.token).andExpect(status().isNotFound());
    }

    private static String path(OpenShop shop) {
        return "/api/merchant/shops/" + shop.shopId + "/plan-usage";
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
            var approver = users.save(UserAccount.systemAccount("plan-usage-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"plan-usage-approver@example.com","password":"Password1"}
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
