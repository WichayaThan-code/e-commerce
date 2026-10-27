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

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Product reviews: POST /api/me/reviews eligibility, public masked list, aggregates, best-sellers top_rated + OTOP slug. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductReviewIntegrationTests {
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
    void onlyTheBuyerOfADeliveredItemMayReviewOnce() throws Exception {
        var shop = openShop("rv-shop@example.com", "0817200001", "ReviewShop", "Review Shop", "1234567890901");
        var a = publishedVariantProduct(shop, "RA", activeCategory(0));
        var method = shippingMethod(shop, "Kerry", 20);
        var buyer = token("rv-buyer@example.com", "0817200101");
        var other = token("rv-other@example.com", "0817200102");
        var order = place(buyer, shop, sku(a, "RA-BLK-S"), 1, method, "review-key-1");
        var item = itemOf(order);

        mvc.perform(post("/api/me/reviews").contentType(MediaType.APPLICATION_JSON).content(review(item, 5)))
            .andExpect(status().isUnauthorized());
        call(post("/api/me/reviews"), buyer, review(item, 5)).andExpect(status().isConflict())   // not delivered yet
            .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
        mark(order, "DELIVERED", "PAID");
        call(post("/api/me/reviews"), other, review(item, 5)).andExpect(status().isNotFound());   // not their order
        call(post("/api/me/reviews"), buyer, review(item, 6)).andExpect(status().isBadRequest());
        call(post("/api/me/reviews"), buyer, review(item, 4)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.rating").value(4))
            .andExpect(jsonPath("$.reviewerName").value("ส*** ผ."));
        call(post("/api/me/reviews"), buyer, review(item, 5)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));
    }

    @Test
    void publicListIsMaskedAndAggregatesFeedBestSellersTopRatedAndOtop() throws Exception {
        var shop = openShop("rv-agg@example.com", "0817200002", "AggShop", "Agg Shop", "1234567890902");
        var shopSlug = shops.findByShopId(shop.shopId).orElseThrow().getSlug();
        var otop = categories.findAll().stream().filter(c -> "otop-products".equals(c.getSlug())).findFirst().orElseThrow();
        var a = publishedVariantProduct(shop, "GA", activeCategory(0));
        var b = publishedVariantProduct(shop, "GB", otop.getId());
        var method = shippingMethod(shop, "Kerry", 20);
        var buyer = token("rv-agg-buyer@example.com", "0817200103");
        var oa = place(buyer, shop, sku(a, "GA-BLK-S"), 5, method, "review-key-a1");  // a sells more
        var ob1 = place(buyer, shop, sku(b, "GB-BLK-S"), 1, method, "review-key-b1");
        var ob2 = place(buyer, shop, sku(b, "GB-BLK-M"), 1, method, "review-key-b2");
        for (var o : List.of(oa, ob1, ob2)) mark(o, "COMPLETED", "PAID");
        call(post("/api/me/reviews"), buyer, review(itemOf(oa), 3)).andExpect(status().isCreated());
        call(post("/api/me/reviews"), buyer, review(itemOf(ob1), 5)).andExpect(status().isCreated());
        call(post("/api/me/reviews"), buyer, review(itemOf(ob2), 4)).andExpect(status().isCreated());
        String bId = JsonPath.read(b, "$.id");

        mvc.perform(get("/api/products/" + bId + "/reviews"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.reviewCount").value(2))
            .andExpect(jsonPath("$.ratingAvg").value(4.5))
            .andExpect(jsonPath("$.items[0].reviewerName").value("ส*** ผ."))
            .andExpect(jsonPath("$.items[0].comment").value("ดีมาก"))
            .andExpect(jsonPath("$..buyerUserId").isEmpty())
            .andExpect(jsonPath("$..email").isEmpty())
            .andExpect(jsonPath("$..phone").isEmpty());
        mvc.perform(get("/api/products/" + JsonPath.read(b, "$.slug") + "/reviews").param("shopSlug", shopSlug))
            .andExpect(status().isOk()).andExpect(jsonPath("$.reviewCount").value(2));
        mvc.perform(get("/api/products/no-such-product/reviews")).andExpect(status().isNotFound());

        // best_selling: a first (5 sold) with its rating; top_rated: b first (4.5 > 3.0)
        mvc.perform(get("/api/products/best-sellers"))
            .andExpect(jsonPath("$.items[0].id").value(JsonPath.<String>read(a, "$.id")))
            .andExpect(jsonPath("$.items[0].rating").value(3.0))
            .andExpect(jsonPath("$.items[0].reviewCount").value(1))
            .andExpect(jsonPath("$.items[1].reviewCount").value(2));
        mvc.perform(get("/api/products/best-sellers").param("sort", "top_rated"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[0].id").value(bId))
            .andExpect(jsonPath("$.items[0].rank").value(1))
            .andExpect(jsonPath("$.items[0].rating").value(4.5))
            .andExpect(jsonPath("$.items[1].id").value(JsonPath.<String>read(a, "$.id")));
        mvc.perform(get("/api/products/best-sellers").param("sort", "nope")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/best-sellers").param("categorySlug", "otop-products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].id").value(bId));

        // hidden reviews drop out of every aggregate
        jdbc.update("UPDATE product_reviews SET status = 'HIDDEN' WHERE product_id = ?", UUID.fromString(JsonPath.read(a, "$.id")));
        mvc.perform(get("/api/products/best-sellers").param("sort", "top_rated"))
            .andExpect(jsonPath("$.total").value(1));
    }

    private static String review(String orderItemId, int rating) {
        return "{\"orderItemId\":\"%s\",\"rating\":%d,\"comment\":\"ดีมาก\"}".formatted(orderItemId, rating);
    }

    private String itemOf(String orderId) {
        return jdbc.queryForObject("SELECT id FROM order_items WHERE order_id = ?", UUID.class, UUID.fromString(orderId)).toString();
    }

    private void mark(String orderId, String status, String payment) {
        jdbc.update("UPDATE orders SET status = ?, payment_status = ? WHERE id = ?", status, payment, UUID.fromString(orderId));
    }

    private String place(String buyer, OpenShop shop, String sku, int qty, String method, String key) throws Exception {
        add(buyer, sku, qty);
        call(put("/api/cart/shops/" + shop.shopId + "/shipping-method"), buyer, "{\"shippingMethodId\":\"%s\"}".formatted(method)).andExpect(status().isOk());
        return JsonPath.read(checkout(buyer, key).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.orders[0].id");
    }

    private void wipeOrders() {
        for (var t : List.of("merchant_order_confirmations", "order_item_reveal_logs", "order_financial_snapshots", "refund_status_history", "payment_status_history",
            "payment_webhook_events", "payment_proofs", "refunds", "payments", "media_files", "shop_payment_gateways",
            "order_status_history", "inventory_reservations",
            "order_tag_assignments", "order_tags", "order_bulk_requests",
            "promotion_usages", "product_reviews", "order_items", "order_addresses")) jdbc.update("DELETE FROM " + t);
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

    private String publishedVariantProduct(OpenShop shop, String prefix, long categoryNo) throws Exception {
        var created = call(post(base(shop) + "/products"), shop.token, COLOR_SIZE.formatted(categoryNo, prefix, prefix, prefix))
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

    private long activeCategory(int index) {
        return categories.findAll().stream().filter(ProductCategory::isActive)
            .sorted(Comparator.comparing(ProductCategory::getId)).toList().get(index).getId();
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
            var approver = users.save(UserAccount.systemAccount("rv-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"rv-approver@example.com","password":"Password1"}
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
