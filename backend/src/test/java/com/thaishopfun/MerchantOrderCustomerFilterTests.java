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
class MerchantOrderCustomerFilterTests {
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
    void filtersByShopCustomerAndBuyerKey() throws Exception {
        var shop = openShop("mocf-a@example.com", "0814200001", "ร้านฟิลเตอร์ออเดอร์", "Order Filter Shop", "1234567890501");
        var other = openShop("mocf-b@example.com", "0814200002", "ร้านอื่น", "Other Shop", "1234567890502");
        var shopUuid = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        var buyer = registerBuyer("mocf-buyer@example.com", "0814200099");

        String crmId = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"ลูกค้า CRM","phone":"0813334444"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        var now = Instant.now();
        insertOrder(shopUuid, UUID.fromString(crmId), null, "CRM-1", "100.00", "MERCHANT_CONFIRMED", "MANUAL", now, now);
        insertOrder(shopUuid, UUID.fromString(crmId), null, "CRM-U", "50.00", "PAID_AWAITING_MERCHANT_CONFIRM", "MANUAL", null, now);
        insertOrder(shopUuid, null, buyer, "BUY-1", "200.00", "MERCHANT_CONFIRMED", "STOREFRONT", now, now);
        insertOrder(shopUuid, null, buyer, "BUY-U", "10.00", "PENDING_PAYMENT", "STOREFRONT", null, now);

        String crmKey = null;
        String buyerKey = null;
        {
            var body = perform(get(base(shop) + "/customer-insights"), shop.token).andReturn().getResponse().getContentAsString();
            var conf = com.jayway.jsonpath.Configuration.defaultConfiguration()
                .addOptions(com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL);
            var doc = com.jayway.jsonpath.JsonPath.using(conf).parse(body);
            int n = ((Number) doc.read("$.total")).intValue();
            for (int i = 0; i < n; i++) {
                Object id = doc.read("$.items[" + i + "].shopCustomerId");
                if (crmId.equals(String.valueOf(id))) crmKey = doc.read("$.items[" + i + "].key");
                if (id == null) buyerKey = doc.read("$.items[" + i + "].key");
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(crmKey);
        org.junit.jupiter.api.Assertions.assertNotNull(buyerKey);

        perform(get(base(shop) + "/orders").param("customer", crmKey), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].orderNumber").value("CRM-1"));

        perform(get(base(shop) + "/orders").param("customer", buyerKey), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].orderNumber").value("BUY-1"));

        perform(get(base(shop) + "/orders").param("customer", crmKey).param("status", "MERCHANT_CONFIRMED").param("q", "CRM"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].orderNumber").value("CRM-1"));

        perform(get(base(other) + "/orders").param("customer", crmKey), other.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CUSTOMER_KEY_INVALID"));

        perform(get(base(shop) + "/orders").param("customer", "not-a-key"), shop.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CUSTOMER_KEY_INVALID"));

        perform(get(base(shop) + "/customer-insights/" + crmKey + "/orders"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1));

        var viewer = token("mocf-viewer@example.com", "0814200008");
        addMember(shop.shopId, users.findByEmailIgnoreCaseOrPhone("mocf-viewer@example.com", "0814200008").orElseThrow().getId(), "STORE_VIEWER");
        jdbc.update("""
            DELETE FROM auth_role_permissions
             WHERE role_id = (SELECT id FROM auth_roles WHERE code = 'STORE_VIEWER')
               AND permission_id = (SELECT id FROM auth_permissions WHERE code = 'ORDER_READ')
            """);
        permissionMatrix.invalidate();
        perform(get(base(shop) + "/orders").param("customer", crmKey), viewer)
            .andExpect(status().isForbidden());
        jdbc.update("""
            INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version, created_date_time, modified_date_time)
            SELECT gen_random_uuid(), r.id, p.id, '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
              FROM auth_roles r, auth_permissions p
             WHERE r.code = 'STORE_VIEWER' AND p.code = 'ORDER_READ'
               AND NOT EXISTS (
                 SELECT 1 FROM auth_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id)
            """);
        permissionMatrix.invalidate();

        var buyerToken = token("mocf-buyer2@example.com", "0814200098");
        mvc.perform(get("/api/orders").param("customer", crmKey).header("Authorization", "Bearer " + buyerToken))
            .andExpect(status().isOk());
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
            var approver = users.save(UserAccount.systemAccount("mocf-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"mocf-approver@example.com","password":"Password1"}
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
