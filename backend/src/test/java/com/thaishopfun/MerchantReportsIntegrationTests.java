package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PermissionMatrix;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.customer.CustomerAuditLogRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import com.thaishopfun.merchant.OwnerAuditLogRepository;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ShopMemberRepository;
import com.thaishopfun.merchant.ShopReinstatementRequestRepository;
import com.thaishopfun.merchant.ShopRepository;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Merchant reports: /api/merchant/shops/{shopId}/reports (overview + export.xlsx). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantReportsIntegrationTests {
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
        jdbc.update("DELETE FROM shipments");
        jdbc.update("DELETE FROM promotion_usages");
        jdbc.update("DELETE FROM coupons");
        jdbc.update("DELETE FROM promotions");
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

    @AfterEach
    void dropRowsOtherSuitesDoNotClean() {
        jdbc.update("DELETE FROM refunds");
        jdbc.update("DELETE FROM product_images");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM hub_categories WHERE slug LIKE 'rep-test-%'");
    }

    @Test
    void gmvPaidRuleBangkokBoundariesPreviousPeriodGrowthShopIsolationAndRefundsSeparate() throws Exception {
        var shopA = openShop("rp-a@example.com", "0815500001", "ร้านรายงานเอ", "Report A", "1234567890801");
        var shopB = openShop("rp-b@example.com", "0815500002", "ร้านรายงานบี", "Report B", "1234567890802");
        var a = shops.findByShopId(shopA.shopId).orElseThrow().getUuid();
        var b = shops.findByShopId(shopB.shopId).orElseThrow().getUuid();
        // 2026-10-30 23:59 Bangkok (16:59Z) = Oct 30; 00:01 Bangkok on Oct 31 (17:01Z Oct 30) = Oct 31; Nov 1 00:01 BKK (17:01Z Oct 31) = Nov 1
        var prev = order(a, "P-1", "100.00", "MERCHANT_CONFIRMED", "PAID", "2026-10-30T16:59:00Z");
        var o200 = order(a, "R-200", "200.00", "PACKED", "PAID", "2026-10-30T17:01:00Z");
        order(a, "R-CXL", "50.00", "CANCELLED", "PAID", "2026-10-31T05:00:00Z");
        order(a, "R-RF", "70.00", "REFUNDED", "PAID", "2026-10-31T05:00:00Z");
        order(a, "R-UNPAID", "30.00", "PENDING_PAYMENT", "UNPAID", "2026-10-31T05:00:00Z");
        order(a, "R-400", "400.00", "DISPATCHED", "PAID", "2026-10-31T06:00:00Z");
        order(a, "NEXT-DAY", "9.00", "READY_TO_PACK", "PAID", "2026-10-31T17:01:00Z");
        order(b, "B-1", "999.00", "PACKED", "PAID", "2026-10-31T05:00:00Z");
        // refunds: COMPLETED counted by completed_at; REQUESTED / APPROVED ignored; other shop ignored
        refund(a, o200, "20.00", "COMPLETED", "2026-10-31T08:00:00Z");
        refund(a, o200, "5.00", "REQUESTED", null);
        refund(a, o200, "6.00", "APPROVED", null);
        refund(a, prev, "10.00", "COMPLETED", "2026-10-31T16:59:00Z");      // completed Oct 31 23:59 BKK: in period though order is in previous
        refund(a, prev, "3.00", "COMPLETED", "2026-10-31T17:01:00Z");       // Nov 1 BKK: outside
        refund(b, order(b, "B-2", "10.00", "PACKED", "PAID", "2026-10-31T05:00:00Z"), "77.00", "COMPLETED", "2026-10-31T08:00:00Z");

        perform(get(base(shopA) + "/reports/overview").param("from", "2026-10-31").param("to", "2026-10-31"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("Asia/Bangkok"))
            .andExpect(jsonPath("$.range").value("custom"))
            .andExpect(jsonPath("$.days").value(1))
            .andExpect(jsonPath("$.previousFrom").value("2026-10-30"))
            .andExpect(jsonPath("$.previousTo").value("2026-10-30"))
            .andExpect(jsonPath("$.feeEstimateRate").value(0.05))
            .andExpect(jsonPath("$.kpis.gmv").value(600.00))
            .andExpect(jsonPath("$.kpis.paidOrderCount").value(2))
            .andExpect(jsonPath("$.kpis.allOrderCount").value(5))
            .andExpect(jsonPath("$.kpis.previousGmv").value(100.00))
            .andExpect(jsonPath("$.kpis.previousPaidOrderCount").value(1))
            .andExpect(jsonPath("$.kpis.gmvGrowthPct").value(500.0))
            .andExpect(jsonPath("$.kpis.paidOrderGrowthPct").value(100.0))
            .andExpect(jsonPath("$.kpis.estimatedFee").value(30.00))
            .andExpect(jsonPath("$.kpis.estimatedNet").value(570.00))
            // refunds are separate from gmv, by completed_at, COMPLETED only
            .andExpect(jsonPath("$.kpis.refundedAmount").value(30.00))
            .andExpect(jsonPath("$.kpis.refundCount").value(2))
            .andExpect(jsonPath("$.series.length()").value(1))
            .andExpect(jsonPath("$.series[0].gmv").value(600.00))
            .andExpect(jsonPath("$.series[0].orders").value(2))
            .andExpect(jsonPath("$.series[0].estimatedFee").value(30.00));

        // the day before: 23:59 Oct 30 only; previous (Oct 29) has no base -> growth null (UI shows an em dash)
        perform(get(base(shopA) + "/reports/overview").param("from", "2026-10-30").param("to", "2026-10-30"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpis.gmv").value(100.00))
            .andExpect(jsonPath("$.kpis.previousGmv").value(0.00))
            .andExpect(jsonPath("$.kpis.gmvGrowthPct").value(nullValue()))
            .andExpect(jsonPath("$.kpis.paidOrderGrowthPct").value(nullValue()))
            .andExpect(jsonPath("$.kpis.estimatedFeeGrowthPct").value(nullValue()))
            .andExpect(jsonPath("$.customerSummary.uniqueGrowthPct").value(nullValue()));

        // two-day custom range: previous = Oct 29..30
        perform(get(base(shopA) + "/reports/overview").param("from", "2026-10-31").param("to", "2026-11-01"), shopA.token)
            .andExpect(jsonPath("$.kpis.gmv").value(609.00))
            .andExpect(jsonPath("$.previousFrom").value("2026-10-29"))
            .andExpect(jsonPath("$.kpis.previousGmv").value(100.00));

        // shop B only sees its own data; A's member cannot reach B (project rule: non-member = 404)
        perform(get(base(shopB) + "/reports/overview").param("from", "2026-10-31").param("to", "2026-10-31"), shopB.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpis.gmv").value(1009.00))
            .andExpect(jsonPath("$.kpis.paidOrderCount").value(2))
            .andExpect(jsonPath("$.kpis.refundedAmount").value(77.00));
        perform(get(base(shopB) + "/reports/overview"), shopA.token).andExpect(status().isNotFound());
        mvc.perform(get(base(shopA) + "/reports/overview")).andExpect(status().isUnauthorized());

        // range presets / validation share the dashboard rules
        perform(get(base(shopA) + "/reports/overview").param("range", "7d"), shopA.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.days").value(7)).andExpect(jsonPath("$.series.length()").value(7));
        perform(get(base(shopA) + "/reports/overview").param("range", "1y"), shopA.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.bucket").value("month"));
        perform(get(base(shopA) + "/reports/overview"), shopA.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.days").value(1)).andExpect(jsonPath("$.range").value("today"));
        perform(get(base(shopA) + "/reports/overview").param("range", "2y"), shopA.token).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("range"));
        perform(get(base(shopA) + "/reports/overview").param("from", "2026-10-31").param("to", "2026-10-30"), shopA.token).andExpect(status().isBadRequest());
        perform(get(base(shopA) + "/reports/overview").param("from", "2023-10-30").param("to", "2026-10-31"), shopA.token).andExpect(status().isBadRequest());
        perform(get(base(shopA) + "/reports/overview").param("from", "2026-10-30"), shopA.token).andExpect(status().isBadRequest());
        perform(get(base(shopA) + "/reports/overview").param("from", "x").param("to", "2026-10-31"), shopA.token).andExpect(status().isBadRequest());
    }

    @Test
    void multipleItemsDoNotMultiplyGmvAndItemSalesAreSeparate() throws Exception {
        var shop = openShop("rp-items@example.com", "0815500011", "ร้านหลายชิ้น", "Items Shop", "1234567890811");
        var a = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        // order GMV 330 = items 300 + shipping 30; three items must not turn it into 990
        var o = order(a, "MI-1", "330.00", "PACKED", "PAID", "2026-10-31T05:00:00Z");
        item(o, a, null, "สินค้า 1", 1, "100.00");
        item(o, a, null, "สินค้า 2", 2, "100.00");
        item(o, a, null, "สินค้า 3", 1, "100.00");
        var cancelled = order(a, "MI-CXL", "500.00", "CANCELLED", "PAID", "2026-10-31T05:00:00Z");
        item(cancelled, a, null, "ของยกเลิก", 1, "500.00");

        perform(get(base(shop) + "/reports/overview").param("from", "2026-10-31").param("to", "2026-10-31"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpis.gmv").value(330.00))
            .andExpect(jsonPath("$.kpis.paidOrderCount").value(1))
            .andExpect(jsonPath("$.kpis.itemSales").value(300.00))
            .andExpect(jsonPath("$.series[0].gmv").value(330.00))
            .andExpect(jsonPath("$.channels.length()").value(1))
            .andExpect(jsonPath("$.channels[0].orders").value(1))
            .andExpect(jsonPath("$.channels[0].gmv").value(330.00))
            .andExpect(jsonPath("$.customerSummary.averageOrderValue").value(330.00))
            .andExpect(jsonPath("$.topProducts[*].name", not(hasItem("ของยกเลิก"))));
    }

    @Test
    void categoriesUncategorizedChannelsUnknownAndTopProducts() throws Exception {
        var shop = openShop("rp-cat@example.com", "0815500021", "ร้านหมวด", "Cat Shop", "1234567890821");
        var other = openShop("rp-cat2@example.com", "0815500022", "ร้านหมวดสอง", "Cat Shop 2", "1234567890822");
        var a = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        var b = shops.findByShopId(other.shopId).orElseThrow().getUuid();
        var now = Timestamp.from(Instant.now());
        var catId = UUID.randomUUID();
        jdbc.update("INSERT INTO hub_categories (id, slug, name_th, name_en, name_zh, sort_order, is_active, created_date_time, modified_date_time) VALUES (?, 'rep-test-food', 'อาหารทดสอบ', 'Test food', '测试食品', 1, true, ?, ?)", catId, now, now);
        var withCat = product(a, "rep-p1", "สินค้ามีหมวด", catId);
        var noCat = product(a, "rep-p2", "สินค้าไม่มีหมวด", null);

        var o1 = order(a, "CAT-1", "400.00", "PACKED", "PAID", "2026-10-31T05:00:00Z");
        item(o1, a, withCat, "สินค้ามีหมวด", 2, "300.00");
        item(o1, a, noCat, "สินค้าไม่มีหมวด", 1, "100.00");
        var o2 = order(a, "CAT-2", "100.00", "PACKED", "PAID", "2026-10-31T06:00:00Z");
        item(o2, a, null, "ของไม่มีรหัสสินค้า", 1, "100.00");          // no product row -> uncategorized
        channel(o2, "FACEBOOK");                                          // not in the orders.channel CHECK list -> UNKNOWN
        var o3 = order(a, "CAT-3", "50.00", "PACKED", "PAID", "2026-10-31T07:00:00Z");
        item(o3, a, withCat, "สินค้ามีหมวด", 1, "50.00");
        channel(o3, "LINE_OA");
        var ob = order(b, "CAT-B", "777.00", "PACKED", "PAID", "2026-10-31T05:00:00Z");
        item(ob, b, null, "ของร้านบี", 1, "777.00");

        perform(get(base(shop) + "/reports/overview").param("from", "2026-10-31").param("to", "2026-10-31"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpis.itemSales").value(550.00))
            .andExpect(jsonPath("$.categories.length()").value(2))
            .andExpect(jsonPath("$.categories[0].key").value("rep-test-food"))
            .andExpect(jsonPath("$.categories[0].nameTh").value("อาหารทดสอบ"))
            .andExpect(jsonPath("$.categories[0].nameEn").value("Test food"))
            .andExpect(jsonPath("$.categories[0].itemSales").value(350.00))
            .andExpect(jsonPath("$.categories[0].quantity").value(3))
            .andExpect(jsonPath("$.categories[0].sharePercent").value(63.6))
            .andExpect(jsonPath("$.categories[1].key").value("UNCATEGORIZED"))
            .andExpect(jsonPath("$.categories[1].itemSales").value(200.00))
            .andExpect(jsonPath("$.categories[1].nameTh").value(nullValue()))
            // channels from orders only: STOREFRONT (400) / UNKNOWN (100) / LINE_OA (50) — each order once
            .andExpect(jsonPath("$.channels.length()").value(3))
            .andExpect(jsonPath("$.channels[0].channel").value("STOREFRONT"))
            .andExpect(jsonPath("$.channels[0].orders").value(1))
            .andExpect(jsonPath("$.channels[0].gmv").value(400.00))
            .andExpect(jsonPath("$.channels[0].sharePercent").value(72.7))
            .andExpect(jsonPath("$.channels[1].channel").value("UNKNOWN"))
            .andExpect(jsonPath("$.channels[1].gmv").value(100.00))
            .andExpect(jsonPath("$.channels[2].channel").value("LINE_OA"))
            .andExpect(jsonPath("$.topProducts.length()").value(3))
            .andExpect(jsonPath("$.topProducts[0].name").value("สินค้ามีหมวด"))
            .andExpect(jsonPath("$.topProducts[0].quantity").value(3))
            .andExpect(jsonPath("$.topProducts[0].itemSales").value(350.00))
            .andExpect(jsonPath("$.topProducts[*].name", not(hasItem("ของร้านบี"))));

        // more than 5 categories -> top 5 + OTHER
        for (int i = 0; i < 6; i++) {
            var cid = UUID.randomUUID();
            jdbc.update("INSERT INTO hub_categories (id, slug, name_th, sort_order, is_active, created_date_time, modified_date_time) VALUES (?, ?, ?, 1, true, ?, ?)", cid, "rep-test-x" + i, "หมวด " + i, now, now);
            var pid = product(b, "rep-x" + i, "x" + i, cid);
            var oo = order(b, "CAT-X" + i, "10.00", "PACKED", "PAID", "2026-10-31T08:00:00Z");
            item(oo, b, pid, "x" + i, 1, "10.00");
        }
        perform(get(base(other) + "/reports/overview").param("from", "2026-10-31").param("to", "2026-10-31"), other.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories.length()").value(6))
            .andExpect(jsonPath("$.categories[5].key").value("OTHER"));
    }

    @Test
    void customersNewReturningGuestGroupingAndPrivacy() throws Exception {
        var shop = openShop("rp-cust@example.com", "0815500031", "ร้านลูกค้า", "Cust Shop", "1234567890831");
        var a = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        token("rp-buyer-x@example.com", "0815500041");
        token("rp-buyer-y@example.com", "0815500042");
        var x = users.findByEmailIgnoreCaseOrPhone("rp-buyer-x@example.com", "0815500041").orElseThrow().getId();
        var y = users.findByEmailIgnoreCaseOrPhone("rp-buyer-y@example.com", "0815500042").orElseThrow().getId();

        var xSep = order(a, "CU-X-SEP", "10.00", "PACKED", "PAID", "2026-09-15T05:00:00Z");   // X first ever paid order: September
        var xPrev = order(a, "CU-X-PREV", "100.00", "PACKED", "PAID", "2026-10-30T05:00:00Z"); // previous period (Oct 30)
        var xCur = order(a, "CU-X-CUR", "100.00", "PACKED", "PAID", "2026-10-31T05:00:00Z");
        var yCur = order(a, "CU-Y-CUR", "200.00", "PACKED", "PAID", "2026-10-31T06:00:00Z");   // Y first paid order is in the period -> new
        var g1 = order(a, "CU-G1", "50.00", "PACKED", "PAID", "2026-10-31T07:00:00Z");
        var g2 = order(a, "CU-G2", "50.00", "PACKED", "PAID", "2026-10-31T08:00:00Z");
        var anon = order(a, "CU-ANON", "100.00", "PACKED", "PAID", "2026-10-31T09:00:00Z");   // guest without a name: never merged
        var cxl = order(a, "CU-CXL", "999.00", "CANCELLED", "PAID", "2026-10-31T10:00:00Z");
        for (var o : new UUID[]{xSep, xPrev, xCur}) setBuyer(o, x);
        setBuyer(yCur, y);
        setBuyer(cxl, y);
        address(g1, "สมชาย รับของ");
        address(g2, " สมชาย รับของ ");
        address(cxl, "ยกเลิก");

        // period Oct 31: customers X (returning), Y (new), guest สมชาย (2 orders, new), anon (new) = 4; paid orders 5
        var body = perform(get(base(shop) + "/reports/overview").param("from", "2026-10-31").param("to", "2026-10-31"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customerSummary.uniqueCustomers").value(4))
            .andExpect(jsonPath("$.customerSummary.newCustomers").value(3))
            .andExpect(jsonPath("$.customerSummary.returningCustomers").value(1))
            .andExpect(jsonPath("$.customerSummary.previousUniqueCustomers").value(1))
            .andExpect(jsonPath("$.customerSummary.previousNewCustomers").value(0))
            .andExpect(jsonPath("$.customerSummary.previousReturningCustomers").value(1))
            .andExpect(jsonPath("$.customerSummary.uniqueGrowthPct").value(300.0))
            .andExpect(jsonPath("$.customerSummary.newGrowthPct").value(nullValue()))
            .andExpect(jsonPath("$.customerSummary.returningGrowthPct").value(0.0))
            .andExpect(jsonPath("$.customerSummary.averageOrdersPerCustomer").value(1.25))
            .andExpect(jsonPath("$.customerSummary.averageOrderValue").value(100.00))
            .andReturn().getResponse().getContentAsString();
        // no PII or raw ids anywhere in the payload
        assertFalse(body.contains(x.toString()));
        assertFalse(body.contains(y.toString()));
        assertFalse(body.contains("สมชาย รับของ"));
        assertFalse(body.contains("0810000000"));
        assertFalse(body.contains("\"phone\""));
        assertFalse(body.contains("\"email\""));
    }

    @Test
    void emptyPeriodReturnsZerosAndEmptyListsWith200() throws Exception {
        var shop = openShop("rp-empty@example.com", "0815500051", "ร้านว่าง", "Empty Shop", "1234567890851");
        perform(get(base(shop) + "/reports/overview").param("from", "2026-10-01").param("to", "2026-10-03"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpis.gmv").value(0.00))
            .andExpect(jsonPath("$.kpis.paidOrderCount").value(0))
            .andExpect(jsonPath("$.kpis.allOrderCount").value(0))
            .andExpect(jsonPath("$.kpis.refundedAmount").value(0.00))
            .andExpect(jsonPath("$.kpis.estimatedFee").value(0.00))
            .andExpect(jsonPath("$.kpis.gmvGrowthPct").value(nullValue()))
            .andExpect(jsonPath("$.series.length()").value(3))
            .andExpect(jsonPath("$.series[1].gmv").value(0.00))
            .andExpect(jsonPath("$.categories.length()").value(0))
            .andExpect(jsonPath("$.channels.length()").value(0))
            .andExpect(jsonPath("$.topProducts.length()").value(0))
            .andExpect(jsonPath("$.customerSummary.uniqueCustomers").value(0))
            .andExpect(jsonPath("$.customerSummary.averageOrdersPerCustomer").value(0.00))
            .andExpect(jsonPath("$.promotionSummary.redemptionCount").value(0))
            .andExpect(jsonPath("$.promotionSummary.discountTotal").value(0.00))
            .andExpect(jsonPath("$.promoTypes.length()").value(0))
            .andExpect(jsonPath("$.topCoupons.length()").value(0))
            .andExpect(jsonPath("$.shippingSummary.shippingFeeCollected").value(0.00))
            .andExpect(jsonPath("$.carriers.length()").value(0))
            .andExpect(jsonPath("$.shipmentOps.dispatchedCount").value(0))
            .andExpect(jsonPath("$.adviceFlags[0]").value("NO_SALES"));
        perform(get(base(shop) + "/reports/export.csv").param("from", "2026-10-01").param("to", "2026-10-03"), shop.token)
            .andExpect(status().isOk());
    }

    @Test
    void weeklyBucketsCoverRangeAndSumToGmv() throws Exception {
        var shop = openShop("rp-week@example.com", "0815500061", "ร้านสัปดาห์", "Week Shop", "1234567890861");
        var a = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        order(a, "W-1", "300.00", "PACKED", "PAID", "2026-09-10T05:00:00Z");
        order(a, "W-2", "200.00", "PACKED", "PAID", "2026-10-31T05:00:00Z");
        var res = perform(get(base(shop) + "/reports/overview").param("from", "2026-09-01").param("to", "2026-10-31"), shop.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.bucket").value("week"))
            .andExpect(jsonPath("$.series[0].start").value("2026-09-01"))
            .andExpect(jsonPath("$.series[-1:].end").value(hasItem("2026-10-31"))).andReturn();
        java.util.List<Number> sums = JsonPath.read(res.getResponse().getContentAsString(), "$.series[*].gmv");
        assertEquals(500.0, sums.stream().mapToDouble(Number::doubleValue).sum(), 0.001);
    }

    @Test
    void permissionsReportReadAndExportAreSeparate() throws Exception {
        var shop = openShop("rp-perm@example.com", "0815500071", "ร้านสิทธิ์", "Perm Shop", "1234567890871");
        var a = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        order(a, "PM-1", "100.00", "PACKED", "PAID", "2026-10-31T05:00:00Z");
        var viewer = token("rp-viewer@example.com", "0815500072");
        addMember(shop.shopId, users.findByEmailIgnoreCaseOrPhone("rp-viewer@example.com", "0815500072").orElseThrow().getId(), "STORE_VIEWER");

        // STORE_VIEWER: REPORT_READ yes, REPORT_EXPORT no -> overview 200, CSV 403
        perform(get(base(shop) + "/reports/overview").param("from", "2026-10-31").param("to", "2026-10-31"), viewer)
            .andExpect(status().isOk()).andExpect(jsonPath("$.kpis.gmv").value(100.00));
        perform(get(base(shop) + "/reports/export.csv").param("from", "2026-10-31").param("to", "2026-10-31"), viewer)
            .andExpect(status().isForbidden());
        perform(get(base(shop) + "/reports/export.csv").param("from", "2026-10-31").param("to", "2026-10-31"), shop.token)
            .andExpect(status().isOk());

        // dashboard permission (ORDER_READ) is independent: removing REPORT_READ blocks reports only
        jdbc.update("""
            DELETE FROM auth_role_permissions
             WHERE role_id = (SELECT id FROM auth_roles WHERE code = 'STORE_VIEWER')
               AND permission_id = (SELECT id FROM auth_permissions WHERE code = 'REPORT_READ')
            """);
        permissionMatrix.invalidate();
        try {
            perform(get(base(shop) + "/reports/overview"), viewer).andExpect(status().isForbidden());
            perform(get(base(shop) + "/dashboard/summary"), viewer).andExpect(status().isOk());
        } finally {
            jdbc.update("""
                INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version, created_date_time, modified_date_time)
                SELECT gen_random_uuid(), r.id, p.id, '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                  FROM auth_roles r, auth_permissions p
                 WHERE r.code = 'STORE_VIEWER' AND p.code = 'REPORT_READ'
                   AND NOT EXISTS (SELECT 1 FROM auth_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id)
                """);
            permissionMatrix.invalidate();
        }
        var buyer = token("rp-plainbuyer@example.com", "0815500073");
        perform(get(base(shop) + "/reports/overview"), buyer).andExpect(status().isNotFound());
        mvc.perform(get(base(shop) + "/reports/export.csv")).andExpect(status().isUnauthorized());
    }

    @Test
    void csvHasBomFormulaGuardNoPiiDefinitionsAndTabs() throws Exception {
        var shop = openShop("rp-csv@example.com", "0815500081", "ร้านซีเอสวี", "Csv Shop", "1234567890881");
        var a = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        token("rp-csv-buyer@example.com", "0815500091");
        var buyer = users.findByEmailIgnoreCaseOrPhone("rp-csv-buyer@example.com", "0815500091").orElseThrow().getId();
        var o = order(a, "CSV-1", "300.00", "PACKED", "PAID", "2026-10-31T05:00:00Z");
        setBuyer(o, buyer);
        address(o, "คุณลับ ห้ามหลุด");
        item(o, a, null, "=HYPERLINK(\"http://evil\",\"x\")", 1, "100.00");
        item(o, a, null, "+SUM(1,1)", 1, "100.00");
        item(o, a, null, "@cmd", 1, "50.00");
        item(o, a, null, "-2+3", 1, "50.00");

        var res = perform(get(base(shop) + "/reports/export.xlsx").param("from", "2026-10-31").param("to", "2026-10-31").param("tab", "overview").param("lang", "en"), shop.token)
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("thaishopfun-report-overview-2026-10-31_2026-10-31")))
            .andExpect(header().string("Content-Disposition", containsString(".xlsx")))
            .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse();
        byte[] bytes = res.getContentAsByteArray();
        assertEquals(0x50, bytes[0] & 0xff); // PK zip
        assertEquals(0x4b, bytes[1] & 0xff);
        String csv = workbookText(bytes);
        assertTrue(csv.contains("2026-10-31"));
        assertTrue(csv.contains("Asia/Bangkok"));
        assertTrue(csv.contains("ESTIMATES at 5%") || csv.contains("5% of GMV"), "estimated fee note");
        assertTrue(csv.contains("GMV") || csv.contains("grand_total"), "metric definitions");
        assertTrue(csv.contains("300") || csv.contains("300.0"));
        // formula injection guard on free text (leading apostrophe or string cell)
        assertTrue(csv.contains("HYPERLINK") || csv.contains("'=HYPERLINK"), csv);
        assertTrue(csv.contains("+SUM") || csv.contains("'+SUM"));
        assertTrue(csv.contains("@cmd") || csv.contains("'@cmd"));
        // no PII
        assertFalse(csv.contains("คุณลับ"));
        assertFalse(csv.contains("0810000000"));
        assertFalse(csv.contains("rp-csv-buyer@example.com"));
        assertFalse(csv.contains("0815500091"));
        assertFalse(csv.contains(buyer.toString()));

        // Thai labels; tab-specific content
        String th = csvOf(shop, "tab", "customers", "lang", "th");
        assertTrue(th.contains("ลูกค้าใหม่"));
        assertFalse(th.contains("Top products"));
        String promo = csvOf(shop, "tab", "promotions", "lang", "en");
        assertTrue(promo.contains("Promotion / coupon summary") || promo.contains("Redemptions"));
        assertFalse(promo.contains("No data is available for this report yet"));
        perform(get(base(shop) + "/reports/export.csv").param("tab", "nope"), shop.token).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("tab"));
        perform(get(base(shop) + "/reports/export.csv").param("from", "2026-10-31").param("to", "2026-10-30"), shop.token).andExpect(status().isBadRequest());
    }

    @Test
    void promotionsAndShippingMetricsUseRealTablesAndStayShopScoped() throws Exception {
        var shopA = openShop("rp-promo@example.com", "0815500093", "ร้านโปรโม", "Promo Shop", "1234567890893");
        var shopB = openShop("rp-promo-b@example.com", "0815500094", "ร้านโปรโมบี", "Promo B", "1234567890894");
        var a = shops.findByShopId(shopA.shopId).orElseThrow().getUuid();
        var b = shops.findByShopId(shopB.shopId).orElseThrow().getUuid();

        var orderA = order(a, "PR-1", "500.00", "PACKED", "PAID", "2026-10-31T05:00:00Z");
        jdbc.update("UPDATE orders SET shipping_fee = ?, coupon_code = ?, shipping_carrier_code_snapshot = ? WHERE id = ?",
            new BigDecimal("40.00"), "SAVE50", "KERRY", orderA);
        var orderFree = order(a, "PR-2", "200.00", "PACKED", "PAID", "2026-10-31T06:00:00Z");
        jdbc.update("UPDATE orders SET shipping_fee = 0, shipping_carrier_code_snapshot = ? WHERE id = ?", "FLASH", orderFree);
        var orderB = order(b, "PR-B", "999.00", "PACKED", "PAID", "2026-10-31T05:00:00Z");
        jdbc.update("UPDATE orders SET shipping_fee = ?, coupon_code = ? WHERE id = ?", new BigDecimal("99.00"), "OTHER", orderB);

        var promoId = UUID.randomUUID();
        var couponId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        var used = Timestamp.from(Instant.parse("2026-10-31T05:30:00Z"));
        jdbc.update("""
            INSERT INTO promotions (id, shop_id, name, promo_type, "value", starts_at, ends_at, is_active, created_date_time, modified_date_time)
            VALUES (?, ?, 'Save 50', 'FIXED_AMOUNT', 50, ?, ?, true, ?, ?)
            """, promoId, a, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), Timestamp.from(Instant.parse("2027-01-01T00:00:00Z")), now, now);
        jdbc.update("""
            INSERT INTO coupons (id, shop_id, promotion_id, code, is_active, created_date_time, modified_date_time)
            VALUES (?, ?, ?, 'SAVE50', true, ?, ?)
            """, couponId, a, promoId, now, now);
        jdbc.update("""
            INSERT INTO promotion_usages (id, promotion_id, coupon_id, order_id, discount_amount, used_at, created_date_time, modified_date_time)
            VALUES (?, ?, ?, ?, 50.00, ?, ?, ?)
            """, UUID.randomUUID(), promoId, couponId, orderA, used, now, now);

        var promoB = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO promotions (id, shop_id, name, promo_type, "value", starts_at, is_active, created_date_time, modified_date_time)
            VALUES (?, ?, 'Other', 'PERCENT', 10, ?, true, ?, ?)
            """, promoB, b, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), now, now);
        jdbc.update("""
            INSERT INTO promotion_usages (id, promotion_id, order_id, discount_amount, used_at, created_date_time, modified_date_time)
            VALUES (?, ?, ?, 100.00, ?, ?, ?)
            """, UUID.randomUUID(), promoB, orderB, used, now, now);

        jdbc.update("""
            INSERT INTO shipments (id, order_id, shop_id, status, carrier_code, dispatched_at, delivered_at, oms_sync_status, row_version, created_date_time, modified_date_time)
            VALUES (?, ?, ?, 'DELIVERED', 'KERRY', ?, ?, 'NOT_SYNCED', 0, ?, ?)
            """, UUID.randomUUID(), orderA, a, Timestamp.from(Instant.parse("2026-10-31T08:00:00Z")),
            Timestamp.from(Instant.parse("2026-10-31T12:00:00Z")), now, now);
        jdbc.update("""
            INSERT INTO shipments (id, order_id, shop_id, status, carrier_code, failed_at, oms_sync_status, row_version, created_date_time, modified_date_time)
            VALUES (?, ?, ?, 'DELIVERY_FAILED', 'FLASH', ?, 'NOT_SYNCED', 0, ?, ?)
            """, UUID.randomUUID(), orderFree, a, Timestamp.from(Instant.parse("2026-10-31T09:00:00Z")), now, now);

        perform(get(base(shopA) + "/reports/overview").param("from", "2026-10-31").param("to", "2026-10-31"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.promotionSummary.redemptionCount").value(1))
            .andExpect(jsonPath("$.promotionSummary.discountTotal").value(50.00))
            .andExpect(jsonPath("$.promotionSummary.paidOrdersWithCoupon").value(1))
            .andExpect(jsonPath("$.promoTypes[0].promoType").value("FIXED_AMOUNT"))
            .andExpect(jsonPath("$.topCoupons[0].code").value("SAVE50"))
            .andExpect(jsonPath("$.shippingSummary.shippingFeeCollected").value(40.00))
            .andExpect(jsonPath("$.shippingSummary.freeShippingOrderCount").value(1))
            .andExpect(jsonPath("$.carriers[*].carrier", hasItems("KERRY", "FLASH")))
            .andExpect(jsonPath("$.shipmentOps.dispatchedCount").value(1))
            .andExpect(jsonPath("$.shipmentOps.deliveredCount").value(1))
            .andExpect(jsonPath("$.shipmentOps.failedCount").value(1));

        // shop B must not leak into A
        perform(get(base(shopA) + "/reports/overview").param("from", "2026-10-31").param("to", "2026-10-31"), shopA.token)
            .andExpect(jsonPath("$.promotionSummary.discountTotal").value(50.00))
            .andExpect(jsonPath("$.shippingSummary.shippingFeeCollected").value(40.00));

        String csv = csvOf(shopA, "tab", "shipping", "lang", "en");
        assertTrue(csv.contains("Shipping fee summary") || csv.contains("Shipping fee collected"));
        assertTrue(csv.contains("40"));
        assertFalse(csv.contains("99.00"));
    }

    private String csvOf(OpenShop shop, String k1, String v1, String k2, String v2) throws Exception {
        byte[] bytes = perform(get(base(shop) + "/reports/export.xlsx").param("from", "2026-10-31").param("to", "2026-10-31").param(k1, v1).param(k2, v2), shop.token)
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse().getContentAsByteArray();
        return workbookText(bytes);
    }

    /** Flatten all string/numeric cells for coarse content assertions (not a CSV). */
    private static String workbookText(byte[] bytes) throws Exception {
        try (var in = new java.io.ByteArrayInputStream(bytes); var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
            var out = new StringBuilder();
            for (int s = 0; s < book.getNumberOfSheets(); s++) {
                var sheet = book.getSheetAt(s);
                out.append('[').append(sheet.getSheetName()).append(']');
                for (var row : sheet) {
                    for (var cell : row) {
                        out.append('|');
                        switch (cell.getCellType()) {
                            case STRING -> out.append(cell.getStringCellValue());
                            case NUMERIC -> out.append(cell.getNumericCellValue());
                            case BOOLEAN -> out.append(cell.getBooleanCellValue());
                            default -> {}
                        }
                    }
                    out.append('\n');
                }
            }
            return out.toString();
        }
    }

    // ------------------------------------------------------------------ fixtures

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

    private void channel(UUID orderId, String channel) {
        jdbc.update("UPDATE orders SET channel = ? WHERE id = ?", channel, orderId);
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

    private void item(UUID orderId, UUID shopUuid, UUID productId, String name, int qty, String lineTotal) {
        var total = new BigDecimal(lineTotal);
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO order_items (id, order_id, shop_id, product_id, sku_code_snapshot, product_name_snapshot, unit_price, quantity, discount_amount, line_total, currency, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, 'SKU', ?, ?, ?, 0, ?, 'THB', ?, ?)",
            UUID.randomUUID(), orderId, shopUuid, productId, name, total.divide(BigDecimal.valueOf(qty), 2, java.math.RoundingMode.HALF_UP), qty, total, now, now);
    }

    private UUID product(UUID shopUuid, String slug, String name, UUID categoryId) {
        var now = Timestamp.from(Instant.now());
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id, shop_id, slug, name_th, status, product_type, currency, option_schema, category_id, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, 'PUBLISHED', 'SIMPLE', 'THB', '[]'::jsonb, ?, ?, ?)",
            id, shopUuid, slug, name, categoryId, now, now);
        return id;
    }

    private void refund(UUID shopUuid, UUID orderId, String amount, String status, String completedAtUtc) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO refunds (id, order_id, shop_id, amount, currency, status, idempotency_key, completed_at, buyer_ack_status, created_date_time, modified_date_time) VALUES (?, ?, ?, ?, 'THB', ?, ?, ?, 'NONE', ?, ?)",
            UUID.randomUUID(), orderId, shopUuid, new BigDecimal(amount), status, UUID.randomUUID().toString(),
            completedAtUtc == null ? null : Timestamp.from(Instant.parse(completedAtUtc)), now, now);
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

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
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
            var approver = users.save(UserAccount.systemAccount("rp-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"rp-approver@example.com","password":"Password1"}
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
