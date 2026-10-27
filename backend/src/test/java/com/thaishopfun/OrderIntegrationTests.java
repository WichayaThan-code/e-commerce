package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.cart.CartItemRepository;
import com.thaishopfun.cart.CartRepository;
import com.thaishopfun.cart.CouponRepository;
import com.thaishopfun.cart.PromotionRepository;
import com.thaishopfun.cart.PromotionUsage;
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
import com.thaishopfun.order.OrderActorType;
import com.thaishopfun.order.OrderStateMachine;
import com.thaishopfun.order.OrderStatus;
import com.thaishopfun.order.OrderSystemService;
import com.thaishopfun.order.InvalidStatusTransitionException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/** Orders: checkout, state machine, side effects (stock, payment, coupon), permissions, idempotency, locking. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final String ADDRESS = """
        {"acceptTerms":true,"shippingAddress":{"recipientName":"สมหญิง ผู้รับ","phone":"0812345678","addressLine1":"1 ถนนสุขุมวิท",
         "subdistrict":"คลองเตย","district":"คลองเตย","province":"กรุงเทพมหานคร","postalCode":"10110"},"buyerNote":"ฝากหน้าบ้าน"}""";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
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

    @Autowired OrderSystemService system;

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
        for (var t : List.of("merchant_order_confirmations", "order_item_reveal_logs", "order_financial_snapshots", "refund_status_history", "payment_status_history",
                "payment_webhook_events", "payment_proofs", "refunds", "payments", "media_files", "shop_payment_gateways",
                "order_status_history", "inventory_reservations",
                "order_tag_assignments", "order_tags", "order_bulk_requests",
                "promotion_usages", "order_items", "order_addresses")) jdbc.update("DELETE FROM " + t);
        ledger.deleteAll();
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM shop_customers");
    }

    private void wipe() {
        cartItems.deleteAll();
        carts.deleteAll();
        jdbc.update("DELETE FROM coupon_collections");
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
        users.deleteAll();
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


    // ---------------- state machine table ----------------

    @Test
    void transitionMatrixIsExactlyTheDesignedTable() {
        Map<OrderStatus, Set<OrderStatus>> expected = new EnumMap<>(OrderStatus.class);
        var P = OrderStatus.PENDING_PAYMENT; var PA = OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM; var MC = OrderStatus.MERCHANT_CONFIRMED;
        var RP = OrderStatus.READY_TO_PACK; var PG = OrderStatus.PACKING; var PK = OrderStatus.PACKED; var D = OrderStatus.DISPATCHED;
        var DL = OrderStatus.DELIVERED; var C = OrderStatus.COMPLETED; var X = OrderStatus.CANCELLED; var RR = OrderStatus.REFUND_REQUESTED;
        var RF = OrderStatus.REFUNDED;
        expected.put(P, Set.of(PA, X));
        expected.put(PA, Set.of(MC, X, RR));
        expected.put(MC, Set.of(RP, PG, PK, D, X, RR));
        expected.put(RP, Set.of(PG, PK, D, X, RR));
        expected.put(PG, Set.of(PK, D, X, RR));
        expected.put(PK, Set.of(D, X, RR));
        expected.put(D, Set.of(DL, RR));
        expected.put(DL, Set.of(C, RR));
        expected.put(RR, Set.of(RF));
        expected.put(C, Set.of()); expected.put(X, Set.of()); expected.put(RF, Set.of());
        for (var from : OrderStatus.values()) {
            assertEquals(expected.get(from), OrderStateMachine.allowedTargets(from), "from " + from);
            for (var to : OrderStatus.values()) {
                boolean legal = expected.get(from).contains(to);
                if (!legal) {
                    var ex = assertThrows(InvalidStatusTransitionException.class, () -> OrderStateMachine.require(from, to, OrderActorType.ADMIN), from + "->" + to);
                    assertEquals(from, ex.getFromStatus());
                    assertEquals(to, ex.getToStatus());
                }
            }
        }
        // actor restrictions
        assertThrows(InvalidStatusTransitionException.class, () -> OrderStateMachine.require(P, PA, OrderActorType.BUYER));
        assertThrows(InvalidStatusTransitionException.class, () -> OrderStateMachine.require(PA, X, OrderActorType.BUYER));
        assertThrows(InvalidStatusTransitionException.class, () -> OrderStateMachine.require(PA, RR, OrderActorType.MERCHANT));
        assertThrows(InvalidStatusTransitionException.class, () -> OrderStateMachine.require(D, DL, OrderActorType.BUYER));
        assertThrows(InvalidStatusTransitionException.class, () -> OrderStateMachine.require(DL, C, OrderActorType.MERCHANT));
        assertThrows(InvalidStatusTransitionException.class, () -> OrderStateMachine.require(RR, RF, OrderActorType.BUYER));
        assertDoesNotThrow(() -> OrderStateMachine.require(P, X, OrderActorType.BUYER));
        assertDoesNotThrow(() -> OrderStateMachine.require(DL, C, OrderActorType.BUYER));
        assertDoesNotThrow(() -> OrderStateMachine.require(P, PA, OrderActorType.SYSTEM));
        // reject refund goes back to the remembered status, for merchant/admin only
        assertTrue(OrderStateMachine.allowedTargets(RR, OrderActorType.MERCHANT, D).contains(D));
        assertFalse(OrderStateMachine.allowedTargets(RR, OrderActorType.BUYER, D).contains(D));
    }

    // ---------------- end to end ----------------

    @Test
    void happyPathCheckoutToCompletedWithSnapshotsStockAndTimeline() throws Exception {
        var shop = openShop("o-happy@example.com", "0815000001", "ร้านสุข", "Happy", "1234567890401");
        var product = publishedVariantProduct(shop, "HP");
        var blkS = sku(product, "HP-BLK-S");
        var method = shippingMethod(shop, "Kerry", 50);
        var buyer = token("o-happy-buyer@example.com", "0815000101");
        add(buyer, blkS, 2);
        call(put("/api/cart/shops/" + shop.shopId + "/shipping-method"), buyer, "{\"shippingMethodId\":\"%s\"}".formatted(method)).andExpect(status().isOk());

        var out = checkout(buyer, "happy-key-0001").andExpect(status().isCreated())
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.orders.length()").value(1))
            .andExpect(jsonPath("$.orders[0].status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.orders[0].paymentStatus").value("UNPAID"))
            .andExpect(jsonPath("$.orders[0].fulfillmentStatus").value("UNFULFILLED"))
            .andExpect(jsonPath("$.orders[0].orderNumber").value(org.hamcrest.Matchers.matchesPattern("TSF-\\d{8}-[A-Z0-9]{6}")))
            .andExpect(jsonPath("$.orders[0].items[0].unitPrice").value(200.0))
            .andExpect(jsonPath("$.orders[0].items[0].quantity").value(2))
            .andExpect(jsonPath("$.orders[0].items[0].costPrice").doesNotExist())
            .andExpect(jsonPath("$.orders[0].subtotal").value(400.0))
            .andExpect(jsonPath("$.orders[0].shippingFee").value(50.0))
            .andExpect(jsonPath("$.orders[0].grandTotal").value(450.0))
            .andExpect(jsonPath("$.orders[0].shipping.methodName").value("Kerry"))
            .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(out, "$.orders[0].id");
        assertArrayEquals(new int[]{5, 2}, level(blkS));
        // cart lines bought are removed
        call(get("/api/cart"), buyer).andExpect(jsonPath("$.groups.length()").value(0));

        // price change after checkout does not touch the order
        jdbc.update("UPDATE product_skus SET price = 999 WHERE id = ?", UUID.fromString(blkS));
        call(get("/api/orders/" + id), buyer).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].unitPrice").value(200.0));
        var mo = base(shop) + "/orders/" + id;
        // owner rule: before accepting, the shop sees only id / number / created time
        call(get(mo), shop.token).andExpect(status().isOk())
            .andExpect(jsonPath("$.awaitingAcceptance").value(true))
            .andExpect(jsonPath("$.itemsHidden").value(true))
            .andExpect(jsonPath("$.itemCount").doesNotExist())
            .andExpect(jsonPath("$.grandTotal").doesNotExist())
            .andExpect(jsonPath("$.shippingAddress").doesNotExist());
        call(post(mo + "/mark-paid"), shop.token, "{\"amount\":450}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        call(post(mo + "/accept"), shop.token, "{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(true))
            .andExpect(jsonPath("$.order.status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.order.awaitingAcceptance").value(false))
            .andExpect(jsonPath("$.order.items[0].costPrice").value(80.0));
        // shipping before payment -> 409 with allowed list
        call(post(mo + "/ship"), shop.token, "{\"carrierCode\":\"KERRY\",\"trackingNumber\":\"K1\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
            .andExpect(jsonPath("$.from").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.to").value("DISPATCHED"))
            .andExpect(jsonPath("$.allowed").isArray());

        // mark paid: amount must match; accepted before payment -> straight to MERCHANT_CONFIRMED
        call(post(mo + "/mark-paid"), shop.token, "{\"amount\":1}").andExpect(status().isBadRequest());
        call(post(mo + "/mark-paid"), shop.token, "{\"amount\":450,\"paymentReference\":\"REF-1\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(true))
            .andExpect(jsonPath("$.order.status").value("MERCHANT_CONFIRMED"))
            .andExpect(jsonPath("$.order.paymentStatus").value("PAID"));
        call(post(mo + "/confirm"), shop.token, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false))
            .andExpect(jsonPath("$.order.status").value("MERCHANT_CONFIRMED"));
        call(get(mo), shop.token).andExpect(jsonPath("$.shippingAddress.masked").value(false))
            .andExpect(jsonPath("$.itemsHidden").value(false))
            .andExpect(jsonPath("$.items[0].costPrice").value(80.0))
            .andExpect(jsonPath("$.shippingAddress.phone").value("0812345678"));
        call(post(mo + "/process"), shop.token, "{\"status\":\"PACKED\"}").andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("PACKED"));
        // ship needs tracking
        call(post(mo + "/ship"), shop.token, "{\"carrierCode\":\"KERRY\"}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("trackingNumber")));
        call(post(mo + "/ship"), shop.token, "{\"carrierCode\":\"KERRY\",\"trackingNumber\":\"K123\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("DISPATCHED"))
            .andExpect(jsonPath("$.order.fulfillmentStatus").value("FULFILLED"))
            .andExpect(jsonPath("$.order.shipment.trackingNumber").value("K123"));
        assertArrayEquals(new int[]{3, 0}, level(blkS)); // 5 - 2 committed, nothing reserved
        call(post(mo + "/deliver"), shop.token, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("DELIVERED"));
        call(post("/api/orders/" + id + "/confirm-received"), buyer, "{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("COMPLETED"));
        // terminal
        call(post(mo + "/cancel"), shop.token, "{\"reason\":\"late\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.allowed.length()").value(0));
        var timeline = call(get(mo + "/timeline"), shop.token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> to = JsonPath.read(timeline, "$[*].toStatus");
        assertEquals(List.of("PENDING_PAYMENT", "PENDING_PAYMENT", "PAID_AWAITING_MERCHANT_CONFIRM", "MERCHANT_CONFIRMED", "PACKED", "DISPATCHED", "DELIVERED", "COMPLETED"), to);
        List<String> events = JsonPath.read(timeline, "$[*].event");
        assertEquals("ORDER_ACCEPTED", events.get(1));
        assertEquals("AUTO_CONFIRMED_ACCEPTED_BEFORE_PAYMENT", events.get(3));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM merchant_order_confirmations WHERE action = 'CONFIRM'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_item_reveal_logs", Integer.class));
        List<String> actors = JsonPath.read(timeline, "$[*].actorType");
        assertEquals("BUYER", actors.get(actors.size() - 1));
        // buyer timeline hides staff ids
        var bt = call(get("/api/orders/" + id + "/timeline"), buyer).andReturn().getResponse().getContentAsString();
        assertEquals(0, ((List<?>) JsonPath.read(bt, "$[?(@.actorType == 'MERCHANT')].actorUserId")).size());
        assertEquals(1, countLedger(blkS, "COMMIT_SALE"));
        // lists
        call(get("/api/orders"), buyer).andExpect(jsonPath("$.totalItems").value(1));
        call(get(base(shop) + "/orders?status=COMPLETED"), shop.token).andExpect(jsonPath("$.totalItems").value(1));
        call(get(base(shop) + "/orders?status=CANCELLED"), shop.token).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void cancelNeedsReasonAndReleasesStockExactlyOnce() throws Exception {
        var shop = openShop("o-cancel@example.com", "0815000002", "ร้านยกเลิก", "Cancel", "1234567890402");
        var blkS = sku(publishedVariantProduct(shop, "CN"), "CN-BLK-S");
        var buyer = token("o-cancel-buyer@example.com", "0815000102");
        add(buyer, blkS, 3);
        String id = JsonPath.read(checkout(buyer, "cancel-key-01").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.orders[0].id");
        assertArrayEquals(new int[]{5, 3}, level(blkS));
        call(post("/api/orders/" + id + "/cancel"), buyer, "{}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        call(post("/api/orders/" + id + "/cancel"), buyer, "{\"reason\":\"เปลี่ยนใจ\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(true)).andExpect(jsonPath("$.order.status").value("CANCELLED"))
            .andExpect(jsonPath("$.order.cancelReason").value("เปลี่ยนใจ"));
        call(post("/api/orders/" + id + "/cancel"), buyer, "{\"reason\":\"again\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(false));
        call(post(base(shop) + "/orders/" + id + "/cancel"), shop.token, "{\"reason\":\"x\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(false));
        assertArrayEquals(new int[]{5, 0}, level(blkS));
        assertEquals(1, countLedger(blkS, "RELEASE"));
        // buyer cannot cancel after payment
        add(buyer, blkS, 1);
        String id2 = JsonPath.read(checkout(buyer, "cancel-key-02").andReturn().getResponse().getContentAsString(), "$.orders[0].id");
        call(post(base(shop) + "/orders/" + id2 + "/accept"), shop.token, "{}").andExpect(status().isOk());
        call(post(base(shop) + "/orders/" + id2 + "/mark-paid"), shop.token, "{}").andExpect(status().isOk());
        call(post("/api/orders/" + id2 + "/cancel"), buyer, "{\"reason\":\"x\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        // merchant cancel of paid order opens an APPROVED refund; money is returned by the shop (proof upload)
        call(post(base(shop) + "/orders/" + id2 + "/cancel"), shop.token, "{\"reason\":\"หมด\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.paymentStatus").value("PAID"));
        assertArrayEquals(new int[]{5, 0}, level(blkS));
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM refunds", String.class));
        var rid = refundId(shop, id2);
        call(post(base(shop) + "/orders/" + id2 + "/refunds/" + rid + "/reject"), shop.token, "{\"reason\":\"no\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("REFUND_CANNOT_REJECT"));
        proof(shop, id2, rid).andExpect(status().isOk()).andExpect(jsonPath("$.order.paymentStatus").value("REFUNDED"))
            .andExpect(jsonPath("$.order.status").value("CANCELLED"));
    }

    @Test
    void refundRejectReturnsToPreviousAndApproveRestocksPerPolicy() throws Exception {
        var shop = openShop("o-refund@example.com", "0815000003", "ร้านคืน", "Refund", "1234567890403");
        var blkS = sku(publishedVariantProduct(shop, "RF"), "RF-BLK-S");
        var buyer = token("o-refund-buyer@example.com", "0815000103");
        add(buyer, blkS, 1);
        String a = JsonPath.read(checkout(buyer, "refund-key-a").andReturn().getResponse().getContentAsString(), "$.orders[0].id");
        var ma = base(shop) + "/orders/" + a;
        call(post(ma + "/accept"), shop.token, "{}").andExpect(status().isOk());
        call(post(ma + "/mark-paid"), shop.token, "{}").andExpect(status().isOk());
        call(post("/api/orders/" + a + "/refund-request"), buyer, "{}").andExpect(status().isBadRequest());
        call(post("/api/orders/" + a + "/refund-request"), buyer, "{\"reason\":\"ไม่ได้ใช้\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("REFUND_REQUESTED"))
            .andExpect(jsonPath("$.order.statusBeforeRefund").value("MERCHANT_CONFIRMED"));
        call(post(ma + "/refund/reject"), shop.token, "{}").andExpect(status().isBadRequest());
        call(post(ma + "/refund/reject"), shop.token, "{\"reason\":\"ส่งแล้ว\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("MERCHANT_CONFIRMED"))
            .andExpect(jsonPath("$.order.paymentStatus").value("PAID"));
        assertArrayEquals(new int[]{5, 1}, level(blkS));
        call(post("/api/orders/" + a + "/refund-request"), buyer, "{\"reason\":\"อีกครั้ง\"}").andExpect(status().isOk());
        call(post(ma + "/refund/approve"), shop.token, "{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.refund.status").value("APPROVED"))
            .andExpect(jsonPath("$.order.status").value("REFUND_REQUESTED"));
        proof(shop, a, refundId(shop, a)).andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("REFUNDED"))
            .andExpect(jsonPath("$.order.paymentStatus").value("REFUNDED"));
        assertArrayEquals(new int[]{5, 0}, level(blkS));

        // after dispatch: restock on approval
        add(buyer, blkS, 2);
        String b = JsonPath.read(checkout(buyer, "refund-key-b").andReturn().getResponse().getContentAsString(), "$.orders[0].id");
        var mb = base(shop) + "/orders/" + b;
        call(post(mb + "/accept"), shop.token, "{}").andExpect(status().isOk());
        call(post(mb + "/mark-paid"), shop.token, "{}").andExpect(status().isOk());
        call(post(mb + "/confirm"), shop.token, "{}").andExpect(status().isOk());
        call(post(mb + "/ship"), shop.token, "{\"carrierCode\":\"THAIPOST\",\"trackingNumber\":\"EX123456789TH\"}").andExpect(status().isOk());
        assertArrayEquals(new int[]{3, 0}, level(blkS));
        call(post("/api/orders/" + b + "/refund-request"), buyer, "{\"reason\":\"ชำรุด\"}").andExpect(status().isOk());
        call(post(mb + "/refund/approve"), shop.token, "{}").andExpect(status().isOk());
        call(post(mb + "/refund/approve"), shop.token, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        proof(shop, b, refundId(shop, b)).andExpect(status().isOk())
            .andExpect(jsonPath("$.order.fulfillmentStatus").value("RETURNED"));
        assertArrayEquals(new int[]{5, 0}, level(blkS));
        assertEquals(1, countLedger(blkS, "RESTOCK_REFUND"));
    }

    @Test
    void permissionsPerRoleAndCrossShopIsolation() throws Exception {
        var shopA = openShop("o-permA@example.com", "0815000004", "ร้านเอ", "ShopA", "1234567890404");
        var shopB = openShop("o-permB@example.com", "0815000005", "ร้านบี", "ShopB", "1234567890405");
        var blkS = sku(publishedVariantProduct(shopA, "PA"), "PA-BLK-S");
        var buyer = token("o-perm-buyer@example.com", "0815000104");
        var other = token("o-perm-other@example.com", "0815000105");
        add(buyer, blkS, 1);
        String id = JsonPath.read(checkout(buyer, "perm-key-001").andReturn().getResponse().getContentAsString(), "$.orders[0].id");
        var mo = base(shopA) + "/orders/" + id;
        // other buyer / other shop
        call(get("/api/orders/" + id), other).andExpect(status().isNotFound());
        call(post("/api/orders/" + id + "/cancel"), other, "{\"reason\":\"x\"}").andExpect(status().isNotFound());
        call(get(mo), shopB.token).andExpect(status().isNotFound());
        call(get(base(shopB) + "/orders/" + id), shopB.token).andExpect(status().isNotFound());
        call(post(base(shopB) + "/orders/" + id + "/mark-paid"), shopB.token, "{}").andExpect(status().isNotFound());
        call(get(base(shopB) + "/orders"), shopB.token).andExpect(jsonPath("$.totalItems").value(0));
        // buyer on merchant API
        call(post(mo + "/mark-paid"), buyer, "{}").andExpect(status().is4xxClientError());
        // staff roles
        var viewer = token("o-viewer@example.com", "0815000106");
        addMember(shopA.shopId, userId(viewer), "STORE_VIEWER");
        call(get(mo), viewer).andExpect(status().isOk());
        call(post(mo + "/mark-paid"), viewer, "{}").andExpect(status().isForbidden());
        call(post(mo + "/cancel"), viewer, "{\"reason\":\"x\"}").andExpect(status().isForbidden());
        // no state change happened (shop has not accepted: status itself is withheld from the shop)
        call(get(mo), shopA.token).andExpect(jsonPath("$.awaitingAcceptance").value(true)).andExpect(jsonPath("$.status").doesNotExist());
        call(post(mo + "/accept"), viewer, "{}").andExpect(status().isForbidden());
        call(post(base(shopB) + "/orders/" + id + "/accept"), shopB.token, "{}").andExpect(status().isNotFound());
        call(get("/api/orders/" + id), buyer).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        // admin: needs reason, can move any order along the table
        var admin = adminToken();
        call(get("/api/admin/orders"), admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        call(get("/api/admin/orders/" + id + "/transitions"), admin).andExpect(status().isOk());
        call(post("/api/admin/orders/" + id + "/transition"), admin, "{\"status\":\"PAID_AWAITING_MERCHANT_CONFIRM\"}").andExpect(status().isBadRequest());
        call(post("/api/admin/orders/" + id + "/transition"), admin, "{\"status\":\"DELIVERED\",\"reason\":\"x\"}").andExpect(status().isConflict());
        call(post("/api/admin/orders/" + id + "/transition"), admin, "{\"status\":\"PAID_AWAITING_MERCHANT_CONFIRM\",\"reason\":\"โอนแล้ว\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.order.paymentStatus").value("PAID"));
        call(get("/api/admin/orders"), buyer).andExpect(status().isForbidden());
        call(post("/api/admin/orders/" + id + "/transition"), shopA.token, "{\"status\":\"CANCELLED\",\"reason\":\"x\"}").andExpect(status().isForbidden());
        call(get("/api/orders/" + id), null).andExpect(status().isUnauthorized());
    }

    @Test
    void idempotencyKeyDoubleSubmitAndConcurrentSubmitCreateOneOrder() throws Exception {
        var shop = openShop("o-idem@example.com", "0815000006", "ร้านซ้ำ", "Idem", "1234567890406");
        var blkS = sku(publishedVariantProduct(shop, "ID"), "ID-BLK-S");
        var buyer = token("o-idem-buyer@example.com", "0815000107");
        add(buyer, blkS, 1);
        checkout(buyer, null).andExpect(status().isBadRequest());
        checkout(buyer, "bad key!").andExpect(status().isBadRequest());
        String first = JsonPath.read(checkout(buyer, "idem-key-0001").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.orders[0].id");
        checkout(buyer, "idem-key-0001").andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.orders[0].id").value(first));
        assertEquals(1, orderCount());
        // empty cart with a fresh key -> invalid
        checkout(buyer, "idem-key-0002").andExpect(status().is4xxClientError());

        add(buyer, blkS, 2);
        var pool = Executors.newFixedThreadPool(4);
        var go = new CountDownLatch(1);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < 4; i++) fs.add(pool.submit(() -> { go.await(); return checkout(buyer, "idem-key-race").andReturn().getResponse().getStatus(); }));
        go.countDown();
        List<Integer> codes = new ArrayList<>();
        for (var f : fs) codes.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(2, orderCount(), "codes " + codes);
        assertEquals(1, codes.stream().filter(c -> c == 201).count(), "codes " + codes);
        assertTrue(codes.stream().allMatch(c -> c == 201 || c == 200 || c == 409), "codes " + codes);
        assertArrayEquals(new int[]{5, 3}, level(blkS));
    }

    @Test
    void optimisticLockingExpectedVersionAndConcurrentTransitions() throws Exception {
        var shop = openShop("o-lock@example.com", "0815000007", "ร้านล็อก", "Lock", "1234567890407");
        var blkS = sku(publishedVariantProduct(shop, "LK"), "LK-BLK-S");
        var buyer = token("o-lock-buyer@example.com", "0815000108");
        add(buyer, blkS, 1);
        var created = checkout(buyer, "lock-key-001").andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.orders[0].id");
        int version = JsonPath.read(created, "$.orders[0].version");
        var mo = base(shop) + "/orders/" + id;
        call(post(mo + "/accept"), shop.token, "{\"expectedVersion\":%d}".formatted(version + 7)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_VERSION_CONFLICT"));
        call(post(mo + "/accept"), shop.token, "{\"expectedVersion\":%d}".formatted(version)).andExpect(status().isOk());
        call(post(mo + "/mark-paid"), shop.token, "{\"expectedVersion\":%d}".formatted(version)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_VERSION_CONFLICT"));
        call(post(mo + "/mark-paid"), shop.token, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("MERCHANT_CONFIRMED"));

        // two staff racing on the same version: exactly one wins, the other gets ORDER_VERSION_CONFLICT
        int current = JsonPath.read(call(get(mo), shop.token).andReturn().getResponse().getContentAsString(), "$.version");
        var pool = Executors.newFixedThreadPool(2);
        var go = new CountDownLatch(1);
        var body = "{\"expectedVersion\":%d,\"reason\":\"race\",\"status\":\"PACKED\"}".formatted(current);
        var f1 = pool.submit(() -> { go.await(); return call(post(mo + "/process"), shop.token, body).andReturn().getResponse().getStatus(); });
        var f2 = pool.submit(() -> { go.await(); return call(post(mo + "/cancel"), shop.token, body).andReturn().getResponse().getStatus(); });
        go.countDown();
        var codes = List.of(f1.get(60, TimeUnit.SECONDS), f2.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, codes.stream().filter(c -> c == 200).count(), "codes " + codes);
        assertEquals(1, codes.stream().filter(c -> c == 409).count(), "codes " + codes);
        // same action twice without a version: second is an idempotent no-op, no duplicate history
        var status = (String) JsonPath.read(call(get(mo), shop.token).andReturn().getResponse().getContentAsString(), "$.status");
        if (status.equals("PACKED")) call(post(mo + "/process"), shop.token, "{\"status\":\"PACKED\"}").andExpect(jsonPath("$.changed").value(false));
        else call(post(mo + "/cancel"), shop.token, "{\"reason\":\"x\"}").andExpect(jsonPath("$.changed").value(false));
        // placed, accepted, paid, auto-confirmed, race winner
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = ?", Integer.class, UUID.fromString(id)));
    }

    @Test
    void couponUsageRecordedLimitEnforcedAndReleasedOnCancel() throws Exception {
        var shop = openShop("o-coupon@example.com", "0815000008", "ร้านคูปอง", "CouponO", "1234567890408");
        var blkS = sku(publishedVariantProduct(shop, "CO"), "CO-BLK-S");
        createCoupon(shop, "{\"code\":\"ONCE\",\"type\":\"FIXED\",\"value\":30,\"usageLimitTotal\":1}");
        var b1 = token("o-coupon-b1@example.com", "0815000109");
        var b2 = token("o-coupon-b2@example.com", "0815000110");
        add(b1, blkS, 1);
        add(b2, blkS, 1);
        applyCoupon(b1, shop, "ONCE").andExpect(status().isOk());
        applyCoupon(b2, shop, "ONCE").andExpect(status().isOk());
        var out = checkout(b1, "coupon-key-1").andExpect(status().isCreated())
            .andExpect(jsonPath("$.orders[0].couponCode").value("ONCE"))
            .andExpect(jsonPath("$.orders[0].discountTotal").value(30.0))
            .andExpect(jsonPath("$.orders[0].items[0].discountAmount").value(30.0))
            .andReturn().getResponse().getContentAsString();
        assertEquals(1, usages.count());
        // second buyer had it in cart already: blocked at checkout
        checkout(b2, "coupon-key-2").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECKOUT_INVALID"));
        assertEquals(1, orderCount());
        String id = JsonPath.read(out, "$.orders[0].id");
        call(post("/api/orders/" + id + "/cancel"), b1, "{\"reason\":\"x\"}").andExpect(status().isOk());
        assertEquals(0, usages.count());
        checkout(b2, "coupon-key-3").andExpect(status().isCreated());
    }

    @Test
    void systemExpiresUnpaidOrdersAndCustomerHistoryStillWorks() throws Exception {
        var shop = openShop("o-exp@example.com", "0815000009", "ร้านหมดเวลา", "Expire", "1234567890409");
        var blkS = sku(publishedVariantProduct(shop, "EX"), "EX-BLK-S");
        var buyer = token("o-exp-buyer@example.com", "0815000111");
        add(buyer, blkS, 1);
        String old = JsonPath.read(checkout(buyer, "exp-key-0001").andReturn().getResponse().getContentAsString(), "$.orders[0].id");
        add(buyer, blkS, 1);
        String fresh = JsonPath.read(checkout(buyer, "exp-key-0002").andReturn().getResponse().getContentAsString(), "$.orders[0].id");
        jdbc.update("UPDATE orders SET placed_at = ? WHERE id = ?", Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)), UUID.fromString(old));
        assertEquals(1, system.expireUnpaid(Duration.ofHours(24)));
        assertEquals(0, system.expireUnpaid(Duration.ofHours(24)));
        call(get("/api/orders/" + old), buyer).andExpect(jsonPath("$.status").value("CANCELLED"));
        call(get("/api/orders/" + fresh), buyer).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        var tl = call(get("/api/orders/" + old + "/timeline"), buyer).andReturn().getResponse().getContentAsString();
        assertEquals(List.of("SYSTEM"), JsonPath.read(tl, "$[?(@.toStatus == 'CANCELLED')].actorType"));
        assertArrayEquals(new int[]{5, 1}, level(blkS));
        // checkout only links an existing CRM record; it never creates one
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM shop_customers", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE shop_customer_id IS NOT NULL", Integer.class));
    }

    // ---------------- order helpers ----------------

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return call(request.contentType(MediaType.APPLICATION_JSON).content(json), token);
    }

    @Test
    void checkoutRequiresAcceptTermsAndStoresAcceptanceTime() throws Exception {
        var shop = openShop("o-terms@example.com", "0815000091", "TermsShop", "Terms", "1234567890491");
        var sku = sku(publishedVariantProduct(shop, "TM"), "TM-BLK-S");
        var buyer = token("o-terms-buyer@example.com", "0815000191");
        add(buyer, sku, 1);
        var noTerms = ADDRESS.replace("\"acceptTerms\":true,", "");
        // missing -> 400, nothing created
        call(post("/api/checkout").header("Idempotency-Key", "terms-key-missing"), buyer, noTerms)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
        // false -> 400
        call(post("/api/checkout").header("Idempotency-Key", "terms-key-false"), buyer, ADDRESS.replace("\"acceptTerms\":true", "\"acceptTerms\":false"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
        assertEquals(0, orderCount());
        // true -> 201 with terms_accepted_at stored + exposed
        var out = checkout(buyer, "terms-key-ok01").andExpect(status().isCreated())
            .andExpect(jsonPath("$.orders[0].termsAcceptedAt").exists()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(out, "$.orders[0].id");
        assertEquals(1, jdbc.queryForObject("select count(*) from orders where id = ? and terms_accepted_at is not null",
            Integer.class, java.util.UUID.fromString(id)));
        // replay with the same key returns the same orders even without acceptTerms (check runs after the replay lookup)
        call(post("/api/checkout").header("Idempotency-Key", "terms-key-ok01"), buyer, noTerms)
            .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true)).andExpect(jsonPath("$.orders[0].id").value(id));
        assertEquals(1, orderCount());
    }

    private ResultActions checkout(String buyer, String key) throws Exception {
        var req = post("/api/checkout");
        if (key != null) req.header("Idempotency-Key", key);
        return call(req, buyer, ADDRESS);
    }

    private void add(String buyer, String sku, int qty) throws Exception {
        call(post("/api/cart/items"), buyer, "{\"skuId\":\"%s\",\"quantity\":%d}".formatted(sku, qty)).andExpect(status().isOk());
    }

    private String shippingMethod(OpenShop shop, String name, int fee) throws Exception {
        var out = call(post(base(shop) + "/shipping-methods"), shop.token, "{\"name\":\"%s\",\"feeType\":\"FLAT\",\"feeAmount\":%d}".formatted(name, fee))
            .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(out, "$.id");
    }

    /** {on_hand, reserved_qty} */
    private int[] level(String sku) {
        return jdbc.queryForObject("SELECT on_hand, reserved_qty FROM inventory_levels WHERE sku_id = ?",
            (rs, n) -> new int[]{rs.getInt(1), rs.getInt(2)}, UUID.fromString(sku));
    }

    private int countLedger(String sku, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE sku_id = ? AND entry_type = ?", Integer.class, UUID.fromString(sku), type);
    }

    private int orderCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
    }

    // ---------------- helpers ----------------

    private ResultActions cart(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions cart(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions merchant(MockHttpServletRequestBuilder request, String token) throws Exception {
        return cart(request, token);
    }

    private ResultActions merchant(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return cart(request, token, json);
    }

    private ResultActions guest(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        request.header("X-Cart-Token", token);
        if (json != null) request.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(request);
    }

    private ResultActions applyCoupon(String buyer, OpenShop shop, String code) throws Exception {
        // V31 coupon wallet: public codes must be collected first (result ignored: unknown / expired codes are rejected by apply below)
        cart(post("/api/me/coupons"), buyer, "{\"shopId\":%d,\"code\":\"%s\"}".formatted(shop.shopId, code));
        return cart(post("/api/cart/shops/" + shop.shopId + "/coupon"), buyer, "{\"code\":\"%s\"}".formatted(code));
    }

    private String createCoupon(OpenShop shop, String json) throws Exception {
        return merchant(post(base(shop) + "/coupons"), shop.token, json)
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private String publishedVariantProduct(OpenShop shop, String prefix) throws Exception {
        var created = createProduct(shop, COLOR_SIZE.formatted(activeCategory(0), prefix, prefix, prefix));
        String productId = JsonPath.read(created, "$.id");
        var file = new MockMultipartFile("file", "photo.png", "image/png", PNG);
        mvc.perform(multipart(base(shop) + "/products/" + productId + "/images").file(file)
            .header("Authorization", "Bearer " + shop.token)).andExpect(status().isOk());
        return merchant(post(base(shop) + "/products/" + productId + "/publish"), shop.token)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String createProduct(OpenShop shop, String json) throws Exception {
        return merchant(post(base(shop) + "/products"), shop.token, json)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String sku(String productJson, String code) {
        return JsonPath.<List<String>>read(productJson, "$.skus[?(@.skuCode == '" + code + "')].id").get(0);
    }

    private static String base(OpenShop shop) {
        return "/api/merchant/shops/" + shop.shopId;
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
            var approver = users.save(UserAccount.systemAccount("order-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"order-approver@example.com","password":"Password1"}
                """)).andExpect(status().isOk()).andReturn();
            adminToken = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
        }
        return adminToken;
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

    private String refundId(OpenShop shop, String orderId) throws Exception {
        var r = call(get(base(shop) + "/orders/" + orderId + "/refunds"), shop.token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> open = JsonPath.read(r, "$.refunds[?(@.status in ['REQUESTED','APPROVED'])].id");
        return open.get(0);
    }

    private ResultActions proof(OpenShop shop, String orderId, String refundId) throws Exception {
        return mvc.perform(multipart(base(shop) + "/orders/" + orderId + "/refunds/" + refundId + "/proof")
            .file(new MockMultipartFile("file", "refund.png", "image/png", PNG)).param("reference", "RF-" + refundId.substring(0, 8))
            .header("Authorization", "Bearer " + shop.token));
    }
}
