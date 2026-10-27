package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PermissionMatrix;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.customer.CustomerAuditLogRepository;
import com.thaishopfun.customer.CustomerInsightsService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerInsightsTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PermissionMatrix permissionMatrix;
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
        adminToken = null;
        jdbc.update("DELETE FROM order_addresses");
        jdbc.update("DELETE FROM orders");
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

    @Test
    void unacceptedOrdersAreExcludedAndStorefrontBuyerAppears() throws Exception {
        var shop = openShop("ci-store@example.com", "0814100001", "ร้านอินไซต์", "Insight Shop", "1234567890401");
        var shopUuid = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        var buyer = registerBuyer("ci-buyer@example.com", "0814100099");

        insertOrder(shopUuid, null, buyer, "CI-UNACC", "500.00", "PENDING_PAYMENT", "STOREFRONT", null, Instant.now());
        insertOrder(shopUuid, null, buyer, "CI-ACC", "1500.00", "PAID", "STOREFRONT", Instant.now(), Instant.now());
        insertAddress(orderIdByNumber(shopUuid, "CI-ACC"), "สมชาย ซื้อของ", "0814100099");

        perform(get(base(shop) + "/customer-insights"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].orderCount").value(1))
            .andExpect(jsonPath("$.items[0].totalSpent").value(1500.0))
            .andExpect(jsonPath("$.items[0].displayName").value("สมชาย ซื้อของ"))
            .andExpect(jsonPath("$.items[0].shopCustomerId").doesNotExist())
            .andExpect(jsonPath("$.items[0].key").isString())
            .andExpect(jsonPath("$.items[0].channel").value("STOREFRONT"));

        String key = JsonPath.read(perform(get(base(shop) + "/customer-insights"), shop.token)
            .andReturn().getResponse().getContentAsString(), "$.items[0].key");
        assertTrue(key.startsWith("c1"));
        assertFalse(key.contains(buyer.toString()));
        assertFalse(key.contains(buyer.toString().replace("-", "")));
        byte[] blob = Base64.getUrlDecoder().decode(key.substring(2));
        String asLatin = new String(blob, StandardCharsets.ISO_8859_1);
        assertFalse(asLatin.contains("u:"));
        assertFalse(asLatin.contains(buyer.toString()));
        String keyAgain = JsonPath.read(perform(get(base(shop) + "/customer-insights"), shop.token)
            .andReturn().getResponse().getContentAsString(), "$.items[0].key");
        assertEquals(key, keyAgain);

        var other = openShop("ci-store-b@example.com", "0814100011", "ร้านอื่นคีย์", "Other Key Shop", "1234567890411");
        perform(get(base(other) + "/customer-insights/" + key), other.token)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        String legacy = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("u:" + buyer).getBytes(StandardCharsets.UTF_8));
        perform(get(base(shop) + "/customer-insights/" + legacy), shop.token)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        perform(get(base(shop) + "/orders").param("customer", legacy), shop.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CUSTOMER_KEY_INVALID"));
        perform(get(base(other) + "/orders").param("customer", key), other.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CUSTOMER_KEY_INVALID"));
    }

    @Test
    void shopCustomerAggregatesSortAndStats() throws Exception {
        var shop = openShop("ci-crm@example.com", "0814100002", "ร้าน CRM", "CRM Shop", "1234567890402");
        var shopUuid = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        String vipId = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"ลูกค้า VIP","phone":"0812223333","email":"vip@example.com","source":"LINE"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String midId = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"ลูกค้าประจำ","phone":"0812224444","source":"SHOPEE"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        var now = Instant.now();
        insertOrder(shopUuid, UUID.fromString(vipId), null, "V1", "6000.00", "COMPLETED", "LINE_OA", now.minus(2, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS));
        insertOrder(shopUuid, UUID.fromString(vipId), null, "V2", "5000.00", "COMPLETED", "LINE_OA", now.minus(1, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        insertOrder(shopUuid, UUID.fromString(midId), null, "M1", "100.00", "COMPLETED", "SHOPEE", now.minus(5, ChronoUnit.DAYS), now.minus(5, ChronoUnit.DAYS));
        insertOrder(shopUuid, UUID.fromString(midId), null, "M2", "200.00", "CANCELLED", "SHOPEE", now.minus(3, ChronoUnit.DAYS), now.minus(3, ChronoUnit.DAYS));
        insertOrder(shopUuid, UUID.fromString(midId), null, "M3", "150.00", "COMPLETED", "SHOPEE", now, now);

        perform(get(base(shop) + "/customer-insights?sort=totalSpent&direction=desc"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[0].displayName").value("ลูกค้า VIP"))
            .andExpect(jsonPath("$.items[0].segment").value("VIP"))
            .andExpect(jsonPath("$.items[0].orderCount").value(2))
            .andExpect(jsonPath("$.items[0].totalSpent").value(11000.0))
            .andExpect(jsonPath("$.items[1].segment").value("REPEAT"))
            .andExpect(jsonPath("$.items[1].orderCount").value(3))
            .andExpect(jsonPath("$.items[1].totalSpent").value(250.0))
            .andExpect(jsonPath("$.stats.totalCustomers").value(2))
            .andExpect(jsonPath("$.stats.vipCustomers").value(1))
            .andExpect(jsonPath("$.stats.repeatCustomers").value(2))
            .andExpect(jsonPath("$.stats.totalRevenue").value(11250.0));

        perform(get(base(shop) + "/customer-insights?sort=orderCount&direction=desc"), shop.token)
            .andExpect(jsonPath("$.items[0].displayName").value("ลูกค้าประจำ"));
        perform(get(base(shop) + "/customer-insights?sort=lastOrderAt&direction=desc"), shop.token)
            .andExpect(jsonPath("$.items[0].displayName").value("ลูกค้าประจำ"));
    }

    @Test
    void piiMaskingPermissionsAndIsolation() throws Exception {
        var shop = openShop("ci-pii@example.com", "0814100003", "ร้าน PII", "PII Shop", "1234567890403");
        var other = openShop("ci-other@example.com", "0814100004", "ร้านอื่น", "Other Shop", "1234567890404");
        String id = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"สมหญิง ใจดี","phone":"081-234-5678","email":"somying@example.com"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        var shopUuid = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        insertOrder(shopUuid, UUID.fromString(id), null, "P1", "100.00", "PAID", "MANUAL", Instant.now(), Instant.now());

        var viewerToken = token("ci-viewer@example.com", "0814100008");
        addMember(shop.shopId, users.findByEmailIgnoreCaseOrPhone("ci-viewer@example.com", "0814100008").orElseThrow().getId(), "STORE_VIEWER");

        perform(get(base(shop) + "/customer-insights"), viewerToken)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].piiMasked").value(true))
            .andExpect(jsonPath("$.items[0].displayName").value("ส***"))
            .andExpect(jsonPath("$.items[0].phone").value("******5678"))
            .andExpect(jsonPath("$.items[0].email").value("s***@example.com"));

        mvc.perform(get(base(shop) + "/customer-insights")).andExpect(status().isUnauthorized());
        perform(get(base(shop) + "/customer-insights"), other.token)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));

        String key = JsonPath.read(perform(get(base(shop) + "/customer-insights"), shop.token)
            .andReturn().getResponse().getContentAsString(), "$.items[0].key");
        perform(get(base(other) + "/customer-insights/" + key), other.token)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));

        // strip ORDER_READ from STORE_VIEWER for this check
        jdbc.update("""
            DELETE FROM auth_role_permissions
             WHERE role_id = (SELECT id FROM auth_roles WHERE code = 'STORE_VIEWER')
               AND permission_id = (SELECT id FROM auth_permissions WHERE code = 'ORDER_READ')
            """);
        permissionMatrix.invalidate();
        perform(get(base(shop) + "/customer-insights/" + key + "/orders"), viewerToken)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        // restore mapping for other tests in same JVM
        jdbc.update("""
            INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version, created_date_time, modified_date_time)
            SELECT gen_random_uuid(), r.id, p.id, '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
              FROM auth_roles r, auth_permissions p
             WHERE r.code = 'STORE_VIEWER' AND p.code = 'ORDER_READ'
               AND NOT EXISTS (
                 SELECT 1 FROM auth_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id)
            """);
        permissionMatrix.invalidate();
    }

    @Test
    void filtersChannelStatusSegmentJoinedAndQueryEscape() throws Exception {
        var shop = openShop("ci-filter@example.com", "0814100005", "ร้านฟิลเตอร์", "Filter Shop", "1234567890405");
        var shopUuid = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        String a = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"Percent % Wild","phone":"0815550001","source":"LINE"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String b = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"Old Customer","phone":"0815550002","source":"SHOPEE","status":"ARCHIVED"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        var now = Instant.now();
        var old = now.minus(100, ChronoUnit.DAYS);
        insertOrder(shopUuid, UUID.fromString(a), null, "F1", "50.00", "PAID", "LINE_OA", now, now);
        insertOrder(shopUuid, UUID.fromString(b), null, "F2", "80.00", "PAID", "SHOPEE", old, old);

        perform(get(base(shop) + "/customer-insights").param("q", "%"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].displayName").value("Percent % Wild"));

        perform(get(base(shop) + "/customer-insights").param("channel", "LINE_OA"), shop.token)
            .andExpect(jsonPath("$.total").value(1));
        perform(get(base(shop) + "/customer-insights").param("status", "INACTIVE"), shop.token)
            .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)));
        perform(get(base(shop) + "/customer-insights").param("segment", "GENERAL"), shop.token)
            .andExpect(jsonPath("$.items[*].segment", everyItem(is("GENERAL"))));

        var today = LocalDate.now(BANGKOK);
        perform(get(base(shop) + "/customer-insights")
            .param("joinedFrom", today.toString())
            .param("joinedTo", today.toString()), shop.token)
            .andExpect(status().isOk());
        perform(get(base(shop) + "/customer-insights")
            .param("joinedFrom", today.plusDays(1).toString())
            .param("joinedTo", today.toString()), shop.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"));

        perform(get(base(shop) + "/customer-insights").param("segment", "NOPE"), shop.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SEGMENT_INVALID"));
        perform(get(base(shop) + "/customer-insights").param("status", "BLOCKED"), shop.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("STATUS_INVALID"));
        perform(get(base(shop) + "/customer-insights").param("sort", "foo"), shop.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SORT_INVALID"));
        perform(get(base(shop) + "/customer-insights").param("size", "0"), shop.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SIZE_INVALID"));

        perform(get(base(shop) + "/customer-insights/channels"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasItems("LINE_OA", "SHOPEE", "LINE")));

        assertEquals("VIP", CustomerInsightsService.segmentOf(new BigDecimal("10000"), 1));
        assertEquals("VIP", CustomerInsightsService.segmentOf(BigDecimal.ZERO, 10));
        assertEquals("REPEAT", CustomerInsightsService.segmentOf(BigDecimal.ONE, 2));
        assertEquals("GENERAL", CustomerInsightsService.segmentOf(BigDecimal.ONE, 1));
    }

    @Test
    void detailAndOrdersEndpoints() throws Exception {
        var shop = openShop("ci-detail@example.com", "0814100006", "ร้านดีเทล", "Detail Shop", "1234567890406");
        var shopUuid = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        String id = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"ดีเทล","phone":"0816667777","email":"detail@example.com",
             "addresses":[{"recipientName":"ดีเทล","phone":"0816667777","addressLine1":"1 ถนน","district":"บางรัก","province":"กรุงเทพมหานคร","postalCode":"10500"}]}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        insertOrder(shopUuid, UUID.fromString(id), null, "D1", "300.00", "PAID", "STOREFRONT", Instant.now(), Instant.now());
        insertOrder(shopUuid, UUID.fromString(id), null, "D2", "200.00", "PAID", "LINE_OA", Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));

        String key = JsonPath.read(perform(get(base(shop) + "/customer-insights"), shop.token)
            .andReturn().getResponse().getContentAsString(), "$.items[0].key");

        perform(get(base(shop) + "/customer-insights/" + key), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.insight.displayName").value("ดีเทล"))
            .andExpect(jsonPath("$.addresses.length()").value(1))
            .andExpect(jsonPath("$.channels.length()").value(2));

        perform(get(base(shop) + "/customer-insights/" + key + "/orders"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[0].orderNumber").value("D1"));
    }

    private UUID orderIdByNumber(UUID shopUuid, String number) {
        return jdbc.queryForObject("SELECT id FROM orders WHERE shop_id = ? AND order_number = ?", UUID.class, shopUuid, number);
    }

    private void insertAddress(UUID orderId, String name, String phone) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
            INSERT INTO order_addresses (id, order_id, address_type, recipient_name, phone, address_line1, district, province, postal_code, country_code, created_date_time, modified_date_time)
            VALUES (?, ?, 'SHIPPING', ?, ?, '88 Test Rd', 'บางรัก', 'กรุงเทพมหานคร', '10500', 'TH', ?, ?)
            """, UUID.randomUUID(), orderId, name, phone, now, now);
    }

    private void insertOrder(UUID shopUuid, UUID customerId, UUID buyerId, String number, String total, String status,
                             String channel, Instant confirmedAt, Instant placedAt) {
        var now = Timestamp.from(Instant.now());
        var placed = Timestamp.from(placedAt == null ? Instant.now() : placedAt);
        var confirmed = confirmedAt == null ? null : Timestamp.from(confirmedAt);
        jdbc.update("""
            INSERT INTO orders (id, shop_id, order_number, shop_customer_id, buyer_user_id, channel, status, payment_status,
              fulfillment_status, currency, grand_total, placed_at, merchant_confirmed_at, merchant_confirm_status, created_date_time, modified_date_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'PAID', 'UNFULFILLED', 'THB', ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), shopUuid, number, customerId, buyerId, channel, status, new BigDecimal(total),
            placed, confirmed, confirmed == null ? "AWAITING" : "CONFIRMED", now, now);
    }

    private UUID registerBuyer(String email, String phone) throws Exception {
        token(email, phone);
        return users.findByEmailIgnoreCaseOrPhone(email, phone).orElseThrow().getId();
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String base(OpenShop shop) {
        return "/api/merchant/shops/" + shop.shopId;
    }

    private void addMember(long shopNo, UUID userId, String role) {
        var shopUuid = shops.findByShopId(shopNo).orElseThrow().getUuid();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO shop_members (id, shop_id, user_id, role, status, joined_at, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)",
            UUID.randomUUID(), shopUuid, userId, role, now, now, now);
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
            var approver = users.save(UserAccount.systemAccount("ci-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"ci-approver@example.com","password":"Password1"}
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
