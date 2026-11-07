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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerInsightsExportTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final Pattern UUID_RE = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

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
        jdbc.update("DELETE FROM order_addresses");
        jdbc.update("DELETE FROM orders");
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
    void exportFiltersMaskingAndNotCapturedAsKey() throws Exception {
        var shop = openShop("ciex-a@example.com", "0814400001", "ร้านส่งออก", "Export Shop", "1234567890701");
        var shopUuid = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        String vipId = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"ลูกค้า VIP ส่งออก","phone":"0817778888","email":"vip-export@example.com"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String genId = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"ลูกค้าทั่วไป","phone":"0817779999"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        var now = Instant.now();
        insertOrder(shopUuid, UUID.fromString(vipId), null, "EX-V1", "6000.00", "COMPLETED", "LINE", now, now);
        insertOrder(shopUuid, UUID.fromString(vipId), null, "EX-V2", "5000.00", "COMPLETED", "LINE", now, now);
        insertOrder(shopUuid, UUID.fromString(genId), null, "EX-G1", "100.00", "COMPLETED", "MANUAL", now, now);
        insertOrder(shopUuid, UUID.fromString(genId), null, "EX-WAIT", "50.00", "PAID_AWAITING_MERCHANT_CONFIRM", "MANUAL", null, now);

        perform(get(base(shop) + "/customer-insights/export"), shop.token)
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("customers-" + shop.shopId)));

        byte[] xlsx = perform(get(base(shop) + "/customer-insights/export").param("segment", "VIP"), shop.token)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sheet = book.getSheet("customers");
            assertEquals(1, sheet.getLastRowNum()); // header + 1 VIP
            assertEquals("ลูกค้า VIP ส่งออก", sheet.getRow(1).getCell(0).getStringCellValue());
            assertFalse(sheet.getTables().isEmpty());
            assertFalse(containsUuidOrKey(sheet));
        }

        var viewer = token("ciex-viewer@example.com", "0814400008");
        addMember(shop.shopId, users.findByEmailIgnoreCaseOrPhone("ciex-viewer@example.com", "0814400008").orElseThrow().getId(), "STORE_VIEWER");
        byte[] masked = perform(get(base(shop) + "/customer-insights/export").param("segment", "VIP"), viewer)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(masked))) {
            var sheet = book.getSheet("customers");
            var name = sheet.getRow(1).getCell(0).getStringCellValue();
            assertTrue(name.startsWith("ล***"), name);
            assertFalse(containsUuidOrKey(sheet));
        }

        jdbc.update("""
            DELETE FROM auth_role_permissions
             WHERE role_id = (SELECT id FROM auth_roles WHERE code = 'STORE_VIEWER')
               AND permission_id = (SELECT id FROM auth_permissions WHERE code = 'CUSTOMER_READ')
            """);
        permissionMatrix.invalidate();
        perform(get(base(shop) + "/customer-insights/export"), viewer)
            .andExpect(status().isForbidden());
        jdbc.update("""
            INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version, created_date_time, modified_date_time)
            SELECT gen_random_uuid(), r.id, p.id, '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
              FROM auth_roles r, auth_permissions p
             WHERE r.code = 'STORE_VIEWER' AND p.code = 'CUSTOMER_READ'
               AND NOT EXISTS (
                 SELECT 1 FROM auth_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id)
            """);
        permissionMatrix.invalidate();

        // /export must not be treated as a customer key
        perform(get(base(shop) + "/customer-insights/export"), shop.token)
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx")));
    }

    private static boolean containsUuidOrKey(org.apache.poi.ss.usermodel.Sheet sheet) {
        for (var row : sheet) {
            for (var cell : row) {
                String text = switch (cell.getCellType()) {
                    case STRING -> cell.getStringCellValue();
                    case NUMERIC -> String.valueOf(cell.getNumericCellValue());
                    default -> "";
                };
                if (text.startsWith("c1") || UUID_RE.matcher(text).find()) return true;
            }
        }
        return false;
    }

    private void insertOrder(UUID shopUuid, UUID customerId, UUID buyerId, String number, String total, String status,
                             String channel, Instant confirmedAt, Instant placedAt) {
        var now = Timestamp.from(Instant.now());
        var placed = Timestamp.from(placedAt == null ? Instant.now() : placedAt);
        var confirmed = confirmedAt == null ? null : Timestamp.from(confirmedAt);
        jdbc.update("""
            INSERT INTO orders (id, shop_id, order_number, shop_customer_id, buyer_user_id, channel, status, payment_status,
              fulfillment_status, currency, grand_total, placed_at, merchant_confirmed_at, merchant_confirm_status, created_date_time, modified_date_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'PAID', 'UNFULFILLED', 'THB', ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), shopUuid, number, customerId, buyerId, channel, status, new BigDecimal(total),
            placed, confirmed, confirmed == null ? "AWAITING" : "CONFIRMED", now, now);
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
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
            var approver = users.save(UserAccount.systemAccount("ciex-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"ciex-approver@example.com","password":"Password1"}
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
