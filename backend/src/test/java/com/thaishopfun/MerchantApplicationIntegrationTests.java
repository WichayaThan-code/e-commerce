package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantApplicationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired MerchantDocumentRepository documents;
    @Autowired MerchantApplicationRepository applications;
    @Autowired ShopRepository shops;
    @Autowired ShopReinstatementRequestRepository reinstatements;
    @Autowired OwnerRepository owners;
    @Autowired OwnerAuditLogRepository ownerAudits;
    @Autowired ShopMemberRepository shopMembers;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordResetTokenRepository passwordResetTokens;
    @Autowired OtpVerificationRepository otpVerifications;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired InventoryLedgerRepository ledger;
    @Autowired InventoryLevelRepository levels;
    @Autowired ProductImageRepository productImages;
    @Autowired ProductSkuRepository productSkus;
    @Autowired ProductRepository catalogProducts;
    @Autowired PlatformRoleRepository platformRoles;

    @BeforeEach void clean(){ledger.deleteAll();levels.deleteAll();productSkus.deleteAll();productImages.deleteAll();catalogProducts.deleteAll();platformRoles.deleteAll();reinstatements.deleteAll();shopMembers.deleteAll();shops.deleteAll();documents.deleteAll();applications.deleteAll();ownerAudits.deleteAll();owners.deleteAll();otpVerifications.deleteAll();passwordResetTokens.deleteAll();refreshTokens.deleteAll();users.deleteAll();}

    @Test void savesUploadsAndSubmitsRealApplication() throws Exception {
        var registered=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
          {"firstName":"สมชาย","lastName":"ใจดี","email":"merchant@example.com","phone":"0812345678","password":"Password1"}
          """)).andExpect(status().isCreated()).andReturn();
        String token=JsonPath.read(registered.getResponse().getContentAsString(),"$.accessToken");

        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("""
          {"shopName":"ร้านทดสอบ","shopNameEn":"Test Shop","description":"สินค้าคุณภาพ","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"https://example.com","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
          """)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("""
          {"ownerType":"PERSON","title":"นาย","firstName":"สมชาย","lastName":"ใจดี","citizenId":"1234567890123","birthDate":"1980-08-15","nationality":"ไทย","phone":"0812345678","email":"merchant@example.com","houseNo":"123/45","villageNo":"5","building":"","alley":"สุขุมวิท 101","road":"สุขุมวิท","province":"กรุงเทพมหานคร","district":"บางนา","subdistrict":"บางนา","postalCode":"10260","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมชาย ใจดี"}
          """)).andExpect(status().isOk()).andExpect(jsonPath("$.citizenIdMasked").value("*********0123"));

        upload(token,"IDENTITY","identity.png");
        upload(token,"IDENTITY","identity-replaced.png");
        upload(token,"ADDRESS","address.png");
        upload(token,"LOGO","logo.png");

        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization","Bearer "+token))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"))
          .andExpect(jsonPath("$.referenceNo").isNotEmpty()).andExpect(jsonPath("$.documents.length()").value(3))
          .andExpect(jsonPath("$.shopPath").value("/shop/test-shop"));
        mvc.perform(get("/api/shops"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/shops/test-shop")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));
        mvc.perform(get("/api/shops/test-shop").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("ร้านทดสอบ"))
          .andExpect(jsonPath("$.status").value("PENDING_REVIEW")).andExpect(jsonPath("$.path").value("/shop/test-shop"))
          .andExpect(jsonPath("$.bankName").doesNotExist())
          .andExpect(jsonPath("$.logoUrl").value("/api/shops/test-shop/logo"));
        mvc.perform(get("/api/shops/test-shop/logo"))
          .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        org.junit.jupiter.api.Assertions.assertEquals(1, shops.count());
        org.junit.jupiter.api.Assertions.assertEquals(1, owners.count());
        mvc.perform(get("/api/merchant-applications/draft").header("Authorization","Bearer "+token))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization","Bearer "+token))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APPLICATION_INCOMPLETE"));
        org.junit.jupiter.api.Assertions.assertEquals(1, shops.count());
    }

    @Test void usesACodeWhenTheShopHasNoEnglishNameAndSuffixesARepeatedSlug() throws Exception {
        String first = tokenFor("thai@example.com", "0833333333");
        saveShop(first, "ร้านไทย", "").andExpect(status().isOk());
        saveOwner(first, "1111111111111").andExpect(status().isOk());
        upload(first, "IDENTITY", "identity.png");
        upload(first, "ADDRESS", "address.png");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + first))
          .andExpect(status().isOk()).andExpect(jsonPath("$.shopPath").value(org.hamcrest.Matchers.matchesPattern("/shop/shop-[0-9]+")));

        String second = tokenFor("other@example.com", "0844444444");
        saveShop(second, "ร้านหนึ่ง", "Shared Shop").andExpect(status().isOk());
        saveOwner(second, "2222222222222").andExpect(status().isOk());
        upload(second, "IDENTITY", "identity.png");
        upload(second, "ADDRESS", "address.png");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + second))
          .andExpect(status().isOk()).andExpect(jsonPath("$.shopPath").value("/shop/shared-shop"));

        String third = tokenFor("third@example.com", "0855555555");
        saveShop(third, "ร้านสอง", "Shared Shop").andExpect(status().isOk());
        saveOwner(third, "3333333333333").andExpect(status().isOk());
        upload(third, "IDENTITY", "identity.png");
        upload(third, "ADDRESS", "address.png");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + third))
          .andExpect(status().isOk()).andExpect(jsonPath("$.shopPath").value("/shop/shared-shop-2"));
        mvc.perform(get("/api/shops/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));
    }

    @Test void englishShopNameRejectsThaiCharactersButThaiNameMayContainEnglish() throws Exception {
        String token = tokenFor("en-name@example.com", "0855555556");
        saveShop(token, "ร้าน ThaiShop", "Thai Shop ร้าน").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("shopNameEn"))
            .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID"));
        saveShop(token, "ร้าน ThaiShop", "Thai Shop 2026 & Co.").andExpect(status().isOk());
    }

    @Test void checksShopNameAvailabilityWithTheSaveShopRule() throws Exception {
        String first = tokenFor("name-a@example.com", "0866666666");
        String second = tokenFor("name-b@example.com", "0877777777");
        saveShop(first, "Unique Name Shop").andExpect(status().isOk());
        // own draft never counts against itself
        mvc.perform(get("/api/merchant-applications/draft/shop-name/check").param("name", "  unique name shop ").header("Authorization", "Bearer " + first))
          .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        // another user: same name, different case/whitespace, is taken
        mvc.perform(get("/api/merchant-applications/draft/shop-name/check").param("name", " UNIQUE NAME SHOP").header("Authorization", "Bearer " + second))
          .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/merchant-applications/draft/shop-name/check").param("name", "Another Free Name").header("Authorization", "Bearer " + second))
          .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        saveShop(second, "Unique Name Shop").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHOP_EXISTS"));
    }

    @Test void rejectsDuplicateShopNameAndAllowsTheSameOwnerOnAnotherShop() throws Exception {
        String first = tokenFor("first@example.com", "0811111111");
        String second = tokenFor("second@example.com", "0822222222");
        saveShop(first, "ร้านซ้ำ").andExpect(status().isOk());
        saveOwner(first).andExpect(status().isOk());
        saveShop(first, "ร้านซ้ำ").andExpect(status().isOk());
        saveShop(second, "ร้านซ้ำ").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHOP_EXISTS"));
        saveShop(second, "ร้านอื่น").andExpect(status().isOk());
        saveOwner(second).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CITIZEN_ID_TAKEN"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("citizenId"))
            .andExpect(jsonPath("$.fieldErrors[0].code").value("CITIZEN_ID_TAKEN"));
    }

    @Test void duplicateCitizenIdAndInvalidOwnerReturnFieldErrors() throws Exception {
        String first = tokenFor("taken-a@example.com", "0810001112");
        String second = tokenFor("taken-b@example.com", "0820002223");
        saveShop(first, "ร้านตรวจซ้ำ").andExpect(status().isOk());
        saveOwner(first, "1100700123451").andExpect(status().isOk());
        saveShop(second, "ร้านตรวจซ้ำสอง").andExpect(status().isOk());
        saveOwner(second, "1100700123451").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CITIZEN_ID_TAKEN"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("citizenId"))
            .andExpect(jsonPath("$.fieldErrors[0].code").value("CITIZEN_ID_TAKEN"));
        mvc.perform(post("/api/merchant-applications/validate-owner").header("Authorization", "Bearer " + second)
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"ownerType":"PERSON","title":"นาง","firstName":"สมหญิง","lastName":"ใจดี","citizenId":"1100700123451","birthDate":"1980-08-15","nationality":"ไทย","phone":"0820002223","email":"taken-b@example.com","houseNo":"1","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมหญิง ใจดี"}
                  """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("citizenId"));
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", "Bearer " + second)
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"ownerType":"PERSON","title":"","firstName":"","lastName":"","citizenId":"abc","birthDate":"1980-01-01","nationality":"ไทย","phone":"xx","email":"not-an-email","houseNo":"1","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"12","bankName":"KBANK","bankAccountNumber":"abc","bankAccountName":""}
                  """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'citizenId')]").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'postalCode')]").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").isNotEmpty());
    }

    @Test void secondShopPrefillsOwnerAndOwnerDocumentsButStillRequiresTheOwnerAndDocumentSteps() throws Exception {
        String token = tokenFor("again@example.com", "0866666666");
        saveShop(token, "ร้านแรก", "First Shop").andExpect(status().isOk());
        saveOwner(token, "4444444444444").andExpect(status().isOk());
        upload(token, "IDENTITY", "identity.png");
        upload(token, "PORTRAIT", "portrait.png");
        upload(token, "ADDRESS", "address.png");
        upload(token, "LOGO", "logo.png");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        var firstApplication = applications.findAll().get(0).getId();
        var firstDocs = documents.findByApplication_Id(firstApplication);
        var listed = mvc.perform(get("/api/merchant/owners").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        String ownerId = JsonPath.read(listed.getResponse().getContentAsString(), "$[0].id");

        // Open a second shop: owner details + owner documents (ID card, portrait) prefilled; payout + shop documents are not.
        var opened = mvc.perform(post("/api/merchant/shops?ownerId=" + ownerId).header("Authorization", "Bearer " + token))
          .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(ownerId))
          .andExpect(jsonPath("$.existingOwner").value(true))
          .andExpect(jsonPath("$.firstName").value("สมหญิง")).andExpect(jsonPath("$.citizenIdMasked").value("*********4444"))
          .andExpect(jsonPath("$.bankName").doesNotExist()).andExpect(jsonPath("$.bankAccountMasked").doesNotExist())
          .andExpect(jsonPath("$.documents.length()").value(2))
          .andExpect(jsonPath("$.documents[?(@.type == 'IDENTITY')]").isNotEmpty())
          .andExpect(jsonPath("$.documents[?(@.type == 'PORTRAIT')]").isNotEmpty())
          .andExpect(jsonPath("$.documents[?(@.type == 'ADDRESS')]").isEmpty())
          .andExpect(jsonPath("$.documents[?(@.type == 'LOGO')]").isEmpty())
          .andReturn();
        String draftId = JsonPath.read(opened.getResponse().getContentAsString(), "$.id");
        var copies = documents.findByApplication_Id(java.util.UUID.fromString(draftId));
        for (var copy : copies) {
            var source = firstDocs.stream().filter(doc -> doc.getType() == copy.getType()).findFirst().orElseThrow();
            org.junit.jupiter.api.Assertions.assertNotEquals(source.getId(), copy.getId());
            org.junit.jupiter.api.Assertions.assertNotEquals(source.getStoragePath(), copy.getStoragePath());
            org.junit.jupiter.api.Assertions.assertEquals(source.getOriginalFilename(), copy.getOriginalFilename());
            org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.isRegularFile(java.nio.file.Path.of(copy.getStoragePath())));
        }
        mvc.perform(get("/api/merchant-applications/draft/documents/IDENTITY").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk()).andExpect(content().contentType("image/png"));

        // Not skipped any more: shop details, payout and the shop address document are still required.
        saveShop(token, "ร้านสอง", "Second Shop").andExpect(status().isOk());
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APPLICATION_INCOMPLETE"));
        saveLinkedOwner(token, "COMPANY", "SCB", "9876543210", "บริษัท ร้านสอง จำกัด").andExpect(status().isOk())
          .andExpect(jsonPath("$.citizenIdMasked").value("*********4444"))
          .andExpect(jsonPath("$.bankAccountMasked").value("******3210"))
          .andExpect(jsonPath("$.bankAccountName").value("บริษัท ร้านสอง จำกัด"))
          .andExpect(jsonPath("$.phone").value("0899999999"));
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APPLICATION_INCOMPLETE"));
        upload(token, "ADDRESS", "address-2.png");
        // Replacing a prefilled copy only affects the new application.
        upload(token, "IDENTITY", "identity-2.png");
        // owner type changed to a company for this shop: the company certificate is required, as for a first application
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APPLICATION_INCOMPLETE"));
        upload(token, "BUSINESS", "company.png");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        var stillThere = documents.findByApplication_Id(firstApplication);
        org.junit.jupiter.api.Assertions.assertEquals(firstDocs.size(), stillThere.size());
        for (var doc : stillThere) org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.isRegularFile(java.nio.file.Path.of(doc.getStoragePath())));
        // The shared owner profile keeps its own payout account; the new shop's account lives on its application.
        var owner = owners.findAll().get(0);
        org.junit.jupiter.api.Assertions.assertEquals("สมหญิง ใจดี", owner.getBankAccountName());
        org.junit.jupiter.api.Assertions.assertEquals("0822222222", owner.getPhone());
        org.junit.jupiter.api.Assertions.assertEquals("NONE", owner.getSensitiveReview());
        mvc.perform(get("/api/merchant/shops").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0].ownerId").value(ownerId))
          .andExpect(jsonPath("$[1].ownerId").value(ownerId));
        org.junit.jupiter.api.Assertions.assertEquals(1, owners.count());
        org.junit.jupiter.api.Assertions.assertEquals(2, shops.count());
    }

    @Test void pickingAnExistingOwnerOnTheOwnerStepCopiesOnlyMissingOwnerDocuments() throws Exception {
        String token = tokenFor("pick@example.com", "0867777777");
        saveShop(token, "ร้านเลือกเจ้าของ", "Pick Shop").andExpect(status().isOk());
        saveOwner(token, "5555555555555").andExpect(status().isOk());
        upload(token, "IDENTITY", "identity.png");
        upload(token, "PORTRAIT", "portrait.png");
        upload(token, "ADDRESS", "address.png");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        var listed = mvc.perform(get("/api/merchant/owners").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        String ownerId = JsonPath.read(listed.getResponse().getContentAsString(), "$[0].id");

        saveShop(token, "ร้านเลือกเจ้าของสอง", "Pick Shop Two").andExpect(status().isOk());
        // a returning merchant's new draft is linked to their first owner profile up front (ensureOwnerPrefill)
        mvc.perform(get("/api/merchant-applications/draft").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(ownerId));
        upload(token, "PORTRAIT", "portrait-new.png");
        var beforePick = mvc.perform(get("/api/merchant-applications/draft").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        java.util.List<String> ownPortrait = JsonPath.read(beforePick.getResponse().getContentAsString(), "$.documents[?(@.type == 'PORTRAIT')].id");
        String ownPortraitId = ownPortrait.get(0);
        mvc.perform(post("/api/merchant-applications/draft/owner/" + ownerId).header("Authorization", "Bearer " + token))
          .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(ownerId))
          .andExpect(jsonPath("$.bankName").doesNotExist())
          .andExpect(jsonPath("$.documents.length()").value(2))
          .andExpect(jsonPath("$.documents[?(@.type == 'IDENTITY')]").isNotEmpty())
          .andExpect(jsonPath("$.documents[?(@.type == 'ADDRESS')]").isEmpty());
        // the PORTRAIT uploaded for this draft is kept, not overwritten by the earlier one
        var current = mvc.perform(get("/api/merchant-applications/draft").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        java.util.List<String> portraitIds = JsonPath.read(current.getResponse().getContentAsString(), "$.documents[?(@.type == 'PORTRAIT')].id");
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(ownPortraitId), portraitIds);
        saveLinkedOwner(token, "KBANK", "12-3456-7890", "สมหญิง ใจดี").andExpect(status().isOk());
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APPLICATION_INCOMPLETE"));
        upload(token, "ADDRESS", "address-2.png");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test void approvalUnlocksStoreManagementAndRejectionClosesIt() throws Exception {
        String merchant = tokenFor("seller@example.com", "0844444444");
        saveShop(merchant, "ร้านรออนุมัติ", "Pending Shop").andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", "Bearer " + merchant).contentType(MediaType.APPLICATION_JSON).content("""
          {"ownerType":"PERSON","title":"นาย","firstName":"สมชาย","lastName":"รอตรวจ","citizenId":"1234567890123","birthDate":"1980-08-15","nationality":"ไทย","phone":"0844444444","email":"seller@example.com","houseNo":"9","province":"เชียงใหม่","district":"เมือง","subdistrict":"ศรีภูมิ","postalCode":"50200","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมชาย รอตรวจ"}
          """)).andExpect(status().isOk());
        upload(merchant, "IDENTITY", "identity.png");
        upload(merchant, "ADDRESS", "address.png");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + merchant)).andExpect(status().isOk());
        mvc.perform(get("/api/merchant/store-access").header("Authorization", "Bearer " + merchant))
          .andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(false)).andExpect(jsonPath("$.hasShop").value(true)).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        mvc.perform(get("/api/admin/store-applications").header("Authorization", "Bearer " + merchant)).andExpect(status().isForbidden());

        var reviewer = users.save(UserAccount.systemAccount("review-admin@example.com", passwords.encode("Password1")));
        // platform permissions come only from platform_roles
        platformRoles.save(PlatformRole.grant(reviewer.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
        var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
          {"identity":"review-admin@example.com","password":"Password1"}
          """)).andExpect(status().isOk()).andReturn();
        String admin = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
        var listed = mvc.perform(get("/api/admin/store-applications").header("Authorization", "Bearer " + admin))
          .andExpect(status().isOk()).andExpect(jsonPath("$[0].shopId").isNumber()).andExpect(jsonPath("$[0].shopName").value("ร้านรออนุมัติ"))
          .andExpect(jsonPath("$[0].ownerName").value("นาย สมชาย รอตรวจ"))
          .andExpect(jsonPath("$[0].status").value("PENDING_REVIEW"))
          .andExpect(jsonPath("$[0].citizenId").doesNotExist())
          .andExpect(jsonPath("$[0].bankAccount").doesNotExist())
          .andExpect(jsonPath("$[0].documents.length()").value(2))
          .andReturn();
        Number shopId = JsonPath.read(listed.getResponse().getContentAsString(), "$[0].shopId");
        String documentId = JsonPath.read(listed.getResponse().getContentAsString(), "$[0].documents[0].id");
        mvc.perform(get("/api/admin/store-applications/" + shopId + "/documents/" + documentId).header("Authorization", "Bearer " + admin))
          .andExpect(status().isOk())
          .andExpect(content().contentType("image/png"))
          .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")));
        mvc.perform(get("/api/admin/store-applications/" + shopId + "/documents/" + documentId).header("Authorization", "Bearer " + merchant))
          .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/store-applications/export").header("Authorization", "Bearer " + admin))
          .andExpect(status().isOk())
          .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mvc.perform(get("/api/admin/store-applications/export").header("Authorization", "Bearer " + merchant))
          .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/store-applications/bulk/approve").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content("{\"shopIds\":[" + shopId + "]}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1)).andExpect(jsonPath("$.applications[0].status").value("APPROVED"));
        mvc.perform(post("/api/admin/store-applications/bulk/approve").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content("{\"shopIds\":[" + shopId + "]}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(0)).andExpect(jsonPath("$.skipped").value(1));
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve").header("Authorization", "Bearer " + admin))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHOP_ALREADY_APPROVED"));
        mvc.perform(get("/api/merchant/store-access").header("Authorization", "Bearer " + merchant))
          .andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(true)).andExpect(jsonPath("$.hasShop").value(true));
        // approval does not touch users: the STORE_OWNER membership (created at submit) is what makes the seller a merchant
        var seller = users.findByEmailIgnoreCaseOrPhone("seller@example.com", "seller@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(shopMembers.findAll().stream().anyMatch(member ->
            seller.getId().equals(member.getUserId()) && "STORE_OWNER".equals(member.getRole())));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + merchant))
          .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MERCHANT"))
          .andExpect(jsonPath("$.roleCodes").isArray());

        mvc.perform(post("/api/admin/store-applications/bulk/suspend").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"shopIds\":[" + shopId + "],\"reasonCode\":\"ADMIN_SUSPEND\",\"reason\":\"ระงับหลังอนุมัติ\"}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1)).andExpect(jsonPath("$.applications[0].status").value("SUSPENDED"));
        mvc.perform(post("/api/admin/store-applications/bulk/reject").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content("{\"shopIds\":[" + shopId + "]}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(0)).andExpect(jsonPath("$.skipped").value(1));
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve").header("Authorization", "Bearer " + admin))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHOP_ALREADY_APPROVED"));
        mvc.perform(get("/api/merchant/store-access").header("Authorization", "Bearer " + merchant))
          .andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(false)).andExpect(jsonPath("$.hasShop").value(true)).andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(post("/api/merchant/reinstatement?shopId=" + shopId).header("Authorization", "Bearer " + merchant).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"ขอให้ตรวจสอบอีกครั้ง ร้านพร้อมแก้ไขตามเงื่อนไข\"}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/admin/store-applications").header("Authorization", "Bearer " + admin))
          .andExpect(status().isOk()).andExpect(jsonPath("$[0].reinstatementMessage").value("ขอให้ตรวจสอบอีกครั้ง ร้านพร้อมแก้ไขตามเงื่อนไข"));
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/reactivate").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/merchant/store-access").header("Authorization", "Bearer " + merchant))
          .andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(true)).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/merchant/settings").header("Authorization", "Bearer " + merchant))
          .andExpect(status().isOk()).andExpect(jsonPath("$.shopName").value("ร้านรออนุมัติ"));
    }

    @Test void submittedShopCreatesStoreOwnerMembershipAndNumericShopId() throws Exception {
        String token = tokenFor("member@example.com", "0877777777");
        saveShop(token, "ร้านสมาชิก", "Member Shop").andExpect(status().isOk());
        saveOwner(token, "5555555555555").andExpect(status().isOk());
        upload(token, "IDENTITY", "identity.png");
        upload(token, "ADDRESS", "address.png");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/merchant/shops").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk()).andExpect(jsonPath("$[0].shopId").isNumber()).andExpect(jsonPath("$[0].status").value("PENDING_REVIEW"));
        var shop = shops.findAll().get(0);
        org.junit.jupiter.api.Assertions.assertNotNull(shop.getId(), "shop_no running number must be generated");
        org.junit.jupiter.api.Assertions.assertTrue(shopMembers.existsByShopIdAndUserIdAndRole(shop.getUuid(), shop.getUser().getId(), "STORE_OWNER"));
    }

    @Test void rejectedApplicantCanStartANewApplication() throws Exception {
        String merchant = tokenFor("retry@example.com", "0888888888");
        saveShop(merchant, "ร้านโดนปฏิเสธ", "Rejected Shop").andExpect(status().isOk());
        saveOwner(merchant, "6666666666666").andExpect(status().isOk());
        upload(merchant, "IDENTITY", "identity.png");
        upload(merchant, "ADDRESS", "address.png");
        var submitted = mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + merchant))
          .andExpect(status().isOk()).andReturn();
        String firstId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.id");

        var rejecter = users.save(UserAccount.systemAccount("reject-admin@example.com", passwords.encode("Password1")));
        platformRoles.save(PlatformRole.grant(rejecter.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
        var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
          {"identity":"reject-admin@example.com","password":"Password1"}
          """)).andExpect(status().isOk()).andReturn();
        String admin = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
        var shopId = shops.findAll().get(0).getId();
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/reject").header("Authorization", "Bearer " + admin))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        org.junit.jupiter.api.Assertions.assertEquals(com.thaishopfun.merchant.ApplicationStatus.REJECTED,
            applications.findById(java.util.UUID.fromString(firstId)).orElseThrow().getStatus());

        var draft = mvc.perform(get("/api/merchant-applications/draft").header("Authorization", "Bearer " + merchant))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn();
        String draftId = JsonPath.read(draft.getResponse().getContentAsString(), "$.id");
        org.junit.jupiter.api.Assertions.assertNotEquals(firstId, draftId);
        saveShop(merchant, "ร้านใหม่หลังปฏิเสธ", "Second Try").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(draftId));
        mvc.perform(get("/api/merchant-applications/draft").header("Authorization", "Bearer " + merchant))
          .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(draftId));
    }

    @Test void ownerCanDownloadOwnDraftDocumentAndOthersGet404() throws Exception {
        String owner = tokenFor("doc-owner@example.com", "0811111101");
        upload(owner, "IDENTITY", "identity.png");
        mvc.perform(get("/api/merchant-applications/draft/documents/IDENTITY").header("Authorization", "Bearer " + owner))
          .andExpect(status().isOk())
          .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
          .andExpect(header().string(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("inline")))
          .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));

        String other = tokenFor("doc-other@example.com", "0811111102");
        mvc.perform(get("/api/merchant-applications/draft/documents/IDENTITY").header("Authorization", "Bearer " + other))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));

        mvc.perform(get("/api/merchant-applications/draft/documents/IDENTITY"))
          .andExpect(status().isUnauthorized());
    }

    @Test void badPathAndQueryParametersReturn400NotServerError() throws Exception {
        String token = tokenFor("params@example.com", "0899999990");
        var file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10});
        mvc.perform(multipart("/api/merchant-applications/draft/documents/NOT_A_TYPE").file(file).header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/merchant/settings?shopId=abc").header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/merchant-applications/draft/owner/not-a-uuid").header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/merchant/reinstatement").header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/merchant/shops")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/categories")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").isNumber()).andExpect(jsonPath("$[0].nameZh").isNotEmpty());
    }

    private String tokenFor(String email, String phone) throws Exception {
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
          {"firstName":"สมหญิง","lastName":"ใจดี","email":"%s","phone":"%s","password":"Password1"}
          """.formatted(email, phone))).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
    }

    private org.springframework.test.web.servlet.ResultActions saveShop(String token, String shopName) throws Exception {
        return saveShop(token, shopName, "Shop");
    }

    private org.springframework.test.web.servlet.ResultActions saveShop(String token, String shopName, String shopNameEn) throws Exception {
        return mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("""
          {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
          """.formatted(shopName, shopNameEn)));
    }

    private org.springframework.test.web.servlet.ResultActions saveLinkedOwner(String token, String bank, String account, String accountName) throws Exception {
        return saveLinkedOwner(token, "PERSON", bank, account, accountName);
    }

    private org.springframework.test.web.servlet.ResultActions saveLinkedOwner(String token, String ownerType, String bank, String account, String accountName) throws Exception {
        return mvc.perform(put("/api/merchant-applications/draft/owner/linked").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("""
          {"ownerType":"%s","title":"นาง","firstName":"สมหญิง","lastName":"ใจดี","birthDate":"1980-08-15","nationality":"ไทย","phone":"0899999999","email":"second@example.com","houseNo":"1","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"%s","bankAccountNumber":"%s","bankAccountName":"%s"}
          """.formatted(ownerType, bank, account, accountName)));
    }

    private org.springframework.test.web.servlet.ResultActions saveOwner(String token) throws Exception {
        return saveOwner(token, "1234567890123");
    }

    private org.springframework.test.web.servlet.ResultActions saveOwner(String token, String citizenId) throws Exception {
        return mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("""
          {"ownerType":"PERSON","title":"นาง","firstName":"สมหญิง","lastName":"ใจดี","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"0822222222","email":"second@example.com","houseNo":"1","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมหญิง ใจดี"}
          """.formatted(citizenId)));
    }

    private void upload(String token,String type,String filename) throws Exception {
        var file=new MockMultipartFile("file",filename,"image/png",new byte[]{(byte)137,80,78,71,13,10,26,10});
        mvc.perform(multipart("/api/merchant-applications/draft/documents/"+type).file(file).header("Authorization","Bearer "+token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.documents[?(@.type == '" + type + "')]").isNotEmpty())
          .andExpect(jsonPath("$.documents[?(@.type == '" + type + "')].originalFilename", everyItem(matchesPattern("Doc_\\(" + type + "\\)_[A-F0-9]{10}\\.png"))));
    }
}
