package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import com.thaishopfun.merchant.OwnerAuditLogRepository;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ShopMemberRepository;
import com.thaishopfun.merchant.ShopReinstatementRequestRepository;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.theme.ShopTheme;
import com.thaishopfun.merchant.theme.ShopThemeRepository;
import com.thaishopfun.merchant.theme.ShopThemeTemplate;
import com.thaishopfun.merchant.theme.ThemePresets;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** AG3: CUSTOM-only repeatable {@code layout.blocks}. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopThemeBlocksIntegrationTests {
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
    @Autowired PlatformRoleRepository platformRoles;
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
        themes.deleteAll();
        platformRoles.deleteAll();
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

    @AfterEach
    void cleanupAfter() {
        themes.deleteAll();
    }

    @Test
    void customWithoutBlocksSavesSynthesizedBlocksMatchingSections() throws Exception {
        var owner = register("blocks-synth@example.com", "0816100001");
        long shopId = openShop(owner, "ร้านบล็อกหนึ่ง", "Blocks One", "1100000000201");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(customPayload("""
                    ,"sectionSettings": {
                      "FEATURED": {"background":"SURFACE","align":"CENTER","columns":3,"paddingY":"LARGE"},
                      "HERO": {"background":"PRIMARY"}
                    }
                    """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout.blocks", hasSize(10)))
            .andExpect(jsonPath("$.config.layout.blocks[*].type", contains(
                "HERO", "BANNERS", "CATEGORIES", "TRUST", "INTRO", "FEATURED", "BESTSELLERS", "NEW_ARRIVALS", "CATALOG", "HOURS")))
            .andExpect(jsonPath("$.config.layout.blocks[0].id").value("legacy-hero"))
            .andExpect(jsonPath("$.config.layout.blocks[0].variant").value("FULL"))
            .andExpect(jsonPath("$.config.layout.blocks[0].settings.background").value("PRIMARY"))
            .andExpect(jsonPath("$.config.layout.blocks[4].enabled").value(false))
            .andExpect(jsonPath("$.config.layout.blocks[5].id").value("legacy-featured"))
            .andExpect(jsonPath("$.config.layout.blocks[5].settings.background").value("SURFACE"))
            .andExpect(jsonPath("$.config.layout.blocks[5].settings.align").value("CENTER"))
            .andExpect(jsonPath("$.config.layout.blocks[5].settings.columns").value(3))
            .andExpect(jsonPath("$.config.layout.blocks[5].settings.paddingY").value("LARGE"))
            .andExpect(jsonPath("$.config.layout.blocks[5].settings.fullWidth").value(false))
            .andExpect(jsonPath("$.config.layout.blocks[7].id").value("sec-new-arrivals"))
            .andExpect(jsonPath("$.config.layout.blocks[2].settings.columns").doesNotExist());

        mvc.perform(get("/api/shops/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.theme.config.layout.blocks", hasSize(10)))
            .andExpect(jsonPath("$.theme.config.layout.blocks[5].id").value("legacy-featured"));
    }

    @Test
    void legacyCustomThemeStoredWithoutBlocksIsMigratedOnRead() throws Exception {
        var owner = register("blocks-legacy@example.com", "0816100002");
        long shopId = openShop(owner, "ร้านบล็อกเก่า", "Blocks Legacy", "1100000000202");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();

        var config = ThemePresets.customConfig();
        var settings = new LinkedHashMap<String, Object>();
        settings.put("CATALOG", new LinkedHashMap<>(java.util.Map.of("background", "GRADIENT", "columns", 4, "paddingY", "COMPACT")));
        config.put("sectionSettings", settings);
        themes.save(new ShopTheme(shops.findByShopId(shopId).orElseThrow(), ShopThemeTemplate.CUSTOM, config, owner.id));

        var body = mvc.perform(get("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.templateKey").value("CUSTOM"))
            .andReturn().getResponse().getContentAsString();

        List<String> sectionKeys = JsonPath.read(body, "$.config.sections[*].key");
        List<String> blockTypes = JsonPath.read(body, "$.config.layout.blocks[*].type");
        assertEquals(sectionKeys, blockTypes, "synthesized blocks must follow the legacy section order");
        List<Boolean> sectionEnabled = JsonPath.read(body, "$.config.sections[*].enabled");
        List<Boolean> blockEnabled = JsonPath.read(body, "$.config.layout.blocks[*].enabled");
        assertEquals(sectionEnabled, blockEnabled);
        List<String> ids = JsonPath.read(body, "$.config.layout.blocks[*].id");
        assertEquals(ids.size(), ids.stream().distinct().count());
        assertTrue(ids.stream().allMatch(id -> id.matches("^[a-z0-9-]{1,40}$")), ids.toString());

        mvc.perform(get("/api/shops/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.theme.config.layout.blocks[?(@.type=='CATALOG')].settings.background", hasItem("GRADIENT")))
            .andExpect(jsonPath("$.theme.config.layout.blocks[?(@.type=='CATALOG')].settings.columns", hasItem(4)))
            .andExpect(jsonPath("$.theme.config.layout.blocks[?(@.type=='CATALOG')].settings.paddingY", hasItem("COMPACT")));
    }

    @Test
    void customSavesRepeatableBlocksStripsUnknownKeysAndSyncsSections() throws Exception {
        var owner = register("blocks-save@example.com", "0816100003");
        long shopId = openShop(owner, "ร้านบล็อกสาม", "Blocks Three", "1100000000203");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(customPayload("""
                    ,"layout": {
                      "evilLayoutKey": true,
                      "blocks": [
                        {"id":"top-banners","type":"BANNERS","enabled":true,"evil":1,"content":{"x":1},
                         "settings":{"background":"SURFACE","align":"LEFT","paddingY":"NORMAL","fullWidth":true,"evilSetting":1}},
                        {"id":"feat-a","type":"FEATURED","enabled":true,"variant":"LARGE",
                         "settings":{"background":"GRADIENT","align":"CENTER","columns":"4","paddingY":"LARGE","fullWidth":false,
                           "gradient":{"angle":45,"css":"url(evil)","stops":[{"color":"#fff1f2","at":0,"evil":1},{"color":"#fffbeb","at":100}]}}},
                        {"id":"hero-main","type":"HERO","enabled":true,"variant":"STRIP",
                         "settings":{"background":"PRIMARY","align":"CENTER","paddingY":"COMPACT","fullWidth":false,"overlay":40}},
                        {"id":"feat-b","type":"FEATURED","enabled":false,"variant":"MENU",
                         "settings":{"background":"NONE","align":"LEFT","paddingY":"NORMAL","fullWidth":false}}
                      ]
                    }
                    """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout.evilLayoutKey").doesNotExist())
            .andExpect(jsonPath("$.config.layout.blocks", hasSize(4)))
            .andExpect(jsonPath("$.config.layout.blocks[*].id", contains("top-banners", "feat-a", "hero-main", "feat-b")))
            .andExpect(jsonPath("$.config.layout.blocks[0].evil").doesNotExist())
            .andExpect(jsonPath("$.config.layout.blocks[0].content.x").doesNotExist())
            .andExpect(jsonPath("$.config.layout.blocks[0].settings.evilSetting").doesNotExist())
            .andExpect(jsonPath("$.config.layout.blocks[0].settings.fullWidth").value(true))
            .andExpect(jsonPath("$.config.layout.blocks[1].settings.columns").value(4))
            .andExpect(jsonPath("$.config.layout.blocks[1].settings.gradient.css").doesNotExist())
            .andExpect(jsonPath("$.config.layout.blocks[1].settings.gradient.stops[0].evil").doesNotExist())
            .andExpect(jsonPath("$.config.layout.blocks[1].settings.gradient.stops[1].color").value("#fffbeb"))
            .andExpect(jsonPath("$.config.layout.blocks[2].settings.overlay").value(40))
            // sections regenerated: unique types in first-seen order, then the missing ones disabled
            .andExpect(jsonPath("$.config.sections[*].key", contains(
                "BANNERS", "FEATURED", "HERO", "CATEGORIES", "TRUST", "INTRO", "BESTSELLERS", "NEW_ARRIVALS", "CATALOG", "HOURS")))
            .andExpect(jsonPath("$.config.sections[1].variant").value("LARGE"))
            .andExpect(jsonPath("$.config.sections[2].variant").value("STRIP"))
            .andExpect(jsonPath("$.config.sections[3].enabled").value(false))
            .andExpect(jsonPath("$.config.hero.layout").value("STRIP"));

        mvc.perform(get("/api/shops/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.theme.config.layout.blocks", hasSize(4)))
            .andExpect(jsonPath("$.theme.config.layout.blocks[3].id").value("feat-b"));
    }

    @Test
    void invalidBlocksReturn400WithPathAndCode() throws Exception {
        var owner = register("blocks-bad@example.com", "0816100004");
        long shopId = openShop(owner, "ร้านบล็อกสี่", "Blocks Four", "1100000000204");

        // duplicate id
        expectBlocksError(owner, shopId, """
            [{"id":"dup","type":"BANNERS","enabled":true,"settings":{}},
             {"id":"dup","type":"CATEGORIES","enabled":true,"settings":{}}]
            """, "config.layout.blocks[1].id", "THEME_BLOCKS_INVALID");

        // bad id pattern
        expectBlocksError(owner, shopId, """
            [{"id":"Bad_Id","type":"BANNERS","enabled":true,"settings":{}}]
            """, "config.layout.blocks[0].id", "THEME_BLOCKS_INVALID");

        // more than 30 blocks
        var tooMany = IntStream.range(0, 31)
            .mapToObj(i -> "{\"id\":\"b" + i + "\",\"type\":\"CATEGORIES\",\"enabled\":true,\"settings\":{}}")
            .collect(Collectors.joining(",", "[", "]"));
        expectBlocksError(owner, shopId, tooMany, "config.layout.blocks", "THEME_BLOCKS_INVALID");

        // two HERO
        expectBlocksError(owner, shopId, """
            [{"id":"h1","type":"HERO","enabled":true,"settings":{}},
             {"id":"h2","type":"HERO","enabled":true,"settings":{}}]
            """, "config.layout.blocks[1].type", "THEME_BLOCKS_INVALID");

        // two HOURS
        expectBlocksError(owner, shopId, """
            [{"id":"o1","type":"HOURS","enabled":true,"settings":{}},
             {"id":"o2","type":"HOURS","enabled":true,"settings":{}}]
            """, "config.layout.blocks[1].type", "THEME_BLOCKS_INVALID");

        // unknown type
        expectBlocksError(owner, shopId, """
            [{"id":"x","type":"CAROUSEL","enabled":true,"settings":{}}]
            """, "config.layout.blocks[0].type", "THEME_BLOCKS_INVALID");

        // bad enum / range tokens
        expectBlocksError(owner, shopId, """
            [{"id":"x","type":"FEATURED","enabled":true,"settings":{"background":"RAINBOW"}}]
            """, "config.layout.blocks[0].settings.background", "THEME_TOKEN_INVALID");
        expectBlocksError(owner, shopId, """
            [{"id":"x","type":"FEATURED","enabled":true,"settings":{"paddingY":"HUGE"}}]
            """, "config.layout.blocks[0].settings.paddingY", "THEME_TOKEN_INVALID");
        expectBlocksError(owner, shopId, """
            [{"id":"x","type":"FEATURED","enabled":true,"settings":{"columns":9}}]
            """, "config.layout.blocks[0].settings.columns", "THEME_TOKEN_INVALID");
        expectBlocksError(owner, shopId, """
            [{"id":"x","type":"BANNERS","enabled":true,"settings":{"columns":3}}]
            """, "config.layout.blocks[0].settings.columns", "THEME_TOKEN_INVALID");
        expectBlocksError(owner, shopId, """
            [{"id":"x","type":"FEATURED","enabled":true,"settings":{"background":"PRIMARY","overlay":81}}]
            """, "config.layout.blocks[0].settings.overlay", "THEME_TOKEN_INVALID");

        // gradient: CSS string / bad angle / bad hex
        expectBlocksError(owner, shopId, """
            [{"id":"x","type":"FEATURED","enabled":true,"settings":{"background":"GRADIENT","gradient":"linear-gradient(90deg,#000,#fff)"}}]
            """, "config.layout.blocks[0].settings.gradient", "THEME_GRADIENT_INVALID");
        expectBlocksError(owner, shopId, """
            [{"id":"x","type":"FEATURED","enabled":true,"settings":{"background":"GRADIENT",
              "gradient":{"angle":400,"stops":[{"color":"#112233","at":0},{"color":"#445566","at":100}]}}}]
            """, "config.layout.blocks[0].settings.gradient.angle", "THEME_GRADIENT_INVALID");
        expectBlocksError(owner, shopId, """
            [{"id":"x","type":"FEATURED","enabled":true,"settings":{"background":"GRADIENT",
              "gradient":{"angle":10,"stops":[{"color":"red","at":0},{"color":"#445566","at":100}]}}}]
            """, "config.layout.blocks[0].settings.gradient.stops[0].color", "THEME_COLOR_INVALID");

        // IMAGE background needs a media id that exists in this shop
        expectBlocksError(owner, shopId, """
            [{"id":"x","type":"FEATURED","enabled":true,"settings":{"background":"IMAGE"}}]
            """, "config.layout.blocks[0].settings.backgroundMediaId", "THEME_BLOCKS_INVALID");
        expectBlocksError(owner, shopId, """
            [{"id":"x","type":"FEATURED","enabled":true,"settings":{"background":"IMAGE","backgroundMediaId":"%s"}}]
            """.formatted(UUID.randomUUID()), "config.layout.blocks[0].settings.backgroundMediaId", "THEME_MEDIA_NOT_FOUND");

        // variant not allowed for the type
        expectBlocksError(owner, shopId, """
            [{"id":"x","type":"BANNERS","enabled":true,"variant":"GRID","settings":{}}]
            """, "config.layout.blocks[0].variant", "THEME_BLOCKS_INVALID");
    }

    @Test
    void generalAndFashionSilentlyStripLayoutBlocks() throws Exception {
        var owner = register("blocks-general@example.com", "0816100005");
        long shopId = openShop(owner, "ร้านบล็อกห้า", "Blocks Five", "1100000000205");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();

        for (var template : List.of("GENERAL", "FASHION")) {
            mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                    .header("Authorization", bearer(owner.token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(themePayload(template, """
                        {
                          "colors": {"primary":"#087bf5","secondary":"#08245d","background":"#ffffff","text":"#132b50"},
                          "font": "NOTO_SANS_THAI",
                          "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                          "hero": {"layout":"FULL","title":"สวัสดี","subtitle":"","ctaLabel":"","ctaLink":""},
                          "banners": [],
                          "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
                          "sections": %s,
                          "layout": {"blocks": [
                            {"id":"dup","type":"HERO","enabled":true,"settings":{}},
                            {"id":"dup","type":"HERO","enabled":true,"settings":{"background":"RAINBOW"}}
                          ]}
                        }
                        """.formatted(SECTIONS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateKey").value(template))
                .andExpect(jsonPath("$.config.layout").doesNotExist())
                .andExpect(jsonPath("$.config.hero.title").value("สวัสดี"))
                .andExpect(jsonPath("$.config.sections", hasSize(10)));

            mvc.perform(get("/api/shops/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme.templateKey").value(template))
                .andExpect(jsonPath("$.theme.config.layout").doesNotExist());
        }
    }

    @Test
    void switchingCustomBackToGeneralDropsBlocks() throws Exception {
        var owner = register("blocks-switch@example.com", "0816100006");
        long shopId = openShop(owner, "ร้านบล็อกหก", "Blocks Six", "1100000000206");

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(customPayload("")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout.blocks", hasSize(10)));

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", baseConfig("") )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.templateKey").value("GENERAL"))
            .andExpect(jsonPath("$.config.layout").doesNotExist());
    }

    private void expectBlocksError(Account owner, long shopId, String blocksJson, String field, String code) throws Exception {
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(customPayload(",\"layout\":{\"blocks\":" + blocksJson + "}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='" + field + "')].code", hasItem(code)));
    }

    private static String baseConfig(String extra) {
        return """
            {
              "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
              "font": "NOTO_SANS_THAI",
              "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
              "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
              "banners": [],
              "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
              "sections": %s
              %s
            }
            """.formatted(SECTIONS, extra);
    }

    private static String customPayload(String extra) {
        return themePayload("CUSTOM", baseConfig(extra));
    }

    private static String themePayload(String templateKey, String configJson) {
        return "{\"templateKey\":\"" + templateKey + "\",\"config\":" + configJson + "}";
    }

    private record Account(UUID id, String token) {}

    private Account register(String email, String phone) throws Exception {
        var body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
              {"firstName":"ทดสอบ","lastName":"บล็อก","email":"%s","phone":"%s","password":"Password1"}
              """.formatted(email, phone)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Account(UUID.fromString(JsonPath.read(body, "$.user.id")), JsonPath.read(body, "$.accessToken"));
    }

    private Account platformAdmin(String email, String phone) throws Exception {
        var account = register(email, phone);
        platformRoles.save(PlatformRole.grant(account.id, PlatformRole.PLATFORM_ADMIN, null, "test"));
        return account;
    }

    private void approve(long shopId) throws Exception {
        var admin = platformAdmin("blocks-admin-" + shopId + "@example.com", "083" + String.format("%07d", shopId % 10000000));
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve")
                .header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk());
    }

    private long openShop(Account owner, String shopName, String shopNameEn, String citizenId) throws Exception {
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
              """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"ownerType":"PERSON","title":"นาย","firstName":"ทดสอบ","lastName":"บล็อก","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"0899999999","email":"owner@example.com","houseNo":"1","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"ทดสอบ บล็อก"}
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
