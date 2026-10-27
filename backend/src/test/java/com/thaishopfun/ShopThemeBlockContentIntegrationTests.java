package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.catalog.Product;
import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.catalog.ProductStatus;
import com.thaishopfun.catalog.ProductType;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** AG4: STEPS / FEATURE / GALLERY / SOCIAL / RICH_TEXT block types (CUSTOM only; never part of {@code sections}). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopThemeBlockContentIntegrationTests {
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
    @Autowired ProductRepository catalogProducts;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        wipe();
    }

    @AfterEach
    void cleanupAfter() {
        themes.deleteAll();
        catalogProducts.deleteAll();
    }

    private void wipe() {
        themes.deleteAll();
        catalogProducts.deleteAll();
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

    // ───────────────────────── valid saves ─────────────────────────

    @Test
    void everyNewTypeSavesAndPublicThemeReturnsDerivedFields() throws Exception {
        var owner = register("ag4-valid@example.com", "0818400001");
        long shopId = openShop(owner, "ร้านเอจีสี่", "AG4 Valid", "1100000000401");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();
        saveCustom(owner, shopId, "[]").andExpect(status().isOk());
        var imageA = uploadImage(owner, shopId, 0x112233);
        var imageB = uploadImage(owner, shopId, 0x445566);
        var product = publishedProduct(shopId, "ag4-feature-product");

        var blocks = """
            [
              {"id":"how-it-works","type":"STEPS","enabled":true,"settings":{},
               "content":{"title":{"th":"ขั้นตอน","en":"How it works"},"subtitle":{"th":"ง่ายนิดเดียว"},
                 "steps":[
                   {"icon":"AUTHENTIC","title":{"th":"เลือก"},"body":{"th":"เลือกสินค้า","en":"Pick"},"mediaId":"%s"},
                   {"icon":"CUSTOM","mediaId":"%s","title":{"th":"สั่ง"},"body":{"th":"สั่งซื้อ"}},
                   {"icon":"NUMBER","title":{"th":"รับ"},"body":{"th":"รับของ"}}],
                 "cta":{"label":{"th":"ดูสินค้า","en":"Shop"},"link":"/shops"}}},
              {"id":"feature-a","type":"FEATURE","enabled":true,"settings":{},
               "content":{"mediaId":"%s","title":{"th":"สินค้าเด่น"},"body":{"th":"รายละเอียด"},
                 "bullets":[{"th":"ข้อหนึ่ง","en":"One"},{"th":"ข้อสอง"}],
                 "cta":{"label":{"th":"ซื้อเลย"},"link":"https://example.com/buy"},
                 "productId":"%s","layout":"IMAGE_RIGHT"}},
              {"id":"feature-b","type":"FEATURE","enabled":false,"settings":{},"content":{"title":{"th":"อีกอัน"},"layout":"IMAGE_LEFT"}},
              {"id":"gallery-a","type":"GALLERY","enabled":true,"settings":{},
               "content":{"title":{"th":"แกลเลอรี"},"layout":"MASONRY",
                 "items":[{"mediaId":"%s","caption":{"th":"รูปหนึ่ง"},"link":"/shops"},{"mediaId":"%s"}]}},
              {"id":"social-a","type":"SOCIAL","enabled":true,"settings":{},
               "content":{"provider":"TIKTOK","videoIds":["7234567890123456789","1234567890"]}},
              {"id":"rich-a","type":"RICH_TEXT","enabled":true,"settings":{},
               "content":{"markdown":{"th":"## หัวข้อ\\n\\nข้อความ **หนา** และ *เอียง* [ลิงก์](https://example.com/a)\\n\\n- หนึ่ง\\n- สอง\\n\\n> คำพูด","en":"### Title\\n\\n1. one\\n2. two"},
                 "html":{"th":"<script>alert(1)</script>"},"evil":1}}
            ]
            """.formatted(imageA, imageB, imageA, product.toString(), imageA, imageB);

        var body = saveCustom(owner, shopId, blocks)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout.blocks[*].type", contains("STEPS", "FEATURE", "FEATURE", "GALLERY", "SOCIAL", "RICH_TEXT")))
            // block-only types never leak into the legacy sections list
            .andExpect(jsonPath("$.config.sections", hasSize(10)))
            .andExpect(jsonPath("$.config.sections[*].key", containsInAnyOrder(
                "HERO", "BANNERS", "CATEGORIES", "TRUST", "INTRO", "FEATURED", "BESTSELLERS", "NEW_ARRIVALS", "CATALOG", "HOURS")))
            .andExpect(jsonPath("$.config.layout.blocks[0].content.steps", hasSize(3)))
            .andExpect(jsonPath("$.config.layout.blocks[0].content.steps[0].mediaId").doesNotExist()) // only CUSTOM icon keeps an image
            .andExpect(jsonPath("$.config.layout.blocks[0].content.steps[1].mediaId").value(imageB))
            .andExpect(jsonPath("$.config.layout.blocks[0].content.steps[1].mediaUrl").value("/api/shops/" + slug + "/theme-media/" + imageB))
            .andExpect(jsonPath("$.config.layout.blocks[0].content.title.en").value("How it works"))
            .andExpect(jsonPath("$.config.layout.blocks[0].content.cta.link").value("/shops"))
            .andExpect(jsonPath("$.config.layout.blocks[1].content.layout").value("IMAGE_RIGHT"))
            .andExpect(jsonPath("$.config.layout.blocks[1].content.mediaUrl").value("/api/shops/" + slug + "/theme-media/" + imageA))
            .andExpect(jsonPath("$.config.layout.blocks[1].content.bullets", hasSize(2)))
            .andExpect(jsonPath("$.config.layout.blocks[1].content.productId").value(product.toString()))
            .andExpect(jsonPath("$.config.layout.blocks[1].content.productSlug").value("ag4-feature-product"))
            .andExpect(jsonPath("$.config.layout.blocks[2].content.layout").value("IMAGE_LEFT"))
            .andExpect(jsonPath("$.config.layout.blocks[3].content.layout").value("MASONRY"))
            .andExpect(jsonPath("$.config.layout.blocks[3].content.items", hasSize(2)))
            .andExpect(jsonPath("$.config.layout.blocks[3].content.items[0].url").value("/api/shops/" + slug + "/theme-media/" + imageA))
            .andExpect(jsonPath("$.config.layout.blocks[4].content.provider").value("TIKTOK"))
            .andExpect(jsonPath("$.config.layout.blocks[4].content.videoIds", contains("7234567890123456789", "1234567890")))
            .andExpect(jsonPath("$.config.layout.blocks[5].content.evil").doesNotExist())
            .andExpect(jsonPath("$.config.layout.blocks[5].content.markdown.th", startsWith("## หัวข้อ")))
            .andReturn().getResponse().getContentAsString();

        String html = JsonPath.read(body, "$.config.layout.blocks[5].content.html.th");
        assertFalse(html.contains("<script"), html);
        assertTrue(html.contains("<h2>หัวข้อ</h2>"), html);
        assertTrue(html.contains("<strong>หนา</strong>"), html);
        assertTrue(html.contains("<em>เอียง</em>"), html);
        assertTrue(html.contains("<ul><li>หนึ่ง</li><li>สอง</li></ul>"), html);
        assertTrue(html.contains("<blockquote>"), html);
        assertTrue(html.contains("href=\"https://example.com/a\""), html);
        assertTrue(html.contains("rel=\"nofollow noopener noreferrer\""), html);
        String htmlEn = JsonPath.read(body, "$.config.layout.blocks[5].content.html.en");
        assertTrue(htmlEn.contains("<h3>Title</h3>") && htmlEn.contains("<ol><li>one</li><li>two</li></ol>"), htmlEn);

        mvc.perform(get("/api/shops/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.theme.config.layout.blocks", hasSize(6)))
            .andExpect(jsonPath("$.theme.config.layout.blocks[3].content.items[1].url").value("/api/shops/" + slug + "/theme-media/" + imageB))
            .andExpect(jsonPath("$.theme.config.layout.blocks[1].content.productSlug").value("ag4-feature-product"))
            .andExpect(jsonPath("$.theme.config.layout.blocks[5].content.html.th", containsString("<h2>")));

        // Content images count as "in use" until the block no longer references them.
        mvc.perform(delete("/api/merchant/settings/theme/media/" + imageA + "?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("THEME_MEDIA_IN_USE"));
        mvc.perform(delete("/api/merchant/settings/theme/media/" + imageB + "?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)))
            .andExpect(status().isConflict());

        // A saved block's product that is later unpublished is no longer linked on read (no slug, no crash).
        var row = catalogProducts.findById(product).orElseThrow();
        row.setStatus(ProductStatus.ARCHIVED);
        catalogProducts.save(row);
        mvc.perform(get("/api/shops/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.theme.config.layout.blocks[1].content.productSlug").doesNotExist());
    }

    @Test
    void newTypesRepeatFreelyAndMixWithLegacyBlocks() throws Exception {
        var owner = register("ag4-repeat@example.com", "0818400002");
        long shopId = openShop(owner, "ร้านซ้ำ", "AG4 Repeat", "1100000000402");

        var blocks = new StringBuilder("[{\"id\":\"hero-1\",\"type\":\"HERO\",\"enabled\":true,\"settings\":{}},");
        for (int i = 0; i < 4; i++) blocks.append("{\"id\":\"f").append(i).append("\",\"type\":\"FEATURE\",\"enabled\":true,\"settings\":{}},");
        blocks.append("{\"id\":\"r1\",\"type\":\"RICH_TEXT\",\"enabled\":true,\"settings\":{}},")
            .append("{\"id\":\"r2\",\"type\":\"RICH_TEXT\",\"enabled\":true,\"settings\":{}},")
            .append("{\"id\":\"cat\",\"type\":\"CATALOG\",\"enabled\":true,\"variant\":\"MENU\",\"settings\":{\"columns\":4}}]");
        saveCustom(owner, shopId, blocks.toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout.blocks", hasSize(8)))
            .andExpect(jsonPath("$.config.sections[?(@.key=='CATALOG')].variant", hasItem("MENU")))
            .andExpect(jsonPath("$.config.sections", hasSize(10)));

        // HERO / HOURS stay singletons even next to the new types.
        saveCustom(owner, shopId, """
            [{"id":"h1","type":"HERO","enabled":true,"settings":{}},
             {"id":"s1","type":"STEPS","enabled":true,"settings":{},"content":{"steps":[{"icon":"NUMBER"},{"icon":"NUMBER"}]}},
             {"id":"h2","type":"HERO","enabled":true,"settings":{}}]
            """).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='config.layout.blocks[2].type')].code", hasItem("THEME_BLOCKS_INVALID")));

        // New types have no variant and no product-grid columns.
        expectError(owner, shopId, """
            [{"id":"x","type":"GALLERY","enabled":true,"variant":"GRID","settings":{},"content":{}}]
            """, "config.layout.blocks[0].variant", "THEME_BLOCKS_INVALID");
        expectError(owner, shopId, """
            [{"id":"x","type":"FEATURE","enabled":true,"settings":{"columns":3}}]
            """, "config.layout.blocks[0].settings.columns", "THEME_TOKEN_INVALID");
    }

    @Test
    void emptyOptionalFieldsStillSaveAndPublicThemeReturns() throws Exception {
        var owner = register("ag4-empty@example.com", "0818400003");
        long shopId = openShop(owner, "ร้านว่าง", "AG4 Empty", "1100000000403");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();

        saveCustom(owner, shopId, """
            [
              {"id":"s1","type":"STEPS","enabled":true,"settings":{},
               "content":{"title":{"th":"","en":""},"subtitle":null,"cta":{"label":{"th":""},"link":""},
                 "steps":[{"icon":"AUTHENTIC","title":{"th":""},"body":{"th":""}},{"icon":"FAST_SHIP"}]}},
              {"id":"f1","type":"FEATURE","enabled":true,"settings":{},
               "content":{"mediaId":"","title":{"th":"  "},"bullets":[],"productId":"","cta":{"label":{"th":""},"link":""}}},
              {"id":"g1","type":"GALLERY","enabled":true,"settings":{},"content":{"items":[{"mediaId":""}]}},
              {"id":"so1","type":"SOCIAL","enabled":true,"settings":{},"content":{"videoIds":[]}},
              {"id":"rt1","type":"RICH_TEXT","enabled":true,"settings":{},"content":{"markdown":{"th":"","en":""}}},
              {"id":"rt2","type":"RICH_TEXT","enabled":true,"settings":{}}
            ]
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout.blocks", hasSize(6)))
            .andExpect(jsonPath("$.config.layout.blocks[0].content.steps", hasSize(2)))
            .andExpect(jsonPath("$.config.layout.blocks[0].content.title").doesNotExist())
            .andExpect(jsonPath("$.config.layout.blocks[0].content.cta").doesNotExist())
            .andExpect(jsonPath("$.config.layout.blocks[0].content.steps[1].icon").value("FAST_SHIP"))
            .andExpect(jsonPath("$.config.layout.blocks[1].content.layout").value("IMAGE_LEFT"))
            .andExpect(jsonPath("$.config.layout.blocks[1].content.mediaId").doesNotExist())
            .andExpect(jsonPath("$.config.layout.blocks[1].content.productId").doesNotExist())
            .andExpect(jsonPath("$.config.layout.blocks[1].content.bullets", hasSize(0)))
            .andExpect(jsonPath("$.config.layout.blocks[2].content.layout").value("GRID"))
            .andExpect(jsonPath("$.config.layout.blocks[2].content.items", hasSize(0)))
            .andExpect(jsonPath("$.config.layout.blocks[3].content.provider").value("TIKTOK"))
            .andExpect(jsonPath("$.config.layout.blocks[3].content.videoIds", hasSize(0)))
            .andExpect(jsonPath("$.config.layout.blocks[4].content.markdown").doesNotExist());

        mvc.perform(get("/api/shops/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.theme.templateKey").value("CUSTOM"))
            .andExpect(jsonPath("$.theme.config.layout.blocks", hasSize(6)));
    }

    // ───────────────────────── invalid content ─────────────────────────

    @Test
    void richTextRejectsScriptsJavascriptEventHandlersAndRawHtml() throws Exception {
        var owner = register("ag4-rich@example.com", "0818400004");
        long shopId = openShop(owner, "ร้านริช", "AG4 Rich", "1100000000404");

        for (var dangerous : List.of(
            "<script>alert(1)</script>",
            "hello <SCRIPT src=x></SCRIPT>",
            "[click](javascript:alert(1))",
            "[click](JaVaScRiPt:alert(1))",
            "[click]( javascript:alert(1))",
            "<img src=x onerror=alert(1)>",
            "plain onerror=alert(1) text",
            "x onload = alert(1)",
            "<a href=\\\"https://x.test\\\">raw html</a>",
            "![pic](https://example.com/a.png)",
            "[plain http](http://example.com)",
            "[proto relative](//evil.example.com)",
            "[data](data:text/html;base64,AAAA)",
            "<iframe src=https://evil.test></iframe>"
        )) {
            expectError(owner, shopId,
                "[{\"id\":\"rt\",\"type\":\"RICH_TEXT\",\"enabled\":true,\"settings\":{},\"content\":{\"markdown\":{\"th\":\"" + dangerous + "\"}}}]",
                "config.layout.blocks[0].content.markdown.th", "THEME_RICHTEXT_INVALID");
        }

        // English copy is checked too, and an over-long text is rejected.
        expectError(owner, shopId, """
            [{"id":"rt","type":"RICH_TEXT","enabled":true,"settings":{},"content":{"markdown":{"th":"ok","en":"[x](javascript:alert(1))"}}}]
            """, "config.layout.blocks[0].content.markdown.en", "THEME_RICHTEXT_INVALID");
        expectError(owner, shopId,
            "[{\"id\":\"rt\",\"type\":\"RICH_TEXT\",\"enabled\":true,\"settings\":{},\"content\":{\"markdown\":{\"th\":\"" + "a".repeat(5001) + "\"}}}]",
            "config.layout.blocks[0].content.markdown.th", "THEME_RICHTEXT_INVALID");
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(customPayload(",\"layout\":{\"blocks\":[{\"id\":\"rt\",\"type\":\"RICH_TEXT\",\"enabled\":true,\"settings\":{},\"content\":{\"markdown\":{\"th\":\""
                    + "a".repeat(5000) + "\"}}}]}")))
            .andExpect(status().isOk());

        // Internal paths and https links are fine; the stored html never carries scripts or handlers.
        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content(customPayload(",\"layout\":{\"blocks\":[{\"id\":\"rt\",\"type\":\"RICH_TEXT\",\"enabled\":true,\"settings\":{},"
                    + "\"content\":{\"markdown\":{\"th\":\"a < b และ 5 > 3 [home](/shop/x) [site](https://example.com/p?a=1&b=2)\"}}}]}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout.blocks[0].content.html.th", allOf(
                containsString("a &lt; b"), containsString("href=\"/shop/x\""), containsString("a=1&amp;b=2"),
                not(containsString("<script")), not(containsString("onerror")))));
    }

    @Test
    void socialAcceptsOnlyNumericTikTokVideoIds() throws Exception {
        var owner = register("ag4-social@example.com", "0818400005");
        long shopId = openShop(owner, "ร้านโซเชียล", "AG4 Social", "1100000000405");

        for (var bad : List.of(
            "\"abc\"",
            "\"123456789\"",
            "\"12345678901234567890123456\"",
            "\"https://www.tiktok.com/@shop/video/7234567890123456789\"",
            "\"<iframe src='https://www.tiktok.com/embed/v2/7234567890123456789'></iframe>\"",
            "\"7234567890123456789\\\"><script>alert(1)</script>\"",
            "\"72345 67890\"",
            "\"-7234567890123\"",
            "{\"id\":\"7234567890123456789\"}",
            "7234567890123456789"
        )) {
            expectError(owner, shopId,
                "[{\"id\":\"so\",\"type\":\"SOCIAL\",\"enabled\":true,\"settings\":{},\"content\":{\"provider\":\"TIKTOK\",\"videoIds\":[" + bad + "]}}]",
                "config.layout.blocks[0].content.videoIds[0]", "THEME_SOCIAL_INVALID");
        }
        // provider allowlist and the 6-video cap
        expectError(owner, shopId, """
            [{"id":"so","type":"SOCIAL","enabled":true,"settings":{},"content":{"provider":"INSTAGRAM","videoIds":["7234567890123456789"]}}]
            """, "config.layout.blocks[0].content.provider", "THEME_SOCIAL_INVALID");
        expectError(owner, shopId, """
            [{"id":"so","type":"SOCIAL","enabled":true,"settings":{},"content":{"provider":"TIKTOK","videoIds":
              ["1000000001","1000000002","1000000003","1000000004","1000000005","1000000006","1000000007"]}}]
            """, "config.layout.blocks[0].content.videoIds", "THEME_SOCIAL_INVALID");

        saveCustom(owner, shopId, """
            [{"id":"so","type":"SOCIAL","enabled":true,"settings":{},"content":{"provider":"tiktok","videoIds":
              [" 1000000001 ","1000000002","1000000003","1000000004","1000000005","1000000006"]}}]
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout.blocks[0].content.provider").value("TIKTOK"))
            .andExpect(jsonPath("$.config.layout.blocks[0].content.videoIds", hasSize(6)))
            .andExpect(jsonPath("$.config.layout.blocks[0].content.videoIds[0]").value("1000000001"));
    }

    @Test
    void featureProductMustBePublishedAndBelongToThisShop() throws Exception {
        var owner = register("ag4-product@example.com", "0818400006");
        long shopId = openShop(owner, "ร้านสินค้า", "AG4 Product", "1100000000406");
        var other = register("ag4-product2@example.com", "0818400007");
        long otherShop = openShop(other, "ร้านอื่นสอง", "AG4 Other Product", "1100000000407");

        var mine = publishedProduct(shopId, "mine");
        var theirs = publishedProduct(otherShop, "theirs");
        var draft = product(shopId, "draft-one", ProductStatus.DRAFT);
        var archived = product(shopId, "archived-one", ProductStatus.ARCHIVED);
        var deleted = product(shopId, "deleted-one", ProductStatus.PUBLISHED);
        jdbc.update("update products set deleted_at = current_timestamp where id = ?", deleted);

        for (var id : List.of(theirs.toString(), draft.toString(), archived.toString(), deleted.toString(), UUID.randomUUID().toString())) {
            expectError(owner, shopId, featureWithProduct(id), "config.layout.blocks[0].content.productId", "THEME_PRODUCT_INVALID");
        }
        expectError(owner, shopId, featureWithProduct("not-a-uuid"), "config.layout.blocks[0].content.productId", "THEME_PRODUCT_INVALID");

        saveCustom(owner, shopId, featureWithProduct(mine.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout.blocks[0].content.productId").value(mine.toString()))
            .andExpect(jsonPath("$.config.layout.blocks[0].content.productSlug").value("mine"));
    }

    @Test
    void stepsGalleryFeatureStructureAndLinksAreValidated() throws Exception {
        var owner = register("ag4-shape@example.com", "0818400008");
        long shopId = openShop(owner, "ร้านรูปทรง", "AG4 Shape", "1100000000408");
        approve(shopId);
        saveCustom(owner, shopId, "[]").andExpect(status().isOk());
        var image = uploadImage(owner, shopId, 0x778899);

        // STEPS: 2–6 items, known icons, bounded text, safe CTA links
        expectError(owner, shopId, stepsBlock("[{\"icon\":\"NUMBER\"}]", ""), "config.layout.blocks[0].content.steps", "THEME_BLOCK_CONTENT_INVALID");
        expectError(owner, shopId,
            stepsBlock(IntStream.range(0, 7).mapToObj(i -> "{\"icon\":\"NUMBER\"}").collect(Collectors.joining(",", "[", "]")), ""),
            "config.layout.blocks[0].content.steps", "THEME_BLOCK_CONTENT_INVALID");
        expectError(owner, shopId, stepsBlock("[{\"icon\":\"NUMBER\"},{\"icon\":\"ROCKET\"}]", ""),
            "config.layout.blocks[0].content.steps[1].icon", "THEME_BLOCK_CONTENT_INVALID");
        expectError(owner, shopId, stepsBlock("[{\"icon\":\"NUMBER\",\"title\":{\"th\":\"" + "x".repeat(81) + "\"}},{\"icon\":\"NUMBER\"}]", ""),
            "config.layout.blocks[0].content.steps[0].title.th", "THEME_BLOCK_CONTENT_INVALID");
        for (var link : List.of("javascript:alert(1)", "//evil.example.com", "http://example.com", "mailto:a@b.co", "/ok<script>")) {
            expectError(owner, shopId, stepsBlock("[{\"icon\":\"NUMBER\"},{\"icon\":\"NUMBER\"}]",
                ",\"cta\":{\"label\":{\"th\":\"ไป\"},\"link\":\"" + link + "\"}"),
                "config.layout.blocks[0].content.cta.link", "THEME_BLOCK_LINK_INVALID");
        }
        expectError(owner, shopId, stepsBlock("[{\"icon\":\"CUSTOM\",\"mediaId\":\"" + UUID.randomUUID() + "\"},{\"icon\":\"NUMBER\"}]", ""),
            "config.layout.blocks[0].content.steps[0].mediaId", "THEME_MEDIA_NOT_FOUND");
        expectError(owner, shopId, stepsBlock("[{\"icon\":\"CUSTOM\",\"mediaId\":\"nope\"},{\"icon\":\"NUMBER\"}]", ""),
            "config.layout.blocks[0].content.steps[0].mediaId", "THEME_MEDIA_INVALID");

        // FEATURE: ≤5 bullets, known layout, image from this shop's library
        expectError(owner, shopId, """
            [{"id":"f","type":"FEATURE","enabled":true,"settings":{},"content":{"bullets":
              [{"th":"1"},{"th":"2"},{"th":"3"},{"th":"4"},{"th":"5"},{"th":"6"}]}}]
            """, "config.layout.blocks[0].content.bullets", "THEME_BLOCK_CONTENT_INVALID");
        expectError(owner, shopId, """
            [{"id":"f","type":"FEATURE","enabled":true,"settings":{},"content":{"layout":"IMAGE_TOP"}}]
            """, "config.layout.blocks[0].content.layout", "THEME_BLOCK_CONTENT_INVALID");
        expectError(owner, shopId, """
            [{"id":"f","type":"FEATURE","enabled":true,"settings":{},"content":{"mediaId":"%s"}}]
            """.formatted(UUID.randomUUID()), "config.layout.blocks[0].content.mediaId", "THEME_MEDIA_NOT_FOUND");

        // GALLERY: ≤24 items, known layout, safe links
        var items = IntStream.range(0, 25).mapToObj(i -> "{\"mediaId\":\"" + image + "\"}").collect(Collectors.joining(",", "[", "]"));
        expectError(owner, shopId, "[{\"id\":\"g\",\"type\":\"GALLERY\",\"enabled\":true,\"settings\":{},\"content\":{\"items\":" + items + "}}]",
            "config.layout.blocks[0].content.items", "THEME_BLOCK_CONTENT_INVALID");
        expectError(owner, shopId, """
            [{"id":"g","type":"GALLERY","enabled":true,"settings":{},"content":{"layout":"SLIDESHOW"}}]
            """, "config.layout.blocks[0].content.layout", "THEME_BLOCK_CONTENT_INVALID");
        expectError(owner, shopId, """
            [{"id":"g","type":"GALLERY","enabled":true,"settings":{},"content":{"items":[{"mediaId":"%s","link":"javascript:alert(1)"}]}}]
            """.formatted(image), "config.layout.blocks[0].content.items[0].link", "THEME_BLOCK_LINK_INVALID");
        expectError(owner, shopId, """
            [{"id":"g","type":"GALLERY","enabled":true,"settings":{},"content":{"items":[{"mediaId":"%s"},{"mediaId":"%s"}]}}]
            """.formatted(image, UUID.randomUUID()), "config.layout.blocks[0].content.items[1].mediaId", "THEME_MEDIA_NOT_FOUND");

        // exactly 24 items is fine
        var max = IntStream.range(0, 24).mapToObj(i -> "{\"mediaId\":\"" + image + "\"}").collect(Collectors.joining(",", "[", "]"));
        saveCustom(owner, shopId, "[{\"id\":\"g\",\"type\":\"GALLERY\",\"enabled\":true,\"settings\":{},\"content\":{\"items\":" + max + "}}]")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout.blocks[0].content.items", hasSize(24)));
    }

    // ───────────────────────── other templates ─────────────────────────

    @Test
    void generalAndFashionDropNewBlocksAndStaySectionBased() throws Exception {
        var owner = register("ag4-general@example.com", "0818400009");
        long shopId = openShop(owner, "ร้านทั่วไป", "AG4 General", "1100000000409");
        approve(shopId);
        var slug = shops.findByShopId(shopId).orElseThrow().getSlug();

        for (var template : List.of("GENERAL", "FASHION")) {
            mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                    .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateKey\":\"" + template + "\",\"config\":" + baseConfig("""
                        ,"layout":{"blocks":[
                          {"id":"s1","type":"STEPS","enabled":true,"content":{"steps":[]}},
                          {"id":"so1","type":"SOCIAL","enabled":true,"content":{"videoIds":["<script>"]}},
                          {"id":"rt1","type":"RICH_TEXT","enabled":true,"content":{"markdown":{"th":"<script>alert(1)</script>"}}}
                        ]}
                        """) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateKey").value(template))
                .andExpect(jsonPath("$.config.layout").doesNotExist())
                .andExpect(jsonPath("$.config.sections", hasSize(10)));

            mvc.perform(get("/api/shops/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme.config.layout").doesNotExist())
                .andExpect(jsonPath("$.theme.config.sections", hasSize(10)));
        }
    }

    @Test
    void switchingCustomWithNewBlocksBackToGeneralDropsThemAndFreesMedia() throws Exception {
        var owner = register("ag4-switch@example.com", "0818400010");
        long shopId = openShop(owner, "ร้านสลับ", "AG4 Switch", "1100000000410");
        approve(shopId);
        saveCustom(owner, shopId, "[]").andExpect(status().isOk());
        var image = uploadImage(owner, shopId, 0x0a0b0c);

        saveCustom(owner, shopId, """
            [{"id":"f","type":"FEATURE","enabled":true,"settings":{},"content":{"mediaId":"%s"}}]
            """.formatted(image)).andExpect(status().isOk());
        mvc.perform(delete("/api/merchant/settings/theme/media/" + image + "?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)))
            .andExpect(status().isConflict());

        mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateKey\":\"GENERAL\",\"config\":" + baseConfig("") + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.layout").doesNotExist());

        mvc.perform(delete("/api/merchant/settings/theme/media/" + image + "?shopId=" + shopId)
                .header("Authorization", bearer(owner.token)))
            .andExpect(status().isNoContent());
    }

    // ───────────────────────── helpers ─────────────────────────

    private static String featureWithProduct(String productId) {
        return "[{\"id\":\"f\",\"type\":\"FEATURE\",\"enabled\":true,\"settings\":{},\"content\":{\"productId\":\"" + productId + "\"}}]";
    }

    private static String stepsBlock(String steps, String extra) {
        return "[{\"id\":\"s\",\"type\":\"STEPS\",\"enabled\":true,\"settings\":{},\"content\":{\"steps\":" + steps + extra + "}}]";
    }

    private UUID publishedProduct(long shopId, String slug) {
        return product(shopId, slug, ProductStatus.PUBLISHED);
    }

    private UUID product(long shopId, String slug, ProductStatus status) {
        var product = new Product(shops.findByShopId(shopId).orElseThrow(), slug, "สินค้า " + slug, ProductType.SIMPLE);
        product.setStatus(status);
        return catalogProducts.save(product).getId();
    }

    private ResultActions saveCustom(Account owner, long shopId, String blocksJson) throws Exception {
        return mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
            .header("Authorization", bearer(owner.token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(customPayload(",\"layout\":{\"blocks\":" + blocksJson + "}")));
    }

    private void expectError(Account owner, long shopId, String blocksJson, String field, String code) throws Exception {
        saveCustom(owner, shopId, blocksJson)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='" + field + "')].code", hasItem(code)));
    }

    private String uploadImage(Account owner, long shopId, int rgb) throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) image.setRGB(x, y, rgb);
        var out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", out));
        var body = mvc.perform(multipart("/api/merchant/settings/theme/media?shopId=" + shopId)
                .file(new MockMultipartFile("file", "ag4.png", "image/png", out.toByteArray()))
                .param("kind", "IMAGE")
                .header("Authorization", bearer(owner.token)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
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
        return "{\"templateKey\":\"CUSTOM\",\"config\":" + baseConfig(extra) + "}";
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
        var admin = platformAdmin("ag4-admin-" + shopId + "@example.com", "084" + String.format("%07d", shopId % 10000000));
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
