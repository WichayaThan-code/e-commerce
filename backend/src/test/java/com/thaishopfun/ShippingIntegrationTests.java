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
import com.thaishopfun.support.FakeCarrierTrackingProvider;
import com.thaishopfun.order.ShipmentStateMachine;
import com.thaishopfun.order.ShipmentStatus;
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


/**
 * Shipping (V30): carriers + shop enablement, tracking validation / uniqueness, shipment status matrix, order sync
 * (ship / delivered / failed / returned for COD and prepaid), timeline, visibility, permissions, cross-shop, concurrency,
 * carrier webhook SPI. Fixtures copied from CodIntegrationTests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShippingIntegrationTests {
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

    // ---------------- shipping fixtures ----------------

    private Fixture prepaidOrder(String tag, int n) throws Exception {
        var s = setup(tag, n);
        call(put(base(s.shop()) + "/payment-accounts/BANK_TRANSFER"), s.shop().token(), BANK).andExpect(status().isOk());
        add(s.buyer(), s.sku(), 1);
        var out = checkoutWith(s.buyer(), "BANK_TRANSFER").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Fixture(s.shop(), s.buyer(), s.sku(), JsonPath.read(out, "$.orders[0].id"));
    }

    /** accept + mark paid → MERCHANT_CONFIRMED */
    private void acceptPaid(Fixture f) throws Exception {
        accept(f);
        call(post(f.mo() + "/mark-paid"), f.shop().token(), "{\"amount\":240,\"provider\":\"BANK_TRANSFER\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("MERCHANT_CONFIRMED"));
    }

    private static String sp(Fixture f) { return f.mo() + "/shipment"; }

    private int trk = 0;

    /** unique generic tracking number */
    private String tracking(String prefix) {
        return prefix + String.format("%04d", ++trk) + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private ResultActions shipWith(Fixture f, String carrier, String tracking) throws Exception {
        return call(post(sp(f)), f.shop().token(), "{\"carrierCode\":\"%s\",\"trackingNumber\":\"%s\"}".formatted(carrier, tracking));
    }

    private ResultActions mark(Fixture f, String action, String json) throws Exception {
        return call(post(sp(f) + "/" + action), f.shop().token(), json);
    }

    private String shipmentJson(Fixture f) throws Exception {
        return call(get(sp(f)), f.shop().token()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // ---------------- tests ----------------

    @Test
    void shipmentStatusMatrix() {
        var all = ShipmentStatus.values();
        var inFlightTargets = Map.of(
            ShipmentStatus.DISPATCHED, Set.of("IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "DELIVERY_FAILED", "RETURNED"),
            ShipmentStatus.IN_TRANSIT, Set.of("OUT_FOR_DELIVERY", "DELIVERED", "DELIVERY_FAILED", "RETURNED"),
            ShipmentStatus.OUT_FOR_DELIVERY, Set.of("IN_TRANSIT", "DELIVERED", "DELIVERY_FAILED", "RETURNED"),
            ShipmentStatus.DELIVERY_FAILED, Set.of("IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "RETURNED"));
        for (var actor : OrderActorType.values()) {
            for (var from : all) {
                var expected = actor == OrderActorType.BUYER ? Set.<String>of() : inFlightTargets.getOrDefault(from, Set.of());
                assertEquals(expected, new HashSet<>(ShipmentStateMachine.allowedTargets(from, actor)), actor + " from " + from);
                for (var to : all) {
                    assertEquals(expected.contains(to.name()), ShipmentStateMachine.allowed(from, to, actor), actor + " " + from + "→" + to);
                }
            }
        }
        assertEquals(ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.parse("failed"));
        assertEquals(ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.parse("out-for-delivery"));
        assertEquals("SHIPMENT_INVALID_TRANSITION", assertThrows(com.thaishopfun.order.ShipmentTransitionException.class,
            () -> ShipmentStateMachine.require(ShipmentStatus.DELIVERED, ShipmentStatus.IN_TRANSIT, OrderActorType.MERCHANT)).getCode());
        assertEquals(Set.of(ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURNED), ShipmentStateMachine.REASON_REQUIRED);
    }

    @Test
    void carrierMasterListShopEnablementAndShippingMethodCarrier() throws Exception {
        call(get("/api/carriers"), null).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].code", hasItem("KERRY"))).andExpect(jsonPath("$[*].code", hasItem("FLASH")))
            .andExpect(jsonPath("$[*].code", hasItem("JT"))).andExpect(jsonPath("$[?(@.code == 'THAIPOST')].trackingPattern", hasItem("^[A-Z]{2}[0-9]{9}TH$")));
        var f = prepaidOrder("car", 21);
        var carriersUrl = base(f.shop()) + "/carriers";
        call(get(carriersUrl), f.shop().token()).andExpect(status().isOk()).andExpect(jsonPath("$.restricted").value(false))
            .andExpect(jsonPath("$.carriers[?(@.code == 'FLASH')].enabled", hasItem(true)));
        call(put(carriersUrl + "/kerry"), f.shop().token(), "{}").andExpect(status().isBadRequest());
        call(put(carriersUrl + "/NOPE"), f.shop().token(), "{\"enabled\":true}").andExpect(status().isNotFound());
        var viewer = token("s-car-viewer@example.com", "0817555521");
        addMember(f.shop().shopId(), userId(viewer), "STORE_VIEWER");
        call(get(carriersUrl), viewer).andExpect(status().isOk());
        call(put(carriersUrl + "/kerry"), viewer, "{\"enabled\":true}").andExpect(status().isForbidden());
        // first choice "enable KERRY" → only KERRY from now on
        call(put(carriersUrl + "/kerry"), f.shop().token(), "{\"enabled\":true}").andExpect(status().isOk())
            .andExpect(jsonPath("$.restricted").value(true))
            .andExpect(jsonPath("$.carriers[?(@.code == 'KERRY')].enabled", hasItem(true)))
            .andExpect(jsonPath("$.carriers[?(@.code == 'FLASH')].enabled", hasItem(false)));
        acceptPaid(f);
        shipWith(f, "FLASH", tracking("FL")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'carrierCode')].code", hasItem("CARRIER_NOT_ENABLED")));
        assertEquals("MERCHANT_CONFIRMED", jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, UUID.fromString(f.orderId())));
        call(put(carriersUrl + "/FLASH"), f.shop().token(), "{\"enabled\":true}").andExpect(status().isOk());
        call(put(carriersUrl + "/kerry"), f.shop().token(), "{\"enabled\":false}").andExpect(status().isOk())
            .andExpect(jsonPath("$.carriers[?(@.code == 'KERRY')].enabled", hasItem(false)));
        shipWith(f, "kerry", tracking("KE")).andExpect(status().isBadRequest());
        shipWith(f, "flash", tracking("FL")).andExpect(status().isOk()).andExpect(jsonPath("$.shipment.carrierCode").value("FLASH"));
        // shipping methods: carrier code must be a master carrier (aliases normalized)
        call(post(base(f.shop()) + "/shipping-methods"), f.shop().token(), "{\"name\":\"J&T\",\"carrierCode\":\"j&t\",\"feeType\":\"FLAT\",\"feeAmount\":45}")
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.carrierCode").value("JT"));
        call(post(base(f.shop()) + "/shipping-methods"), f.shop().token(), "{\"name\":\"X\",\"carrierCode\":\"TH\",\"feeType\":\"FLAT\",\"feeAmount\":45}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("carrierCode")));
        // first choice "disable" on a fresh shop keeps every other carrier
        var g = setup("car2", 22);
        call(put(base(g.shop()) + "/carriers/SPX"), g.shop().token(), "{\"enabled\":false}").andExpect(status().isOk())
            .andExpect(jsonPath("$.restricted").value(true))
            .andExpect(jsonPath("$.carriers[?(@.code == 'SPX')].enabled", hasItem(false)))
            .andExpect(jsonPath("$.carriers[?(@.code == 'KERRY')].enabled", hasItem(true)));
    }

    @Test
    void trackingValidationNormalizationUniquenessAndShippedAt() throws Exception {
        var f = prepaidOrder("trk", 23);
        acceptPaid(f);
        shipWith(f, "NOPE", tracking("XX")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'carrierCode')].code", hasItem("INVALID")));
        shipWith(f, "THAIPOST", "12345").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'trackingNumber')].code", hasItem("INVALID_FORMAT")));
        shipWith(f, "KERRY", "K1").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'trackingNumber')].code", hasItem("INVALID_FORMAT")));
        shipWith(f, "KERRY", "AB#123").andExpect(status().isBadRequest());
        call(post(sp(f)), f.shop().token(), "{\"carrierCode\":\"KERRY\"}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("trackingNumber")));
        // shipped date
        call(post(sp(f)), f.shop().token(), "{\"carrierCode\":\"KERRY\",\"trackingNumber\":\"%s\",\"shippedAt\":\"%s\"}"
            .formatted(tracking("KE"), Instant.now().plus(Duration.ofHours(2)))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'shippedAt')].code", hasItem("FUTURE")));
        call(post(sp(f)), f.shop().token(), "{\"carrierCode\":\"KERRY\",\"trackingNumber\":\"%s\",\"shippedAt\":\"2020-01-01T10:00:00\"}"
            .formatted(tracking("KE"))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'shippedAt')].code", hasItem("BEFORE_ORDER")));
        call(post(sp(f)), f.shop().token(), "{\"carrierCode\":\"KERRY\",\"trackingNumber\":\"x\",\"shippedAt\":\"yesterday\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[?(@.field == 'shippedAt')].code", hasItem("INVALID")));
        assertEquals(0, count("SELECT COUNT(*) FROM shipments"));
        // THAIPOST S10 format, normalized (spaces / lower case)
        var shippedAt = Instant.now().minusSeconds(20).truncatedTo(ChronoUnit.SECONDS);
        call(post(sp(f)), f.shop().token(), "{\"carrierCode\":\"thailand post\",\"trackingNumber\":\" ex 123456780 th \",\"shippedAt\":\"%s\",\"note\":\"ส่งที่สาขาสีลม\"}"
            .formatted(shippedAt)).andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(true))
            .andExpect(jsonPath("$.orderToStatus").value("DISPATCHED"))
            .andExpect(jsonPath("$.shipment.carrierCode").value("THAIPOST"))
            .andExpect(jsonPath("$.shipment.trackingNumber").value("EX123456780TH"))
            .andExpect(jsonPath("$.shipment.trackingUrl").value("https://track.thailandpost.co.th/?trackNumber=EX123456780TH"))
            .andExpect(jsonPath("$.shipment.shippedAt").value(shippedAt.toString()))
            .andExpect(jsonPath("$.shipment.events[0].note").value("ส่งที่สาขาสีลม"));
        // same carrier + number on another order (any case) → 409; another carrier may reuse it
        var g = prepaidOrder("trk2", 24);
        acceptPaid(g);
        shipWith(g, "THAIPOST", "ex123456780th").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRACKING_NUMBER_DUPLICATE"));
        assertEquals("MERCHANT_CONFIRMED", jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, UUID.fromString(g.orderId())));
        var gTracking = tracking("KE");
        shipWith(g, "KERRY", gTracking).andExpect(status().isOk());
        // correcting tracking: duplicate refused, valid change audited in both timelines
        call(put(sp(g) + "/tracking"), g.shop().token(), "{\"carrierCode\":\"THAIPOST\",\"trackingNumber\":\"EX123456780TH\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRACKING_NUMBER_DUPLICATE"));
        call(put(sp(g) + "/tracking"), g.shop().token(), "{\"trackingNumber\":\"%s\"}".formatted(gTracking)).andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(false));
        var fixed = tracking("KE");
        call(put(sp(g) + "/tracking"), g.shop().token(), "{\"trackingNumber\":\"%s\",\"expectedVersion\":99}".formatted(fixed))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHIPMENT_VERSION_CONFLICT"));
        call(put(sp(g) + "/tracking"), g.shop().token(), "{\"trackingNumber\":\"%s\"}".formatted(fixed)).andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(true))
            .andExpect(jsonPath("$.shipment.trackingNumber").value(fixed))
            .andExpect(jsonPath("$.shipment.events[*].eventType", hasItem("TRACKING_UPDATED")));
        assertTrue(events(g).contains("SHIPMENT_TRACKING_UPDATED"));
        // the old number is free again
        var h = prepaidOrder("trk3", 25);
        acceptPaid(h);
        shipWith(h, "KERRY", gTracking).andExpect(status().isOk());
    }

    @Test
    void prepaidFullFlowSyncsOrderAndKeepsTimeline() throws Exception {
        var f = prepaidOrder("flow", 26);
        acceptPaid(f);
        call(get(sp(f)), f.shop().token()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHIPMENT_NOT_FOUND"));
        // tracking can be recorded before dispatch and is reused by ship
        var number = tracking("KE");
        call(put(sp(f) + "/tracking"), f.shop().token(), "{\"carrierCode\":\"KERRY\",\"trackingNumber\":\"%s\"}".formatted(number)).andExpect(status().isOk())
            .andExpect(jsonPath("$.shipment.status").value("READY_TO_PACK"));
        call(post(f.mo() + "/process"), f.shop().token(), "{\"status\":\"PACKED\"}").andExpect(status().isOk());
        call(post(sp(f) + "/in-transit"), f.shop().token(), "{}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SHIPMENT_INVALID_TRANSITION")).andExpect(jsonPath("$.from").value("PACKED"));
        call(post(sp(f)), f.shop().token(), "{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.fromStatus").value("PACKED")).andExpect(jsonPath("$.toStatus").value("DISPATCHED"))
            .andExpect(jsonPath("$.shipment.trackingNumber").value(number))
            .andExpect(jsonPath("$.shipment.orderStatus").value("DISPATCHED"));
        assertEquals(4, level(f.sku())[0]); // stock committed by the order SHIP transition
        call(post(sp(f)), f.shop().token(), "{}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        mark(f, "in-transit", "{\"location\":\"ศูนย์คัดแยกบางนา\",\"carrierStatusCode\":\"PICKUP\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.toStatus").value("IN_TRANSIT")).andExpect(jsonPath("$.orderToStatus").value("DISPATCHED"))
            .andExpect(jsonPath("$.shipment.pickedUpAt").isNotEmpty());
        mark(f, "in-transit", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        mark(f, "events", "{\"note\":\"ถึงศูนย์ปลายทาง\",\"location\":\"เชียงใหม่\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(true)).andExpect(jsonPath("$.toStatus").value("IN_TRANSIT"));
        mark(f, "events", "{}").andExpect(status().isBadRequest());
        mark(f, "events", "{\"status\":\"PACKED\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("status")));
        mark(f, "events", "{\"status\":\"NOPE\"}").andExpect(status().isBadRequest());
        mark(f, "out-for-delivery", "{\"eventAt\":\"2099-01-01T00:00:00Z\"}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'eventAt')].code", hasItem("FUTURE")));
        mark(f, "out-for-delivery", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.toStatus").value("OUT_FOR_DELIVERY"));
        mark(f, "failed", "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("reason")));
        mark(f, "failed", "{\"reason\":\"ไม่มีคนรับ\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.toStatus").value("DELIVERY_FAILED")).andExpect(jsonPath("$.orderToStatus").value("DISPATCHED"))
            .andExpect(jsonPath("$.shipment.failureReason").value("ไม่มีคนรับ"));
        mark(f, "events", "{\"status\":\"OUT_FOR_DELIVERY\",\"note\":\"นัดส่งใหม่\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.toStatus").value("OUT_FOR_DELIVERY"));
        var version = (int) JsonPath.read(shipmentJson(f), "$.version");
        mark(f, "delivered", "{\"expectedVersion\":%d}".formatted(version - 1)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SHIPMENT_VERSION_CONFLICT"));
        mark(f, "delivered", "{\"expectedVersion\":%d,\"note\":\"ผู้รับเซ็นแล้ว\"}".formatted(version)).andExpect(status().isOk())
            .andExpect(jsonPath("$.toStatus").value("DELIVERED")).andExpect(jsonPath("$.orderFromStatus").value("DISPATCHED"))
            .andExpect(jsonPath("$.orderToStatus").value("DELIVERED"))
            .andExpect(jsonPath("$.shipment.deliveredAt").isNotEmpty())
            .andExpect(jsonPath("$.shipment.allowedStatuses").isEmpty());
        mark(f, "delivered", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        mark(f, "in-transit", "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHIPMENT_INVALID_TRANSITION"))
            .andExpect(jsonPath("$.allowed").isEmpty());
        call(put(sp(f) + "/tracking"), f.shop().token(), "{\"trackingNumber\":\"%s\"}".formatted(tracking("KE"))).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SHIPMENT_TRACKING_LOCKED"));
        assertEquals(1, count("SELECT COUNT(*) FROM order_status_history WHERE to_status = 'DELIVERED' AND from_status = 'DISPATCHED'"));
        // shipment timeline: who / actor type / status / location / time
        var json = shipmentJson(f);
        List<String> statuses = JsonPath.read(json, "$.events[*].status");
        assertEquals(List.of("READY_TO_PACK", "PACKED", "DISPATCHED", "IN_TRANSIT", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERY_FAILED", "OUT_FOR_DELIVERY", "DELIVERED"), statuses);
        List<String> types = JsonPath.read(json, "$.events[*].eventType");
        assertEquals(List.of("TRACKING_UPDATED", "STATUS_CHANGED", "STATUS_CHANGED", "STATUS_CHANGED", "NOTE", "STATUS_CHANGED", "STATUS_CHANGED", "STATUS_CHANGED", "STATUS_CHANGED"), types);
        assertEquals("ศูนย์คัดแยกบางนา", JsonPath.read(json, "$.events[3].location"));
        assertEquals("PICKUP", JsonPath.read(json, "$.events[3].carrierStatusCode"));
        assertEquals("DISPATCHED", JsonPath.read(json, "$.events[3].fromStatus"));
        assertEquals("MERCHANT", JsonPath.read(json, "$.events[3].actorType"));
        assertEquals("MERCHANT", JsonPath.read(json, "$.events[3].source"));
        assertNotNull(JsonPath.read(json, "$.events[3].actorUserId"));
        // buyer: same shipment + timeline, staff user ids hidden
        var buyerJson = call(get(f.bo() + "/shipment"), f.buyer()).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DELIVERED")).andExpect(jsonPath("$.shippedBy").doesNotExist())
            .andExpect(jsonPath("$.allowedStatuses").isEmpty())
            .andReturn().getResponse().getContentAsString();
        List<Object> ids = JsonPath.read(buyerJson, "$.events[*].actorUserId");
        assertTrue(ids.stream().allMatch(Objects::isNull), ids.toString());
        var timeline = events(f);
        for (var e : List.of("SHIPPED", "SHIPMENT_IN_TRANSIT", "SHIPMENT_OUT_FOR_DELIVERY", "SHIPMENT_DELIVERY_FAILED", "DELIVERED")) {
            assertTrue(timeline.contains(e), e + " in " + timeline);
        }
        // buyer completes as before
        call(post(f.bo() + "/confirm-received"), f.buyer(), "{}").andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("COMPLETED"));
    }

    @Test
    void codFailedReturnedCancelsAndRestocksOnce() throws Exception {
        var f = codOrder("cfail", 27);
        accept(f);
        shipWith(f, "KERRY", tracking("KE")).andExpect(status().isOk());
        assertEquals(4, level(f.sku())[0]);
        // a failed attempt alone keeps the order out for delivery
        mark(f, "failed", "{\"reason\":\"โทรไม่ติด\"}").andExpect(status().isOk()).andExpect(jsonPath("$.orderToStatus").value("DISPATCHED"));
        mark(f, "returned", "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("reason")));
        mark(f, "returned", "{\"reason\":\"ผู้รับปฏิเสธ ตีกลับ\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.toStatus").value("RETURNED")).andExpect(jsonPath("$.orderToStatus").value("CANCELLED"))
            .andExpect(jsonPath("$.shipment.returnedAt").isNotEmpty());
        call(get(f.mo()), f.shop().token()).andExpect(jsonPath("$.fulfillmentStatus").value("RETURNED"))
            .andExpect(jsonPath("$.paymentStatus").value("UNPAID")).andExpect(jsonPath("$.cancelReason").value("ผู้รับปฏิเสธ ตีกลับ"));
        assertEquals(5, level(f.sku())[0]);
        assertEquals(1, countLedger(f.sku(), "RETURN"));
        // repeats: no second restock
        mark(f, "returned", "{\"reason\":\"ซ้ำ\"}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        call(post(f.mo() + "/cancel"), f.shop().token(), "{\"reason\":\"ซ้ำ\"}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        mark(f, "failed", "{\"reason\":\"x\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHIPMENT_INVALID_TRANSITION"));
        assertEquals(5, level(f.sku())[0]);
        assertEquals(1, countLedger(f.sku(), "RETURN"));
        assertEquals(0, count("SELECT COUNT(*) FROM refunds"));
        assertTrue(events(f).contains("DELIVERY_FAILED"));

        // failed + returned in one call
        var g = codOrder("cfail2", 28);
        accept(g);
        shipWith(g, "FLASH", tracking("FL")).andExpect(status().isOk());
        mark(g, "failed", "{\"reason\":\"ที่อยู่ผิด\",\"returned\":true}").andExpect(status().isOk())
            .andExpect(jsonPath("$.fromStatus").value("DISPATCHED")).andExpect(jsonPath("$.toStatus").value("RETURNED"))
            .andExpect(jsonPath("$.orderToStatus").value("CANCELLED"));
        assertEquals(5, level(g.sku())[0]);
        assertEquals(1, countLedger(g.sku(), "RETURN"));
        List<String> statuses = JsonPath.read(shipmentJson(g), "$.events[*].status");
        assertEquals(List.of("DISPATCHED", "DELIVERY_FAILED", "RETURNED"), statuses);
    }

    @Test
    void prepaidReturnGoesThroughRefundFlowAndRestocksOnce() throws Exception {
        var f = prepaidOrder("pret", 29);
        acceptPaid(f);
        shipWith(f, "JT", tracking("JT")).andExpect(status().isOk());
        assertEquals(4, level(f.sku())[0]);
        mark(f, "returned", "{\"reason\":\"พัสดุตีกลับ\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.toStatus").value("RETURNED")).andExpect(jsonPath("$.orderToStatus").value("DISPATCHED"));
        assertEquals(4, level(f.sku())[0]); // no stock movement until the refund
        assertTrue(events(f).contains("SHIPMENT_RETURNED"));
        // cannot be "delivered" any more, neither via order nor shipment
        call(post(f.mo() + "/deliver"), f.shop().token(), "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHIPMENT_RETURNED"));
        mark(f, "delivered", "{}").andExpect(status().isConflict());
        // refund flow (buyer request → approve → proof) restocks once
        call(post(f.bo() + "/refund-request"), f.buyer(), "{\"reason\":\"ไม่ได้รับของ\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("REFUND_REQUESTED"));
        var r = openRefund(f);
        call(post(f.mo() + "/refunds/" + r + "/approve"), f.shop().token(), "{}").andExpect(status().isOk());
        refundProof(f, r).andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("REFUNDED"));
        assertEquals(5, level(f.sku())[0]);
        assertEquals(1, countLedger(f.sku(), "RESTOCK_REFUND"));
        call(get(sp(f)), f.shop().token()).andExpect(jsonPath("$.status").value("RETURNED"));

        // in-flight updates still work while the buyer's refund request is open
        var g = prepaidOrder("pret2", 30);
        acceptPaid(g);
        shipWith(g, "KERRY", tracking("KE")).andExpect(status().isOk());
        call(post(g.bo() + "/refund-request"), g.buyer(), "{\"reason\":\"ช้า\"}").andExpect(status().isOk());
        mark(g, "in-transit", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.orderToStatus").value("REFUND_REQUESTED"));
        mark(g, "delivered", "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void visibilityPermissionsCrossShopBuyerAndAdmin() throws Exception {
        var f = codOrder("vis", 31);
        // not accepted: nothing about the shipment
        call(get(sp(f)), f.shop().token()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        shipWith(f, "KERRY", tracking("KE")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        call(put(sp(f) + "/tracking"), f.shop().token(), "{\"carrierCode\":\"KERRY\",\"trackingNumber\":\"%s\"}".formatted(tracking("KE")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        mark(f, "events", "{\"note\":\"x\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        assertEquals(0, count("SELECT COUNT(*) FROM shipments"));
        accept(f);
        var number = tracking("KE");
        shipWith(f, "KERRY", number).andExpect(status().isOk());
        // permissions inside the shop
        var viewer = token("s-vis-viewer@example.com", "0817555531");
        addMember(f.shop().shopId(), userId(viewer), "STORE_VIEWER");
        call(get(sp(f)), viewer).andExpect(status().isOk());
        call(get(base(f.shop()) + "/shipments"), viewer).andExpect(status().isOk());
        call(post(sp(f) + "/in-transit"), viewer, "{}").andExpect(status().isForbidden());
        call(put(sp(f) + "/tracking"), viewer, "{}").andExpect(status().isForbidden());
        var warehouse = token("s-vis-wh@example.com", "0817555532");
        addMember(f.shop().shopId(), userId(warehouse), "STORE_WAREHOUSE");
        call(post(sp(f) + "/in-transit"), warehouse, "{}").andExpect(status().isOk());
        call(get(sp(f)), null).andExpect(status().isUnauthorized());
        // other shop: own path → 404 (order of another shop), foreign path → not a member (404, shop not revealed)
        var other = setup("vis2", 32);
        call(get(base(other.shop()) + "/orders/" + f.orderId() + "/shipment"), other.shop().token()).andExpect(status().isNotFound());
        call(post(base(other.shop()) + "/orders/" + f.orderId() + "/shipment/delivered"), other.shop().token(), "{}").andExpect(status().isNotFound());
        call(get(sp(f)), other.shop().token()).andExpect(status().isNotFound());
        call(get(base(other.shop()) + "/shipments"), other.shop().token()).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        // buyer: own order only
        call(get(f.bo() + "/shipment"), f.buyer()).andExpect(status().isOk()).andExpect(jsonPath("$.trackingNumber").value(number));
        call(get(f.bo() + "/shipment"), other.buyer()).andExpect(status().isNotFound());
        call(get("/api/orders/tracking?carrierCode=kerry&trackingNumber=" + number.toLowerCase()), f.buyer()).andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(f.orderId()));
        call(get("/api/orders/tracking?carrierCode=KERRY&trackingNumber=" + number), other.buyer()).andExpect(status().isNotFound());
        call(get("/api/orders/tracking?carrierCode=KERRY"), f.buyer()).andExpect(status().isBadRequest());
        call(get("/api/orders/tracking?carrierCode=KERRY&trackingNumber=" + number), null).andExpect(status().isUnauthorized());
        call(post(f.bo() + "/shipment/delivered"), f.buyer(), "{}").andExpect(status().is4xxClientError());
        // merchant list + filters (accepted orders only)
        var pending = codOrder("vis3", 33); // never accepted, other shop
        call(get(base(f.shop()) + "/shipments?status=in_transit&carrierCode=kerry&trackingNumber=" + number.substring(2, 8)), f.shop().token())
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].orderId").value(f.orderId()));
        call(get(base(f.shop()) + "/shipments?status=DELIVERED"), f.shop().token()).andExpect(jsonPath("$.totalItems").value(0));
        call(get(base(f.shop()) + "/shipments?status=BAD"), f.shop().token()).andExpect(status().isBadRequest());
        call(get(base(f.shop()) + "/shipments?from=2026-13-01"), f.shop().token()).andExpect(status().isBadRequest());
        call(get(base(f.shop()) + "/shipments?size=0"), f.shop().token()).andExpect(status().isBadRequest());
        // admin: view anything, override only with a reason
        var admin = adminToken();
        call(get("/api/admin/orders/" + f.orderId() + "/shipment"), admin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_TRANSIT"));
        call(get("/api/admin/shipments?shopId=" + f.shop().shopId()), admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        call(get("/api/admin/shipments"), f.shop().token()).andExpect(status().isForbidden());
        call(post("/api/admin/orders/" + f.orderId() + "/shipment/status"), admin, "{\"status\":\"OUT_FOR_DELIVERY\"}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("reason")));
        call(post("/api/admin/orders/" + f.orderId() + "/shipment/status"), admin, "{\"status\":\"OUT_FOR_DELIVERY\",\"reason\":\"ขนส่งแจ้งทางโทรศัพท์\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.toStatus").value("OUT_FOR_DELIVERY"))
            .andExpect(jsonPath("$.shipment.events[-1:].actorType", hasItem("ADMIN")))
            .andExpect(jsonPath("$.shipment.events[-1:].source", hasItem("ADMIN")));
        call(put("/api/admin/orders/" + f.orderId() + "/shipment/tracking"), admin, "{\"trackingNumber\":\"%s\"}".formatted(tracking("KE")))
            .andExpect(status().isBadRequest());
        call(put("/api/admin/orders/" + f.orderId() + "/shipment/tracking"), admin, "{\"trackingNumber\":\"%s\",\"reason\":\"ร้านพิมพ์ผิด\"}".formatted(tracking("KE")))
            .andExpect(status().isOk());
        call(post("/api/admin/orders/" + f.orderId() + "/shipment/status"), admin, "{\"status\":\"DELIVERED\",\"reason\":\"ยืนยันกับขนส่ง\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.orderToStatus").value("DELIVERED"));
        call(post("/api/admin/orders/" + f.orderId() + "/shipment/status"), f.shop().token(), "{\"status\":\"RETURNED\",\"reason\":\"x\"}")
            .andExpect(status().isForbidden());
        call(get("/api/admin/orders/" + pending.orderId() + "/shipment"), admin).andExpect(status().isNotFound());
    }

    @Test
    void concurrentStatusAndTrackingChangesAreSerialized() throws Exception {
        var f = prepaidOrder("conc", 34);
        acceptPaid(f);
        shipWith(f, "KERRY", tracking("KE")).andExpect(status().isOk());
        var pool = Executors.newFixedThreadPool(4);
        var go = new CountDownLatch(1);
        var results = new ArrayList<Future<String>>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> {
                go.await();
                var r = mark(f, "delivered", "{}").andReturn().getResponse();
                return r.getStatus() + ":" + (r.getStatus() == 200 ? JsonPath.read(r.getContentAsString(), "$.changed").toString() : JsonPath.read(r.getContentAsString(), "$.code"));
            }));
        }
        go.countDown();
        var outcomes = new ArrayList<String>();
        for (var r : results) outcomes.add(r.get(30, TimeUnit.SECONDS));
        assertEquals(1, outcomes.stream().filter("200:true"::equals).count(), outcomes.toString());
        assertTrue(outcomes.stream().allMatch(o -> o.equals("200:true") || o.equals("200:false") || o.startsWith("409")), outcomes.toString());
        assertEquals(1, count("SELECT COUNT(*) FROM order_status_history WHERE to_status = 'DELIVERED' AND from_status = 'DISPATCHED'"));
        assertEquals(1, count("SELECT COUNT(*) FROM shipment_tracking_events WHERE normalized_status = 'DELIVERED'"));

        // two orders racing for the same carrier + tracking number: exactly one wins
        var a = prepaidOrder("conc2", 35);
        var b = prepaidOrder("conc3", 36);
        acceptPaid(a);
        acceptPaid(b);
        var number = tracking("FL");
        var go2 = new CountDownLatch(1);
        var race = new ArrayList<Future<Integer>>();
        for (var x : List.of(a, b)) {
            race.add(pool.submit(() -> {
                go2.await();
                return shipWith(x, "FLASH", number).andReturn().getResponse().getStatus();
            }));
        }
        go2.countDown();
        var codes = new ArrayList<Integer>();
        for (var r : race) codes.add(r.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        codes.sort(Comparator.naturalOrder());
        assertEquals(List.of(200, 409), codes);
        assertEquals(1, count("SELECT COUNT(*) FROM shipments WHERE carrier_code = 'FLASH' AND tracking_number = ?", number));
        assertEquals(1, count("SELECT COUNT(*) FROM orders WHERE status = 'DISPATCHED' AND id IN (?, ?)", UUID.fromString(a.orderId()), UUID.fromString(b.orderId())));
    }

    @Test
    void carrierWebhookSpiIsClosedByDefaultAndAppliesVerifiedUpdates() throws Exception {
        call(post("/api/shipping/webhooks/kerry"), null, "{}").andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CARRIER_WEBHOOK_NOT_CONFIGURED"));
        call(post("/api/shipping/webhooks/flash").header(FakeCarrierTrackingProvider.HEADER, "deadbeef"), null, "{\"events\":[]}")
            .andExpect(status().isUnauthorized());
        var f = prepaidOrder("hook", 37);
        acceptPaid(f);
        var number = tracking("FL");
        shipWith(f, "FLASH", number).andExpect(status().isOk());
        var body = """
            {"events":[
              {"id":"e1","tracking":"%1$s","status":"IN_TRANSIT","code":"PICKED","description":"รับพัสดุแล้ว","location":"Flash บางนา","at":"%2$s"},
              {"id":"e1","tracking":"%1$s","status":"IN_TRANSIT","code":"PICKED"},
              {"id":"e2","tracking":"%1$s","status":null,"code":"SCAN","location":"DC ลำลูกกา"},
              {"id":"e3","tracking":"%1$s","status":"DELIVERED","code":"POD","description":"ส่งสำเร็จ"},
              {"id":"e4","tracking":"UNKNOWN0001","status":"DELIVERED"}]}""".formatted(number.toLowerCase(), Instant.now().minusSeconds(60));
        call(post("/api/shipping/webhooks/FLASH").header(FakeCarrierTrackingProvider.HEADER, FakeCarrierTrackingProvider.sign(body)), null, body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.received").value(5)).andExpect(jsonPath("$.applied").value(2))
            .andExpect(jsonPath("$.duplicates").value(1)).andExpect(jsonPath("$.recorded").value(1)).andExpect(jsonPath("$.unmatched").value(1));
        call(get(f.mo()), f.shop().token()).andExpect(jsonPath("$.status").value("DELIVERED"));
        var json = shipmentJson(f);
        List<String> sources = JsonPath.read(json, "$.events[?(@.source == 'CARRIER_WEBHOOK')].eventType");
        assertEquals(List.of("STATUS_CHANGED", "CARRIER_UPDATE", "STATUS_CHANGED"), sources);
        List<String> actors = JsonPath.read(json, "$.events[?(@.source == 'CARRIER_WEBHOOK')].actorType");
        assertTrue(actors.stream().allMatch("SYSTEM"::equals));
        // replay: all duplicates, nothing changes
        call(post("/api/shipping/webhooks/flash").header(FakeCarrierTrackingProvider.HEADER, FakeCarrierTrackingProvider.sign(body)), null, body)
            .andExpect(status().isOk()).andExpect(jsonPath("$.applied").value(0)).andExpect(jsonPath("$.duplicates").value(4));
        assertEquals(1, count("SELECT COUNT(*) FROM order_status_history WHERE to_status = 'DELIVERED' AND from_status = 'DISPATCHED'"));
    }

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
            var approver = users.save(UserAccount.systemAccount("shp-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"shp-approver@example.com","password":"Password1"}
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
