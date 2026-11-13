package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.catalog.InventoryLedgerRepository;
import com.thaishopfun.catalog.InventoryLevelRepository;
import com.thaishopfun.catalog.ProductImageRepository;
import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.catalog.ProductSkuRepository;
import com.thaishopfun.catalog.collection.ShopCollectionProductRepository;
import com.thaishopfun.catalog.collection.ShopCollectionRepository;
import com.thaishopfun.catalog.configurator.ProductConfiguratorRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Shared fixtures for the V41 collection / V42 configurator tests (shop onboarding, products, theme, media). */
abstract class CatalogExtrasTestSupport {
    static final String SECTIONS = """
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
    @Autowired JdbcTemplate jdbc;
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
    @Autowired InventoryLedgerRepository ledger;
    @Autowired InventoryLevelRepository levels;
    @Autowired ProductImageRepository productImages;
    @Autowired ProductSkuRepository productSkus;
    @Autowired ProductRepository catalogProducts;
    @Autowired ShopCollectionProductRepository collectionItems;
    @Autowired ShopCollectionRepository collections;
    @Autowired ProductConfiguratorRepository configurators;

    record Account(UUID id, String token) {}

    record ShopFixture(Account owner, long shopId, String slug) {}

    /** The in-memory H2 is shared across Spring contexts; leave nothing behind for later test classes. */
    @AfterEach
    void cleanAfter() {
        clean();
    }

    @BeforeEach
    void clean() {
        collectionItems.deleteAll();
        collections.deleteAll();
        configurators.deleteAll();
        ledger.deleteAll();
        levels.deleteAll();
        productSkus.deleteAll();
        productImages.deleteAll();
        catalogProducts.deleteAll();
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

    ShopFixture approvedShop(String key, String phone, String citizenId, String template) throws Exception {
        var owner = register(key + "@example.com", phone);
        long shopId = openShop(owner, "ร้าน " + key, "Shop " + key, citizenId);
        approve(shopId);
        if (template != null) saveTheme(owner, shopId, template);
        return new ShopFixture(owner, shopId, shops.findByShopId(shopId).orElseThrow().getSlug());
    }

    /** Creates a SIMPLE product through the merchant API; {@code published} flips status directly (no image needed). */
    UUID product(ShopFixture shop, String nameEn, String price, boolean published) throws Exception {
        var body = mvc.perform(post("/api/merchant/shops/" + shop.shopId() + "/products").header("Authorization", bearer(shop.owner().token()))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"nameTh":"สินค้า %s","nameEn":"%s","productType":"SIMPLE","basePrice":%s,"skus":[{"skuCode":"%s","price":%s}]}
                    """.formatted(nameEn, nameEn, price, nameEn.toUpperCase().replaceAll("[^A-Z0-9]", "") + "-1", price)))
            .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        var id = UUID.fromString(JsonPath.read(body, "$.id"));
        if (published) jdbc.update("update products set status = 'PUBLISHED' where id = ?", id);
        return id;
    }

    String uploadImage(ShopFixture shop, int rgb) throws Exception {
        var body = mvc.perform(multipart("/api/merchant/settings/theme/media?shopId=" + shop.shopId())
                .file(new MockMultipartFile("file", "img.png", "image/png", png(rgb)))
                .param("kind", "IMAGE")
                .header("Authorization", bearer(shop.owner().token())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    void saveTheme(Account owner, long shopId, String template) throws Exception {
        saveThemeConfig(owner, shopId, template, "");
    }

    /** {@code extra} is appended inside config, e.g. {@code ,"layout":{"blocks":[...]}}. */
    org.springframework.test.web.servlet.ResultActions saveThemeConfig(Account owner, long shopId, String template, String extra) throws Exception {
        var config = """
            {
              "colors": {"primary":"#112233","secondary":"#445566","background":"#ffffff","text":"#111111"},
              "font": "NOTO_SANS_THAI",
              "style": {"radius":12,"cardStyle":"BORDERED","density":"COMFORTABLE","buttonShape":"ROUNDED"},
              "hero": {"layout":"FULL","title":"","subtitle":"","ctaLabel":"","ctaLink":""},
              "banners": [],
              "trust": {"enabled":true,"items":[{"icon":"AUTHENTIC","label":"ของแท้","enabled":true}]},
              "sections": %s%s
            }
            """.formatted(SECTIONS, extra);
        return mvc.perform(put("/api/merchant/settings/theme?shopId=" + shopId)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateKey\":\"" + template + "\",\"config\":" + config + "}"));
    }

    Account register(String email, String phone) throws Exception {
        var body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
              {"firstName":"ทดสอบ","lastName":"คอลเลกชัน","email":"%s","phone":"%s","password":"Password1"}
              """.formatted(email, phone)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Account(UUID.fromString(JsonPath.read(body, "$.user.id")), JsonPath.read(body, "$.accessToken"));
    }

    void approve(long shopId) throws Exception {
        var admin = register("extras-admin-" + shopId + "@example.com", "083" + String.format("%07d", shopId % 10000000));
        platformRoles.save(PlatformRole.grant(admin.id(), PlatformRole.PLATFORM_ADMIN, null, "test"));
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve").header("Authorization", bearer(admin.token())))
            .andExpect(status().isOk());
    }

    long openShop(Account owner, String shopName, String shopNameEn, String citizenId) throws Exception {
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", bearer(owner.token())).contentType(MediaType.APPLICATION_JSON).content("""
              {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
              """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", bearer(owner.token())).contentType(MediaType.APPLICATION_JSON).content("""
              {"ownerType":"PERSON","title":"นาย","firstName":"ทดสอบ","lastName":"คอลเลกชัน","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"0899999999","email":"owner@example.com","houseNo":"1","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"ทดสอบ คอลเลกชัน"}
              """.formatted(citizenId))).andExpect(status().isOk());
        for (var type : List.of("IDENTITY", "ADDRESS")) {
            mvc.perform(multipart("/api/merchant-applications/draft/documents/" + type)
                    .file(new MockMultipartFile("file", "image.png", "image/png", new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
                    .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk());
        }
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", bearer(owner.token()))).andExpect(status().isOk());
        return shops.findAll().stream().filter(shop -> shop.getName().equals(shopName)).findFirst().orElseThrow().getId();
    }

    static byte[] png(int rgb) throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) image.setRGB(x, y, rgb);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    static String bearer(String token) { return "Bearer " + token; }
}
