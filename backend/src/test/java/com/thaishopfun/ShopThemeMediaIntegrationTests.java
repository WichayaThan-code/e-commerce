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

/** max-images=1 so the quota case needs only two uploads; every other case uploads at most one distinct image. */
@SpringBootTest(properties = "app.merchant.theme-media.max-images=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopThemeMediaIntegrationTests {
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
    void customShopUploadsImageVideoAndFont() throws Exception {
        var owner = register("media-ok@example.com", "0815200001");
        long shopId = openShop(owner, "ร้านสื่อ", "Media Shop", "1100000000201");
        approve(shopId);
        saveTheme(owner, shopId, "CUSTOM", "[]");

        var imageBody = mvc.perform(upload(owner, shopId, "IMAGE", file("banner.png", "image/png", png(0x112233))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("IMAGE"))
            .andExpect(jsonPath("$.mimeType").value("image/png"))
            .andExpect(jsonPath("$.width").value(2))
            .andExpect(jsonPath("$.height").value(2))
            .andExpect(jsonPath("$.originalFilename").value("banner.png"))
            .andReturn().getResponse().getContentAsString();
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();
        String imageId = JsonPath.read(imageBody, "$.id");
        assertEquals("/api/shops/" + slug + "/theme-media/" + imageId, JsonPath.read(imageBody, "$.url"));

        mvc.perform(upload(owner, shopId, "VIDEO", file("clip.mp4", "video/mp4", mp4())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("VIDEO"))
            .andExpect(jsonPath("$.mimeType").value("video/mp4"));

        mvc.perform(upload(owner, shopId, "FONT", file("brand.woff2", "font/woff2", woff2())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("FONT"))
            .andExpect(jsonPath("$.mimeType").value("font/woff2"));

        mvc.perform(get("/api/merchant/settings/theme/media?shopId=" + shopId).header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/merchant/settings/theme/media?shopId=" + shopId + "&kind=VIDEO").header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].kind").value("VIDEO"));
        mvc.perform(get("/api/merchant/settings/theme/media?shopId=" + shopId + "&kind=NOPE").header("Authorization", bearer(owner.token)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_KIND_INVALID"));
    }

    @Test
    void acceptsJpegAndRejectsOversizedDimensions() throws Exception {
        var owner = register("media-jpg@example.com", "0815200009");
        long shopId = openShop(owner, "ร้านรูปเจเพก", "Media Jpeg Shop", "1100000000209");
        approve(shopId);
        saveTheme(owner, shopId, "CUSTOM", "[]");

        var wide = new BufferedImage(4001, 2, BufferedImage.TYPE_INT_RGB);
        var wideBytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(wide, "png", wideBytes));
        mvc.perform(upload(owner, shopId, "IMAGE", file("wide.png", "image/png", wideBytes.toByteArray())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_INVALID"));

        mvc.perform(upload(owner, shopId, "IMAGE", file("photo.jpeg", "application/octet-stream", image("jpg", 0x336699))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mimeType").value("image/jpeg"))
            .andExpect(jsonPath("$.width").value(2));
    }

    @Test
    void rejectsSvgGifOversizedAndOverQuota() throws Exception {
        var owner = register("media-bad@example.com", "0815200002");
        long shopId = openShop(owner, "ร้านสื่อผิด", "Media Bad Shop", "1100000000202");
        approve(shopId);
        saveTheme(owner, shopId, "CUSTOM", "[]");

        var svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        mvc.perform(upload(owner, shopId, "IMAGE", file("evil.png", "image/png", svg)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_INVALID"));
        var xml = "<?xml version=\"1.0\"?><svg/>".getBytes(StandardCharsets.UTF_8);
        mvc.perform(upload(owner, shopId, "IMAGE", file("evil.png", "image/png", xml)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_INVALID"));

        var gif = "GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1);
        mvc.perform(upload(owner, shopId, "IMAGE", file("anim.png", "image/png", gif)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_INVALID"));

        // Wrong kind for the bytes: a PNG is not a font; unknown kinds are rejected.
        mvc.perform(upload(owner, shopId, "FONT", file("x.woff2", "font/woff2", png(1))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_INVALID"));
        mvc.perform(upload(owner, shopId, "SOUND", file("x.png", "image/png", png(1))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_KIND_INVALID"));

        var oversized = Arrays.copyOf(png(2), 5 * 1024 * 1024 + 1);
        mvc.perform(upload(owner, shopId, "IMAGE", file("big.png", "image/png", oversized)))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        var bigFont = new byte[1024 * 1024 + 1];
        System.arraycopy("wOF2".getBytes(StandardCharsets.US_ASCII), 0, bigFont, 0, 4);
        mvc.perform(upload(owner, shopId, "FONT", file("big.woff2", "font/woff2", bigFont)))
            .andExpect(status().isPayloadTooLarge());

        // max-images=1: first image fits, a different second image does not, identical bytes dedupe to the first.
        var first = mvc.perform(upload(owner, shopId, "IMAGE", file("a.png", "image/png", png(0x010203))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(upload(owner, shopId, "IMAGE", file("b.png", "image/png", png(0x040506))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_QUOTA_EXCEEDED"));
        mvc.perform(upload(owner, shopId, "IMAGE", file("renamed.png", "image/png", png(0x010203))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id")));
        assertEquals(1, themeMedia.count());
    }

    @Test
    void otherShopCannotListOrDeleteMedia() throws Exception {
        var owner = register("media-a@example.com", "0815200003");
        long shopA = openShop(owner, "ร้านเอ", "Media A Shop", "1100000000203");
        approve(shopA);
        saveTheme(owner, shopA, "CUSTOM", "[]");
        String id = JsonPath.read(mvc.perform(upload(owner, shopA, "VIDEO", file("a.mp4", "video/mp4", mp4())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");

        var other = register("media-b@example.com", "0815200004");
        long shopB = openShop(other, "ร้านบี", "Media B Shop", "1100000000204");
        approve(shopB);
        saveTheme(other, shopB, "CUSTOM", "[]");

        mvc.perform(get("/api/merchant/settings/theme/media?shopId=" + shopB).header("Authorization", bearer(other.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/merchant/settings/theme/media/" + id + "?shopId=" + shopB).header("Authorization", bearer(other.token)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_NOT_FOUND"));
        // Pointing at someone else's shop is rejected as well.
        mvc.perform(delete("/api/merchant/settings/theme/media/" + id + "?shopId=" + shopA).header("Authorization", bearer(other.token)))
            .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/merchant/settings/theme/media?shopId=" + shopA).header("Authorization", bearer(other.token)))
            .andExpect(status().is4xxClientError());

        mvc.perform(get("/api/merchant/settings/theme/media?shopId=" + shopA).header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void nonCustomThemeCannotUpload() throws Exception {
        var owner = register("media-general@example.com", "0815200005");
        long shopId = openShop(owner, "ร้านทั่วไปสื่อ", "Media General Shop", "1100000000205");
        approve(shopId);

        // No theme row yet = GENERAL.
        mvc.perform(upload(owner, shopId, "IMAGE", file("a.png", "image/png", png(1))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_CUSTOM_ONLY"));

        saveTheme(owner, shopId, "GENERAL", "[]");
        mvc.perform(upload(owner, shopId, "IMAGE", file("a.png", "image/png", png(1))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_CUSTOM_ONLY"));
        assertEquals(0, themeMedia.count());
    }

    @Test
    void mediaInUseCannotBeDeletedAndNonCustomSaveStripsMediaId() throws Exception {
        var owner = register("media-use@example.com", "0815200006");
        long shopId = openShop(owner, "ร้านใช้สื่อ", "Media Use Shop", "1100000000206");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();
        saveTheme(owner, shopId, "CUSTOM", "[]");
        String id = JsonPath.read(mvc.perform(upload(owner, shopId, "IMAGE", file("b.png", "image/png", png(0x778899))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        var publicUrl = "/api/shops/" + slug + "/theme-media/" + id;

        // Unknown media id is a field error, not a 500.
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("CUSTOM", "[{\"mediaId\":\"" + UUID.randomUUID() + "\",\"title\":\"x\"}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.banners[0].mediaId')].code", hasItem("THEME_MEDIA_NOT_FOUND")));

        // Banner needs imageUrl OR mediaId.
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("CUSTOM", "[{\"title\":\"x\"}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.banners[0].imageUrl')].code", hasItem("THEME_BANNER_IMAGE_REQUIRED")));

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("CUSTOM", "[{\"mediaId\":\"" + id + "\",\"title\":\"โปร\"}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.banners[0].mediaId").value(id))
            .andExpect(jsonPath("$.config.banners[0].imageUrl").value(publicUrl));
        mvc.perform(get("/api/shops/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.theme.config.banners[0].imageUrl").value(publicUrl));

        mvc.perform(delete("/api/merchant/settings/theme/media/" + id + "?shopId=" + shopId).header("Authorization", bearer(owner.token)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_IN_USE"));

        // Switching to a non-CUSTOM template silently drops mediaId (no 400) and frees the media.
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(themePayload("GENERAL", "[{\"mediaId\":\"" + id + "\",\"imageUrl\":\"/img/x.png\",\"title\":\"โปร\"}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.banners[0].mediaId").doesNotExist())
            .andExpect(jsonPath("$.config.banners[0].imageUrl").value("/img/x.png"));

        mvc.perform(delete("/api/merchant/settings/theme/media/" + id + "?shopId=" + shopId).header("Authorization", bearer(owner.token)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/shops/" + slug + "/theme-media/" + id)).andExpect(status().isNotFound());

        // Soft delete frees the sha256 so the same bytes can be uploaded again.
        saveTheme(owner, shopId, "CUSTOM", "[]");
        mvc.perform(upload(owner, shopId, "IMAGE", file("b.png", "image/png", png(0x778899))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(not(id)));
    }

    @Test
    void publicEndpointServesMediaWithNosniffAndChecksSlug() throws Exception {
        var owner = register("media-public@example.com", "0815200007");
        long shopId = openShop(owner, "ร้านสาธารณะ", "Media Public Shop", "1100000000207");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();
        saveTheme(owner, shopId, "CUSTOM", "[]");
        var bytes = png(0xabcdef);
        String imageId = JsonPath.read(mvc.perform(upload(owner, shopId, "IMAGE", file("p.png", "image/jpeg", bytes)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        String videoId = JsonPath.read(mvc.perform(upload(owner, shopId, "VIDEO", file("p.mp4", "application/octet-stream", mp4())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");

        var served = mvc.perform(get("/api/shops/" + slug + "/theme-media/" + imageId))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
            .andExpect(header().string("Cache-Control", containsString("public")))
            .andExpect(content().contentType("image/png"))
            .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(bytes, served);

        mvc.perform(get("/api/shops/" + slug + "/theme-media/" + videoId))
            .andExpect(status().isOk())
            .andExpect(content().contentType("video/mp4"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        var other = register("media-public2@example.com", "0815200008");
        long otherShop = openShop(other, "ร้านอื่น", "Media Other Shop", "1100000000208");
        approve(otherShop);
        var otherSlug = shops.findByShopId(otherShop).orElseThrow().getSlug();
        mvc.perform(get("/api/shops/" + otherSlug + "/theme-media/" + imageId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/shops/no-such-shop/theme-media/" + imageId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/shops/" + slug + "/theme-media/" + UUID.randomUUID())).andExpect(status().isNotFound());
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

    private void saveTheme(Account owner, long shopId, String template, String banners) throws Exception {
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(themePayload(template, banners)))
            .andExpect(status().isOk());
    }

    private static String themePayload(String templateKey, String bannersJson) {
        var config = """
            {
              "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
              "font": "NOTO_SANS_THAI",
              "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
              "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
              "banners": %s,
              "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
              "sections": %s
            }
            """.formatted(bannersJson, SECTIONS);
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
