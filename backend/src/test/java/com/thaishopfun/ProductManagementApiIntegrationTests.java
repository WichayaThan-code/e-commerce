package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.catalog.InventoryLedgerRepository;
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
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Merchant product management API: create/read/update/archive, search by name/SKU/barcode, category/brand/status
 * filters, paging and sorting, image upload/delete, field-level validation, permissions and shop isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductManagementApiIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0};

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryLedgerRepository ledger;
    @Autowired InventoryLevelRepository levels;
    @Autowired ProductImageRepository productImages;
    @Autowired ProductSkuRepository productSkus;
    @Autowired ProductRepository catalogProducts;
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
        ledger.deleteAll();
        levels.deleteAll();
        productImages.deleteAll();
        productSkus.deleteAll();
        catalogProducts.deleteAll();
        jdbc.update("DELETE FROM hub_categories WHERE slug LIKE 'pm-test-%'");
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
    void createReadUpdateAndArchiveAreSavedToTheDatabase() throws Exception {
        var shop = openShop("pm-crud@example.com", "0812000001", "ร้านครบ", "Crud Shop", "1234567890123");
        var category = activeCategory(0);
        // draft without basePrice: the price comes from the SKU, and the opening stock is received into the ledger
        var created = perform(post(base(shop) + "/products"), shop.token, """
            {"nameTh":"ชาเขียว","nameEn":"Green Tea","productType":"SIMPLE","categoryId":%d,"brand":"Tea House",
             "compareAtPrice":60.00,"skus":[{"skuCode":"TEA-1","barcode":"8850001234567","price":50.00,"initialStock":7}]}
            """.formatted(category))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.basePrice").value(50.0))
            .andExpect(jsonPath("$.categoryId").value(category))
            .andExpect(jsonPath("$.brand").value("Tea House"))
            .andExpect(jsonPath("$.skus[0].barcode").value("8850001234567"))
            .andExpect(jsonPath("$.skus[0].onHand").value(7))
            .andReturn();
        String productId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        String skuId = JsonPath.read(created.getResponse().getContentAsString(), "$.skus[0].id");

        var saved = catalogProducts.findById(UUID.fromString(productId)).orElseThrow();
        assertEquals("ชาเขียว", saved.getNameTh());
        assertEquals(0, new BigDecimal("50.00").compareTo(saved.getBasePrice()));
        assertEquals(1, productSkus.count());
        assertEquals(1, ledger.count());
        assertEquals(7, (int) jdbc.queryForObject("SELECT on_hand FROM inventory_levels WHERE sku_id = ?", Integer.class, UUID.fromString(skuId)));

        perform(get(base(shop) + "/products/" + productId), shop.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.nameTh").value("ชาเขียว")).andExpect(jsonPath("$.skus.length()").value(1));

        perform(put(base(shop) + "/products/" + productId), shop.token, """
            {"nameTh":"ชาเขียวมัทฉะ","nameEn":"Matcha","productType":"SIMPLE","categoryId":%d,"brand":"Tea House","basePrice":80.00,
             "compareAtPrice":80.00,"skus":[{"id":"%s","skuCode":"TEA-1","barcode":"8850001234568","price":80.00}]}
            """.formatted(category, skuId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nameTh").value("ชาเขียวมัทฉะ"))
            .andExpect(jsonPath("$.basePrice").value(80.0))
            .andExpect(jsonPath("$.skus[0].id").value(skuId))
            .andExpect(jsonPath("$.skus[0].barcode").value("8850001234568"))
            .andExpect(jsonPath("$.skus[0].onHand").value(7));
        var updated = catalogProducts.findById(UUID.fromString(productId)).orElseThrow();
        assertEquals("ชาเขียวมัทฉะ", updated.getNameTh());
        assertEquals(0, new BigDecimal("80.00").compareTo(updated.getBasePrice()));
        assertEquals(1, productSkus.count(), "the SIMPLE product keeps its single SKU");

        perform(post(base(shop) + "/products/" + productId + "/archive"), shop.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED")).andExpect(jsonPath("$.archivedAt").isNotEmpty());
        perform(post(base(shop) + "/products/" + productId + "/archive"), shop.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        assertEquals(1, catalogProducts.count(), "archive is a soft status change");
        // archived products leave the default list but stay reachable through the status filter
        perform(get(base(shop) + "/products"), shop.token).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        perform(get(base(shop) + "/products").param("status", "ARCHIVED"), shop.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(productId));
        perform(post(base(shop) + "/products/" + productId + "/unarchive"), shop.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        perform(get(base(shop) + "/products"), shop.token).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void searchMatchesNameSkuAndBarcodeAndFiltersByCategoryBrandAndStatus() throws Exception {
        var shop = openShop("pm-search@example.com", "0812000002", "ร้านค้นหา", "Search Shop", "1234567890124");
        var tea = activeCategory(0);
        var snack = activeCategory(1);
        var greenTea = create(shop, product("ชาเขียว", "Green Tea", tea, "Tea House", "TEA-GRN", "8850000000011", "45.00"));
        create(shop, product("ชาดำ", "Black Tea", tea, "tea house ", "TEA-BLK", "8850000000012", "40.00"));
        var chips = create(shop, product("มันฝรั่งทอด", "Potato Chips", snack, "Crunchy", "SNK-CHP", "8850000000013", "25.00"));
        create(shop, product("ขนม 100%", "Snack 100%", snack, null, "SNK_100", null, "15.00"));

        perform(get(base(shop) + "/products").param("q", "GREEN"), shop.token)
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(greenTea));
        perform(get(base(shop) + "/products").param("q", "มันฝรั่ง"), shop.token)
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(chips));
        perform(get(base(shop) + "/products").param("q", "snk-chp"), shop.token)
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(chips));
        perform(get(base(shop) + "/products").param("q", "8850000000011"), shop.token)
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(greenTea));
        perform(get(base(shop) + "/products").param("q", "tea-"), shop.token).andExpect(jsonPath("$.total").value(2));
        // LIKE wildcards in the search text are matched literally
        perform(get(base(shop) + "/products").param("q", "100%"), shop.token).andExpect(jsonPath("$.total").value(1));
        perform(get(base(shop) + "/products").param("q", "SNK_"), shop.token).andExpect(jsonPath("$.total").value(1));
        perform(get(base(shop) + "/products").param("q", "%"), shop.token).andExpect(jsonPath("$.total").value(1));

        perform(get(base(shop) + "/products").param("categoryId", String.valueOf(tea)), shop.token).andExpect(jsonPath("$.total").value(2));
        perform(get(base(shop) + "/products").param("brand", "TEA HOUSE"), shop.token).andExpect(jsonPath("$.total").value(2));
        perform(get(base(shop) + "/products").param("brand", "Crunchy").param("categoryId", String.valueOf(tea)), shop.token)
            .andExpect(jsonPath("$.total").value(0));
        perform(get(base(shop) + "/products/brands"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].name", hasItems("Crunchy")))
            .andExpect(jsonPath("$[?(@.name == 'Crunchy')].productCount", hasItem(1)));

        upload(shop, greenTea, PNG).andExpect(status().isOk());
        perform(post(base(shop) + "/products/" + greenTea + "/publish"), shop.token).andExpect(status().isOk());
        perform(post(base(shop) + "/products/" + chips + "/archive"), shop.token).andExpect(status().isOk());
        perform(get(base(shop) + "/products").param("status", "PUBLISHED"), shop.token)
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(greenTea));
        perform(get(base(shop) + "/products").param("status", "DRAFT"), shop.token).andExpect(jsonPath("$.total").value(2));
        perform(get(base(shop) + "/products").param("status", "ARCHIVED").param("q", "chips"), shop.token)
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(chips));
        perform(get(base(shop) + "/products").param("q", "chips"), shop.token).andExpect(jsonPath("$.total").value(0));
        perform(get(base(shop) + "/products").param("status", "BOGUS"), shop.token).andExpect(status().isBadRequest());
    }

    @Test
    void paginationAndSortingAreBoundedAndStable() throws Exception {
        var shop = openShop("pm-page@example.com", "0812000003", "ร้านหน้า", "Page Shop", "1234567890125");
        var category = activeCategory(0);
        var prices = List.of("30.00", "10.00", "50.00", "20.00", "40.00");
        for (var i = 0; i < prices.size(); i++) {
            create(shop, product("สินค้า " + (char) ('A' + i), "Item " + (char) ('A' + i), category, null, "ITEM-" + i, null, prices.get(i)));
        }
        perform(get(base(shop) + "/products").param("size", "2").param("page", "0").param("sort", "price_asc"), shop.token)
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].basePrice").value(10.0))
            .andExpect(jsonPath("$.items[1].basePrice").value(20.0));
        perform(get(base(shop) + "/products").param("size", "2").param("page", "2").param("sort", "price_asc"), shop.token)
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].basePrice").value(50.0));
        perform(get(base(shop) + "/products").param("sort", "price,desc"), shop.token)
            .andExpect(jsonPath("$.items[0].basePrice").value(50.0)).andExpect(jsonPath("$.items[4].basePrice").value(10.0));
        perform(get(base(shop) + "/products").param("sort", "name"), shop.token)
            .andExpect(jsonPath("$.items[0].nameTh").value("สินค้า A")).andExpect(jsonPath("$.items[4].nameTh").value("สินค้า E"));
        perform(get(base(shop) + "/products").param("sort", "name_desc"), shop.token)
            .andExpect(jsonPath("$.items[0].nameTh").value("สินค้า E"));
        perform(get(base(shop) + "/products").param("sort", "oldest"), shop.token)
            .andExpect(jsonPath("$.items[0].nameTh").value("สินค้า A"));

        // pages walked one by one return every product exactly once even when the sort key ties
        jdbc.update("UPDATE products SET created_date_time = ?", Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        var seen = new java.util.ArrayList<String>();
        for (var page = 0; page < 3; page++) {
            var body = perform(get(base(shop) + "/products").param("size", "2").param("page", String.valueOf(page)).param("sort", "newest"), shop.token)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> ids = JsonPath.read(body, "$.items[*].id");
            seen.addAll(ids);
        }
        assertEquals(5, seen.size());
        assertEquals(5, seen.stream().distinct().count());

        perform(get(base(shop) + "/products").param("size", "500"), shop.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.totalPages").value(1));
        perform(get(base(shop) + "/products").param("sort", "passwordHash"), shop.token)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SORT"));
        perform(get(base(shop) + "/products").param("sort", "price;drop table products"), shop.token)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void imagesAreStoredOnDiskAndDeletingRemovesTheFile() throws Exception {
        var shop = openShop("pm-image@example.com", "0812000004", "ร้านรูป", "Image Shop", "1234567890126");
        var productId = create(shop, product("แก้ว", "Glass", activeCategory(0), null, "GLS-1", null, "99.00"));

        // a draft saves without images, publishing needs one
        perform(post(base(shop) + "/products/" + productId + "/publish"), shop.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PUBLISH_INCOMPLETE"))
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("images")))
            .andExpect(jsonPath("$.fieldErrors[*].code", hasItem("PUBLISH_IMAGE_REQUIRED")));

        upload(shop, productId, GIF).andExpect(status().isBadRequest());
        upload(shop, productId, "not an image".getBytes()).andExpect(status().isBadRequest());
        assertEquals(0, productImages.count());

        String first = JsonPath.read(upload(shop, productId, PNG).andExpect(status().isOk())
            .andExpect(jsonPath("$.images.length()").value(1))
            .andExpect(jsonPath("$.images[0].primary").value(true))
            .andExpect(jsonPath("$.images[0].sortOrder").value(0))
            .andReturn().getResponse().getContentAsString(), "$.images[0].id");
        String second = JsonPath.read(upload(shop, productId, PNG).andExpect(status().isOk())
            .andExpect(jsonPath("$.images.length()").value(2))
            .andExpect(jsonPath("$.images[1].sortOrder").value(1))
            .andExpect(jsonPath("$.images[1].url").isNotEmpty())
            .andReturn().getResponse().getContentAsString(), "$.images[1].id");
        var stored = productImages.findByProduct_IdOrderBySortOrderAsc(UUID.fromString(productId));
        assertEquals(2, stored.size());
        var secondPath = Path.of(stored.get(1).getStoragePath());
        assertTrue(Files.exists(secondPath), "uploaded file is written to storage");

        perform(put(base(shop) + "/products/" + productId + "/images/order"), shop.token,
            "{\"imageIds\":[\"%s\",\"%s\"],\"primaryImageId\":\"%s\"}".formatted(second, first, second))
            .andExpect(status().isOk()).andExpect(jsonPath("$.images[0].id").value(second)).andExpect(jsonPath("$.images[0].primary").value(true));

        perform(delete(base(shop) + "/products/" + productId + "/images/" + second), shop.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.images.length()").value(1));
        assertFalse(Files.exists(secondPath), "deleting the image removes the file");
        assertEquals(1, productImages.count());

        perform(post(base(shop) + "/products/" + productId + "/publish"), shop.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        // a published product cannot lose its last image
        perform(delete(base(shop) + "/products/" + productId + "/images/" + first), shop.token)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PUBLISHED_IMAGE_REQUIRED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("images"));
        assertEquals(1, productImages.count());
        perform(delete(base(shop) + "/products/" + productId + "/images/" + UUID.randomUUID()), shop.token)
            .andExpect(status().isNotFound());
    }

    @Test
    void invalidDataIsRejectedWithFieldErrorsAndNothingIsSaved() throws Exception {
        var shop = openShop("pm-valid@example.com", "0812000005", "ร้านตรวจ", "Valid Shop", "1234567890127");
        var category = activeCategory(0);
        perform(post(base(shop) + "/products"), shop.token, """
            {"nameTh":"  ","productType":"SIMPLE","categoryId":%d,"basePrice":0,"compareAtPrice":-1,
             "skus":[{"skuCode":"","barcode":"88 50","price":0,"initialStock":-1}]}
            """.formatted(category))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItems("nameTh", "basePrice", "compareAtPrice", "skus[0].skuCode",
                "skus[0].barcode", "skus[0].price", "skus[0].initialStock")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'nameTh')].code", hasItem("NAME_REQUIRED")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus[0].price')].code", hasItem("PRICE_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus[0].initialStock')].code", hasItem("STOCK_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'skus[0].barcode')].code", hasItem("BARCODE_INVALID")));
        assertEquals(0, catalogProducts.count());

        perform(post(base(shop) + "/products"), shop.token, """
            {"nameTh":"ชา","productType":"SIMPLE","categoryId":%d,"basePrice":50,"compareAtPrice":49.99,
             "skus":[{"skuCode":"TEA-1","price":50}]}
            """.formatted(category))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("compareAtPrice"))
            .andExpect(jsonPath("$.fieldErrors[0].code").value("COMPARE_PRICE_INVALID"));
        perform(post(base(shop) + "/products"), shop.token, """
            {"nameTh":"ชา","productType":"SIMPLE","categoryId":999999,"skus":[{"skuCode":"TEA-1","price":50}]}
            """)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("UNKNOWN_CATEGORY"));
        var inactive = categories.save(new ProductCategory("pm-test-closed", "หมวดปิด", "Closed", "关闭", 999));
        jdbc.update("UPDATE hub_categories SET is_active = FALSE WHERE id = ?", inactive.getUuid());
        Long inactiveNo = jdbc.queryForObject("SELECT category_no FROM hub_categories WHERE id = ?", Long.class, inactive.getUuid());
        perform(post(base(shop) + "/products"), shop.token, """
            {"nameTh":"ชา","productType":"SIMPLE","categoryId":%d,"skus":[{"skuCode":"TEA-1","price":50}]}
            """.formatted(inactiveNo))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("CATEGORY_INACTIVE"));
        perform(post(base(shop) + "/products"), shop.token, """
            {"nameTh":"ชุด","productType":"VARIABLE","categoryId":%d,"skus":[
              {"skuCode":"SET-1","barcode":"111","price":10},{"skuCode":"SET-1","barcode":"111","price":10}]}
            """.formatted(category))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].code", hasItems("SKU_CODE_DUPLICATE_IN_FORM", "BARCODE_DUPLICATE")));
        assertEquals(0, catalogProducts.count());

        create(shop, product("ชาเขียว", "Green Tea", category, null, "TEA-1", "8850001", "50.00"));
        perform(post(base(shop) + "/products"), shop.token, product("ชาดำ", "Black Tea", category, null, "TEA-1", "8850001", "40.00"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("skus[0].skuCode", "skus[0].barcode")))
            .andExpect(jsonPath("$.fieldErrors[*].code", containsInAnyOrder("SKU_CODE_TAKEN", "BARCODE_TAKEN")));
        assertEquals(1, catalogProducts.count());

        // the same SKU code and barcode are fine in another shop
        var other = openShop("pm-valid-other@example.com", "0812000006", "ร้านอื่น", "Other Valid", "1234567890128");
        create(other, product("ชาเขียว", "Green Tea", category, null, "TEA-1", "8850001", "50.00"));
    }

    @Test
    void permissionsAndShopIsolationAreEnforced() throws Exception {
        var shop = openShop("pm-owner@example.com", "0812000007", "ร้านเจ้าของ", "Owner Shop", "1234567890129");
        var other = openShop("pm-rival@example.com", "0812000008", "ร้านคู่แข่ง", "Rival Shop", "1234567890130");
        var category = activeCategory(0);
        var productId = create(shop, product("ชาเขียว", "Green Tea", category, null, "TEA-1", null, "50.00"));
        String imageId = JsonPath.read(upload(shop, productId, PNG).andReturn().getResponse().getContentAsString(), "$.images[0].id");

        // a STORE_VIEWER member can read but not write
        var viewerToken = token("pm-viewer@example.com", "0812000009");
        var viewer = users.findByEmailIgnoreCaseOrPhone("pm-viewer@example.com", "0812000009").orElseThrow();
        addMember(shop.shopId, viewer.getId(), "STORE_VIEWER");
        perform(get(base(shop) + "/products"), viewerToken).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        perform(get(base(shop) + "/products/" + productId), viewerToken).andExpect(status().isOk());
        perform(post(base(shop) + "/products"), viewerToken, product("ชาดำ", "Black Tea", category, null, "TEA-2", null, "40.00"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        perform(put(base(shop) + "/products/" + productId), viewerToken, product("ชาดำ", "Black Tea", category, null, "TEA-1", null, "40.00"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        perform(post(base(shop) + "/products/" + productId + "/archive"), viewerToken)
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        upload(viewerToken, shop.shopId, productId, PNG)
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        perform(delete(base(shop) + "/products/" + productId + "/images/" + imageId), viewerToken)
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertEquals(1, catalogProducts.count());
        assertEquals(1, productImages.count());

        // a STORE_MANAGER member works with the shop's products through the permission matrix
        var managerToken = token("pm-manager@example.com", "0812000010");
        addMember(shop.shopId, users.findByEmailIgnoreCaseOrPhone("pm-manager@example.com", "0812000010").orElseThrow().getId(), "STORE_MANAGER");
        create(managerToken, shop.shopId, product("ชาดำ", "Black Tea", category, null, "TEA-2", null, "40.00"));
        assertEquals(2, catalogProducts.count());

        // another shop's owner cannot see or touch the product: 404 either way
        perform(get(base(shop) + "/products"), other.token).andExpect(status().isNotFound());
        perform(get(base(shop) + "/products/" + productId), other.token).andExpect(status().isNotFound());
        perform(get(base(other) + "/products/" + productId), other.token)
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        perform(put(base(other) + "/products/" + productId), other.token, product("ขโมย", "Stolen", category, null, "X-1", null, "1.00"))
            .andExpect(status().isNotFound());
        perform(post(base(other) + "/products/" + productId + "/archive"), other.token).andExpect(status().isNotFound());
        upload(other.token, other.shopId, productId, PNG).andExpect(status().isNotFound());
        perform(delete(base(other) + "/products/" + productId + "/images/" + imageId), other.token).andExpect(status().isNotFound());
        perform(get(base(other) + "/products").param("q", "green"), other.token).andExpect(jsonPath("$.total").value(0));
        assertEquals("ชาเขียว", catalogProducts.findById(UUID.fromString(productId)).orElseThrow().getNameTh());
        assertEquals(1, productImages.count());

        mvc.perform(get(base(shop) + "/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicMarketplaceSearchReturnsOnlyApprovedShopsAndPublishedProducts() throws Exception {
        var shop = openShop("pm-global@example.com", "0812000009", "ร้านค้นทั่วไป", "Global Finder Shop", "1234567890129");
        var tea = activeCategory(0);
        var published = create(shop, product("ชาอู่หลง", "Oolong Tea", tea, "Finder Leaf", "OOL-1", "8850000000091", "80.00"));
        create(shop, product("ชาอู่หลงร่าง", "Oolong Draft", tea, "Finder Leaf", "OOL-2", null, "70.00"));
        upload(shop, published, PNG).andExpect(status().isOk());
        perform(post(base(shop) + "/products/" + published + "/publish"), shop.token).andExpect(status().isOk());

        // anonymous: no Authorization header
        mvc.perform(get("/api/search").param("q", "oolong"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products.total").value(1))
            .andExpect(jsonPath("$.products.items[0].product.id").value(published))
            .andExpect(jsonPath("$.products.items[0].shop.slug").isNotEmpty());
        mvc.perform(get("/api/search").param("q", "global finder"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shops.length()").value(1))
            .andExpect(jsonPath("$.shops[0].productCount").value(1))
            .andExpect(jsonPath("$.shops[0].phone").doesNotExist())
            .andExpect(jsonPath("$.shops[0].email").doesNotExist());
        mvc.perform(get("/api/search").param("q", "finder le"))
            .andExpect(jsonPath("$.brands[0].name").value("Finder Leaf"))
            .andExpect(jsonPath("$.brands[0].productCount").value(1));
        mvc.perform(get("/api/search").param("q", "oolong").param("brand", "FINDER LEAF"))
            .andExpect(jsonPath("$.products.total").value(1))
            .andExpect(jsonPath("$.shops.length()").value(0));
        // LIKE wildcards are literal and a blank query returns nothing
        mvc.perform(get("/api/search").param("q", "%")).andExpect(jsonPath("$.products.total").value(0));
        mvc.perform(get("/api/search").param("q", "  ")).andExpect(status().isOk()).andExpect(jsonPath("$.products.total").value(0));

        // a suspended shop disappears from both shops and products
        jdbc.update("UPDATE shops SET status = 'SUSPENDED' WHERE shop_no = ?", shop.shopId);
        mvc.perform(get("/api/search").param("q", "oolong")).andExpect(jsonPath("$.products.total").value(0));
        mvc.perform(get("/api/search").param("q", "global finder")).andExpect(jsonPath("$.shops.length()").value(0));
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
            var approver = users.save(UserAccount.systemAccount("pm-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"pm-approver@example.com","password":"Password1"}
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
