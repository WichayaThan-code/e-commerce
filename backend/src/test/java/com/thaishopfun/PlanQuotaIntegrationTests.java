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
import com.thaishopfun.payment.PaymentFileService;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanQuotaIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final String ADDRESS = """
        {"acceptTerms":true,"shippingAddress":{"recipientName":"สมหญิง ผู้รับ","phone":"0812345678","addressLine1":"1 ถนนสุขุมวิท",
         "subdistrict":"คลองเตย","district":"คลองเตย","province":"กรุงเทพมหานคร","postalCode":"10110"},"buyerNote":""}""";

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
    @Autowired PaymentFileService paymentFiles;

    private String adminToken;

    @BeforeEach
    void clean() {
        adminToken = null;
        wipe();
        syncPlanCatalog();
    }

    @AfterEach
    void after() {
        wipe();
    }

    private void syncPlanCatalog() {
        jdbc.update("""
            UPDATE plans SET limit_products = 5, limit_orders_per_month = 50, limit_staff = 2, limit_storage_bytes = 104857600,
              price_monthly = 0, price_yearly = 0, description = 'ร้านเริ่มต้น', sort_order = 1 WHERE code = 'FREE'
            """);
        jdbc.update("""
            UPDATE plans SET limit_products = NULL, limit_orders_per_month = NULL, limit_staff = NULL,
              limit_storage_bytes = 10737418240 WHERE code = 'PRO'
            """);
    }

    @Test
    void newShopAssignedFreePlan() throws Exception {
        var shop = openShop("quota-new@example.com", "0815910001", "ร้านใหม่", "NewShop", "1234567891001");
        assertEquals("FREE", jdbc.queryForObject("SELECT plan_code FROM shops WHERE shop_no = ?", String.class, shop.shopId));
    }

    @Test
    void draftsUnlimitedOnFree_sixthPublishFails() throws Exception {
        var shop = openShop("quota-prod@example.com", "0815910002", "ร้านโควตาสินค้า", "ProdQuota", "1234567891002");
        var ids = new java.util.ArrayList<String>();
        for (int i = 1; i <= 6; i++) {
            var body = perform(post("/api/merchant/shops/" + shop.shopId + "/products"), shop.token,
                simpleProduct("สินค้า" + i, "Item" + i, "SKU-" + i, "40.00"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            ids.add(JsonPath.read(body, "$.id"));
        }
        for (int i = 0; i < 5; i++) {
            publishReady(shop, ids.get(i));
            perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + ids.get(i) + "/publish"), shop.token)
                .andExpect(status().isOk());
        }
        publishReady(shop, ids.get(5));
        perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + ids.get(5) + "/publish"), shop.token)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PRODUCT_QUOTA_EXCEEDED"));
        perform(get("/api/merchant/shops/" + shop.shopId + "/plan-usage"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metrics[?(@.code=='PRODUCTS')].used").value(hasItem(5)));
    }

    @Test
    void unpublishThenPublishNewAllowedOnFree() throws Exception {
        var shop = openShop("quota-swap@example.com", "0815910012", "ร้านสลับเผยแพร่", "SwapQ", "1234567891012");
        var first = createAndPublish(shop, "หนึ่ง", "One", "SW-1");
        createAndPublish(shop, "สอง", "Two", "SW-2");
        createAndPublish(shop, "สาม", "Three", "SW-3");
        createAndPublish(shop, "สี่", "Four", "SW-4");
        createAndPublish(shop, "ห้า", "Five", "SW-5");
        var sixth = createDraft(shop, "หก", "Six", "SW-6");
        publishReady(shop, sixth);
        perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + sixth + "/publish"), shop.token)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PRODUCT_QUOTA_EXCEEDED"));
        perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + first + "/archive"), shop.token)
            .andExpect(status().isOk());
        perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + sixth + "/publish"), shop.token)
            .andExpect(status().isOk());
    }

    @Test
    void overQuotaShopCanStillDraftArchiveAndEdit() throws Exception {
        var shop = openShop("quota-over@example.com", "0815910013", "ร้านเกินโควตา", "OverQ", "1234567891013");
        var ids = new java.util.ArrayList<String>();
        for (int i = 1; i <= 6; i++) {
            var id = createDraft(shop, "เกิน" + i, "Over" + i, "OV-" + i);
            publishReady(shop, id);
            ids.add(id);
        }
        // Force over-quota published state (legacy shops) without going through publish API.
        jdbc.update("""
            UPDATE products SET status = 'PUBLISHED', published_at = NOW(), archived_at = NULL
             WHERE shop_id = (SELECT id FROM shops WHERE shop_no = ?) AND deleted_at IS NULL
            """, shop.shopId);
        perform(post("/api/merchant/shops/" + shop.shopId + "/products"), shop.token, simpleProduct("ร่างใหม่", "NewDraft", "OV-NEW", "40.00"))
            .andExpect(status().isOk());
        perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + ids.get(0) + "/archive"), shop.token)
            .andExpect(status().isOk());
        perform(put("/api/merchant/shops/" + shop.shopId + "/products/" + ids.get(1)), shop.token,
            simpleProduct("แก้ได้", "Editable", "OV-2", "45.00"))
            .andExpect(status().isOk());
        var blocked = createDraft(shop, "ห้ามเผย", "NoPub", "OV-BLOCK");
        publishReady(shop, blocked);
        perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + blocked + "/publish"), shop.token)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PRODUCT_QUOTA_EXCEEDED"));
    }

    @Test
    void importCreatesDraftsEvenWhenPublishedQuotaFull() throws Exception {
        var shop = openShop("quota-import@example.com", "0815910003", "ร้านนำเข้า", "ImportQ", "1234567891003");
        for (int i = 1; i <= 5; i++) {
            createAndPublish(shop, "สินค้า" + i, "Item" + i, "IMP-" + i);
        }
        var file = new MockMultipartFile("file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", twoRowImport());
        mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/products/import").file(file).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isAccepted());
    }

    @Test
    void proAllowsUnlimitedPublish() throws Exception {
        var shop = openShop("quota-pro@example.com", "0815910004", "ร้านโปร", "ProQuota", "1234567891004");
        jdbc.update("UPDATE shops SET plan_code = 'PRO' WHERE shop_no = ?", shop.shopId);
        for (int i = 1; i <= 6; i++) {
            createAndPublish(shop, "โปร" + i, "Pro" + i, "PRO-" + i);
        }
    }

    @Test
    void staffInviteOverFreeQuotaFails() throws Exception {
        var shop = openShop("quota-staff@example.com", "0815910005", "ร้านทีม", "StaffQ", "1234567891005");
        perform(post("/api/merchant/shops/" + shop.shopId + "/team/invitations"), shop.token, """
            {"email":"invitee-a@example.com","role":"STORE_VIEWER"}""")
            .andExpect(status().isOk());
        perform(post("/api/merchant/shops/" + shop.shopId + "/team/invitations"), shop.token, """
            {"email":"invitee-b@example.com","role":"STORE_VIEWER"}""")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MEMBER_QUOTA_EXCEEDED"));
    }

    @Test
    void storageOverQuotaFailsOnUpload() throws Exception {
        var shop = openShop("quota-storage@example.com", "0815910006", "ร้านพื้นที่", "StoreQ", "1234567891006");
        UUID shopUuid = jdbc.queryForObject("SELECT id FROM shops WHERE shop_no = ?", UUID.class, shop.shopId);
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
            INSERT INTO media_files (id, shop_id, uploader_id, purpose, storage_provider, storage_key, original_filename,
              content_type, file_size, sha256, visibility, status, created_date_time, modified_date_time)
            VALUES (?, ?, ?, 'PAYMENT_SLIP', 'LOCAL', '/tmp/full.bin', 'full.bin', 'application/octet-stream', ?, 'deadbeef',
              'PRIVATE', 'READY', ?, ?)
            """, UUID.randomUUID(), shopUuid, shop.userId, 104857600L, now, now);
        var file = new MockMultipartFile("file", "slip.png", "image/png", PNG);
        assertThrows(com.thaishopfun.auth.AuthException.class,
            () -> paymentFiles.store(UUID.randomUUID(), shopUuid, shop.userId, file, PaymentFileService.PAYMENT_SLIP));
    }

    @Test
    void checkoutBlockedAfterOrderRollingQuota() throws Exception {
        var shop = openShop("quota-order@example.com", "0815910007", "ร้านออเดอร์", "OrderQ", "1234567891007");
        UUID shopUuid = jdbc.queryForObject("SELECT id FROM shops WHERE shop_no = ?", UUID.class, shop.shopId);
        var buyer = registerVerified("buyer-order-q@example.com", "0815910008");
        var skuId = UUID.fromString(publishSimpleProduct(shop));
        for (int i = 0; i < 50; i++) {
            insertOrder(shopUuid, buyer.userId, "Q-" + i);
        }
        String shipId = JsonPath.read(
            perform(post("/api/merchant/shops/" + shop.shopId + "/shipping-methods"), shop.token,
                "{\"name\":\"ส่งปกติ\",\"feeType\":\"FLAT\",\"feeAmount\":40}").andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString(),
            "$.id");
        UUID productId = jdbc.queryForObject("SELECT product_id FROM product_skus WHERE id = ?", UUID.class, skuId);
        var cartId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
            INSERT INTO carts (id, buyer_user_id, shop_id, status, currency, shipping_method_id, created_date_time, modified_date_time)
            VALUES (?, ?, ?, 'ACTIVE', 'THB', ?, ?, ?)
            """, cartId, buyer.userId, shopUuid, UUID.fromString(shipId), now, now);
        jdbc.update("""
            INSERT INTO cart_items (id, cart_id, sku_id, product_id, quantity, unit_price_snapshot, is_selected, created_date_time, modified_date_time)
            VALUES (?, ?, ?, ?, 1, 50.00, TRUE, ?, ?)
            """, UUID.randomUUID(), cartId, skuId, productId, now, now);
        perform(post("/api/checkout").header("Idempotency-Key", "quota-order-key-1"), buyer.token, ADDRESS)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_QUOTA_EXCEEDED"));
    }

    @Test
    void adminPlanChangeUpdatesMerchantLimits() throws Exception {
        var shop = openShop("quota-admin@example.com", "0815910009", "ร้านแอดมิน", "AdminQ", "1234567891009");
        ensureAdmin();
        perform(put("/api/admin/shops/" + shop.shopId + "/plan"), adminToken, "{\"planCode\":\"PRO\"}").andExpect(status().isOk());
        perform(get("/api/merchant/shops/" + shop.shopId + "/plan-usage"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.code").value("PRO"))
            .andExpect(jsonPath("$.metrics[?(@.code=='PRODUCTS')].limit.kind").value(hasItem("UNLIMITED")))
            .andExpect(jsonPath("$.metrics[?(@.code=='STAFF')].limit.kind").value(hasItem("UNLIMITED")));
    }

    private void insertOrder(UUID shopUuid, UUID buyerId, String number) {
        var id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
            INSERT INTO orders (id, shop_id, order_number, buyer_user_id, channel, status, payment_status,
              fulfillment_status, currency, grand_total, placed_at, created_date_time, modified_date_time)
            VALUES (?, ?, ?, ?, 'STOREFRONT', 'PENDING_PAYMENT', 'UNPAID', 'UNFULFILLED', 'THB', ?, ?, ?, ?)
            """, id, shopUuid, number, buyerId, new BigDecimal("100.00"), now, now, now);
    }

    private String publishSimpleProduct(OpenShop shop) throws Exception {
        var productId = createAndPublish(shop, "ชา", "Tea", "TEA-Q");
        var published = perform(get("/api/merchant/shops/" + shop.shopId + "/products/" + productId), shop.token)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.<java.util.List<String>>read(published, "$.skus[?(@.skuCode == 'TEA-Q')].id").get(0);
    }

    private String createDraft(OpenShop shop, String nameTh, String nameEn, String sku) throws Exception {
        var created = perform(post("/api/merchant/shops/" + shop.shopId + "/products"), shop.token,
            simpleProduct(nameTh, nameEn, sku, "50.00")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private void publishReady(OpenShop shop, String productId) throws Exception {
        var file = new MockMultipartFile("file", "photo.png", "image/png", PNG);
        mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/images").file(file)
            .header("Authorization", "Bearer " + shop.token)).andExpect(status().isOk());
    }

    private String createAndPublish(OpenShop shop, String nameTh, String nameEn, String sku) throws Exception {
        var productId = createDraft(shop, nameTh, nameEn, sku);
        publishReady(shop, productId);
        perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/publish"), shop.token)
            .andExpect(status().isOk());
        return productId;
    }

    private byte[] twoRowImport() {
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = workbook.createSheet("products");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("productKey");
            header.createCell(1).setCellValue("nameTh");
            header.createCell(2).setCellValue("skuCode");
            header.createCell(3).setCellValue("price");
            var row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("NEW-1");
            row1.createCell(1).setCellValue("นำเข้า1");
            row1.createCell(2).setCellValue("IMP-A");
            row1.createCell(3).setCellValue(40);
            var row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("NEW-2");
            row2.createCell(1).setCellValue("นำเข้า2");
            row2.createCell(2).setCellValue("IMP-B");
            row2.createCell(3).setCellValue(40);
            var out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void wipe() {
        try { jdbc.update("DELETE FROM shop_invitations"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM cart_items"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM carts"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM orders"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM media_files"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM inventory_ledger"); } catch (Exception ignored) { }
        try { jdbc.update("DELETE FROM inventory_levels"); } catch (Exception ignored) { }
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
        String email = "quota-admin-" + UUID.randomUUID() + "@example.com";
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

    private static String simpleProduct(String nameTh, String nameEn, String sku, String price) {
        return """
            {"nameTh":"%s","nameEn":"%s","productType":"SIMPLE","basePrice":%s,"skus":[{"skuCode":"%s","price":%s}]}
            """.formatted(nameTh, nameEn, price, sku, price);
    }

    private record OpenShop(String token, long shopId, UUID userId) {}
    private record SessionUser(String token, UUID userId) {}
}
