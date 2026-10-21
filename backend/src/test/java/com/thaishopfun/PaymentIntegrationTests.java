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


/** Payments (slip / confirm / reject / webhook), refund workflow, merchant visibility. Fixtures copied from OrderIntegrationTests. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIntegrationTests {
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


    // ---------------- payment fixtures ----------------

    private static final String BANK = "{\"bankName\":\"KBANK\",\"accountNumber\":\"1234567890\",\"accountName\":\"ร้านทดสอบ\"}";

    private record Fixture(OpenShop shop, String buyer, String sku, String orderId) {
        String mo() { return "/api/merchant/shops/" + shop.shopId() + "/orders/" + orderId; }
        String bo() { return "/api/orders/" + orderId; }
    }

    private int seq = 0;

    /** shop + bank account + a 1-unit order (200 + shipping 0 = 200) awaiting payment */
    private Fixture fixture(String tag, boolean account) throws Exception {
        int n = 10 + (int) (Math.abs((long) tag.hashCode()) % 80);
        var shop = openShop("p-" + tag + "@example.com", "08160000" + n, "ร้าน" + tag, "Pay" + tag, "12345678905" + n);
        if (account) call(put(base(shop) + "/payment-accounts/BANK_TRANSFER"), shop.token(), BANK).andExpect(status().isOk());
        var sku = sku(publishedVariantProduct(shop, tag.toUpperCase().substring(0, 2) + n), tag.toUpperCase().substring(0, 2) + n + "-BLK-S");
        var buyer = token("p-" + tag + "-buyer@example.com", "08170000" + n);
        return new Fixture(shop, buyer, sku, newOrder(buyer, sku));
    }

    private String newOrder(String buyer, String sku) throws Exception {
        add(buyer, sku, 1);
        var out = checkout(buyer, "pay-key-" + (++seq) + "-" + UUID.randomUUID()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(out, "$.orders[0].id");
    }

    private ResultActions slip(String buyer, String orderId, String amount, String reference) throws Exception {
        return slip(buyer, orderId, amount, reference, new MockMultipartFile("file", "slip.png", "image/png", PNG), Instant.now().minusSeconds(60).toString());
    }

    private ResultActions slip(String buyer, String orderId, String amount, String reference, MockMultipartFile file, String at) throws Exception {
        var req = multipart("/api/orders/" + orderId + "/payments");
        if (file != null) req.file(file);
        if (amount != null) req.param("amount", amount);
        if (at != null) req.param("transferredAt", at);
        if (reference != null) req.param("reference", reference);
        req.param("bankName", "SCB").param("provider", "BANK_TRANSFER");
        return mvc.perform(req.header("Authorization", "Bearer " + buyer));
    }

    /** The shop accepts the order (idempotent); required before it may see or review payments. */
    private void accept(Fixture f) throws Exception {
        call(post(f.mo() + "/accept"), f.shop().token(), "{}").andExpect(status().isOk());
    }

    private String pendingPaymentId(Fixture f) throws Exception {
        accept(f);
        var r = call(get(f.mo() + "/payments"), f.shop().token()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(r, "$..[?(@.status == 'PENDING')].id");
        return ids.get(0);
    }

    private String grand(Fixture f) throws Exception {
        return JsonPath.read(call(get(f.bo()), f.buyer()).andReturn().getResponse().getContentAsString(), "$.grandTotal").toString();
    }

    private String openRefund(Fixture f) throws Exception {
        var r = call(get(f.mo() + "/refunds"), f.shop().token()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> open = JsonPath.read(r, "$.refunds[?(@.status in ['REQUESTED','APPROVED'])].id");
        return open.get(0);
    }

    private ResultActions refundProof(Fixture f, String refundId) throws Exception {
        return mvc.perform(multipart(f.mo() + "/refunds/" + refundId + "/proof")
            .file(new MockMultipartFile("file", "refund.png", "image/png", PNG)).param("reference", "RFP" + refundId.substring(0, 8))
            .header("Authorization", "Bearer " + f.shop().token()));
    }

    private void paid(Fixture f, String ref) throws Exception {
        slip(f.buyer(), f.orderId(), grand(f), ref).andExpect(status().isCreated());
        call(post(f.mo() + "/payments/" + pendingPaymentId(f) + "/confirm"), f.shop().token(), "{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("MERCHANT_CONFIRMED")); // accepted before payment
    }

    // ---------------- tests ----------------

    @Test
    void paymentInfoShowsShopAccountOnlyToOwnerWhilePending() throws Exception {
        var f = fixture("info", false);
        call(get(f.bo() + "/payment-info"), f.buyer()).andExpect(status().isOk()).andExpect(jsonPath("$.accounts.length()").value(0));
        slip(f.buyer(), f.orderId(), grand(f), "REF0001").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAYMENT_ACCOUNT_NOT_CONFIGURED"));
        call(put(base(f.shop()) + "/payment-accounts/BANK_TRANSFER"), f.shop().token(), "{\"bankName\":\"KBANK\"}").andExpect(status().isBadRequest());
        call(put(base(f.shop()) + "/payment-accounts/BANK_TRANSFER"), f.shop().token(), BANK).andExpect(status().isOk());
        call(get(base(f.shop()) + "/payment-accounts"), f.shop().token()).andExpect(jsonPath("$[0].accountNumber").value("1234567890"));
        var viewer = token("p-info-viewer@example.com", "0817111111");
        addMember(f.shop().shopId(), userId(viewer), "STORE_VIEWER");
        call(get(base(f.shop()) + "/payment-accounts"), viewer).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].masked").value(true)).andExpect(jsonPath("$[0].accountNumber").value(not("1234567890")));
        call(put(base(f.shop()) + "/payment-accounts/BANK_TRANSFER"), viewer, BANK).andExpect(status().isForbidden());
        call(get(f.bo() + "/payment-info"), f.buyer()).andExpect(status().isOk())
            .andExpect(jsonPath("$.canSubmitSlip").value(true))
            .andExpect(jsonPath("$.accounts[0].accountNumber").value("1234567890"));
        var other = token("p-info-other@example.com", "0817111112");
        call(get(f.bo() + "/payment-info"), other).andExpect(status().isNotFound());
        paid(f, "REF0002");
        call(get(f.bo() + "/payment-info"), f.buyer()).andExpect(jsonPath("$.accounts.length()").value(0))
            .andExpect(jsonPath("$.canSubmitSlip").value(false));
    }

    @Test
    void slipValidationDuplicateReferenceAndResubmit() throws Exception {
        var f = fixture("valid", true);
        var amount = grand(f);
        slip(f.buyer(), f.orderId(), amount, "REF1000", null, Instant.now().toString()).andExpect(status().isBadRequest());
        slip(f.buyer(), f.orderId(), amount, "REF1000", new MockMultipartFile("file", "x.txt", "text/plain", "hello".getBytes()), Instant.now().toString())
            .andExpect(status().is4xxClientError());
        var big = new byte[6 * 1024 * 1024];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        slip(f.buyer(), f.orderId(), amount, "REF1000", new MockMultipartFile("file", "big.png", "image/png", big), Instant.now().toString())
            .andExpect(status().isPayloadTooLarge());
        slip(f.buyer(), f.orderId(), "-5", "REF1000").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("amount")));
        slip(f.buyer(), f.orderId(), "abc", "REF1000").andExpect(status().isBadRequest());
        slip(f.buyer(), f.orderId(), amount, "REF1000", new MockMultipartFile("file", "s.png", "image/png", PNG), Instant.now().plus(Duration.ofHours(2)).toString())
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("transferredAt")));
        slip(f.buyer(), f.orderId(), amount, "bad ref !!").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[*].field", hasItem("reference")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM payment_proofs", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM media_files", Integer.class));

        slip(f.buyer(), f.orderId(), amount, "REF1000").andExpect(status().isCreated());
        call(get(f.bo()), f.buyer()).andExpect(jsonPath("$.paymentStatus").value("PENDING"));
        // another buyer / shop cannot
        var other = token("p-valid-other@example.com", "0817222222");
        slip(other, f.orderId(), amount, "REF1001").andExpect(status().isNotFound());
        // duplicate reference in the same shop (other order), case-insensitive
        var second = newOrder(f.buyer(), f.sku());
        slip(f.buyer(), second, amount, "ref1000").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_REFERENCE_DUPLICATE"));
        // resubmit while pending supersedes the previous slip
        slip(f.buyer(), f.orderId(), amount, "REF1002").andExpect(status().isCreated());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payment_proofs WHERE status = 'PENDING_REVIEW' AND order_id = ?", Integer.class, UUID.fromString(f.orderId())));
        // the superseded reference is free again
        slip(f.buyer(), second, amount, "REF1000").andExpect(status().isCreated());
        // file served to owner only, with safe headers
        var r = call(get(f.bo() + "/payments"), f.buyer()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> proofs = JsonPath.read(r, "$..proofs[?(@.status == 'PENDING_REVIEW')].id");
        call(get(f.bo() + "/payments/proofs/" + proofs.get(0) + "/file"), f.buyer()).andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        call(get(f.bo() + "/payments/proofs/" + proofs.get(0) + "/file"), other).andExpect(status().isNotFound());
        // buyer cannot cancel while slip is under review
        call(post(f.bo() + "/cancel"), f.buyer(), "{\"reason\":\"x\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAYMENT_UNDER_REVIEW"));
    }

    @Test
    void confirmRejectAmountMismatchHistoryAndVisibility() throws Exception {
        var f = fixture("conf", true);
        var amount = new BigDecimal(grand(f));
        // before accepting, the shop cannot see or act on payments
        slip(f.buyer(), f.orderId(), amount.subtract(BigDecimal.TEN).toPlainString(), "REF2000").andExpect(status().isCreated());
        call(get(f.mo()), f.shop().token()).andExpect(jsonPath("$.itemsHidden").value(true))
            .andExpect(jsonPath("$.items").doesNotExist()).andExpect(jsonPath("$.grandTotal").doesNotExist());
        call(get(f.mo() + "/payments"), f.shop().token()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        var hiddenPid = jdbc.queryForObject("SELECT id FROM payments WHERE status = 'PENDING'", UUID.class);
        call(post(f.mo() + "/payments/" + hiddenPid + "/confirm"), f.shop().token(), "{}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        call(post(f.mo() + "/payments/" + hiddenPid + "/reject"), f.shop().token(), "{\"reason\":\"x\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        var pid = pendingPaymentId(f);
        call(get(f.mo()), f.shop().token()).andExpect(jsonPath("$.itemsHidden").value(false)).andExpect(jsonPath("$.grandTotal").exists());
        call(post(f.mo() + "/payments/" + pid + "/confirm"), f.shop().token(), "{}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
        call(post(f.mo() + "/payments/" + pid + "/reject"), f.shop().token(), "{}").andExpect(status().isBadRequest());
        call(post(f.mo() + "/payments/" + pid + "/reject"), f.shop().token(), "{\"reason\":\"ยอดไม่ตรง\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.order.paymentStatus").value("FAILED"));
        call(post(f.mo() + "/payments/" + pid + "/confirm"), f.shop().token(), "{}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAYMENT_NOT_PENDING"));
        // resubmit with a short amount again; shop accepts the mismatch with a reason
        slip(f.buyer(), f.orderId(), amount.subtract(BigDecimal.ONE).toPlainString(), "REF2001").andExpect(status().isCreated());
        var pid2 = pendingPaymentId(f);
        assertNotEquals(pid, pid2);
        call(post(f.mo() + "/payments/" + pid2 + "/confirm"), f.shop().token(), "{\"acceptAmountMismatch\":true}").andExpect(status().isBadRequest());
        call(post(f.mo() + "/payments/" + pid2 + "/confirm"), f.shop().token(), "{\"acceptAmountMismatch\":true,\"reason\":\"ลูกค้าโอนขาด 1 บาท ยอมรับ\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(true))
            .andExpect(jsonPath("$.order.status").value("MERCHANT_CONFIRMED"))
            .andExpect(jsonPath("$.order.paymentStatus").value("PAID"));
        call(post(f.mo() + "/payments/" + pid2 + "/confirm"), f.shop().token(), "{}").andExpect(jsonPath("$.changed").value(false));
        call(post(f.mo() + "/payments/" + pid2 + "/reject"), f.shop().token(), "{\"reason\":\"x\"}").andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE status = 'CAPTURED'", Integer.class));
        assertEquals(true, jdbc.queryForObject("SELECT amount_mismatch FROM payment_proofs WHERE status = 'VERIFIED'", Boolean.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id = ?", Integer.class, UUID.fromString(f.orderId())));
        // history: who / actor type / reason / from-to
        var hist = jdbc.queryForList("SELECT from_status, to_status, actor_type, reason FROM payment_status_history ORDER BY id");
        assertTrue(hist.stream().anyMatch(h -> "FAILED".equals(h.get("to_status")) && "MERCHANT".equals(h.get("actor_type")) && "ยอดไม่ตรง".equals(h.get("reason"))), hist.toString());
        assertTrue(hist.stream().anyMatch(h -> "CAPTURED".equals(h.get("to_status"))), hist.toString());
        var tl = call(get(f.mo() + "/timeline"), f.shop().token()).andReturn().getResponse().getContentAsString();
        assertTrue(tl.contains("PAYMENT_REJECTED") && tl.contains("PAYMENT_SLIP_SUBMITTED"), tl);
        // already confirmed through acceptance: confirm is a no-op, items revealed once and audited
        call(post(f.mo() + "/confirm"), f.shop().token(), "{}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        call(get(f.mo()), f.shop().token()).andExpect(jsonPath("$.itemsHidden").value(false)).andExpect(jsonPath("$.items.length()").value(1));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_item_reveal_logs", Integer.class));
        // slip at a non-pending order
        slip(f.buyer(), f.orderId(), amount.toPlainString(), "REF2009").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_AWAITING_PAYMENT"));
        // buyer always sees items
        call(get(f.bo()), f.buyer()).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void twoConcurrentConfirmsCaptureOnce() throws Exception {
        var f = fixture("race", true);
        slip(f.buyer(), f.orderId(), grand(f), "REF3000").andExpect(status().isCreated());
        var pid = pendingPaymentId(f);
        var pool = Executors.newFixedThreadPool(2);
        var go = new CountDownLatch(1);
        List<Future<String>> fs = new ArrayList<>();
        for (int i = 0; i < 2; i++) fs.add(pool.submit(() -> { go.await();
            var r = call(post(f.mo() + "/payments/" + pid + "/confirm"), f.shop().token(), "{}").andReturn().getResponse();
            return r.getStatus() + ":" + r.getContentAsString(); }));
        go.countDown();
        var out = new ArrayList<String>();
        for (var x : fs) out.add(x.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, out.stream().filter(o -> o.startsWith("200") && o.contains("\"changed\":true")).count(), out.toString());
        assertTrue(out.stream().allMatch(o -> o.startsWith("200") || o.startsWith("409")), out.toString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE status = 'CAPTURED'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = ? AND to_status = 'PAID_AWAITING_MERCHANT_CONFIRM'",
            Integer.class, UUID.fromString(f.orderId())));
    }

    @Test
    void partialThenFullRefundWithLimitsProofAndBuyerAck() throws Exception {
        var f = fixture("refund", true);
        paid(f, "REF4000");
        var total = new BigDecimal(grand(f));
        call(post(f.bo() + "/refund-request"), f.buyer(), "{\"reason\":\"เกิน\",\"amount\":%s}".formatted(total.add(BigDecimal.ONE))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].code").value("EXCEEDS_REFUNDABLE"));
        call(post(f.bo() + "/refund-request"), f.buyer(), "{\"reason\":\"ศูนย์\",\"amount\":0}").andExpect(status().isBadRequest());
        // partial 50
        call(post(f.bo() + "/refund-request"), f.buyer(), "{\"reason\":\"คืนบางส่วน\",\"amount\":50}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("REFUND_REQUESTED"));
        var r1 = openRefund(f);
        refundProof(f, r1).andExpect(status().isConflict()); // must approve first
        call(post(f.mo() + "/refunds/" + r1 + "/approve"), f.shop().token(), "{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.refund.status").value("APPROVED"));
        refundProof(f, r1).andExpect(status().isOk())
            .andExpect(jsonPath("$.refund.status").value("PROOF_UPLOADED"))
            .andExpect(jsonPath("$.order.status").value("MERCHANT_CONFIRMED"))
            .andExpect(jsonPath("$.order.paymentStatus").value("PARTIALLY_REFUNDED"));
        call(get(f.bo() + "/refunds"), f.buyer()).andExpect(jsonPath("$.refundedAmount").value(50.0))
            .andExpect(jsonPath("$.refundableAmount").value(total.subtract(new BigDecimal(50)).doubleValue()));
        call(get(f.bo() + "/refunds/" + r1 + "/proof-file"), f.buyer()).andExpect(status().isOk());
        call(post(f.bo() + "/refunds/" + r1 + "/dispute"), f.buyer(), "{}").andExpect(status().isBadRequest());
        call(post(f.bo() + "/refunds/" + r1 + "/dispute"), f.buyer(), "{\"note\":\"ยังไม่ได้รับ\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.refund.status").value("DISPUTED"));
        refundProof(f, r1).andExpect(status().isOk()).andExpect(jsonPath("$.refund.status").value("PROOF_UPLOADED"));
        call(post(f.bo() + "/refunds/" + r1 + "/confirm-received"), f.buyer(), "{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.refund.status").value("COMPLETED"));
        // remainder: more than remaining refused, exactly remaining (default) → REFUNDED
        call(post(f.bo() + "/refund-request"), f.buyer(), "{\"reason\":\"ที่เหลือ\",\"amount\":%s}".formatted(total)).andExpect(status().isBadRequest());
        call(post(f.bo() + "/refund-request"), f.buyer(), "{\"reason\":\"ที่เหลือ\"}").andExpect(status().isOk());
        var r2 = openRefund(f);
        call(post(f.bo() + "/refund-request"), f.buyer(), "{\"reason\":\"ซ้ำ\"}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE status IN ('REQUESTED','APPROVED')", Integer.class));
        call(post(f.mo() + "/refunds/" + r2 + "/approve"), f.shop().token(), "{}").andExpect(status().isOk());
        refundProof(f, r2).andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("REFUNDED"))
            .andExpect(jsonPath("$.order.paymentStatus").value("REFUNDED"));
        assertEquals(0, total.compareTo(jdbc.queryForObject("SELECT SUM(amount) FROM refunds WHERE status IN ('PROOF_UPLOADED','COMPLETED')", BigDecimal.class)));
        call(post(f.bo() + "/refund-request"), f.buyer(), "{\"reason\":\"อีก\"}").andExpect(status().isConflict());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM refund_status_history", Integer.class) >= 8);
    }

    @Test
    void refundRejectKeepsOrderAndAdminCanResolve() throws Exception {
        var f = fixture("rrej", true);
        paid(f, "REF5000");
        call(post(f.bo() + "/refund-request"), f.buyer(), "{\"reason\":\"ไม่เอา\"}").andExpect(status().isOk());
        var r = openRefund(f);
        call(post(f.mo() + "/refunds/" + r + "/reject"), f.shop().token(), "{}").andExpect(status().isBadRequest());
        call(post(f.mo() + "/refunds/" + r + "/reject"), f.shop().token(), "{\"reason\":\"ส่งแล้ว\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.refund.status").value("REJECTED"))
            .andExpect(jsonPath("$.order.status").value("MERCHANT_CONFIRMED"))
            .andExpect(jsonPath("$.order.paymentStatus").value("PAID"));
        call(post(f.bo() + "/refund-request"), f.buyer(), "{\"reason\":\"ขออีกครั้ง\"}").andExpect(status().isOk());
        var r2 = openRefund(f);
        call(post("/api/admin/orders/" + f.orderId() + "/refunds/" + r2 + "/approve"), adminToken(), "{}").andExpect(status().isBadRequest());
        call(post("/api/admin/orders/" + f.orderId() + "/refunds/" + r2 + "/approve"), adminToken(), "{\"reason\":\"ตรวจแล้ว\"}").andExpect(status().isOk());
        refundProof(f, r2).andExpect(status().isOk());
        call(post(f.bo() + "/refunds/" + r2 + "/dispute"), f.buyer(), "{\"note\":\"ยอดไม่ครบ\"}").andExpect(status().isOk());
        call(post("/api/admin/orders/" + f.orderId() + "/refunds/" + r2 + "/resolve"), adminToken(), "{\"reason\":\"ตรวจสลิปแล้ว\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.refund.status").value("COMPLETED"));
        // buyer / shop cannot use admin API
        call(get("/api/admin/orders/" + f.orderId() + "/payments"), f.shop().token()).andExpect(status().isForbidden());
    }

    @Test
    void crossShopBuyerIsolationAndPermissions() throws Exception {
        var f = fixture("isoa", true);
        var shopB = openShop("p-isob@example.com", "0816999999", "ร้านบี", "PayIsoB", "1234567890599");
        slip(f.buyer(), f.orderId(), grand(f), "REF6000").andExpect(status().isCreated());
        var pid = pendingPaymentId(f);
        var foreign = base(shopB) + "/orders/" + f.orderId();
        call(get(foreign + "/payments"), shopB.token()).andExpect(status().isNotFound());
        call(post(foreign + "/payments/" + pid + "/confirm"), shopB.token(), "{}").andExpect(status().isNotFound());
        call(get(f.mo() + "/payments"), shopB.token()).andExpect(status().isNotFound());
        var other = token("p-iso-other@example.com", "0817333333");
        call(get(f.bo() + "/payments"), other).andExpect(status().isNotFound());
        call(get(f.bo() + "/refunds"), other).andExpect(status().isNotFound());
        call(post(f.mo() + "/payments/" + pid + "/confirm"), f.buyer(), "{}").andExpect(status().isNotFound());
        var viewer = token("p-iso-viewer@example.com", "0817444444");
        addMember(f.shop().shopId(), userId(viewer), "STORE_VIEWER");
        call(get(f.mo() + "/payments"), viewer).andExpect(status().isOk());
        call(post(f.mo() + "/payments/" + pid + "/confirm"), viewer, "{}").andExpect(status().isForbidden());
        call(post(f.mo() + "/payments/" + pid + "/reject"), viewer, "{\"reason\":\"x\"}").andExpect(status().isForbidden());
        call(get(f.mo() + "/payments"), null).andExpect(status().isUnauthorized());
        // admin may confirm but must give a reason
        call(post("/api/admin/orders/" + f.orderId() + "/payments/" + pid + "/confirm"), adminToken(), "{}").andExpect(status().isBadRequest());
        call(post("/api/admin/orders/" + f.orderId() + "/payments/" + pid + "/confirm"), adminToken(), "{\"reason\":\"ร้านติดต่อไม่ได้\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.paymentStatus").value("PAID"));
        assertEquals("ADMIN", jdbc.queryForObject("SELECT actor_type FROM payment_status_history WHERE to_status = 'CAPTURED'", String.class));
    }

    @Test
    void webhookSignatureAndIdempotencyWithFakeProvider() throws Exception {
        var f = fixture("hook", false);
        var amount = grand(f);
        var body = "{\"id\":\"evt_1\",\"type\":\"payment.captured\",\"orderId\":\"%s\",\"paymentId\":\"pay_1\",\"amount\":%s}".formatted(f.orderId(), amount);
        mvc.perform(post("/api/payments/webhooks/OMISE").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/webhooks/BANK_TRANSFER").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/webhooks/CUSTOM").contentType(MediaType.APPLICATION_JSON).content(body)
            .header(FakePaymentGateway.HEADER, "deadbeef")).andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_events", Integer.class));
        hook(body).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false)).andExpect(jsonPath("$.status").value("PROCESSED"));
        hook(body).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
        // a second event id for the same provider payment: no double capture
        hook(body.replace("evt_1", "evt_2")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSED"));
        call(get(f.bo()), f.buyer()).andExpect(jsonPath("$.status").value("PAID_AWAITING_MERCHANT_CONFIRM")).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE status = 'CAPTURED'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE to_status = 'PAID_AWAITING_MERCHANT_CONFIRM'", Integer.class));
        // amount mismatch on another order → FAILED, order untouched
        var o2 = newOrder(f.buyer(), f.sku());
        hook("{\"id\":\"evt_3\",\"type\":\"payment.captured\",\"orderId\":\"%s\",\"paymentId\":\"pay_3\",\"amount\":1}".formatted(o2))
            .andExpect(jsonPath("$.status").value("FAILED"));
        call(get("/api/orders/" + o2), f.buyer()).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        // unknown type ignored
        hook("{\"id\":\"evt_4\",\"type\":\"payment.pending\",\"orderId\":\"%s\",\"paymentId\":\"pay_4\",\"amount\":1}".formatted(o2))
            .andExpect(jsonPath("$.status").value("IGNORED"));
        // same provider payment id on another order is rejected
        hook("{\"id\":\"evt_5\",\"type\":\"payment.captured\",\"orderId\":\"%s\",\"paymentId\":\"pay_1\",\"amount\":%s}".formatted(o2, amount))
            .andExpect(jsonPath("$.status").value("FAILED"));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_events", Integer.class));
    }

    // ---------------- merchant visibility before / after accept ----------------

    @Test
    void merchantSeesOnlyNumberAndDateUntilAcceptOnEveryEndpoint() throws Exception {
        var f = fixture("vis", true);
        slip(f.buyer(), f.orderId(), grand(f), "REF8000").andExpect(status().isCreated());
        var proofId = jdbc.queryForObject("SELECT id FROM payment_proofs", UUID.class).toString();
        var paymentId = jdbc.queryForObject("SELECT id FROM payments", UUID.class).toString();
        var number = jdbc.queryForObject("SELECT order_number FROM orders", String.class);
        var token = f.shop().token();
        // detail: id / number / created time / version / awaiting only
        var detail = call(get(f.mo()), token).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(f.orderId())).andExpect(jsonPath("$.orderNumber").value(number))
            .andExpect(jsonPath("$.placedAt").exists()).andExpect(jsonPath("$.awaitingAcceptance").value(true))
            .andReturn().getResponse().getContentAsString();
        for (var hidden : List.of("status", "paymentStatus", "fulfillmentStatus", "subtotal", "grandTotal", "shippingFee", "discountTotal",
                "items", "itemCount", "totalQuantity", "shippingAddress", "shipping", "buyerNote", "buyerUserId", "shopCustomerId",
                "paidAt", "paidAmount", "refundedAmount", "refundableAmount", "couponCode", "currency")) {
            assertFalse(((Map<?, ?>) JsonPath.read(detail, "$")).containsKey(hidden), hidden + " leaked: " + detail);
        }
        assertFalse(detail.contains("สมหญิง") || detail.contains("0812345678"), detail);
        // list
        var list = call(get(base(f.shop()) + "/orders"), token).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].orderNumber").value(number)).andExpect(jsonPath("$.items[0].awaitingAcceptance").value(true))
            .andExpect(jsonPath("$.items[0].grandTotal").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.items[0].itemCount").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.items[0].status").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.items[0].paymentStatus").value(org.hamcrest.Matchers.nullValue()))
            .andReturn().getResponse().getContentAsString();
        assertFalse(list.contains("สมหญิง"), list);
        // timeline: one bare "placed" entry
        call(get(f.mo() + "/timeline"), token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].event").value("ORDER_PLACED")).andExpect(jsonPath("$[0].toStatus").doesNotExist())
            .andExpect(jsonPath("$[0].metadata").doesNotExist());
        // transitions: accept + decline only, no status
        call(get(f.mo() + "/transitions"), token).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.transitions[*].action", org.hamcrest.Matchers.containsInAnyOrder("ACCEPT", "CANCEL")));
        // money / slip / refund endpoints: 409 ORDER_NOT_ACCEPTED
        for (var path : List.of("/payments", "/payments/proofs/" + proofId + "/file", "/refunds")) {
            call(get(f.mo() + path), token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        }
        for (var path : List.of("/payments/" + paymentId + "/confirm", "/payments/" + paymentId + "/reject", "/mark-paid")) {
            call(post(f.mo() + path), token, "{\"reason\":\"x\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        }
        // filters / search / sort cannot probe hidden data
        call(get(base(f.shop()) + "/orders?q=สมหญิง"), token).andExpect(jsonPath("$.totalItems").value(0));
        call(get(base(f.shop()) + "/orders?q=0812345678"), token).andExpect(jsonPath("$.totalItems").value(0));
        call(get(base(f.shop()) + "/orders?q=" + number.toLowerCase()), token).andExpect(jsonPath("$.totalItems").value(1));
        call(get(base(f.shop()) + "/orders?status=PENDING_PAYMENT"), token).andExpect(jsonPath("$.totalItems").value(0));
        call(get(base(f.shop()) + "/orders?paymentStatus=PENDING"), token).andExpect(jsonPath("$.totalItems").value(0));
        call(get(base(f.shop()) + "/orders?acceptance=AWAITING"), token).andExpect(jsonPath("$.totalItems").value(1));
        call(get(base(f.shop()) + "/orders?acceptance=ACCEPTED"), token).andExpect(jsonPath("$.totalItems").value(0));
        call(get(base(f.shop()) + "/orders?acceptance=maybe"), token).andExpect(status().isBadRequest());
        for (var sort : List.of("grandTotal", "paidAt", "status")) {
            call(get(base(f.shop()) + "/orders?sort=" + sort + "&direction=asc"), token).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        }
        // buyer and admin are not affected
        call(get(f.bo()), f.buyer()).andExpect(jsonPath("$.grandTotal").exists()).andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.awaitingAcceptance").doesNotExist());
        call(get("/api/admin/orders/" + f.orderId()), adminToken()).andExpect(jsonPath("$.grandTotal").exists()).andExpect(jsonPath("$.items.length()").value(1));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM order_item_reveal_logs", Integer.class));

        // accept → everything visible, audited once
        call(post(f.mo() + "/accept"), token, "{\"reason\":\"รับออเดอร์\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(true)).andExpect(jsonPath("$.fromStatus").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.toStatus").value("PENDING_PAYMENT")).andExpect(jsonPath("$.order.awaitingAcceptance").value(false))
            .andExpect(jsonPath("$.order.merchantConfirmStatus").value("CONFIRMED"));
        call(post(f.mo() + "/accept"), token, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        call(get(f.mo()), token).andExpect(jsonPath("$.grandTotal").exists()).andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.itemCount").value(1)).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.paymentStatus").value("PENDING")).andExpect(jsonPath("$.shippingAddress.district").exists());
        call(get(base(f.shop()) + "/orders"), token).andExpect(jsonPath("$.items[0].grandTotal").exists())
            .andExpect(jsonPath("$.items[0].awaitingAcceptance").value(false));
        call(get(base(f.shop()) + "/orders?q=สมหญิง"), token).andExpect(jsonPath("$.totalItems").value(1));
        call(get(base(f.shop()) + "/orders?status=PENDING_PAYMENT"), token).andExpect(jsonPath("$.totalItems").value(1));
        call(get(f.mo() + "/timeline"), token).andExpect(jsonPath("$[?(@.event == 'ORDER_ACCEPTED')].actorType").value(hasItem("MERCHANT")))
            .andExpect(jsonPath("$[?(@.event == 'ORDER_ACCEPTED')].reason").value(hasItem("รับออเดอร์")));
        call(get(f.mo() + "/transitions"), token).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        for (var path : List.of("/payments", "/payments/proofs/" + proofId + "/file", "/refunds")) call(get(f.mo() + path), token).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_item_reveal_logs WHERE reveal_reason = 'MERCHANT_CONFIRM'", Integer.class));
        assertEquals(userId(token), jdbc.queryForObject("SELECT confirmed_by_user_id FROM merchant_order_confirmations WHERE action = 'CONFIRM'", UUID.class));
        assertNotNull(jdbc.queryForObject("SELECT items_revealed_at FROM orders", Timestamp.class));
        // payment confirm after acceptance goes straight to MERCHANT_CONFIRMED
        call(post(f.mo() + "/payments/" + paymentId + "/confirm"), token, "{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("MERCHANT_CONFIRMED")).andExpect(jsonPath("$.order.paymentStatus").value("PAID"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_item_reveal_logs", Integer.class));
    }

    @Test
    void acceptTransitionsMatrixAndDecline() throws Exception {
        var f = fixture("acc", true);
        var token = f.shop().token();
        var amount = grand(f);
        // PAID_AWAITING (gateway paid an un-accepted order) → accept = confirm → MERCHANT_CONFIRMED
        hook("{\"id\":\"evt_a1\",\"type\":\"payment.captured\",\"orderId\":\"%s\",\"paymentId\":\"pay_a1\",\"amount\":%s}".formatted(f.orderId(), amount))
            .andExpect(jsonPath("$.status").value("PROCESSED"));
        call(get(f.mo()), token).andExpect(jsonPath("$.awaitingAcceptance").value(true)).andExpect(jsonPath("$.status").doesNotExist());
        // un-accepted paid order: the shop cannot cancel (it would owe a refund it cannot see) – accept first
        call(post(f.mo() + "/cancel"), token, "{\"reason\":\"x\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        call(post(f.mo() + "/accept"), token, "{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.fromStatus").value("PAID_AWAITING_MERCHANT_CONFIRM")).andExpect(jsonPath("$.toStatus").value("MERCHANT_CONFIRMED"));
        call(post(f.mo() + "/accept"), token, "{}").andExpect(jsonPath("$.changed").value(false));

        // accepted before payment, then the gateway pays → SYSTEM auto-confirm
        var o2 = newOrder(f.buyer(), f.sku());
        call(post(base(f.shop()) + "/orders/" + o2 + "/accept"), token, "{}").andExpect(status().isOk());
        hook("{\"id\":\"evt_a2\",\"type\":\"payment.captured\",\"orderId\":\"%s\",\"paymentId\":\"pay_a2\",\"amount\":%s}".formatted(o2, amount))
            .andExpect(jsonPath("$.status").value("PROCESSED"));
        call(get("/api/orders/" + o2), f.buyer()).andExpect(jsonPath("$.status").value("MERCHANT_CONFIRMED"));
        assertEquals("SYSTEM", jdbc.queryForObject("SELECT changed_by_type FROM order_status_history WHERE order_id = ? AND to_status = 'MERCHANT_CONFIRMED'",
            String.class, UUID.fromString(o2)));

        // REFUND_REQUESTED on an un-accepted paid order: accept keeps the status so the shop can settle it
        var o3 = newOrder(f.buyer(), f.sku());
        hook("{\"id\":\"evt_a3\",\"type\":\"payment.captured\",\"orderId\":\"%s\",\"paymentId\":\"pay_a3\",\"amount\":%s}".formatted(o3, amount));
        call(post("/api/orders/" + o3 + "/refund-request"), f.buyer(), "{\"reason\":\"เปลี่ยนใจ\"}").andExpect(status().isOk());
        var m3 = base(f.shop()) + "/orders/" + o3;
        call(get(m3 + "/refunds"), token).andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        call(post(m3 + "/refund/approve"), token, "{}").andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED"));
        call(post(m3 + "/accept"), token, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.toStatus").value("REFUND_REQUESTED"));
        call(get(m3 + "/refunds"), token).andExpect(status().isOk()).andExpect(jsonPath("$.refunds.length()").value(1));

        // CANCELLED by the buyer before acceptance: stays hidden until accepted
        var o4 = newOrder(f.buyer(), f.sku());
        call(post("/api/orders/" + o4 + "/cancel"), f.buyer(), "{\"reason\":\"x\"}").andExpect(status().isOk());
        call(get(base(f.shop()) + "/orders/" + o4), token).andExpect(jsonPath("$.awaitingAcceptance").value(true));
        call(get(base(f.shop()) + "/orders/" + o4 + "/transitions"), token).andExpect(jsonPath("$.transitions[*].action", org.hamcrest.Matchers.contains("ACCEPT")));

        // decline (cancel) an un-accepted unpaid order: allowed, recorded as REJECT
        var o5 = newOrder(f.buyer(), f.sku());
        call(post(base(f.shop()) + "/orders/" + o5 + "/cancel"), token, "{\"reason\":\"ของหมด\"}").andExpect(status().isOk());
        assertEquals("REJECTED", jdbc.queryForObject("SELECT merchant_confirm_status FROM orders WHERE id = ?", String.class, UUID.fromString(o5)));
        assertEquals("REJECT", jdbc.queryForObject("SELECT action FROM merchant_order_confirmations WHERE order_id = ?", String.class, UUID.fromString(o5)));
        // terminal / forward statuses can't be "accepted" (all already accepted) – COMPLETED path covered by OrderIntegrationTests
        assertTrue(com.thaishopfun.order.OrderStateMachine.canAccept(OrderStatus.PENDING_PAYMENT, OrderActorType.MERCHANT));
        assertFalse(com.thaishopfun.order.OrderStateMachine.canAccept(OrderStatus.MERCHANT_CONFIRMED, OrderActorType.MERCHANT));
        assertFalse(com.thaishopfun.order.OrderStateMachine.canAccept(OrderStatus.PENDING_PAYMENT, OrderActorType.BUYER));
        assertFalse(com.thaishopfun.order.OrderStateMachine.canAccept(OrderStatus.PENDING_PAYMENT, OrderActorType.SYSTEM));
        // buyers cannot use the accept endpoint; other shops get 404
        call(post(f.mo() + "/accept"), f.buyer(), "{}").andExpect(status().is4xxClientError());
    }

    @Test
    void twoConcurrentAcceptsRecordOnce() throws Exception {
        var f = fixture("acr", true);
        var pool = Executors.newFixedThreadPool(2);
        var go = new CountDownLatch(1);
        List<Future<String>> fs = new ArrayList<>();
        for (int i = 0; i < 2; i++) fs.add(pool.submit(() -> { go.await();
            var r = call(post(f.mo() + "/accept"), f.shop().token(), "{}").andReturn().getResponse();
            return r.getStatus() + ":" + r.getContentAsString(); }));
        go.countDown();
        var out = new ArrayList<String>();
        for (var x : fs) out.add(x.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, out.stream().filter(o -> o.startsWith("200") && o.contains("\"changed\":true")).count(), out.toString());
        assertTrue(out.stream().allMatch(o -> o.startsWith("200") || o.startsWith("409")), out.toString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM merchant_order_confirmations", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_item_reveal_logs", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE metadata_json LIKE '%ORDER_ACCEPTED%'", Integer.class));
    }

    private ResultActions hook(String body) throws Exception {
        return mvc.perform(post("/api/payments/webhooks/CUSTOM").contentType(MediaType.APPLICATION_JSON).content(body)
            .header(FakePaymentGateway.HEADER, FakePaymentGateway.sign(body)));
    }

    @Test
    void paymentStatusMatrixAcrossOrderStates() throws Exception {
        var f = fixture("matrix", true);
        // UNPAID → PENDING (slip) → FAILED (reject) → PENDING (resubmit) → PAID (confirm)
        call(get(f.bo()), f.buyer()).andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        slip(f.buyer(), f.orderId(), grand(f), "REF7000").andExpect(status().isCreated());
        call(get(f.bo()), f.buyer()).andExpect(jsonPath("$.paymentStatus").value("PENDING"));
        call(post(f.mo() + "/payments/" + pendingPaymentId(f) + "/reject"), f.shop().token(), "{\"reason\":\"สลิปไม่ชัด\"}").andExpect(status().isOk());
        call(get(f.bo()), f.buyer()).andExpect(jsonPath("$.paymentStatus").value("FAILED"));
        // a rejected order can still expire / be cancelled by the buyer
        var o2 = newOrder(f.buyer(), f.sku());
        slip(f.buyer(), o2, grand(f), "REF7001").andExpect(status().isCreated());
        call(post("/api/orders/" + o2 + "/cancel"), f.buyer(), "{\"reason\":\"x\"}").andExpect(status().isConflict());
        call(post("/api/admin/orders/" + o2 + "/payments/" + JsonPath.<List<String>>read(call(get("/api/admin/orders/" + o2 + "/payments"),
            adminToken()).andReturn().getResponse().getContentAsString(), "$..[?(@.status == 'PENDING')].id").get(0) + "/reject"), adminToken(), "{\"reason\":\"ปลอม\"}")
            .andExpect(status().isOk());
        call(post("/api/orders/" + o2 + "/cancel"), f.buyer(), "{\"reason\":\"x\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.paymentStatus").value("UNPAID"));
        slip(f.buyer(), o2, grand(f), "REF7002").andExpect(status().isConflict());
        // back to the first order
        slip(f.buyer(), f.orderId(), grand(f), "REF7003").andExpect(status().isCreated());
        call(get(f.bo()), f.buyer()).andExpect(jsonPath("$.paymentStatus").value("PENDING"));
        call(post(f.mo() + "/payments/" + pendingPaymentId(f) + "/confirm"), f.shop().token(), "{\"expectedVersion\":9999}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_VERSION_CONFLICT"));
        call(post(f.mo() + "/payments/" + pendingPaymentId(f) + "/confirm"), f.shop().token(), "{}").andExpect(status().isOk());
        call(get(f.bo()), f.buyer()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        // PAID → cancel by shop → refund APPROVED (cannot reject), still PAID → proof → REFUNDED
        call(post(f.mo() + "/cancel"), f.shop().token(), "{\"reason\":\"ของหมด\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.order.paymentStatus").value("PAID"));
        var r = openRefund(f);
        call(post(f.mo() + "/refunds/" + r + "/reject"), f.shop().token(), "{\"reason\":\"no\"}").andExpect(status().isConflict());
        refundProof(f, r).andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("CANCELLED"))
            .andExpect(jsonPath("$.order.paymentStatus").value("REFUNDED"));
        var payStatuses = jdbc.queryForList("SELECT DISTINCT status FROM payments", String.class);
        assertTrue(payStatuses.containsAll(List.of("FAILED", "REFUNDED")), payStatuses.toString());
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
            var approver = users.save(UserAccount.systemAccount("pay-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"pay-approver@example.com","password":"Password1"}
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
