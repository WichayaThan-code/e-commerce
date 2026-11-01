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
import com.thaishopfun.merchant.theme.ShopThemeRepository;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopThemeIntegrationTests {
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
          {"key":"HOURS","enabled":false,"variant":null}
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

    @org.junit.jupiter.api.AfterEach
    void cleanupAfter() {
        themes.deleteAll();
    }

    @Test
    void presetsIncludeCustom() throws Exception {
        var owner = register("theme-presets@example.com", "0815100001");
        long shopId = openShop(owner, "ร้านพรีเซ็ต", "Preset Shop", "1100000000101");

        mvc.perform(get("/api/merchant/settings/theme/presets?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].templateKey", hasItems("GENERAL", "FASHION", "FOOD_BEVERAGE", "CUSTOM")))
            .andExpect(jsonPath("$[?(@.templateKey=='CUSTOM')].config.colors.primary", hasItem("#087bf5")));
    }

    @Test
    void brandFontBodyAndAccentForEveryTemplate() throws Exception {
        var owner = register("theme-brand@example.com", "0815100099");
        long shopId = openShop(owner, "Brand Shop TH", "Brand Shop", "1100000000199");
        approve(shopId);

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", """
                    {
                      "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111","accent":"#c8a24b"},
                      "font": "PROMPT",
                      "fontBody": "noto_serif_thai", "brand": {"logoPosition":"center","textAlign":"CENTER","evil":"x"},
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                      "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"Genuine","enabled":true}]},
                      "sections": %s
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.font").value("PROMPT"))
            .andExpect(jsonPath("$.config.fontBody").value("NOTO_SERIF_THAI"))
            .andExpect(jsonPath("$.config.brand.logoPosition").value("CENTER"))
            .andExpect(jsonPath("$.config.brand.textAlign").value("CENTER"))
            .andExpect(jsonPath("$.config.brand.evil").doesNotExist())
            .andExpect(jsonPath("$.config.colors.accent").value("#c8a24b"));

        // Old single-font config stays valid (no fontBody / brand).
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", """
                    {
                      "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
                      "font": "PROMPT",
                      
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                      "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"Genuine","enabled":true}]},
                      "sections": %s
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.fontBody").doesNotExist())
            .andExpect(jsonPath("$.config.brand").doesNotExist());

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", """
                    {
                      "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111","accent":"red"},
                      "font": "PROMPT",
                      "fontBody": "COMIC", "brand": {"logoPosition":"RIGHT"},
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                      "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"Genuine","enabled":true}]},
                      "sections": %s
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.fontBody')].code", hasItem("THEME_FONT_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.brand.logoPosition')].code", hasItem("THEME_TOKEN_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.colors.accent')].code", hasItem("THEME_COLOR_INVALID")));
    }

    @Test
    void saveCustomWithTokensAndStripUnknown() throws Exception {
        var owner = register("theme-custom@example.com", "0815100002");
        long shopId = openShop(owner, "ร้านคัสตอม", "Custom Shop", "1100000000102");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();

        var body = mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("CUSTOM", """
                    {
                      "evilTop": true,
                      "colors": {
                        "primary": "#112233",
                        "secondary": "#445566",
                        "background": "#ffffff",
                        "text": "#111111",
                        "accent": "#ff6600",
                        "surface": "#f5f5f5",
                        "muted": "#888888",
                        "ctaBackground": "#112233",
                        "ctaText": "#ffffff",
                        "border": "#dddddd",
                        "hackColor": "#000000"
                      },
                      "font": "NOTO_SANS_THAI",
                      "style": {
                        "radius": 12,
                        "cardStyle": "BORDERED",
                        "density": "COMFORTABLE",
                        "buttonShape": "ROUNDED",
                        "shadow": "SOFT",
                        "headingTextShadow": true,
                        "evilStyle": 1
                      },
                      "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
                      "sections": %s,
                      "gradients": {
                        "hero": {"angle":90,"stops":[{"color":"#112233","at":0},{"color":"#445566","at":100}]},
                        "evilSlot": {"angle":0,"stops":[{"color":"#000000","at":0},{"color":"#ffffff","at":100}]}
                      },
                      "productCard": {
                        "imageRatio": "PORTRAIT",
                        "ctaStyle": "PILL",
                        "showPrice": true,
                        "edgeToEdge": false,
                        "titleAlign": "CENTER",
                        "evilCard": true
                      },
                      "sectionSettings": {
                        "FEATURED": {"background":"SURFACE","align":"CENTER","columns":3,"paddingY":"LARGE","evil":1},
                        "UNKNOWN_SECTION": {"background":"PRIMARY"}
                      }
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.templateKey").value("CUSTOM"))
            .andExpect(jsonPath("$.config.colors.accent").value("#ff6600"))
            .andExpect(jsonPath("$.config.colors.surface").value("#f5f5f5"))
            .andExpect(jsonPath("$.config.colors.ctaBackground").value("#112233"))
            .andExpect(jsonPath("$.config.style.shadow").value("SOFT"))
            .andExpect(jsonPath("$.config.style.headingTextShadow").value(true))
            .andExpect(jsonPath("$.config.gradients.hero.angle").value(90))
            .andExpect(jsonPath("$.config.productCard.imageRatio").value("PORTRAIT"))
            .andExpect(jsonPath("$.config.productCard.titleAlign").value("CENTER"))
            .andExpect(jsonPath("$.config.sectionSettings.FEATURED.columns").value(3))
            .andExpect(jsonPath("$.config.evilTop").doesNotExist())
            .andExpect(jsonPath("$.config.colors.hackColor").doesNotExist())
            .andExpect(jsonPath("$.config.style.evilStyle").doesNotExist())
            .andExpect(jsonPath("$.config.gradients.evilSlot").doesNotExist())
            .andExpect(jsonPath("$.config.productCard.evilCard").doesNotExist())
            .andExpect(jsonPath("$.config.sectionSettings.FEATURED.evil").doesNotExist())
            .andExpect(jsonPath("$.config.sectionSettings.UNKNOWN_SECTION").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("evilTop"));
        assertFalse(body.contains("hackColor"));
        assertFalse(body.contains("UNKNOWN_SECTION"));

        mvc.perform(get("/api/shops/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.theme.templateKey").value("CUSTOM"))
            .andExpect(jsonPath("$.theme.config.gradients.hero.stops[0].color").value("#112233"))
            .andExpect(jsonPath("$.theme.config.productCard.ctaStyle").value("PILL"))
            .andExpect(jsonPath("$.theme.config.sectionSettings.FEATURED.background").value("SURFACE"))
            .andExpect(jsonPath("$.theme.config.colors.muted").value("#888888"));
    }

    @Test
    void invalidGradientHexAndEnumReturn400() throws Exception {
        var owner = register("theme-bad@example.com", "0815100003");
        long shopId = openShop(owner, "ร้านผิด", "Bad Theme Shop", "1100000000103");

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("CUSTOM", """
                    {
                      "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
                      "font": "NOTO_SANS_THAI",
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                      "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
                      "sections": %s,
                      "gradients": {"hero":"linear-gradient(90deg,#000,#fff)"}
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.gradients.hero')].code", hasItem("THEME_GRADIENT_INVALID")));

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("CUSTOM", """
                    {
                      "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
                      "font": "NOTO_SANS_THAI",
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                      "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
                      "sections": %s,
                      "gradients": {"hero":{"angle":400,"stops":[{"color":"#112233","at":0},{"color":"#445566","at":100}]}}
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.gradients.hero.angle')].code", hasItem("THEME_GRADIENT_INVALID")));

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("CUSTOM", """
                    {
                      "colors": {
                        "primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111",
                        "accent":"red"
                      },
                      "font": "NOTO_SANS_THAI",
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                      "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
                      "sections": %s
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.colors.accent')].code", hasItem("THEME_COLOR_INVALID")));

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("CUSTOM", """
                    {
                      "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
                      "font": "NOTO_SANS_THAI",
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED","shadow":"HUGE"},
                      "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
                      "sections": %s,
                      "productCard": {"imageRatio":"WIDE"}
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.style.shadow')].code", hasItem("THEME_TOKEN_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.productCard.imageRatio')].code", hasItem("THEME_TOKEN_INVALID")));
    }

    @Test
    void generalTemplateStillSavesAndReads() throws Exception {
        var owner = register("theme-general@example.com", "0815100004");
        long shopId = openShop(owner, "ร้านทั่วไป", "General Theme Shop", "1100000000104");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", """
                    {
                      "colors": {"primary":"#087bf5","secondary":"#08245d","background":"#ffffff","text":"#132b50"},
                      "font": "NOTO_SANS_THAI",
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                      "hero": {"layout":"FULL","title":"สวัสดี","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
                      "sections": %s
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.templateKey").value("GENERAL"))
            .andExpect(jsonPath("$.config.hero.title").value("สวัสดี"));

        mvc.perform(get("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.templateKey").value("GENERAL"))
            .andExpect(jsonPath("$.config.colors.primary").value("#087bf5"));

        mvc.perform(get("/api/shops/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.theme.templateKey").value("GENERAL"))
            .andExpect(jsonPath("$.theme.config.hero.title").value("สวัสดี"));
    }

    @Test
    void generalPresetFollowsThemesSpecAndKeepsSpecColorRoles() throws Exception {
        var owner = register("theme-general-spec@example.com", "0815100014");
        long shopId = openShop(owner, "ร้านสเปก", "General Spec Shop", "1100000000114");
        approve(shopId);

        mvc.perform(get("/api/merchant/settings/theme/presets?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.templateKey=='GENERAL')].config.colors.primary", hasItem("#0a5fd6")))
            .andExpect(jsonPath("$[?(@.templateKey=='GENERAL')].config.colors.sale", hasItem("#c4271b")))
            .andExpect(jsonPath("$[?(@.templateKey=='GENERAL')].config.font", hasItem("IBM_PLEX_SANS_THAI")))
            .andExpect(jsonPath("$[?(@.templateKey=='GENERAL')].config.hero.title", hasItem("ของดีทั่วไทย ส่งตรงถึงบ้านคุณ")))
            .andExpect(jsonPath("$[?(@.templateKey=='GENERAL')].config.hero.ctaSecondaryLabel", hasItem("ดูหมวดหมู่")))
            .andExpect(jsonPath("$[?(@.templateKey=='GENERAL')].config.sections[*].key", contains("HERO", "TRUST", "CATEGORIES", "FEATURED", "BANNERS", "BESTSELLERS", "INTRO", "CATALOG", "NEW_ARRIVALS", "HOURS")))
            .andExpect(jsonPath("$[?(@.templateKey=='CUSTOM')].config.font", hasItem("NOTO_SANS_THAI")))
            .andExpect(jsonPath("$[?(@.templateKey=='FASHION')].config.colors.secondary", hasItem("#f1efea")))
            .andExpect(jsonPath("$[?(@.templateKey=='FASHION')].config.colors.accent", hasItem("#e3ff3d")))
            .andExpect(jsonPath("$[?(@.templateKey=='FASHION')].config.colors.sale", hasItem("#c8102e")))
            .andExpect(jsonPath("$[?(@.templateKey=='FASHION')].config.font", hasItem("PROMPT")))
            .andExpect(jsonPath("$[?(@.templateKey=='FASHION')].config.style.buttonShape", hasItem("PILL")))
            .andExpect(jsonPath("$[?(@.templateKey=='FASHION')].config.style.radius", hasItem(0)))
            .andExpect(jsonPath("$[?(@.templateKey=='FASHION')].config.sections[*].key", contains("HERO", "NEW_ARRIVALS", "BANNERS", "CATEGORIES", "BESTSELLERS", "INTRO", "TRUST", "FEATURED", "CATALOG", "HOURS")));

        var config = """
            {
              "colors": {"primary":"#0a5fd6","secondary":"#08245d","background":"#ffffff","text":"#132b50",
                         "borderStrong":"#7d8ba0","sale":"%s","focus":"#0a5fd6"},
              "font": "IBM_PLEX_SANS_THAI",
              "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
              "hero": %s,
              "banners": [],
              "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
              "sections": %s
            }
            """;
        var heroCopy = """
            {"layout":"FULL","eyebrow":"ส่งตรงจากสวน","title":"ทุเรียนหมอนทอง","subtitle":"สดทุกวัน",
             "ctaLabel":"สั่งเลย","ctaLink":"","ctaSecondaryLabel":"ดูโปร","ctaSecondaryLink":"/shops"}""";
        var heroMarkup = heroCopy.replace("ส่งตรงจากสวน", "<b>x</b>");
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", config.formatted("#c4271b", heroCopy, SECTIONS))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.colors.sale").value("#c4271b"))
            .andExpect(jsonPath("$.config.colors.borderStrong").value("#7d8ba0"))
            .andExpect(jsonPath("$.config.hero.eyebrow").value("ส่งตรงจากสวน"))
            .andExpect(jsonPath("$.config.hero.ctaSecondaryLabel").value("ดูโปร"))
            .andExpect(jsonPath("$.config.hero.ctaSecondaryLink").value("/shops"));

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", config.formatted("#c4271b", heroMarkup, SECTIONS))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.eyebrow')].code", hasItem("THEME_HERO_TEXT_INVALID")));

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", config.formatted("red", heroCopy, SECTIONS))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.colors.sale')].code", hasItem("THEME_COLOR_INVALID")));

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("FASHION", config.formatted("red", heroCopy, SECTIONS))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.colors.sale')].code", hasItem("THEME_COLOR_INVALID")));

        // FASHION shares the themes-spec palette roles; GENERAL-only hero copy is still dropped.
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("FASHION", config.formatted("#c8102e", heroCopy, SECTIONS))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.colors.sale").value("#c8102e"))
            .andExpect(jsonPath("$.config.colors.borderStrong").value("#7d8ba0"))
            .andExpect(jsonPath("$.config.hero.eyebrow").doesNotExist());

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("FOOD_BEVERAGE", config.formatted("red", heroCopy, SECTIONS))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.colors.sale')].code", hasItem("THEME_COLOR_INVALID")));

        // FOOD_BEVERAGE joined the themes-spec tree: palette roles are kept, GENERAL-only hero copy is dropped.
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("FOOD_BEVERAGE", config.formatted("#a63a17", heroCopy, SECTIONS))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.colors.sale").value("#a63a17"))
            .andExpect(jsonPath("$.config.colors.borderStrong").value("#7d8ba0"))
            .andExpect(jsonPath("$.config.hero.eyebrow").doesNotExist())
            .andExpect(jsonPath("$.config.hero.ctaSecondaryLabel").doesNotExist());
    }

    @Test
    void oldTemplatesAcceptSemicolonHeroAndSilentlyDropAg1Tokens() throws Exception {
        var owner = register("theme-legacy@example.com", "0815100005");
        long shopId = openShop(owner, "ร้านเดิม", "Legacy Theme Shop", "1100000000105");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();

        for (var template : List.of("GENERAL", "FASHION")) {
            var body = mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                    .header("Authorization", bearer(owner.token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(themePayload(template, """
                        {
                          "colors": {
                            "primary":"#087bf5","secondary":"#08245d","background":"#ffffff","text":"#132b50",
                            "surface":"red","muted":"url(x)","ctaBackground":"#000000","ctaText":"#ffffff","border":"#dddddd"
                          },
                          "font": "NOTO_SANS_THAI",
                          "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED",
                                    "shadow":"HUGE","headingTextShadow":"yes"},
                          "hero": {"layout":"FULL","title":"ลด 50 บาท; วันนี้เท่านั้น","subtitle":"ส่งฟรี; ทั่วไทย","ctaLabel":"ช้อปเลย;","ctaLink":""},
                          "banners": [],
                          "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
                          "sections": %s,
                          "gradients": {"hero":"linear-gradient(90deg,#000,#fff)"},
                          "productCard": {"imageRatio":"WIDE"},
                          "sectionSettings": {"UNKNOWN_SECTION":{"background":"PRIMARY"},"FEATURED":{"columns":9}}
                        }
                        """.formatted(SECTIONS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateKey").value(template))
                .andExpect(jsonPath("$.config.hero.title").value("ลด 50 บาท; วันนี้เท่านั้น"))
                .andExpect(jsonPath("$.config.hero.subtitle").value("ส่งฟรี; ทั่วไทย"))
                .andExpect(jsonPath("$.config.hero.ctaLabel").value("ช้อปเลย;"))
                .andExpect(jsonPath("$.config.gradients").doesNotExist())
                .andExpect(jsonPath("$.config.productCard").doesNotExist())
                .andExpect(jsonPath("$.config.sectionSettings").doesNotExist())
                .andExpect(jsonPath("$.config.colors.surface").doesNotExist())
                .andExpect(jsonPath("$.config.colors.muted").doesNotExist())
                .andExpect(jsonPath("$.config.colors.ctaBackground").doesNotExist())
                .andExpect(jsonPath("$.config.colors.ctaText").doesNotExist())
                .andExpect(jsonPath("$.config.colors.border").doesNotExist())
                .andExpect(jsonPath("$.config.style.shadow").doesNotExist())
                .andExpect(jsonPath("$.config.style.headingTextShadow").doesNotExist())
                .andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("UNKNOWN_SECTION"));

            mvc.perform(get("/api/shops/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme.templateKey").value(template))
                .andExpect(jsonPath("$.theme.config.hero.title").value("ลด 50 บาท; วันนี้เท่านั้น"))
                .andExpect(jsonPath("$.theme.config.gradients").doesNotExist())
                .andExpect(jsonPath("$.theme.config.productCard").doesNotExist());
        }
    }

    @Test
    void customStripsGradientAndSectionSettingInternalsAndAllowsSemicolonHero() throws Exception {
        var owner = register("theme-custom-strip@example.com", "0815100006");
        long shopId = openShop(owner, "ร้านคัสตอมสอง", "Custom Strip Shop", "1100000000106");

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("CUSTOM", """
                    {
                      "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
                      "font": "NOTO_SANS_THAI",
                      "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
                      "hero": {"layout":"FULL","title":"โปร; พิเศษ","subtitle":"","ctaLabel":"","ctaLink":""},
                      "banners": [],
                      "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
                      "sections": %s,
                      "gradients": {"hero": {"angle":45,"css":"url(evil)","stops":[
                        {"color":"#112233","at":0,"evil":"x"},{"color":"#445566","at":100,"opacity":1}]}},
                      "sectionSettings": {"NOPE":{"background":"SURFACE"},"CATALOG":{"columns":4,"junk":true}}
                    }
                    """.formatted(SECTIONS))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.hero.title").value("โปร; พิเศษ"))
            .andExpect(jsonPath("$.config.gradients.hero.angle").value(45))
            .andExpect(jsonPath("$.config.gradients.hero.css").doesNotExist())
            .andExpect(jsonPath("$.config.gradients.hero.stops[0].evil").doesNotExist())
            .andExpect(jsonPath("$.config.gradients.hero.stops[1].opacity").doesNotExist())
            .andExpect(jsonPath("$.config.gradients.hero.stops[1].color").value("#445566"))
            .andExpect(jsonPath("$.config.sectionSettings.NOPE").doesNotExist())
            .andExpect(jsonPath("$.config.sectionSettings.CATALOG.columns").value(4))
            .andExpect(jsonPath("$.config.sectionSettings.CATALOG.junk").doesNotExist());
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

    private Account platformAdmin(String email, String phone) throws Exception {
        var account = register(email, phone);
        platformRoles.save(PlatformRole.grant(account.id, PlatformRole.PLATFORM_ADMIN, null, "test"));
        return account;
    }

    private void approve(long shopId) throws Exception {
        var admin = platformAdmin("theme-admin-" + shopId + "@example.com", "082" + String.format("%07d", shopId % 10000000));
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve")
                .header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk());
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
