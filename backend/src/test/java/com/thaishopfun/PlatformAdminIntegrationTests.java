package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.admin.AdminAuditLogService;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PermissionMatrix;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.auth.authz.RequirePermission;
import com.thaishopfun.cart.CartItemRepository;
import com.thaishopfun.cart.CartRepository;
import com.thaishopfun.cart.CouponRepository;
import com.thaishopfun.cart.PromotionRepository;
import com.thaishopfun.cart.PromotionUsageRepository;
import com.thaishopfun.cart.ShippingMethodRepository;
import com.thaishopfun.catalog.InventoryLedgerRepository;
import com.thaishopfun.catalog.InventoryLevelRepository;
import com.thaishopfun.catalog.ProductImageRepository;
import com.thaishopfun.catalog.ProductOptionGroupRepository;
import com.thaishopfun.catalog.ProductOptionValueRepository;
import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.catalog.ProductSkuOptionValueRepository;
import com.thaishopfun.catalog.ProductSkuRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import com.thaishopfun.merchant.OwnerAuditLogRepository;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ProductCategory;
import com.thaishopfun.merchant.ProductCategoryRepository;
import com.thaishopfun.merchant.ShopMemberRepository;
import com.thaishopfun.merchant.ShopReinstatementRequestRepository;
import com.thaishopfun.merchant.ShopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Platform admin API: merchants / shops / users views, plans & usage, merchant + shop suspension (reason, idempotency,
 * 409, enforced effects), audit logs (writer + query API), and the separation of platform permissions from shop roles
 * (every /api/admin/** endpoint is 403 for a merchant owner and for platform roles without the permission).
 * Fixtures copied from ShippingIntegrationTests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformAdminIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final String ADDRESS = """
        {"shippingAddress":{"recipientName":"สมหญิง ผู้รับ","phone":"0812345678","addressLine1":"1 ถนนสุขุมวิท",
         "subdistrict":"คลองเตย","district":"คลองเตย","province":"กรุงเทพมหานคร","postalCode":"10110"}}""";
    private static final String REASON = "{\"reason\":\"ละเมิดนโยบายการขายสินค้า\",\"reasonCode\":\"POLICY_VIOLATION\"}";
    private static final String RESTORE = "{\"reason\":\"แก้ไขตามนโยบายเรียบร้อยแล้ว\"}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PermissionMatrix matrix;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired CouponRepository coupons;
    @Autowired PromotionRepository promotions;
    @Autowired PromotionUsageRepository usages;
    @Autowired ShippingMethodRepository shippingMethods;
    @Autowired InventoryLedgerRepository ledger;
    @Autowired InventoryLevelRepository levels;
    @Autowired ProductImageRepository productImages;
    @Autowired ProductSkuRepository productSkus;
    @Autowired ProductRepository catalogProducts;
    @Autowired ProductOptionGroupRepository optionGroups;
    @Autowired ProductOptionValueRepository optionValues;
    @Autowired ProductSkuOptionValueRepository skuOptions;
    @Autowired ProductCategoryRepository categories;
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
        wipeOrders();
        wipe();
    }

    @AfterEach
    void cleanAfter() {
        wipeOrders();
        wipe();
    }

    private void wipeOrders() {
        for (var t : List.of("shipment_tracking_events", "shipments", "shop_carriers", "merchant_order_confirmations", "order_item_reveal_logs", "order_financial_snapshots", "refund_status_history", "payment_status_history",
                "payment_webhook_events", "payment_proofs", "refunds", "payments", "media_files", "shop_payment_gateways",
                "order_status_history", "inventory_reservations",
                "promotion_usages", "order_items", "order_addresses")) jdbc.update("DELETE FROM " + t);
        ledger.deleteAll();
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM shop_customers");
    }

    private void wipe() {
        cartItems.deleteAll();
        carts.deleteAll();
        usages.deleteAll();
        coupons.deleteAll();
        promotions.deleteAll();
        shippingMethods.deleteAll();
        skuOptions.deleteAll();
        optionValues.deleteAll();
        optionGroups.deleteAll();
        ledger.deleteAll();
        levels.deleteAll();
        productImages.deleteAll();
        productSkus.deleteAll();
        catalogProducts.deleteAll();
        reinstatements.deleteAll();
        shopMembers.deleteAll();
        shops.deleteAll();
        documents.deleteAll();
        applications.deleteAll();
        ownerAudits.deleteAll();
        owners.deleteAll();
        refreshTokens.deleteAll();
        platformRoles.deleteAll();
        jdbc.update("DELETE FROM auth_role_permissions WHERE role_id IN (SELECT id FROM auth_roles WHERE code LIKE 'CUSTOM\\_%' ESCAPE '\\')");
        jdbc.update("DELETE FROM auth_roles WHERE code LIKE 'CUSTOM\\_%' ESCAPE '\\'");
        jdbc.update("DELETE FROM audit_logs");
        users.deleteAll();
        matrix.invalidate();
    }

    // ======================================================================
    // 5. platform permissions vs merchant (shop) roles
    // ======================================================================

    /** handler methods mapped under /api/admin/** with one concrete request per (method, pattern) */
    private record AdminEndpoint(String verb, String pattern, String permission) {
        String uri() { return pattern.replaceAll("\\{[^}]+}", "1"); }
    }

    private List<AdminEndpoint> adminEndpoints() {
        var list = new ArrayList<AdminEndpoint>();
        for (Map.Entry<org.springframework.web.servlet.mvc.method.RequestMappingInfo, HandlerMethod> e : mappings.getHandlerMethods().entrySet()) {
            var info = e.getKey();
            var patterns = info.getPathPatternsCondition() == null ? Set.<String>of() : info.getPathPatternsCondition().getPatternValues();
            for (var pattern : patterns) {
                if (!pattern.startsWith("/api/admin")) continue;
                var method = e.getValue();
                var rule = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequirePermission.class);
                if (rule == null) rule = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequirePermission.class);
                var verbs = info.getMethodsCondition().getMethods();
                for (var verb : verbs.isEmpty() ? Set.of(org.springframework.web.bind.annotation.RequestMethod.GET) : verbs) {
                    list.add(new AdminEndpoint(verb.name(), pattern, rule == null ? null : rule.value()));
                }
            }
        }
        list.sort(Comparator.comparing(AdminEndpoint::pattern).thenComparing(AdminEndpoint::verb));
        return list;
    }

    @Test
    void everyAdminEndpointRequiresAPlatformScopePermission() {
        var endpoints = adminEndpoints();
        assertTrue(endpoints.size() >= 50, "expected all admin endpoints, got " + endpoints.size());
        var platformCodes = new HashSet<>(jdbc.queryForList("SELECT code FROM auth_permissions WHERE scope = 'PLATFORM' AND is_active = TRUE", String.class));
        for (var endpoint : endpoints) {
            assertNotNull(endpoint.permission(), endpoint + " has no @RequirePermission");
            assertTrue(endpoint.permission().startsWith("PLATFORM_") && platformCodes.contains(endpoint.permission()),
                endpoint + " must use an existing PLATFORM-scope code");
        }
        // the new endpoints are among them
        var patterns = endpoints.stream().map(AdminEndpoint::pattern).toList();
        for (var p : List.of("/api/admin/merchants", "/api/admin/merchants/{merchantId}/suspend", "/api/admin/shops/{shopId}/reactivate",
                "/api/admin/plans", "/api/admin/plans/usage", "/api/admin/audit-logs", "/api/admin/audit-logs/{id}")) {
            assertTrue(patterns.contains(p), p);
        }
    }

    @Test
    void merchantOwnerGets403OnEveryAdminEndpoint() throws Exception {
        var shop = openShop("own-403@example.com", "0816000001", "ร้านเจ้าของ", "OwnerShop", "1234567890501", true);
        // the owner holds every store permission in their shop ...
        call(get("/api/merchant/shops/" + shop.shopId() + "/products"), shop.token()).andExpect(status().isOk());
        // ... and none of them reaches the platform API
        var endpoints = adminEndpoints();
        for (var endpoint : endpoints) {
            var request = request(HttpMethod.valueOf(endpoint.verb()), endpoint.uri()).header("Authorization", "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON).content("{}");
            var result = mvc.perform(request).andReturn().getResponse();
            assertEquals(403, result.getStatus(), endpoint + " -> " + result.getContentAsString());
            assertTrue(result.getContentAsString().contains("PERMISSION_DENIED"), endpoint + " -> " + result.getContentAsString());
        }
        // a plain buyer as well (spot check)
        var buyer = token("own-403-buyer@example.com", "0817000001");
        call(get("/api/admin/shops"), buyer).andExpect(status().isForbidden());
        call(get("/api/admin/audit-logs"), buyer).andExpect(status().isForbidden());
    }

    @Test
    void misSeededPlatformCodeOnShopOrBuyerRoleStillDoesNotGrantPlatformAccess() throws Exception {
        var shop = openShop("seed-403@example.com", "0816000002", "ร้านซีด", "SeedShop", "1234567890502", true);
        var buyer = token("seed-403-buyer@example.com", "0817000002");
        // simulate a wrong mapping row: PLATFORM_SHOP_READ given to STORE_OWNER and to the implicit BUYER role
        var permissionId = jdbc.queryForObject("SELECT id FROM auth_permissions WHERE code = 'PLATFORM_SHOP_READ'", UUID.class);
        var inserted = new ArrayList<UUID>();
        for (var role : List.of("STORE_OWNER", "BUYER")) {
            var roleId = jdbc.queryForObject("SELECT id FROM auth_roles WHERE code = ?", UUID.class, role);
            var id = UUID.randomUUID();
            var now = Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version, created_date_time, modified_date_time) VALUES (?, ?, ?, 'TEST', ?, ?)",
                id, roleId, permissionId, now, now);
            inserted.add(id);
        }
        matrix.invalidate();
        try {
            call(get("/api/admin/shops"), shop.token()).andExpect(status().isForbidden());
            call(get("/api/admin/shops"), buyer).andExpect(status().isForbidden());
            call(get("/api/admin/shops"), adminToken()).andExpect(status().isOk());
        } finally {
            for (var id : inserted) jdbc.update("DELETE FROM auth_role_permissions WHERE id = ?", id);
            matrix.invalidate();
        }
    }

    @Test
    void platformRoleWithoutThePermissionGets403() throws Exception {
        // custom platform role holding only PLATFORM_ACCOUNT_READ
        var created = call(post("/api/admin/roles"), adminToken(),
            "{\"context\":\"PLATFORM\",\"nameTh\":\"ซัพพอร์ตอ่านอย่างเดียว\",\"permissions\":[\"PLATFORM_ACCOUNT_READ\"]}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(created, "$.role.code");
        var supportToken = token("support-ro@example.com", "0817000003");
        var supportId = userId(supportToken);
        call(put("/api/admin/users/" + supportId + "/role"), adminToken(), "{\"roleCode\":\"" + code + "\"}").andExpect(status().isOk());
        supportToken = login("support-ro@example.com"); // permission_version changed
        var shop = openShop("support-shop@example.com", "0816000003", "ร้านซัพ", "SupShop", "1234567890503", true);

        call(get("/api/admin/users"), supportToken).andExpect(status().isOk());
        call(get("/api/admin/shops"), supportToken).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        call(get("/api/admin/merchants"), supportToken).andExpect(status().isForbidden());
        call(get("/api/admin/plans/usage"), supportToken).andExpect(status().isForbidden());
        call(get("/api/admin/audit-logs"), supportToken).andExpect(status().isForbidden());
        call(post("/api/admin/shops/" + shop.shopId() + "/suspend"), supportToken, REASON).andExpect(status().isForbidden());
        // every admin endpoint that needs another code is 403 for this role
        for (var endpoint : adminEndpoints()) {
            if ("PLATFORM_ACCOUNT_READ".equals(endpoint.permission())) continue;
            var status = mvc.perform(request(HttpMethod.valueOf(endpoint.verb()), endpoint.uri()).header("Authorization", "Bearer " + supportToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
            assertEquals(403, status, endpoint.toString());
        }
        // a platform role does not grant shop-scope access either (not a member of the shop -> 404 like everyone else)
        call(get("/api/merchant/shops/" + shop.shopId() + "/products"), supportToken).andExpect(status().isNotFound());
        assertEquals("SUSPENDED".equals(shopStatus(shop.shopId())), false);
    }

    // ======================================================================
    // 1. + 2. views: shops, merchants, users, plans & usage
    // ======================================================================

    @Test
    void shopsListFiltersPaginatesAndDetailShowsMembersCountsAndUsage() throws Exception {
        var a = openShop("view-a@example.com", "0816000011", "ร้านเอ", "AlphaShop", "1234567890511", true);
        var b = openShop("view-b@example.com", "0816000012", "ร้านบี", "BetaShop", "1234567890512", true);
        var pending = openShop("view-p@example.com", "0816000013", "ร้านรอ", "PendingShop", "1234567890513", false);
        publishedVariantProduct(a, "VA");
        var staff = token("view-staff@example.com", "0817000011");
        addMember(a.shopId(), userId(staff), "STORE_ADMIN");

        var all = call(get("/api/admin/shops?size=2&sort=shopId&dir=asc"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(3)).andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].shopId").value(a.shopId()))
            .andReturn().getResponse().getContentAsString();
        assertEquals(2, (int) JsonPath.read(all, "$.items[0].activeMembers"));
        assertEquals(1, (int) JsonPath.read(all, "$.items[0].products"));
        call(get("/api/admin/shops?status=PENDING_REVIEW"), adminToken()).andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].shopId").value(pending.shopId())).andExpect(jsonPath("$.items[0].status").value("PENDING_REVIEW"));
        call(get("/api/admin/shops?q=beta"), adminToken()).andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].shopId").value(b.shopId()));
        call(get("/api/admin/shops?q=" + b.shopId()), adminToken()).andExpect(jsonPath("$.items[0].shopId").value(b.shopId()));
        call(get("/api/admin/shops?status=NOPE"), adminToken()).andExpect(status().isBadRequest());

        call(get("/api/admin/shops/" + a.shopId()), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.merchantStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[?(@.role == 'STORE_ADMIN')].email").value("view-staff@example.com"))
            .andExpect(jsonPath("$.usage.products.total").value(1))
            .andExpect(jsonPath("$.usage.products.published").value(1))
            .andExpect(jsonPath("$.usage.products.activeSkus").value(3))
            .andExpect(jsonPath("$.usage.products.images").value(1))
            .andExpect(jsonPath("$.usage.staff.active").value(2))
            .andExpect(jsonPath("$.usage.staff.byRole.STORE_OWNER").value(1))
            .andExpect(jsonPath("$.usage.storage.documents").value(2))
            .andExpect(jsonPath("$.usage.planCode").doesNotExist());
        call(get("/api/admin/shops/999999"), adminToken()).andExpect(status().isNotFound());

        call(get("/api/admin/plans"), adminToken()).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("NONE"))
            .andExpect(jsonPath("$.plans.length()").value(0));
        call(get("/api/admin/plans/usage?sort=shopId&dir=asc"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(3)).andExpect(jsonPath("$.items[0].shopId").value(a.shopId()))
            .andExpect(jsonPath("$.items[0].products.total").value(1)).andExpect(jsonPath("$.items[1].products.total").value(0));
        call(get("/api/admin/shops/" + a.shopId() + "/usage"), adminToken()).andExpect(jsonPath("$.orders.total").value(0));
    }

    @Test
    void merchantsListAndDetailWithoutSensitiveFields() throws Exception {
        var a = openShop("mer-a@example.com", "0816000021", "ร้านผู้ขายเอ", "MerA", "1234567890521", true);
        openShop("mer-b@example.com", "0816000022", "ร้านผู้ขายบี", "MerB", "1234567890522", false);
        var list = call(get("/api/admin/merchants?sort=createdAt&dir=asc"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(2)).andReturn().getResponse().getContentAsString();
        String merchantId = JsonPath.read(list, "$.items[0].merchantId");
        assertEquals(1, (int) JsonPath.read(list, "$.items[0].approvedShops"));
        call(get("/api/admin/merchants?shopStatus=PENDING_REVIEW"), adminToken()).andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].email").value("mer-b@example.com"));
        call(get("/api/admin/merchants?q=mer-a@"), adminToken()).andExpect(jsonPath("$.totalItems").value(1));
        call(get("/api/admin/merchants?status=WRONG"), adminToken()).andExpect(status().isBadRequest());
        var detail = call(get("/api/admin/merchants/" + merchantId), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.shops[0].shopId").value(a.shopId()))
            .andExpect(jsonPath("$.counts.shops").value(1))
            .andExpect(jsonPath("$.user.email").value("mer-a@example.com"))
            .andReturn().getResponse().getContentAsString();
        assertFalse(detail.toLowerCase().contains("citizen"), detail);
        assertFalse(detail.toLowerCase().contains("bankaccount"), detail);
        assertFalse(detail.contains("1234567890521"), detail);
        call(get("/api/admin/merchants/" + UUID.randomUUID()), adminToken()).andExpect(status().isNotFound());
    }

    @Test
    void userDetailHasCountsAndSuspendRecordsOptionalReason() throws Exception {
        var shop = openShop("usr-a@example.com", "0816000031", "ร้านยูส", "UsrShop", "1234567890531", true);
        var id = userId(shop.token());
        call(get("/api/admin/users/" + id), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.counts.ownedShops").value(1)).andExpect(jsonPath("$.counts.activeMemberships").value(1))
            .andExpect(jsonPath("$.counts.merchantProfiles").value(1)).andExpect(jsonPath("$.shops[0].role").value("STORE_OWNER"));
        call(post("/api/admin/users/" + id + "/suspend").header("User-Agent", "AdminTest/1.0"), adminToken(), "{\"reason\":\"สแปมลูกค้า\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        // existing UI call without a body still works
        call(post("/api/admin/users/" + id + "/restore"), adminToken()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        call(get("/api/admin/audit-logs?resourceId=" + id + "&action=ACCOUNT_SUSPENDED"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].reason").value("สแปมลูกค้า"))
            .andExpect(jsonPath("$.items[0].before.status").value("ACTIVE"))
            .andExpect(jsonPath("$.items[0].after.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.items[0].userAgent").value("AdminTest/1.0"))
            .andExpect(jsonPath("$.items[0].ipAddress").value("127.0.0.1"))
            .andExpect(jsonPath("$.items[0].actor.email").value("pla-admin@example.com"))
            .andExpect(jsonPath("$.items[0].kind").value("BUSINESS"));
    }

    // ======================================================================
    // 3. + 4. shop suspension: reason, idempotency, 409, effects, audit
    // ======================================================================

    @Test
    void shopSuspendRequiresReasonIsIdempotentAndAudited() throws Exception {
        var shop = openShop("sus-a@example.com", "0816000041", "ร้านระงับ", "SusShop", "1234567890541", true);
        var pending = openShop("sus-p@example.com", "0816000042", "ร้านรอระงับ", "SusPending", "1234567890542", false);
        var url = "/api/admin/shops/" + shop.shopId();

        call(post(url + "/suspend"), adminToken(), "{}").andExpect(status().isBadRequest());
        call(post(url + "/suspend"), adminToken(), "{\"reason\":\"สั้น\"}").andExpect(status().isBadRequest());
        call(post(url + "/suspend"), adminToken(), "{\"reason\":\"เหตุผลยาวพอสมควร\",\"reasonCode\":\"bad code\"}").andExpect(status().isBadRequest());
        call(post(url + "/suspend").header("X-Request-Id", "req-sus-1"), adminToken(), REASON).andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(true)).andExpect(jsonPath("$.shop.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.shop.suspension.reasonCode").value("POLICY_VIOLATION"))
            .andExpect(jsonPath("$.shop.suspension.reason").value("ละเมิดนโยบายการขายสินค้า"))
            .andExpect(jsonPath("$.shop.suspension.suspendedByName").exists());
        var row = jdbc.queryForMap("SELECT status, suspend_reason, suspend_reason_code, suspended_by, suspended_at FROM shops WHERE shop_no = ?", shop.shopId());
        assertEquals("SUSPENDED", row.get("status"));
        assertEquals("ละเมิดนโยบายการขายสินค้า", row.get("suspend_reason"));
        assertEquals("POLICY_VIOLATION", row.get("suspend_reason_code"));
        assertNotNull(row.get("suspended_by"));
        assertNotNull(row.get("suspended_at"));
        // idempotent: repeat is a no-op without a second audit row
        call(post(url + "/suspend"), adminToken(), "{\"reason\":\"อีกเหตุผลหนึ่งที่ต่างออกไป\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(false)).andExpect(jsonPath("$.shop.suspension.reasonCode").value("POLICY_VIOLATION"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_logs WHERE action = 'SHOP_SUSPENDED'"));
        call(get("/api/admin/audit-logs?shopId=" + shop.shopId() + "&kind=BUSINESS"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(2)) // SHOP_APPROVED (fixture) + SHOP_SUSPENDED, newest first
            .andExpect(jsonPath("$.items[0].action").value("SHOP_SUSPENDED"))
            .andExpect(jsonPath("$.items[0].target.type").value("SHOP"))
            .andExpect(jsonPath("$.items[0].target.shopId").value(shop.shopId()))
            .andExpect(jsonPath("$.items[0].permission").value("PLATFORM_SHOP_SUSPEND"))
            .andExpect(jsonPath("$.items[0].before.status").value("APPROVED"))
            .andExpect(jsonPath("$.items[0].after.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.items[0].reasonCode").value("POLICY_VIOLATION"))
            .andExpect(jsonPath("$.items[0].requestId").value("req-sus-1"));
        // the HTTP-level row also carries the IP now
        call(get("/api/admin/audit-logs?kind=HTTP&action=POST /api/admin/shops/" + shop.shopId() + "/suspend"), adminToken())
            .andExpect(jsonPath("$.items[0].ipAddress").value("127.0.0.1"));

        // 409 on invalid status
        call(post("/api/admin/shops/" + pending.shopId() + "/suspend"), adminToken(), REASON).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SHOP_NOT_APPROVED"));
        call(post("/api/admin/shops/" + pending.shopId() + "/reactivate"), adminToken(), RESTORE).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SHOP_NOT_SUSPENDED"));
        call(post("/api/admin/shops/999999/suspend"), adminToken(), REASON).andExpect(status().isNotFound());

        // reactivate: reason required, idempotent, V1 restored_* columns
        call(post(url + "/reactivate"), adminToken(), "{}").andExpect(status().isBadRequest());
        call(post(url + "/reactivate"), adminToken(), RESTORE).andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(true)).andExpect(jsonPath("$.shop.status").value("APPROVED"));
        call(post(url + "/reactivate"), adminToken(), RESTORE).andExpect(jsonPath("$.changed").value(false));
        assertNotNull(jdbc.queryForObject("SELECT restored_by FROM shops WHERE shop_no = ?", Object.class, shop.shopId()));
        call(get("/api/admin/audit-logs?action=SHOP_REACTIVATED"), adminToken()).andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].reason").value("แก้ไขตามนโยบายเรียบร้อยแล้ว"))
            .andExpect(jsonPath("$.items[0].reasonCode").value("ADMIN_RESTORE"));
        call(get("/api/admin/audit-logs?action=SHOP_*&size=1"), adminToken()).andExpect(jsonPath("$.totalItems").value(3))
            .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].action").value("SHOP_REACTIVATED"));
    }

    @Test
    void suspendedShopCannotSellCheckoutOrBeManaged() throws Exception {
        var shop = openShop("eff-a@example.com", "0816000051", "ร้านผล", "EffShop", "1234567890551", true);
        var product = publishedVariantProduct(shop, "EF");
        String productId = JsonPath.read(product, "$.id");
        var sku = sku(product, "EF-BLK-S");
        var buyer = token("eff-buyer@example.com", "0817000051");
        add(buyer, sku, 1);
        var slug = JsonPath.read(call(get("/api/admin/shops/" + shop.shopId()), adminToken()).andReturn().getResponse().getContentAsString(), "$.slug");
        call(get("/api/shops/" + slug), null).andExpect(status().isOk());

        call(post("/api/admin/shops/" + shop.shopId() + "/suspend"), adminToken(), REASON).andExpect(status().isOk());

        // cannot sell: storefront hidden, add to cart and checkout refused
        call(get("/api/shops/" + slug), null).andExpect(status().isNotFound());
        call(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":1}".formatted(sku)).andExpect(status().isConflict());
        call(post("/api/checkout").header("Idempotency-Key", "sus-" + UUID.randomUUID()), buyer, ADDRESS).andExpect(status().isConflict());
        assertEquals(0, count("SELECT COUNT(*) FROM orders"));
        // cannot manage: every shop-scope write is 403 SHOP_SUSPENDED (owner and super admin), reads still work
        var base = "/api/merchant/shops/" + shop.shopId();
        call(get(base + "/orders"), shop.token()).andExpect(status().isOk());
        call(get(base + "/shipping-methods"), shop.token()).andExpect(status().isOk());
        call(post(base + "/products/" + productId + "/archive"), shop.token()).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SHOP_SUSPENDED"));
        call(post(base + "/shipping-methods"), shop.token(), "{\"name\":\"ส่งด่วน\",\"feeType\":\"FLAT\",\"feeAmount\":50}")
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SHOP_SUSPENDED"));
        call(post(base + "/shipping-methods"), adminToken(), "{\"name\":\"ส่งด่วน\",\"feeType\":\"FLAT\",\"feeAmount\":50}")
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SHOP_SUSPENDED"));
        // the reinstatement request stays available
        call(post("/api/merchant/reinstatement?shopId=" + shop.shopId()), shop.token(), "{\"message\":\"ขอเปิดร้านอีกครั้ง แก้ไขแล้วครับ\"}")
            .andExpect(status().isOk());

        call(post("/api/admin/shops/" + shop.shopId() + "/reactivate"), adminToken(), RESTORE).andExpect(status().isOk());
        call(get("/api/shops/" + slug), null).andExpect(status().isOk());
        call(post(base + "/shipping-methods"), shop.token(), "{\"name\":\"ส่งด่วน\",\"feeType\":\"FLAT\",\"feeAmount\":50}")
            .andExpect(status().is2xxSuccessful());
        call(post("/api/checkout").header("Idempotency-Key", "sus-" + UUID.randomUUID()), buyer, ADDRESS).andExpect(status().isCreated());
    }

    @Test
    void buyerCannotPayAnOpenOrderOfASuspendedShop() throws Exception {
        var shop = openShop("pay-a@example.com", "0816000056", "ร้านจ่าย", "PayShop", "1234567890556", true);
        var sku = sku(publishedVariantProduct(shop, "PY"), "PY-BLK-S");
        call(put(base(shop) + "/payment-accounts/BANK_TRANSFER"), shop.token(),
            "{\"bankName\":\"KBANK\",\"accountNumber\":\"1234567890\",\"accountName\":\"ร้านทดสอบ\"}").andExpect(status().isOk());
        var buyer = token("pay-buyer@example.com", "0817000056");
        add(buyer, sku, 1);
        var out = call(post("/api/checkout").header("Idempotency-Key", "pay-" + UUID.randomUUID()), buyer,
            ADDRESS.substring(0, ADDRESS.lastIndexOf('}')) + ",\"paymentMethod\":\"BANK_TRANSFER\"}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(out, "$.orders[0].id");
        Number total = JsonPath.read(out, "$.orders[0].grandTotal");
        call(post("/api/admin/shops/" + shop.shopId() + "/suspend"), adminToken(), REASON).andExpect(status().isOk());
        slip(buyer, orderId, total.toString(), "REF" + orderId.substring(0, 8)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SHOP_SUSPENDED"));
        call(post("/api/admin/shops/" + shop.shopId() + "/reactivate"), adminToken(), RESTORE).andExpect(status().isOk());
        slip(buyer, orderId, total.toString(), "REF" + orderId.substring(0, 8)).andExpect(status().isCreated());
    }

    private ResultActions slip(String buyer, String orderId, String amount, String reference) throws Exception {
        return mvc.perform(multipart("/api/orders/" + orderId + "/payments")
            .file(new MockMultipartFile("file", "slip.png", "image/png", PNG))
            .param("amount", amount).param("transferredAt", Instant.now().toString()).param("reference", reference)
            .param("bankName", "SCB").param("provider", "BANK_TRANSFER")
            .header("Authorization", "Bearer " + buyer));
    }

    @Test
    void legacyStoreApplicationEndpointsStillWorkAndAreAudited() throws Exception {
        var shop = openShop("leg-a@example.com", "0816000061", "ร้านเดิม", "LegShop", "1234567890561", true);
        var url = "/api/admin/store-applications/" + shop.shopId();
        call(post(url + "/suspend"), adminToken()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        call(post(url + "/suspend"), adminToken()).andExpect(status().isConflict()); // existing contract: repeat = 409
        call(post(url + "/reactivate"), adminToken(), "{\"reason\":\"ตรวจสอบแล้ว\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        call(get("/api/admin/audit-logs?shopId=" + shop.shopId() + "&kind=BUSINESS&dir=asc"), adminToken())
            .andExpect(jsonPath("$.totalItems").value(3)) // approve (fixture) + suspend + reactivate
            .andExpect(jsonPath("$.items[0].action").value("SHOP_APPROVED"))
            .andExpect(jsonPath("$.items[1].action").value("SHOP_SUSPENDED"))
            .andExpect(jsonPath("$.items[1].reasonCode").value("NO_REASON_GIVEN"))
            .andExpect(jsonPath("$.items[2].reason").value("ตรวจสอบแล้ว"));
        call(post("/api/admin/store-applications/bulk/suspend"), adminToken(), "{\"shopIds\":[" + shop.shopId() + "]}")
            .andExpect(jsonPath("$.updated").value(1));
        assertEquals("SUSPENDED", shopStatus(shop.shopId()));
    }

    // ======================================================================
    // merchant suspension (cascade)
    // ======================================================================

    @Test
    void merchantSuspendCascadesToShopsAndReactivateRestoresOnlyThose() throws Exception {
        var a = openShop("cas-a@example.com", "0816000071", "ร้านแคส", "CasShop", "1234567890571", true);
        var b = openShop("cas-b@example.com", "0816000072", "ร้านแคสบี", "CasShopB", "1234567890572", true);
        var merchantA = merchantOf(a.shopId());
        var merchantB = merchantOf(b.shopId());
        // B's shop was suspended on its own before the merchant suspension
        call(post("/api/admin/shops/" + b.shopId() + "/suspend"), adminToken(), REASON).andExpect(status().isOk());

        call(post("/api/admin/merchants/" + merchantA + "/suspend"), adminToken(), "{}").andExpect(status().isBadRequest());
        call(post("/api/admin/merchants/" + merchantA + "/suspend"), adminToken(), REASON).andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(true)).andExpect(jsonPath("$.merchant.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.affectedShopIds[0]").value(a.shopId()))
            .andExpect(jsonPath("$.merchant.suspension.reason").value("ละเมิดนโยบายการขายสินค้า"))
            .andExpect(jsonPath("$.merchant.shops[0].status").value("SUSPENDED"))
            .andExpect(jsonPath("$.merchant.shops[0].suspendReasonCode").value("MERCHANT_SUSPENDED"));
        call(post("/api/admin/merchants/" + merchantA + "/suspend"), adminToken(), REASON).andExpect(jsonPath("$.changed").value(false));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_logs WHERE action = 'MERCHANT_SUSPENDED'"));
        call(get("/api/admin/audit-logs?resourceType=OWNER&resourceId=" + merchantA), adminToken())
            .andExpect(jsonPath("$.items[0].action").value("MERCHANT_SUSPENDED"))
            .andExpect(jsonPath("$.items[0].before.status").value("ACTIVE"))
            .andExpect(jsonPath("$.items[0].after.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.items[0].after.cascadedShopIds[0]").value(a.shopId()));
        // effects: the merchant's shop is frozen and cannot be reactivated / approved on its own
        call(post("/api/merchant/shops/" + a.shopId() + "/shipping-methods"), a.token(), "{\"name\":\"ส่ง\",\"feeType\":\"FLAT\",\"feeAmount\":10}")
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SHOP_SUSPENDED"));
        call(post("/api/admin/shops/" + a.shopId() + "/reactivate"), adminToken(), RESTORE).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MERCHANT_SUSPENDED"));
        call(post("/api/admin/store-applications/" + a.shopId() + "/reactivate"), adminToken()).andExpect(status().isConflict());
        call(get("/api/admin/merchants?status=SUSPENDED"), adminToken()).andExpect(jsonPath("$.totalItems").value(1));
        call(get("/api/admin/shops?suspendReasonCode=MERCHANT_SUSPENDED"), adminToken()).andExpect(jsonPath("$.totalItems").value(1));

        // merchant B: B's shop was already suspended by itself -> not cascaded, and stays suspended after reactivation
        call(post("/api/admin/merchants/" + merchantB + "/suspend"), adminToken(), REASON).andExpect(jsonPath("$.affectedShopIds.length()").value(0));
        call(post("/api/admin/merchants/" + merchantB + "/reactivate"), adminToken(), RESTORE).andExpect(jsonPath("$.changed").value(true))
            .andExpect(jsonPath("$.affectedShopIds.length()").value(0));
        assertEquals("SUSPENDED", shopStatus(b.shopId()));

        call(post("/api/admin/merchants/" + merchantA + "/reactivate"), adminToken(), RESTORE).andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(true)).andExpect(jsonPath("$.merchant.status").value("ACTIVE"))
            .andExpect(jsonPath("$.affectedShopIds[0]").value(a.shopId()));
        call(post("/api/admin/merchants/" + merchantA + "/reactivate"), adminToken(), RESTORE).andExpect(jsonPath("$.changed").value(false));
        assertEquals("ACTIVE", shopStatus(a.shopId()));
        call(post("/api/merchant/shops/" + a.shopId() + "/shipping-methods"), a.token(), "{\"name\":\"ส่ง\",\"feeType\":\"FLAT\",\"feeAmount\":10}")
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void pendingShopOfSuspendedMerchantCannotBeApproved() throws Exception {
        var pending = openShop("pen-a@example.com", "0816000081", "ร้านรออนุมัติ", "PenShop", "1234567890581", false);
        var merchant = merchantOf(pending.shopId());
        call(post("/api/admin/merchants/" + merchant + "/suspend"), adminToken(), REASON).andExpect(jsonPath("$.affectedShopIds.length()").value(0));
        call(post("/api/admin/store-applications/" + pending.shopId() + "/approve"), adminToken()).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MERCHANT_SUSPENDED"));
        call(post("/api/admin/store-applications/bulk/approve"), adminToken(), "{\"shopIds\":[" + pending.shopId() + "]}")
            .andExpect(jsonPath("$.updated").value(0)).andExpect(jsonPath("$.skipped").value(1));
        call(post("/api/admin/merchants/" + merchant + "/reactivate"), adminToken(), RESTORE).andExpect(status().isOk());
        call(post("/api/admin/store-applications/" + pending.shopId() + "/approve"), adminToken()).andExpect(status().isOk());
    }

    @Test
    void auditLogQueryValidatesFiltersAndMasksSensitiveKeys() throws Exception {
        call(get("/api/admin/audit-logs?kind=WRONG"), adminToken()).andExpect(status().isBadRequest());
        call(get("/api/admin/audit-logs/987654321"), adminToken()).andExpect(status().isNotFound());
        var masked = AdminAuditLogService.redact(Map.of("status", "ACTIVE", "phone", "0812345678", "nested", Map.of("email", "a@b.c", "x", 1)));
        assertEquals("ACTIVE", masked.get("status"));
        assertEquals("***", masked.get("phone"));
        assertEquals("***", ((Map<?, ?>) masked.get("nested")).get("email"));
        assertEquals(1, ((Map<?, ?>) masked.get("nested")).get("x"));
        var shop = openShop("aud-a@example.com", "0816000091", "ร้านออดิท", "AudShop", "1234567890591", true);
        var page = call(get("/api/admin/audit-logs?kind=BUSINESS&action=SHOP_APPROVED"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertEquals(1, (int) JsonPath.read(page, "$.totalItems"));
        Number id = JsonPath.read(page, "$.items[0].id");
        call(get("/api/admin/audit-logs/" + id), adminToken()).andExpect(jsonPath("$.target.shopId").value(shop.shopId()))
            .andExpect(jsonPath("$.before.status").value("PENDING_REVIEW")).andExpect(jsonPath("$.after.status").value("APPROVED"));
    }

    // ---------------- helpers ----------------

    private String shopStatus(long shopNo) {
        return jdbc.queryForObject("SELECT status FROM shops WHERE shop_no = ?", String.class, shopNo);
    }

    private String merchantOf(long shopNo) {
        return jdbc.queryForObject("SELECT owner_id FROM shops WHERE shop_no = ?", UUID.class, shopNo).toString();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return call(request.contentType(MediaType.APPLICATION_JSON).content(json), token);
    }

    private void add(String buyer, String sku, int qty) throws Exception {
        call(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":%d}".formatted(sku, qty)).andExpect(status().isOk());
    }

    static final String COLOR_SIZE = """
        {"nameTh":"เสื้อยืด","nameEn":"T-Shirt","productType":"VARIABLE","categoryId":%d,"brand":"Basic",
         "optionGroups":[
           {"name":"Color","values":[{"value":"Black"},{"value":"White"}]},
           {"name":"Size","values":[{"value":"S"},{"value":"M"}]}],
         "skus":[
           {"skuCode":"%s-BLK-S","optionValues":{"Color":"Black","Size":"S"},"price":200.00,"costPrice":80.00,"initialStock":5},
           {"skuCode":"%s-BLK-M","optionValues":{"Color":"Black","Size":"M"},"price":250.00,"costPrice":82.50,"initialStock":3},
           {"skuCode":"%s-WHT-S","optionValues":{"Color":"White","Size":"S"},"price":300.00,"costPrice":90,"initialStock":0}]}
        """;

    private String publishedVariantProduct(OpenShop shop, String prefix) throws Exception {
        var created = call(post(base(shop) + "/products"), shop.token(), COLOR_SIZE.formatted(activeCategory(0), prefix, prefix, prefix))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String productId = JsonPath.read(created, "$.id");
        var file = new MockMultipartFile("file", "photo.png", "image/png", PNG);
        mvc.perform(multipart(base(shop) + "/products/" + productId + "/images").file(file)
            .header("Authorization", "Bearer " + shop.token())).andExpect(status().isOk());
        return call(post(base(shop) + "/products/" + productId + "/publish"), shop.token())
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String sku(String productJson, String code) {
        return JsonPath.<List<String>>read(productJson, "$.skus[?(@.skuCode == '" + code + "')].id").get(0);
    }

    private static String base(OpenShop shop) {
        return "/api/merchant/shops/" + shop.shopId();
    }

    private UUID userId(String token) throws Exception {
        var me = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(me, "$.id"));
    }

    private long activeCategory(int index) {
        return categories.findAll().stream().filter(ProductCategory::isActive)
            .sorted(Comparator.comparing(ProductCategory::getId)).toList().get(index).getId();
    }

    private void addMember(long shopNo, UUID userId, String role) {
        var shopUuid = shops.findByShopId(shopNo).orElseThrow().getUuid();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO shop_members (id, shop_id, user_id, role, status, joined_at, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)",
            UUID.randomUUID(), shopUuid, userId, role, now, now, now);
    }

    private OpenShop openShop(String email, String phone, String shopName, String shopNameEn, String citizenId, boolean approve) throws Exception {
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
        if (approve) {
            mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve").header("Authorization", "Bearer " + adminToken())).andExpect(status().isOk());
        }
        return new OpenShop(seller, shopId.longValue());
    }

    private String adminToken() throws Exception {
        if (adminToken == null) {
            var approver = users.save(UserAccount.systemAccount("pla-admin@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            adminToken = login("pla-admin@example.com");
        }
        return adminToken;
    }

    private String login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"identity":"%s","password":"Password1"}
            """.formatted(email))).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String token(String email, String phone) throws Exception {
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"สมชาย","lastName":"ผู้ซื้อ","email":"%s","phone":"%s","password":"Password1"}
            """.formatted(email, phone))).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
    }

    private void uploadDocument(String token, String type) throws Exception {
        var file = new MockMultipartFile("file", type.toLowerCase() + ".png", "image/png", PNG);
        mvc.perform(multipart("/api/merchant-applications/draft/documents/" + type).file(file).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private record OpenShop(String token, long shopId) {}
}
