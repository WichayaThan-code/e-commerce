package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.customer.ContactNormalizer;
import com.thaishopfun.customer.CustomerAuditLogRepository;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Shop customer records: CRUD, multiple addresses, tags/notes, search/filter/sort/paging, duplicate detection with
 * Thai phone format variants, merge (addresses/tags/orders moved, secondary MERGED, audit, idempotent), validation,
 * permissions (403, PII masking) and shop isolation (404).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerIntegrationTests {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
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

    private static final String ADDR_HOME = """
        {"label":"บ้าน","recipientName":"สมหญิง ใจดี","phone":"081-234-5678","addressLine1":"99/1 ถ.สุขุมวิท","subdistrict":"คลองตันเหนือ","district":"วัฒนา","province":"กรุงเทพมหานคร","postalCode":"10110"}
        """;
    private static final String ADDR_WORK = """
        {"label":"ที่ทำงาน","recipientName":"สมหญิง ใจดี","addressLine1":"1 อาคารสาทร","district":"สาทร","province":"กรุงเทพมหานคร","postalCode":"10120","defaultBilling":true}
        """;

    @Test
    void normalizesThaiPhonesAndEmails() {
        for (var raw : List.of("081-234-5678", "0812345678", "081 234 5678", "+66 81 234 5678", "+66812345678", "66812345678", "+66 (0)81-234-5678", "0066812345678")) {
            assertEquals("0812345678", ContactNormalizer.phone(raw), raw);
        }
        assertEquals("021234567", ContactNormalizer.phone("+66 2 123 4567"));
        assertTrue(ContactNormalizer.isValidPhone(ContactNormalizer.phone("02-123-4567")));
        assertFalse(ContactNormalizer.isValidPhone(ContactNormalizer.phone("12345")));
        assertEquals("som@example.com", ContactNormalizer.email("  Som@Example.COM "));
    }

    @Test
    void crudWithMultipleAddressesTagsNotesSearchFilterAndPaging() throws Exception {
        var shop = openShop("cu-crud@example.com", "0814000001", "ร้านลูกค้า", "Customer Shop", "1234567890301");
        var created = perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"สมหญิง ใจดี","phone":"081-234-5678","email":"Somying@Example.com","customerCode":"C-001","source":"LINE",
             "notes":"ชอบสีชมพู","tags":["VIP"," vip ","ขาประจำ"],"addresses":[%s,%s]}
            """.formatted(ADDR_HOME, ADDR_WORK))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.displayName").value("สมหญิง ใจดี"))
            .andExpect(jsonPath("$.source").value("LINE"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.tags", containsInAnyOrder("VIP", "ขาประจำ")))
            .andExpect(jsonPath("$.notes").value("ชอบสีชมพู"))
            .andExpect(jsonPath("$.addresses.length()").value(2))
            .andExpect(jsonPath("$.addresses[0].defaultShipping").value(true))
            .andExpect(jsonPath("$.addresses[0].defaultBilling").value(false))
            .andExpect(jsonPath("$.addresses[1].defaultBilling").value(true))
            .andExpect(jsonPath("$.piiMasked").value(false))
            .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        String home = JsonPath.read(created, "$.addresses[0].id");
        assertEquals("0812345678", jdbc.queryForObject("SELECT phone_normalized FROM shop_customers WHERE id = ?", String.class, UUID.fromString(id)));
        assertEquals("somying@example.com", jdbc.queryForObject("SELECT email_normalized FROM shop_customers WHERE id = ?", String.class, UUID.fromString(id)));

        perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"Anan","phone":"02-123-4567","source":"SHOPEE","tags":["wholesale"]}
            """).andExpect(status().isCreated()).andExpect(jsonPath("$.addresses.length()").value(0));
        perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"Boon","source":"walk-in"}
            """).andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("WALK_IN"));

        // update fields + tags + status, addresses untouched when omitted
        perform(put(base(shop) + "/customers/" + id), shop.token, """
            {"displayName":"สมหญิง ใจดีมาก","phone":"0812345678","email":"somying@example.com","customerCode":"C-001","status":"BLOCKED","notes":"ค้างชำระ","tags":["VIP","ค้างจ่าย"]}
            """).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("BLOCKED"))
            .andExpect(jsonPath("$.tags", containsInAnyOrder("VIP", "ค้างจ่าย")))
            .andExpect(jsonPath("$.addresses.length()").value(2));

        // address endpoints: add as default shipping, update, delete
        var withThird = perform(post(base(shop) + "/customers/" + id + "/addresses"), shop.token, """
            {"recipientName":"แม่","addressLine1":"5 ม.2","subdistrict":"ช้างเผือก","district":"เมืองเชียงใหม่","province":"เชียงใหม่","postalCode":"50300","defaultShipping":true}
            """).andExpect(status().isOk()).andExpect(jsonPath("$.addresses.length()").value(3)).andReturn().getResponse().getContentAsString();
        assertEquals(List.of(true), JsonPath.read(withThird, "$.addresses[?(@.recipientName == 'แม่')].defaultShipping"));
        assertEquals(1, ((List<?>) JsonPath.read(withThird, "$.addresses[?(@.defaultShipping == true)]")).size());
        perform(put(base(shop) + "/customers/" + id + "/addresses/" + home), shop.token, ADDR_HOME.replace("บ้าน", "บ้านใหม่"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.addresses[?(@.label == 'บ้านใหม่')]").isNotEmpty());
        perform(delete(base(shop) + "/customers/" + id + "/addresses/" + home), shop.token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.addresses.length()").value(2));

        // search by name / phone format variant / email case / code
        perform(get(base(shop) + "/customers?q=ใจดี"), shop.token).andExpect(jsonPath("$.total").value(1));
        perform(get(base(shop) + "/customers").param("q", "+66 81 234 5678"), shop.token).andExpect(jsonPath("$.items[0].id").value(id));
        perform(get(base(shop) + "/customers?q=SOMYING@EXAMPLE"), shop.token).andExpect(jsonPath("$.total").value(1));
        perform(get(base(shop) + "/customers?q=c-001"), shop.token).andExpect(jsonPath("$.total").value(1));
        // filters
        perform(get(base(shop) + "/customers?source=SHOPEE"), shop.token).andExpect(jsonPath("$.items[*].displayName", contains("Anan")));
        perform(get(base(shop) + "/customers?status=BLOCKED"), shop.token).andExpect(jsonPath("$.items[*].id", contains(id)));
        perform(get(base(shop) + "/customers?tag=vip"), shop.token).andExpect(jsonPath("$.items[*].id", contains(id)));
        // sort + paging
        perform(get(base(shop) + "/customers?sort=name,asc&size=2&page=0"), shop.token)
            .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items[*].displayName", contains("Anan", "Boon")));
        perform(get(base(shop) + "/customers?sort=name,asc&size=2&page=1"), shop.token).andExpect(jsonPath("$.items[*].displayName", contains("สมหญิง ใจดีมาก")));
        perform(get(base(shop) + "/customers?sort=phone,asc"), shop.token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("SORT_INVALID"));

        // archive = soft delete, hidden from default list, visible with status filter
        perform(delete(base(shop) + "/customers/" + id), shop.token).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        perform(get(base(shop) + "/customers"), shop.token).andExpect(jsonPath("$.total").value(2));
        perform(get(base(shop) + "/customers?status=ARCHIVED"), shop.token).andExpect(jsonPath("$.items[*].id", contains(id)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM shop_customers WHERE id = ?", Integer.class, UUID.fromString(id)));
        assertFalse(customerAudits.findByResourceTypeAndResourceIdOrderByIdAsc("CUSTOMER", UUID.fromString(id)).isEmpty());
    }

    @Test
    void duplicatePhoneOrEmailIsDetectedAcrossFormatsAndRejectedUnlessAllowed() throws Exception {
        var shop = openShop("cu-dup@example.com", "0814000002", "ร้านซ้ำ", "Dup Shop", "1234567890302");
        String first = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"ลูกค้า A","phone":"081-234-5678","email":"a@example.com"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        for (var variant : List.of("+66 81 234 5678", "66812345678", "0812345678", "081 234 5678")) {
            perform(get(base(shop) + "/customers/duplicates").param("phone", variant), shop.token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNormalized").value("0812345678"))
                .andExpect(jsonPath("$.matches[0].id").value(first))
                .andExpect(jsonPath("$.matches[0].matchedOn", contains("PHONE")));
        }
        perform(get(base(shop) + "/customers/duplicates").param("email", " A@EXAMPLE.COM "), shop.token)
            .andExpect(jsonPath("$.matches[0].matchedOn", contains("EMAIL")));
        perform(get(base(shop) + "/customers/duplicates").param("phone", "0899999999"), shop.token).andExpect(jsonPath("$.matches.length()").value(0));

        perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"ลูกค้า A (LINE)","phone":"+66 81 234 5678"}
            """).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_CUSTOMER"))
            .andExpect(jsonPath("$.existingCustomerId").value(first))
            .andExpect(jsonPath("$.duplicates[0].matchedOn", contains("PHONE")));
        perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"อีกคน","email":"A@Example.com"}
            """).andExpect(status().isConflict()).andExpect(jsonPath("$.duplicates[0].matchedOn", contains("EMAIL")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM shop_customers", Integer.class));

        String second = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"ลูกค้า A (LINE)","phone":"+66 81 234 5678","allowDuplicate":true}
            """).andExpect(status().isCreated())
            .andExpect(jsonPath("$.possibleDuplicates[0].id").value(first))
            .andReturn().getResponse().getContentAsString(), "$.id");
        perform(get(base(shop) + "/customers/" + second + "/duplicates"), shop.token).andExpect(jsonPath("$.matches[*].id", contains(first)));

        // update that moves onto another customer's email is also checked
        String third = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"C","email":"c@example.com"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        perform(put(base(shop) + "/customers/" + third), shop.token, """
            {"displayName":"C","email":"a@example.com"}
            """).andExpect(status().isConflict()).andExpect(jsonPath("$.existingCustomerId").value(first));
        assertEquals("c@example.com", jdbc.queryForObject("SELECT email FROM shop_customers WHERE id = ?", String.class, UUID.fromString(third)));

        // same phone in another shop is not a duplicate
        var other = openShop("cu-dup2@example.com", "0814000003", "ร้านซ้ำสอง", "Dup Shop Two", "1234567890303");
        perform(post(base(other) + "/customers"), other.token, """
            {"displayName":"ลูกค้า A","phone":"0812345678"}
            """).andExpect(status().isCreated());
    }

    @Test
    void mergeMovesAddressesTagsNotesOrdersAndMarksSecondaryMerged() throws Exception {
        var shop = openShop("cu-merge@example.com", "0814000004", "ร้านรวม", "Merge Shop", "1234567890304");
        String primary = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"สมหญิง","phone":"081-234-5678","source":"LINE","notes":"ลูกค้า LINE","tags":["VIP"],"addresses":[%s]}
            """.formatted(ADDR_HOME)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String secondary = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"Somying FB","phone":"+66812345678","email":"somying@example.com","customerCode":"FB-9","source":"FACEBOOK",
             "notes":"ลูกค้า Facebook","tags":["vip","Facebook"],"addresses":[%s,%s],"allowDuplicate":true}
            """.formatted(ADDR_HOME.replace("081-234-5678", "+66 81 234 5678"), ADDR_WORK)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        var shopUuid = shops.findByShopId(shop.shopId).orElseThrow().getUuid();
        insertOrder(shopUuid, UUID.fromString(secondary), "SO-1", "500.00", "COMPLETED");
        insertOrder(shopUuid, UUID.fromString(secondary), "SO-2", "120.00", "CANCELLED");
        insertOrder(shopUuid, UUID.fromString(primary), "SO-3", "80.00", "DELIVERED");

        perform(post(base(shop) + "/customers/merge"), shop.token, """
            {"primaryId":"%s","secondaryId":"%s"}
            """.formatted(primary, secondary)).andExpect(status().isOk())
            .andExpect(jsonPath("$.alreadyMerged").value(false))
            .andExpect(jsonPath("$.addressesMoved").value(1))
            .andExpect(jsonPath("$.addressesSkippedAsDuplicate").value(1))
            .andExpect(jsonPath("$.ordersMoved").value(2))
            .andExpect(jsonPath("$.tagsAdded", contains("Facebook")))
            .andExpect(jsonPath("$.fieldsFilled", hasItems("email", "customerCode", "notes")))
            .andExpect(jsonPath("$.primary.displayName").value("สมหญิง"))
            .andExpect(jsonPath("$.primary.phone").value("081-234-5678"))
            .andExpect(jsonPath("$.primary.source").value("LINE"))
            .andExpect(jsonPath("$.primary.email").value("somying@example.com"))
            .andExpect(jsonPath("$.primary.customerCode").value("FB-9"))
            .andExpect(jsonPath("$.primary.tags", containsInAnyOrder("VIP", "Facebook")))
            .andExpect(jsonPath("$.primary.notes", allOf(containsString("ลูกค้า LINE"), containsString("ลูกค้า Facebook"), containsString("รวมจากลูกค้า Somying FB"))))
            .andExpect(jsonPath("$.primary.addresses.length()").value(2))
            .andExpect(jsonPath("$.primary.addresses[?(@.defaultShipping == true)].label", contains("บ้าน")))
            .andExpect(jsonPath("$.primary.addresses[?(@.defaultBilling == true)].label", contains("บ้าน")))
            .andExpect(jsonPath("$.primary.orderCount").value(3));

        perform(get(base(shop) + "/customers/" + secondary), shop.token)
            .andExpect(jsonPath("$.status").value("MERGED")).andExpect(jsonPath("$.mergedIntoId").value(primary));
        perform(get(base(shop) + "/customers/" + primary + "/orders?size=2"), shop.token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderCount").value(3))
            .andExpect(jsonPath("$.totalSpent").value(580.0))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.totalPages").value(2));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE shop_customer_id = ?", Integer.class, UUID.fromString(secondary)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM shop_customer_addresses WHERE customer_id = ? AND deleted_at IS NULL", Integer.class, UUID.fromString(secondary)));
        var audit = customerAudits.findByResourceTypeAndResourceIdOrderByIdAsc("CUSTOMER", UUID.fromString(primary)).stream()
            .filter(row -> row.getAction().equals("CUSTOMER_MERGE")).toList();
        assertEquals(1, audit.size());
        assertEquals(secondary, audit.get(0).getAfterData().get("secondaryId"));
        assertEquals(List.of("PHONE"), audit.get(0).getAfterData().get("matchedOn"));

        // default list hides MERGED; duplicate check ignores MERGED
        perform(get(base(shop) + "/customers"), shop.token).andExpect(jsonPath("$.items[*].id", contains(primary)));
        perform(get(base(shop) + "/customers/" + primary + "/duplicates"), shop.token).andExpect(jsonPath("$.matches.length()").value(0));

        // idempotent repeat
        perform(post(base(shop) + "/customers/merge"), shop.token, """
            {"primaryId":"%s","secondaryId":"%s"}
            """.formatted(primary, secondary)).andExpect(status().isOk()).andExpect(jsonPath("$.alreadyMerged").value(true)).andExpect(jsonPath("$.ordersMoved").value(0));
        assertEquals(1, customerAudits.findByResourceTypeAndResourceIdOrderByIdAsc("CUSTOMER", UUID.fromString(primary)).stream()
            .filter(row -> row.getAction().equals("CUSTOMER_MERGE")).count());

        // cannot merge into a MERGED customer, cannot edit a MERGED customer, cannot merge with itself
        String third = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"Third"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        perform(post(base(shop) + "/customers/merge"), shop.token, """
            {"primaryId":"%s","secondaryId":"%s"}
            """.formatted(secondary, third)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MERGE_TARGET_MERGED"));
        perform(post(base(shop) + "/customers/merge"), shop.token, """
            {"primaryId":"%s","secondaryId":"%s"}
            """.formatted(third, secondary)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOMER_ALREADY_MERGED"));
        perform(put(base(shop) + "/customers/" + secondary), shop.token, """
            {"displayName":"x"}
            """).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOMER_MERGED"));
        perform(post(base(shop) + "/customers/merge"), shop.token, """
            {"primaryId":"%s","secondaryId":"%s"}
            """.formatted(third, third)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("MERGE_SAME_CUSTOMER"));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM shop_customers WHERE id = ?", String.class, UUID.fromString(third)));
    }

    @Test
    void invalidCustomerDataIsRejectedWithFieldErrorsAndNothingIsSaved() throws Exception {
        var shop = openShop("cu-valid@example.com", "0814000005", "ร้านตรวจ", "Valid Shop", "1234567890305");
        perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":" ","phone":"12-34","email":"not-an-email","customerCode":"bad code!","source":"MYSPACE","status":"MERGED",
             "tags":["", "%s"],
             "addresses":[{"recipientName":"","addressLine1":"","district":"","province":"","postalCode":"1011","phone":"999"},
                          {"recipientName":"A","addressLine1":"1","district":"D","province":"P","postalCode":"10110","defaultShipping":true,"countryCode":"THA"},
                          {"recipientName":"B","addressLine1":"2","district":"D","province":"P","postalCode":"10110","defaultShipping":true}]}
            """.formatted("x".repeat(51)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItems("displayName", "phone", "email", "customerCode", "source", "status", "tags.0", "tags.1",
                "addresses.0.recipientName", "addresses.0.addressLine1", "addresses.0.district", "addresses.0.province", "addresses.0.postalCode",
                "addresses.0.phone", "addresses.1.countryCode", "addresses")))
            .andExpect(jsonPath("$.fieldErrors[*].code", hasItems("DISPLAY_NAME_REQUIRED", "PHONE_INVALID", "EMAIL_INVALID", "CUSTOMER_CODE_INVALID",
                "SOURCE_INVALID", "STATUS_INVALID", "TAG_INVALID", "POSTAL_CODE_INVALID", "ADDRESS_DEFAULT_DUPLICATE")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM shop_customers", Integer.class));

        perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"A","customerCode":"C1"}
            """).andExpect(status().isCreated());
        perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"B","customerCode":"c1"}
            """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("CUSTOMER_CODE_TAKEN"));
        perform(get(base(shop) + "/customers?status=GONE"), shop.token).andExpect(status().isBadRequest());
    }

    @Test
    void permissionsPiiMaskingAndShopIsolation() throws Exception {
        var shop = openShop("cu-owner@example.com", "0814000006", "ร้านเจ้าของ", "Owner Shop", "1234567890306");
        var other = openShop("cu-rival@example.com", "0814000007", "ร้านคู่แข่ง", "Rival Shop", "1234567890307");
        String id = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"สมหญิง ใจดี","phone":"081-234-5678","email":"somying@example.com","notes":"ลับ","addresses":[%s]}
            """.formatted(ADDR_HOME)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String b = JsonPath.read(perform(post(base(shop) + "/customers"), shop.token, """
            {"displayName":"B"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String foreign = JsonPath.read(perform(post(base(other) + "/customers"), other.token, """
            {"displayName":"Rival customer","phone":"0812345678"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        var viewerToken = token("cu-viewer@example.com", "0814000008");
        addMember(shop.shopId, users.findByEmailIgnoreCaseOrPhone("cu-viewer@example.com", "0814000008").orElseThrow().getId(), "STORE_VIEWER");
        perform(get(base(shop) + "/customers/" + id), viewerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.piiMasked").value(true))
            .andExpect(jsonPath("$.displayName").value("ส***"))
            .andExpect(jsonPath("$.phone").value("******5678"))
            .andExpect(jsonPath("$.email").value("s***@example.com"))
            .andExpect(jsonPath("$.notes").doesNotExist())
            .andExpect(jsonPath("$.addresses").doesNotExist());
        perform(get(base(shop) + "/customers"), viewerToken).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        perform(get(base(shop) + "/customers/" + id + "/orders"), viewerToken).andExpect(status().isOk());
        perform(post(base(shop) + "/customers"), viewerToken, "{\"displayName\":\"x\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        perform(put(base(shop) + "/customers/" + id), viewerToken, "{\"displayName\":\"x\"}").andExpect(status().isForbidden());
        perform(delete(base(shop) + "/customers/" + id), viewerToken).andExpect(status().isForbidden());
        perform(post(base(shop) + "/customers/" + id + "/addresses"), viewerToken, ADDR_WORK).andExpect(status().isForbidden());
        perform(post(base(shop) + "/customers/merge"), viewerToken, "{\"primaryId\":\"%s\",\"secondaryId\":\"%s\"}".formatted(id, b))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        // another shop's owner: shop path -> SHOP_NOT_FOUND; own shop path with a foreign id -> CUSTOMER_NOT_FOUND
        perform(get(base(shop) + "/customers/" + id), other.token).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));
        perform(get(base(shop) + "/customers"), other.token).andExpect(status().isNotFound());
        perform(get(base(other) + "/customers/" + id), other.token).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        perform(put(base(other) + "/customers/" + id), other.token, "{\"displayName\":\"x\"}").andExpect(status().isNotFound());
        perform(get(base(other) + "/customers/" + id + "/orders"), other.token).andExpect(status().isNotFound());
        perform(post(base(other) + "/customers/merge"), other.token, "{\"primaryId\":\"%s\",\"secondaryId\":\"%s\"}".formatted(foreign, id))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        perform(post(base(shop) + "/customers/merge"), shop.token, "{\"primaryId\":\"%s\",\"secondaryId\":\"%s\"}".formatted(id, foreign))
            .andExpect(status().isNotFound());
        perform(get(base(other) + "/customers/duplicates?phone=0812345678"), other.token).andExpect(jsonPath("$.matches[*].id", contains(foreign)));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM shop_customers WHERE id = ?", String.class, UUID.fromString(foreign)));
        mvc.perform(get(base(shop) + "/customers")).andExpect(status().isUnauthorized());
    }

    private void insertOrder(UUID shopUuid, UUID customerId, String number, String total, String status) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO orders (id, shop_id, order_number, shop_customer_id, channel, status, payment_status, fulfillment_status, currency, grand_total, placed_at, created_date_time) "
            + "VALUES (?, ?, ?, ?, 'MANUAL', ?, 'PAID', 'UNFULFILLED', 'THB', ?, ?, ?)", UUID.randomUUID(), shopUuid, number, customerId, status, new BigDecimal(total), now, now);
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

    /** One platform admin per test: system accounts share a placeholder phone number. */
    private String adminToken() throws Exception {
        if (adminToken == null) {
            var approver = users.save(UserAccount.systemAccount("cu-approver@example.com", passwords.encode("Password1")));
            platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
            var adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identity":"cu-approver@example.com","password":"Password1"}
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
