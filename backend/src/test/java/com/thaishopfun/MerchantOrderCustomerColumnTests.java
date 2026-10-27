package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PermissionMatrix;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantOrderCustomerColumnTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

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
    void customerColumnOnAcceptedOrders() throws Exception {
        var shop = openShop("mocc-a@example.com", "0814300001", "ร้านคอลัมน์ลูกค้า", "Customer Column Shop", "1234567890601");
        var shopUuid = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        var buyer = registerBuyer("mocc-buyer@example.com", "0814300099");

        String crmId = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"ลูกค้า CRM คอลัมน์","phone":"0814445555"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        var now = Instant.now();
        insertOrder(shopUuid, UUID.fromString(crmId), null, "COL-CRM", "100.00", "MERCHANT_CONFIRMED", "MANUAL", now, now);
        insertAddress(orderIdByNumber(shopUuid, "COL-CRM"), "ชื่อจากที่อยู่ CRM", "0814445555");
        insertOrder(shopUuid, null, buyer, "COL-BUY", "200.00", "MERCHANT_CONFIRMED", "STOREFRONT", now, now);
        insertAddress(orderIdByNumber(shopUuid, "COL-BUY"), "สมชาย ผู้รับ", "0814300099");
        insertOrder(shopUuid, UUID.fromString(crmId), null, "COL-WAIT", "50.00", "PAID_AWAITING_MERCHANT_CONFIRM", "MANUAL", null, now);

        String insightsBody = perform(get(base(shop) + "/customer-insights"), shop.token).andReturn().getResponse().getContentAsString();
        var conf = com.jayway.jsonpath.Configuration.defaultConfiguration()
            .addOptions(com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL);
        var doc = com.jayway.jsonpath.JsonPath.using(conf).parse(insightsBody);
        String crmKey = null;
        String buyerKey = null;
        int n = ((Number) doc.read("$.total")).intValue();
        for (int i = 0; i < n; i++) {
            Object id = doc.read("$.items[" + i + "].shopCustomerId");
            if (crmId.equals(String.valueOf(id))) crmKey = doc.read("$.items[" + i + "].key");
            if (id == null) buyerKey = doc.read("$.items[" + i + "].key");
        }
        org.junit.jupiter.api.Assertions.assertNotNull(crmKey);
        org.junit.jupiter.api.Assertions.assertNotNull(buyerKey);

        var listBody = perform(get(base(shop) + "/orders").param("size", "20"), shop.token)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var listDoc = com.jayway.jsonpath.JsonPath.using(conf).parse(listBody);
        int total = ((Number) listDoc.read("$.totalItems")).intValue();
        String crmName = null, crmListKey = null, buyName = null, buyListKey = null;
        Boolean waitHasName = null;
        for (int i = 0; i < total; i++) {
            String num = listDoc.read("$.items[" + i + "].orderNumber");
            if ("COL-CRM".equals(num)) {
                crmName = listDoc.read("$.items[" + i + "].customerName");
                crmListKey = listDoc.read("$.items[" + i + "].customerKey");
            } else if ("COL-BUY".equals(num)) {
                buyName = listDoc.read("$.items[" + i + "].customerName");
                buyListKey = listDoc.read("$.items[" + i + "].customerKey");
            } else if ("COL-WAIT".equals(num)) {
                waitHasName = listDoc.read("$.items[" + i + "].customerName") != null
                    || listDoc.read("$.items[" + i + "].customerKey") != null;
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals("ลูกค้า CRM คอลัมน์", crmName);
        org.junit.jupiter.api.Assertions.assertEquals(crmKey, crmListKey);
        org.junit.jupiter.api.Assertions.assertEquals("สมชาย ผู้รับ", buyName);
        org.junit.jupiter.api.Assertions.assertEquals(buyerKey, buyListKey);
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, waitHasName);

        perform(get(base(shop) + "/orders").param("customer", crmListKey), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].orderNumber").value("COL-CRM"));

        var viewer = token("mocc-viewer@example.com", "0814300008");
        addMember(shop.shopId, users.findByEmailIgnoreCaseOrPhone("mocc-viewer@example.com", "0814300008").orElseThrow().getId(), "STORE_VIEWER");
        perform(get(base(shop) + "/orders").param("size", "20"), viewer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.orderNumber=='COL-CRM')].customerName").value(hasItem(startsWith("ล***"))))
            .andExpect(jsonPath("$.items[?(@.orderNumber=='COL-BUY')].customerName").value(hasItem(startsWith("ส***"))));

        perform(get(base(shop) + "/orders").param("size", "20"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.orderNumber=='COL-CRM')].customerName").value(hasItem("ลูกค้า CRM คอลัมน์")))
            .andExpect(jsonPath("$.items[*].email").doesNotExist())
            .andExpect(jsonPath("$.items[*].phone").doesNotExist())
            .andExpect(jsonPath("$.items[*].buyerUserId").doesNotExist());

        var buyerToken = token("mocc-buyer2@example.com", "0814300098");
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].customerName").doesNotExist())
            .andExpect(jsonPath("$.items[*].customerKey").doesNotExist());
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
            var approver = users.save(UserAccount.systemAccount("mocc-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"mocc-approver@example.com","password":"Password1"}
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
