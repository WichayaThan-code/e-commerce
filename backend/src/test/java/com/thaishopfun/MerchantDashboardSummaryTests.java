package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PermissionMatrix;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.customer.CustomerAuditLogRepository;
import com.thaishopfun.customer.CustomerInsightKeys;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantDashboardSummaryTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PermissionMatrix permissionMatrix;
    @Autowired CustomerAuditLogRepository customerAudits;
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
    @Autowired CustomerInsightKeys customerKeys;

    private String adminToken;

    @BeforeEach
    void clean() {
        adminToken = null;
        jdbc.update("DELETE FROM merchant_notification_reads");
        jdbc.update("DELETE FROM merchant_notification_cursor");
        jdbc.update("DELETE FROM order_status_history");
        jdbc.update("DELETE FROM refund_status_history");
        jdbc.update("DELETE FROM refunds");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM order_addresses");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM inventory_ledger");
        jdbc.update("DELETE FROM inventory_levels");
        jdbc.update("DELETE FROM product_skus");
        jdbc.update("DELETE FROM product_images");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM shop_customer_tags");
        jdbc.update("DELETE FROM shop_customer_addresses");
        jdbc.update("UPDATE shop_customers SET merged_into_id = NULL");
        jdbc.update("DELETE FROM shop_customers");
        customerAudits.deleteAll();
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

    @Test
    void summaryBangkokDayBoundaryStatusesShopIsolationAnd403() throws Exception {
        var shopA = openShop("md-a@example.com", "0814400001", "ร้านแดชเอ", "Dash A", "1234567890701");
        var shopB = openShop("md-b@example.com", "0814400002", "ร้านแดชบี", "Dash B", "1234567890702");
        var a = shops.findByShopId(shopA.shopId).orElseThrow().getUuid();
        var b = shops.findByShopId(shopB.shopId).orElseThrow().getUuid();
        // 2026-10-30 23:59 Bangkok (= 16:59Z) belongs to Oct 30; 2026-10-31 00:01 Bangkok (= 17:01Z on Oct 30) to Oct 31
        order(a, "D30-2359", "100.00", "MERCHANT_CONFIRMED", "PAID", "2026-10-30T16:59:00Z");
        order(a, "D31-0001", "200.00", "PACKED", "PAID", "2026-10-30T17:01:00Z");
        order(a, "D31-CXL", "50.00", "CANCELLED", "PAID", "2026-10-31T05:00:00Z");
        order(a, "D31-RF", "70.00", "REFUNDED", "PAID", "2026-10-31T05:00:00Z");
        order(a, "D31-UNPAID", "30.00", "PENDING_PAYMENT", "UNPAID", "2026-10-31T05:00:00Z");
        order(a, "D31-SHIP", "400.00", "DISPATCHED", "PAID", "2026-10-31T06:00:00Z");
        order(a, "D31-2359", "25.00", "PAID_AWAITING_MERCHANT_CONFIRM", "PAID", "2026-10-31T16:59:00Z");
        order(a, "D01-0001", "9.00", "READY_TO_PACK", "PAID", "2026-10-31T17:01:00Z");
        order(b, "B31", "999.00", "PACKING", "PAID", "2026-10-31T05:00:00Z");

        perform(get(base(shopA) + "/dashboard/summary").param("date", "2026-10-31"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.date").value("2026-10-31"))
            .andExpect(jsonPath("$.timezone").value("Asia/Bangkok"))
            .andExpect(jsonPath("$.todayOrderCount").value(6))
            .andExpect(jsonPath("$.todayPaidOrderCount").value(3))
            .andExpect(jsonPath("$.todaySales").value(625.00))
            // MERCHANT_CONFIRMED + PACKED + PAID_AWAITING + READY_TO_PACK (any day); DISPATCHED/CANCELLED/UNPAID excluded
            .andExpect(jsonPath("$.pendingOrders").value(4));
        perform(get(base(shopA) + "/dashboard/summary").param("date", "2026-10-30"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.todayOrderCount").value(1))
            .andExpect(jsonPath("$.todaySales").value(100.00));
        perform(get(base(shopA) + "/dashboard/summary").param("date", "2026-11-01"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.todayOrderCount").value(1))
            .andExpect(jsonPath("$.todaySales").value(9.00));
        perform(get(base(shopA) + "/dashboard/summary"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.date").isString());

        perform(get(base(shopB) + "/dashboard/summary").param("date", "2026-10-31"), shopB.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.todayOrderCount").value(1))
            .andExpect(jsonPath("$.todaySales").value(999.00))
            .andExpect(jsonPath("$.pendingOrders").value(1));
        // A11 custom range (inclusive Bangkok days) + previous same-length period (Oct 28-29: nothing)
        perform(get(base(shopA) + "/dashboard/summary").param("from", "2026-10-30").param("to", "2026-10-31"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.range").value("custom"))
            .andExpect(jsonPath("$.days").value(2))
            .andExpect(jsonPath("$.orderCount").value(7))
            .andExpect(jsonPath("$.sales").value(725.00))
            .andExpect(jsonPath("$.previousFrom").value("2026-10-28"))
            .andExpect(jsonPath("$.previousTo").value("2026-10-29"))
            .andExpect(jsonPath("$.previousOrderCount").value(0))
            .andExpect(jsonPath("$.pendingOrders").value(4));
        // range boundary: Oct 31 alone excludes 23:59 Oct 30 and 00:01 Nov 1; previous day = Oct 30
        perform(get(base(shopA) + "/dashboard/summary").param("from", "2026-10-31").param("to", "2026-10-31"), shopA.token)
            .andExpect(jsonPath("$.orderCount").value(6))
            .andExpect(jsonPath("$.previousOrderCount").value(1))
            .andExpect(jsonPath("$.previousSales").value(100.00));
        perform(get(base(shopA) + "/dashboard/summary").param("from", "2023-10-31").param("to", "2026-10-31"), shopA.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.orderCount").value(7));
        order(a, "NOW-1", "11.00", "PACKED", "PAID", Instant.now().toString());
        perform(get(base(shopA) + "/dashboard/summary").param("range", "today"), shopA.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.days").value(1)).andExpect(jsonPath("$.orderCount").value(greaterThanOrEqualTo(1)));
        perform(get(base(shopA) + "/dashboard/summary").param("range", "7d"), shopA.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.days").value(7));
        perform(get(base(shopA) + "/dashboard/summary").param("range", "30d"), shopA.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.days").value(30));
        perform(get(base(shopA) + "/dashboard/summary").param("range", "1y"), shopA.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.days").value(anyOf(is(365), is(366))));
        // invalid ranges -> 400
        perform(get(base(shopA) + "/dashboard/summary").param("from", "2026-10-31").param("to", "2026-10-30"), shopA.token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fieldErrors[0].field").value("from")).andExpect(jsonPath("$.fieldErrors[0].code").value("RANGE"));
        perform(get(base(shopA) + "/dashboard/summary").param("from", "2023-10-30").param("to", "2026-10-31"), shopA.token).andExpect(status().isBadRequest());
        perform(get(base(shopA) + "/dashboard/summary").param("from", "2026-10-30"), shopA.token).andExpect(status().isBadRequest());
        perform(get(base(shopA) + "/dashboard/summary").param("range", "2y"), shopA.token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fieldErrors[0].field").value("range"));
        perform(get(base(shopA) + "/dashboard/summary").param("from", "x").param("to", "2026-10-31"), shopA.token).andExpect(status().isBadRequest());        // project rule (RequireShopPermission): non-member of the shop = 404 SHOP_NOT_FOUND, member without ORDER_READ = 403
        perform(get(base(shopB) + "/dashboard/summary"), shopA.token).andExpect(status().isNotFound());
        var viewer = token("md-viewer@example.com", "0814400008");
        addMember(shopA.shopId, users.findByEmailIgnoreCaseOrPhone("md-viewer@example.com", "0814400008").orElseThrow().getId(), "STORE_VIEWER");
        perform(get(base(shopA) + "/dashboard/summary").param("date", "2026-10-31"), viewer)
            .andExpect(status().isOk()).andExpect(jsonPath("$.todayOrderCount").value(6));
        jdbc.update("""
            DELETE FROM auth_role_permissions
             WHERE role_id = (SELECT id FROM auth_roles WHERE code = 'STORE_VIEWER')
               AND permission_id = (SELECT id FROM auth_permissions WHERE code = 'ORDER_READ')
            """);
        permissionMatrix.invalidate();
        try {
            perform(get(base(shopA) + "/dashboard/summary"), viewer).andExpect(status().isForbidden());
        } finally {
            jdbc.update("""
                INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version, created_date_time, modified_date_time)
                SELECT gen_random_uuid(), r.id, p.id, '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                  FROM auth_roles r, auth_permissions p
                 WHERE r.code = 'STORE_VIEWER' AND p.code = 'ORDER_READ'
                   AND NOT EXISTS (SELECT 1 FROM auth_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id)
                """);
            permissionMatrix.invalidate();
        }
        perform(get(base(shopA) + "/dashboard/summary").param("date", "31-10-2026"), shopA.token).andExpect(status().isBadRequest());
        var buyer = token("md-buyer@example.com", "0814400099");
        perform(get(base(shopA) + "/dashboard/summary"), buyer).andExpect(status().isNotFound());
        mvc.perform(get(base(shopA) + "/dashboard/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void insightsSeriesPaymentMethodsTopProductsAndValidation() throws Exception {
        var shopA = openShop("mi-a@example.com", "0814400051", "ร้านอินไซต์เอ", "Insight A", "1234567890751");
        var shopB = openShop("mi-b@example.com", "0814400052", "ร้านอินไซต์บี", "Insight B", "1234567890752");
        var a = shops.findByShopId(shopA.shopId).orElseThrow().getUuid();
        var b = shops.findByShopId(shopB.shopId).orElseThrow().getUuid();
        var o1 = order(a, "I-1", "300.00", "PACKED", "PAID", "2026-10-30T16:59:00Z", "BANK_TRANSFER");   // Oct 30 BKK
        var o2 = order(a, "I-2", "200.00", "DISPATCHED", "PAID", "2026-10-30T17:01:00Z", "COD");        // Oct 31 BKK
        var o3 = order(a, "I-3", "100.00", "MERCHANT_CONFIRMED", "PAID", "2026-10-31T05:00:00Z", "BANK_TRANSFER");
        var cx = order(a, "I-CXL", "999.00", "CANCELLED", "PAID", "2026-10-31T05:00:00Z", "BANK_TRANSFER");
        order(a, "I-UNPAID", "888.00", "PENDING_PAYMENT", "UNPAID", "2026-10-31T05:00:00Z", "BANK_TRANSFER");
        var ob = order(b, "IB-1", "777.00", "PACKED", "PAID", "2026-10-31T05:00:00Z", "BANK_TRANSFER");
        payment(o1, a, "PROMPTPAY", "CAPTURED", "300.00");
        payment(o3, a, "BANK_TRANSFER", "FAILED", "100.00");   // failed attempt ignored -> falls back to orders.payment_method
        item(o1, a, "เสื้อ", 2, "300.00");
        item(o2, a, "หมวก", 1, "150.00");
        item(o2, a, "เสื้อ", 1, "50.00");
        item(o3, a, "ถุงเท้า", 4, "100.00");
        item(cx, a, "ของยกเลิก", 9, "999.00");
        item(ob, b, "ของร้านบี", 1, "777.00");

        perform(get(base(shopA) + "/dashboard/insights").param("from", "2026-10-29").param("to", "2026-10-31"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bucket").value("day"))
            .andExpect(jsonPath("$.days").value(3))
            .andExpect(jsonPath("$.sales").value(600.00))
            .andExpect(jsonPath("$.paidOrderCount").value(3))
            .andExpect(jsonPath("$.series.length()").value(3))
            .andExpect(jsonPath("$.series[0].start").value("2026-10-29"))
            .andExpect(jsonPath("$.series[0].sales").value(0))
            .andExpect(jsonPath("$.series[1].sales").value(300.00))
            .andExpect(jsonPath("$.series[2].sales").value(300.00))
            .andExpect(jsonPath("$.series[2].orders").value(2))
            .andExpect(jsonPath("$.paymentMethods.length()").value(3))
            .andExpect(jsonPath("$.paymentMethods[0].method").value("PROMPTPAY"))
            .andExpect(jsonPath("$.paymentMethods[1].method").value("COD"))
            .andExpect(jsonPath("$.paymentMethods[1].sales").value(200.00))
            .andExpect(jsonPath("$.paymentMethods[2].method").value("BANK_TRANSFER"))
            .andExpect(jsonPath("$.topProducts.length()").value(3))
            .andExpect(jsonPath("$.topProducts[0].name").value("เสื้อ"))
            .andExpect(jsonPath("$.topProducts[0].quantity").value(3))
            .andExpect(jsonPath("$.topProducts[0].sales").value(350.00))
            .andExpect(jsonPath("$.topProducts[*].name", not(hasItem("ของยกเลิก"))))
            .andExpect(jsonPath("$.topProducts[*].name", not(hasItem("ของร้านบี"))));
        // weekly (<=186 days) and monthly buckets cover the whole range and sum to the period sales
        var week = perform(get(base(shopA) + "/dashboard/insights").param("from", "2026-09-01").param("to", "2026-10-31"), shopA.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.bucket").value("week"))
            .andExpect(jsonPath("$.series[0].start").value("2026-09-01"))
            .andExpect(jsonPath("$.series[-1:].end").value(hasItem("2026-10-31"))).andReturn();
        java.util.List<Number> weekSales = JsonPath.read(week.getResponse().getContentAsString(), "$.series[*].sales");
        assertEquals(600.0, weekSales.stream().mapToDouble(Number::doubleValue).sum(), 0.001);
        perform(get(base(shopA) + "/dashboard/insights").param("range", "1y"), shopA.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.bucket").value("month"))
            .andExpect(jsonPath("$.series.length()").value(anyOf(is(12), is(13))));
        perform(get(base(shopA) + "/dashboard/insights").param("range", "7d"), shopA.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.series.length()").value(7));
        perform(get(base(shopA) + "/dashboard/insights").param("from", "2026-10-31").param("to", "2026-10-30"), shopA.token).andExpect(status().isBadRequest());
        perform(get(base(shopA) + "/dashboard/insights").param("range", "2y"), shopA.token).andExpect(status().isBadRequest());
        perform(get(base(shopB) + "/dashboard/insights"), shopA.token).andExpect(status().isNotFound());
        mvc.perform(get(base(shopA) + "/dashboard/insights")).andExpect(status().isUnauthorized());
        jdbc.update("DELETE FROM order_items");
    }

    private UUID order(UUID shopUuid, String number, String total, String status, String payment, String placedAtUtc, String method) {
        order(shopUuid, number, total, status, payment, placedAtUtc);
        jdbc.update("UPDATE orders SET payment_method = ? WHERE order_number = ? AND shop_id = ?", method, number, shopUuid);
        return jdbc.queryForObject("SELECT id FROM orders WHERE order_number = ? AND shop_id = ?", UUID.class, number, shopUuid);
    }

    private void payment(UUID orderId, UUID shopUuid, String provider, String status, String amount) {
        jdbc.update("INSERT INTO payments (id, order_id, shop_id, provider, amount, currency, status, idempotency_key, paid_at, attempt_no, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, ?, 'THB', ?, ?, ?, 1, ?, ?)",
            UUID.randomUUID(), orderId, shopUuid, provider, new BigDecimal(amount), status, UUID.randomUUID().toString(), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
    }

    private void item(UUID orderId, UUID shopUuid, String name, int qty, String lineTotal) {
        var total = new BigDecimal(lineTotal);
        jdbc.update("INSERT INTO order_items (id, order_id, shop_id, sku_code_snapshot, product_name_snapshot, unit_price, quantity, discount_amount, line_total, currency, created_date_time, modified_date_time) VALUES (?, ?, ?, 'SKU', ?, ?, ?, 0, ?, 'THB', ?, ?)",
            UUID.randomUUID(), orderId, shopUuid, name, total.divide(BigDecimal.valueOf(qty), 2, java.math.RoundingMode.HALF_UP), qty, total, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
    }

    private UUID order(UUID shopUuid, String number, String total, String status, String payment, String placedAtUtc) {
        var placed = Timestamp.from(Instant.parse(placedAtUtc));
        var now = Timestamp.from(Instant.now());
        var id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO orders (id, shop_id, order_number, channel, status, payment_status, fulfillment_status, currency,
              grand_total, placed_at, merchant_confirm_status, created_date_time, modified_date_time)
            VALUES (?, ?, ?, 'STOREFRONT', ?, ?, 'UNFULFILLED', 'THB', ?, ?, 'AWAITING', ?, ?)
            """, id, shopUuid, number, status, payment, new BigDecimal(total), placed, placed, now);
        return id;
    }

    private void setBuyer(UUID orderId, UUID buyerUserId) {
        jdbc.update("UPDATE orders SET buyer_user_id = ? WHERE id = ?", buyerUserId, orderId);
    }

    private void address(UUID orderId, String recipientName) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
            INSERT INTO order_addresses (id, order_id, address_type, recipient_name, phone, address_line1, district, province, postal_code, country_code, created_date_time, modified_date_time)
            VALUES (?, ?, 'SHIPPING', ?, '0810000000', '1 Test Rd.', 'บางรัก', 'กรุงเทพมหานคร', '10500', 'TH', ?, ?)
            """, UUID.randomUUID(), orderId, recipientName, now, now);
    }
    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @org.junit.jupiter.api.AfterEach
    void dropRowsOtherSuitesDoNotClean() {
        jdbc.update("DELETE FROM product_images");
        jdbc.update("DELETE FROM order_items");
    }

    @Test
    void insightsTopCustomersRankingPaidRuleShopIsolationGuestMergeAndShare() throws Exception {
        var shopA = openShop("mc-a@example.com", "0814400071", "ร้านลูกค้าเอ", "Cust A", "1234567890771");
        var shopB = openShop("mc-b@example.com", "0814400072", "ร้านลูกค้าบี", "Cust B", "1234567890772");
        var a = shops.findByShopId(shopA.shopId).orElseThrow().getUuid();
        var b = shops.findByShopId(shopB.shopId).orElseThrow().getUuid();
        token("mc-buyer-top@example.com", "0814400081");
        token("mc-buyer-mid@example.com", "0814400082");
        var buyerTop = users.findByEmailIgnoreCaseOrPhone("mc-buyer-top@example.com", "0814400081").orElseThrow();
        var buyerMid = users.findByEmailIgnoreCaseOrPhone("mc-buyer-mid@example.com", "0814400082").orElseThrow();

        // Top buyer: 500 (two paid orders). Mid buyer: 300. Guest "สมชาย รับของ": 200+50=250 merged by name.
        // Boundary: 23:59 Oct 30 BKK excluded from Oct 31; 00:01 Oct 31 included.
        var oTop1 = order(a, "C-TOP-1", "300.00", "PACKED", "PAID", "2026-10-31T05:00:00Z");
        var oTop2 = order(a, "C-TOP-2", "200.00", "DISPATCHED", "PAID", "2026-10-31T06:00:00Z");
        var oMid = order(a, "C-MID", "300.00", "MERCHANT_CONFIRMED", "PAID", "2026-10-31T07:00:00Z");
        var oGuest1 = order(a, "C-G1", "200.00", "PACKED", "PAID", "2026-10-31T08:00:00Z");
        var oGuest2 = order(a, "C-G2", "50.00", "PACKED", "PAID", "2026-10-31T09:00:00Z");
        var oBefore = order(a, "C-BEFORE", "999.00", "PACKED", "PAID", "2026-10-30T16:59:00Z"); // Oct 30 23:59 BKK
        var oCxl = order(a, "C-CXL", "800.00", "CANCELLED", "PAID", "2026-10-31T10:00:00Z");
        var oRf = order(a, "C-RF", "700.00", "REFUNDED", "PAID", "2026-10-31T11:00:00Z");
        var oUnpaid = order(a, "C-UNPAID", "600.00", "PENDING_PAYMENT", "UNPAID", "2026-10-31T12:00:00Z");
        var oOtherShop = order(b, "C-B1", "5000.00", "PACKED", "PAID", "2026-10-31T05:00:00Z");

        setBuyer(oTop1, buyerTop.getId());
        setBuyer(oTop2, buyerTop.getId());
        setBuyer(oMid, buyerMid.getId());
        // guests: no buyer_user_id
        address(oTop1, "ผู้ซื้อท็อป");
        address(oTop2, "ผู้ซื้อท็อป");
        address(oMid, "ผู้ซื้อกลาง");
        address(oGuest1, "สมชาย รับของ");
        address(oGuest2, " สมชาย รับของ "); // same guest after trim/lower
        address(oBefore, "นอกช่วง");
        address(oCxl, "ยกเลิก");
        address(oRf, "คืนเงิน");
        address(oUnpaid, "ยังไม่จ่าย");
        address(oOtherShop, "ร้านอื่น");

        // Paid total Oct 31 = 300+200+300+200+50 = 1050
        var body = perform(get(base(shopA) + "/dashboard/insights").param("date", "2026-10-31"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sales").value(1050.00))
            .andExpect(jsonPath("$.topCustomers.length()").value(3))
            .andExpect(jsonPath("$.topCustomers[0].displayName").value("ผู้ซื้อท็อป"))
            .andExpect(jsonPath("$.topCustomers[0].orderCount").value(2))
            .andExpect(jsonPath("$.topCustomers[0].totalAmount").value(500.00))
            .andExpect(jsonPath("$.topCustomers[0].sharePercent").value(47.6))
            .andExpect(jsonPath("$.topCustomers[0].customerKey").value(startsWith("c1")))
            .andExpect(jsonPath("$.topCustomers[1].displayName").value("ผู้ซื้อกลาง"))
            .andExpect(jsonPath("$.topCustomers[1].totalAmount").value(300.00))
            .andExpect(jsonPath("$.topCustomers[1].sharePercent").value(28.6))
            .andExpect(jsonPath("$.topCustomers[2].displayName").value("สมชาย รับของ"))
            .andExpect(jsonPath("$.topCustomers[2].orderCount").value(2))
            .andExpect(jsonPath("$.topCustomers[2].totalAmount").value(250.00))
            .andExpect(jsonPath("$.topCustomers[2].sharePercent").value(23.8))
            .andExpect(jsonPath("$.topCustomers[2].customerKey").value(startsWith("n1")))
            .andExpect(jsonPath("$.topCustomers[*].displayName", not(hasItem("นอกช่วง"))))
            .andExpect(jsonPath("$.topCustomers[*].displayName", not(hasItem("ยกเลิก"))))
            .andExpect(jsonPath("$.topCustomers[*].displayName", not(hasItem("ร้านอื่น"))))
            .andExpect(jsonPath("$.topCustomers[0].phone").doesNotExist())
            .andExpect(jsonPath("$.topCustomers[0].email").doesNotExist())
            .andExpect(jsonPath("$.topCustomers[0].userId").doesNotExist())
            .andExpect(jsonPath("$.topCustomers[0].buyerUserId").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(buyerTop.getId().toString()), "raw buyer uuid must not appear");
        assertFalse(body.contains("\"phone\""), "phone field must not appear");
        assertFalse(body.contains("\"email\""), "email field must not appear");
        assertFalse(body.contains("\"userId\""), "userId field must not appear");
        java.util.List<Number> shares = JsonPath.read(body, "$.topCustomers[*].sharePercent");
        double sum = shares.stream().mapToDouble(Number::doubleValue).sum();
        assertTrue(sum <= 100.001, "top-5 share sum must not exceed 100, was " + sum);
        assertEquals(customerKeys.encode(a, null, buyerTop.getId()), JsonPath.read(body, "$.topCustomers[0].customerKey"));

        perform(get(base(shopB) + "/dashboard/insights").param("date", "2026-10-31"), shopB.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topCustomers.length()").value(1))
            .andExpect(jsonPath("$.topCustomers[0].displayName").value("ร้านอื่น"))
            .andExpect(jsonPath("$.topCustomers[0].totalAmount").value(5000.00))
            .andExpect(jsonPath("$.topCustomers[0].sharePercent").value(100.0));
    }

    @Test
    void insightsTopProductImagePrefersCurrentPrimaryThenSnapshot() throws Exception {
        var shop = openShop("mi-img@example.com", "0814400061", "Img Shop", "Img Shop", "1234567890761");
        var a = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        var now = Timestamp.from(Instant.now());
        var pid = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id, shop_id, slug, name_th, status, product_type, currency, option_schema, created_date_time, modified_date_time) VALUES (?, ?, 'p-img', 'Img product', 'PUBLISHED', 'SIMPLE', 'THB', '[]'::jsonb, ?, ?)", pid, a, now, now);
        var other = UUID.randomUUID();
        var primary = UUID.randomUUID();
        jdbc.update("INSERT INTO product_images (id, product_id, storage_path, sort_order, is_primary, created_date_time, modified_date_time) VALUES (?, ?, 'x/1.png', 0, false, ?, ?)", other, pid, now, now);
        jdbc.update("INSERT INTO product_images (id, product_id, storage_path, sort_order, is_primary, created_date_time, modified_date_time) VALUES (?, ?, 'x/2.png', 1, true, ?, ?)", primary, pid, now, now);
        var o = order(a, "IMG-1", "600.00", "PACKED", "PAID", "2026-10-31T05:00:00Z", "BANK_TRANSFER");
        jdbc.update("INSERT INTO order_items (id, order_id, shop_id, product_id, sku_code_snapshot, product_name_snapshot, image_url_snapshot, unit_price, quantity, discount_amount, line_total, currency, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, 'SKU', 'With product', '/old/snap.png', 300, 1, 0, 300, 'THB', ?, ?)", UUID.randomUUID(), o, a, pid, now, now);
        jdbc.update("INSERT INTO order_items (id, order_id, shop_id, sku_code_snapshot, product_name_snapshot, image_url_snapshot, unit_price, quantity, discount_amount, line_total, currency, created_date_time, modified_date_time) VALUES (?, ?, ?, 'SKU', 'Snapshot only', '/api/shops/s/products/x/images/y', 200, 1, 0, 200, 'THB', ?, ?)", UUID.randomUUID(), o, a, now, now);
        item(o, a, "No image", 1, "100.00");

        perform(get(base(shop) + "/dashboard/insights").param("date", "2026-10-31"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topProducts[0].name").value("With product"))
            .andExpect(jsonPath("$.topProducts[0].imageUrl").value("/api/merchant/shops/" + shop.shopId + "/products/" + pid + "/images/" + primary))
            .andExpect(jsonPath("$.topProducts[1].imageUrl").value("/api/shops/s/products/x/images/y"))
            .andExpect(jsonPath("$.topProducts[2].imageUrl").doesNotExist());
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

    private String adminToken() throws Exception {
        if (adminToken == null) {
            var approver = users.save(UserAccount.systemAccount("md-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"md-approver@example.com","password":"Password1"}
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
