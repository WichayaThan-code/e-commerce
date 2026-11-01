package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.customer.CustomerAuditLogRepository;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDashboardSummaryTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final String BASE = "/api/admin/dashboard/summary";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerAuditLogRepository customerAudits;
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
        wipe();
    }

    @AfterEach
    void tearDown() {
        wipeOrderChildren();
    }

    private void wipe() {
        adminToken = null;
        wipeOrderChildren();
        jdbc.update("DELETE FROM inventory_ledger");
        jdbc.update("DELETE FROM inventory_levels");
        jdbc.update("DELETE FROM product_skus");
        jdbc.update("DELETE FROM product_images");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM shop_customer_tags");
        jdbc.update("DELETE FROM shop_customer_addresses");
        jdbc.update("UPDATE shop_customers SET merged_into_id = NULL");
        jdbc.update("DELETE FROM shop_customers");
        customerAudits.deleteAll();
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

    private void wipeOrderChildren() {
        jdbc.update("DELETE FROM merchant_notification_reads");
        jdbc.update("DELETE FROM merchant_notification_cursor");
        jdbc.update("DELETE FROM order_status_history");
        jdbc.update("DELETE FROM refund_status_history");
        jdbc.update("DELETE FROM refunds");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM order_addresses");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
    }

    @Test
    void summaryCrossShopPaidRuleDayBoundaryTopShopsCountsAndAuth() throws Exception {
        var shopA = openShop("ad-a@example.com", "0815500001", "ร้านแอดเอ", "Admin A", "1234567890801");
        var shopB = openShop("ad-b@example.com", "0815500002", "ร้านแอดบี", "Admin B", "1234567890802");
        var a = shops.findByShopId(shopA.shopId).orElseThrow().getUuid();
        var b = shops.findByShopId(shopB.shopId).orElseThrow().getUuid();

        // Bangkok day boundary: 23:59 on Oct 30 / 00:01 on Oct 31
        var oPrev = order(a, "A30-2359", "100.00", "MERCHANT_CONFIRMED", "PAID", "2026-10-30T16:59:00Z");
        var oA1 = order(a, "A31-0001", "200.00", "PACKED", "PAID", "2026-10-30T17:01:00Z");
        order(a, "A31-CXL", "50.00", "CANCELLED", "PAID", "2026-10-31T05:00:00Z");
        order(a, "A31-RF", "70.00", "REFUNDED", "PAID", "2026-10-31T05:00:00Z");
        order(a, "A31-UNPAID", "30.00", "PENDING_PAYMENT", "UNPAID", "2026-10-31T05:00:00Z");
        var oAShip = order(a, "A31-SHIP", "400.00", "DISPATCHED", "PAID", "2026-10-31T06:00:00Z");
        var oBTop = order(b, "B31-TOP", "900.00", "PACKING", "PAID", "2026-10-31T05:00:00Z");
        var oB2 = order(b, "B31-2", "50.00", "PACKED", "PAID", "2026-10-31T06:00:00Z");
        payment(oPrev, a, "PROMPTPAY", "100.00", "CAPTURED");
        payment(oA1, a, "PROMPTPAY", "200.00", "CAPTURED");
        payment(oAShip, a, "BANK_TRANSFER", "400.00", "CAPTURED");
        payment(oBTop, b, "COD", "900.00", "CAPTURED");
        payment(oB2, b, "COD", "50.00", "CAPTURED");

        var body = perform(get(BASE).param("from", "2026-10-31").param("to", "2026-10-31"), adminToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("Asia/Bangkok"))
            .andExpect(jsonPath("$.currency").value("THB"))
            .andExpect(jsonPath("$.from").value("2026-10-31"))
            .andExpect(jsonPath("$.to").value("2026-10-31"))
            .andExpect(jsonPath("$.totalSales").value(1550.00))
            .andExpect(jsonPath("$.paidOrderCount").value(4))
            .andExpect(jsonPath("$.orderCount").value(7))
            .andExpect(jsonPath("$.series.length()").value(1))
            .andExpect(jsonPath("$.series[0].sales").value(1550.00))
            .andExpect(jsonPath("$.series[0].orders").value(4))
            .andExpect(jsonPath("$.topShops.length()").value(2))
            .andExpect(jsonPath("$.topShops[0].shopName").value("ร้านแอดบี"))
            .andExpect(jsonPath("$.topShops[0].sales").value(950.00))
            .andExpect(jsonPath("$.topShops[0].orderCount").value(2))
            .andExpect(jsonPath("$.topShops[0].shopId").value((int) shopB.shopId))
            .andExpect(jsonPath("$.topShops[0].slug").isString())
            .andExpect(jsonPath("$.topShops[1].shopName").value("ร้านแอดเอ"))
            .andExpect(jsonPath("$.topShops[1].sales").value(600.00))
            .andExpect(jsonPath("$.counts.shopsTotal").value(2))
            .andExpect(jsonPath("$.counts.shopsApproved").value(2))
            .andExpect(jsonPath("$.counts.pendingApplications").value(0))
            .andExpect(jsonPath("$.counts.usersTotal").value(greaterThanOrEqualTo(3)))
            .andExpect(jsonPath("$.paymentMethods.length()").value(3))
            .andExpect(jsonPath("$.paymentMethods[0].method").value("COD"))
            .andExpect(jsonPath("$.paymentMethods[0].amount").value(950.00))
            .andExpect(jsonPath("$.paymentMethods[0].orderCount").value(2))
            .andExpect(jsonPath("$.paymentMethods[1].method").value("BANK_TRANSFER"))
            .andExpect(jsonPath("$.paymentMethods[1].amount").value(400.00))
            .andExpect(jsonPath("$.paymentMethods[2].method").value("PROMPTPAY"))
            .andExpect(jsonPath("$.paymentMethods[2].amount").value(200.00))
            .andExpect(jsonPath("$.recentOrders.length()").value(greaterThanOrEqualTo(5)))
            .andExpect(jsonPath("$.recentOrders[0].orderNumber").isString())
            .andExpect(jsonPath("$.recentOrders[0].shopName").isString())
            .andExpect(jsonPath("$.recentOrders[0].phone").doesNotExist())
            .andExpect(jsonPath("$.recentOrders[0].email").doesNotExist())
            .andExpect(jsonPath("$.recentOrders[0].userId").doesNotExist())
            .andExpect(jsonPath("$.recentOrders[0].buyerUserId").doesNotExist())
            .andExpect(jsonPath("$.topShops[0].phone").doesNotExist())
            .andExpect(jsonPath("$.topShops[0].email").doesNotExist())
            .andExpect(jsonPath("$.recentUsers").isArray())
            .andReturn().getResponse().getContentAsString();

        // paymentMethods amounts must sum to totalSales (cross-shop, paid only)
        @SuppressWarnings("unchecked")
        var methods = (java.util.List<java.util.Map<String, Object>>) JsonPath.read(body, "$.paymentMethods");
        BigDecimal methodSum = methods.stream()
            .map(m -> new BigDecimal(String.valueOf(m.get("amount"))))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, methodSum.compareTo(new BigDecimal("1550.00")));

        String dumped = body.toLowerCase();
        assertFalse(dumped.contains("\"phone\""));
        assertFalse(dumped.contains("\"email\""));
        assertFalse(dumped.contains("\"userid\""));
        assertFalse(dumped.contains("\"buyeruserid\""));
        assertFalse(dumped.contains("ad-a@example.com"));
        assertFalse(dumped.contains("0815500001"));

        // previous day (Oct 30) paid = 100
        perform(get(BASE).param("from", "2026-10-31").param("to", "2026-10-31"), adminToken())
            .andExpect(jsonPath("$.previousSales").value(100.00))
            .andExpect(jsonPath("$.previousPaidOrderCount").value(1));

        perform(get(BASE).param("range", "nope"), adminToken()).andExpect(status().isBadRequest());
        perform(get(BASE).param("from", "2026-10-31"), adminToken()).andExpect(status().isBadRequest());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());

        var seller = shopA.token;
        perform(get(BASE), seller).andExpect(status().isForbidden());
    }

    @Test
    void summaryPendingApplicationCountAndDefaultToday() throws Exception {
        openShop("ad-ok@example.com", "0815500011", "ร้านอนุมัติ", "Ok Shop", "1234567890811");
        // leave one application pending (submitted, not approved)
        var seller = token("ad-pend@example.com", "0815500012");
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
            {"shopName":"ร้านรอ","shopNameEn":"Pending Shop","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
            """)).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
            {"ownerType":"PERSON","title":"นาย","firstName":"สมชาย","lastName":"รออนุมัติ","citizenId":"1234567890812","birthDate":"1980-08-15","nationality":"ไทย","phone":"0815500012","email":"ad-pend@example.com","houseNo":"9","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมชาย รออนุมัติ"}
            """)).andExpect(status().isOk());
        uploadDocument(seller, "IDENTITY");
        uploadDocument(seller, "ADDRESS");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + seller)).andExpect(status().isOk());

        var todayBody = perform(get(BASE), adminToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.range").value("today"))
            .andExpect(jsonPath("$.counts.shopsTotal").value(2))
            .andExpect(jsonPath("$.counts.shopsApproved").value(1))
            .andExpect(jsonPath("$.counts.shopsPendingReview").value(1))
            .andExpect(jsonPath("$.counts.pendingApplications").value(1))
            .andExpect(jsonPath("$.recentUsers.length()").value(greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.recentUsers[0].displayName").isString())
            .andExpect(jsonPath("$.recentUsers[0].emailMasked").value(containsString("***@")))
            .andExpect(jsonPath("$.recentUsers[0].role").isString())
            .andExpect(jsonPath("$.recentUsers[0].createdAt").isString())
            .andExpect(jsonPath("$.recentUsers[0].userId").doesNotExist())
            .andExpect(jsonPath("$.recentUsers[0].phone").doesNotExist())
            .andExpect(jsonPath("$.recentUsers[0].email").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        String dumped = todayBody.toLowerCase();
        assertFalse(dumped.contains("\"userid\""));
        assertFalse(dumped.contains("\"phone\""));
        assertFalse(dumped.contains("\"email\""));
        assertFalse(dumped.contains("ad-ok@example.com"));
        assertFalse(dumped.contains("ad-pend@example.com"));
        assertFalse(dumped.contains("0815500011"));
        assertTrue(JsonPath.read(todayBody, "$.recentUsers[0].emailMasked").toString().contains("***@"));

        // Outside the selected window → empty recentUsers
        perform(get(BASE).param("from", "2020-01-01").param("to", "2020-01-02"), adminToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recentUsers.length()").value(0));
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private UUID order(UUID shopUuid, String number, String total, String status, String payment, String placedAtUtc) {
        var placed = Timestamp.from(Instant.parse(placedAtUtc));
        var now = Timestamp.from(Instant.now());
        var id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO orders (id, shop_id, order_number, channel, status, payment_status, fulfillment_status, currency,
              grand_total, placed_at, merchant_confirm_status, created_date_time, modified_date_time)
            VALUES (?, ?, ?, 'STOREFRONT', ?, ?, 'UNFULFILLED', 'THB', ?, ?, 'AWAITING', ?, ?)
            """, id, shopUuid, number, status, payment, new BigDecimal(total), placed, placed, now);
        return id;
    }

    private void payment(UUID orderId, UUID shopUuid, String provider, String amount, String status) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
            INSERT INTO payments (id, order_id, shop_id, provider, amount, currency, status, idempotency_key,
              paid_at, attempt_no, created_date_time, modified_date_time)
            VALUES (?, ?, ?, ?, ?, 'THB', ?, ?, ?, 1, ?, ?)
            """, UUID.randomUUID(), orderId, shopUuid, provider, new BigDecimal(amount), status,
            "adm-" + UUID.randomUUID(), now, now, now);
    }

    private OpenShop openShop(String email, String phone, String shopName, String shopNameEn, String citizenId) throws Exception {
        var seller = token(email, phone);
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
            {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
            """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
            {"ownerType":"PERSON","title":"นาย","firstName":"สมชาย","lastName":"ขายของ","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"%s","email":"%s","houseNo":"9","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมชาย ขายของ"}
            """.formatted(citizenId, phone, email))).andExpect(status().isOk());
        uploadDocument(seller, "IDENTITY");
        uploadDocument(seller, "ADDRESS");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + seller)).andExpect(status().isOk());
        var listed = mvc.perform(get("/api/merchant/shops").header("Authorization", "Bearer " + seller)).andExpect(status().isOk()).andReturn();
        Number shopId = JsonPath.read(listed.getResponse().getContentAsString(), "$[0].shopId");
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve").header("Authorization", "Bearer " + adminToken())).andExpect(status().isOk());
        return new OpenShop(seller, shopId.longValue());
    }

    private String adminToken() throws Exception {
        if (adminToken == null) {
            var approver = users.save(UserAccount.systemAccount("ad-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"ad-approver@example.com","password":"Password1"}
                """)).andExpect(status().isOk()).andReturn();
            adminToken = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
        }
        return adminToken;
    }

    private String token(String email, String phone) throws Exception {
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"สมชาย","lastName":"ขายของ","email":"%s","phone":"%s","password":"Password1"}
            """.formatted(email, phone))).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
    }

    private void uploadDocument(String token, String type) throws Exception {
        var file = new MockMultipartFile("file", type.toLowerCase() + ".png", "image/png", PNG);
        mvc.perform(multipart("/api/merchant-applications/draft/documents/" + type).file(file).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private record OpenShop(String token, long shopId) {}
}
