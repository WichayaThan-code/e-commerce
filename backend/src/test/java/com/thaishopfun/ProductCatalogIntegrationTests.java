package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.catalog.InventoryLedgerRepository;
import com.thaishopfun.catalog.ProductImportJobService;
import com.thaishopfun.catalog.ProductSpreadsheet;
import com.thaishopfun.catalog.InventoryLevelRepository;
import com.thaishopfun.catalog.ProductImageRepository;
import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.catalog.ProductSkuRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import com.thaishopfun.merchant.OwnerAuditLogRepository;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ShopMemberRepository;
import com.thaishopfun.merchant.ShopReinstatementRequestRepository;
import com.thaishopfun.merchant.ShopRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductCatalogIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    @Autowired MockMvc mvc;
    @Autowired InventoryLedgerRepository ledger;
    @Autowired InventoryLevelRepository levels;
    @Autowired ProductImageRepository productImages;
    @Autowired ProductSkuRepository productSkus;
    @Autowired ProductRepository catalogProducts;
    @Autowired ShopReinstatementRequestRepository reinstatements;
    @Autowired ShopMemberRepository shopMembers;
    @Autowired ShopRepository shops;
    @Autowired MerchantDocumentRepository documents;
    @Autowired MerchantApplicationRepository applications;
    @Autowired OwnerAuditLogRepository ownerAudits;
    @Autowired OwnerRepository owners;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired UserAccountRepository users;
    @Autowired PlatformRoleRepository platformRoles;
    @Autowired PasswordEncoder passwords;
    @Autowired ProductImportJobService importJobs;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        ledger.deleteAll();
        levels.deleteAll();
        productSkus.deleteAll();
        productImages.deleteAll();
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

    @Test
    void hidesOtherShopsAndBlocksUnapprovedOwners() throws Exception {
        var pending = openShop("pending-seller@example.com", "0811111111", "ร้านรอ", "Pending Goods", false);
        var stranger = token("stranger@example.com", "0822222222");
        mvc.perform(get("/api/merchant/shops/" + pending.shopId + "/products").header("Authorization", "Bearer " + stranger))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));
        mvc.perform(post("/api/merchant/shops/" + pending.shopId + "/products").header("Authorization", "Bearer " + pending.token)
            .contentType(MediaType.APPLICATION_JSON).content(simpleProduct("ชา", "Tea", "TEA-1", "40.00")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SHOP_NOT_APPROVED"));
        mvc.perform(get("/api/shops/" + pending.slug)).andExpect(status().isNotFound());
        mvc.perform(get("/api/shops/" + pending.slug + "/products")).andExpect(status().isNotFound());

        var catalogAdmin = users.save(UserAccount.systemAccount("catalog-admin@example.com", passwords.encode("Password1")));
        platformRoles.save(PlatformRole.grant(catalogAdmin.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
        var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"identity":"catalog-admin@example.com","password":"Password1"}
            """)).andExpect(status().isOk()).andReturn();
        String admin = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/shops/" + pending.slug).header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        mvc.perform(get("/api/merchant/shops/999999/products").header("Authorization", "Bearer " + pending.token))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));
    }

    @Test
    void archiveHidesTheProductAndPublicListShowsOnlyPublishedGoods() throws Exception {
        var shop = openShop("catalog-seller@example.com", "0833333333", "ร้านแคตตาล็อก", "Catalog Shop", true);
        var created = mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content(simpleProduct("ชาเขียว", "Green Tea", "TEA-1", "50.00")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.slug").value("green-tea"))
            .andReturn();
        String productId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/shops/" + shop.slug + "/products")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/publish").header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUBLISH_INCOMPLETE"));

        var file = new MockMultipartFile("file", "tea.png", "image/png", PNG);
        var uploaded = mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/images").file(file).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.images.length()").value(1)).andReturn();
        String imageId = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.images[0].id");
        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/publish").header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/shops/" + shop.slug + "/products").param("q", "green"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].slug").value("green-tea"));
        mvc.perform(get("/api/shops/" + shop.slug + "/products").param("priceBands", "under100"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/shops/" + shop.slug + "/products").param("priceBands", "b100_300"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/shops/" + shop.slug + "/products/green-tea")).andExpect(status().isOk()).andExpect(jsonPath("$.nameTh").value("ชาเขียว"));
        mvc.perform(get("/api/shops/" + shop.slug + "/products/" + productId + "/images/" + imageId))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));

        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/archive").header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/shops/" + shop.slug + "/products")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/shops/" + shop.slug + "/products/green-tea")).andExpect(status().isNotFound());
        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/unarchive").header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(get("/api/shops/" + shop.slug + "/products")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        org.junit.jupiter.api.Assertions.assertEquals(1, catalogProducts.count());
    }

    @Test
    void stockAdjustmentsWriteTheLedgerAndRejectConflicts() throws Exception {
        var shop = openShop("stock-seller@example.com", "0844444444", "ร้านสต็อก", "Stock Shop", true);
        var created = mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content(simpleProduct("ขนม", "Snack", "SNK-1", "20.00")))
            .andExpect(status().isOk()).andReturn();
        String skuId = JsonPath.read(created.getResponse().getContentAsString(), "$.skus[0].id");
        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/skus/" + skuId + "/stock-adjustments").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"entryType\":\"RECEIVE\",\"quantityDelta\":5,\"version\":0,\"idempotencyKey\":\"recv-1\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.onHand").value(5)).andExpect(jsonPath("$.available").value(5)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/skus/" + skuId + "/stock-adjustments").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"entryType\":\"RECEIVE\",\"quantityDelta\":5,\"version\":0,\"idempotencyKey\":\"recv-1\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.onHand").value(5)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/skus/" + skuId + "/stock-adjustments").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"entryType\":\"ADJUST\",\"quantityDelta\":-2,\"version\":1}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STOCK_REASON_REQUIRED"));
        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/skus/" + skuId + "/stock-adjustments").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"entryType\":\"ADJUST\",\"quantityDelta\":-2,\"reason\":\"นับสต็อก\",\"version\":0}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STOCK_CONFLICT"));
        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/skus/" + skuId + "/stock-adjustments").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"entryType\":\"ADJUST\",\"quantityDelta\":-2,\"reason\":\"นับสต็อก\",\"version\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.onHand").value(3)).andExpect(jsonPath("$.version").value(2));
        org.junit.jupiter.api.Assertions.assertEquals(2, ledger.count());
    }

    @Test
    void variantsKeepOpeningStockAndRejectUnsafeSkuChanges() throws Exception {
        var shop = openShop("variant-seller@example.com", "0877777777", "ร้านตัวเลือก", "Variant Shop", true);
        var created = mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content(variantProduct("เสื้อ", matrix(null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productType").value("VARIABLE"))
            .andExpect(jsonPath("$.optionSchema[0].name").value("สี"))
            .andExpect(jsonPath("$.skus.length()").value(6))
            .andReturn();
        String body = created.getResponse().getContentAsString();
        String productId = JsonPath.read(body, "$.id");
        org.junit.jupiter.api.Assertions.assertEquals(4, onHand(body, "RED-S"));
        org.junit.jupiter.api.Assertions.assertEquals(9, onHand(body, "BLUE-L"));

        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/stock-adjustments/bulk")
            .header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"idempotencyKey":"bulk-rollback","items":[
                  {"skuId":"%s","entryType":"RECEIVE","quantityDelta":1,"version":1,"reason":"เพิ่ม"},
                  {"skuId":"%s","entryType":"RECEIVE","quantityDelta":1,"version":99,"reason":"เพิ่ม"}
                ]}
                """.formatted(skuId(body, "RED-S"), skuId(body, "RED-M"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STOCK_CONFLICT"));
        var afterBulk = mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(4, onHand(afterBulk, "RED-S"));
        org.junit.jupiter.api.Assertions.assertEquals(5, onHand(afterBulk, "RED-M"));

        var added = mvc.perform(put("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content(variantProduct("เสื้อ", matrix(body) + "," + sku(null, "RED-XL", "แดง", "XL", 8))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skus.length()").value(7))
            .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(8, onHand(added, "RED-XL"));
        org.junit.jupiter.api.Assertions.assertEquals(4, onHand(added, "RED-S"));

        mvc.perform(put("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content(variantProduct("เสื้อ", sku(skuId(added, "RED-S"), "RED-S", "แดง", "S", null))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SKU_HAS_STOCK"));

        var removed = mvc.perform(put("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content(variantProduct("เสื้อ", sku(skuId(added, "RED-S"), "RED-S", "แดง", "S", null), true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skus.length()").value(1))
            .andReturn().getResponse().getContentAsString();

        var reused = mvc.perform(put("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content(variantProduct("เสื้อ",
                sku(skuId(removed, "RED-S"), "RED-S", "แดง", "S", null) + "," + sku(null, "RED-M", "แดง", "M", 3), true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skus.length()").value(2))
            .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(3, onHand(reused, "RED-M"));

        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"nameTh":"ซ้ำ","productType":"VARIABLE","optionSchema":[{"name":"สี","values":["แดง","น้ำเงิน"]}],
                 "skus":[
                   {"skuCode":"DUP-1","price":10,"optionValues":{"สี":"แดง"}},
                   {"skuCode":"DUP-1","price":12,"optionValues":{"สี":"น้ำเงิน"}}
                 ]}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SKU_CODE_DUPLICATE_IN_FORM"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("skus[1].skuCode"))
            .andExpect(jsonPath("$.fieldErrors[0].code").value("SKU_CODE_DUPLICATE_IN_FORM"));

        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content(simpleProduct("เสื้อสอง", "Shirt Two", "RED-S", "40.00")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SKU_CODE_TAKEN"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("RED-S")));

        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/stock-adjustments/bulk")
            .header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"idempotencyKey":"bulk-ok","items":[
                  {"skuId":"%s","entryType":"RECEIVE","quantityDelta":1,"version":1,"reason":"เพิ่ม"},
                  {"skuId":"%s","entryType":"RECEIVE","quantityDelta":2,"version":1,"reason":"เพิ่ม"}
                ]}
                """.formatted(skuId(reused, "RED-S"), skuId(reused, "RED-M"))))
            .andExpect(status().isOk());
        var afterOk = mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(5, onHand(afterOk, "RED-S"));
        org.junit.jupiter.api.Assertions.assertEquals(5, onHand(afterOk, "RED-M"));
    }

    @Test
    void createdVariableProductKeepsSchemaAndBasePrice() throws Exception {
        var shop = openShop("variant-read@example.com", "0877001001", "ร้านอ่านตัวเลือก", "Variant Read", true);
        var created = mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"nameTh":"เสื้อยืด","productType":"VARIABLE",
                 "optionSchema":[{"name":"ไซซ์","values":["S","M","L"]}],
                 "skus":[
                   {"skuCode":"TEE-S","price":80,"optionValues":{"ไซซ์":"S"}},
                   {"skuCode":"TEE-M","price":90,"optionValues":{"ไซซ์":"M"}},
                   {"skuCode":"TEE-L","price":100,"optionValues":{"ไซซ์":"L"}}
                 ]}
                """))
            .andExpect(status().isOk()).andReturn();
        String productId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.optionSchema[0].name").value("ไซซ์"))
            .andExpect(jsonPath("$.optionSchema[0].values[0]").value("S"))
            .andExpect(jsonPath("$.optionSchema[0].values[1]").value("M"))
            .andExpect(jsonPath("$.optionSchema[0].values[2]").value("L"))
            .andExpect(jsonPath("$.basePrice").value(80.00))
            .andExpect(jsonPath("$.skus[?(@.skuCode=='TEE-S')].optionValues.ไซซ์").value("S"));
    }

    @Test
    void swapsSkuCodesAndReusesARenamedCodeInOneRequest() throws Exception {
        var shop = openShop("variant-swap@example.com", "0877001002", "ร้านสลับรหัส", "Variant Swap", true);
        var created = mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"nameTh":"สลับ","productType":"VARIABLE",
                 "optionSchema":[{"name":"สี","values":["แดง","น้ำเงิน","เขียว"]}],
                 "skus":[
                   {"skuCode":"AAA","price":10,"optionValues":{"สี":"แดง"}},
                   {"skuCode":"BBB","price":10,"optionValues":{"สี":"น้ำเงิน"}}
                 ]}
                """))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String productId = JsonPath.read(created, "$.id");
        String redId = skuId(created, "AAA");
        String blueId = skuId(created, "BBB");
        var swapped = mvc.perform(put("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"nameTh":"สลับ","productType":"VARIABLE",
                 "optionSchema":[{"name":"สี","values":["แดง","น้ำเงิน","เขียว"]}],
                 "skus":[
                   {"id":"%s","skuCode":"BBB","price":10,"optionValues":{"สี":"แดง"}},
                   {"id":"%s","skuCode":"AAA","price":10,"optionValues":{"สี":"น้ำเงิน"}}
                 ]}
                """.formatted(redId, blueId)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(redId, skuId(swapped, "BBB"));
        org.junit.jupiter.api.Assertions.assertEquals(blueId, skuId(swapped, "AAA"));

        var reused = mvc.perform(put("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"nameTh":"สลับ","productType":"VARIABLE",
                 "optionSchema":[{"name":"สี","values":["แดง","น้ำเงิน","เขียว"]}],
                 "skus":[
                   {"id":"%s","skuCode":"BBB","price":10,"optionValues":{"สี":"แดง"}},
                   {"id":"%s","skuCode":"CCC","price":10,"optionValues":{"สี":"น้ำเงิน"}},
                   {"skuCode":"AAA","price":10,"optionValues":{"สี":"เขียว"},"initialStock":2}
                 ]}
                """.formatted(skuId(swapped, "BBB"), skuId(swapped, "AAA"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fieldErrors").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(blueId, skuId(reused, "CCC"));
        org.junit.jupiter.api.Assertions.assertEquals(2, onHand(reused, "AAA"));
    }

    @Test
    void removingAStockedSkuWritesAZeroingLedgerEntry() throws Exception {
        var shop = openShop("variant-remove@example.com", "0877001003", "ร้านลบสต็อก", "Variant Remove", true);
        var created = mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"nameTh":"ลบสต็อก","productType":"VARIABLE",
                 "optionSchema":[{"name":"สี","values":["แดง","น้ำเงิน"]}],
                 "skus":[
                   {"skuCode":"KEEP","price":10,"optionValues":{"สี":"แดง"},"initialStock":1},
                   {"skuCode":"DROP","price":10,"optionValues":{"สี":"น้ำเงิน"},"initialStock":6}
                 ]}
                """))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String productId = JsonPath.read(created, "$.id");
        String dropId = skuId(created, "DROP");
        mvc.perform(put("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"nameTh":"ลบสต็อก","productType":"VARIABLE","confirmRemoveStocked":true,
                 "optionSchema":[{"name":"สี","values":["แดง","น้ำเงิน"]}],
                 "skus":[{"id":"%s","skuCode":"KEEP","price":10,"optionValues":{"สี":"แดง"}}]}
                """.formatted(skuId(created, "KEEP"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skus.length()").value(1));
        var level = levels.findBySku_Id(java.util.UUID.fromString(dropId)).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, level.getOnHand());
        var entry = ledger.findByIdempotencyKey("variant-removed-" + dropId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("VARIANT_REMOVED", entry.getReason());
        org.junit.jupiter.api.Assertions.assertEquals("ADJUST", entry.getEntryType());
        org.junit.jupiter.api.Assertions.assertEquals(-6, entry.getQuantityDelta());
        org.junit.jupiter.api.Assertions.assertEquals(0, entry.getOnHandAfter());
    }

    private static String variantProduct(String name, String skus) {
        return variantProduct(name, skus, false);
    }

    private static String variantProduct(String name, String skus, boolean confirm) {
        return """
            {"nameTh":"%s","productType":"VARIABLE","confirmRemoveStocked":%s,
             "optionSchema":[{"name":"สี","values":["แดง","น้ำเงิน"]},{"name":"ไซซ์","values":["S","M","L","XL"]}],
             "skus":[%s]}
            """.formatted(name, confirm, skus);
    }

    private static String matrix(String existing) {
        var rows = new StringBuilder();
        var index = 0;
        for (var color : new String[][] {{"แดง", "RED"}, {"น้ำเงิน", "BLUE"}}) {
            for (var size : new String[] {"S", "M", "L"}) {
                if (rows.length() > 0) rows.append(',');
                String code = color[1] + "-" + size;
                String id = existing == null ? null : skuId(existing, code);
                rows.append(sku(id, code, color[0], size, 4 + index));
                index++;
            }
        }
        return rows.toString();
    }

    private static String sku(String id, String code, String color, String size, Integer stock) {
        var identity = id == null ? "" : "\"id\":\"" + id + "\",";
        var opening = stock == null ? "" : ",\"initialStock\":" + stock;
        return "{\"skuCode\":\"" + code + "\"," + identity + "\"price\":100,\"optionLabel\":\"" + color + " / " + size
            + "\",\"optionValues\":{\"สี\":\"" + color + "\",\"ไซซ์\":\"" + size + "\"}" + opening + "}";
    }

    private static String skuId(String json, String code) {
        java.util.List<String> ids = JsonPath.read(json, "$.skus[?(@.skuCode=='" + code + "')].id");
        return ids.get(0);
    }

    private static int onHand(String json, String code) {
        java.util.List<Integer> values = JsonPath.read(json, "$.skus[?(@.skuCode=='" + code + "')].onHand");
        return values.get(0);
    }

    @Test
    void recordsChangingRequestsInTheAuditLog() throws Exception {
        var shop = openShop("audit-seller@example.com", "0877777773", "ร้านบันทึก", "Audit Shop", true, "5555555555555");
        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content(simpleProduct("ชาบันทึก", "Audit tea", "AUD-1", "40.00")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk());
        Integer posts = jdbc.queryForObject("select count(*) from audit_logs where action like 'POST %/products' and outcome = 'SUCCESS'", Integer.class);
        Integer reads = jdbc.queryForObject("select count(*) from audit_logs where action like 'GET %' and outcome = 'SUCCESS'", Integer.class);
        org.junit.jupiter.api.Assertions.assertTrue(posts != null && posts >= 1);
        org.junit.jupiter.api.Assertions.assertEquals(0, reads);
    }

    @Test
    void importsGroupedVariableProductsAndKeepsTheFollowingRows() throws Exception {
        var shop = openShop("variant-import@example.com", "0877777771", "ร้านตัวเลือก", "Variant Shop", true, "3333333333333");
        var file = new MockMultipartFile("file", "products.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", variantWorkbook());
        var accepted = mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/products/import").file(file).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.total").value(5))
            .andReturn();
        String jobId = JsonPath.read(accepted.getResponse().getContentAsString(), "$.jobId");
        var body = awaitImport(shop, jobId);
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", JsonPath.read(body, "$.status"));
        org.junit.jupiter.api.Assertions.assertEquals(5, ((Number) JsonPath.read(body, "$.processed")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals(2, ((Number) JsonPath.read(body, "$.created")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals(3, ((Number) JsonPath.read(body, "$.skuCreated")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals(1, ((Number) JsonPath.read(body, "$.failed")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals(5, ((Number) JsonPath.read(body, "$.errors[0].row")).intValue());
        org.junit.jupiter.api.Assertions.assertTrue(JsonPath.read(body, "$.errors[0].message").toString().contains("VARIANT_OPTION_REQUIRED"), body);

        var listed = mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        java.util.List<String> rejected = JsonPath.read(listed, "$.items[?(@.nameTh=='เสื้อเสีย')].id");
        org.junit.jupiter.api.Assertions.assertTrue(rejected.isEmpty(), listed);
        String variableId = ((java.util.List<String>) JsonPath.read(listed, "$.items[?(@.nameTh=='เสื้อสก็อต')].id")).get(0);
        String legacyId = ((java.util.List<String>) JsonPath.read(listed, "$.items[?(@.nameTh=='ชาไทย')].id")).get(0);
        var variable = mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products/" + variableId).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productType").value("VARIABLE"))
            .andExpect(jsonPath("$.optionSchema[0].name").value("สี"))
            .andExpect(jsonPath("$.optionSchema[0].values[0]").value("แดง"))
            .andExpect(jsonPath("$.optionSchema[0].values[1]").value("ดำ"))
            .andExpect(jsonPath("$.optionSchema[1].name").value("ไซซ์"))
            .andExpect(jsonPath("$.optionSchema[1].values[0]").value("S"))
            .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(4, onHand(variable, "RED-S"));
        org.junit.jupiter.api.Assertions.assertEquals(5, onHand(variable, "BLK-S"));
        String redImage = ((java.util.List<String>) JsonPath.read(variable, "$.skus[?(@.skuCode=='RED-S')].imageId")).get(0);
        String blackImage = ((java.util.List<String>) JsonPath.read(variable, "$.skus[?(@.skuCode=='BLK-S')].imageId")).get(0);
        org.junit.jupiter.api.Assertions.assertNotNull(redImage);
        org.junit.jupiter.api.Assertions.assertNotNull(blackImage);
        org.junit.jupiter.api.Assertions.assertNotEquals(redImage, blackImage);
        mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products/" + legacyId).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productType").value("SIMPLE"))
            .andExpect(jsonPath("$.skus.length()").value(1))
            .andExpect(jsonPath("$.skus[0].skuCode").value("LEGACY-1"))
            .andExpect(jsonPath("$.skus[0].onHand").value(2));
    }

    @Test
    void importsALegacySixteenColumnFileAsSimpleProducts() throws Exception {
        var shop = openShop("legacy-import@example.com", "0877777772", "ร้านเดิม", "Legacy Shop", true, "4444444444444");
        byte[] bytes;
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("products");
            var header = sheet.createRow(0);
            for (var index = 0; index < 16; index++) header.createCell(index).setCellValue(ProductSpreadsheet.HEADERS[index]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("ชาไทย");
            row.createCell(8).setCellValue("OLD-1");
            row.createCell(11).setCellValue(45);
            row.createCell(14).setCellValue(3);
            book.write(out);
            bytes = out.toByteArray();
        }
        var file = new MockMultipartFile("file", "products.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        var accepted = mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/products/import").file(file).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isAccepted())
            .andReturn();
        String jobId = JsonPath.read(accepted.getResponse().getContentAsString(), "$.jobId");
        var body = awaitImport(shop, jobId);
        org.junit.jupiter.api.Assertions.assertEquals(1, ((Number) JsonPath.read(body, "$.processed")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals(1, ((Number) JsonPath.read(body, "$.created")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals(1, ((Number) JsonPath.read(body, "$.skuCreated")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals(0, ((Number) JsonPath.read(body, "$.failed")).intValue());
        var listed = mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String productId = ((java.util.List<String>) JsonPath.read(listed, "$.items[?(@.skuCode=='OLD-1' || @.nameTh=='ชาไทย')].id")).get(0);
        mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productType").value("SIMPLE"))
            .andExpect(jsonPath("$.skus[0].skuCode").value("OLD-1"))
            .andExpect(jsonPath("$.skus[0].onHand").value(3));
    }

    private String awaitImport(OpenShop shop, String jobId) throws Exception {
        String body = "";
        for (var attempt = 0; attempt < 50; attempt++) {
            body = mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products/import/" + jobId).header("Authorization", "Bearer " + shop.token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            String status = JsonPath.read(body, "$.status");
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) return body;
            Thread.sleep(100);
        }
        return body;
    }

    private static byte[] variantWorkbook() throws Exception {
        var red = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        var black = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=");
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("products");
            var header = sheet.createRow(0);
            for (var index = 0; index < ProductSpreadsheet.HEADERS.length; index++) header.createCell(index).setCellValue(ProductSpreadsheet.HEADERS[index]);
            variantRow(sheet.createRow(1), "เสื้อสก็อต", "ลายสก็อต", "PLAID", "RED-S", 390, 4, "สี", "แดง", "ไซซ์", "S");
            variantRow(sheet.createRow(2), "เสื้อสก็อต", null, "PLAID", "BLK-S", 420, 5, "สี", "ดำ", "ไซซ์", "S");
            variantRow(sheet.createRow(3), "เสื้อเสีย", null, "BAD", "BAD-1", 100, 1, "สี", "แดง", "ไซซ์", "S");
            variantRow(sheet.createRow(4), "เสื้อเสีย", null, "BAD", "BAD-2", 100, 1, "สี", "ดำ", "ไซซ์", null);
            var legacy = sheet.createRow(5);
            legacy.createCell(0).setCellValue("ชาไทย");
            legacy.createCell(8).setCellValue("LEGACY-1");
            legacy.createCell(11).setCellValue(45);
            legacy.createCell(14).setCellValue(2);
            var drawing = sheet.createDrawingPatriarch();
            drawing.createPicture(new XSSFClientAnchor(0, 0, 0, 0, 15, 1, 16, 2), book.addPicture(red, Workbook.PICTURE_TYPE_PNG));
            drawing.createPicture(new XSSFClientAnchor(0, 0, 0, 0, 15, 2, 16, 3), book.addPicture(black, Workbook.PICTURE_TYPE_PNG));
            book.write(out);
            return out.toByteArray();
        }
    }

    private static void variantRow(org.apache.poi.ss.usermodel.Row row, String name, String description, String key, String sku, double price, int stock, String option1Name, String option1Value, String option2Name, String option2Value) {
        row.createCell(0).setCellValue(name);
        if (description != null) row.createCell(3).setCellValue(description);
        row.createCell(8).setCellValue(sku);
        row.createCell(11).setCellValue(price);
        row.createCell(14).setCellValue(stock);
        row.createCell(16).setCellValue(key);
        row.createCell(17).setCellValue("VARIABLE");
        row.createCell(18).setCellValue(option1Name);
        row.createCell(19).setCellValue(option1Value);
        row.createCell(20).setCellValue(option2Name);
        if (option2Value != null) row.createCell(21).setCellValue(option2Value);
    }

    @Test
    void importJobReportsProgressAndRejectsASecondRun() throws Exception {
        var shop = openShop("import-seller@example.com", "0855555555", "ร้านนำเข้า", "Import Shop", true);
        var other = openShop("import-other@example.com", "0866666666", "ร้านอื่น", "Other Shop", false, "9876543210987");
        var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"identity":"approver-0855555555@example.com","password":"Password1"}
            """)).andExpect(status().isOk()).andReturn();
        String admin = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(post("/api/admin/store-applications/" + other.shopId + "/approve").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        var rows = List.of(
            new ProductSpreadsheet.SheetRow(2, "ชาไทย", null, null, null, null, null, null, null, "IMP-1", null, null, new BigDecimal("45.00"), null, null, 1, null, List.of(), null, null, null, null, null, null, null, null, null),
            new ProductSpreadsheet.SheetRow(3, "ขนม", null, null, null, null, null, null, null, "IMP-2", null, null, null, null, null, null, null, List.of(), null, null, null, null, null, null, null, null, null)
        );
        var file = new MockMultipartFile("file", "products.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ProductSpreadsheet.write(rows));
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        importJobs.armForTest(started, release);
        try {
            var accepted = mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/products/import").file(file).header("Authorization", "Bearer " + shop.token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.total").value(2))
                .andReturn();
            String jobId = JsonPath.read(accepted.getResponse().getContentAsString(), "$.jobId");
            org.junit.jupiter.api.Assertions.assertTrue(started.await(10, TimeUnit.SECONDS));
            mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/products/import").file(file).header("Authorization", "Bearer " + shop.token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_RUNNING"))
                .andExpect(jsonPath("$.jobId").value(jobId));
            mvc.perform(get("/api/merchant/shops/" + other.shopId + "/products/import/" + jobId).header("Authorization", "Bearer " + other.token))
                .andExpect(status().isNotFound());
            release.countDown();
            String body = "";
            var completed = false;
            for (var attempt = 0; attempt < 50; attempt++) {
                var polled = mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products/import/" + jobId).header("Authorization", "Bearer " + shop.token))
                    .andExpect(status().isOk())
                    .andReturn();
                body = polled.getResponse().getContentAsString();
                if ("COMPLETED".equals(JsonPath.read(body, "$.status"))) {
                    completed = true;
                    break;
                }
                Thread.sleep(100);
            }
            org.junit.jupiter.api.Assertions.assertTrue(completed, body);
            Number processed = JsonPath.read(body, "$.processed");
            Number created = JsonPath.read(body, "$.created");
            Number failed = JsonPath.read(body, "$.failed");
            org.junit.jupiter.api.Assertions.assertEquals(2, processed.intValue());
            org.junit.jupiter.api.Assertions.assertEquals(1, created.intValue());
            org.junit.jupiter.api.Assertions.assertEquals(1, failed.intValue());
        } finally {
            release.countDown();
            importJobs.armForTest(null, null);
        }
    }

    private OpenShop openShop(String email, String phone, String shopName, String shopNameEn, boolean approve) throws Exception {
        return openShop(email, phone, shopName, shopNameEn, approve, "1234567890123");
    }

    private OpenShop openShop(String email, String phone, String shopName, String shopNameEn, boolean approve, String citizenId) throws Exception {
        var seller = token(email, phone);
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
            {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
            """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
            {"ownerType":"PERSON","title":"นาย","firstName":"สมชาย","lastName":"ขายของ","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"%s","email":"%s","houseNo":"9","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมชาย ขายของ"}
            """.formatted(citizenId, phone, email))).andExpect(status().isOk());
        upload(seller, "IDENTITY");
        upload(seller, "ADDRESS");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + seller)).andExpect(status().isOk());
        var listed = mvc.perform(get("/api/merchant/shops").header("Authorization", "Bearer " + seller)).andExpect(status().isOk()).andReturn();
        Number shopId = JsonPath.read(listed.getResponse().getContentAsString(), "$[0].shopId");
        String slug = JsonPath.read(listed.getResponse().getContentAsString(), "$[0].slug");
        if (approve) {
            // admin rights come only from platform_roles (users.role no longer exists)
            var approver = users.save(UserAccount.systemAccount("approver-" + phone + "@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"approver-%s@example.com","password":"Password1"}
                """.formatted(phone))).andExpect(status().isOk()).andReturn();
            String admin = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
            mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        }
        return new OpenShop(seller, shopId.longValue(), slug);
    }

    private String token(String email, String phone) throws Exception {
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"สมชาย","lastName":"ขายของ","email":"%s","phone":"%s","password":"Password1"}
            """.formatted(email, phone))).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
    }

    private void upload(String token, String type) throws Exception {
        var file = new MockMultipartFile("file", type.toLowerCase() + ".png", "image/png", PNG);
        mvc.perform(multipart("/api/merchant-applications/draft/documents/" + type).file(file).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private static String simpleProduct(String nameTh, String nameEn, String sku, String price) {
        return """
            {"nameTh":"%s","nameEn":"%s","productType":"SIMPLE","basePrice":%s,"skus":[{"skuCode":"%s","price":%s}]}
            """.formatted(nameTh, nameEn, price, sku, price);
    }

    private record OpenShop(String token, long shopId, String slug) {}
}
