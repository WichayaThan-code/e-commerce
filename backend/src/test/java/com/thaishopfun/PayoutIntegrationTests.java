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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Shop wallet + withdrawals. Credits are PLATFORM_HOLDING only — never from order GMV.
 * Uses H2 test profile; does not touch the developer's local DB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayoutIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final String BANK = "{\"bankName\":\"KBANK\",\"accountNumber\":\"1234567890\",\"accountName\":\"ร้านทดสอบ\"}";

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
        wipe();
    }

    @AfterEach
    void after() {
        wipe();
    }

    private void wipe() {
        jdbc.update("DELETE FROM shop_withdrawal_events");
        jdbc.update("DELETE FROM shop_withdrawal_requests");
        jdbc.update("DELETE FROM shop_wallet_ledger");
        jdbc.update("DELETE FROM shop_wallets");
        jdbc.update("DELETE FROM shop_payment_gateways");
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
    void walletStartsEmptyAndPaidOrdersDoNotCredit() throws Exception {
        var shop = openShop("po-empty@example.com", "0815600001", "ร้านว่าง", "Empty", "1234567890901");
        perform(get(base(shop) + "/finance/wallet"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(0.00))
            .andExpect(jsonPath("$.reserved").value(0.00))
            .andExpect(jsonPath("$.canRequestWithdrawal").value(false))
            .andExpect(jsonPath("$.holdingPolicy", containsString("PLATFORM_HELD_ONLY")));

        // Insert a PAID order directly — must not become withdrawable balance.
        var shopUuid = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        jdbc.update("""
            INSERT INTO orders (id, shop_id, order_number, channel, status, payment_status, fulfillment_status, currency,
              grand_total, placed_at, merchant_confirm_status, created_date_time, modified_date_time)
            VALUES (?, ?, 'PO-1', 'STOREFRONT', 'PACKED', 'PAID', 'UNFULFILLED', 'THB', 999.00, NOW(), 'AWAITING', NOW(), NOW())
            """, UUID.randomUUID(), shopUuid);
        perform(get(base(shop) + "/finance/wallet"), shop.token)
            .andExpect(jsonPath("$.available").value(0.00));
    }

    @Test
    void creditReserveWithdrawApprovePayAndExport() throws Exception {
        var shop = openShop("po-flow@example.com", "0815600002", "ร้านไหล", "Flow", "1234567890902");
        call(put(base(shop) + "/payment-accounts/BANK_TRANSFER"), shop.token, BANK).andExpect(status().isOk());

        perform(post("/api/admin/payouts/shops/" + shop.shopId + "/credits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":100.00,"currency":"THB","idempotencyKey":"credit-1","note":"test holding","sourceReference":"MANUAL-TEST"}
                    """), adminToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(100.00));

        // Idempotent replay
        perform(post("/api/admin/payouts/shops/" + shop.shopId + "/credits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":100.00,"currency":"THB","idempotencyKey":"credit-1","note":"test holding","sourceReference":"MANUAL-TEST"}
                    """), adminToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(100.00));

        // Same key, different payload → reject
        perform(post("/api/admin/payouts/shops/" + shop.shopId + "/credits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":50.00,"currency":"THB","idempotencyKey":"credit-1","note":"diff","sourceReference":"X"}
                    """), adminToken())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_PAYLOAD_MISMATCH"));

        var created = perform(post(base(shop) + "/finance/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":80.00,"currency":"THB","accountProvider":"BANK_TRANSFER","idempotencyKey":"wd-1"}
                    """), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REQUESTED"))
            .andExpect(jsonPath("$.accountNumberMasked", containsString("7890")))
            .andReturn();
        String wdId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        perform(get(base(shop) + "/finance/wallet"), shop.token)
            .andExpect(jsonPath("$.available").value(20.00))
            .andExpect(jsonPath("$.reserved").value(80.00));

        perform(post("/api/admin/payouts/" + wdId + "/approve"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
        perform(post("/api/admin/payouts/" + wdId + "/processing"), adminToken()).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSING"));
        perform(post("/api/admin/payouts/" + wdId + "/paid").contentType(MediaType.APPLICATION_JSON).content("""
            {"transferReference":"TX-001","transferAmount":80.00,"transferDate":"2026-11-08","externalTransferKey":"BANK-TX-001","note":"wired"}
            """), adminToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"));

        // Reuse bank transfer key on another request → reject
        perform(post("/api/admin/payouts/shops/" + shop.shopId + "/credits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":50.00,"currency":"THB","idempotencyKey":"credit-2","note":"more","sourceReference":"M2"}
                    """), adminToken()).andExpect(status().isOk());
        var second = perform(post(base(shop) + "/finance/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":40.00,"currency":"THB","accountProvider":"BANK_TRANSFER","idempotencyKey":"wd-2"}
                    """), shop.token).andExpect(status().isOk()).andReturn();
        String wd2 = JsonPath.read(second.getResponse().getContentAsString(), "$.id");
        perform(post("/api/admin/payouts/" + wd2 + "/approve"), adminToken()).andExpect(status().isOk());
        perform(post("/api/admin/payouts/" + wd2 + "/paid").contentType(MediaType.APPLICATION_JSON).content("""
            {"transferReference":"TX-002","transferAmount":40.00,"transferDate":"2026-11-08","externalTransferKey":"BANK-TX-001"}
            """), adminToken())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("TRANSFER_REFERENCE_REUSED"));

        perform(get(base(shop) + "/finance/withdrawals/export.csv"), shop.token)
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("withdrawals.csv")))
            .andExpect(content().string(containsString("PAID")))
            .andExpect(content().string(not(containsString("1234567890")))); // full account never exported
    }

    @Test
    void concurrentWithdrawalsCannotOverdraw() throws Exception {
        var shop = openShop("po-race@example.com", "0815600003", "ร้านแข่ง", "Race", "1234567890903");
        call(put(base(shop) + "/payment-accounts/BANK_TRANSFER"), shop.token, BANK).andExpect(status().isOk());
        perform(post("/api/admin/payouts/shops/" + shop.shopId + "/credits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":100.00,"currency":"THB","idempotencyKey":"race-credit","note":"race","sourceReference":"R"}
                    """), adminToken()).andExpect(status().isOk());

        var pool = Executors.newFixedThreadPool(2);
        var latch = new CountDownLatch(2);
        var ok = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    var status = mvc.perform(post(base(shop) + "/finance/withdrawals")
                            .header("Authorization", "Bearer " + shop.token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"amount":80.00,"currency":"THB","accountProvider":"BANK_TRANSFER","idempotencyKey":"race-%d"}
                                """.formatted(n)))
                        .andReturn().getResponse().getStatus();
                    if (status == 200) ok.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
        }
        pool.shutdown();
        while (!pool.isTerminated()) Thread.sleep(20);
        assertEquals(1, ok.get());
        perform(get(base(shop) + "/finance/wallet"), shop.token)
            .andExpect(jsonPath("$.available", anyOf(is(20.0), is(20.00))))
            .andExpect(jsonPath("$.reserved", anyOf(is(80.0), is(80.00))));
    }

    @Test
    void cancelReleasesAndCrossShopDenied() throws Exception {
        var shopA = openShop("po-a@example.com", "0815600004", "ร้านเอ", "A", "1234567890904");
        var shopB = openShop("po-b@example.com", "0815600005", "ร้านบี", "B", "1234567890905");
        call(put(base(shopA) + "/payment-accounts/BANK_TRANSFER"), shopA.token, BANK).andExpect(status().isOk());
        perform(post("/api/admin/payouts/shops/" + shopA.shopId + "/credits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":60.00,"currency":"THB","idempotencyKey":"c-a","note":"a","sourceReference":"A"}
                    """), adminToken()).andExpect(status().isOk());
        var created = perform(post(base(shopA) + "/finance/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":30.00,"currency":"THB","accountProvider":"BANK_TRANSFER","idempotencyKey":"wd-a"}
                    """), shopA.token).andExpect(status().isOk()).andReturn();
        String wdId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        perform(post(base(shopA) + "/finance/withdrawals/" + wdId + "/cancel"), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        perform(get(base(shopA) + "/finance/wallet"), shopA.token)
            .andExpect(jsonPath("$.available").value(60.00))
            .andExpect(jsonPath("$.reserved").value(0.00));

        perform(get(base(shopB) + "/finance/withdrawals/" + wdId), shopB.token).andExpect(status().isNotFound());
        perform(get("/api/admin/payouts"), shopA.token).andExpect(status().isForbidden());
    }

    @Test
    void insufficientBalanceAndNeedsReviewKeepsReserve() throws Exception {
        var shop = openShop("po-hold@example.com", "0815600006", "ร้านโฮลด์", "Hold", "1234567890906");
        call(put(base(shop) + "/payment-accounts/BANK_TRANSFER"), shop.token, BANK).andExpect(status().isOk());
        perform(post(base(shop) + "/finance/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":10.00,"currency":"THB","accountProvider":"BANK_TRANSFER","idempotencyKey":"no-bal"}
                    """), shop.token)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_AVAILABLE_BALANCE"));

        perform(post("/api/admin/payouts/shops/" + shop.shopId + "/credits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":25.00,"currency":"THB","idempotencyKey":"hold-c","note":"h","sourceReference":"H"}
                    """), adminToken()).andExpect(status().isOk());
        var created = perform(post(base(shop) + "/finance/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":25.00,"currency":"THB","accountProvider":"BANK_TRANSFER","idempotencyKey":"hold-w"}
                    """), shop.token).andExpect(status().isOk()).andReturn();
        String wdId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        perform(post("/api/admin/payouts/" + wdId + "/approve"), adminToken()).andExpect(status().isOk());
        perform(post("/api/admin/payouts/" + wdId + "/needs-review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"bank timeout\"}"), adminToken())
            .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"));
        perform(get(base(shop) + "/finance/wallet"), shop.token)
            .andExpect(jsonPath("$.available").value(0.00))
            .andExpect(jsonPath("$.reserved").value(25.00));
    }

    private static String base(OpenShop shop) {
        return "/api/merchant/shops/" + shop.shopId;
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
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
            var approver = users.save(UserAccount.systemAccount("po-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"po-approver@example.com","password":"Password1"}
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
