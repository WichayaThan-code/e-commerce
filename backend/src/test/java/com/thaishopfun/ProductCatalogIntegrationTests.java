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

import java.math.BigDecimal;
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

    @BeforeEach
    void clean() {
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

        users.save(UserAccount.admin("catalog-admin@example.com", passwords.encode("Password1")));
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
    void importJobReportsProgressAndRejectsASecondRun() throws Exception {
        var shop = openShop("import-seller@example.com", "0855555555", "ร้านนำเข้า", "Import Shop", true);
        var other = openShop("import-other@example.com", "0866666666", "ร้านอื่น", "Other Shop", false, "9876543210987");
        var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"identity":"approver-0855555555@example.com","password":"Password1"}
            """)).andExpect(status().isOk()).andReturn();
        String admin = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(post("/api/admin/store-applications/" + other.shopId + "/approve").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        var rows = List.of(
            new ProductSpreadsheet.SheetRow(2, "ชาไทย", null, null, null, null, null, null, null, "IMP-1", null, null, new BigDecimal("45.00"), null, null, 1, null, List.of(), null),
            new ProductSpreadsheet.SheetRow(3, "ขนม", null, null, null, null, null, null, null, "IMP-2", null, null, null, null, null, null, null, List.of(), null)
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
            var approver = users.save(UserAccount.admin("approver-" + phone + "@example.com", passwords.encode("Password1")));
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
