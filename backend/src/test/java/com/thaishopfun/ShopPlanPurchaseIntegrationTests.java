package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import com.thaishopfun.merchant.OwnerAuditLogRepository;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ShopMemberRepository;
import com.thaishopfun.merchant.ShopReinstatementRequestRepository;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.plan.ShopPlanPurchaseService;
import com.thaishopfun.merchant.plan.ShopPlanSubscription;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopPlanPurchaseIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ShopRepository shops;
    @Autowired ProductRepository products;
    @Autowired ShopMemberRepository shopMembers;
    @Autowired ShopReinstatementRequestRepository reinstatements;
    @Autowired MerchantDocumentRepository documents;
    @Autowired MerchantApplicationRepository applications;
    @Autowired OwnerAuditLogRepository ownerAudits;
    @Autowired OwnerRepository owners;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformRoleRepository platformRoles;
    @Autowired ShopPlanPurchaseService purchases;

    private String adminToken;

    @BeforeEach
    void clean() {
        adminToken = null;
        wipe();
        jdbc.update("""
            UPDATE plans SET price_monthly = 290, price_yearly = 2900 WHERE code = 'BASIC'
            """);
        jdbc.update("""
            UPDATE plans SET price_monthly = 790, price_yearly = 7900 WHERE code = 'PRO'
            """);
    }

    @AfterEach
    void after() {
        wipe();
    }

    @Test
    void createRequiresBillingCycle() throws Exception {
        var shop = openShop("buy-cycle@example.com", "0817000001", "ร้านรอบบิล", "CycleShop", "1234567892001");
        perform(post("/api/merchant/shops/" + shop.shopId + "/plan-purchases"), shop.token, "{\"planCode\":\"BASIC\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BILLING_CYCLE_REQUIRED"));
    }

    @Test
    void pricesComeFromDatabaseNotClient() throws Exception {
        var shop = openShop("buy-price@example.com", "0817000002", "ร้านราคา", "PriceShop", "1234567892002");
        perform(post("/api/merchant/shops/" + shop.shopId + "/plan-purchases"), shop.token,
            "{\"planCode\":\"BASIC\",\"billingCycle\":\"MONTHLY\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value(290.0));
        perform(post("/api/merchant/shops/" + shop.shopId + "/plan-purchases/" +
                JsonPath.read(perform(get("/api/merchant/shops/" + shop.shopId + "/plan-purchases"), shop.token)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.openOrder.id")
                + "/cancel"), shop.token)
            .andExpect(status().isOk());
        perform(post("/api/merchant/shops/" + shop.shopId + "/plan-purchases"), shop.token,
            "{\"planCode\":\"PRO\",\"billingCycle\":\"YEARLY\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value(7900.0));
    }

    @Test
    void duplicateOpenOrderRejected() throws Exception {
        var shop = openShop("buy-dup@example.com", "0817000003", "ร้านซ้ำ", "DupShop", "1234567892003");
        perform(post("/api/merchant/shops/" + shop.shopId + "/plan-purchases"), shop.token,
            "{\"planCode\":\"BASIC\",\"billingCycle\":\"MONTHLY\"}").andExpect(status().isOk());
        perform(post("/api/merchant/shops/" + shop.shopId + "/plan-purchases"), shop.token,
            "{\"planCode\":\"PRO\",\"billingCycle\":\"YEARLY\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PLAN_PURCHASE_OPEN"));
    }

    @Test
    void approveSetsPlanAndExpiry_monthlyAndRenewal() throws Exception {
        var shop = openShop("buy-ok@example.com", "0817000004", "ร้านอนุมัติ", "OkShop", "1234567892004");
        ensureAdmin();
        String id = JsonPath.read(perform(post("/api/merchant/shops/" + shop.shopId + "/plan-purchases"), shop.token,
            "{\"planCode\":\"BASIC\",\"billingCycle\":\"MONTHLY\"}").andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), "$.id");
        var file = new MockMultipartFile("file", "slip.png", "image/png", PNG);
        mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/plan-purchases/" + id + "/proof")
            .file(file).header("Authorization", "Bearer " + shop.token)).andExpect(status().isOk());
        perform(post("/api/admin/plan-purchases/" + id + "/approve"), adminToken).andExpect(status().isOk());
        assertEquals("BASIC", jdbc.queryForObject("SELECT plan_code FROM shops WHERE shop_no = ?", String.class, shop.shopId));
        assertEquals("MONTHLY", jdbc.queryForObject("SELECT plan_billing_cycle FROM shops WHERE shop_no = ?", String.class, shop.shopId));
        Instant firstEnd = jdbc.queryForObject("SELECT plan_expires_at FROM shops WHERE shop_no = ?", Timestamp.class, shop.shopId).toInstant();
        assertTrue(firstEnd.isAfter(Instant.now().plus(20, ChronoUnit.DAYS)));

        perform(post("/api/admin/plan-purchases/" + id + "/approve"), adminToken)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PLAN_PURCHASE_ALREADY_APPROVED"));

        String renewId = JsonPath.read(perform(post("/api/merchant/shops/" + shop.shopId + "/plan-purchases"), shop.token,
            "{\"planCode\":\"BASIC\",\"billingCycle\":\"YEARLY\"}").andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/plan-purchases/" + renewId + "/proof")
            .file(file).header("Authorization", "Bearer " + shop.token)).andExpect(status().isOk());
        perform(post("/api/admin/plan-purchases/" + renewId + "/approve"), adminToken).andExpect(status().isOk());
        Instant secondEnd = jdbc.queryForObject("SELECT plan_expires_at FROM shops WHERE shop_no = ?", Timestamp.class, shop.shopId).toInstant();
        assertTrue(secondEnd.isAfter(firstEnd.plus(300, ChronoUnit.DAYS)));
        assertEquals("YEARLY", jdbc.queryForObject("SELECT plan_billing_cycle FROM shops WHERE shop_no = ?", String.class, shop.shopId));
    }

    @Test
    void expiryRevertsToFreeWithoutDeletingProducts() throws Exception {
        var shop = openShop("buy-exp@example.com", "0817000005", "ร้านหมดอายุ", "ExpShop", "1234567892005");
        perform(post("/api/merchant/shops/" + shop.shopId + "/products"), shop.token,
            "{\"nameTh\":\"สินค้า\",\"nameEn\":\"Item\",\"productType\":\"SIMPLE\",\"basePrice\":40,\"skus\":[{\"skuCode\":\"E1\",\"price\":40}]}")
            .andExpect(status().isOk());
        jdbc.update("UPDATE shops SET plan_code = 'BASIC', plan_billing_cycle = 'MONTHLY', plan_expires_at = ? WHERE shop_no = ?",
            Timestamp.from(Instant.now().minusSeconds(60)), shop.shopId);
        UUID shopUuid = jdbc.queryForObject("SELECT id FROM shops WHERE shop_no = ?", UUID.class, shop.shopId);
        jdbc.update("""
            INSERT INTO shop_plan_subscriptions
              (id, shop_id, plan_code, billing_cycle, amount, currency, status, starts_at, ends_at, created_at, updated_at)
            VALUES (?, ?, 'BASIC', 'MONTHLY', 290, 'THB', 'ACTIVE', ?, ?, ?, ?)
            """, UUID.randomUUID(), shopUuid, Timestamp.from(Instant.now().minus(40, ChronoUnit.DAYS)),
            Timestamp.from(Instant.now().minusSeconds(60)), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        int n = purchases.expireDuePlans();
        assertTrue(n >= 1);
        assertEquals("FREE", jdbc.queryForObject("SELECT plan_code FROM shops WHERE shop_no = ?", String.class, shop.shopId));
        Long products = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE shop_id = ? AND deleted_at IS NULL", Long.class, shopUuid);
        assertEquals(1L, products);
        assertEquals(ShopPlanSubscription.EXPIRED, jdbc.queryForObject(
            "SELECT status FROM shop_plan_subscriptions WHERE shop_id = ? ORDER BY created_at DESC LIMIT 1",
            String.class, shopUuid));
    }

    private void wipe() {
        try { jdbc.update("DELETE FROM shop_plan_subscriptions"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM media_files"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM product_skus"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM product_images"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM products"); } catch (Exception ignored) { }
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

    private void ensureAdmin() throws Exception {
        if (adminToken != null) return;
        String email = "plan-admin-" + UUID.randomUUID() + "@example.com";
        var approver = users.save(UserAccount.systemAccount(email, passwords.encode("Password1")));
        platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
        var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"identity":"%s","password":"Password1"}""".formatted(email))).andExpect(status().isOk()).andReturn();
        adminToken = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
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
        ensureAdmin();
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

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private record OpenShop(String token, long shopId, UUID userId) {}
    private record SessionUser(String token, UUID userId) {}
}
