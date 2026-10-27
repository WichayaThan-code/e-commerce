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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** AC1: buyer order list search by order number / shop name / slug. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BuyerOrderSearchTests {
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
    void buyerFindsOrderByShopSlugNameCaseInsensitiveAndNameEn() throws Exception {
        var shop = openShop("search-shop@example.com", "0817000001", "tinzShop", "Tinz English", "1234567890701");
        var shopUuid = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        jdbc.update("UPDATE shops SET name = ?, name_en = ?, slug = ? WHERE id = ?",
            "tinzShop", "Tinz English", "tinzshop-ac1", shopUuid);
        jdbc.update("UPDATE shops SET name = name || ' ร้านทินซ์' WHERE id = ?", shopUuid);

        var sku = sku(publishedVariantProduct(shop, "SR"), "SR-BLK-S");
        var method = shippingMethod(shop, "Kerry", 20);
        var buyer = token("search-buyer@example.com", "0817000101");
        var other = token("search-other@example.com", "0817000102");
        String orderId = place(buyer, shop, sku, method, "search-key-shop");

        call(get("/api/orders?q=tinzshop"), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].id").value(orderId));
        call(get("/api/orders?q=ทินซ์"), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(orderId));
        call(get("/api/orders?q=tinz english"), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(orderId));
        call(get("/api/orders?q=tinzshop-ac1"), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(orderId));
        call(get("/api/orders?q=tinzshop"), other)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void buyerFindsOrderByOrderNumberCaseInsensitiveAndHashPrefix() throws Exception {
        var shop = openShop("search-num@example.com", "0817000003", "NumShop", "Num", "1234567890703");
        var sku = sku(publishedVariantProduct(shop, "SN"), "SN-BLK-S");
        var method = shippingMethod(shop, "Kerry", 20);
        var buyer = token("search-num-buyer@example.com", "0817000103");
        String orderId = place(buyer, shop, sku, method, "search-key-num");
        String number = jdbc.queryForObject("SELECT order_number FROM orders WHERE id = ?", String.class, UUID.fromString(orderId));
        String fragment = number.substring(4, Math.min(number.length(), 12)).toLowerCase();

        call(get("/api/orders").param("q", fragment), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].id", hasItem(orderId)));
        call(get("/api/orders").param("q", fragment.toUpperCase()), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].id", hasItem(orderId)));
        call(get("/api/orders").param("q", "#" + fragment), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].id", hasItem(orderId)));
    }

    @Test
    void percentAndUnderscoreInQueryAreEscaped() throws Exception {
        var shop = openShop("search-esc@example.com", "0817000004", "EscShop", "Esc", "1234567890704");
        var sku = sku(publishedVariantProduct(shop, "SE"), "SE-BLK-S");
        var method = shippingMethod(shop, "Kerry", 20);
        var buyer = token("search-esc-buyer@example.com", "0817000104");
        place(buyer, shop, sku, method, "search-key-esc");

        call(get("/api/orders?q=%25"), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));
        call(get("/api/orders?q=_"), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void merchantSearchStillMatchesOrderNumberOnAcceptedOrdersOnly() throws Exception {
        var shop = openShop("search-merch@example.com", "0817000005", "MerchSearch", "Merch", "1234567890705");
        var sku = sku(publishedVariantProduct(shop, "SM"), "SM-BLK-S");
        var method = shippingMethod(shop, "Kerry", 20);
        var buyer = token("search-merch-buyer@example.com", "0817000105");
        String orderId = place(buyer, shop, sku, method, "search-key-merch");
        String number = jdbc.queryForObject("SELECT order_number FROM orders WHERE id = ?", String.class, UUID.fromString(orderId));
        String fragment = number.substring(4, Math.min(number.length(), 12));

        call(get(base(shop) + "/orders?q=" + fragment), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].id", hasItem(orderId)));

        call(post(base(shop) + "/orders/" + orderId + "/accept"), shop.token, "{}").andExpect(status().isOk());
        call(get(base(shop) + "/orders?q=สมหญิง"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].id", hasItem(orderId)));
        // shop-name match is buyer/admin only — merchant q on shop name should not suddenly change isolation
        call(get(base(shop) + "/orders?q=MerchSearch"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].id", not(hasItem(orderId))));
    }

    private String place(String buyer, OpenShop shop, String sku, String method, String key) throws Exception {
        add(buyer, sku, 1);
        call(put("/api/cart/shops/" + shop.shopId + "/shipping-method"), buyer, "{\"shippingMethodId\":\"%s\"}".formatted(method)).andExpect(status().isOk());
        return JsonPath.read(checkout(buyer, key).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.orders[0].id");
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
            var approver = users.save(UserAccount.systemAccount("search-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"search-approver@example.com","password":"Password1"}
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
