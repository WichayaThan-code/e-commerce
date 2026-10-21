package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Merchant promotions/coupons (V27 PROMOTION_READ / PROMOTION_MANAGE): CRUD, validation, per-shop unique codes,
 * status filters (Asia/Bangkok), pause/resume, delete guard when used, permissions, shop isolation, and the public
 * storefront list (active + in-date + shown only, no usage data).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PromotionIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final ZoneId BKK = ZoneId.of("Asia/Bangkok");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ShopMemberRepository shopMembers;
    @Autowired ShopReinstatementRequestRepository reinstatements;
    @Autowired ShopRepository shops;
    @Autowired MerchantDocumentRepository documents;
    @Autowired MerchantApplicationRepository applications;
    @Autowired OwnerAuditLogRepository ownerAudits;
    @Autowired OwnerRepository owners;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformRoleRepository platformRoles;

    private String adminToken;

    @BeforeEach
    void clean() {
        adminToken = null;
        jdbc.update("DELETE FROM promotion_usages");
        jdbc.update("DELETE FROM coupons");
        jdbc.update("DELETE FROM promotions");
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

    private static String local(Instant instant) {
        return LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MINUTES), BKK).toString();
    }

    private static String body(String code, String type, String value, String extra) {
        var now = Instant.now();
        return """
            {"name":"โปร %s","code":"%s","type":"%s","value":%s,"startsAt":"%s","endsAt":"%s"%s}
            """.formatted(code, code, type, value, local(now.minus(1, ChronoUnit.DAYS)), local(now.plus(10, ChronoUnit.DAYS)), extra == null ? "" : "," + extra);
    }

    @Test
    void merchantCreatesListsUpdatesPausesAndDeletesPromotions() throws Exception {
        var shop = openShop("pr-crud@example.com", "0815000001", "ร้านโปร", "Promo Shop", "1234567890401");
        var created = perform(post(base(shop) + "/promotions"), shop.token, body("tinz10", "PERCENT", "10", "\"maxDiscountAmount\":100,\"minOrderAmount\":300,\"usageLimitTotal\":200,\"usageLimitPerBuyer\":1"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("TINZ10"))
            .andExpect(jsonPath("$.type").value("PERCENT"))
            .andExpect(jsonPath("$.value").value(10.0))
            .andExpect(jsonPath("$.maxDiscountAmount").value(100.0))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.showOnStorefront").value(true))
            .andExpect(jsonPath("$.usedCount").value(0))
            .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        perform(post(base(shop) + "/promotions"), shop.token, body("FREESHIP399", "FREE_SHIPPING", "55", "\"minOrderAmount\":399"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.value").value(0.0));
        var later = Instant.now().plus(5, ChronoUnit.DAYS);
        perform(post(base(shop) + "/promotions"), shop.token, """
            {"name":"11.11","code":"TINZ1111","type":"FIXED_AMOUNT","value":111,"minOrderAmount":1111,"startsAt":"%s","endsAt":"%s"}
            """.formatted(local(later), local(later.plus(1, ChronoUnit.DAYS))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("UPCOMING"));
        perform(post(base(shop) + "/promotions"), shop.token, """
            {"name":"Old","code":"OLD50","type":"FIXED_AMOUNT","value":50,"startsAt":"2025-01-01T00:00:00+07:00","endsAt":"2025-02-01T00:00"}
            """).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EXPIRED"));

        perform(get(base(shop) + "/promotions"), shop.token).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(4))
            .andExpect(jsonPath("$.items.length()").value(4))
            .andExpect(jsonPath("$.summary.total").value(4))
            .andExpect(jsonPath("$.summary.active").value(2))
            .andExpect(jsonPath("$.summary.upcoming").value(1))
            .andExpect(jsonPath("$.summary.expired").value(1))
            .andExpect(jsonPath("$.summary.freeShipping").value(1))
            .andExpect(jsonPath("$.summary.fixedAmount").value(2));
        perform(get(base(shop) + "/promotions?status=ACTIVE"), shop.token).andExpect(jsonPath("$.items[*].code", containsInAnyOrder("TINZ10", "FREESHIP399")));
        perform(get(base(shop) + "/promotions?status=expired"), shop.token).andExpect(jsonPath("$.items[*].code", contains("OLD50")));
        perform(get(base(shop) + "/promotions?type=PERCENT,FIXED_AMOUNT&q=tinz"), shop.token).andExpect(jsonPath("$.items[*].code", containsInAnyOrder("TINZ10", "TINZ1111")));
        perform(get(base(shop) + "/promotions?size=2&page=1"), shop.token)
            .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.page").value(1));
        perform(get(base(shop) + "/promotions?q=100%25"), shop.token).andExpect(jsonPath("$.total").value(0));
        perform(get(base(shop) + "/promotions?status=NOPE"), shop.token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("STATUS_INVALID"));

        perform(put(base(shop) + "/promotions/" + id), shop.token, body("TINZ15", "PERCENT", "15", "\"showOnStorefront\":false"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("TINZ15")).andExpect(jsonPath("$.value").value(15.0))
            .andExpect(jsonPath("$.showOnStorefront").value(false)).andExpect(jsonPath("$.maxDiscountAmount").doesNotExist());
        perform(get(base(shop) + "/promotions/" + id), shop.token).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("TINZ15"));

        perform(post(base(shop) + "/promotions/" + id + "/pause"), shop.token).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAUSED")).andExpect(jsonPath("$.active").value(false));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT is_active FROM coupons WHERE promotion_id = ?", Boolean.class, UUID.fromString(id)));
        perform(get(base(shop) + "/promotions?status=PAUSED"), shop.token).andExpect(jsonPath("$.items[*].code", contains("TINZ15")));
        perform(post(base(shop) + "/promotions/" + id + "/resume"), shop.token).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

        // used in an order -> cannot delete (409), counters reflect usage, usage limit cannot go below used
        jdbc.update("INSERT INTO promotion_usages (id, promotion_id, order_id, discount_amount, used_at, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(id), UUID.randomUUID(), new BigDecimal("42.50"), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO promotion_usages (id, promotion_id, order_id, discount_amount, used_at, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(id), UUID.randomUUID(), new BigDecimal("10.00"), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        perform(get(base(shop) + "/promotions/" + id), shop.token).andExpect(jsonPath("$.usedCount").value(2)).andExpect(jsonPath("$.discountTotal").value(52.5));
        perform(get(base(shop) + "/promotions"), shop.token).andExpect(jsonPath("$.summary.usageCount").value(2));
        perform(put(base(shop) + "/promotions/" + id), shop.token, body("TINZ15", "PERCENT", "15", "\"usageLimitTotal\":1"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("USAGE_LIMIT_BELOW_USED"));
        perform(delete(base(shop) + "/promotions/" + id), shop.token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROMOTION_IN_USE"));

        String old = JsonPath.read(perform(get(base(shop) + "/promotions?status=EXPIRED"), shop.token).andReturn().getResponse().getContentAsString(), "$.items[0].id");
        perform(delete(base(shop) + "/promotions/" + old), shop.token).andExpect(status().isNoContent());
        perform(get(base(shop) + "/promotions/" + old), shop.token).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROMOTION_NOT_FOUND"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM coupons WHERE promotion_id = ?", Integer.class, UUID.fromString(old)));
    }

    @Test
    void validationUsesTheStandardErrorFormat() throws Exception {
        var shop = openShop("pr-valid@example.com", "0815000002", "ร้านตรวจ", "Valid Shop", "1234567890402");
        perform(post(base(shop) + "/promotions"), shop.token, "{}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.fieldErrors[*].code", hasItems("NAME_REQUIRED", "CODE_REQUIRED", "TYPE_REQUIRED", "STARTS_AT_REQUIRED")));
        perform(post(base(shop) + "/promotions"), shop.token, body("A B", "PERCENT", "0", null)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].code", hasItems("CODE_INVALID", "PERCENT_OUT_OF_RANGE")));
        perform(post(base(shop) + "/promotions"), shop.token, body("P100", "PERCENT", "100", null)).andExpect(jsonPath("$.fieldErrors[0].code").value("PERCENT_OUT_OF_RANGE"));
        perform(post(base(shop) + "/promotions"), shop.token, body("PX", "BOGO", "1", null)).andExpect(jsonPath("$.fieldErrors[*].code", hasItems("TYPE_INVALID", "CODE_INVALID")));
        perform(post(base(shop) + "/promotions"), shop.token, body("FIX1", "FIXED_AMOUNT", "0", null)).andExpect(jsonPath("$.fieldErrors[0].code").value("AMOUNT_INVALID"));
        perform(post(base(shop) + "/promotions"), shop.token, body("FIX2", "FIXED_AMOUNT", "10.555", null)).andExpect(jsonPath("$.fieldErrors[0].code").value("AMOUNT_SCALE_INVALID"));
        perform(post(base(shop) + "/promotions"), shop.token, body("FIX3", "FIXED_AMOUNT", "100", "\"maxDiscountAmount\":50")).andExpect(jsonPath("$.fieldErrors[0].code").value("MAX_DISCOUNT_NOT_APPLICABLE"));
        perform(post(base(shop) + "/promotions"), shop.token, body("FIX4", "FIXED_AMOUNT", "500", "\"minOrderAmount\":100")).andExpect(jsonPath("$.fieldErrors[0].code").value("DISCOUNT_EXCEEDS_MIN_ORDER"));
        perform(post(base(shop) + "/promotions"), shop.token, body("PCT1", "PERCENT", "10", "\"maxDiscountAmount\":0,\"minOrderAmount\":-1")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].code", hasItems("MAX_DISCOUNT_INVALID", "MIN_ORDER_INVALID")));
        perform(post(base(shop) + "/promotions"), shop.token, body("PCT2", "PERCENT", "10", "\"usageLimitTotal\":0,\"usageLimitPerBuyer\":0")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].code", hasItems("USAGE_LIMIT_INVALID", "PER_BUYER_LIMIT_INVALID")));
        perform(post(base(shop) + "/promotions"), shop.token, body("PCT3", "PERCENT", "10", "\"usageLimitTotal\":5,\"usageLimitPerBuyer\":6"))
            .andExpect(jsonPath("$.fieldErrors[0].code").value("PER_BUYER_EXCEEDS_TOTAL"));
        perform(post(base(shop) + "/promotions"), shop.token, """
            {"name":"x","code":"DATE1","type":"PERCENT","value":5,"startsAt":"2026-11-11T00:00","endsAt":"2026-11-10T23:59"}
            """).andExpect(jsonPath("$.fieldErrors[0].field").value("endsAt")).andExpect(jsonPath("$.fieldErrors[0].code").value("ENDS_BEFORE_START"));
        perform(post(base(shop) + "/promotions"), shop.token, """
            {"name":"x","code":"DATE2","type":"PERCENT","value":5,"startsAt":"11/11/2026"}
            """).andExpect(jsonPath("$.fieldErrors[0].code").value("DATE_INVALID"));
        perform(post(base(shop) + "/promotions"), shop.token, "{\"name\":\"" + "ก".repeat(151) + "\",\"code\":\"LONG1\",\"type\":\"PERCENT\",\"value\":5,\"startsAt\":\"2026-11-11T00:00\"}")
            .andExpect(jsonPath("$.fieldErrors[0].code").value("NAME_TOO_LONG"));

        // per-shop unique code, case-insensitive; same code is fine in another shop; updating itself keeps its code
        String id = JsonPath.read(perform(post(base(shop) + "/promotions"), shop.token, body("WELCOME50", "FIXED_AMOUNT", "50", null))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        perform(post(base(shop) + "/promotions"), shop.token, body("welcome50", "PERCENT", "5", null))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROMOTION_CODE_TAKEN")).andExpect(jsonPath("$.fieldErrors[0].field").value("code"));
        perform(put(base(shop) + "/promotions/" + id), shop.token, body("Welcome50", "FIXED_AMOUNT", "60", null)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("WELCOME50"));
        var other = openShop("pr-valid2@example.com", "0815000003", "ร้านอื่น", "Other Shop", "1234567890403");
        perform(post(base(other) + "/promotions"), other.token, body("WELCOME50", "FIXED_AMOUNT", "50", null)).andExpect(status().isCreated());
    }

    @Test
    void permissionsAndShopIsolation() throws Exception {
        var shop = openShop("pr-perm@example.com", "0815000004", "ร้านสิทธิ์", "Perm Shop", "1234567890404");
        var other = openShop("pr-perm2@example.com", "0815000005", "ร้านคู่แข่ง", "Rival Shop", "1234567890405");
        String id = JsonPath.read(perform(post(base(shop) + "/promotions"), shop.token, body("SALE10", "PERCENT", "10", null))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        var viewerToken = token("pr-viewer@example.com", "0815000006");
        addMember(shop.shopId, users.findByEmailIgnoreCaseOrPhone("pr-viewer@example.com", "0815000006").orElseThrow().getId(), "STORE_VIEWER");
        perform(get(base(shop) + "/promotions"), viewerToken).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        perform(get(base(shop) + "/promotions/" + id), viewerToken).andExpect(status().isOk());
        perform(post(base(shop) + "/promotions"), viewerToken, body("SALE20", "PERCENT", "20", null)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        perform(put(base(shop) + "/promotions/" + id), viewerToken, body("SALE10", "PERCENT", "11", null)).andExpect(status().isForbidden());
        perform(post(base(shop) + "/promotions/" + id + "/pause"), viewerToken).andExpect(status().isForbidden());
        perform(delete(base(shop) + "/promotions/" + id), viewerToken).andExpect(status().isForbidden());

        var warehouseToken = token("pr-wh@example.com", "0815000007");
        addMember(shop.shopId, users.findByEmailIgnoreCaseOrPhone("pr-wh@example.com", "0815000007").orElseThrow().getId(), "STORE_WAREHOUSE");
        perform(get(base(shop) + "/promotions"), warehouseToken).andExpect(status().isForbidden());

        var managerToken = token("pr-mgr@example.com", "0815000008");
        addMember(shop.shopId, users.findByEmailIgnoreCaseOrPhone("pr-mgr@example.com", "0815000008").orElseThrow().getId(), "STORE_MANAGER");
        perform(post(base(shop) + "/promotions/" + id + "/pause"), managerToken).andExpect(status().isOk());

        perform(get(base(shop) + "/promotions"), other.token).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));
        perform(get(base(other) + "/promotions/" + id), other.token).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROMOTION_NOT_FOUND"));
        perform(put(base(other) + "/promotions/" + id), other.token, body("SALE10", "PERCENT", "11", null)).andExpect(status().isNotFound());
        perform(delete(base(other) + "/promotions/" + id), other.token).andExpect(status().isNotFound());
        mvc.perform(get(base(shop) + "/promotions")).andExpect(status().isUnauthorized());
    }

    @Test
    void storefrontListsOnlyActiveInDatePublicPromotionsWithSafeFields() throws Exception {
        var shop = openShop("pr-public@example.com", "0815000009", "ร้านหน้าร้าน", "Storefront Shop", "1234567890406");
        var slug = shops.findByShopId(shop.shopId).orElseThrow().getSlug();
        perform(post(base(shop) + "/promotions"), shop.token, body("SHOWN10", "PERCENT", "10", "\"maxDiscountAmount\":100,\"usageLimitTotal\":50")).andExpect(status().isCreated());
        perform(post(base(shop) + "/promotions"), shop.token, body("HIDDEN", "PERCENT", "10", "\"showOnStorefront\":false")).andExpect(status().isCreated());
        String paused = JsonPath.read(perform(post(base(shop) + "/promotions"), shop.token, body("PAUSED", "PERCENT", "10", null)).andReturn().getResponse().getContentAsString(), "$.id");
        perform(post(base(shop) + "/promotions/" + paused + "/pause"), shop.token).andExpect(status().isOk());
        perform(post(base(shop) + "/promotions"), shop.token, """
            {"name":"later","code":"LATER","type":"PERCENT","value":5,"startsAt":"%s"}
            """.formatted(local(Instant.now().plus(3, ChronoUnit.DAYS)))).andExpect(status().isCreated());
        perform(post(base(shop) + "/promotions"), shop.token, """
            {"name":"old","code":"GONE","type":"PERCENT","value":5,"startsAt":"2025-01-01T00:00","endsAt":"2025-01-02T00:00"}
            """).andExpect(status().isCreated());
        String used = JsonPath.read(perform(post(base(shop) + "/promotions"), shop.token, body("USEDUP", "FIXED_AMOUNT", "20", "\"usageLimitTotal\":1"))
            .andReturn().getResponse().getContentAsString(), "$.id");
        jdbc.update("INSERT INTO promotion_usages (id, promotion_id, order_id, discount_amount, used_at, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(used), UUID.randomUUID(), new BigDecimal("20.00"), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        perform(post(base(shop) + "/promotions"), shop.token, body("SHIPFREE", "FREE_SHIPPING", "0", "\"minOrderAmount\":399")).andExpect(status().isCreated());

        mvc.perform(get("/api/shops/" + slug + "/promotions")).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].code", containsInAnyOrder("SHOWN10", "SHIPFREE")))
            .andExpect(jsonPath("$[0].name").exists())
            .andExpect(jsonPath("$[0].id").doesNotExist())
            .andExpect(jsonPath("$[0].usedCount").doesNotExist())
            .andExpect(jsonPath("$[0].usageLimitTotal").doesNotExist())
            .andExpect(jsonPath("$[0].discountTotal").doesNotExist())
            .andExpect(jsonPath("$[0].active").doesNotExist())
            .andExpect(jsonPath("$[0].showOnStorefront").doesNotExist());
        mvc.perform(get("/api/shops/no-such-shop/promotions")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));

        // suspended / not approved shop -> 404 even with promotions
        jdbc.update("UPDATE shops SET status = 'SUSPENDED' WHERE slug = ?", slug);
        mvc.perform(get("/api/shops/" + slug + "/promotions")).andExpect(status().isNotFound());
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String base(OpenShop shop) {
        return "/api/merchant/shops/" + shop.shopId;
    }

    private void addMember(long shopNo, UUID userId, String role) {
        var shopUuid = shops.findByShopId(shopNo).orElseThrow().getUuid();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO shop_members (id, shop_id, user_id, role, status, joined_at, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)",
            UUID.randomUUID(), shopUuid, userId, role, now, now, now);
    }

    private OpenShop openShop(String email, String phone, String shopName, String shopNameEn, String citizenId) throws Exception {
        var seller = token(email, phone);
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
            {"shopName":"%s","shopNameEn":"%s","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
            """.formatted(shopName, shopNameEn))).andExpect(status().isOk());
        mvc.perform(put("/api/merchant-applications/draft/owner").header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
            {"ownerType":"PERSON","title":"นาย","firstName":"สมชาย","lastName":"ขายของ","citizenId":"%s","birthDate":"1980-08-15","nationality":"ไทย","phone":"%s","email":"%s","houseNo":"9","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500","bankName":"KBANK","bankAccountNumber":"1234567890","bankAccountName":"สมชาย ขายของ"}
            """.formatted(citizenId, phone, email))).andExpect(status().isOk());
        uploadDocument(seller, "IDENTITY");
        uploadDocument(seller, "ADDRESS");
        mvc.perform(post("/api/merchant-applications/draft/submit").header("Authorization", "Bearer " + seller)).andExpect(status().isOk());
        var listed = mvc.perform(get("/api/merchant/shops").header("Authorization", "Bearer " + seller)).andExpect(status().isOk()).andReturn();
        Number shopId = JsonPath.read(listed.getResponse().getContentAsString(), "$[0].shopId");
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve").header("Authorization", "Bearer " + adminToken())).andExpect(status().isOk());
        return new OpenShop(seller, shopId.longValue());
    }

    /** One platform admin per test: system accounts share a placeholder phone number. */
    private String adminToken() throws Exception {
        if (adminToken == null) {
            var approver = users.save(UserAccount.systemAccount("pr-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"pr-approver@example.com","password":"Password1"}
                """)).andExpect(status().isOk()).andReturn();
            adminToken = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
        }
        return adminToken;
    }

    private String token(String email, String phone) throws Exception {
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"สมชาย","lastName":"ขายของ","email":"%s","phone":"%s","password":"Password1"}
            """.formatted(email, phone))).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
    }

    private void uploadDocument(String token, String type) throws Exception {
        var file = new MockMultipartFile("file", type.toLowerCase() + ".png", "image/png", PNG);
        mvc.perform(multipart("/api/merchant-applications/draft/documents/" + type).file(file).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private record OpenShop(String token, long shopId) {}
}