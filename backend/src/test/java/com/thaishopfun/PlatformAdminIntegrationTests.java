package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.admin.AdminAuditLogService;
import com.thaishopfun.admin.AdminContactMask;
import com.thaishopfun.admin.AdminShopUsageService;
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

import static org.hamcrest.Matchers.nullValue;
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
        {"acceptTerms":true,"shippingAddress":{"recipientName":"สมหญิง ผู้รับ","phone":"0812345678","addressLine1":"1 ถนนสุขุมวิท",
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
    @Autowired AdminShopUsageService shopUsage;

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

    /** Test-only: set shops.plan_code via JDBC (no in-memory plan map). */
    private void setShopPlan(long shopNo, String planCode) {
        jdbc.update("UPDATE shops SET plan_code = ? WHERE shop_no = ?", planCode, shopNo);
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
                "/api/admin/plans", "/api/admin/plans/usage", "/api/admin/plans/usage-summary",
                "/api/admin/audit-logs", "/api/admin/audit-logs/{id}")) {
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
            .andExpect(jsonPath("$.usage.planCode").value("FREE"));
        call(get("/api/admin/shops/999999"), adminToken()).andExpect(status().isNotFound());

        call(get("/api/admin/plans"), adminToken()).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("DB"))
            .andExpect(jsonPath("$.plans.length()").value(3))
            .andExpect(jsonPath("$.plans[0].code").exists())
            .andExpect(jsonPath("$.plans[0].priceMonthly").exists())
            .andExpect(jsonPath("$.plans[0].sortOrder").exists());
        call(get("/api/admin/plans/usage?sort=shopId&dir=asc"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(3)).andExpect(jsonPath("$.items[0].shopId").value(a.shopId()))
            .andExpect(jsonPath("$.items[0].products.total").value(1)).andExpect(jsonPath("$.items[1].products.total").value(0))
            .andExpect(jsonPath("$.items[0].planCode").value("FREE"));
        call(get("/api/admin/shops/" + a.shopId() + "/usage"), adminToken()).andExpect(jsonPath("$.orders.total").value(0))
            .andExpect(jsonPath("$.planCode").value("FREE"));
    }

    @Test
    void usageEndpointsForbiddenForMerchantAndSupportWithoutShopRead() throws Exception {
        var shop = openShop("usage-403@example.com", "0816000091", "ร้านยูส", "Usage403", "1234567890591", true);
        call(get("/api/admin/plans/usage"), shop.token()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        call(get("/api/admin/shops/" + shop.shopId() + "/usage"), shop.token()).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        call(get("/api/admin/plans/usage-summary"), shop.token()).andExpect(status().isForbidden());

        var created = call(post("/api/admin/roles"), adminToken(),
            "{\"context\":\"PLATFORM\",\"nameTh\":\"ซัพพอร์ตไม่มีอ่านร้าน\",\"permissions\":[\"PLATFORM_ACCOUNT_READ\"]}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(created, "$.role.code");
        var supportToken = token("usage-support@example.com", "0817000091");
        call(put("/api/admin/users/" + userId(supportToken) + "/role"), adminToken(), "{\"roleCode\":\"" + code + "\"}")
            .andExpect(status().isOk());
        supportToken = login("usage-support@example.com");
        call(get("/api/admin/shops/" + shop.shopId() + "/usage"), supportToken).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        call(get("/api/admin/plans/usage"), supportToken).andExpect(status().isForbidden());
        call(get("/api/admin/plans/usage-summary"), supportToken).andExpect(status().isForbidden());
    }

    @Test
    void planUsageFilterReturnsOnlyMatchingShopsAndSummaryCountsAll() throws Exception {
        var a = openShop("plan-a@example.com", "0816000092", "ร้านแพลนเอ", "PlanA", "1234567890592", true);
        var b = openShop("plan-b@example.com", "0816000093", "ร้านแพลนบี", "PlanB", "1234567890593", true);
        var c = openShop("plan-c@example.com", "0816000094", "ร้านแพลนซี", "PlanC", "1234567890594", true);
        setShopPlan(a.shopId(), "BASIC");
        setShopPlan(b.shopId(), "BASIC");
        setShopPlan(c.shopId(), "PRO");

        call(get("/api/admin/plans/usage?plan=BASIC&sort=shopId&dir=asc"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(2))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].shopId").value(a.shopId()))
            .andExpect(jsonPath("$.items[0].planCode").value("BASIC"))
            .andExpect(jsonPath("$.items[1].shopId").value(b.shopId()));
        call(get("/api/admin/plans/usage?plan=PRO"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].shopId").value(c.shopId()))
            .andExpect(jsonPath("$.items[0].planCode").value("PRO"));
        call(get("/api/admin/plans/usage?plan=__NONE__"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));
        call(get("/api/admin/plans/usage?plan=BASIC&q=planb"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].shopId").value(b.shopId()));

        var summary = call(get("/api/admin/plans/usage-summary"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = JsonPath.read(summary, "$");
        long basic = rows.stream().filter(r -> "BASIC".equals(r.get("planCode"))).mapToLong(r -> ((Number) r.get("shopCount")).longValue()).sum();
        long pro = rows.stream().filter(r -> "PRO".equals(r.get("planCode"))).mapToLong(r -> ((Number) r.get("shopCount")).longValue()).sum();
        long none = rows.stream().filter(r -> r.get("planCode") == null).mapToLong(r -> ((Number) r.get("shopCount")).longValue()).sum();
        assertEquals(2, basic);
        assertEquals(1, pro);
        assertEquals(0, none);
    }

    @Test
    void planParamNormalizesCaseRejectsInvalidAndBlankMeansNoFilter() throws Exception {
        var a = openShop("plan-case-a@example.com", "0816000095", "ร้านเคสเอ", "PlanCaseA", "1234567890595", true);
        var b = openShop("plan-case-b@example.com", "0816000096", "ร้านเคสบี", "PlanCaseB", "1234567890596", true);
        setShopPlan(a.shopId(), "BASIC");
        setShopPlan(b.shopId(), "PRO");

        call(get("/api/admin/plans/usage?plan=basic&sort=shopId&dir=asc"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].shopId").value(a.shopId()))
            .andExpect(jsonPath("$.items[0].planCode").value("BASIC"));
        call(get("/api/admin/plans/usage?plan=BASIC"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].shopId").value(a.shopId()));

        call(get("/api/admin/plans/usage?plan=bad-value!"), adminToken()).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PLAN_INVALID"));
        call(get("/api/admin/plans/usage-summary?plan=bad-value!"), adminToken()).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PLAN_INVALID"));

        call(get("/api/admin/plans/usage?plan="), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(2));
        call(get("/api/admin/plans/usage"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(2));
        call(get("/api/admin/plans/usage?plan=UNKNOWN_PLAN"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void planNoneFilterPaginationSummaryAndBatchedUsage() throws Exception {
        var a = openShop("plan-none-a@example.com", "0816000097", "ร้านนันเอ", "PlanNoneA", "1234567890597", true);
        var b = openShop("plan-none-b@example.com", "0816000098", "ร้านนันบี", "PlanNoneB", "1234567890598", true);
        var c = openShop("plan-none-c@example.com", "0816000099", "ร้านนันซี", "PlanNoneC", "1234567890599", true);
        var d = openShop("plan-none-d@example.com", "0816000100", "ร้านนันดี", "PlanNoneD", "1234567890600", true);
        setShopPlan(a.shopId(), "BASIC");
        setShopPlan(b.shopId(), "BASIC");
        setShopPlan(c.shopId(), "BASIC");
        // New shops get FREE by default — clear d so __NONE__ still covers unassigned shops.
        jdbc.update("UPDATE shops SET plan_code = NULL WHERE shop_no = ?", d.shopId());

        call(get("/api/admin/plans/usage?plan=__NONE__"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].shopId").value(d.shopId()))
            .andExpect(jsonPath("$.items[0].planCode").value(nullValue()));

        call(get("/api/admin/plans/usage?plan=BASIC&sort=shopId&dir=asc&page=0&size=2"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].shopId").value(a.shopId()))
            .andExpect(jsonPath("$.items[1].shopId").value(b.shopId()));
        call(get("/api/admin/plans/usage?plan=BASIC&sort=shopId&dir=asc&page=1&size=2"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(3))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].shopId").value(c.shopId()));
        assertEquals(1, shopUsage.lastUsageBatchSize(), "page of 1 shop → one batch of size 1");

        call(get("/api/admin/plans/usage?plan=BASIC&sort=shopId&dir=asc&page=0&size=3"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(3));
        assertEquals(3, shopUsage.lastUsageBatchSize(), "usage must load the page as one batch, not N+1");

        var summary = call(get("/api/admin/plans/usage-summary"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = JsonPath.read(summary, "$");
        long basic = rows.stream().filter(r -> "BASIC".equals(r.get("planCode"))).mapToLong(r -> ((Number) r.get("shopCount")).longValue()).sum();
        long none = rows.stream().filter(r -> r.get("planCode") == null).mapToLong(r -> ((Number) r.get("shopCount")).longValue()).sum();
        assertEquals(3, basic);
        assertEquals(1, none);

        call(put("/api/admin/shops/" + d.shopId() + "/plan"), adminToken(), "{\"planCode\":\"FREE\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(true))
            .andExpect(jsonPath("$.shop.usage.planCode").value("FREE"));
        call(get("/api/admin/plans/usage?plan=__NONE__"), adminToken()).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void adminShopOwnerContactIsMaskedInShopAndStoreApplicationApis() throws Exception {
        var phone = "0816000088";
        var email = "mask-owner@example.com";
        var shop = openShop(email, phone, "ร้านปิดบัง", "MaskShop", "1234567890588", true);
        var expectedPhone = AdminContactMask.phone(phone);
        var expectedEmail = AdminContactMask.email(email);
        assertEquals("08x-xxx-0088", expectedPhone);
        assertEquals("m***@example.com", expectedEmail);

        call(get("/api/admin/shops/" + shop.shopId()), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.owner.phone").value(expectedPhone))
            .andExpect(jsonPath("$.owner.email").value(expectedEmail));
        call(get("/api/admin/shops?q=MaskShop"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].owner.phone").value(expectedPhone))
            .andExpect(jsonPath("$.items[0].owner.email").value(expectedEmail));

        var apps = call(get("/api/admin/store-applications"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertFalse(apps.contains(phone), "store-applications must not include full owner phone");
        assertFalse(apps.contains(email), "store-applications must not include full owner email");
        assertTrue(apps.contains(expectedPhone));
        assertTrue(apps.contains(expectedEmail));
    }

    @Test
    void shopsContactSearchAllowsLast4AndFullExactOnlyAndAuditsFullLookup() throws Exception {
        var phone = "0816000077";
        var email = "search-owner@phonesearch.test";
        var shop = openShop(email, phone, "ร้านค้นหาเบอร์", "PhoneSearchShop", "1234567890577", true);
        var maskedPhone = AdminContactMask.phone(phone);
        var maskedEmail = AdminContactMask.email(email);
        var other = openShop("other-search@phonesearch.test", "0816000099", "ร้านอื่นค้นหา", "OtherSearch", "1234567890578", true);

        // 1–3 digits must not match by phone
        var short2 = call(get("/api/admin/shops?q=77"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(((List<?>) JsonPath.read(short2, "$.items[?(@.shopId == " + shop.shopId() + ")]")).isEmpty());
        var short3 = call(get("/api/admin/shops?q=077"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(((List<?>) JsonPath.read(short3, "$.items[?(@.shopId == " + shop.shopId() + ")]")).isEmpty());

        // last 4 digits → hit, still masked
        call(get("/api/admin/shops?q=0077"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].shopId").value(shop.shopId()))
            .andExpect(jsonPath("$.items[0].owner.phone").value(maskedPhone))
            .andExpect(jsonPath("$.items[0].owner.email").value(maskedEmail));

        // full phone → hit + CONTACT_SEARCHED (no raw value in audit)
        call(get("/api/admin/shops?q=" + phone), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].shopId").value(shop.shopId()));
        var phoneAudit = call(get("/api/admin/audit-logs?action=CONTACT_SEARCHED&kind=BUSINESS"), adminToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andReturn().getResponse().getContentAsString();
        assertTrue(phoneAudit.contains("PHONE"));
        assertFalse(phoneAudit.contains(phone));

        // email local-part prefix must not match; full email and @domain must
        var localPart = call(get("/api/admin/shops?q=search-owner"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(((List<?>) JsonPath.read(localPart, "$.items[?(@.shopId == " + shop.shopId() + ")]")).isEmpty());
        call(get("/api/admin/shops?q=" + email), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].shopId").value(shop.shopId()));
        var emailAudit = call(get("/api/admin/audit-logs?action=CONTACT_SEARCHED&kind=BUSINESS"), adminToken())
            .andReturn().getResponse().getContentAsString();
        assertTrue(emailAudit.contains("EMAIL"));
        assertFalse(emailAudit.contains(email));
        var domainHits = call(get("/api/admin/shops?q=@phonesearch.test"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertFalse(((List<?>) JsonPath.read(domainHits, "$.items[?(@.shopId == " + shop.shopId() + ")]")).isEmpty());
        assertFalse(((List<?>) JsonPath.read(domainHits, "$.items[?(@.shopId == " + other.shopId() + ")]")).isEmpty());

        var body = call(get("/api/admin/shops?q=0077"), adminToken()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(phone));
        assertFalse(body.contains(email));
    }

    @Test
    void merchantsContactSearchAllowsLast4AndFullExactOnlyAndAuditsFullLookup() throws Exception {
        var phone = "0816000171";
        var email = "mer-contact@mercontact.test";
        var shop = openShop(email, phone, "ร้านค้นหาผู้ขาย", "MerContactShop", "1234567890171", true);
        var merchantId = merchantOf(shop.shopId());
        openShop("other-mer@mercontact.test", "0816000199", "ร้านผู้ขายอื่น", "OtherMerContact", "1234567890172", true);

        var short2 = call(get("/api/admin/merchants?q=71"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(((List<?>) JsonPath.read(short2, "$.items[?(@.merchantId == '" + merchantId + "')]")).isEmpty());
        var short3 = call(get("/api/admin/merchants?q=171"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(((List<?>) JsonPath.read(short3, "$.items[?(@.merchantId == '" + merchantId + "')]")).isEmpty());

        call(get("/api/admin/merchants?q=0171"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].merchantId").value(merchantId));

        call(get("/api/admin/merchants?q=" + phone), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].merchantId").value(merchantId));
        var phoneAudit = call(get("/api/admin/audit-logs?action=CONTACT_SEARCHED&kind=BUSINESS"), adminToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andReturn().getResponse().getContentAsString();
        assertTrue(phoneAudit.contains("PHONE"));
        assertFalse(phoneAudit.contains(phone));

        var localPart = call(get("/api/admin/merchants?q=mer-contact"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(((List<?>) JsonPath.read(localPart, "$.items[?(@.merchantId == '" + merchantId + "')]")).isEmpty());
        call(get("/api/admin/merchants?q=" + email), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].merchantId").value(merchantId));
        var emailAudit = call(get("/api/admin/audit-logs?action=CONTACT_SEARCHED&kind=BUSINESS"), adminToken())
            .andReturn().getResponse().getContentAsString();
        assertTrue(emailAudit.contains("EMAIL"));
        assertFalse(emailAudit.contains(email));
        var domainHits = call(get("/api/admin/merchants?q=@mercontact.test"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertFalse(((List<?>) JsonPath.read(domainHits, "$.items[?(@.merchantId == '" + merchantId + "')]")).isEmpty());
    }

    @Test
    void usersContactSearchAllowsLast4AndFullExactOnlyAndAuditsFullLookup() throws Exception {
        var phone = "0816000181";
        var email = "user-contact@usercontact.test";
        var userToken = token(email, phone);
        var id = userId(userToken).toString();
        token("other-user@usercontact.test", "0816000182");

        var short2 = call(get("/api/admin/users?q=81"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(((List<?>) JsonPath.read(short2, "$.items[?(@.id == '" + id + "')]")).isEmpty());
        var short3 = call(get("/api/admin/users?q=181"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(((List<?>) JsonPath.read(short3, "$.items[?(@.id == '" + id + "')]")).isEmpty());

        call(get("/api/admin/users?q=0181"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].id").value(id));

        call(get("/api/admin/users?q=" + phone), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(id));
        var phoneAudit = call(get("/api/admin/audit-logs?action=CONTACT_SEARCHED&kind=BUSINESS"), adminToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andReturn().getResponse().getContentAsString();
        assertTrue(phoneAudit.contains("PHONE"));
        assertFalse(phoneAudit.contains(phone));

        var localPart = call(get("/api/admin/users?q=user-contact"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(((List<?>) JsonPath.read(localPart, "$.items[?(@.id == '" + id + "')]")).isEmpty());
        call(get("/api/admin/users?q=" + email), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(id));
        var emailAudit = call(get("/api/admin/audit-logs?action=CONTACT_SEARCHED&kind=BUSINESS"), adminToken())
            .andReturn().getResponse().getContentAsString();
        assertTrue(emailAudit.contains("EMAIL"));
        assertFalse(emailAudit.contains(email));
        var domainHits = call(get("/api/admin/users?q=@usercontact.test"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertFalse(((List<?>) JsonPath.read(domainHits, "$.items[?(@.id == '" + id + "')]")).isEmpty());
    }

    @Test
    void usersExportWithFullPhoneAuditsContactSearchedWithoutStoringQuery() throws Exception {
        var phone = "0816000183";
        token("export-contact@usercontact.test", phone);
        call(get("/api/admin/users/export?q=" + phone), adminToken()).andExpect(status().isOk());
        var audit = call(get("/api/admin/audit-logs?action=CONTACT_SEARCHED&kind=BUSINESS"), adminToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andReturn().getResponse().getContentAsString();
        assertTrue(audit.contains("PHONE"));
        assertFalse(audit.contains(phone));
    }

    @Test
    void revealStoreApplicationContactAuditsWithoutStoringRawValues() throws Exception {
        var phone = "0816000076";
        var email = "reveal-owner@example.com";
        var shop = openShop(email, phone, "ร้านเปิดดูติดต่อ", "RevealShop", "1234567890576", false);

        call(post("/api/admin/store-applications/" + shop.shopId() + "/contact"), adminToken(), "{}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value(phone))
            .andExpect(jsonPath("$.email").value(email));

        call(get("/api/admin/audit-logs?action=CONTACT_REVEALED&kind=BUSINESS&shopId=" + shop.shopId()), adminToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.items[0].action").value("CONTACT_REVEALED"))
            .andExpect(jsonPath("$.items[0].permission").value("PLATFORM_CONTACT_REVEAL"));
        var audit = call(get("/api/admin/audit-logs?action=CONTACT_REVEALED&kind=BUSINESS&shopId=" + shop.shopId()), adminToken())
            .andReturn().getResponse().getContentAsString();
        assertFalse(audit.contains(phone), "audit must not contain full phone");
        assertFalse(audit.contains(email), "audit must not contain full email");

        call(post("/api/admin/store-applications/" + shop.shopId() + "/contact"), shop.token(), "{}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        var created = call(post("/api/admin/roles"), adminToken(),
            "{\"context\":\"PLATFORM\",\"nameTh\":\"ซัพพอร์ตไม่มีเปิดดูติดต่อ\",\"permissions\":[\"PLATFORM_APPLICATION_READ\",\"PLATFORM_SHOP_READ\"]}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(created, "$.role.code");
        var supportToken = token("reveal-support@example.com", "0817000076");
        call(put("/api/admin/users/" + userId(supportToken) + "/role"), adminToken(), "{\"roleCode\":\"" + code + "\"}")
            .andExpect(status().isOk());
        supportToken = login("reveal-support@example.com");
        call(post("/api/admin/store-applications/" + shop.shopId() + "/contact"), supportToken, "{}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void assignShopPlanForbiddenForMerchantAndSupportWithoutSuspendPermission() throws Exception {
        var shop = openShop("plan-403@example.com", "0816000089", "ร้านแพลนห้าม", "Plan403", "1234567890589", true);
        call(put("/api/admin/shops/" + shop.shopId() + "/plan"), shop.token(), "{\"planCode\":\"FREE\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        var created = call(post("/api/admin/roles"), adminToken(),
            "{\"context\":\"PLATFORM\",\"nameTh\":\"ซัพพอร์ตไม่มีระงับ\",\"permissions\":[\"PLATFORM_SHOP_READ\",\"PLATFORM_ACCOUNT_READ\"]}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(created, "$.role.code");
        var supportToken = token("plan-support-403@example.com", "0817000089");
        call(put("/api/admin/users/" + userId(supportToken) + "/role"), adminToken(), "{\"roleCode\":\"" + code + "\"}")
            .andExpect(status().isOk());
        supportToken = login("plan-support-403@example.com");
        call(get("/api/admin/shops/" + shop.shopId()), supportToken).andExpect(status().isOk());
        call(put("/api/admin/shops/" + shop.shopId() + "/plan"), supportToken, "{\"planCode\":\"BASIC\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
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
        call(get("/api/admin/merchants?q=mer-a@example.com"), adminToken()).andExpect(jsonPath("$.totalItems").value(1));
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

    @Test
    void userHistoryShowsLegacyAdminStatusAndNewAdminSuspendRows() throws Exception {
        var shop = openShop("hist-rc@example.com", "0816000141", "ร้านประวัติรหัส", "HistRc", "1234567890641", true);
        var userId = userId(shop.token());
        adminToken();
        var adminId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, "pla-admin@example.com");
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
            INSERT INTO audit_logs (occurred_at, actor_id, actor_type, action, resource_type, resource_id,
              outcome, reason_code, reason, auth_context, policy_version, created_date_time, created_by, modified_date_time)
            VALUES (?, ?, 'USER', 'ACCOUNT_SUSPENDED', 'USER', ?, 'SUCCESS', 'ADMIN_STATUS', 'เหตุผลเก่า', 'PLATFORM', '1.0', ?, ?, ?)
            """, now, adminId, userId, now, adminId, now);
        jdbc.update("""
            INSERT INTO audit_logs (occurred_at, actor_id, actor_type, action, resource_type, resource_id,
              outcome, reason_code, reason, auth_context, policy_version, created_date_time, created_by, modified_date_time)
            VALUES (?, ?, 'USER', 'ACCOUNT_SUSPENDED', 'USER', ?, 'SUCCESS', 'ADMIN_SUSPEND', 'เหตุผลใหม่', 'PLATFORM', '1.0', ?, ?, ?)
            """, Timestamp.from(Instant.now().plusSeconds(1)), adminId, userId,
            Timestamp.from(Instant.now().plusSeconds(1)), adminId, Timestamp.from(Instant.now().plusSeconds(1)));

        var history = call(get("/api/admin/users/" + userId + "/history?limit=50"), adminToken())
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(history.contains("เปลี่ยนสถานะโดยแอดมิน"), history);
        assertTrue(history.contains("ระงับโดยผู้ดูแล"), history);
        assertTrue(history.contains("เหตุผลเก่า"), history);
        assertTrue(history.contains("เหตุผลใหม่"), history);

        call(get("/api/admin/audit-logs?reasonCode=ADMIN_STATUS&resourceId=" + userId + "&kind=BUSINESS"), adminToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(2));
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
        call(post(url + "/suspend"), adminToken()).andExpect(status().isBadRequest()); // reasonCode required
        call(post(url + "/suspend"), adminToken(), "{\"reasonCode\":\"POLICY_VIOLATION\",\"reason\":\"ตรวจพบการละเมิด\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        call(post(url + "/suspend"), adminToken(), "{\"reasonCode\":\"POLICY_VIOLATION\"}").andExpect(status().isConflict()); // existing contract: repeat = 409
        call(post(url + "/reactivate"), adminToken(), "{\"reason\":\"ตรวจสอบแล้ว\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        call(get("/api/admin/audit-logs?shopId=" + shop.shopId() + "&kind=BUSINESS&dir=asc"), adminToken())
            .andExpect(jsonPath("$.totalItems").value(3)) // approve (fixture) + suspend + reactivate
            .andExpect(jsonPath("$.items[0].action").value("SHOP_APPROVED"))
            .andExpect(jsonPath("$.items[1].action").value("SHOP_SUSPENDED"))
            .andExpect(jsonPath("$.items[1].reasonCode").value("POLICY_VIOLATION"))
            .andExpect(jsonPath("$.items[1].reason").value("ตรวจพบการละเมิด"))
            .andExpect(jsonPath("$.items[2].reason").value("ตรวจสอบแล้ว"));
        call(post("/api/admin/store-applications/bulk/suspend"), adminToken(),
            "{\"shopIds\":[" + shop.shopId() + "],\"reasonCode\":\"ADMIN_SUSPEND\",\"reason\":\"bulk note\"}")
            .andExpect(jsonPath("$.updated").value(1));
        assertEquals("SUSPENDED", shopStatus(shop.shopId()));
    }

    @Test
    void bulkSuspendRequiresReasonAndSharesCorrelationId() throws Exception {
        var a = openShop("blk-a@example.com", "0816000121", "ร้านบัลค์เอ", "BlkA", "1234567890621", true);
        var b = openShop("blk-b@example.com", "0816000122", "ร้านบัลค์บี", "BlkB", "1234567890622", true);
        var c = openShop("blk-c@example.com", "0816000123", "ร้านบัลค์ซี", "BlkC", "1234567890623", true);
        call(post("/api/admin/store-applications/bulk/suspend"), adminToken(),
            "{\"shopIds\":[" + a.shopId() + "," + b.shopId() + "," + c.shopId() + "]}")
            .andExpect(status().isBadRequest());
        assertEquals(0, count("SELECT COUNT(*) FROM audit_logs WHERE action = 'SHOP_SUSPENDED'"));
        assertEquals("ACTIVE", shopStatus(a.shopId()));

        call(post("/api/admin/store-applications/bulk/suspend"), adminToken(),
            "{\"shopIds\":[" + a.shopId() + "," + b.shopId() + "," + c.shopId() + "],\"reasonCode\":\"POLICY_VIOLATION\",\"reason\":\"ระงับชุดทดสอบ\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(3));
        assertEquals(3, count("SELECT COUNT(*) FROM audit_logs WHERE action = 'SHOP_SUSPENDED' AND reason_code = 'POLICY_VIOLATION'"));
        var page = call(get("/api/admin/audit-logs?action=SHOP_SUSPENDED&kind=BUSINESS&size=10"), adminToken())
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(3, (int) JsonPath.read(page, "$.totalItems"));
        String correlation = JsonPath.read(page, "$.items[0].after.correlationId");
        assertNotNull(correlation);
        call(get("/api/admin/audit-logs?action=SHOP_SUSPENDED&kind=BUSINESS&size=10"), adminToken())
            .andExpect(jsonPath("$.items[0].after.correlationId").value(correlation))
            .andExpect(jsonPath("$.items[1].after.correlationId").value(correlation))
            .andExpect(jsonPath("$.items[2].after.correlationId").value(correlation))
            .andExpect(jsonPath("$.items[0].reason").value("ระงับชุดทดสอบ"));

        call(post("/api/admin/store-applications/bulk/reactivate"), adminToken(),
            "{\"shopIds\":[" + a.shopId() + "," + b.shopId() + "," + c.shopId() + "]}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(3));
        assertEquals("ACTIVE", shopStatus(a.shopId()));
        assertEquals("ACTIVE", shopStatus(b.shopId()));
        assertEquals("ACTIVE", shopStatus(c.shopId()));
    }

    @Test
    void reservedReasonCodesRejectedWithoutAuditOrStatusChange() throws Exception {
        var shop = openShop("rc-a@example.com", "0816000131", "ร้านรหัสเหตุผล", "RcShop", "1234567890631", true);
        var url = "/api/admin/shops/" + shop.shopId();
        var beforeLogs = count("SELECT COUNT(*) FROM audit_logs WHERE action = 'SHOP_SUSPENDED'");

        // (ก) reserved cascade code on shop suspend → 400, no log, status unchanged
        call(post(url + "/suspend"), adminToken(),
            "{\"reason\":\"ทดสอบรหัสสงวน cascade\",\"reasonCode\":\"MERCHANT_SUSPENDED\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REASON_CODE_INVALID"));
        assertEquals("ACTIVE", shopStatus(shop.shopId()));
        assertEquals(beforeLogs, count("SELECT COUNT(*) FROM audit_logs WHERE action = 'SHOP_SUSPENDED'"));

        // restore default must not be accepted on suspend
        call(post(url + "/suspend"), adminToken(),
            "{\"reason\":\"ทดสอบรหัสสงวน restore\",\"reasonCode\":\"ADMIN_RESTORE\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REASON_CODE_INVALID"));
        assertEquals("ACTIVE", shopStatus(shop.shopId()));

        // (ข) LEGACY_NO_REASON on bulk suspend → 400
        call(post("/api/admin/store-applications/bulk/suspend"), adminToken(),
            "{\"shopIds\":[" + shop.shopId() + "],\"reasonCode\":\"NO_REASON_GIVEN\",\"reason\":\"legacy\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REASON_CODE_INVALID"));
        assertEquals("ACTIVE", shopStatus(shop.shopId()));
        assertEquals(beforeLogs, count("SELECT COUNT(*) FROM audit_logs WHERE action = 'SHOP_SUSPENDED'"));

        // (ค) every allowlisted suspend code succeeds
        var allowed = List.of(
            new String[]{"ADMIN_SUSPEND", "rc-admin@example.com", "0816000132", "1234567890632", "RcAdmin"},
            new String[]{"POLICY_VIOLATION", "rc-policy@example.com", "0816000133", "1234567890633", "RcPolicy"},
            new String[]{"FRAUD", "rc-fraud@example.com", "0816000134", "1234567890634", "RcFraud"},
            new String[]{"ABUSE", "rc-abuse@example.com", "0816000135", "1234567890635", "RcAbuse"},
            new String[]{"OTHER", "rc-other@example.com", "0816000136", "1234567890636", "RcOther"});
        for (var row : allowed) {
            var code = row[0];
            var s = openShop(row[1], row[2], "ร้าน" + row[4], row[4], row[3], true);
            call(post("/api/admin/shops/" + s.shopId() + "/suspend"), adminToken(),
                "{\"reason\":\"รหัสอนุญาต " + code + " ผ่าน\",\"reasonCode\":\"" + code + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shop.suspension.reasonCode").value(code));
            assertEquals("SUSPENDED", shopStatus(s.shopId()));
        }
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
        var merchantAudit = call(get("/api/admin/audit-logs?resourceType=OWNER&resourceId=" + merchantA), adminToken())
            .andExpect(jsonPath("$.items[0].action").value("MERCHANT_SUSPENDED"))
            .andExpect(jsonPath("$.items[0].before.status").value("ACTIVE"))
            .andExpect(jsonPath("$.items[0].after.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.items[0].after.cascadedShopIds[0]").value(a.shopId()))
            .andExpect(jsonPath("$.items[0].after.correlationId").exists())
            .andExpect(jsonPath("$.items[0].actor.role").exists())
            .andExpect(jsonPath("$.items[0].target.name").exists())
            .andReturn().getResponse().getContentAsString();
        String correlationId = JsonPath.read(merchantAudit, "$.items[0].after.correlationId");
        call(get("/api/admin/audit-logs?shopId=" + a.shopId() + "&action=SHOP_SUSPENDED&kind=BUSINESS"), adminToken())
            .andExpect(jsonPath("$.items[0].after.correlationId").value(correlationId))
            .andExpect(jsonPath("$.items[0].after.parentResourceType").value("OWNER"))
            .andExpect(jsonPath("$.items[0].after.parentResourceId").value(merchantA))
            .andExpect(jsonPath("$.items[0].after.cascadeFromMerchantId").value(merchantA));
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

    @Test
    void statusActionsWriteOneAuditRowEachWithoutPiiAndRejectPathsWriteNone() throws Exception {
        var shop = openShop("six-a@example.com", "0816000101", "ร้านหกแอกชัน", "SixShop", "1234567890601", true);
        var userId = userId(shop.token());
        var merchantId = merchantOf(shop.shopId());
        var statusActions = "SELECT COUNT(*) FROM audit_logs WHERE action IN ('SHOP_SUSPENDED','SHOP_REACTIVATED','MERCHANT_SUSPENDED','MERCHANT_REACTIVATED','ACCOUNT_SUSPENDED','ACCOUNT_RESTORED')";
        var beforeStatusActions = count(statusActions);

        // 400 / 404 / 403 must not leave a successful suspend/reactivate business row
        call(post("/api/admin/shops/" + shop.shopId() + "/suspend"), adminToken(), "{}").andExpect(status().isBadRequest());
        call(post("/api/admin/shops/999999001/suspend"), adminToken(), REASON).andExpect(status().isNotFound());
        call(post("/api/admin/shops/" + shop.shopId() + "/suspend"), shop.token(), REASON).andExpect(status().isForbidden());
        call(post("/api/admin/users/" + UUID.randomUUID() + "/suspend"), adminToken(), "{\"reason\":\"หาย\"}").andExpect(status().isNotFound());
        assertEquals(beforeStatusActions, count(statusActions));

        call(post("/api/admin/shops/" + shop.shopId() + "/suspend"), adminToken(), REASON).andExpect(status().isOk());
        call(post("/api/admin/shops/" + shop.shopId() + "/reactivate"), adminToken(), RESTORE).andExpect(status().isOk());
        call(post("/api/admin/merchants/" + merchantId + "/suspend"), adminToken(), REASON).andExpect(status().isOk());
        call(post("/api/admin/merchants/" + merchantId + "/reactivate"), adminToken(), RESTORE).andExpect(status().isOk());
        call(post("/api/admin/users/" + userId + "/suspend"), adminToken(), "{\"reason\":\"ทดสอบระงับผู้ใช้\"}").andExpect(status().isOk());
        call(post("/api/admin/users/" + userId + "/restore"), adminToken(), "{\"reason\":\"คืนสิทธิ์ทดสอบ\"}").andExpect(status().isOk());

        // direct shop + cascade-from-merchant each write one SHOP_* row
        assertEquals(2, count("SELECT COUNT(*) FROM audit_logs WHERE action = 'SHOP_SUSPENDED'"));
        assertEquals(2, count("SELECT COUNT(*) FROM audit_logs WHERE action = 'SHOP_REACTIVATED'"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_logs WHERE action = 'MERCHANT_SUSPENDED'"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_logs WHERE action = 'MERCHANT_REACTIVATED'"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_logs WHERE action = 'ACCOUNT_SUSPENDED' AND resource_id = ?", userId));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_logs WHERE action = 'ACCOUNT_RESTORED' AND resource_id = ?", userId));

        var suspended = call(get("/api/admin/audit-logs?action=SHOP_SUSPENDED&kind=BUSINESS"), adminToken()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertFalse(suspended.toLowerCase(Locale.ROOT).contains("0816000101"), suspended);
        assertFalse(suspended.toLowerCase(Locale.ROOT).contains("six-a@example.com"), suspended);
        call(get("/api/admin/audit-logs?action=ACCOUNT_SUSPENDED&resourceId=" + userId), adminToken())
            .andExpect(jsonPath("$.items[0].before.status").value("ACTIVE"))
            .andExpect(jsonPath("$.items[0].after.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.items[0].after.targetName").exists())
            .andExpect(jsonPath("$.items[0].actor.role").exists());
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
        jdbc.update("INSERT INTO shop_members (id, shop_id, user_id, role, status, permission_version, joined_at, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, 'ACTIVE', 0, ?, ?, ?)",
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
