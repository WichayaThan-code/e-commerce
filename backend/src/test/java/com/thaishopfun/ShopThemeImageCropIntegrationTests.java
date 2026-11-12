package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import com.thaishopfun.merchant.OwnerAuditLogRepository;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ShopMemberRepository;
import com.thaishopfun.merchant.ShopReinstatementRequestRepository;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.theme.ShopThemeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Image focal point + zoom ({@code crop} / {@code mobileCrop}) on theme media holders. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopThemeImageCropIntegrationTests {
    private static final String SECTIONS = """
        [
          {"key":"HERO","enabled":true,"variant":"FULL"},
          {"key":"BANNERS","enabled":true,"variant":null},
          {"key":"CATEGORIES","enabled":true,"variant":null},
          {"key":"TRUST","enabled":true,"variant":null},
          {"key":"INTRO","enabled":false,"variant":null},
          {"key":"FEATURED","enabled":true,"variant":"GRID"},
          {"key":"BESTSELLERS","enabled":true,"variant":"GRID"},
          {"key":"NEW_ARRIVALS","enabled":true,"variant":"GRID"},
          {"key":"CATALOG","enabled":true,"variant":"GRID"},
          {"key":"HOURS","enabled":false,"variant":null},
          {"key":"COUPONS","enabled":true,"variant":null}
        ]
        """;

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordResetTokenRepository passwordResetTokens;
    @Autowired OtpVerificationRepository otpVerifications;
    @Autowired MerchantApplicationRepository applications;
    @Autowired MerchantDocumentRepository documents;
    @Autowired ShopReinstatementRequestRepository reinstatements;
    @Autowired ShopMemberRepository shopMembers;
    @Autowired ShopRepository shops;
    @Autowired OwnerAuditLogRepository ownerAudits;
    @Autowired OwnerRepository owners;
    @Autowired ShopThemeRepository themes;

    @BeforeEach
    void clean() {
        wipe();
    }

    @AfterEach
    void cleanupAfter() {
        themes.deleteAll();
    }

    private void wipe() {
        themes.deleteAll();
        reinstatements.deleteAll();
        shopMembers.deleteAll();
        shops.deleteAll();
        documents.deleteAll();
        applications.deleteAll();
        ownerAudits.deleteAll();
        owners.deleteAll();
        otpVerifications.deleteAll();
        passwordResetTokens.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void saveCropThenReadBackSameValues() throws Exception {
        var owner = register("theme-crop-save@example.com", "0815100201");
        long shopId = openShop(owner, "ร้านครอป", "Crop Shop", "1100000000201");

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", """
                    {
                      "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
                      "font": "NOTO_SANS_THAI",
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                      "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [{
                        "imageUrl":"https://cdn.example.com/banner.jpg",
                        "title":"Banner",
                        "link":"/products",
                        "crop":{"x":22.5,"y":67.25,"zoom":1.75},
                        "mobileCrop":{"x":40,"y":30,"zoom":2}
                      }],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"Genuine","enabled":true}]},
                      "sections": %s
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.banners[0].crop.x").value(22.5))
            .andExpect(jsonPath("$.config.banners[0].crop.y").value(67.25))
            .andExpect(jsonPath("$.config.banners[0].crop.zoom").value(1.75))
            .andExpect(jsonPath("$.config.banners[0].mobileCrop.x").value(40.0))
            .andExpect(jsonPath("$.config.banners[0].mobileCrop.y").value(30.0))
            .andExpect(jsonPath("$.config.banners[0].mobileCrop.zoom").value(2.0));

        mvc.perform(get("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.banners[0].crop.x").value(22.5))
            .andExpect(jsonPath("$.config.banners[0].crop.zoom").value(1.75))
            .andExpect(jsonPath("$.config.banners[0].mobileCrop.y").value(30.0));
    }

    @Test
    void outOfRangeCropIsClampedAndMalformedIsRejected() throws Exception {
        var owner = register("theme-crop-clamp@example.com", "0815100202");
        long shopId = openShop(owner, "ร้านครอปคลैंप", "Crop Clamp Shop", "1100000000202");

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", """
                    {
                      "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
                      "font": "NOTO_SANS_THAI",
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                      "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [{
                        "imageUrl":"https://cdn.example.com/banner.jpg",
                        "title":"Banner",
                        "crop":{"x":-20,"y":150,"zoom":9}
                      }],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"Genuine","enabled":true}]},
                      "sections": %s
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.banners[0].crop.x").value(0.0))
            .andExpect(jsonPath("$.config.banners[0].crop.y").value(100.0))
            .andExpect(jsonPath("$.config.banners[0].crop.zoom").value(3.0));

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", """
                    {
                      "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
                      "font": "NOTO_SANS_THAI",
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                      "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [{
                        "imageUrl":"https://cdn.example.com/banner.jpg",
                        "title":"Banner",
                        "crop":"not-an-object"
                      }],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"Genuine","enabled":true}]},
                      "sections": %s
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.banners[0].crop')].code", hasItem("THEME_IMAGE_CROP_INVALID")));
    }

    @Test
    void legacyThemeWithoutCropStillSaves() throws Exception {
        var owner = register("theme-crop-legacy@example.com", "0815100203");
        long shopId = openShop(owner, "ร้านครอปเดิม", "Crop Legacy Shop", "1100000000203");

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", """
                    {
                      "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
                      "font": "NOTO_SANS_THAI",
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                      "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [{"imageUrl":"https://cdn.example.com/banner.jpg","title":"Old"}],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"Genuine","enabled":true}]},
                      "sections": %s
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.banners[0].imageUrl").value("https://cdn.example.com/banner.jpg"))
            .andExpect(jsonPath("$.config.banners[0].crop").doesNotExist())
            .andExpect(jsonPath("$.config.banners[0].mobileCrop").doesNotExist());
    }

    private static String themePayload(String templateKey, String configJson) {
        return "{\"templateKey\":\"" + templateKey + "\",\"config\":" + configJson + "}";
    }

    private record Account(UUID id, String token) {}

    private Account register(String email, String phone) throws Exception {
        var body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
              {"firstName":"ทดสอบ","lastName":"ธีม","email":"%s","phone":"%s","password":"Password1"}
              """.formatted(email, phone)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Account(UUID.fromString(JsonPath.read(body, "$.user.id")), JsonPath.read(body, "$.accessToken"));
    }

    private long openShop(Account owner, String shopName, String shopNameEn, String citizenId) throws Exception {
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
              """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"ownerType":"PERSON","title":"นาย","firstName":"ทดสอบ","lastName":"ธีม","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"0899999999","email":"owner@example.com","houseNo":"1","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"ทดสอบ ธีม"}
              """.formatted(citizenId))).andExpect(status().isOk());
        for (var type : List.of("IDENTITY", "ADDRESS")) {
            mvc.perform(multipart("/api/merchant-applications/draft/documents/" + type).file(png()).header("Authorization", bearer(owner.token)))
                .andExpect(status().isOk());
        }
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", bearer(owner.token))).andExpect(status().isOk());
        return shops.findAll().stream().filter(shop -> shop.getName().equals(shopName)).findFirst().orElseThrow().getId();
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "image.png", "image/png", new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
