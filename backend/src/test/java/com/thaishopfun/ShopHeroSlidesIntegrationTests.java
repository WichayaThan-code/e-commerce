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
import com.thaishopfun.merchant.theme.ShopThemeMediaRepository;
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
import org.springframework.test.web.servlet.RequestBuilder;

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

/** Hero slider: up to 5 shop-media IMAGEs in {@code hero.slides}, for every template. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopHeroSlidesIntegrationTests {
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
    @Autowired ShopThemeMediaRepository themeMedia;

    /** The in-memory H2 is shared across Spring contexts; leave no shops/themes/media behind for later test classes. */
    @AfterEach
    void cleanAfter() {
        clean();
    }

    @BeforeEach
    void clean() {
        themeMedia.deleteAll();
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

    @Test
    void heroSlidesWorkForEveryTemplateMaxFiveInOrder() throws Exception {
        var owner = register("slides-ok@example.com", "0815200101");
        long shopId = openShop(owner, "ร้านสไลด์", "Slides Shop", "1100000000391");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();
        var ids = new java.util.ArrayList<String>();
        for (int i = 0; i < 6; i++) {
            ids.add(JsonPath.read(mvc.perform(upload(owner, shopId, "IMAGE", file("s" + i + ".png", "image/png", png(0x101010 * (i + 1)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id"));
        }
        for (var template : List.of("GENERAL", "FASHION", "FOOD_BEVERAGE", "CUSTOM")) {
            var five = slides(ids.get(2), ids.get(0), ids.get(1), ids.get(3), ids.get(4));
            mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                    .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                    .content(themePayload(template, five)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.hero.slides", hasSize(5)))
                .andExpect(jsonPath("$.config.hero.slides[0].slideMediaId").value(ids.get(2)))
                .andExpect(jsonPath("$.config.hero.slides[0].imageUrl").value("/api/shops/" + slug + "/theme-media/" + ids.get(2)));
            mvc.perform(get("/api/shops/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme.config.hero.slides", hasSize(5)))
                .andExpect(jsonPath("$.theme.config.hero.slides[1].imageUrl").value("/api/shops/" + slug + "/theme-media/" + ids.get(0)));
        }

        // Six slides → 400 TOO_MANY.
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", slides(ids.toArray(String[]::new)))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.slides')].code", hasItem("THEME_HERO_SLIDES_TOO_MANY")));

        // Slides in use cannot be deleted; removing them from the theme frees the media.
        mvc.perform(delete("/api/merchant/settings/theme/media/" + ids.get(0) + "?shopId=" + shopId).header("Authorization", bearer(owner.token)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_IN_USE"));
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", slides())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.hero.slides").doesNotExist());
        mvc.perform(delete("/api/merchant/settings/theme/media/" + ids.get(0) + "?shopId=" + shopId).header("Authorization", bearer(owner.token)))
            .andExpect(status().isNoContent());
    }

    @Test
    void heroSlidesRejectForeignUnknownAndNonImageMedia() throws Exception {
        var owner = register("slides-bad@example.com", "0815200102");
        long shopId = openShop(owner, "ร้านสไลด์สอง", "Slides Shop Two", "1100000000392");
        approve(shopId);
        var other = register("slides-other@example.com", "0815200103");
        long otherShop = openShop(other, "ร้านสไลด์อื่น", "Slides Other", "1100000000393");
        approve(otherShop);
        String foreign = JsonPath.read(mvc.perform(upload(other, otherShop, "IMAGE", file("f.png", "image/png", png(0x333333))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        saveTheme(owner, shopId, "CUSTOM", slides());
        String video = JsonPath.read(mvc.perform(upload(owner, shopId, "VIDEO", file("v.mp4", "video/mp4", mp4())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", slides(foreign, UUID.randomUUID().toString(), "not-a-uuid", video))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.slides[0].slideMediaId')].code", hasItem("THEME_MEDIA_NOT_FOUND")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.slides[1].slideMediaId')].code", hasItem("THEME_MEDIA_NOT_FOUND")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.slides[2].slideMediaId')].code", hasItem("THEME_MEDIA_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.slides[3].slideMediaId')].code", hasItem("THEME_MEDIA_KIND_INVALID")));

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", "{\"bad\":true}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.slides')].code", hasItem("THEME_HERO_SLIDES_INVALID")));
    }

    @Test
    void storyIntroKeepsPlainTextAndShopImageForTsfTemplatesOnly() throws Exception {
        var owner = register("intro-ok@example.com", "0815200104");
        long shopId = openShop(owner, "Intro Shop TH", "Intro Shop", "1100000000394");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();
        String image = JsonPath.read(mvc.perform(upload(owner, shopId, "IMAGE", file("i.png", "image/png", png(0x224466))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        var intro = "{\"eyebrow\":\"Our story\",\"title\":\"Since 1999\",\"body\":\"Family recipes.\",\"ctaLabel\":\"Menu\",\"ctaLink\":\"/products\",\"introMediaId\":\"" + image + "\",\"reverse\":true,\"junk\":1}";
        for (var template : List.of("GENERAL", "FASHION", "FOOD_BEVERAGE")) {
            mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                    .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                    .content(introPayload(template, intro)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.intro.title").value("Since 1999"))
                .andExpect(jsonPath("$.config.intro.reverse").value(true))
                .andExpect(jsonPath("$.config.intro.junk").doesNotExist())
                .andExpect(jsonPath("$.config.intro.imageUrl").value("/api/shops/" + slug + "/theme-media/" + image));
            mvc.perform(get("/api/shops/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme.config.intro.body").value("Family recipes."))
                .andExpect(jsonPath("$.theme.config.intro.introMediaId").value(image));
        }
        // The intro image is in use: it cannot be deleted while the theme references it.
        mvc.perform(delete("/api/merchant/settings/theme/media/" + image + "?shopId=" + shopId).header("Authorization", bearer(owner.token)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_IN_USE"));
        // CUSTOM never carries the StoryBlock.
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(introPayload("CUSTOM", intro)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.intro").doesNotExist());
        mvc.perform(delete("/api/merchant/settings/theme/media/" + image + "?shopId=" + shopId).header("Authorization", bearer(owner.token)))
            .andExpect(status().isNoContent());
    }

    @Test
    void storyIntroRejectsMarkupBadLinksLongTextAndForeignMedia() throws Exception {
        var owner = register("intro-bad@example.com", "0815200105");
        long shopId = openShop(owner, "Intro Bad TH", "Intro Bad", "1100000000395");
        approve(shopId);
        var longBody = "a".repeat(1001);
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(introPayload("FOOD_BEVERAGE", "{\"title\":\"<b>x</b>\",\"body\":\"" + longBody + "\",\"ctaLink\":\"javascript:alert(1)\",\"reverse\":\"yes\"}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.intro.title')].code", hasItem("THEME_INTRO_TEXT_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.intro.body')].code", hasItem("THEME_INTRO_TEXT_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.intro.ctaLink')].code", hasItem("THEME_INTRO_LINK_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.intro.reverse')].code", hasItem("THEME_INTRO_TEXT_INVALID")));
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(introPayload("GENERAL", "{\"title\":\"ok\",\"ctaLink\":\"http://example.com/a\",\"introMediaId\":\"" + UUID.randomUUID() + "\"}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.intro.introMediaId')].code", hasItem("THEME_MEDIA_NOT_FOUND")));
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(introPayload("GENERAL", "{\"title\":\"ok\",\"ctaLink\":\"http://example.com/a\"}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.intro.ctaLink").value("http://example.com/a"));
    }

    /** Item 2: SPLIT / POSTER hero layouts are valid (and kept, with slides) for GENERAL / FASHION / FOOD_BEVERAGE. */
    @Test
    void heroSplitAndPosterLayoutsAreKeptForEveryTsfTemplate() throws Exception {
        var owner = register("hero-split@example.com", "0815200844");
        long shopId = openShop(owner, "ร้านแบ่งครึ่ง", "Split Shop", "1100000000844");
        approve(shopId);
        String image = JsonPath.read(mvc.perform(upload(owner, shopId, "IMAGE", file("split.png", "image/png", png(0x336699))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        for (var template : List.of("GENERAL", "FASHION", "FOOD_BEVERAGE")) {
            for (var layout : List.of("SPLIT", "POSTER")) {
                mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                        .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                        .content(heroLayoutPayload(template, layout, slides(image))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.config.hero.layout").value(layout))
                    .andExpect(jsonPath("$.config.sections[0].variant").value(layout))
                    .andExpect(jsonPath("$.config.hero.slides", hasSize(1)));
            }
            // Legacy layouts keep working: STRIP everywhere, MEDIA_FULL only GENERAL (others fall back to FULL).
            mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                    .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                    .content(heroLayoutPayload(template, "STRIP", slides())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.hero.layout").value("STRIP"));
            mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                    .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                    .content(heroLayoutPayload(template, "MEDIA_FULL", slides())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.hero.layout").value("GENERAL".equals(template) ? "MEDIA_FULL" : "FULL"));
        }
        // POSTER keeps GENERAL badges (stickers).
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(heroLayoutPayload("GENERAL", "POSTER", slides()).replace("\"layout\":\"POSTER\",", "\"layout\":\"POSTER\",\"badges\":[\"NEW\"],")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.hero.badges[0]").value("NEW"));
        // Unknown layouts are still rejected, on hero.layout and on the HERO section variant.
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(heroLayoutPayload("FASHION", "HALF", slides())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.layout')].code", hasItem("THEME_HERO_LAYOUT_INVALID")));
    }

    private static String heroLayoutPayload(String templateKey, String layout, String slidesJson) {
        return themePayload(templateKey, slidesJson)
            .replace("\"layout\":\"FULL\",", "\"layout\":\"" + layout + "\",")
            .replace("{\"key\":\"HERO\",\"enabled\":true,\"variant\":\"FULL\"}", "{\"key\":\"HERO\",\"enabled\":true,\"variant\":\"" + layout + "\"}");
    }

    private static String introPayload(String templateKey, String introJson) {
        return themePayload(templateKey, slides()).replace("\"banners\": []", "\"intro\": " + introJson + ", \"banners\": []");
    }

    private static String slides(String... ids) {
        var parts = new java.util.ArrayList<String>();
        for (var id : ids) parts.add("{\"slideMediaId\":\"" + id + "\"}");
        return "[" + String.join(",", parts) + "]";
    }

    private static String copyPayload(String templateKey, String badges, String sub, String announcement) {
        return themePayload(templateKey, slides())
            .replace("\"layout\":\"FULL\",", "\"layout\":\"MEDIA_FULL\",\"badges\":" + badges + ",")
            .replace("\"enabled\":true}]}", "\"enabled\":true,\"sub\":" + sub + "}]}")
            .replace("\"banners\": []", "\"announcement\": " + announcement + ", \"banners\": []");
    }

    @Test
    void tsfCopyBadgesTrustSubAndAnnouncementArePlainTextAndBounded() throws Exception {
        var owner = register("tsf-copy@example.com", "0815200177");
        long shopId = openShop(owner, "Copy TH", "Copy", "1100000000777");
        approve(shopId);
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(copyPayload("GENERAL", "[\"NEW\",\"SALE 50%\"]", "\"ok sub\"", "{\"items\":[\"Free ship\",\"COD\"],\"junk\":1}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.hero.badges[1]").value("SALE 50%"))
            .andExpect(jsonPath("$.config.trust.items[0].sub").value("ok sub"))
            .andExpect(jsonPath("$.config.announcement.items[0]").value("Free ship"))
            .andExpect(jsonPath("$.config.announcement.junk").doesNotExist());
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(copyPayload("GENERAL", "[\"<b>\",\"" + "a".repeat(25) + "\"]", "\"" + "b".repeat(81) + "\"", "{\"items\":[\"<i>x</i>\"]}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.badges[0]')].code", hasItem("THEME_HERO_BADGES_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.badges[1]')].code", hasItem("THEME_HERO_BADGES_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.trust.items[0].sub')].code", hasItem("THEME_TRUST_SUB_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.announcement.items[0]')].code", hasItem("THEME_ANNOUNCEMENT_INVALID")));
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(copyPayload("GENERAL", "[\"a\",\"b\",\"c\",\"d\"]", "null", "{\"items\":[\"a\",\"b\",\"c\"]}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.hero.badges')].code", hasItem("THEME_HERO_BADGES_INVALID")))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.announcement.items')].code", hasItem("THEME_ANNOUNCEMENT_INVALID")));
        // Badges are GENERAL-only; other templates drop them silently.
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(copyPayload("FASHION", "[\"NEW\"]", "\"s\"", "{\"items\":[\"x\"]}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.hero.badges").doesNotExist())
            .andExpect(jsonPath("$.config.announcement.items[0]").value("x"));
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private RequestBuilder upload(Account owner, long shopId, String kind, MockMultipartFile file) {
        return multipart("/api/merchant/settings/theme/media?shopId=" + shopId)
            .file(file)
            .param("kind", kind)
            .header("Authorization", bearer(owner.token));
    }

    private static MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    private static byte[] png(int rgb) throws Exception {
        return image("png", rgb);
    }

    private static byte[] image(String format, int rgb) throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) image.setRGB(x, y, rgb);
        var out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out));
        return out.toByteArray();
    }

    /** Minimal "mp4": 0x18-byte ftyp box header (isom) followed by padding. */
    private static byte[] mp4() {
        var bytes = new byte[64];
        var head = new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }

    private static byte[] woff2() {
        var bytes = new byte[48];
        System.arraycopy("wOF2".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        return bytes;
    }

    private void saveTheme(Account owner, long shopId, String template, String slidesJson) throws Exception {
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload(template, slidesJson)))
            .andExpect(status().isOk());
    }

    private static String themePayload(String templateKey, String slidesJson) {
        var config = """
            {
              "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
              "font": "NOTO_SANS_THAI",
              "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
              "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":"","slides":%s},
              "banners": [],
              "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
              "sections": %s
            }
            """.formatted(slidesJson, SECTIONS);
        return "{\"templateKey\":\"" + templateKey + "\",\"config\":" + config + "}";
    }

    private record Account(UUID id, String token) {}

    private Account register(String email, String phone) throws Exception {
        var body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
              {"firstName":"ทดสอบ","lastName":"สื่อ","email":"%s","phone":"%s","password":"Password1"}
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
        var admin = platformAdmin("media-admin-" + shopId + "@example.com", "083" + String.format("%07d", shopId % 10000000));
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve")
                .header("Authorization", bearer(admin.token)))
            .andExpect(status().isOk());
    }

    private long openShop(Account owner, String shopName, String shopNameEn, String citizenId) throws Exception {
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
              """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON).content("""
              {"ownerType":"PERSON","title":"นาย","firstName":"ทดสอบ","lastName":"สื่อ","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"0899999999","email":"owner@example.com","houseNo":"1","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"ทดสอบ สื่อ"}
              """.formatted(citizenId))).andExpect(status().isOk());
        for (var type : List.of("IDENTITY", "ADDRESS")) {
            mvc.perform(multipart("/api/merchant-applications/draft/documents/" + type).file(docPng()).header("Authorization", bearer(owner.token)))
                .andExpect(status().isOk());
        }
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", bearer(owner.token))).andExpect(status().isOk());
        return shops.findAll().stream().filter(shop -> shop.getName().equals(shopName)).findFirst().orElseThrow().getId();
    }

    private static MockMultipartFile docPng() {
        return new MockMultipartFile("file", "image.png", "image/png", new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
