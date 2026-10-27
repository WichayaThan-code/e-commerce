package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.cart.CartItemRepository;
import com.thaishopfun.cart.CartRepository;
import com.thaishopfun.cart.CouponRepository;
import com.thaishopfun.cart.PromotionRepository;
import com.thaishopfun.cart.PromotionUsageRepository;
import com.thaishopfun.cart.ShippingMethodRepository;
import com.thaishopfun.catalog.InventoryLedgerRepository;
import com.thaishopfun.catalog.InventoryLevelRepository;
import com.thaishopfun.catalog.ProductImageRepository;
import com.thaishopfun.catalog.ProductOptionGroupRepository;
import com.thaishopfun.catalog.ProductOptionValueRepository;
import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.catalog.ProductSkuOptionValueRepository;
import com.thaishopfun.catalog.ProductSkuRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import com.thaishopfun.merchant.OwnerAuditLogRepository;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ProductCategory;
import com.thaishopfun.merchant.ProductCategoryRepository;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** AB2: order tags, internal notes, list filters, bulk actions. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderTagsNotesBulkTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final String ADDRESS = """
        {"acceptTerms":true,"shippingAddress":{"recipientName":"สมหญิง ผู้รับ","phone":"0812345678","addressLine1":"1 ถนนสุขุมวิท",
         "subdistrict":"คลองเตย","district":"คลองเตย","province":"กรุงเทพมหานคร","postalCode":"10110"},"buyerNote":"ฝากหน้าบ้าน"}""";
    private static final String COLOR_SIZE = """
        {"nameTh":"เสื้อยืด","nameEn":"T-Shirt","productType":"VARIABLE","categoryId":%d,"brand":"Basic",
         "optionGroups":[
           {"name":"Color","values":[{"value":"Black"},{"value":"White"}]},
           {"name":"Size","values":[{"value":"S"},{"value":"M"}]}],
         "skus":[
           {"skuCode":"%s-BLK-S","optionValues":{"Color":"Black","Size":"S"},"price":200.00,"costPrice":80.00,"initialStock":20},
           {"skuCode":"%s-BLK-M","optionValues":{"Color":"Black","Size":"M"},"price":250.00,"costPrice":82.50,"initialStock":10},
           {"skuCode":"%s-WHT-S","optionValues":{"Color":"White","Size":"S"},"price":300.00,"costPrice":90,"initialStock":10}]}
        """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired CouponRepository coupons;
    @Autowired PromotionRepository promotions;
    @Autowired PromotionUsageRepository usages;
    @Autowired ShippingMethodRepository shippingMethods;
    @Autowired InventoryLedgerRepository ledger;
    @Autowired InventoryLevelRepository levels;
    @Autowired ProductImageRepository productImages;
    @Autowired ProductSkuRepository productSkus;
    @Autowired ProductRepository catalogProducts;
    @Autowired ProductOptionGroupRepository optionGroups;
    @Autowired ProductOptionValueRepository optionValues;
    @Autowired ProductSkuOptionValueRepository skuOptions;
    @Autowired ProductCategoryRepository categories;
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
        wipeOrders();
        wipe();
    }

    @AfterEach
    void cleanAfter() {
        wipeOrders();
        wipe();
    }

    @Test
    void tagCrudDuplicateAndCrossShop404() throws Exception {
        var shopA = openShop("tag-a@example.com", "0816000001", "TagA", "TagA", "1234567890601");
        var shopB = openShop("tag-b@example.com", "0816000002", "TagB", "TagB", "1234567890602");
        var created = call(post(base(shopA) + "/order-tags"), shopA.token, "{\"name\":\" VIP \",\"color\":\"#112233\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("VIP"))
            .andExpect(jsonPath("$.color").value("#112233"))
            .andExpect(jsonPath("$.usageCount").value(0))
            .andReturn().getResponse().getContentAsString();
        String tagId = JsonPath.read(created, "$.id");
        call(post(base(shopA) + "/order-tags"), shopA.token, "{\"name\":\"vip\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAG_NAME_DUPLICATE"));
        call(get(base(shopA) + "/order-tags"), shopA.token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        call(put(base(shopB) + "/order-tags/" + tagId), shopB.token, "{\"name\":\"Other\"}")
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));
        call(delete(base(shopA) + "/order-tags/" + tagId), shopA.token).andExpect(status().isNoContent());
        call(get(base(shopA) + "/order-tags"), shopA.token).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void noteAndTagsRequireAcceptVersionAndWriteTimeline_buyerNeverSeesThem() throws Exception {
        var shop = openShop("note@example.com", "0816000003", "NoteShop", "Note", "1234567890603");
        var sku = sku(publishedVariantProduct(shop, "NT"), "NT-BLK-S");
        var method = shippingMethod(shop, "Kerry", 40);
        var buyer = token("note-buyer@example.com", "0816000103");
        add(buyer, sku, 1);
        call(put("/api/cart/shops/" + shop.shopId + "/shipping-method"), buyer, "{\"shippingMethodId\":\"%s\"}".formatted(method)).andExpect(status().isOk());
        String orderId = JsonPath.read(checkout(buyer, "note-key-0001").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.orders[0].id");
        var mo = base(shop) + "/orders/" + orderId;

        call(patch(mo + "/internal-note"), shop.token, "{\"internalNote\":\"secret\",\"expectedVersion\":0}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        call(post(mo + "/accept"), shop.token, "{}").andExpect(status().isOk());
        int version = JsonPath.read(call(get(mo), shop.token).andReturn().getResponse().getContentAsString(), "$.version");

        call(patch(mo + "/internal-note"), shop.token, "{\"internalNote\":\"secret-note\",\"expectedVersion\":" + (version - 1) + "}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_VERSION_CONFLICT"));
        call(patch(mo + "/internal-note"), shop.token, "{\"internalNote\":\"secret-note\",\"expectedVersion\":" + version + "}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.internalNote").value("secret-note"));
        version = JsonPath.read(call(get(mo), shop.token).andExpect(jsonPath("$.internalNote").value("secret-note")).andReturn().getResponse().getContentAsString(), "$.version");

        String tagId = JsonPath.read(call(post(base(shop) + "/order-tags"), shop.token, "{\"name\":\"Rush\",\"color\":\"#FF0000\"}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        call(put(mo + "/tags"), shop.token, "{\"tagIds\":[\"" + tagId + "\"],\"expectedVersion\":" + version + "}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags[0].name").value("Rush"));

        call(get(mo + "/timeline"), shop.token)
            .andExpect(jsonPath("$[*].event", hasItem("NOTE_UPDATED")))
            .andExpect(jsonPath("$[*].event", hasItem("TAGS_UPDATED")));
        String timeline = call(get(mo + "/timeline"), shop.token).andReturn().getResponse().getContentAsString();
        assertFalse(timeline.contains("secret-note"));
        // same-status rows are events, never fake "PENDING_PAYMENT -> PENDING_PAYMENT" status changes
        List<java.util.Map<String, Object>> rows = JsonPath.read(timeline, "$");
        for (var row : rows) {
            assertFalse(row.get("fromStatus") != null && row.get("fromStatus").equals(row.get("toStatus")), "same-status row: " + row.get("event"));
            if (List.of("ORDER_ACCEPTED", "NOTE_UPDATED", "TAGS_UPDATED").contains(row.get("event"))) {
                assertEquals(null, row.get("fromStatus"), "event row should not carry fromStatus: " + row.get("event"));
            }
        }

        call(get("/api/orders/" + orderId), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.internalNote").doesNotExist())
            .andExpect(jsonPath("$.tags").doesNotExist());
        call(get("/api/orders"), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].tags").doesNotExist())
            .andExpect(jsonPath("$.items[0].internalNote").doesNotExist());
    }

    @Test
    void listFiltersTagPaymentMethodCarrierTracking() throws Exception {
        var shop = openShop("filt@example.com", "0816000004", "FiltShop", "Filt", "1234567890604");
        var product = publishedVariantProduct(shop, "FL");
        var sku = sku(product, "FL-BLK-S");
        var method = shippingMethod(shop, "Kerry", 30);
        var buyer = token("filt-buyer@example.com", "0816000104");
        String a = place(buyer, shop, sku, method, "filt-key-a");
        String b = place(buyer, shop, sku, method, "filt-key-b");
        call(post(base(shop) + "/orders/" + a + "/accept"), shop.token, "{}").andExpect(status().isOk());
        call(post(base(shop) + "/orders/" + b + "/accept"), shop.token, "{}").andExpect(status().isOk());
        call(post(base(shop) + "/orders/" + a + "/mark-paid"), shop.token, "{\"amount\":230}").andExpect(status().isOk());
        call(post(base(shop) + "/orders/" + b + "/mark-paid"), shop.token, "{\"amount\":230}").andExpect(status().isOk());
        call(post(base(shop) + "/orders/" + a + "/process"), shop.token, "{\"status\":\"PACKED\"}").andExpect(status().isOk());
        call(post(base(shop) + "/orders/" + a + "/ship"), shop.token, "{\"carrierCode\":\"KERRY\",\"trackingNumber\":\"TRK-A-1\"}").andExpect(status().isOk());

        String tagId = JsonPath.read(call(post(base(shop) + "/order-tags"), shop.token, "{\"name\":\"Hot\"}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        int verB = JsonPath.read(call(get(base(shop) + "/orders/" + b), shop.token).andReturn().getResponse().getContentAsString(), "$.version");
        call(put(base(shop) + "/orders/" + b + "/tags"), shop.token, "{\"tagIds\":[\"" + tagId + "\"],\"expectedVersion\":" + verB + "}")
            .andExpect(status().isOk());

        call(get(base(shop) + "/orders?paymentMethod=BANK_TRANSFER"), shop.token)
            .andExpect(jsonPath("$.totalItems").value(2));
        call(get(base(shop) + "/orders?hasTracking=true"), shop.token)
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].id").value(a))
            .andExpect(jsonPath("$.items[0].trackingNumber").value("TRK-A-1"))
            .andExpect(jsonPath("$.items[0].carrierCode").value("KERRY"));
        call(get(base(shop) + "/orders?carrierCode=KERRY"), shop.token)
            .andExpect(jsonPath("$.totalItems").value(1));
        call(get(base(shop) + "/orders?tag=" + tagId), shop.token)
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].id").value(b))
            .andExpect(jsonPath("$.items[0].tags[0].name").value("Hot"));
        call(get(base(shop) + "/orders?paymentMethod=CRYPTO"), shop.token)
            .andExpect(status().isBadRequest());
    }

    @Test
    void bulkAcceptPartialFailureIdempotencyAndTooMany() throws Exception {
        var shop = openShop("bulk@example.com", "0816000005", "BulkShop", "Bulk", "1234567890605");
        var sku = sku(publishedVariantProduct(shop, "BK"), "BK-BLK-S");
        var method = shippingMethod(shop, "Kerry", 20);
        var buyer = token("bulk-buyer@example.com", "0816000105");
        String o1 = place(buyer, shop, sku, method, "bulk-key-1");
        String o2 = place(buyer, shop, sku, method, "bulk-key-2");
        String o3 = place(buyer, shop, sku, method, "bulk-key-3");
        int v1 = version(shop, o1);
        int v2 = version(shop, o2);
        int v3 = version(shop, o3);

        var body = """
            {"items":[
              {"orderId":"%s","expectedVersion":%d},
              {"orderId":"%s","expectedVersion":%d},
              {"orderId":"%s","expectedVersion":%d}
            ]}""".formatted(o1, v1, o2, v2 + 99, o3, v3);
        var first = call(post(base(shop) + "/orders/bulk/accept").header("Idempotency-Key", "bulk-accept-1"), shop.token, body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.succeeded").value(2))
            .andExpect(jsonPath("$.failed").value(1))
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.results[?(@.orderId=='%s')].ok".formatted(o2), hasItem(false)))
            .andExpect(jsonPath("$.results[?(@.orderId=='%s')].code".formatted(o2), hasItem("ORDER_VERSION_CONFLICT")))
            .andReturn().getResponse().getContentAsString();
        assertEquals(2, (int) JsonPath.read(first, "$.succeeded"));

        call(post(base(shop) + "/orders/bulk/accept").header("Idempotency-Key", "bulk-accept-1"), shop.token, body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.succeeded").value(2));

        call(post(base(shop) + "/orders/bulk/accept").header("Idempotency-Key", "bulk-accept-1"), shop.token,
            body.replace("\"expectedVersion\":" + v3, "\"expectedVersion\":999"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        var tooMany = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) tooMany.append(',');
            tooMany.append("{\"orderId\":\"").append(UUID.randomUUID()).append("\",\"expectedVersion\":0}");
        }
        tooMany.append("]}");
        call(post(base(shop) + "/orders/bulk/accept").header("Idempotency-Key", "bulk-accept-51"), shop.token, tooMany.toString())
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BULK_TOO_MANY"));
    }

    @Test
    void bulkCancelForbiddenWithoutPermission() throws Exception {
        var shop = openShop("bulkperm@example.com", "0816000006", "BulkPerm", "BulkP", "1234567890606");
        var sku = sku(publishedVariantProduct(shop, "BP"), "BP-BLK-S");
        var method = shippingMethod(shop, "Kerry", 20);
        var buyer = token("bulkperm-buyer@example.com", "0816000106");
        String id = place(buyer, shop, sku, method, "bulk-perm-1");
        var viewer = token("bulk-viewer@example.com", "0816000107");
        addMember(shop.shopId, userId(viewer), "STORE_VIEWER");
        call(post(base(shop) + "/orders/bulk/cancel").header("Idempotency-Key", "bulk-cancel-deny"), viewer,
            "{\"items\":[{\"orderId\":\"%s\",\"expectedVersion\":0}],\"reason\":\"nope\"}".formatted(id))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    private String place(String buyer, OpenShop shop, String sku, String method, String key) throws Exception {
        add(buyer, sku, 1);
        call(put("/api/cart/shops/" + shop.shopId + "/shipping-method"), buyer, "{\"shippingMethodId\":\"%s\"}".formatted(method)).andExpect(status().isOk());
        return JsonPath.read(checkout(buyer, key).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.orders[0].id");
    }

    private int version(OpenShop shop, String orderId) throws Exception {
        return JsonPath.read(call(get(base(shop) + "/orders/" + orderId), shop.token).andReturn().getResponse().getContentAsString(), "$.version");
    }

    private void wipeOrders() {
        for (var t : List.of("merchant_order_confirmations", "order_item_reveal_logs", "order_financial_snapshots", "refund_status_history", "payment_status_history",
            "payment_webhook_events", "payment_proofs", "refunds", "payments", "media_files", "shop_payment_gateways",
            "order_status_history", "inventory_reservations",
            "order_tag_assignments", "order_tags", "order_bulk_requests",
            "promotion_usages", "order_items", "order_addresses")) jdbc.update("DELETE FROM " + t);
        ledger.deleteAll();
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM shop_customers");
    }

    private void wipe() {
        cartItems.deleteAll();
        carts.deleteAll();
        jdbc.update("DELETE FROM coupon_collections");
        usages.deleteAll();
        coupons.deleteAll();
        promotions.deleteAll();
        shippingMethods.deleteAll();
        skuOptions.deleteAll();
        optionValues.deleteAll();
        optionGroups.deleteAll();
        ledger.deleteAll();
        levels.deleteAll();
        productImages.deleteAll();
        productSkus.deleteAll();
        catalogProducts.deleteAll();
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

    private ResultActions checkout(String buyer, String key) throws Exception {
        var req = post("/api/checkout");
        if (key != null) req.header("Idempotency-Key", key);
        return call(req, buyer, ADDRESS);
    }

    private void add(String buyer, String sku, int qty) throws Exception {
        call(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":%d}".formatted(sku, qty)).andExpect(status().isOk());
    }

    private String shippingMethod(OpenShop shop, String name, int fee) throws Exception {
        var out = call(post(base(shop) + "/shipping-methods"), shop.token, "{\"name\":\"%s\",\"feeType\":\"FLAT\",\"feeAmount\":%d}".formatted(name, fee))
            .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(out, "$.id");
    }

    private String publishedVariantProduct(OpenShop shop, String prefix) throws Exception {
        var created = call(post(base(shop) + "/products"), shop.token, COLOR_SIZE.formatted(activeCategory(0), prefix, prefix, prefix))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String productId = JsonPath.read(created, "$.id");
        var file = new MockMultipartFile("file", "photo.png", "image/png", PNG);
        mvc.perform(multipart(base(shop) + "/products/" + productId + "/images").file(file)
            .header("Authorization", "Bearer " + shop.token)).andExpect(status().isOk());
        return call(post(base(shop) + "/products/" + productId + "/publish"), shop.token)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String sku(String productJson, String code) {
        return JsonPath.<List<String>>read(productJson, "$.skus[?(@.skuCode == '" + code + "')].id").get(0);
    }

    private static String base(OpenShop shop) {
        return "/api/merchant/shops/" + shop.shopId;
    }

    private UUID userId(String token) throws Exception {
        var me = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(me, "$.id"));
    }

    private long activeCategory(int index) {
        return categories.findAll().stream().filter(ProductCategory::isActive)
            .sorted(Comparator.comparing(ProductCategory::getId)).toList().get(index).getId();
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
            var approver = users.save(UserAccount.systemAccount("tags-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"tags-approver@example.com","password":"Password1"}
                """)).andExpect(status().isOk()).andReturn();
            adminToken = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
        }
        return adminToken;
    }

    private String token(String email, String phone) throws Exception {
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"สมชาย","lastName":"ผู้ซื้อ","email":"%s","phone":"%s","password":"Password1"}
            """.formatted(email, phone))).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
    }

    private void uploadDocument(String token, String type) throws Exception {
        var file = new MockMultipartFile("file", type.toLowerCase() + ".png", "image/png", PNG);
        mvc.perform(multipart("/api/merchant-applications/draft/documents/" + type).file(file).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private record OpenShop(String token, long shopId) {}
}
