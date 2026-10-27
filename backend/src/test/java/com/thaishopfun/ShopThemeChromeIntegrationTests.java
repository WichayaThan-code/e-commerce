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
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** AG5: CUSTOM hero MEDIA_FULL, header, footer and custom fonts (CUSTOM only; every other template strips them). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopThemeChromeIntegrationTests {
    private static final String SECTIONS = """
        [
          {"key":"HERO","enabled":true,"variant":"__HERO__"},
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
        wipe();
    }

    @AfterEach
    void cleanupAfter() {
        themes.deleteAll();
    }

    private void wipe() {
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

    // ───────────────────────── CUSTOM round trip ─────────────────────────

    @Test
    void customSavesMediaFullHeroHeaderFooterAndFontsAndReadsThemeBack() throws Exception {
        var owner = register("ag5-valid@example.com", "0818500001");
        long shopId = openShop(owner, "ร้านเอจีห้า", "AG5 Valid", "1100000000501");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();
        saveCustom(owner, shopId, heroPlain("FULL"), "").andExpect(status().isOk());

        var video = upload(owner, shopId, "VIDEO", mp4(1));
        var poster = uploadImage(owner, shopId, 0x112233);
        var sticker = uploadImage(owner, shopId, 0x445566);
        var headerBg = uploadImage(owner, shopId, 0x778899);
        var headingFont = upload(owner, shopId, "FONT", woff2(1));
        var bodyFont = upload(owner, shopId, "FONT", woff2(2));

        var hero = """
            {"layout":"MEDIA_FULL","title":"โปรโมชันใหญ่","subtitle":"ลดทั้งร้าน","ctaLabel":"ช้อปเลย","ctaLink":"/shops",
             "background":{"type":"VIDEO","mediaId":"%s","posterMediaId":"%s"},
             "overlay":"35","heightVh":80,"textAlign":"CENTER","headlineSize":"XL",
             "stickers":[
               {"mediaId":"%s","x":12,"y":80,"size":140,"rotate":-12,"hideOnMobile":true},
               {"mediaId":"%s","x":"90","y":"10","size":60,"rotate":30,"hideOnMobile":false,"evil":"<script>"}],
             "ctaSecondary":{"label":"ดูโปรโมชัน","link":"https://example.com/sale"}}
            """.formatted(video, poster, sticker, sticker);
        var extra = """
            ,"header":{"layout":"LOGO_CENTER","background":"IMAGE","backgroundMediaId":"%s","overlay":40,"sticky":true,
               "menu":[{"label":{"th":"หน้าแรก","en":"Home"},"link":"/shops"},{"label":"สินค้า","link":"https://example.com/products"}],
               "showSearch":true,"showSocialIcons":true,
               "announcementBar":{"text":"ส่งฟรีทั้งร้าน","link":"/shops","backgroundColor":"#111111","textColor":"#FFFFFF"},
               "ownerPhone":"0899999999"}
            ,"footer":{"columns":[{"title":"ช่วยเหลือ","links":[{"label":"ติดต่อ","link":"/shops"}]},{"title":"","links":[]}],
               "about":{"th":"เกี่ยวกับเรา","en":"About us"},"showContacts":true,"showSocial":true,"background":"GRADIENT",
               "gradient":{"angle":180,"stops":[{"color":"#111111","at":0},{"color":"#222222","at":100}]},
               "copyright":"© 2026 ร้านทดสอบ","ownerEmail":"owner@example.com"}
            ,"fontCustom":{"headingMediaId":"%s","bodyMediaId":"%s","fallback":"MITR"}
            """.formatted(headerBg, headingFont, bodyFont);

        saveCustom(owner, shopId, hero, extra)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.hero.layout").value("MEDIA_FULL"))
            .andExpect(jsonPath("$.config.sections[0].variant").value("MEDIA_FULL"))
            .andExpect(jsonPath("$.config.layout.blocks[0].variant").value("MEDIA_FULL"))
            .andExpect(jsonPath("$.config.hero.background.type").value("VIDEO"))
            .andExpect(jsonPath("$.config.hero.background.mediaId").value(video))
            .andExpect(jsonPath("$.config.hero.background.posterMediaId").value(poster))
            .andExpect(jsonPath("$.config.hero.background.url").value("/api/shops/" + slug + "/theme-media/" + video))
            .andExpect(jsonPath("$.config.hero.background.posterUrl").value("/api/shops/" + slug + "/theme-media/" + poster))
            .andExpect(jsonPath("$.config.hero.overlay").value(35))
            .andExpect(jsonPath("$.config.hero.heightVh").value(80))
            .andExpect(jsonPath("$.config.hero.textAlign").value("CENTER"))
            .andExpect(jsonPath("$.config.hero.headlineSize").value("XL"))
            .andExpect(jsonPath("$.config.hero.stickers", hasSize(2)))
            .andExpect(jsonPath("$.config.hero.stickers[0].rotate").value(-12))
            .andExpect(jsonPath("$.config.hero.stickers[0].hideOnMobile").value(true))
            .andExpect(jsonPath("$.config.hero.stickers[0].url").value("/api/shops/" + slug + "/theme-media/" + sticker))
            .andExpect(jsonPath("$.config.hero.stickers[1].x").value(90))
            .andExpect(jsonPath("$.config.hero.stickers[1].evil").doesNotExist())
            .andExpect(jsonPath("$.config.hero.ctaSecondary.label").value("ดูโปรโมชัน"))
            .andExpect(jsonPath("$.config.hero.ctaSecondary.link").value("https://example.com/sale"))
            .andExpect(jsonPath("$.config.header.layout").value("LOGO_CENTER"))
            .andExpect(jsonPath("$.config.header.background").value("IMAGE"))
            .andExpect(jsonPath("$.config.header.backgroundMediaId").value(headerBg))
            .andExpect(jsonPath("$.config.header.backgroundUrl").value("/api/shops/" + slug + "/theme-media/" + headerBg))
            .andExpect(jsonPath("$.config.header.sticky").value(true))
            .andExpect(jsonPath("$.config.header.menu", hasSize(2)))
            .andExpect(jsonPath("$.config.header.menu[0].label.en").value("Home"))
            .andExpect(jsonPath("$.config.header.menu[1].label.th").value("สินค้า"))
            .andExpect(jsonPath("$.config.header.showSearch").value(true))
            .andExpect(jsonPath("$.config.header.announcementBar.text.th").value("ส่งฟรีทั้งร้าน"))
            .andExpect(jsonPath("$.config.header.announcementBar.backgroundColor").value("#111111"))
            .andExpect(jsonPath("$.config.header.ownerPhone").doesNotExist())
            .andExpect(jsonPath("$.config.footer.columns", hasSize(1)))
            .andExpect(jsonPath("$.config.footer.columns[0].links[0].link").value("/shops"))
            .andExpect(jsonPath("$.config.footer.about.en").value("About us"))
            .andExpect(jsonPath("$.config.footer.showContacts").value(true))
            .andExpect(jsonPath("$.config.footer.background").value("GRADIENT"))
            .andExpect(jsonPath("$.config.footer.gradient.stops", hasSize(2)))
            .andExpect(jsonPath("$.config.footer.copyright").value("© 2026 ร้านทดสอบ"))
            .andExpect(jsonPath("$.config.footer.ownerEmail").doesNotExist())
            .andExpect(jsonPath("$.config.fontCustom.fallback").value("MITR"))
            .andExpect(jsonPath("$.config.fontCustom.headingUrl").value("/api/shops/" + slug + "/theme-media/" + headingFont))
            .andExpect(jsonPath("$.config.fontCustom.bodyUrl").value("/api/shops/" + slug + "/theme-media/" + bodyFont));

        // Merchant GET and the public shop both return the same chrome; derived URLs are never stored.
        mvc.perform(get("/api/merchant/settings/theme?shopId=" + shopId).header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.hero.background.mediaId").value(video))
            .andExpect(jsonPath("$.config.header.menu", hasSize(2)));
        mvc.perform(get("/api/shops/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.theme.config.header.layout").value("LOGO_CENTER"))
            .andExpect(jsonPath("$.theme.config.footer.columns", hasSize(1)))
            .andExpect(jsonPath("$.theme.config.fontCustom.headingMediaId").value(headingFont))
            .andExpect(jsonPath("$.theme.config.hero.stickers", hasSize(2)));
        var stored = themes.findAll().get(0).getConfig().toString();
        assertFalse(stored.contains("posterUrl"), "derived URLs must not be persisted");
        assertFalse(stored.contains("headingUrl"), "derived URLs must not be persisted");

        // A used font / video cannot be deleted from the library.
        for (var inUse : List.of(video, headingFont, sticker, headerBg)) {
            mvc.perform(delete("/api/merchant/settings/theme/media/" + inUse + "?shopId=" + shopId)
                    .header("Authorization", bearer(owner.token)))
                .andExpect(status().isConflict());
        }
    }

    @Test
    void minimalChromeGetsDefaultsAndEmptyFontCustomIsDropped() throws Exception {
        var owner = register("ag5-min@example.com", "0818500002");
        long shopId = openShop(owner, "ร้านเอจีห้าน้อย", "AG5 Minimal", "1100000000502");
        approve(shopId);

        saveCustom(owner, shopId, heroPlain("FULL").replace("\"ctaLink\":\"\"", "\"ctaLink\":\"\",\"stickers\":[],\"ctaSecondary\":{\"label\":\"\",\"link\":\"\"}"), """
            ,"header":{}
            ,"footer":{}
            ,"fontCustom":{"fallback":"PROMPT"}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.header.layout").value("LOGO_LEFT"))
            .andExpect(jsonPath("$.config.header.background").value("NONE"))
            .andExpect(jsonPath("$.config.header.sticky").value(false))
            .andExpect(jsonPath("$.config.header.menu", hasSize(0)))
            .andExpect(jsonPath("$.config.header.announcementBar").doesNotExist())
            .andExpect(jsonPath("$.config.footer.columns", hasSize(0)))
            .andExpect(jsonPath("$.config.footer.showContacts").value(false))
            .andExpect(jsonPath("$.config.footer.background").value("NONE"))
            .andExpect(jsonPath("$.config.fontCustom").doesNotExist())
            .andExpect(jsonPath("$.config.hero.stickers", hasSize(0)))
            .andExpect(jsonPath("$.config.hero.ctaSecondary").doesNotExist());
    }

    // ───────────────────────── numeric ranges ─────────────────────────

    @Test
    void numericRangesAndLimitsAreValidated() throws Exception {
        var owner = register("ag5-range@example.com", "0818500003");
        long shopId = openShop(owner, "ร้านช่วงค่า", "AG5 Range", "1100000000503");
        approve(shopId);
        saveCustom(owner, shopId, heroPlain("FULL"), "").andExpect(status().isOk());
        var image = uploadImage(owner, shopId, 0x010203);

        expectHeroError(owner, shopId, "\"overlay\":81", "config.hero.overlay", "THEME_TOKEN_INVALID");
        expectHeroError(owner, shopId, "\"overlay\":-1", "config.hero.overlay", "THEME_TOKEN_INVALID");
        expectHeroError(owner, shopId, "\"overlay\":\"abc\"", "config.hero.overlay", "THEME_TOKEN_INVALID");
        expectHeroError(owner, shopId, "\"heightVh\":49", "config.hero.heightVh", "THEME_TOKEN_INVALID");
        expectHeroError(owner, shopId, "\"heightVh\":101", "config.hero.heightVh", "THEME_TOKEN_INVALID");
        expectHeroError(owner, shopId, "\"textAlign\":\"RIGHT\"", "config.hero.textAlign", "THEME_TOKEN_INVALID");
        expectHeroError(owner, shopId, "\"headlineSize\":\"M\"", "config.hero.headlineSize", "THEME_TOKEN_INVALID");
        expectHeroError(owner, shopId, "\"background\":{\"type\":\"GIF\"}", "config.hero.background.type", "THEME_TOKEN_INVALID");
        expectHeroError(owner, shopId, "\"background\":{\"type\":\"IMAGE\"}", "config.hero.background.mediaId", "THEME_MEDIA_INVALID");
        expectHeroError(owner, shopId, "\"background\":{\"type\":\"VIDEO\",\"mediaId\":\"not-a-uuid\"}", "config.hero.background.mediaId", "THEME_MEDIA_INVALID");

        String[] badStickers = {
            "\"x\":101", "\"x\":-1", "\"y\":101", "\"size\":39", "\"size\":241", "\"rotate\":31", "\"rotate\":-31", "\"hideOnMobile\":\"yes\"",
        };
        for (var bad : badStickers) {
            var key = bad.substring(1, bad.indexOf('"', 1));
            expectHeroError(owner, shopId, "\"stickers\":[{\"mediaId\":\"" + image + "\"," + bad + "}]",
                "config.hero.stickers[0]." + key, "THEME_TOKEN_INVALID");
        }
        var seven = String.join(",", java.util.Collections.nCopies(7, "{\"mediaId\":\"" + image + "\"}"));
        expectHeroError(owner, shopId, "\"stickers\":[" + seven + "]", "config.hero.stickers", "THEME_TOKEN_INVALID");
        expectHeroError(owner, shopId, "\"stickers\":[{\"x\":10}]", "config.hero.stickers[0].mediaId", "THEME_MEDIA_INVALID");

        // Boundary values are accepted (6 stickers, edges of every range).
        var six = String.join(",", java.util.Collections.nCopies(6, "{\"mediaId\":\"" + image + "\",\"x\":0,\"y\":100,\"size\":240,\"rotate\":-30}"));
        saveCustom(owner, shopId, hero("MEDIA_FULL", "\"overlay\":80,\"heightVh\":50,\"stickers\":[" + six + "]"), "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.hero.stickers", hasSize(6)))
            .andExpect(jsonPath("$.config.hero.overlay").value(80));
        saveCustom(owner, shopId, hero("MEDIA_FULL", "\"overlay\":0,\"heightVh\":100,\"stickers\":[{\"mediaId\":\"" + image + "\",\"size\":40,\"rotate\":30}]"), "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.hero.heightVh").value(100));

        var menu9 = String.join(",", java.util.Collections.nCopies(9, "{\"label\":\"a\",\"link\":\"/shops\"}"));
        expectChromeError(owner, shopId, ",\"header\":{\"menu\":[" + menu9 + "]}", "config.header.menu", "THEME_CHROME_INVALID");
        expectChromeError(owner, shopId, ",\"header\":{\"layout\":\"LOGO_RIGHT\"}", "config.header.layout", "THEME_TOKEN_INVALID");
        expectChromeError(owner, shopId, ",\"header\":{\"background\":\"VIDEO\"}", "config.header.background", "THEME_TOKEN_INVALID");
        expectChromeError(owner, shopId, ",\"header\":{\"background\":\"IMAGE\"}", "config.header.backgroundMediaId", "THEME_MEDIA_INVALID");
        expectChromeError(owner, shopId, ",\"header\":{\"overlay\":81}", "config.header.overlay", "THEME_TOKEN_INVALID");
        expectChromeError(owner, shopId, ",\"header\":{\"sticky\":\"yes\"}", "config.header.sticky", "THEME_TOKEN_INVALID");
        expectChromeError(owner, shopId, ",\"header\":{\"background\":\"GRADIENT\",\"gradient\":{\"angle\":999,\"stops\":[]}}",
            "config.header.gradient.angle", "THEME_GRADIENT_INVALID");
        expectChromeError(owner, shopId, ",\"header\":{\"announcementBar\":{\"text\":\"hi\",\"backgroundColor\":\"red\",\"textColor\":\"#FFFFFF\"}}",
            "config.header.announcementBar.backgroundColor", "THEME_COLOR_INVALID");
        expectChromeError(owner, shopId, ",\"header\":{\"announcementBar\":{\"text\":\"hi\",\"backgroundColor\":\"#000000\",\"textColor\":\"url(x)\"}}",
            "config.header.announcementBar.textColor", "THEME_COLOR_INVALID");
        expectChromeError(owner, shopId, ",\"header\":{\"menu\":[{\"label\":\"x\"}]}", "config.header.menu[0].link", "THEME_BLOCK_LINK_INVALID");
        expectChromeError(owner, shopId, ",\"header\":{\"menu\":[{\"link\":\"/shops\"}]}", "config.header.menu[0].label", "THEME_CHROME_INVALID");

        var column = "{\"title\":\"t\",\"links\":[]}";
        var columns5 = String.join(",", java.util.Collections.nCopies(5, column));
        expectChromeError(owner, shopId, ",\"footer\":{\"columns\":[" + columns5 + "]}", "config.footer.columns", "THEME_CHROME_INVALID");
        var links9 = String.join(",", java.util.Collections.nCopies(9, "{\"label\":\"a\",\"link\":\"/shops\"}"));
        expectChromeError(owner, shopId, ",\"footer\":{\"columns\":[{\"title\":\"t\",\"links\":[" + links9 + "]}]}",
            "config.footer.columns[0].links", "THEME_CHROME_INVALID");
        expectChromeError(owner, shopId, ",\"footer\":{\"copyright\":\"" + "x".repeat(201) + "\"}", "config.footer.copyright", "THEME_BLOCK_CONTENT_INVALID");
        expectChromeError(owner, shopId, ",\"footer\":{\"about\":{\"th\":\"" + "x".repeat(501) + "\"}}", "config.footer.about.th", "THEME_BLOCK_CONTENT_INVALID");
        expectChromeError(owner, shopId, ",\"footer\":{\"showSocial\":1}", "config.footer.showSocial", "THEME_TOKEN_INVALID");
        expectChromeError(owner, shopId, ",\"fontCustom\":{\"bodyMediaId\":\"" + image + "\",\"fallback\":\"COMIC_SANS\"}",
            "config.fontCustom.fallback", "THEME_FONT_INVALID");
        expectChromeError(owner, shopId, ",\"header\":\"nope\"", "config.header", "THEME_CHROME_INVALID");
        expectChromeError(owner, shopId, ",\"footer\":[1]", "config.footer", "THEME_CHROME_INVALID");
        expectChromeError(owner, shopId, ",\"fontCustom\":\"Comic\"", "config.fontCustom", "THEME_FONT_CUSTOM_INVALID");
    }

    // ───────────────────────── media kinds ─────────────────────────

    @Test
    void mediaReferencesMustExistBelongToTheShopAndHaveTheRightKind() throws Exception {
        var owner = register("ag5-kinds@example.com", "0818500004");
        long shopId = openShop(owner, "ร้านชนิดสื่อ", "AG5 Kinds", "1100000000504");
        approve(shopId);
        saveCustom(owner, shopId, heroPlain("FULL"), "").andExpect(status().isOk());
        var image = uploadImage(owner, shopId, 0x0a0a0a);
        var video = upload(owner, shopId, "VIDEO", mp4(7));
        var font = upload(owner, shopId, "FONT", woff2(7));
        var missing = UUID.randomUUID().toString();

        // IMAGE background must be an IMAGE; VIDEO background must be a VIDEO.
        expectHeroError(owner, shopId, bg("IMAGE", video), "config.hero.background.mediaId", "THEME_MEDIA_KIND_INVALID");
        expectHeroError(owner, shopId, bg("IMAGE", font), "config.hero.background.mediaId", "THEME_MEDIA_KIND_INVALID");
        expectHeroError(owner, shopId, bg("VIDEO", image), "config.hero.background.mediaId", "THEME_MEDIA_KIND_INVALID");
        expectHeroError(owner, shopId, bg("VIDEO", missing), "config.hero.background.mediaId", "THEME_MEDIA_NOT_FOUND");
        // Poster is an IMAGE.
        expectHeroError(owner, shopId, "\"background\":{\"type\":\"VIDEO\",\"mediaId\":\"" + video + "\",\"posterMediaId\":\"" + video + "\"}",
            "config.hero.background.posterMediaId", "THEME_MEDIA_KIND_INVALID");
        // Stickers are IMAGEs.
        expectHeroError(owner, shopId, "\"stickers\":[{\"mediaId\":\"" + font + "\"}]", "config.hero.stickers[0].mediaId", "THEME_MEDIA_KIND_INVALID");
        expectHeroError(owner, shopId, "\"stickers\":[{\"mediaId\":\"" + video + "\"}]", "config.hero.stickers[0].mediaId", "THEME_MEDIA_KIND_INVALID");
        expectHeroError(owner, shopId, "\"stickers\":[{\"mediaId\":\"" + missing + "\"}]", "config.hero.stickers[0].mediaId", "THEME_MEDIA_NOT_FOUND");
        // Header / footer backgrounds are IMAGEs.
        expectChromeError(owner, shopId, ",\"header\":{\"background\":\"IMAGE\",\"backgroundMediaId\":\"" + video + "\"}",
            "config.header.backgroundMediaId", "THEME_MEDIA_KIND_INVALID");
        expectChromeError(owner, shopId, ",\"footer\":{\"background\":\"IMAGE\",\"backgroundMediaId\":\"" + missing + "\"}",
            "config.footer.backgroundMediaId", "THEME_MEDIA_NOT_FOUND");
        // Fonts are FONT media only.
        expectChromeError(owner, shopId, ",\"fontCustom\":{\"headingMediaId\":\"" + image + "\",\"fallback\":\"MITR\"}",
            "config.fontCustom.headingMediaId", "THEME_MEDIA_KIND_INVALID");
        expectChromeError(owner, shopId, ",\"fontCustom\":{\"bodyMediaId\":\"" + video + "\",\"fallback\":\"MITR\"}",
            "config.fontCustom.bodyMediaId", "THEME_MEDIA_KIND_INVALID");
        expectChromeError(owner, shopId, ",\"fontCustom\":{\"bodyMediaId\":\"" + missing + "\",\"fallback\":\"MITR\"}",
            "config.fontCustom.bodyMediaId", "THEME_MEDIA_NOT_FOUND");

        // The same ids with the right kinds save fine.
        saveCustom(owner, shopId, hero("MEDIA_FULL", bg("IMAGE", image)), ",\"fontCustom\":{\"headingMediaId\":\"" + font + "\",\"fallback\":\"MITR\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.hero.background.type").value("IMAGE"))
            .andExpect(jsonPath("$.config.fontCustom.bodyMediaId").doesNotExist());

        // Another shop's media is never accepted.
        var other = register("ag5-kinds-other@example.com", "0818500014");
        long otherShop = openShop(other, "ร้านอื่นเอจีห้า", "AG5 Other", "1100000000514");
        approve(otherShop);
        saveCustom(other, otherShop, heroPlain("FULL"), "").andExpect(status().isOk());
        saveCustom(other, otherShop, hero("MEDIA_FULL", bg("IMAGE", image)), "")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.background.mediaId')].code", hasItem("THEME_MEDIA_NOT_FOUND")));
    }

    // ───────────────────────── unsafe links ─────────────────────────

    @Test
    void unsafeLinksAreRejectedEverywhere() throws Exception {
        var owner = register("ag5-links@example.com", "0818500005");
        long shopId = openShop(owner, "ร้านลิงก์", "AG5 Links", "1100000000505");
        approve(shopId);
        saveCustom(owner, shopId, heroPlain("FULL"), "").andExpect(status().isOk());

        for (var bad : List.of("javascript:alert(1)", "http://example.com", "//evil.example.com", "data:text/html,x", "ftp://x.test/a",
            "https://exa mple.com", "/path with space", "https://example.com/<script>")) {
            expectHeroError(owner, shopId, "\"ctaSecondary\":{\"label\":\"x\",\"link\":\"" + bad + "\"}",
                "config.hero.ctaSecondary.link", "THEME_HERO_LINK_INVALID");
            expectChromeError(owner, shopId, ",\"header\":{\"menu\":[{\"label\":\"x\",\"link\":\"" + bad + "\"}]}",
                "config.header.menu[0].link", "THEME_BLOCK_LINK_INVALID");
            expectChromeError(owner, shopId, ",\"header\":{\"announcementBar\":{\"text\":\"x\",\"link\":\"" + bad + "\",\"backgroundColor\":\"#000000\",\"textColor\":\"#ffffff\"}}",
                "config.header.announcementBar.link", "THEME_BLOCK_LINK_INVALID");
            expectChromeError(owner, shopId, ",\"footer\":{\"columns\":[{\"title\":\"c\",\"links\":[{\"label\":\"x\",\"link\":\"" + bad + "\"}]}]}",
                "config.footer.columns[0].links[0].link", "THEME_BLOCK_LINK_INVALID");
        }
        expectHeroError(owner, shopId, "\"ctaSecondary\":{\"label\":\"" + "x".repeat(121) + "\",\"link\":\"/shops\"}",
            "config.hero.ctaSecondary.label", "THEME_HERO_CTA_TOO_LONG");

        // Internal paths and https links are fine.
        saveCustom(owner, shopId, hero("FULL", "\"ctaSecondary\":{\"label\":\"ok\",\"link\":\"/shop/a?x=1#y\"}"), """
            ,"header":{"menu":[{"label":"a","link":"/shops"},{"label":"b","link":"https://example.com/p?q=1"}]}
            """).andExpect(status().isOk());
    }

    // ───────────────────────── other templates ─────────────────────────

    @Test
    void nonCustomTemplatesStripChromeAndFallBackFromMediaFullToFull() throws Exception {
        var owner = register("ag5-strip@example.com", "0818500006");
        long shopId = openShop(owner, "ร้านสลับเทมเพลต", "AG5 Strip", "1100000000506");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();
        saveCustom(owner, shopId, heroPlain("FULL"), "").andExpect(status().isOk());
        var image = uploadImage(owner, shopId, 0x0b0c0d);
        var font = upload(owner, shopId, "FONT", woff2(9));

        var chromeHero = hero("MEDIA_FULL", bg("IMAGE", image)
            + ",\"overlay\":90,\"heightVh\":10,\"stickers\":[{\"mediaId\":\"" + image + "\",\"x\":500}],\"ctaSecondary\":{\"label\":\"x\",\"link\":\"javascript:alert(1)\"}");
        var chromeExtra = """
            ,"header":{"layout":"NOPE","menu":[{"label":"x","link":"javascript:alert(1)"}]}
            ,"footer":{"columns":"nope"}
            ,"fontCustom":{"headingMediaId":"%s","fallback":"MITR"}
            """.formatted(font);

        for (var template : List.of("GENERAL", "FASHION", "FOOD_BEVERAGE")) {
            mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                    .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateKey\":\"" + template + "\",\"config\":" + baseConfig(chromeHero, "MEDIA_FULL", chromeExtra) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateKey").value(template))
                .andExpect(jsonPath("$.config.header").doesNotExist())
                .andExpect(jsonPath("$.config.footer").doesNotExist())
                .andExpect(jsonPath("$.config.fontCustom").doesNotExist())
                .andExpect(jsonPath("$.config.layout").doesNotExist())
                .andExpect(jsonPath("$.config.hero.layout").value("FULL"))
                .andExpect(jsonPath("$.config.hero.background").doesNotExist())
                .andExpect(jsonPath("$.config.hero.stickers").doesNotExist())
                .andExpect(jsonPath("$.config.hero.ctaSecondary").doesNotExist())
                .andExpect(jsonPath("$.config.hero.overlay").doesNotExist())
                .andExpect(jsonPath("$.config.hero.heightVh").doesNotExist())
                .andExpect(jsonPath("$.config.sections[0].variant").value("FULL"));

            mvc.perform(get("/api/shops/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme.templateKey").value(template))
                .andExpect(jsonPath("$.theme.config.header").doesNotExist())
                .andExpect(jsonPath("$.theme.config.footer").doesNotExist())
                .andExpect(jsonPath("$.theme.config.fontCustom").doesNotExist())
                .andExpect(jsonPath("$.theme.config.hero.layout").value("FULL"))
                .andExpect(jsonPath("$.theme.config.hero.stickers").doesNotExist());
        }

        // Switching away freed the media (nothing references it any more).
        mvc.perform(delete("/api/merchant/settings/theme/media/" + font + "?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)))
            .andExpect(status().isNoContent());
    }

    @Test
    void legacyHeroFullAndStripStillWorkForGeneralAndCustom() throws Exception {
        var owner = register("ag5-legacy@example.com", "0818500007");
        long shopId = openShop(owner, "ร้านฮีโร่เดิม", "AG5 Legacy", "1100000000507");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();

        for (var layout : List.of("FULL", "STRIP")) {
            mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                    .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateKey\":\"GENERAL\",\"config\":" + baseConfig(heroPlain(layout), layout, "") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.hero.layout").value(layout))
                .andExpect(jsonPath("$.config.sections[0].variant").value(layout))
                .andExpect(jsonPath("$.config.hero.title").value("ร้านทดสอบ"));
            mvc.perform(get("/api/shops/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme.config.hero.layout").value(layout));
        }

        for (var layout : List.of("STRIP", "FULL")) {
            saveCustom(owner, shopId, heroPlain(layout), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.hero.layout").value(layout))
                .andExpect(jsonPath("$.config.layout.blocks[0].variant").value(layout))
                .andExpect(jsonPath("$.config.header").doesNotExist())
                .andExpect(jsonPath("$.config.footer").doesNotExist())
                .andExpect(jsonPath("$.config.fontCustom").doesNotExist())
                .andExpect(jsonPath("$.config.hero.background").doesNotExist());
        }

        // Unknown hero layouts are still rejected (MEDIA_FULL is the only new one).
        saveCustom(owner, shopId, heroPlain("WIDE"), "")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.layout')].code", hasItem("THEME_HERO_LAYOUT_INVALID")));
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateKey\":\"GENERAL\",\"config\":" + baseConfig(heroPlain("WIDE"), "WIDE", "") + "}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void mediaFullBlockVariantIsAllowedForHeroBlocksOnly() throws Exception {
        var owner = register("ag5-block@example.com", "0818500008");
        long shopId = openShop(owner, "ร้านบล็อกฮีโร่", "AG5 Block", "1100000000508");
        approve(shopId);

        var heroBlock = "{\"id\":\"hero-1\",\"type\":\"HERO\",\"enabled\":true,\"variant\":\"MEDIA_FULL\",\"settings\":{}}";
        var catalogBlock = "{\"id\":\"cat-1\",\"type\":\"CATALOG\",\"enabled\":true,\"variant\":\"MEDIA_FULL\",\"settings\":{}}";
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateKey\":\"CUSTOM\",\"config\":" + baseConfig(heroPlain("FULL"), "FULL",
                    ",\"layout\":{\"blocks\":[" + heroBlock + "]}") + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout.blocks[0].variant").value("MEDIA_FULL"))
            .andExpect(jsonPath("$.config.sections[0].variant").value("MEDIA_FULL"))
            .andExpect(jsonPath("$.config.hero.layout").value("MEDIA_FULL"));

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateKey\":\"CUSTOM\",\"config\":" + baseConfig(heroPlain("FULL"), "FULL",
                    ",\"layout\":{\"blocks\":[" + catalogBlock + "]}") + "}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.layout.blocks[0].variant')].code", hasItem("THEME_BLOCKS_INVALID")));

        // GENERAL strips layout.blocks entirely, so a MEDIA_FULL block can never leak into another template.
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateKey\":\"GENERAL\",\"config\":" + baseConfig(heroPlain("FULL"), "FULL",
                    ",\"layout\":{\"blocks\":[" + heroBlock + "]}") + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout").doesNotExist())
            .andExpect(jsonPath("$.config.hero.layout").value("FULL"));
    }

    // ───────────────────────── helpers ─────────────────────────

    private static String heroPlain(String layout) {
        return hero(layout, "");
    }

    private static String hero(String layout, String extra) {
        return "{\"layout\":\"" + layout + "\",\"title\":\"ร้านทดสอบ\",\"subtitle\":\"\",\"ctaLabel\":\"\",\"ctaLink\":\"\""
            + (extra == null || extra.isBlank() ? "" : "," + extra) + "}";
    }

    private static String bg(String type, String mediaId) {
        return "\"background\":{\"type\":\"" + type + "\",\"mediaId\":\"" + mediaId + "\"}";
    }

    private void expectHeroError(Account owner, long shopId, String heroExtra, String field, String code) throws Exception {
        saveCustom(owner, shopId, hero("MEDIA_FULL", heroExtra), "")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='" + field + "')].code", hasItem(code)));
    }

    private void expectChromeError(Account owner, long shopId, String extra, String field, String code) throws Exception {
        saveCustom(owner, shopId, heroPlain("FULL"), extra)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='" + field + "')].code", hasItem(code)));
    }

    private ResultActions saveCustom(Account owner, long shopId, String heroJson, String extra) throws Exception {
        var layout = JsonPath.<String>read(heroJson, "$.layout");
        return mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
            .header("Authorization", bearer(owner.token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateKey\":\"CUSTOM\",\"config\":" + baseConfig(heroJson, layout, extra) + "}"));
    }

    private static String baseConfig(String heroJson, String heroVariant, String extra) {
        return """
            {
              "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
              "font": "NOTO_SANS_THAI",
              "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
              "hero": %s,
              "banners": [],
              "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
              "sections": %s
              %s
            }
            """.formatted(heroJson, SECTIONS.replace("__HERO__", heroVariant), extra);
    }

    private String uploadImage(Account owner, long shopId, int rgb) throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) image.setRGB(x, y, rgb);
        var out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", out));
        return upload(owner, shopId, "IMAGE", out.toByteArray(), "ag5.png", "image/png");
    }

    private String upload(Account owner, long shopId, String kind, byte[] bytes) throws Exception {
        var name = "VIDEO".equals(kind) ? "ag5.mp4" : "ag5.woff2";
        var mime = "VIDEO".equals(kind) ? "video/mp4" : "font/woff2";
        return upload(owner, shopId, kind, bytes, name, mime);
    }

    private String upload(Account owner, long shopId, String kind, byte[] bytes, String name, String mime) throws Exception {
        var body = mvc.perform(multipart("/api/merchant/settings/theme/media?shopId=" + shopId)
                .file(new MockMultipartFile("file", name, mime, bytes))
                .param("kind", kind)
                .header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Smallest bytes the upload accepts as MP4 (an {@code ftyp} box at offset 4) plus a distinguishing tail. */
    private static byte[] mp4(int seed) {
        var bytes = new byte[32];
        System.arraycopy(new byte[]{0, 0, 0, 24}, 0, bytes, 0, 4);
        System.arraycopy("ftypisom".getBytes(StandardCharsets.US_ASCII), 0, bytes, 4, 8);
        Arrays.fill(bytes, 12, bytes.length, (byte) seed);
        return bytes;
    }

    /** Smallest bytes the upload accepts as WOFF2 (the {@code wOF2} magic) plus a distinguishing tail. */
    private static byte[] woff2(int seed) {
        var bytes = new byte[32];
        System.arraycopy("wOF2".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        Arrays.fill(bytes, 4, bytes.length, (byte) seed);
        return bytes;
    }

    private record Account(UUID id, String token) {}

    private Account register(String email, String phone) throws Exception {
        var body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
              {"firstName":"ทดสอบ","lastName":"ชุดหัวท้าย","email":"%s","phone":"%s","password":"Password1"}
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
        var admin = platformAdmin("ag5-admin-" + shopId + "@example.com", "085" + String.format("%07d", shopId % 10000000));
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve")
                .header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk());
    }

    private long openShop(Account owner, String shopName, String shopNameEn, String citizenId) throws Exception {
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
              """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"ownerType":"PERSON","title":"นาย","firstName":"ทดสอบ","lastName":"ชุดหัวท้าย","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"089%s","email":"owner-%s@example.com","houseNo":"1","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"ทดสอบ ชุดหัวท้าย"}
              """.formatted(citizenId, citizenId.substring(6), citizenId, citizenId))).andExpect(status().isOk());
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
