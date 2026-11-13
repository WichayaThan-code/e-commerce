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
import com.thaishopfun.merchant.ShopMemberRepository;
import com.thaishopfun.merchant.ShopReinstatementRequestRepository;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.catalog.ProductSpreadsheet;
import com.thaishopfun.translation.ContentTranslationRepository;
import com.thaishopfun.translation.FakeTranslationProvider;
import com.thaishopfun.translation.TranslationEntityType;
import com.thaishopfun.translation.TranslationOrigin;
import com.thaishopfun.translation.TranslationService;
import com.thaishopfun.translation.TranslationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TranslationIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    @Autowired MockMvc mvc;
    @Autowired ContentTranslationRepository translations;
    @Autowired TranslationService translationService;
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

    @BeforeEach
    void clean() {
        translations.deleteAll();
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
    void productSaveStoresThaiFallbackRespectsManualAndAcceptLanguage() throws Exception {
        var shop = openShop("translate-seller@example.com", "0899999999", "ร้านแปล", "Translate Shop", true);
        var created = mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content(thaiOnlyProduct("ชาไทย", "TEA-TH", "45.00")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nameTh").value("ชาไทย"))
            .andExpect(jsonPath("$.nameEnMeta.status").value("DONE"))
            .andExpect(jsonPath("$.nameEnMeta.origin").value("MANUAL"))
            .andReturn();
        String productId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        String productSlug = JsonPath.read(created.getResponse().getContentAsString(), "$.slug");
        assertEquals("ชาไทย", productNameRow(productId, "en").getTranslatedText());
        assertEquals("ชาไทย", productNameRow(productId, "zh").getTranslatedText());
        assertEquals(0, pendingProductRows(productId));

        var humanUpdate = mvc.perform(put("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"nameTh":"ชาไทย","nameEn":"Thai Tea","productType":"SIMPLE","basePrice":45.00,"skus":[{"skuCode":"TEA-TH","price":45.00}]}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nameEnMeta.origin").value("MANUAL"))
            .andReturn();
        productSlug = JsonPath.read(humanUpdate.getResponse().getContentAsString(), "$.slug");
        var humanEnglish = productNameRow(productId, "en");
        assertEquals(TranslationOrigin.MANUAL, humanEnglish.getOrigin());
        assertEquals("Thai Tea", humanEnglish.getTranslatedText());
        assertEquals("ชาไทย", productNameRow(productId, "zh").getTranslatedText());

        var renamed = mvc.perform(put("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content(thaiOnlyProduct("ชาเขียว", "TEA-TH", "45.00")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nameEnMeta.status").value("DONE"))
            .andExpect(jsonPath("$.nameEnMeta.origin").value("MANUAL"))
            .andReturn();
        productSlug = JsonPath.read(renamed.getResponse().getContentAsString(), "$.slug");
        translationService.processPendingForTests();
        var refreshed = productNameRow(productId, "en");
        assertEquals(TranslationStatus.DONE, refreshed.getStatus());
        assertEquals("ชาเขียว", refreshed.getTranslatedText());
        assertEquals(0, pendingProductRows(productId));

        var file = new MockMultipartFile("file", "tea.png", "image/png", PNG);
        mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/images").file(file).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk());
        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/publish").header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk());

        mvc.perform(get("/api/shops/" + shop.slug + "/products/" + productSlug).header("Accept-Language", "en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nameEn").value("ชาเขียว"));
    }

    @Test
    void thaiOnlyProductPublishesAndPublicLocaleShowsThai() throws Exception {
        var shop = openShop("publish-th@example.com", "0899999998", "ร้านไทย", "Thai Only", true);
        var created = mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content(thaiOnlyProduct("ชาไทย", "TEA-PUB", "45.00")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nameEnMeta.status").value("DONE"))
            .andExpect(jsonPath("$.nameZhMeta.status").value("DONE"))
            .andReturn();
        String productId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        String productSlug = JsonPath.read(created.getResponse().getContentAsString(), "$.slug");
        assertEquals(0, pendingProductRows(productId));

        var file = new MockMultipartFile("file", "tea.png", "image/png", PNG);
        mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/images").file(file).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk());
        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/publish").header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk());

        translationService.processPendingForTests();
        mvc.perform(get("/api/shops/" + shop.slug + "/products/" + productSlug + "?locale=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nameEn").value("ชาไทย"));
    }

    @Test
    void productSaveNeverCallsProviderEvenWhenItFails() throws Exception {
        FakeTranslationProvider.fail.set(true);
        try {
            var shop = openShop("fail-tr@example.com", "0899999997", "ร้านล้ม", "Fail Shop", true);
            var created = mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
                .contentType(MediaType.APPLICATION_JSON).content(thaiOnlyProduct("ชาไทย", "TEA-FAIL", "45.00")))
                .andExpect(status().isOk())
                .andReturn();
            String productId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
            String productSlug = JsonPath.read(created.getResponse().getContentAsString(), "$.slug");
            translationService.processPendingForTests();
            var english = productNameRow(productId, "en");
            assertEquals(TranslationStatus.DONE, english.getStatus());
            assertEquals(TranslationOrigin.MANUAL, english.getOrigin());
            assertEquals(0, english.getAttempts());
            assertEquals("ชาไทย", english.getTranslatedText());

            var file = new MockMultipartFile("file", "tea.png", "image/png", PNG);
            mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/images").file(file).header("Authorization", "Bearer " + shop.token))
                .andExpect(status().isOk());
            mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/publish").header("Authorization", "Bearer " + shop.token))
                .andExpect(status().isOk());
            mvc.perform(get("/api/shops/" + shop.slug + "/products/" + productSlug + "?locale=en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("ชาไทย"));
        } finally {
            FakeTranslationProvider.fail.set(false);
        }
    }

    @Test
    void optionCodesStayUntranslated() throws Exception {
        var shop = openShop("option-tr@example.com", "0899999996", "ร้านตัวเลือก", "Option Shop", true);
        var created = mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"nameTh":"เสื้อ","productType":"VARIABLE","basePrice":100.00,
                 "optionSchema":[{"name":"สี","values":["แดง","S"]},{"name":"ไซซ์","values":["XL","128GB"]}],
                 "skus":[{"skuCode":"SHIRT-1","price":100.00,"optionValues":{"สี":"แดง","ไซซ์":"XL"},"optionLabel":"แดง / XL"},
                         {"skuCode":"SHIRT-2","price":100.00,"optionValues":{"สี":"S","ไซซ์":"128GB"},"optionLabel":"S / 128GB"}]}
                """))
            .andExpect(status().isOk())
            .andReturn();
        String productId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        String productSlug = JsonPath.read(created.getResponse().getContentAsString(), "$.slug");
        translationService.processPendingForTests();
        var optionFields = translations.findAll().stream()
            .filter(row -> row.getEntityType() == TranslationEntityType.PRODUCT_OPTION && productId.equals(row.getEntityId()) && "en".equals(row.getLocale()))
            .map(row -> row.getFieldName())
            .sorted()
            .toList();
        assertEquals(List.of("n0", "n1", "v0.0"), optionFields);

        var file = new MockMultipartFile("file", "shirt.png", "image/png", PNG);
        mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/images").file(file).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk());
        mvc.perform(post("/api/merchant/shops/" + shop.shopId + "/products/" + productId + "/publish").header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk());
        mvc.perform(get("/api/shops/" + shop.slug + "/products/" + productSlug + "?locale=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.optionSchema[0].name").value("EN:สี"))
            .andExpect(jsonPath("$.optionSchema[0].values[0]").value("EN:แดง"))
            .andExpect(jsonPath("$.optionSchema[0].values[1]").value("S"))
            .andExpect(jsonPath("$.optionSchema[1].values[0]").value("XL"))
            .andExpect(jsonPath("$.optionSchema[1].values[1]").value("128GB"));
    }

    @Test
    void importWithOnlyThaiNameStoresThaiFallback() throws Exception {
        var shop = openShop("import-th@example.com", "0899999995", "ร้านนำเข้า", "Import Shop", true);
        var bytes = ProductSpreadsheet.write(List.of(new ProductSpreadsheet.SheetRow(
            2, "ชาไทย", null, null, "ชาหอม", null, null, null, null, "TEA-IMP", null, null,
            new BigDecimal("45"), null, null, 3, null, List.of(), null, null, null, null, null, null, null, null, null
        )));
        var file = new MockMultipartFile("file", "products.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        var accepted = mvc.perform(multipart("/api/merchant/shops/" + shop.shopId + "/products/import").file(file).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isAccepted())
            .andReturn();
        String jobId = JsonPath.read(accepted.getResponse().getContentAsString(), "$.jobId");
        String body = "";
        for (var attempt = 0; attempt < 50; attempt++) {
            body = mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products/import/" + jobId).header("Authorization", "Bearer " + shop.token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            String jobStatus = JsonPath.read(body, "$.status");
            if ("COMPLETED".equals(jobStatus) || "FAILED".equals(jobStatus)) break;
            Thread.sleep(100);
        }
        assertEquals(1, ((Number) JsonPath.read(body, "$.created")).intValue());
        assertEquals(0, ((Number) JsonPath.read(body, "$.failed")).intValue());
        var listed = mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products").header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String productId = JsonPath.read(listed, "$.items[0].id");
        var saved = mvc.perform(get("/api/merchant/shops/" + shop.shopId + "/products/" + productId).header("Authorization", "Bearer " + shop.token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nameTh").value("ชาไทย"))
            .andExpect(jsonPath("$.nameEnMeta.origin").value("MANUAL"))
            .andExpect(jsonPath("$.nameEnMeta.status").value("DONE"))
            .andExpect(jsonPath("$.nameEn").value("ชาไทย"))
            .andReturn();
        assertEquals(0, pendingProductRows(productId));
    }

    private com.thaishopfun.translation.ContentTranslation productNameRow(String productId, String locale) {
        return translations.findAll().stream()
            .filter(row -> row.getEntityType() == TranslationEntityType.PRODUCT
                && productId.equals(row.getEntityId())
                && locale.equals(row.getLocale())
                && "name".equals(row.getFieldName()))
            .findFirst()
            .orElseThrow();
    }

    private long pendingProductRows(String productId) {
        return translations.findAll().stream()
            .filter(row -> row.getEntityType() == TranslationEntityType.PRODUCT
                && productId.equals(row.getEntityId())
                && row.getStatus() == TranslationStatus.PENDING)
            .count();
    }

    private OpenShop openShop(String email, String phone, String shopName, String shopNameEn, boolean approve) throws Exception {
        var seller = token(email, phone);
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
            {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
            """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
            {"ownerType":"PERSON","title":"นาย","firstName":"สมชาย","lastName":"ขายของ","citizenId":"1234567890123","birthDate":"1980-08-15","nationality":"ไทย","phone":"%s","email":"%s","houseNo":"9","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมชาย ขายของ"}
            """.formatted(phone, email))).andExpect(status().isOk());
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

    private static String thaiOnlyProduct(String nameTh, String sku, String price) {
        return """
            {"nameTh":"%s","productType":"SIMPLE","basePrice":%s,"skus":[{"skuCode":"%s","price":%s}]}
            """.formatted(nameTh, price, sku, price);
    }

    private record OpenShop(String token, long shopId, String slug) {}
}
