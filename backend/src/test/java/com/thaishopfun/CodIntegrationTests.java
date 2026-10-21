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
import com.thaishopfun.support.FakePaymentGateway;
import com.thaishopfun.order.OrderStateMachine;
import com.thaishopfun.order.OrderStatus;
import com.thaishopfun.order.PaymentMethod;
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


/** Cash on Delivery (V29): settings, checkout, accept → ship → deliver → cash collected, failed delivery, refund, slip blocking. Fixtures copied from PaymentIntegrationTests. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CodIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final String ADDRESS = """
        {"shippingAddress":{"recipientName":"สมหญิง ผู้รับ","phone":"0812345678","addressLine1":"1 ถนนสุขุมวิท",
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

    // ---------------- COD fixtures ----------------

    private static final String BANK = "{\"bankName\":\"KBANK\",\"accountNumber\":\"1234567890\",\"accountName\":\"ร้านทดสอบ\"}";

    private record Fixture(OpenShop shop, String buyer, String sku, String orderId) {
        String mo() { return "/api/merchant/shops/" + shop.shopId() + "/orders/" + orderId; }
        String bo() { return "/api/orders/" + orderId; }
    }

    private record Setup(OpenShop shop, String buyer, String sku) {}

    private int seq = 0;

    /** shop n (10..89) with one published product; buyer has nothing in the cart yet */
    private Setup setup(String tag, int n) throws Exception {
        var shop = openShop("c-" + tag + "@example.com", "08160000" + n, "ร้าน" + tag, "Cod" + tag, "12345678905" + n);
        var prefix = tag.toUpperCase().substring(0, 2) + n;
        var sku = sku(publishedVariantProduct(shop, prefix), prefix + "-BLK-S");
        var buyer = token("c-" + tag + "-buyer@example.com", "08170000" + n);
        return new Setup(shop, buyer, sku);
    }

    private ResultActions enableCod(OpenShop shop, String json) throws Exception {
        return call(put(base(shop) + "/payment-methods/cod"), shop.token(), json);
    }

    private ResultActions checkoutWith(String buyer, String method) throws Exception {
        var body = ADDRESS.substring(0, ADDRESS.lastIndexOf('}')) + (method == null ? "" : ",\"paymentMethod\":\"" + method + "\"") + "}";
        return call(post("/api/checkout").header("Idempotency-Key", "cod-key-" + (++seq) + "-" + UUID.randomUUID()), buyer, body);
    }

    /** 1 unit (200 + shipping 40) by COD with fee 20 → grand total 260 */
    private Fixture codOrder(String tag, int n) throws Exception {
        var s = setup(tag, n);
        enableCod(s.shop(), "{\"enabled\":true,\"fee\":20}").andExpect(status().isOk());
        add(s.buyer(), s.sku(), 1);
        var out = checkoutWith(s.buyer(), "COD").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Fixture(s.shop(), s.buyer(), s.sku(), JsonPath.read(out, "$.orders[0].id"));
    }

    private void accept(Fixture f) throws Exception {
        call(post(f.mo() + "/accept"), f.shop().token(), "{}").andExpect(status().isOk());
    }

    private void ship(Fixture f) throws Exception {
        call(post(f.mo() + "/ship"), f.shop().token(), "{\"carrierCode\":\"KERRY\",\"trackingNumber\":\"KER%s\"}".formatted(f.orderId().substring(0, 8)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("DISPATCHED"));
    }

    private void deliver(Fixture f) throws Exception {
        call(post(f.mo() + "/deliver"), f.shop().token(), "{}").andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("DELIVERED"));
    }

    private ResultActions collect(Fixture f, String json) throws Exception {
        return call(post(f.mo() + "/cod/collect"), f.shop().token(), json);
    }

    private List<String> events(Fixture f) throws Exception {
        return JsonPath.read(call(get(f.bo() + "/timeline"), f.buyer()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$[*].event");
    }

    private ResultActions refundProof(Fixture f, String refundId) throws Exception {
        return mvc.perform(multipart(f.mo() + "/refunds/" + refundId + "/proof")
            .file(new MockMultipartFile("file", "refund.png", "image/png", PNG)).param("reference", "RFP" + refundId.substring(0, 8))
            .header("Authorization", "Bearer " + f.shop().token()));
    }

    private String openRefund(Fixture f) throws Exception {
        var r = call(get(f.mo() + "/refunds"), f.shop().token()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> open = JsonPath.read(r, "$.refunds[?(@.status in ['REQUESTED','APPROVED'])].id");
        return open.get(0);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ---------------- tests ----------------

    @Test
    void stateMachineCodEdgesAreConditional() {
        var P = OrderStatus.PENDING_PAYMENT; var PA = OrderStatus.PAID_AWAITING_MERCHANT_CONFIRM; var MC = OrderStatus.MERCHANT_CONFIRMED;
        var D = OrderStatus.DISPATCHED; var DL = OrderStatus.DELIVERED; var C = OrderStatus.COMPLETED; var X = OrderStatus.CANCELLED;
        var RR = OrderStatus.REFUND_REQUESTED;
        var cod = new OrderStateMachine.Facts(PaymentMethod.COD, false);
        var collected = new OrderStateMachine.Facts(PaymentMethod.COD, true);
        var prepaid = OrderStateMachine.Facts.PREPAID;
        assertEquals(Set.of(MC, X), OrderStateMachine.allowedTargets(P, cod));
        assertEquals(Set.of(PA, X), OrderStateMachine.allowedTargets(P, prepaid));
        assertEquals(Set.of(DL, X), OrderStateMachine.allowedTargets(D, cod));
        assertEquals(Set.of(DL, RR), OrderStateMachine.allowedTargets(D, prepaid));
        assertEquals(Set.of(), OrderStateMachine.allowedTargets(DL, cod));
        assertEquals(Set.of(C, RR), OrderStateMachine.allowedTargets(DL, collected));
        assertFalse(OrderStateMachine.allowedTargets(MC, cod).contains(RR));
        assertEquals(MC, OrderStateMachine.acceptTarget(P, cod));
        assertNull(OrderStateMachine.acceptTarget(P, prepaid));
        assertEquals("COD_ORDER", assertThrows(InvalidStatusTransitionException.class,
            () -> OrderStateMachine.require(P, PA, OrderActorType.SYSTEM, cod)).getCode());
        assertEquals("COD_NOT_COLLECTED", assertThrows(InvalidStatusTransitionException.class,
            () -> OrderStateMachine.require(DL, C, OrderActorType.BUYER, cod)).getCode());
        assertEquals("COD_ALREADY_COLLECTED", assertThrows(InvalidStatusTransitionException.class,
            () -> OrderStateMachine.require(D, X, OrderActorType.MERCHANT, collected)).getCode());
        assertEquals("INVALID_STATUS_TRANSITION", assertThrows(InvalidStatusTransitionException.class,
            () -> OrderStateMachine.require(D, X, OrderActorType.MERCHANT, prepaid)).getCode());
        assertEquals("INVALID_STATUS_TRANSITION", assertThrows(InvalidStatusTransitionException.class,
            () -> OrderStateMachine.require(P, MC, OrderActorType.MERCHANT, prepaid)).getCode());
        assertThrows(InvalidStatusTransitionException.class, () -> OrderStateMachine.require(D, X, OrderActorType.BUYER, cod));
        assertTrue(OrderStateMachine.canCollectCod(DL, OrderActorType.MERCHANT, cod));
        assertTrue(OrderStateMachine.canCollectCod(DL, OrderActorType.SYSTEM, cod));
        assertFalse(OrderStateMachine.canCollectCod(DL, OrderActorType.BUYER, cod));
        assertFalse(OrderStateMachine.canCollectCod(D, OrderActorType.MERCHANT, cod));
        assertFalse(OrderStateMachine.canCollectCod(DL, OrderActorType.MERCHANT, collected));
        assertFalse(OrderStateMachine.canCollectCod(DL, OrderActorType.MERCHANT, prepaid));
    }

    @Test
    void codSettingsPermissionsAndCheckoutValidation() throws Exception {
        var s = setup("set", 11);
        call(get(base(s.shop()) + "/payment-methods"), s.shop().token()).andExpect(status().isOk())
            .andExpect(jsonPath("$.methods[?(@.method == 'COD')].enabled", hasItem(false)))
            .andExpect(jsonPath("$.methods[?(@.method == 'BANK_TRANSFER')].enabled", hasItem(true)));
        add(s.buyer(), s.sku(), 1);
        // method not enabled → 409 CHECKOUT_INVALID, nothing created
        checkoutWith(s.buyer(), "COD").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECKOUT_INVALID"))
            .andExpect(jsonPath("$.issues[0].code").value("PAYMENT_METHOD_NOT_AVAILABLE"))
            .andExpect(jsonPath("$.issues[0].shopId").value(s.shop().shopId()));
        checkoutWith(s.buyer(), "CASH").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("paymentMethod")));
        assertEquals(0, count("SELECT COUNT(*) FROM orders"));
        // permissions: viewer reads, cannot change; outsider / buyer cannot touch the shop setting
        var viewer = token("c-set-viewer@example.com", "0817555555");
        addMember(s.shop().shopId(), userId(viewer), "STORE_VIEWER");
        call(get(base(s.shop()) + "/payment-methods"), viewer).andExpect(status().isOk());
        call(put(base(s.shop()) + "/payment-methods/cod"), viewer, "{\"enabled\":true}").andExpect(status().isForbidden());
        call(put(base(s.shop()) + "/payment-methods/cod"), s.buyer(), "{\"enabled\":true}").andExpect(status().is4xxClientError());
        call(put(base(s.shop()) + "/payment-methods/cod"), null, "{\"enabled\":true}").andExpect(status().isUnauthorized());
        // validation
        enableCod(s.shop(), "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("enabled")));
        enableCod(s.shop(), "{\"enabled\":true,\"fee\":-1}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("fee")));
        enableCod(s.shop(), "{\"enabled\":true,\"fee\":1.234}").andExpect(status().isBadRequest());
        enableCod(s.shop(), "{\"enabled\":true,\"maxOrderAmount\":0}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("maxOrderAmount")));
        // enabled with a limit below 240 + fee 20 → COD_LIMIT_EXCEEDED
        enableCod(s.shop(), "{\"enabled\":true,\"fee\":20,\"maxOrderAmount\":259.99}").andExpect(status().isOk())
            .andExpect(jsonPath("$.methods[?(@.method == 'COD')].enabled", hasItem(true)));
        checkoutWith(s.buyer(), "COD").andExpect(status().isConflict()).andExpect(jsonPath("$.issues[0].code").value("COD_LIMIT_EXCEEDED"));
        enableCod(s.shop(), "{\"enabled\":true,\"fee\":20,\"maxOrderAmount\":260}").andExpect(status().isOk());
        call(get("/api/checkout/payment-methods?shopId=" + s.shop().shopId()), s.buyer()).andExpect(status().isOk())
            .andExpect(jsonPath("$.methods[?(@.method == 'COD')].fee", hasItem(20.0)))
            .andExpect(jsonPath("$.methods[?(@.method == 'COD')].maxOrderAmount", hasItem(260.0)));
        call(get("/api/checkout/payment-methods?shopId=" + s.shop().shopId()), null).andExpect(status().isUnauthorized());
        // stored encrypted (no plain JSON in the row)
        assertFalse(jdbc.queryForObject("SELECT config_encrypted FROM shop_payment_gateways WHERE provider = 'COD'", String.class).contains("fee"));
        // COD checkout: no slip / no payment row, total incl. fee, awaiting merchant acceptance
        var out = checkoutWith(s.buyer(), "cod").andExpect(status().isCreated())
            .andExpect(jsonPath("$.orders[0].paymentMethod").value("COD"))
            .andExpect(jsonPath("$.orders[0].codFee").value(20.0))
            .andExpect(jsonPath("$.orders[0].grandTotal").value(260.0))
            .andExpect(jsonPath("$.orders[0].status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.orders[0].paymentStatus").value("UNPAID"))
            .andExpect(jsonPath("$.orders[0].merchantConfirmStatus").value("AWAITING"))
            .andExpect(jsonPath("$.grandTotal").value(260.0)).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(out, "$.orders[0].id");
        assertEquals(0, count("SELECT COUNT(*) FROM payments"));
        assertEquals(1, level(s.sku())[1]); // reserved
        var f = new Fixture(s.shop(), s.buyer(), s.sku(), orderId);
        assertTrue(events(f).contains("ORDER_PLACED"));
        assertEquals("COD", jdbc.queryForObject("SELECT payment_method FROM orders WHERE id = ?", String.class, UUID.fromString(orderId)));
        // unpaid-order expiry never cancels COD
        jdbc.update("UPDATE orders SET placed_at = ? WHERE id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(3))), UUID.fromString(orderId));
        assertEquals(0, system.expireUnpaid(Duration.ofHours(24)));
        // BANK_TRANSFER stays the default and needs no setting
        add(s.buyer(), s.sku(), 1);
        checkoutWith(s.buyer(), null).andExpect(status().isCreated()).andExpect(jsonPath("$.orders[0].paymentMethod").value("BANK_TRANSFER"))
            .andExpect(jsonPath("$.orders[0].grandTotal").value(240.0)).andExpect(jsonPath("$.orders[0].codFee").value(0.0));
        // disabling stops new COD checkouts only
        enableCod(s.shop(), "{\"enabled\":false}").andExpect(status().isOk());
        add(s.buyer(), s.sku(), 1);
        checkoutWith(s.buyer(), "COD").andExpect(status().isConflict()).andExpect(jsonPath("$.issues[0].code").value("PAYMENT_METHOD_NOT_AVAILABLE"));
        call(get(f.bo()), s.buyer()).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void codAcceptShipDeliverCollectPaidAndComplete() throws Exception {
        var f = codOrder("flow", 12);
        // merchant visibility: nothing but accept / decline before acceptance
        call(get(f.mo()), f.shop().token()).andExpect(jsonPath("$.awaitingAcceptance").value(true))
            .andExpect(jsonPath("$.paymentMethod").doesNotExist()).andExpect(jsonPath("$.grandTotal").doesNotExist());
        call(get(f.mo() + "/transitions"), f.shop().token()).andExpect(jsonPath("$.transitions[*].action", hasItem("ACCEPT")));
        collect(f, "{\"amount\":260}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        call(post(f.mo() + "/ship"), f.shop().token(), "{\"carrierCode\":\"K\",\"trackingNumber\":\"T1\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        // no slip for COD; buyer payment page shows no account
        call(get(f.bo() + "/payment-info"), f.buyer()).andExpect(status().isOk()).andExpect(jsonPath("$.paymentMethod").value("COD"))
            .andExpect(jsonPath("$.canSubmitSlip").value(false)).andExpect(jsonPath("$.amountDue").value(260.0));
        // admin cannot "mark paid" a COD order (state machine: PREPAID edge)
        call(post("/api/admin/orders/" + f.orderId() + "/transition"), adminToken(),
            "{\"status\":\"PAID_AWAITING_MERCHANT_CONFIRM\",\"reason\":\"x\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COD_ORDER"));
        // accept: straight to MERCHANT_CONFIRMED, payment untouched, stock held
        call(post(f.mo() + "/accept"), f.shop().token(), "{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("MERCHANT_CONFIRMED"))
            .andExpect(jsonPath("$.order.paymentStatus").value("UNPAID"))
            .andExpect(jsonPath("$.order.paymentMethod").value("COD"));
        assertEquals("PAID_HOLD", jdbc.queryForObject("SELECT status FROM inventory_reservations", String.class));
        assertTrue(events(f).contains("COD_ACCEPTED"));
        // cash cannot be recorded before delivery
        collect(f, "{\"amount\":260}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        ship(f);
        assertEquals(1, countLedger(f.sku(), "COMMIT_SALE"));
        deliver(f);
        call(get(f.mo() + "/transitions"), f.shop().token()).andExpect(jsonPath("$.transitions[*].action", hasItem("COLLECT_COD")));
        // not complete before the cash is in
        call(post(f.bo() + "/confirm-received"), f.buyer(), "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COD_NOT_COLLECTED"));
        // validation / mismatch
        collect(f, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("amount")));
        collect(f, "{\"amount\":260,\"collectedAt\":\"%s\"}".formatted(Instant.now().plus(Duration.ofHours(2)))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("collectedAt")));
        collect(f, "{\"amount\":200}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
        assertEquals(0, count("SELECT COUNT(*) FROM payments"));
        // collected, with evidence file (multipart)
        mvc.perform(multipart(f.mo() + "/cod/collect").file(new MockMultipartFile("file", "receipt.png", "image/png", PNG))
                .param("amount", "260.00").param("reference", "CODREM-0001").param("reason", "เก็บเงินแล้ว")
                .header("Authorization", "Bearer " + f.shop().token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(true))
            .andExpect(jsonPath("$.payment.provider").value("COD")).andExpect(jsonPath("$.payment.status").value("CAPTURED"))
            .andExpect(jsonPath("$.payment.amount").value(260.0))
            .andExpect(jsonPath("$.payment.proofs[0].proofType").value("COD_RECEIPT"))
            .andExpect(jsonPath("$.payment.proofs[0].status").value("VERIFIED"))
            .andExpect(jsonPath("$.payment.history[0].event").value("COD_COLLECTED"))
            .andExpect(jsonPath("$.order.status").value("DELIVERED"))
            .andExpect(jsonPath("$.order.paymentStatus").value("PAID"))
            .andExpect(jsonPath("$.order.paidAmount").value(260.0));
        assertEquals("DELIVERY_PROOF", jdbc.queryForObject("SELECT purpose FROM media_files", String.class));
        // repeat = no-op
        collect(f, "{\"amount\":260}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        assertEquals(1, count("SELECT COUNT(*) FROM payments"));
        assertTrue(events(f).contains("COD_COLLECTED"));
        call(get(f.mo() + "/transitions"), f.shop().token()).andExpect(jsonPath("$.transitions[*].action", not(hasItem("COLLECT_COD"))));
        // still no slips for COD after collection
        var pid = jdbc.queryForObject("SELECT id FROM payments", UUID.class);
        call(post(f.mo() + "/payments/" + pid + "/confirm"), f.shop().token(), "{}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COD_NO_SLIP"));
        call(post(f.bo() + "/confirm-received"), f.buyer(), "{}").andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("COMPLETED"));
    }

    @Test
    void slipEndpointsAreBlockedForCod() throws Exception {
        var f = codOrder("slip", 13);
        call(put(base(f.shop()) + "/payment-accounts/BANK_TRANSFER"), f.shop().token(), BANK).andExpect(status().isOk());
        mvc.perform(multipart(f.bo() + "/payments").file(new MockMultipartFile("file", "slip.png", "image/png", PNG))
                .param("amount", "260").param("transferredAt", Instant.now().minusSeconds(60).toString()).param("reference", "REF9000")
                .header("Authorization", "Bearer " + f.buyer()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COD_NO_SLIP"));
        accept(f);
        var fake = UUID.randomUUID();
        call(post(f.mo() + "/payments/" + fake + "/confirm"), f.shop().token(), "{}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COD_NO_SLIP"));
        call(post(f.mo() + "/payments/" + fake + "/reject"), f.shop().token(), "{\"reason\":\"x\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COD_NO_SLIP"));
        call(post("/api/admin/orders/" + f.orderId() + "/payments/" + fake + "/confirm"), adminToken(), "{\"reason\":\"x\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COD_NO_SLIP"));
        call(post(f.mo() + "/mark-paid"), f.shop().token(), "{\"amount\":260}").andExpect(status().isConflict());
        assertEquals(0, count("SELECT COUNT(*) FROM payments"));
        assertEquals(0, count("SELECT COUNT(*) FROM payment_proofs"));
    }

    @Test
    void failedDeliveryCancelsWithReasonAndRestocksOnce() throws Exception {
        var f = codOrder("fail", 14);
        assertEquals(5, level(f.sku())[0]);
        accept(f);
        ship(f);
        assertEquals(4, level(f.sku())[0]);
        call(get(f.mo() + "/transitions"), f.shop().token()).andExpect(jsonPath("$.transitions[*].action", hasItem("CANCEL")));
        call(post(f.mo() + "/cancel"), f.shop().token(), "{}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("reason")));
        call(post(f.bo() + "/cancel"), f.buyer(), "{\"reason\":\"x\"}").andExpect(status().isConflict()); // buyer cannot after dispatch
        call(post(f.mo() + "/cancel"), f.shop().token(), "{\"reason\":\"ผู้รับปฏิเสธพัสดุ ตีกลับ\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("CANCELLED"))
            .andExpect(jsonPath("$.order.fulfillmentStatus").value("RETURNED"))
            .andExpect(jsonPath("$.order.paymentStatus").value("UNPAID"))
            .andExpect(jsonPath("$.order.cancelReason").value("ผู้รับปฏิเสธพัสดุ ตีกลับ"))
            .andExpect(jsonPath("$.order.shipment.status").value("RETURNED"));
        assertEquals(5, level(f.sku())[0]);
        assertEquals(0, level(f.sku())[1]);
        assertEquals(1, countLedger(f.sku(), "RETURN"));
        // repeated cancel is a no-op: stock comes back once
        call(post(f.mo() + "/cancel"), f.shop().token(), "{\"reason\":\"ซ้ำ\"}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        assertEquals(5, level(f.sku())[0]);
        assertEquals(1, countLedger(f.sku(), "RETURN"));
        assertEquals(0, count("SELECT COUNT(*) FROM refunds"));
        assertTrue(events(f).contains("DELIVERY_FAILED"));
        assertEquals(1, count("SELECT COUNT(*) FROM order_status_history WHERE to_status = 'CANCELLED' AND from_status = 'DISPATCHED'"));
    }

    @Test
    void collectedCodOrderCannotBeCancelledAndPrepaidCannotCancelAfterDispatch() throws Exception {
        var f = codOrder("noca", 15);
        accept(f);
        ship(f);
        deliver(f);
        call(post(f.mo() + "/cancel"), f.shop().token(), "{\"reason\":\"x\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        // prepaid: dispatched → cancelled still not allowed
        call(put(base(f.shop()) + "/payment-accounts/BANK_TRANSFER"), f.shop().token(), BANK).andExpect(status().isOk());
        add(f.buyer(), f.sku(), 1);
        var out = checkoutWith(f.buyer(), "BANK_TRANSFER").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var p = new Fixture(f.shop(), f.buyer(), f.sku(), JsonPath.read(out, "$.orders[0].id"));
        accept(p);
        call(post(p.mo() + "/mark-paid"), p.shop().token(), "{\"amount\":240,\"provider\":\"BANK_TRANSFER\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("MERCHANT_CONFIRMED"));
        ship(p);
        call(post(p.mo() + "/cancel"), p.shop().token(), "{\"reason\":\"x\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        // collect on a prepaid order
        call(post(p.mo() + "/cod/collect"), p.shop().token(), "{\"amount\":200}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("NOT_COD_ORDER"));
    }

    @Test
    void codRefundAfterCollectionUsesRefundWorkflow() throws Exception {
        var f = codOrder("rfnd", 16);
        accept(f);
        ship(f);
        deliver(f);
        // nothing to refund before the cash was collected
        call(post(f.bo() + "/refund-request"), f.buyer(), "{\"reason\":\"ของเสีย\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COD_NOT_COLLECTED"));
        collect(f, "{\"amount\":260}").andExpect(status().isOk());
        call(post(f.bo() + "/refund-request"), f.buyer(), "{\"reason\":\"ของเสีย\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("REFUND_REQUESTED"));
        var r = openRefund(f);
        call(post(f.mo() + "/refunds/" + r + "/approve"), f.shop().token(), "{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.refund.status").value("APPROVED")).andExpect(jsonPath("$.refund.amount").value(260.0));
        refundProof(f, r).andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("REFUNDED"))
            .andExpect(jsonPath("$.order.paymentStatus").value("REFUNDED"));
        assertEquals("REFUNDED", jdbc.queryForObject("SELECT status FROM payments", String.class));
        assertEquals(1, countLedger(f.sku(), "RESTOCK_REFUND"));
        assertEquals(5, level(f.sku())[0]);
        // the collect stays a no-op afterwards
        collect(f, "{\"amount\":260}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
    }

    @Test
    void collectPermissionsIsolationAndAdmin() throws Exception {
        var f = codOrder("perm", 17);
        accept(f);
        ship(f);
        deliver(f);
        var shopB = openShop("c-permb@example.com", "0816999998", "ร้านบี", "CodPermB", "1234567890598");
        call(post(base(shopB) + "/orders/" + f.orderId() + "/cod/collect"), shopB.token(), "{\"amount\":260}").andExpect(status().isNotFound());
        call(post(f.mo() + "/cod/collect"), f.buyer(), "{\"amount\":260}").andExpect(status().is4xxClientError());
        call(post(f.mo() + "/cod/collect"), null, "{\"amount\":260}").andExpect(status().isUnauthorized());
        var viewer = token("c-perm-viewer@example.com", "0817666666");
        addMember(f.shop().shopId(), userId(viewer), "STORE_VIEWER");
        call(post(f.mo() + "/cod/collect"), viewer, "{\"amount\":260}").andExpect(status().isForbidden());
        call(post("/api/admin/orders/" + f.orderId() + "/cod/collect"), f.shop().token(), "{\"amount\":260,\"reason\":\"x\"}").andExpect(status().isForbidden());
        call(post("/api/admin/orders/" + f.orderId() + "/cod/collect"), adminToken(), "{\"amount\":260}").andExpect(status().isBadRequest());
        call(post("/api/admin/orders/" + f.orderId() + "/cod/collect"), adminToken(), "{\"amount\":260,\"reason\":\"รายงานขนส่ง\",\"reference\":\"KER-REMIT-77\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.order.paymentStatus").value("PAID"));
        assertEquals("ADMIN", jdbc.queryForObject("SELECT actor_type FROM payment_status_history WHERE event = 'COD_COLLECTED'", String.class));
        // the same remittance reference cannot back another order of the shop
        add(f.buyer(), f.sku(), 1);
        var out = checkoutWith(f.buyer(), "COD").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var g = new Fixture(f.shop(), f.buyer(), f.sku(), JsonPath.read(out, "$.orders[0].id"));
        accept(g);
        ship(g);
        deliver(g);
        collect(g, "{\"amount\":260,\"reference\":\"ker-remit-77\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAYMENT_REFERENCE_DUPLICATE"));
        collect(g, "{\"amount\":260,\"expectedVersion\":0}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_VERSION_CONFLICT"));
    }

    // ---------------- order helpers ----------------

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return call(request.contentType(MediaType.APPLICATION_JSON).content(json), token);
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
            var approver = users.save(UserAccount.systemAccount("cod-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"cod-approver@example.com","password":"Password1"}
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
}
