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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

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
        jdbc.update("DELETE FROM shop_invoice_events");
        jdbc.update("DELETE FROM shop_invoice_lines");
        jdbc.update("DELETE FROM shop_invoices");
        jdbc.update("DELETE FROM shop_invoice_sequences");
        jdbc.update("DELETE FROM shop_tax_profiles");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM order_addresses");
        jdbc.update("DELETE FROM orders");
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
    void taxInvoiceBlockedUntilProfileReadyCommercialInvoiceWorks() throws Exception {
        var shop = openShop("inv-a@example.com", "0815700001", "ร้านเอกสาร", "Docs", "1234567890911");
        UUID orderId = seedOrder(shop.shopId, "INV-ORD-1", new BigDecimal("100.00"));

        perform(get(base(shop) + "/finance/invoices/tax-profile"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canIssueTaxInvoice").value(false));

        perform(post(base(shop) + "/finance/invoices").contentType(MediaType.APPLICATION_JSON).content("""
            {"orderId":"%s","docType":"TAX_INVOICE","idempotencyKey":"tax-1","buyerTaxId":"1234567890123"}
            """.formatted(orderId)), shop.token)
            .andExpect(status().isConflict());

        var draft = perform(post(base(shop) + "/finance/invoices").contentType(MediaType.APPLICATION_JSON).content("""
            {"orderId":"%s","docType":"INVOICE","idempotencyKey":"inv-1"}
            """.formatted(orderId)), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.docType").value("INVOICE"))
            .andExpect(jsonPath("$.taxAmount").value(0.00))
            .andExpect(jsonPath("$.grandTotal").value(107.00))
            .andReturn();
        String id = JsonPath.read(draft.getResponse().getContentAsString(), "$.id");

        // idempotent replay
        perform(post(base(shop) + "/finance/invoices").contentType(MediaType.APPLICATION_JSON).content("""
            {"orderId":"%s","docType":"INVOICE","idempotencyKey":"inv-1"}
            """.formatted(orderId)), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id));

        // mismatch payload
        perform(post(base(shop) + "/finance/invoices").contentType(MediaType.APPLICATION_JSON).content("""
            {"orderId":"%s","docType":"TAX_INVOICE","idempotencyKey":"inv-1","buyerTaxId":"1234567890123"}
            """.formatted(orderId)), shop.token)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_PAYLOAD_MISMATCH"));

        perform(post(base(shop) + "/finance/invoices/" + id + "/issue").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"issue-1\"}"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ISSUED"))
            .andExpect(jsonPath("$.documentNumber", startsWith("INV-")));

        perform(get(base(shop) + "/finance/invoices/" + id + "/pdf"), shop.token)
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("application/pdf")))
            .andExpect(content().string(not(emptyOrNullString())));

        perform(get(base(shop) + "/finance/invoices/export.csv"), shop.token)
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("documentNumber")));
    }

    @Test
    void taxInvoiceAfterProfileAndCrossShopDenied() throws Exception {
        var shopA = openShop("inv-b@example.com", "0815700002", "ร้านภาษี", "Vat", "1234567890912");
        var shopB = openShop("inv-c@example.com", "0815700003", "ร้านอื่น", "Other", "1234567890913");
        UUID orderId = seedOrder(shopA.shopId, "TAX-ORD-1", new BigDecimal("200.00"));

        perform(put(base(shopA) + "/finance/invoices/tax-profile").contentType(MediaType.APPLICATION_JSON).content("""
            {"legalName":"หจก.ทดสอบ","taxId":"0123456789012","branchCode":"00000","vatRegistered":true,"vatRate":0.07}
            """), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canIssueTaxInvoice").value(true));

        var created = perform(post(base(shopA) + "/finance/invoices").contentType(MediaType.APPLICATION_JSON).content("""
            {"orderId":"%s","docType":"TAX_INVOICE","buyerTaxId":"1103700123456","idempotencyKey":"tax-ok"}
            """.formatted(orderId)), shopA.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.docType").value("TAX_INVOICE"))
            .andExpect(jsonPath("$.vatRateApplied").value(0.0700))
            .andExpect(jsonPath("$.taxAmount", greaterThan(0.0)))
            .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        perform(post(base(shopA) + "/finance/invoices/" + id + "/issue").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"issue-tax\"}"), shopA.token)
            .andExpect(jsonPath("$.status").value("ISSUED"))
            .andExpect(jsonPath("$.documentNumber", startsWith("TAX-")));

        perform(get(base(shopB) + "/finance/invoices/" + id), shopB.token).andExpect(status().isNotFound());
        perform(get(base(shopB) + "/finance/invoices/" + id + "/pdf"), shopB.token).andExpect(status().isNotFound());
    }

    private UUID seedOrder(long shopNo, String orderNumber, BigDecimal itemTotal) {
        var shopUuid = shops.findByShopId(shopNo).orElseThrow().getUuid();
        var orderId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO orders (id, shop_id, order_number, channel, status, payment_status, fulfillment_status, currency,
              subtotal, discount_total, shipping_fee, tax_total, grand_total, cod_fee, placed_at, merchant_confirm_status,
              created_date_time, modified_date_time, row_version, stock_committed, payment_method, oms_sync_status)
            VALUES (?, ?, ?, 'STOREFRONT', 'PACKED', 'PAID', 'UNFULFILLED', 'THB',
              ?, 0, 7.00, 0, ?, 0, NOW(), 'AWAITING', NOW(), NOW(), 0, false, 'BANK_TRANSFER', 'NOT_SYNCED')
            """, orderId, shopUuid, orderNumber, itemTotal, itemTotal.add(new BigDecimal("7.00")));
        jdbc.update("""
            INSERT INTO order_items (id, order_id, shop_id, sku_code_snapshot, product_name_snapshot, unit_price, quantity,
              discount_amount, line_total, currency, created_date_time, modified_date_time)
            VALUES (?, ?, ?, 'SKU-1', 'สินค้าทดสอบ', ?, 1, 0, ?, 'THB', NOW(), NOW())
            """, itemId, orderId, shopUuid, itemTotal, itemTotal);
        jdbc.update("""
            INSERT INTO order_addresses (id, order_id, address_type, recipient_name, phone, address_line1, district, province,
              postal_code, country_code, created_date_time, modified_date_time)
            VALUES (?, ?, 'SHIPPING', 'ลูกค้าทดสอบ', '0811111111', '1 ถนนทดสอบ', 'บางรัก', 'กรุงเทพมหานคร', '10500', 'TH', NOW(), NOW())
            """, UUID.randomUUID(), orderId);
        return orderId;
    }

    private static String base(OpenShop shop) {
        return "/api/merchant/shops/" + shop.shopId;
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
        if (adminToken == null) {
            var approver = users.save(UserAccount.systemAccount("inv-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"inv-approver@example.com","password":"Password1"}
                """)).andExpect(status().isOk()).andReturn();
            adminToken = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.accessToken");
        }
        mvc.perform(post("/api/admin/store-applications/" + shopId + "/approve").header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        return new OpenShop(seller, shopId.longValue());
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
