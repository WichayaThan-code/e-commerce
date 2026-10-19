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
import com.thaishopfun.cart.PromotionUsage;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cart: add / update / remove / clear, variant choice, same-SKU increment, stock caps, coupons (percent, fixed,
 * free shipping, min subtotal, expired, usage limits), shipping fees with free-shipping threshold, per-shop and
 * overall totals, checkout validation (price change, out of stock, unavailable), guest cart + merge on login,
 * isolation and permissions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

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
        wipe();
    }

    @AfterEach
    void cleanAfter() {
        cartItems.deleteAll();
        carts.deleteAll();
        usages.deleteAll();
        coupons.deleteAll();
        promotions.deleteAll();
        shippingMethods.deleteAll();
    }

    private void wipe() {
        cartItems.deleteAll();
        carts.deleteAll();
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

    static final String COLOR_SIZE = """
        {"nameTh":"เสื้อยืด","nameEn":"T-Shirt","productType":"VARIABLE","categoryId":%d,"brand":"Basic",
         "optionGroups":[
           {"name":"Color","values":[{"value":"Black"},{"value":"White"}]},
           {"name":"Size","values":[{"value":"S"},{"value":"M"}]}],
         "skus":[
           {"skuCode":"%s-BLK-S","optionValues":{"Color":"Black","Size":"S"},"price":200.00,"costPrice":80.00,"initialStock":5},
           {"skuCode":"%s-BLK-M","optionValues":{"Color":"Black","Size":"M"},"price":250.00,"costPrice":82.50,"initialStock":3},
           {"skuCode":"%s-WHT-S","optionValues":{"Color":"White","Size":"S"},"price":300.00,"costPrice":90,"initialStock":0}]}
        """;

    // ---------------- basic cart operations ----------------

    @Test
    void addUpdateRemoveClearWithVariantChoiceAndSameSkuIncrement() throws Exception {
        var shop = openShop("c-basic@example.com", "0814000001", "ร้านเสื้อ", "Shirt", "1234567890301");
        var product = publishedVariantProduct(shop, "BS");
        var blkS = sku(product, "BS-BLK-S");
        var blkM = sku(product, "BS-BLK-M");
        var buyer = token("c-basic-buyer@example.com", "0814000101");

        var added = cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":2}".formatted(blkS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(1))
            .andExpect(jsonPath("$.groups[0].shopId").value(shop.shopId))
            .andExpect(jsonPath("$.groups[0].items[0].skuId").value(blkS))
            .andExpect(jsonPath("$.groups[0].items[0].optionLabel").value("Black / S"))
            .andExpect(jsonPath("$.groups[0].items[0].options.Color").value("Black"))
            .andExpect(jsonPath("$.groups[0].items[0].quantity").value(2))
            .andExpect(jsonPath("$.groups[0].items[0].unitPrice").value(200.0))
            .andExpect(jsonPath("$.groups[0].items[0].lineTotal").value(400.0))
            .andExpect(jsonPath("$.groups[0].items[0].availableQuantity").value(5))
            .andExpect(jsonPath("$.groups[0].items[0].status").value("OK"))
            .andExpect(jsonPath("$.cartToken").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(added, "$.groups[0].items[0].id");

        // same SKU again: the line increments (UNIQUE cart_id, sku_id), no second line
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":1}".formatted(blkS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].items.length()").value(1))
            .andExpect(jsonPath("$.groups[0].items[0].id").value(itemId))
            .andExpect(jsonPath("$.groups[0].items[0].quantity").value(3));
        // another variant = another line
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\"}".formatted(blkM))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].items.length()").value(2))
            .andExpect(jsonPath("$.counts.lineCount").value(2))
            .andExpect(jsonPath("$.counts.itemQuantity").value(4));
        cart(get("/api/cart/count"), buyer).andExpect(status().isOk())
            .andExpect(jsonPath("$.lineCount").value(2)).andExpect(jsonPath("$.itemQuantity").value(4))
            .andExpect(jsonPath("$.selectedQuantity").value(4));

        // set quantity (absolute)
        cart(patch("/api/cart/items/" + itemId), buyer, "{\"quantity\":4}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].items[0].quantity").value(4));
        // invalid quantity
        cart(patch("/api/cart/items/" + itemId), buyer, "{\"quantity\":0}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
        // switch variant of the line: Black/S x4 -> Black/M (stock 3) rejected, then x2 merges into existing Black/M line
        cart(patch("/api/cart/items/" + itemId), buyer, "{\"skuId\":\"%s\"}".formatted(blkM))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        cart(patch("/api/cart/items/" + itemId), buyer, "{\"quantity\":2}").andExpect(status().isOk());
        cart(patch("/api/cart/items/" + itemId), buyer, "{\"skuId\":\"%s\"}".formatted(blkM))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].items.length()").value(1))
            .andExpect(jsonPath("$.groups[0].items[0].skuId").value(blkM))
            .andExpect(jsonPath("$.groups[0].items[0].quantity").value(3));

        // deselect / select
        var view = cart(get("/api/cart"), buyer).andReturn().getResponse().getContentAsString();
        String mLine = JsonPath.read(view, "$.groups[0].items[0].id");
        cart(put("/api/cart/selection"), buyer, "{\"itemIds\":[\"%s\"],\"selected\":false}".formatted(mLine))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].items[0].selected").value(false))
            .andExpect(jsonPath("$.summary.subtotal").value(0.0))
            .andExpect(jsonPath("$.summary.shippingFee").value(0.0))
            .andExpect(jsonPath("$.counts.selectedLineCount").value(0));
        cart(put("/api/cart/selection"), buyer, "{\"selected\":true}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.summary.subtotal").value(750.0));

        // remove one, then clear
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\"}".formatted(blkS)).andExpect(status().isOk());
        cart(delete("/api/cart/items/" + mLine), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].items.length()").value(1));
        cart(delete("/api/cart"), buyer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(0))
            .andExpect(jsonPath("$.counts.lineCount").value(0));
        cart(delete("/api/cart/items/" + mLine), buyer).andExpect(status().isNotFound());
    }

    @Test
    void overStockIsRejectedWithAvailableQuantityAndCapOptionAddsWhatFits() throws Exception {
        var shop = openShop("c-stock@example.com", "0814000002", "ร้านสต็อก", "Stock", "1234567890302");
        var product = publishedVariantProduct(shop, "ST");
        var buyer = token("c-stock-buyer@example.com", "0814000102");
        var blkM = sku(product, "ST-BLK-M"); // stock 3
        var whtS = sku(product, "ST-WHT-S"); // stock 0

        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":4}".formatted(blkM))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.availableQuantity").value(3))
            .andExpect(jsonPath("$.canAddQuantity").value(3))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":2}".formatted(blkM)).andExpect(status().isOk());
        // 2 in cart + 2 more > 3 available
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":2}".formatted(blkM))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.inCartQuantity").value(2))
            .andExpect(jsonPath("$.canAddQuantity").value(1));
        // capToAvailable: adds the 1 that fits and reports the adjustment
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":2,\"capToAvailable\":true}".formatted(blkM))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].items[0].quantity").value(3))
            .andExpect(jsonPath("$.lastAction.appliedQuantity").value(1))
            .andExpect(jsonPath("$.lastAction.adjusted").value(true));
        // out of stock
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\"}".formatted(whtS))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.availableQuantity").value(0));
        // update above stock
        var line = JsonPath.read(cart(get("/api/cart"), buyer).andReturn().getResponse().getContentAsString(), "$.groups[0].items[0].id");
        cart(patch("/api/cart/items/" + line), buyer, "{\"quantity\":5}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.availableQuantity").value(3));
        // max per line
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":100}".formatted(blkM))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("MAX"));
        // unknown SKU
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\"}".formatted(UUID.randomUUID()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"));
        // draft (unpublished) product cannot be added
        var draft = createProduct(shop, COLOR_SIZE.formatted(activeCategory(0), "DR", "DR", "DR"));
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\"}".formatted(sku(draft, "DR-BLK-S")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
    }

    // ---------------- coupons ----------------

    @Test
    void couponPercentFixedFreeShippingMinSubtotalExpiredAndUsageLimits() throws Exception {
        var shop = openShop("c-coupon@example.com", "0814000003", "ร้านคูปอง", "Coupon", "1234567890303");
        var product = publishedVariantProduct(shop, "CP");
        var buyer = token("c-coupon-buyer@example.com", "0814000103");
        var blkS = sku(product, "CP-BLK-S"); // 200
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":3}".formatted(blkS)).andExpect(status().isOk()); // 600

        // merchant creates coupons
        var percent = createCoupon(shop, """
            {"code":"save10","type":"PERCENT","value":10,"maxDiscount":50}""");
        assertEquals("SAVE10", JsonPath.read(percent, "$.code"));
        assertEquals("ACTIVE", JsonPath.read(percent, "$.state"));
        createCoupon(shop, """
            {"code":"FIX100","type":"FIXED","value":100,"minSubtotal":500}""");
        createCoupon(shop, """
            {"code":"BIGMIN","type":"FIXED","value":100,"minSubtotal":1000}""");
        createCoupon(shop, """
            {"code":"FREESHIP","type":"FREE_SHIPPING"}""");
        var expiredStart = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        createCoupon(shop, """
            {"code":"OLD","type":"FIXED","value":10,"startsAt":"%s","endsAt":"%s"}""".formatted(expiredStart, expiredStart.plus(1, ChronoUnit.DAYS)));
        var limited = createCoupon(shop, """
            {"code":"ONCE","type":"FIXED","value":20,"usageLimitTotal":1}""");
        var perCustomer = createCoupon(shop, """
            {"code":"MINE","type":"FIXED","value":30,"usageLimitTotal":10,"usageLimitPerCustomer":1}""");
        // duplicate code (case-insensitive) and bad values
        merchant(post(base(shop) + "/coupons"), shop.token, "{\"code\":\"Save10\",\"type\":\"PERCENT\",\"value\":5}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COUPON_CODE_DUPLICATE"));
        merchant(post(base(shop) + "/coupons"), shop.token, "{\"code\":\"X\",\"type\":\"PERCENT\",\"value\":150}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("code")))
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("value")));

        // default shipping (shop has no method) = 40
        // PERCENT 10% of 600 = 60, capped at 50
        applyCoupon(buyer, shop, "save10").andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].coupon.code").value("SAVE10"))
            .andExpect(jsonPath("$.groups[0].coupon.applicable").value(true))
            .andExpect(jsonPath("$.groups[0].totals.subtotal").value(600.0))
            .andExpect(jsonPath("$.groups[0].totals.discount").value(50.0))
            .andExpect(jsonPath("$.groups[0].totals.shippingFee").value(40.0))
            .andExpect(jsonPath("$.groups[0].totals.grandTotal").value(590.0));
        // FIXED 100 with min subtotal 500
        applyCoupon(buyer, shop, "FIX100").andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].totals.discount").value(100.0))
            .andExpect(jsonPath("$.groups[0].totals.grandTotal").value(540.0));
        // FREE_SHIPPING
        applyCoupon(buyer, shop, "FREESHIP").andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].totals.discount").value(0.0))
            .andExpect(jsonPath("$.groups[0].totals.shippingFee").value(0.0))
            .andExpect(jsonPath("$.groups[0].totals.shippingDiscount").value(40.0))
            .andExpect(jsonPath("$.groups[0].coupon.shippingDiscountAmount").value(40.0))
            .andExpect(jsonPath("$.groups[0].totals.grandTotal").value(600.0));
        // min subtotal not met: applied but not applicable, no discount
        applyCoupon(buyer, shop, "BIGMIN").andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].coupon.applicable").value(false))
            .andExpect(jsonPath("$.groups[0].coupon.reason").value("COUPON_MIN_SUBTOTAL_NOT_MET"))
            .andExpect(jsonPath("$.groups[0].totals.discount").value(0.0));
        cart(post("/api/cart/validate"), buyer).andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.issues[*].code", hasItem("COUPON_INVALID")));
        // expired / unknown
        applyCoupon(buyer, shop, "OLD").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COUPON_EXPIRED"));
        applyCoupon(buyer, shop, "NOPE").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));
        // total usage limit reached
        var onceId = UUID.fromString(JsonPath.read(limited, "$.id"));
        var oncePromo = coupons.findById(onceId).orElseThrow().getPromotionId();
        usages.save(new PromotionUsage(oncePromo, onceId, UUID.randomUUID(), null, new BigDecimal("20.00")));
        applyCoupon(buyer, shop, "ONCE").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COUPON_USAGE_LIMIT_REACHED"));
        merchant(get(base(shop) + "/coupons/" + onceId), shop.token).andExpect(status().isOk())
            .andExpect(jsonPath("$.usedCount").value(1)).andExpect(jsonPath("$.state").value("USED_UP"));
        // per-customer limit: this buyer used it once already
        var mineId = UUID.fromString(JsonPath.read(perCustomer, "$.id"));
        var minePromo = coupons.findById(mineId).orElseThrow().getPromotionId();
        usages.save(new PromotionUsage(minePromo, mineId, UUID.randomUUID(), userId(buyer), new BigDecimal("30.00")));
        applyCoupon(buyer, shop, "MINE").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COUPON_PER_CUSTOMER_LIMIT_REACHED"));
        // disabled coupon
        String percentId = JsonPath.read(percent, "$.id");
        merchant(post(base(shop) + "/coupons/" + percentId + "/disable"), shop.token).andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("DISABLED"));
        applyCoupon(buyer, shop, "SAVE10").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COUPON_INACTIVE"));
        // remove coupon
        cart(delete("/api/cart/shops/" + shop.shopId + "/coupon"), buyer).andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].coupon").doesNotExist());
        // list
        merchant(get(base(shop) + "/coupons?state=EXPIRED"), shop.token).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].code").value("OLD"));
    }

    // ---------------- shipping + totals ----------------

    @Test
    void shippingThresholdWeightAndTotalsPerShopAndOverall() throws Exception {
        var shopA = openShop("c-shipa@example.com", "0814000004", "ร้านเอ", "Shop A", "1234567890304");
        var shopB = openShop("c-shipb@example.com", "0814000005", "ร้านบี", "Shop B", "1234567890305");
        var productA = publishedVariantProduct(shopA, "SA");
        var productB = publishedVariantProduct(shopB, "SB");
        var buyer = token("c-ship-buyer@example.com", "0814000104");

        // shop A: flat 50, free at >= 500; shop B: weight based 30 first kg + 10 per extra kg
        var flat = merchant(post(base(shopA) + "/shipping-methods"), shopA.token,
            "{\"name\":\"Kerry\",\"carrierCode\":\"kerry\",\"feeType\":\"FLAT\",\"feeAmount\":50,\"freeShippingMinAmount\":500}")
            .andExpect(status().isCreated()).andExpect(jsonPath("$.carrierCode").value("KERRY"))
            .andReturn().getResponse().getContentAsString();
        merchant(post(base(shopA) + "/shipping-methods"), shopA.token, "{\"name\":\"EMS\",\"feeType\":\"FLAT\",\"feeAmount\":80}")
            .andExpect(status().isCreated());
        merchant(post(base(shopB) + "/shipping-methods"), shopB.token,
            "{\"name\":\"Flash\",\"feeType\":\"WEIGHT\",\"feeAmount\":30,\"extraFeePerKg\":10}").andExpect(status().isCreated());
        merchant(post(base(shopB) + "/shipping-methods"), shopB.token, "{\"name\":\"\",\"feeType\":\"BOAT\",\"feeAmount\":-1}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.length()").value(3));
        jdbc.update("UPDATE product_skus SET weight_grams = 1200 WHERE sku_code = 'SB-BLK-S'");

        // A: 2 x 200 = 400 < 500 -> 50 (cheapest), amountToFreeShipping 100
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":2}".formatted(sku(productA, "SA-BLK-S")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].shipping.methodName").value("Kerry"))
            .andExpect(jsonPath("$.groups[0].shipping.fee").value(50.0))
            .andExpect(jsonPath("$.groups[0].shipping.amountToFreeShipping").value(100.0))
            .andExpect(jsonPath("$.groups[0].shipping.options.length()").value(2));
        // B: 2 x 200, 2 x 1.2kg = 2.4kg -> 3 kg -> 30 + 2*10 = 50
        var both = cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":2}".formatted(sku(productB, "SB-BLK-S")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(2))
            .andReturn().getResponse().getContentAsString();
        assertEquals(List.of(2400), JsonPath.<List<Integer>>read(both, "$.groups[?(@.shopId == " + shopB.shopId + ")].shipping.totalWeightGrams"));
        assertEquals(List.of(50.0), JsonPath.<List<Double>>read(both, "$.groups[?(@.shopId == " + shopB.shopId + ")].shipping.fee"));
        // A: add Black/M 250 -> 650 >= 500 -> free shipping
        var full = cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\"}".formatted(sku(productA, "SA-BLK-M")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String aGroup = "$.groups[?(@.shopId == " + shopA.shopId + ")]";
        assertEquals(List.of(650.0), JsonPath.<List<Double>>read(full, aGroup + ".totals.subtotal"));
        assertEquals(List.of(0.0), JsonPath.<List<Double>>read(full, aGroup + ".shipping.fee"));
        assertEquals(List.of(650.0), JsonPath.<List<Double>>read(full, aGroup + ".totals.grandTotal"));
        // overall: 650 + (400 + 50)
        assertEquals(1050.0, JsonPath.<Double>read(full, "$.summary.subtotal"));
        assertEquals(50.0, JsonPath.<Double>read(full, "$.summary.shippingFee"));
        assertEquals(1100.0, JsonPath.<Double>read(full, "$.summary.grandTotal"));
        assertEquals(5, (int) JsonPath.<Integer>read(full, "$.counts.itemQuantity"));

        // buyer picks the EMS method for shop A: fee 80 even above the Kerry threshold (EMS has none)
        var methods = JsonPath.<List<String>>read(full, aGroup + ".shipping.options[?(@.name == 'EMS')].id");
        cart(put("/api/cart/shops/" + shopA.shopId + "/shipping-method"), buyer, "{\"shippingMethodId\":\"%s\"}".formatted(methods.get(0)))
            .andExpect(status().isOk());
        var ems = cart(get("/api/cart"), buyer).andReturn().getResponse().getContentAsString();
        assertEquals(List.of(80.0), JsonPath.<List<Double>>read(ems, aGroup + ".shipping.fee"));
        assertEquals(List.of(false), JsonPath.<List<Boolean>>read(ems, aGroup + ".shipping.autoSelected"));
        // another shop's method id is rejected
        String flatId = JsonPath.read(flat, "$.id");
        cart(put("/api/cart/shops/" + shopB.shopId + "/shipping-method"), buyer, "{\"shippingMethodId\":\"%s\"}".formatted(flatId))
            .andExpect(status().isNotFound());
        // disabling a method drops it from the options
        merchant(post(base(shopA) + "/shipping-methods/" + flatId + "/disable"), shopA.token).andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
        var after = cart(get("/api/cart"), buyer).andReturn().getResponse().getContentAsString();
        assertEquals(1, JsonPath.<List<Object>>read(after, aGroup + ".shipping.options[*]").size());
        // percent rounding HALF_UP to 2 dp: 12.5% of 650 = 81.25; 7.77% of 650 = 50.505 -> 50.51
        createCoupon(shopA, "{\"code\":\"ODD\",\"type\":\"PERCENT\",\"value\":7.77}");
        var odd = applyCoupon(buyer, shopA, "ODD").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(List.of(50.51), JsonPath.<List<Double>>read(odd, aGroup + ".totals.discount"));
        // clear one shop only
        cart(delete("/api/cart?shopId=" + shopA.shopId), buyer).andExpect(status().isOk())
            .andExpect(jsonPath("$.groups.length()").value(1)).andExpect(jsonPath("$.groups[0].shopId").value(shopB.shopId));
    }

    // ---------------- checkout validation ----------------

    @Test
    void validateDetectsPriceChangeOutOfStockInsufficientAndUnavailable() throws Exception {
        var shop = openShop("c-valid@example.com", "0814000006", "ร้านตรวจ", "Validate", "1234567890306");
        var product = publishedVariantProduct(shop, "VA");
        var buyer = token("c-valid-buyer@example.com", "0814000106");
        var blkS = sku(product, "VA-BLK-S");
        var blkM = sku(product, "VA-BLK-M");
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":2}".formatted(blkS)).andExpect(status().isOk());
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":3}".formatted(blkM)).andExpect(status().isOk());

        cart(post("/api/cart/validate"), buyer).andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.issues.length()").value(0));

        // price raised, stock of Black/M drops to 1
        jdbc.update("UPDATE product_skus SET price = 220.00 WHERE id = ?", UUID.fromString(blkS));
        jdbc.update("UPDATE inventory_levels SET on_hand = 1, available = 1 WHERE sku_id = ?", UUID.fromString(blkM));
        var result = cart(post("/api/cart/validate"), buyer).andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andReturn().getResponse().getContentAsString();
        assertEquals(List.of("PRICE_CHANGED"), JsonPath.<List<String>>read(result, "$.issues[?(@.skuId == '" + blkS + "')].code"));
        assertEquals(List.of(200.0), JsonPath.<List<Double>>read(result, "$.issues[?(@.code == 'PRICE_CHANGED')].details.previousPrice"));
        assertEquals(List.of(220.0), JsonPath.<List<Double>>read(result, "$.issues[?(@.code == 'PRICE_CHANGED')].details.currentPrice"));
        assertEquals(List.of(1), JsonPath.<List<Integer>>read(result, "$.issues[?(@.code == 'INSUFFICIENT_STOCK')].details.availableQuantity"));
        // cart is repriced from the DB
        assertEquals(List.of(440.0), JsonPath.<List<Double>>read(result, "$.cart.groups[0].items[?(@.skuId == '" + blkS + "')].lineTotal"));
        assertEquals(List.of(true), JsonPath.<List<Boolean>>read(result, "$.cart.groups[0].items[?(@.skuId == '" + blkS + "')].priceChanged"));

        // accept the new prices, fix quantity -> valid again
        cart(post("/api/cart/accept-prices"), buyer).andExpect(status().isOk());
        String mLine = JsonPath.<List<String>>read(result, "$.cart.groups[0].items[?(@.skuId == '" + blkM + "')].id").get(0);
        cart(patch("/api/cart/items/" + mLine), buyer, "{\"quantity\":1}").andExpect(status().isOk());
        cart(post("/api/cart/validate"), buyer).andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));

        // out of stock
        jdbc.update("UPDATE inventory_levels SET on_hand = 0, available = 0 WHERE sku_id = ?", UUID.fromString(blkM));
        cart(post("/api/cart/validate"), buyer).andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.issues[0].code").value("OUT_OF_STOCK"));
        // deselected out-of-stock line is not checked
        cart(put("/api/cart/selection"), buyer, "{\"itemIds\":[\"%s\"],\"selected\":false}".formatted(mLine)).andExpect(status().isOk());
        cart(post("/api/cart/validate"), buyer).andExpect(jsonPath("$.valid").value(true));

        // product archived -> unavailable
        String productId = JsonPath.read(product, "$.id");
        merchant(post(base(shop) + "/products/" + productId + "/archive"), shop.token).andExpect(status().isOk());
        cart(post("/api/cart/validate"), buyer).andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.issues[0].code").value("PRODUCT_UNAVAILABLE"))
            .andExpect(jsonPath("$.cart.groups[0].items[0].status").value("PRODUCT_UNAVAILABLE"))
            .andExpect(jsonPath("$.cart.summary.subtotal").value(440.0));

        // empty selection
        cart(delete("/api/cart"), buyer).andExpect(status().isOk());
        cart(post("/api/cart/validate"), buyer).andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.issues[0].code").value("CART_EMPTY"));
    }

    // ---------------- guest cart + merge ----------------

    @Test
    void guestCartWithTokenAndMergeIntoBuyerCartOnLogin() throws Exception {
        var shop = openShop("c-guest@example.com", "0814000007", "ร้านแขก", "Guest", "1234567890307");
        var product = publishedVariantProduct(shop, "GU");
        var blkS = sku(product, "GU-BLK-S"); // 5
        var blkM = sku(product, "GU-BLK-M"); // 3

        // anonymous GET without token = empty cart
        mvc.perform(get("/api/guest-cart")).andExpect(status().isOk()).andExpect(jsonPath("$.groups.length()").value(0));
        // first add issues a token
        var first = mvc.perform(post("/api/guest-cart/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":\"%s\",\"quantity\":4}".formatted(blkS)))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Cart-Token"))
            .andReturn();
        String token = first.getResponse().getHeader("X-Cart-Token");
        assertEquals(token, JsonPath.read(first.getResponse().getContentAsString(), "$.cartToken"));
        guest(post("/api/guest-cart/items"), token, "{\"skuId\":\"%s\",\"quantity\":2}".formatted(blkM))
            .andExpect(status().isOk()).andExpect(jsonPath("$.counts.itemQuantity").value(6));
        guest(get("/api/guest-cart/count"), token, null).andExpect(jsonPath("$.lineCount").value(2));
        // token is stored hashed only
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE guest_token_hash = ?", Integer.class, token));
        // another guest token sees nothing and cannot touch these lines
        var otherToken = "ab".repeat(24);
        guest(get("/api/guest-cart"), otherToken, null).andExpect(jsonPath("$.groups.length()").value(0));
        var guestView = guest(get("/api/guest-cart"), token, null).andReturn().getResponse().getContentAsString();
        String guestLine = JsonPath.read(guestView, "$.groups[0].items[0].id");
        guest(patch("/api/guest-cart/items/" + guestLine), otherToken, "{\"quantity\":1}").andExpect(status().isNotFound());
        guest(patch("/api/guest-cart/items/" + guestLine), "not-a-token!", "{\"quantity\":1}").andExpect(status().isBadRequest());
        // guest validate: valid but LOGIN_REQUIRED info
        guest(post("/api/guest-cart/validate"), token, null).andExpect(status().isOk())
            .andExpect(jsonPath("$.issues[*].code", hasItem("LOGIN_REQUIRED")));

        // buyer already has 3 x Black/S; merge: 3 + 4 > 5 available -> capped to 5; Black/M moves over
        var buyer = token("c-guest-buyer@example.com", "0814000107");
        cart(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":3}".formatted(blkS)).andExpect(status().isOk());
        var merged = mvc.perform(post("/api/cart/merge").header("Authorization", "Bearer " + buyer).header("X-Cart-Token", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].items.length()").value(2))
            .andExpect(jsonPath("$.mergeNotes[0].code").value("QUANTITY_ADJUSTED"))
            .andExpect(jsonPath("$.mergeNotes[0].appliedQuantity").value(2))
            .andReturn().getResponse().getContentAsString();
        assertEquals(List.of(5), JsonPath.<List<Integer>>read(merged, "$.groups[0].items[?(@.skuId == '" + blkS + "')].quantity"));
        assertEquals(List.of(2), JsonPath.<List<Integer>>read(merged, "$.groups[0].items[?(@.skuId == '" + blkM + "')].quantity"));
        // guest cart is gone; merging again changes nothing
        guest(get("/api/guest-cart"), token, null).andExpect(jsonPath("$.groups.length()").value(0));
        mvc.perform(post("/api/cart/merge").header("Authorization", "Bearer " + buyer).header("X-Cart-Token", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.counts.itemQuantity").value(7));
        // merge needs a signed-in buyer
        mvc.perform(post("/api/cart/merge").header("X-Cart-Token", token)).andExpect(status().isUnauthorized());
    }

    // ---------------- isolation + permissions ----------------

    @Test
    void buyersCannotTouchOtherCartsAndCouponsArePerShopWithShopPermissions() throws Exception {
        var shopA = openShop("c-isoa@example.com", "0814000008", "ร้านไอเอ", "Iso A", "1234567890308");
        var shopB = openShop("c-isob@example.com", "0814000009", "ร้านไอบี", "Iso B", "1234567890309");
        var productA = publishedVariantProduct(shopA, "IA");
        var alice = token("c-alice@example.com", "0814000108");
        var bob = token("c-bob@example.com", "0814000109");
        var added = cart(post("/api/cart/items"), alice, "{\"skuId\":\"%s\"}".formatted(sku(productA, "IA-BLK-S")))
            .andReturn().getResponse().getContentAsString();
        String aliceLine = JsonPath.read(added, "$.groups[0].items[0].id");

        // Bob sees an empty cart and cannot update / remove Alice's line
        cart(get("/api/cart"), bob).andExpect(jsonPath("$.groups.length()").value(0));
        cart(patch("/api/cart/items/" + aliceLine), bob, "{\"quantity\":2}").andExpect(status().isNotFound());
        cart(delete("/api/cart/items/" + aliceLine), bob).andExpect(status().isNotFound());
        cart(post("/api/cart/items/remove"), bob, "{\"itemIds\":[\"%s\"]}".formatted(aliceLine)).andExpect(status().isNotFound());
        cart(get("/api/cart"), alice).andExpect(jsonPath("$.groups[0].items[0].quantity").value(1));
        // guest token cannot reach a buyer line either
        guest(delete("/api/guest-cart/items/" + aliceLine), "cd".repeat(24), null).andExpect(status().isNotFound());

        // shop B's coupon does not apply to shop A's cart
        createCoupon(shopB, "{\"code\":\"BONLY\",\"type\":\"FIXED\",\"value\":10}");
        applyCoupon(alice, shopA, "BONLY").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));
        // coupon for a shop the buyer has no cart in
        applyCoupon(alice, shopB, "BONLY").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CART_EMPTY"));

        // anonymous -> 401 on buyer endpoints
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        // merchant APIs: other shop's owner -> 404, buyer -> 404, viewer member -> read ok / write 403
        merchant(get(base(shopA) + "/coupons"), shopB.token).andExpect(status().isNotFound());
        merchant(post(base(shopA) + "/coupons"), bob, "{\"code\":\"HACK\",\"type\":\"FIXED\",\"value\":10}").andExpect(status().isNotFound());
        var viewerToken = token("c-viewer@example.com", "0814000110");
        addMember(shopA.shopId, userId(viewerToken), "STORE_VIEWER");
        merchant(get(base(shopA) + "/coupons"), viewerToken).andExpect(status().isOk());
        merchant(post(base(shopA) + "/coupons"), viewerToken, "{\"code\":\"VIEW\",\"type\":\"FIXED\",\"value\":10}")
            .andExpect(status().isForbidden());
        merchant(post(base(shopA) + "/shipping-methods"), viewerToken, "{\"name\":\"X\",\"feeAmount\":10}")
            .andExpect(status().isForbidden());
        // another shop's coupon id through this shop's URL
        var bCoupon = coupons.findAll().stream().filter(c -> c.getCode().equals("BONLY")).findFirst().orElseThrow();
        merchant(get(base(shopA) + "/coupons/" + bCoupon.getId()), shopA.token).andExpect(status().isNotFound());
    }

    // ---------------- helpers ----------------

    private ResultActions cart(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions cart(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions merchant(MockHttpServletRequestBuilder request, String token) throws Exception {
        return cart(request, token);
    }

    private ResultActions merchant(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return cart(request, token, json);
    }

    private ResultActions guest(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        request.header("X-Cart-Token", token);
        if (json != null) request.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(request);
    }

    private ResultActions applyCoupon(String buyer, OpenShop shop, String code) throws Exception {
        return cart(post("/api/cart/shops/" + shop.shopId + "/coupon"), buyer, "{\"code\":\"%s\"}".formatted(code));
    }

    private String createCoupon(OpenShop shop, String json) throws Exception {
        return merchant(post(base(shop) + "/coupons"), shop.token, json)
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private String publishedVariantProduct(OpenShop shop, String prefix) throws Exception {
        var created = createProduct(shop, COLOR_SIZE.formatted(activeCategory(0), prefix, prefix, prefix));
        String productId = JsonPath.read(created, "$.id");
        var file = new MockMultipartFile("file", "photo.png", "image/png", PNG);
        mvc.perform(multipart(base(shop) + "/products/" + productId + "/images").file(file)
            .header("Authorization", "Bearer " + shop.token)).andExpect(status().isOk());
        return merchant(post(base(shop) + "/products/" + productId + "/publish"), shop.token)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String createProduct(OpenShop shop, String json) throws Exception {
        return merchant(post(base(shop) + "/products"), shop.token, json)
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
            var approver = users.save(UserAccount.systemAccount("cart-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"cart-approver@example.com","password":"Password1"}
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

    private record OpenShop(String token, long shopId) {}
}
