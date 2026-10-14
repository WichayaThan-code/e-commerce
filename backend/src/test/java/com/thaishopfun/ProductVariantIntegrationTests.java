package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.catalog.InventoryLedgerRepository;
import com.thaishopfun.catalog.ProductOptionGroupRepository;
import com.thaishopfun.catalog.ProductOptionValueRepository;
import com.thaishopfun.catalog.ProductSkuOptionValueRepository;
import com.thaishopfun.catalog.InventoryLevelRepository;
import com.thaishopfun.catalog.ProductImageRepository;
import com.thaishopfun.catalog.ProductRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Variant products: option groups (Color, Size), values, variants mapped to one value per group, each with its own
 * SKU, barcode, price, cost, image and stock; validation, update flow, permissions and shop isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductVariantIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
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
           {"name":"Size","values":[{"value":"S"},{"value":"M"},{"value":"L"}]}],
         "skus":[
           {"skuCode":"TS-BLK-S","barcode":"8850000000101","optionValues":{"Color":"Black","Size":"S"},"price":199.00,"costPrice":80.00,"initialStock":5},
           {"skuCode":"TS-BLK-M","barcode":"8850000000102","optionValues":{"Color":"Black","Size":"M"},"price":209.00,"costPrice":82.50,"initialStock":3},
           {"skuCode":"TS-WHT-S","barcode":"8850000000103","optionValues":{"Color":"White","Size":"S"},"price":219.00,"costPrice":0,"initialStock":0}]}
        """;

    @Test
    void colorSizeVariantsAreSavedWithOwnSkuBarcodePriceCostImageAndStock() throws Exception {
        var shop = openShop("pv-create@example.com", "0813000001", "ร้านเสื้อ", "Shirt Shop", "1234567890201");
        var category = activeCategory(0);
        var created = perform(post(base(shop) + "/products"), shop.token, COLOR_SIZE.formatted(category))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productType").value("VARIABLE"))
            .andExpect(jsonPath("$.basePrice").value(199.0))
            .andExpect(jsonPath("$.optionGroups.length()").value(2))
            .andExpect(jsonPath("$.optionGroups[0].name").value("Color"))
            .andExpect(jsonPath("$.optionGroups[0].values[*].value", contains("Black", "White")))
            .andExpect(jsonPath("$.optionGroups[1].name").value("Size"))
            .andExpect(jsonPath("$.optionGroups[1].values[*].value", contains("S", "M", "L")))
            .andExpect(jsonPath("$.skus.length()").value(3))
            .andReturn().getResponse().getContentAsString();
        String productId = JsonPath.read(created, "$.id");
        assertEquals("Black", JsonPath.<List<String>>read(created, "$.skus[?(@.skuCode == 'TS-BLK-M')].options[0].value").get(0));
        assertEquals("M", JsonPath.<List<String>>read(created, "$.skus[?(@.skuCode == 'TS-BLK-M')].options[1].value").get(0));
        assertEquals("Black / M", JsonPath.<List<String>>read(created, "$.skus[?(@.skuCode == 'TS-BLK-M')].optionLabel").get(0));
        assertEquals(List.of(5), JsonPath.<List<Integer>>read(created, "$.skus[?(@.skuCode == 'TS-BLK-S')].onHand"));
        assertEquals(List.of(3), JsonPath.<List<Integer>>read(created, "$.skus[?(@.skuCode == 'TS-BLK-M')].onHand"));
        assertEquals(List.of(0), JsonPath.<List<Integer>>read(created, "$.skus[?(@.skuCode == 'TS-WHT-S')].onHand"));
        assertEquals(List.of(82.5), JsonPath.<List<Double>>read(created, "$.skus[?(@.skuCode == 'TS-BLK-M')].costPrice"));
        assertEquals(List.of(219.0), JsonPath.<List<Double>>read(created, "$.skus[?(@.skuCode == 'TS-WHT-S')].price"));
        assertEquals(List.of("8850000000103"), JsonPath.<List<String>>read(created, "$.skus[?(@.skuCode == 'TS-WHT-S')].barcode"));

        // saved in the database: 2 groups, 5 values, 3 SKUs x 2 mapping rows, opening stock through the ledger
        assertEquals(2, optionGroups.count());
        assertEquals(5, optionValues.count());
        assertEquals(6, skuOptions.count());
        assertEquals(3, productSkus.count());
        assertEquals(2, ledger.count(), "only SKUs with opening stock > 0 get a RECEIVE entry");
        assertEquals(8, (int) jdbc.queryForObject("SELECT SUM(on_hand) FROM inventory_levels", Integer.class));

        // per-variant image: upload to the product, then point White/S at it
        var withImage = upload(shop, productId, PNG).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String imageId = JsonPath.read(withImage, "$.images[0].id");
        var updateJson = updateBody(created, category, Map.of("TS-WHT-S", imageId));
        var updated = perform(put(base(shop) + "/products/" + productId), shop.token, updateJson)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(List.of(imageId), JsonPath.<List<String>>read(updated, "$.skus[?(@.skuCode == 'TS-WHT-S')].imageId"));
        assertEquals(1, JsonPath.<List<Object>>read(updated, "$.skus[?(@.imageId == null)]").size() - 1, "the two black variants keep no image");
        assertEquals(6, skuOptions.count());

        perform(post(base(shop) + "/products/" + productId + "/publish"), shop.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        // the public product exposes the groups and each variant's options, image and availability (not the cost)
        var publicShopSlug = shops.findByShopId(shop.shopId).orElseThrow().getSlug();
        var slug = catalogProducts.findById(UUID.fromString(productId)).orElseThrow().getSlug();
        var pub = mvc.perform(get("/api/shops/" + publicShopSlug + "/products/" + slug)).andExpect(status().isOk())
            .andExpect(jsonPath("$.optionGroups.length()").value(2))
            .andExpect(jsonPath("$.skus.length()").value(3))
            .andReturn().getResponse().getContentAsString();
        assertEquals(List.of(imageId), JsonPath.<List<String>>read(pub, "$.skus[?(@.skuCode == 'TS-WHT-S')].imageId"));
        assertFalse(pub.contains("costPrice"));

        // deleting the image clears the variant image
        perform(post(base(shop) + "/products/" + productId + "/archive"), shop.token).andExpect(status().isOk());
        perform(delete(base(shop) + "/products/" + productId + "/images/" + imageId), shop.token).andExpect(status().isOk());
        var after = perform(get(base(shop) + "/products/" + productId), shop.token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(3, JsonPath.<List<Object>>read(after, "$.skus[?(@.imageId == null)]").size());
    }

    @Test
    void updateRenamesValuesAddsAndRemovesVariantsAndKeepsTheStockLedger() throws Exception {
        var shop = openShop("pv-update@example.com", "0813000002", "ร้านอัปเดต", "Update Shop", "1234567890202");
        var category = activeCategory(0);
        var created = perform(post(base(shop) + "/products"), shop.token, COLOR_SIZE.formatted(category))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String productId = JsonPath.read(created, "$.id");
        String colorId = JsonPath.read(created, "$.optionGroups[0].id");
        String blackId = JsonPath.read(created, "$.optionGroups[0].values[0].id");
        String whiteId = JsonPath.read(created, "$.optionGroups[0].values[1].id");
        String sizeId = JsonPath.read(created, "$.optionGroups[1].id");
        String sId = JsonPath.read(created, "$.optionGroups[1].values[0].id");
        String mId = JsonPath.read(created, "$.optionGroups[1].values[1].id");
        String blkS = sku(created, "TS-BLK-S");
        String whtS = sku(created, "TS-WHT-S");
        String blkM = sku(created, "TS-BLK-M");

        // rename White -> Ivory (same id), drop size L, drop variant Black/M, add Ivory/M with opening stock 4;
        // initialStock on an existing SKU is ignored (stock changes go through the adjustment endpoint)
        var body = """
            {"nameTh":"เสื้อยืด","productType":"VARIABLE","categoryId":%d,
             "optionGroups":[
               {"id":"%s","name":"Colour","values":[{"id":"%s","value":"Black"},{"id":"%s","value":"Ivory"}]},
               {"id":"%s","name":"Size","values":[{"id":"%s","value":"S"},{"id":"%s","value":"M"}]}],
             "skus":[
               {"id":"%s","skuCode":"TS-BLK-S","barcode":"8850000000101","optionValues":{"Colour":"Black","Size":"S"},"price":189.00,"costPrice":80.00,"initialStock":99},
               {"id":"%s","skuCode":"TS-IVR-S","barcode":"8850000000103","optionValues":{"Colour":"Ivory","Size":"S"},"price":219.00},
               {"skuCode":"TS-IVR-M","barcode":"8850000000104","optionValues":{"Colour":"Ivory","Size":"M"},"price":229.00,"costPrice":90,"initialStock":4}]}
            """.formatted(category, colorId, blackId, whiteId, sizeId, sId, mId, blkS, whtS);
        var updated = perform(put(base(shop) + "/products/" + productId), shop.token, body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.optionGroups[0].id").value(colorId))
            .andExpect(jsonPath("$.optionGroups[0].name").value("Colour"))
            .andExpect(jsonPath("$.optionGroups[0].values[1].id").value(whiteId))
            .andExpect(jsonPath("$.optionGroups[0].values[1].value").value("Ivory"))
            .andExpect(jsonPath("$.optionGroups[1].values.length()").value(2))
            .andExpect(jsonPath("$.skus.length()").value(3))
            .andReturn().getResponse().getContentAsString();
        assertEquals(List.of(5), JsonPath.<List<Integer>>read(updated, "$.skus[?(@.skuCode == 'TS-BLK-S')].onHand"));
        assertEquals(List.of(4), JsonPath.<List<Integer>>read(updated, "$.skus[?(@.skuCode == 'TS-IVR-M')].onHand"));
        assertEquals(List.of("Ivory / S"), JsonPath.<List<String>>read(updated, "$.skus[?(@.skuCode == 'TS-IVR-S')].optionLabel"));
        assertEquals(List.of(189.0), JsonPath.<List<Double>>read(updated, "$.skus[?(@.skuCode == 'TS-BLK-S')].price"));

        // the removed variant is soft-deleted: row, stock level and ledger history stay
        var removed = productSkus.findById(UUID.fromString(blkM)).orElseThrow();
        assertNotNull(removed.getDeletedAt());
        assertFalse(removed.isActive());
        assertEquals(3, (int) jdbc.queryForObject("SELECT on_hand FROM inventory_levels WHERE sku_id = ?", Integer.class, UUID.fromString(blkM)));
        assertEquals(3, ledger.count(), "Black/S and Black/M opening + Ivory/M opening");
        perform(get(base(shop) + "/products/" + productId + "/ledger"), shop.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$[*].skuCode", hasItems("TS-BLK-M", "TS-BLK-S", "TS-IVR-M")));
        assertEquals(4, productSkus.count());
        assertEquals(2, optionGroups.count());
        assertEquals(4, optionValues.count());
        // its code stays reserved in the shop so history keeps pointing at one SKU
        perform(put(base(shop) + "/products/" + productId), shop.token, body.replace("TS-IVR-M", "TS-BLK-M"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.2.skuCode')].code", hasItem("SKU_CODE_TAKEN")));

        // removing a value that a variant still uses is rejected clearly
        var dropIvory = """
            {"nameTh":"เสื้อยืด","productType":"VARIABLE","categoryId":%d,
             "optionGroups":[{"id":"%s","name":"Colour","values":[{"id":"%s","value":"Black"}]},
                             {"id":"%s","name":"Size","values":[{"id":"%s","value":"S"},{"id":"%s","value":"M"}]}],
             "skus":[{"id":"%s","skuCode":"TS-BLK-S","optionValues":{"Colour":"Black","Size":"S"},"price":189.00},
                     {"id":"%s","skuCode":"TS-IVR-S","optionValues":{"Colour":"Ivory","Size":"S"},"price":219.00}]}
            """.formatted(category, colorId, blackId, sizeId, sId, mId, blkS, whtS);
        perform(put(base(shop) + "/products/" + productId), shop.token, dropIvory)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.1.optionValues.Colour')].code", hasItem("OPTION_VALUE_IN_USE")));
        assertEquals(4, optionValues.count(), "nothing changed");

        // switching to SIMPLE removes the option data but not the SKU history
        perform(put(base(shop) + "/products/" + productId), shop.token, """
            {"nameTh":"เสื้อยืด","productType":"SIMPLE","categoryId":%d,"skus":[{"id":"%s","skuCode":"TS-BLK-S","price":189.00}]}
            """.formatted(category, blkS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.optionGroups.length()").value(0)).andExpect(jsonPath("$.skus[0].options.length()").value(0));
        assertEquals(0, optionGroups.count());
        assertEquals(0, skuOptions.count());
    }

    @Test
    void groupsAreDerivedFromSkuOptionValuesWhenTheFormSendsOnlyThose() throws Exception {
        var shop = openShop("pv-derived@example.com", "0813000003", "ร้านฟอร์ม", "Form Shop", "1234567890203");
        var category = activeCategory(0);
        var created = perform(post(base(shop) + "/products"), shop.token, """
            {"nameTh":"กางเกง","productType":"VARIABLE","categoryId":%d,"skus":[
              {"skuCode":"PT-1","optionValues":{"สี":"ดำ","ไซซ์":"S"},"price":300},
              {"skuCode":"PT-2","optionValues":{"สี":"ขาว","ไซซ์":"S"},"price":300}]}
            """.formatted(category))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.optionGroups[*].name", contains("สี", "ไซซ์")))
            .andExpect(jsonPath("$.optionGroups[0].values[*].value", contains("ดำ", "ขาว")))
            .andReturn().getResponse().getContentAsString();
        assertEquals(4, skuOptions.count());
        // same strict rules: a SKU missing a group, or repeating a combination, is rejected
        perform(post(base(shop) + "/products"), shop.token, """
            {"nameTh":"กางเกง2","productType":"VARIABLE","categoryId":%d,"skus":[
              {"skuCode":"PT-3","optionValues":{"สี":"ดำ","ไซซ์":"S"},"price":300},
              {"skuCode":"PT-4","optionValues":{"สี":"ดำ"},"price":300},
              {"skuCode":"PT-5","optionValues":{"สี":"ดำ","ไซซ์":"S"},"price":300}]}
            """.formatted(category))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.1.optionValues.ไซซ์')].code", hasItem("VARIANT_OPTION_REQUIRED")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.2.optionValues')].code", hasItem("VARIANT_COMBINATION_DUPLICATE")));
        assertNotNull(created);
    }

    @Test
    void invalidVariantDataIsRejectedWithFieldErrorsAndNothingIsSaved() throws Exception {
        var shop = openShop("pv-valid@example.com", "0813000004", "ร้านตรวจ", "Valid Shop", "1234567890204");
        var category = activeCategory(0);
        var first = perform(post(base(shop) + "/products"), shop.token, COLOR_SIZE.formatted(category)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var other = perform(post(base(shop) + "/products"), shop.token, """
            {"nameTh":"หมวก","productType":"SIMPLE","categoryId":%d,"skus":[{"skuCode":"HAT-1","price":50}]}
            """.formatted(category)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String otherId = JsonPath.read(other, "$.id");
        String foreignImage = JsonPath.read(upload(shop, otherId, PNG).andReturn().getResponse().getContentAsString(), "$.images[0].id");
        var products = catalogProducts.count();
        var skuRows = productSkus.count();

        perform(post(base(shop) + "/products"), shop.token, """
            {"nameTh":"ผิดหมด","productType":"VARIABLE","categoryId":%d,
             "optionGroups":[
               {"name":"Color","values":[{"value":"Red"},{"value":"red"}]},
               {"name":" color ","values":[{"value":"X"}]},
               {"name":"","values":[]},
               {"name":"Material","values":[{"value":"Cotton"}]}],
             "skus":[
               {"skuCode":"","optionValues":{"Color":"Red","Material":"Cotton"},"price":0,"costPrice":-1,"initialStock":-1},
               {"skuCode":"V-2","barcode":"8850000000101","optionValues":{"Color":"Blue","Material":"Cotton","Weight":"1kg"},"price":10,"imageId":"%s"},
               {"skuCode":"V-3","optionValues":{"Material":"Cotton"},"price":10},
               {"skuCode":"TS-BLK-S","optionValues":{"Color":"Red","Material":"Cotton"},"price":10}]}
            """.formatted(category, foreignImage))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'optionGroups')].code", hasItem("OPTION_GROUP_LIMIT")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'optionGroups.0.values.1.value')].code", hasItem("OPTION_VALUE_DUPLICATE")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'optionGroups.1.name')].code", hasItem("OPTION_GROUP_DUPLICATE")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'optionGroups.2.name')].code", hasItem("OPTION_GROUP_NAME_REQUIRED")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'optionGroups.2.values')].code", hasItem("OPTION_VALUE_REQUIRED")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.0.skuCode')].code", hasItem("SKU_CODE_REQUIRED")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.0.price')].code", hasItem("PRICE_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.0.costPrice')].code", hasItem("COST_PRICE_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.0.initialStock')].code", hasItem("STOCK_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.1.barcode')].code", hasItem("BARCODE_TAKEN")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.1.imageId')].code", hasItem("VARIANT_IMAGE_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.1.optionValues.Color')].code", hasItem("OPTION_VALUE_UNKNOWN")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.1.optionValues.Weight')].code", hasItem("OPTION_GROUP_UNKNOWN")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.2.optionValues.Color')].code", hasItem("VARIANT_OPTION_REQUIRED")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.3.optionValues')].code", hasItem("VARIANT_COMBINATION_DUPLICATE")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.3.skuCode')].code", hasItem("SKU_CODE_TAKEN")));

        // VARIABLE with an explicit empty group list, SIMPLE with groups, image of another product on update
        perform(post(base(shop) + "/products"), shop.token, """
            {"nameTh":"ไม่มีกลุ่ม","productType":"VARIABLE","categoryId":%d,"optionGroups":[],"skus":[{"skuCode":"NG-1","price":10}]}
            """.formatted(category))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[?(@.field == 'optionGroups')].code", hasItem("OPTION_GROUP_REQUIRED")));
        perform(post(base(shop) + "/products"), shop.token, """
            {"nameTh":"เดี่ยว","productType":"SIMPLE","categoryId":%d,"optionGroups":[{"name":"Color","values":[{"value":"Red"}]}],"skus":[{"skuCode":"SG-1","price":10}]}
            """.formatted(category))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[?(@.field == 'optionGroups')].code", hasItem("OPTION_GROUPS_NOT_ALLOWED")));
        String firstId = JsonPath.read(first, "$.id");
        perform(put(base(shop) + "/products/" + firstId), shop.token, updateBody(first, category, Map.of("TS-BLK-S", foreignImage)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus.1.imageId')].code", hasItem("VARIANT_IMAGE_INVALID")));

        assertEquals(products, catalogProducts.count());
        assertEquals(skuRows, productSkus.count());
        assertEquals(2, optionGroups.count());
        assertEquals(5, optionValues.count());
    }

    @Test
    void combinationHelperListsEveryCombination() throws Exception {
        var shop = openShop("pv-combo@example.com", "0813000005", "ร้านช่วย", "Combo Shop", "1234567890205");
        perform(post(base(shop) + "/products/variant-combinations"), shop.token, """
            {"skuPrefix":"TS","optionGroups":[{"name":"Color","values":[{"value":"Black"},{"value":"White"}]},{"name":"Size","values":[{"value":"S"},{"value":"M"},{"value":"L"}]}]}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(6))
            .andExpect(jsonPath("$[0].optionLabel").value("Black / S"))
            .andExpect(jsonPath("$[0].optionValues.Color").value("Black"))
            .andExpect(jsonPath("$[0].skuCode").value("TS-BLACK-S"))
            .andExpect(jsonPath("$[5].skuCode").value("TS-WHITE-L"));
        perform(post(base(shop) + "/products/variant-combinations"), shop.token, """
            {"optionGroups":[{"name":"Color","values":[{"value":"Black"},{"value":"black"}]}]}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'optionGroups.0.values.1.value')].code", hasItem("OPTION_VALUE_DUPLICATE")));
        assertEquals(0, catalogProducts.count());
    }

    @Test
    void permissionsAndShopIsolationApplyToVariants() throws Exception {
        var shop = openShop("pv-owner@example.com", "0813000006", "ร้านเจ้าของ", "Variant Owner", "1234567890206");
        var other = openShop("pv-rival@example.com", "0813000007", "ร้านคู่แข่ง", "Variant Rival", "1234567890207");
        var category = activeCategory(0);
        var created = perform(post(base(shop) + "/products"), shop.token, COLOR_SIZE.formatted(category)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String productId = JsonPath.read(created, "$.id");

        var viewerToken = token("pv-viewer@example.com", "0813000008");
        addMember(shop.shopId, users.findByEmailIgnoreCaseOrPhone("pv-viewer@example.com", "0813000008").orElseThrow().getId(), "STORE_VIEWER");
        perform(get(base(shop) + "/products/" + productId), viewerToken).andExpect(status().isOk()).andExpect(jsonPath("$.optionGroups.length()").value(2));
        perform(post(base(shop) + "/products"), viewerToken, COLOR_SIZE.formatted(category).replace("TS-", "VW-").replace("88500000001", "88500000009"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        perform(put(base(shop) + "/products/" + productId), viewerToken, updateBody(created, category, Map.of()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        perform(post(base(shop) + "/skus/" + sku(created, "TS-BLK-S") + "/stock-adjustments"), viewerToken, """
            {"entryType":"RECEIVE","quantityDelta":5,"version":1}
            """).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        // per-variant stock adjustment through the ledger by the owner
        perform(post(base(shop) + "/skus/" + sku(created, "TS-BLK-S") + "/stock-adjustments"), shop.token, """
            {"entryType":"RECEIVE","quantityDelta":2,"version":1}
            """).andExpect(status().isOk()).andExpect(jsonPath("$.onHand").value(7));

        perform(get(base(shop) + "/products/" + productId), other.token).andExpect(status().isNotFound());
        perform(get(base(other) + "/products/" + productId), other.token).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        perform(put(base(other) + "/products/" + productId), other.token, updateBody(created, category, Map.of())).andExpect(status().isNotFound());
        perform(post(base(shop) + "/products/variant-combinations"), other.token, """
            {"optionGroups":[{"name":"Color","values":[{"value":"Black"}]}]}
            """).andExpect(status().isNotFound());
        perform(post(base(other) + "/skus/" + sku(created, "TS-BLK-S") + "/stock-adjustments"), other.token, """
            {"entryType":"RECEIVE","quantityDelta":5,"version":2}
            """).andExpect(status().isNotFound());
        assertEquals(7, (int) jdbc.queryForObject("SELECT on_hand FROM inventory_levels WHERE sku_id = ?", Integer.class, UUID.fromString(sku(created, "TS-BLK-S"))));
        assertEquals(2, optionGroups.count());
    }

    /** PUT body that re-sends the product as returned, optionally setting variant images by SKU code. */
    private static String updateBody(String productJson, long category, Map<String, String> imagesBySku) {
        var skus = JsonPath.<List<Map<String, Object>>>read(productJson, "$.skus");
        var groups = JsonPath.<List<Map<String, Object>>>read(productJson, "$.optionGroups");
        var groupJson = groups.stream().map(group -> "{\"id\":\"%s\",\"name\":\"%s\",\"values\":[%s]}".formatted(group.get("id"), group.get("name"),
            ((List<Map<String, Object>>) group.get("values")).stream().map(value -> "{\"id\":\"%s\",\"value\":\"%s\"}".formatted(value.get("id"), value.get("value")))
                .collect(java.util.stream.Collectors.joining(",")))).collect(java.util.stream.Collectors.joining(","));
        var skuJson = skus.stream().map(sku -> {
            var options = ((List<Map<String, Object>>) sku.get("options")).stream().map(option -> "\"%s\":\"%s\"".formatted(option.get("groupName"), option.get("value")))
                .collect(java.util.stream.Collectors.joining(","));
            var image = imagesBySku.get((String) sku.get("skuCode"));
            return "{\"id\":\"%s\",\"skuCode\":\"%s\",\"barcode\":%s,\"optionValues\":{%s},\"price\":%s,\"costPrice\":%s,\"imageId\":%s}".formatted(
                sku.get("id"), sku.get("skuCode"), quoted((String) sku.get("barcode")), options, sku.get("price"), sku.get("costPrice"), quoted(image));
        }).collect(java.util.stream.Collectors.joining(","));
        return "{\"nameTh\":\"%s\",\"productType\":\"VARIABLE\",\"categoryId\":%d,\"optionGroups\":[%s],\"skus\":[%s]}".formatted(
            JsonPath.read(productJson, "$.nameTh"), category, groupJson, skuJson);
    }

    private static String sku(String productJson, String code) {
        return JsonPath.<List<String>>read(productJson, "$.skus[?(@.skuCode == '" + code + "')].id").get(0);
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions upload(OpenShop shop, String productId, byte[] bytes) throws Exception {
        return upload(shop.token, shop.shopId, productId, bytes);
    }

    private ResultActions upload(String token, long shopId, String productId, byte[] bytes) throws Exception {
        var file = new MockMultipartFile("file", "photo.png", "image/png", bytes);
        return mvc.perform(multipart("/api/merchant/shops/" + shopId + "/products/" + productId + "/images").file(file)
            .header("Authorization", "Bearer " + token));
    }

    private String create(OpenShop shop, String json) throws Exception {
        return create(shop.token, shop.shopId, json);
    }

    private String create(String token, long shopId, String json) throws Exception {
        var body = perform(post("/api/merchant/shops/" + shopId + "/products"), token, json)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String base(OpenShop shop) {
        return "/api/merchant/shops/" + shop.shopId;
    }

    private static String product(String nameTh, String nameEn, long categoryId, String brand, String sku, String barcode, String price) {
        return """
            {"nameTh":"%s","nameEn":"%s","productType":"SIMPLE","categoryId":%d,"brand":%s,"basePrice":%s,
             "skus":[{"skuCode":"%s","barcode":%s,"price":%s}]}
            """.formatted(nameTh, nameEn, categoryId, quoted(brand), price, sku, quoted(barcode), price);
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
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

    /** One platform admin per test: system accounts share a placeholder phone number. */
    private String adminToken() throws Exception {
        if (adminToken == null) {
            var approver = users.save(UserAccount.systemAccount("pv-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"pv-approver@example.com","password":"Password1"}
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
