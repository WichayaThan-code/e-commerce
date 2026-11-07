package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PermissionMatrix;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.customer.CustomerAuditLogRepository;
import com.thaishopfun.customer.CustomerInsightKeys;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import com.thaishopfun.merchant.OwnerAuditLogRepository;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ShopMemberRepository;
import com.thaishopfun.merchant.ShopReinstatementRequestRepository;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.notification.MerchantLifecycleNotificationWriter;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantNotificationTests {
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
    @Autowired CustomerInsightKeys customerKeys;
    @Autowired MerchantLifecycleNotificationWriter lifecycleNotifications;

    private String adminToken;

    @BeforeEach
    void clean() {
        adminToken = null;
        jdbc.update("DELETE FROM merchant_notification_reads");
        jdbc.update("DELETE FROM merchant_notification_cursor");
        jdbc.update("DELETE FROM merchant_notifications");
        jdbc.update("DELETE FROM order_status_history");
        jdbc.update("DELETE FROM refund_status_history");
        jdbc.update("DELETE FROM refunds");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM order_addresses");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM inventory_ledger");
        jdbc.update("DELETE FROM inventory_levels");
        jdbc.update("DELETE FROM product_skus");
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

    @Test
    void shopScopePermissionMaskReadAndNoBuyerUuid() throws Exception {
        var shopA = openShop("mn-a@example.com", "0814300001", "ร้านแจ้งเตือนเอ", "Notify A", "1234567890601");
        var shopB = openShop("mn-b@example.com", "0814300002", "ร้านแจ้งเตือนบี", "Notify B", "1234567890602");
        var shopAUuid = shops.findByShopId(shopA.shopId).orElseThrow().getUuid();
        var shopBUuid = shops.findByShopId(shopB.shopId).orElseThrow().getUuid();
        var buyer = registerBuyer("mn-buyer@example.com", "0814300099");
        var now = Instant.now();

        insertOrder(shopAUuid, null, buyer, "NEW-1", "150.00", "PAID_AWAITING_MERCHANT_CONFIRM", null, now);
        insertOrder(shopAUuid, null, buyer, "PAID-1", "200.00", "MERCHANT_CONFIRMED", now.minus(1, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS));
        var paidOrderId = orderId(shopAUuid, "PAID-1");
        insertPayment(shopAUuid, paidOrderId, "200.00", now.minus(30, ChronoUnit.MINUTES));
        var cancelOrderId = insertOrder(shopAUuid, null, buyer, "CXL-1", "80.00", "CANCELLED", now.minus(3, ChronoUnit.HOURS), now.minus(4, ChronoUnit.HOURS));
        insertHistory(cancelOrderId, "MERCHANT_CONFIRMED", "CANCELLED", now.minus(50, ChronoUnit.MINUTES));
        insertHistory(cancelOrderId, "MERCHANT_CONFIRMED", "MERCHANT_CONFIRMED", now.minus(40, ChronoUnit.MINUTES)); // tag/note noise
        var refundOrderId = insertOrder(shopAUuid, null, buyer, "RF-1", "90.00", "REFUND_REQUESTED", now.minus(5, ChronoUnit.HOURS), now.minus(6, ChronoUnit.HOURS));
        insertRefund(shopAUuid, refundOrderId, "90.00", now.minus(20, ChronoUnit.MINUTES));

        String crmId = JsonPath.read(perform(post(base(shopA) + "/customers"), shopA.token, """
            {"displayName":"สมชาย ลูกค้าใหม่","phone":"0813335555"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        insertOrder(shopAUuid, UUID.fromString(crmId), null, "CRM-1", "110.00", "MERCHANT_CONFIRMED", now.minus(10, ChronoUnit.MINUTES), now.minus(15, ChronoUnit.MINUTES));

        insertLowStock(shopAUuid, "SKU-LOW", 2, 5, now.minus(1, ChronoUnit.HOURS));
        insertOrder(shopBUuid, null, buyer, "OTHER-1", "999.00", "PAID_AWAITING_MERCHANT_CONFIRM", null, now);

        var listBody = perform(get(base(shopA) + "/notifications").param("size", "50"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].type", hasItems("ORDER_NEW", "ORDER_PAID", "ORDER_CANCELLED", "REFUND_REQUESTED", "CUSTOMER_NEW", "LOW_STOCK")))
            .andExpect(jsonPath("$.items[?(@.type=='ORDER_NEW')].orderNo", hasItem("NEW-1")))
            .andExpect(jsonPath("$.items[?(@.type=='ORDER_NEW')].customerKey", everyItem(nullValue())))
            .andReturn().getResponse().getContentAsString();

        assertFalse(listBody.contains(buyer.toString()), "buyer uuid must not appear in JSON");
        assertFalse(listBody.contains("\"buyerUserId\""), "must not expose buyerUserId field");

        String customerKey = firstString(listBody, "$.items[?(@.type=='CUSTOMER_NEW' && @.orderNo=='CRM-1')].customerKey");
        assertNotNull(customerKey);
        assertTrue(customerKey.startsWith("c1"));
        assertTrue(customerKeys.resolve(shopAUuid, customerKey).isPresent());
        assertTrue(customerKeys.resolve(shopBUuid, customerKey).isEmpty(), "other shop must not resolve key");

        var viewer = token("mn-viewer@example.com", "0814300008");
        addMember(shopA.shopId, users.findByEmailIgnoreCaseOrPhone("mn-viewer@example.com", "0814300008").orElseThrow().getId(), "STORE_VIEWER");
        var viewerBody = perform(get(base(shopA) + "/notifications").param("size", "50"), viewer)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertEquals("ส***", firstString(viewerBody, "$.items[?(@.type=='CUSTOMER_NEW' && @.orderNo=='CRM-1')].titleParams.customerName"));
        assertFalse(viewerBody.contains(buyer.toString()));

        perform(get(base(shopB) + "/notifications"), shopB.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.orderNo=='NEW-1')]").isEmpty())
            .andExpect(jsonPath("$.items[?(@.orderNo=='OTHER-1')].type", hasItem("ORDER_NEW")));

        perform(get(base(shopA) + "/notifications/unread-count"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count", greaterThan(0)));

        String orderNewKey = firstString(listBody, "$.items[?(@.type=='ORDER_NEW' && @.orderNo=='NEW-1')].key");
        perform(post(base(shopA) + "/notifications/{key}/read", orderNewKey), shopA.token)
            .andExpect(status().isOk());
        perform(get(base(shopA) + "/notifications").param("size", "50"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.key=='" + orderNewKey + "')].read", hasItem(true)));

        perform(post(base(shopA) + "/notifications/read-all"), shopA.token)
            .andExpect(status().isOk());
        perform(get(base(shopA) + "/notifications/unread-count"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0));
        perform(get(base(shopA) + "/notifications").param("size", "50"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.read==false)]").isEmpty());

        jdbc.update("""
            DELETE FROM auth_role_permissions
             WHERE role_id = (SELECT id FROM auth_roles WHERE code = 'STORE_VIEWER')
               AND permission_id IN (SELECT id FROM auth_permissions WHERE code IN ('ORDER_READ', 'CUSTOMER_READ', 'PRODUCT_READ'))
            """);
        permissionMatrix.invalidate();
        // SHOP_READ alone keeps lifecycle inbox; operational order/customer/stock rows stay gated out.
        perform(get(base(shopA) + "/notifications").param("size", "50"), viewer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.type=='ORDER_NEW')]").isEmpty())
            .andExpect(jsonPath("$.items[?(@.type=='SHOP_APPROVED')].type", hasItem("SHOP_APPROVED")));
        restoreViewerPermissions();
    }

    @Test
    void shopRestorePublishesDistinctLifecycleNotification() throws Exception {
        var shop = openShop("mn-restore@example.com", "0814300011", "ร้านคืนสถานะ", "Restore Shop", "1234567890611");
        perform(post("/api/admin/shops/" + shop.shopId + "/suspend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"ทดสอบระงับเพื่อแจ้งเตือน","reasonCode":"POLICY_VIOLATION"}
                    """), adminToken())
            .andExpect(status().isOk());

        perform(get(base(shop) + "/notifications").param("size", "50"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.type=='SHOP_SUSPENDED')].type", hasItem("SHOP_SUSPENDED")));

        perform(post("/api/admin/shops/" + shop.shopId + "/reactivate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"ทดสอบคืนสถานะเพื่อแจ้งเตือน","reasonCode":"ADMIN_RESTORE"}
                    """), adminToken())
            .andExpect(status().isOk());

        var body = perform(get(base(shop) + "/notifications").param("size", "50"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.type=='SHOP_RESTORED')].type", hasItem("SHOP_RESTORED")))
            .andExpect(jsonPath("$.items[?(@.type=='SHOP_APPROVED')].type", hasItem("SHOP_APPROVED")))
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("SHOP_RESTORED"), "restore must be visible separately from first approval");

        // Second restore after another suspend must notify again (new restored_at → new dedupe).
        perform(post("/api/admin/shops/" + shop.shopId + "/suspend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"ระงับรอบสอง","reasonCode":"POLICY_VIOLATION"}
                    """), adminToken())
            .andExpect(status().isOk());
        Thread.sleep(5);
        perform(post("/api/admin/shops/" + shop.shopId + "/reactivate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"คืนสถานะรอบสอง","reasonCode":"ADMIN_RESTORE"}
                    """), adminToken())
            .andExpect(status().isOk());

        perform(get(base(shop) + "/notifications").param("size", "50"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.type=='SHOP_RESTORED')]", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void restorePublishThenReconcileDoesNotDuplicate() throws Exception {
        var shop = openShop("mn-reconcile@example.com", "0814300012", "ร้านไม่ซ้ำ", "No Dup Shop", "1234567890612");
        perform(post("/api/admin/shops/" + shop.shopId + "/suspend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"ทดสอบไม่ให้ซ้ำ","reasonCode":"POLICY_VIOLATION"}
                    """), adminToken())
            .andExpect(status().isOk());
        perform(post("/api/admin/shops/" + shop.shopId + "/reactivate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"คืนสถานะครั้งเดียว","reasonCode":"ADMIN_RESTORE"}
                    """), adminToken())
            .andExpect(status().isOk());

        // Simulate startup reconcile for the same restored_at (must not insert a second row).
        lifecycleNotifications.reconcileLifecycleFromShops();

        perform(get(base(shop) + "/notifications").param("size", "50"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.type=='SHOP_RESTORED')]", hasSize(1)));

        Long restoredRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM merchant_notifications WHERE type = 'SHOP_RESTORED' AND entity_id = ?",
            Long.class, String.valueOf(shop.shopId));
        assertEquals(1L, restoredRows);
    }

    @Test
    void legacyDedupeKeyForSameRestoredAtDoesNotGainSecondRow() throws Exception {
        var shop = openShop("mn-legacy-key@example.com", "0814300013", "ร้านคีย์เก่า", "Legacy Key", "1234567890613");
        perform(post("/api/admin/shops/" + shop.shopId + "/suspend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"ทดสอบคีย์เก่า","reasonCode":"POLICY_VIOLATION"}
                    """), adminToken())
            .andExpect(status().isOk());
        perform(post("/api/admin/shops/" + shop.shopId + "/reactivate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"คืนสถานะคีย์เก่า","reasonCode":"ADMIN_RESTORE"}
                    """), adminToken())
            .andExpect(status().isOk());

        var shopRow = shops.findByShopId(shop.shopId).orElseThrow();
        // Pretend an older backfill row already exists for the same event time under another key.
        jdbc.update("""
            UPDATE merchant_notifications
               SET dedupe_key = ?
             WHERE shop_id = ? AND type = 'SHOP_RESTORED'
            """, "SHOP_RESTORED:backfill:shop:" + shop.shopId, shopRow.getUuid());

        lifecycleNotifications.reconcileLifecycleFromShops();
        lifecycleNotifications.publishShopRestored(shopRow);

        Long restoredRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM merchant_notifications WHERE shop_id = ? AND type = 'SHOP_RESTORED'",
            Long.class, shopRow.getUuid());
        assertEquals(1L, restoredRows);

        perform(get(base(shop) + "/notifications").param("size", "50"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.type=='SHOP_RESTORED')]", hasSize(1)));
    }

    private static String firstString(String json, String path) {
        Object value = JsonPath.read(json, path);
        if (value instanceof net.minidev.json.JSONArray arr) {
            assertFalse(arr.isEmpty(), path);
            return String.valueOf(arr.get(0));
        }
        return value == null ? null : String.valueOf(value);
    }

    private void restoreViewerPermissions() {
        for (String code : new String[]{"ORDER_READ", "CUSTOMER_READ", "PRODUCT_READ"}) {
            jdbc.update("""
                INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version, created_date_time, modified_date_time)
                SELECT gen_random_uuid(), r.id, p.id, '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                  FROM auth_roles r, auth_permissions p
                 WHERE r.code = 'STORE_VIEWER' AND p.code = ?
                   AND NOT EXISTS (
                     SELECT 1 FROM auth_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id)
                """, code);
        }
        permissionMatrix.invalidate();
    }

    private UUID insertOrder(UUID shopUuid, UUID customerId, UUID buyerId, String number, String total, String status,
                             Instant confirmedAt, Instant placedAt) {
        var id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        var placed = Timestamp.from(placedAt == null ? Instant.now() : placedAt);
        var confirmed = confirmedAt == null ? null : Timestamp.from(confirmedAt);
        jdbc.update("""
            INSERT INTO orders (id, shop_id, order_number, shop_customer_id, buyer_user_id, channel, status, payment_status,
              fulfillment_status, currency, grand_total, placed_at, merchant_confirmed_at, merchant_confirm_status, created_date_time, modified_date_time)
            VALUES (?, ?, ?, ?, ?, 'STOREFRONT', ?, 'PAID', 'UNFULFILLED', 'THB', ?, ?, ?, ?, ?, ?)
            """, id, shopUuid, number, customerId, buyerId, status, new BigDecimal(total),
            placed, confirmed, confirmed == null ? "AWAITING" : "CONFIRMED", placed, now);
        return id;
    }

    private UUID orderId(UUID shopUuid, String number) {
        return jdbc.queryForObject("SELECT id FROM orders WHERE shop_id = ? AND order_number = ?", UUID.class, shopUuid, number);
    }

    private void insertPayment(UUID shopUuid, UUID orderId, String amount, Instant at) {
        var ts = Timestamp.from(at);
        jdbc.update("""
            INSERT INTO payments (id, order_id, shop_id, provider, amount, currency, status, idempotency_key,
              attempt_no, captured_at, paid_at, created_date_time, modified_date_time)
            VALUES (?, ?, ?, 'PROMPTPAY', ?, 'THB', 'CAPTURED', ?, 1, ?, ?, ?, ?)
            """, UUID.randomUUID(), orderId, shopUuid, new BigDecimal(amount), "idem-" + UUID.randomUUID(), ts, ts, ts, ts);
    }

    private void insertHistory(UUID orderId, String from, String to, Instant at) {
        var ts = Timestamp.from(at);
        jdbc.update("""
            INSERT INTO order_status_history (order_id, from_status, to_status, changed_by_type, created_date_time, modified_date_time)
            VALUES (?, ?, ?, 'SYSTEM', ?, ?)
            """, orderId, from, to, ts, ts);
    }

    private void insertRefund(UUID shopUuid, UUID orderId, String amount, Instant at) {
        var ts = Timestamp.from(at);
        jdbc.update("""
            INSERT INTO refunds (id, order_id, shop_id, amount, currency, status, idempotency_key, created_date_time, modified_date_time)
            VALUES (?, ?, ?, ?, 'THB', 'REQUESTED', ?, ?, ?)
            """, UUID.randomUUID(), orderId, shopUuid, new BigDecimal(amount), "rf-" + UUID.randomUUID(), ts, ts);
    }

    private void insertLowStock(UUID shopUuid, String skuCode, int available, int threshold, Instant modifiedAt) {
        var productId = UUID.randomUUID();
        var skuId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        var mod = Timestamp.from(modifiedAt);
        jdbc.update("""
            INSERT INTO products (id, shop_id, slug, name_th, status, product_type, currency, option_schema, created_date_time, modified_date_time)
            VALUES (?, ?, ?, ?, 'PUBLISHED', 'SIMPLE', 'THB', '[]'::jsonb, ?, ?)
            """, productId, shopUuid, "p-" + skuCode.toLowerCase(), "สินค้า " + skuCode, now, now);
        jdbc.update("""
            INSERT INTO product_skus (id, shop_id, product_id, sku_code, price, currency, option_values, is_active, created_date_time, modified_date_time)
            VALUES (?, ?, ?, ?, 100, 'THB', '{}'::jsonb, true, ?, ?)
            """, skuId, shopUuid, productId, skuCode, now, now);
        jdbc.update("""
            INSERT INTO inventory_levels (id, shop_id, sku_id, on_hand, reserved_qty, available, safety_stock, low_stock_threshold, version, created_date_time, modified_date_time)
            VALUES (?, ?, ?, ?, 0, ?, 0, ?, 0, ?, ?)
            """, UUID.randomUUID(), shopUuid, skuId, available, available, threshold, now, mod);
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
            var approver = users.save(UserAccount.systemAccount("mn-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"mn-approver@example.com","password":"Password1"}
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
