package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.help.HelpAudience;
import com.thaishopfun.help.HelpFaq;
import com.thaishopfun.help.HelpFaqRepository;
import com.thaishopfun.help.HelpGuide;
import com.thaishopfun.help.HelpGuideRepository;
import com.thaishopfun.help.HelpTopic;
import com.thaishopfun.help.HelpTopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HelpCenterIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired HelpFaqRepository faqs;
    @Autowired HelpTopicRepository topics;
    @Autowired HelpGuideRepository guides;
    @Autowired com.thaishopfun.help.HelpFeedbackRepository feedback;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordResetTokenRepository passwordResetTokens;
    @Autowired OtpVerificationRepository otpVerifications;
    @Autowired UserAccountRepository users;
    @Autowired PlatformRoleRepository platformRoles;
    @Autowired PasswordEncoder passwords;
    @Autowired com.thaishopfun.merchant.ShopReinstatementRequestRepository reinstatements;
    @Autowired com.thaishopfun.merchant.ShopMemberRepository shopMembers;
    @Autowired com.thaishopfun.merchant.ShopRepository shops;
    @Autowired com.thaishopfun.merchant.MerchantDocumentRepository documents;
    @Autowired com.thaishopfun.merchant.MerchantApplicationRepository applications;
    @Autowired com.thaishopfun.audit.PlatformAuditLogRepository auditLogs;

    @BeforeEach void clean() {
        feedback.deleteAll();
        faqs.deleteAll();
        topics.deleteAll();
        guides.deleteAll();
        platformRoles.deleteAll();
        reinstatements.deleteAll();
        shopMembers.deleteAll();
        shops.deleteAll();
        documents.deleteAll();
        applications.deleteAll();
        otpVerifications.deleteAll();
        passwordResetTokens.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test void publicEndpointsListOnlyActiveLocalizedAndOrdered() throws Exception {
        var orders = topic("ordering", 2, true, "การสั่งซื้อ", "Orders", null);
        var payment = topic("payment", 1, true, "การชำระเงิน", "Payments", "付款");
        topic("hidden", 3, false, "ซ่อน", "Hidden", "隐藏");
        faq(orders, 1, true, HelpAudience.ALL, "สั่งซื้ออย่างไร", "How to order", "如何下单", "ตอบไทย", "English answer", "中文回答");
        faq(orders, 2, true, HelpAudience.CUSTOMER, "ยกเลิกออเดอร์", "Cancel order", null, "ยกเลิกได้", "Cancel before packing", null);
        faq(payment, 3, true, HelpAudience.MERCHANT, "รับเงินอย่างไร", "Get payouts", null, "ทุกสัปดาห์", "Weekly", null);
        faq(orders, 4, false, HelpAudience.ALL, "ปิดอยู่", "Inactive question", null, "ปิด", "Closed", null);
        guide("ordering-guide", 2, true, "คู่มือสั่งซื้อ", "Ordering guide");
        guide("old-guide", 1, false, "คู่มือเก่า", "Old guide");
        guide("pay-guide", 1, true, "คู่มือชำระเงิน", "Payment guide");

        mvc.perform(get("/api/help/topics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].slug").value("payment"))
            .andExpect(jsonPath("$[0].title").value("การชำระเงิน"))
            .andExpect(jsonPath("$[0].faqCount").value(1))
            .andExpect(jsonPath("$[1].slug").value("ordering"))
            .andExpect(jsonPath("$[1].faqCount").value(2));
        mvc.perform(get("/api/help/topics?lang=en"))
            .andExpect(jsonPath("$[0].title").value("Payments"))
            .andExpect(jsonPath("$[1].title").value("Orders"));
        // zh falls back to th where the translation is blank
        mvc.perform(get("/api/help/topics?lang=zh"))
            .andExpect(jsonPath("$[0].title").value("付款"))
            .andExpect(jsonPath("$[1].title").value("การสั่งซื้อ"));

        mvc.perform(get("/api/help/faqs?lang=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].question").value("How to order"))
            .andExpect(jsonPath("$[0].topicSlug").value("ordering"))
            .andExpect(jsonPath("$[0].answer").value("English answer"));
        mvc.perform(get("/api/help/faqs?topic=payment")).andExpect(jsonPath("$.length()").value(1));
        // audience filter: matching audience plus ALL
        mvc.perform(get("/api/help/faqs?audience=CUSTOMER")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/help/faqs?audience=MERCHANT")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/help/faqs?audience=")).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/help/faqs?audience=BOGUS"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HELP_INVALID_AUDIENCE"));
        // search is case-insensitive over question and answer in every language
        mvc.perform(get("/api/help/faqs").param("q", "CANCEL before")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/help/faqs?q=ยกเลิก")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/help/faqs?q=中文")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/help/faqs?q=inactive")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/help/faqs?q=weekly&topic=payment&audience=MERCHANT&lang=en"))
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].answer").value("Weekly"));

        mvc.perform(get("/api/help/guides?lang=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].slug").value("pay-guide"))
            .andExpect(jsonPath("$[0].title").value("Payment guide"))
            .andExpect(jsonPath("$[1].slug").value("ordering-guide"));
    }

    @Test void adminEndpointsRejectAnonymousCustomerAndMerchantWithoutHelpPermissions() throws Exception {
        String customer = tokenFor("help-customer@example.com", "0811110001");
        String merchant = tokenFor("help-merchant@example.com", "0811110002");
        // a shop applicant / merchant still has no PLATFORM_HELP_* permission
        mvc.perform(put("/api/merchant-applications/draft/shop").header("Authorization", "Bearer " + merchant)
            .contentType(MediaType.APPLICATION_JSON).content("""
              {"shopName":"ร้านช่วยเหลือ","shopNameEn":"Help Shop","description":"สินค้า","category":"อาหารและเครื่องดื่ม","saleType":"OWNER","website":"","address":"88 Silom Rd.","province":"กรุงเทพมหานคร","district":"บางรัก","subdistrict":"สีลม","postalCode":"10500"}
              """)).andExpect(status().isOk());

        var id = UUID.randomUUID();
        String body = "{\"slug\":\"x\",\"icon\":\"Box\",\"titleTh\":\"ก\",\"descriptionTh\":\"ข\"}";
        for (String token : new String[]{customer, merchant}) {
            mvc.perform(get("/api/admin/help/topics").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/help/faqs").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/help/guides").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/help/topics").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/help/topics/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
            mvc.perform(patch("/api/admin/help/faqs/" + id + "/active").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/help/guides/reorder").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[]}")).andExpect(status().isForbidden());
            mvc.perform(delete("/api/admin/help/guides/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/help/topics")).andExpect(status().isUnauthorized());
        assertEquals(0, topics.count());
    }

    @Test void platformAdminManagesTopicsFaqsAndGuides() throws Exception {
        String admin = adminToken();

        // ---- topics ----
        String topicBody = """
          {"slug":"shipping","icon":"Truck","titleTh":"การจัดส่ง","titleEn":"Shipping","descriptionTh":"ติดตามพัสดุ","descriptionEn":"Track parcels"}
          """;
        var created = mvc.perform(post("/api/admin/help/topics").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(topicBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("shipping"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.sortOrder").value(1))
            .andExpect(jsonPath("$.titleZh").doesNotExist())
            .andExpect(jsonPath("$.updatedBy").isNotEmpty())
            .andReturn();
        String topicId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/admin/help/topics").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(topicBody))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HELP_SLUG_TAKEN"));
        mvc.perform(post("/api/admin/help/topics").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"slug\":\"no-title\",\"icon\":\"Box\",\"descriptionTh\":\"x\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/help/topics").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"slug\":\"" + "s".repeat(81) + "\",\"icon\":\"Box\",\"titleTh\":\"ก\",\"descriptionTh\":\"x\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HELP_FIELD_TOO_LONG"));
        mvc.perform(post("/api/admin/help/topics").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"slug\":\"long-title\",\"icon\":\"Box\",\"titleTh\":\"" + "ก".repeat(201) + "\",\"descriptionTh\":\"x\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HELP_FIELD_TOO_LONG"));

        var second = mvc.perform(post("/api/admin/help/topics").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"returns\",\"icon\":\"Box\",\"titleTh\":\"คืนสินค้า\",\"descriptionTh\":\"เงื่อนไข\",\"active\":false}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.sortOrder").value(2)).andExpect(jsonPath("$.active").value(false))
            .andReturn();
        String secondTopicId = JsonPath.read(second.getResponse().getContentAsString(), "$.id");
        // updating a topic with its own slug is fine; taking another topic's slug is a conflict
        mvc.perform(put("/api/admin/help/topics/" + topicId).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"shipping\",\"icon\":\"Package\",\"titleTh\":\"จัดส่งสินค้า\",\"descriptionTh\":\"ติดตามพัสดุ\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.icon").value("Package")).andExpect(jsonPath("$.titleTh").value("จัดส่งสินค้า"))
            .andExpect(jsonPath("$.sortOrder").value(1)).andExpect(jsonPath("$.titleEn").doesNotExist());
        mvc.perform(put("/api/admin/help/topics/" + topicId).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"returns\",\"icon\":\"Package\",\"titleTh\":\"ก\",\"descriptionTh\":\"ข\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HELP_SLUG_TAKEN"));
        mvc.perform(put("/api/admin/help/topics/" + UUID.randomUUID()).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(topicBody))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/admin/help/topics").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        // inactive topic is hidden from the public API; activating it shows it
        mvc.perform(get("/api/help/topics")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(patch("/api/admin/help/topics/" + secondTopicId + "/active").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(patch("/api/admin/help/topics/" + secondTopicId + "/active").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/help/topics")).andExpect(jsonPath("$.length()").value(2));

        mvc.perform(put("/api/admin/help/topics/reorder").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + secondTopicId + "\",\"" + topicId + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").value("returns")).andExpect(jsonPath("$[0].sortOrder").value(1))
            .andExpect(jsonPath("$[1].slug").value("shipping")).andExpect(jsonPath("$[1].sortOrder").value(2));
        mvc.perform(put("/api/admin/help/topics/reorder").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + UUID.randomUUID() + "\"]}"))
            .andExpect(status().isNotFound());

        // ---- faqs ----
        var faqCreated = mvc.perform(post("/api/admin/help/faqs").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"topicId":"%s","audience":"customer","questionTh":"ส่งกี่วัน","questionEn":"How long?","answerTh":"1-3 วัน","answerEn":"1-3 days"}
                  """.formatted(topicId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.audience").value("CUSTOMER"))
            .andExpect(jsonPath("$.topicSlug").value("shipping"))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn();
        String faqId = JsonPath.read(faqCreated.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/admin/help/faqs").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"topicId":"%s","audience":"VIP","questionTh":"q","answerTh":"a"}
                  """.formatted(topicId)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HELP_INVALID_AUDIENCE"));
        mvc.perform(post("/api/admin/help/faqs").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"topicId":"%s","questionTh":" ","answerTh":"a"}
                  """.formatted(topicId)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HELP_FIELD_REQUIRED"));
        mvc.perform(post("/api/admin/help/faqs").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"topicId":"%s","questionTh":"%s","answerTh":"a"}
                  """.formatted(topicId, "q".repeat(501))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HELP_FIELD_TOO_LONG"));
        mvc.perform(post("/api/admin/help/faqs").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"topicId":"%s","questionTh":"q","answerTh":"a"}
                  """.formatted(UUID.randomUUID())))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/help/faqs").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].questionEn").value("How long?"));
        mvc.perform(put("/api/admin/help/faqs/" + faqId).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"topicId":"%s","audience":"ALL","questionTh":"จัดส่งกี่วัน","answerTh":"2-4 วัน"}
                  """.formatted(secondTopicId)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.audience").value("ALL"))
            .andExpect(jsonPath("$.topicSlug").value("returns")).andExpect(jsonPath("$.questionEn").doesNotExist());
        mvc.perform(get("/api/help/faqs?topic=returns")).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].question").value("จัดส่งกี่วัน"));
        mvc.perform(patch("/api/admin/help/faqs/" + faqId + "/active").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/help/faqs")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/admin/help/faqs/reorder").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + faqId + "\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].sortOrder").value(1));

        // topic with FAQs cannot be deleted
        mvc.perform(delete("/api/admin/help/topics/" + secondTopicId).header("Authorization", "Bearer " + admin))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HELP_TOPIC_IN_USE"));
        mvc.perform(delete("/api/admin/help/faqs/" + faqId).header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/help/faqs/" + faqId).header("Authorization", "Bearer " + admin)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/help/topics/" + secondTopicId).header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());
        assertEquals(1, topics.count());
        assertEquals(0, faqs.count());

        // ---- guides ----
        String guideBody = """
          {"slug":"merchant-guide","titleTh":"คู่มือร้านค้า","titleEn":"Merchant guide","summaryTh":"เริ่มขาย","url":"https://example.com/guide"}
          """;
        var guideCreated = mvc.perform(post("/api/admin/help/guides").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(guideBody))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.url").value("https://example.com/guide")).andExpect(jsonPath("$.sortOrder").value(1))
            .andReturn();
        String guideId = JsonPath.read(guideCreated.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/admin/help/guides").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(guideBody))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HELP_SLUG_TAKEN"));
        var guideTwo = mvc.perform(post("/api/admin/help/guides").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"slug\":\"ordering-guide\",\"titleTh\":\"คู่มือสั่งซื้อ\",\"summaryTh\":\"ขั้นตอน\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.url").doesNotExist()).andReturn();
        String guideTwoId = JsonPath.read(guideTwo.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/admin/help/guides").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"slug\":\"g\",\"titleTh\":\"ก\",\"summaryTh\":\"" + "s".repeat(501) + "\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HELP_FIELD_TOO_LONG"));
        mvc.perform(put("/api/admin/help/guides/" + guideId).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"ordering-guide\",\"titleTh\":\"ก\",\"summaryTh\":\"ข\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HELP_SLUG_TAKEN"));
        mvc.perform(put("/api/admin/help/guides/" + guideId).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"merchant-guide\",\"titleTh\":\"คู่มือร้านค้า v2\",\"summaryTh\":\"เริ่มขาย\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.titleTh").value("คู่มือร้านค้า v2")).andExpect(jsonPath("$.url").doesNotExist());
        mvc.perform(put("/api/admin/help/guides/reorder").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + guideTwoId + "\",\"" + guideId + "\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].slug").value("ordering-guide")).andExpect(jsonPath("$[1].sortOrder").value(2));
        mvc.perform(put("/api/admin/help/guides/reorder").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/help/guides/" + guideId + "/active").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/help/guides")).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].slug").value("ordering-guide"));
        mvc.perform(get("/api/admin/help/guides").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(delete("/api/admin/help/guides/" + guideId).header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());
        assertEquals(1, guides.count());

        // writes were audited with the manage permission
        var helpAudits = auditLogs.findAll().stream().filter(log -> log.getAction().startsWith("HELP_")).toList();
        org.junit.jupiter.api.Assertions.assertFalse(helpAudits.isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(helpAudits.stream().allMatch(log -> "PLATFORM_HELP_MANAGE".equals(log.getPermission())));
        org.junit.jupiter.api.Assertions.assertTrue(helpAudits.stream().anyMatch(log ->
            "HELP_TOPIC_CREATE".equals(log.getAction()) && "HELP_TOPIC".equals(log.getResourceType())));
        org.junit.jupiter.api.Assertions.assertTrue(helpAudits.stream().anyMatch(log ->
            "HELP_GUIDE_DELETE".equals(log.getAction()) && "HELP_GUIDE".equals(log.getResourceType())));
    }

    @Test void publicTopicDetailAndFaqDetailAreLocalizedAndHideInactiveItems() throws Exception {
        var orders = topic("ordering", 1, true, "การสั่งซื้อ", "Orders", null);
        var hidden = topic("hidden", 2, false, "ซ่อน", "Hidden", null);
        var visible = faq(orders, 1, true, HelpAudience.ALL, "สั่งซื้ออย่างไร", "How to order", null, "ตอบไทย", "English answer", null);
        faq(orders, 2, false, HelpAudience.ALL, "ปิดอยู่", "Inactive", null, "ปิด", "Closed", null);
        var inHidden = faq(hidden, 1, true, HelpAudience.ALL, "ในหัวข้อซ่อน", "In hidden", null, "ก", "a", null);

        mvc.perform(get("/api/help/topics/ordering?lang=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("ordering"))
            .andExpect(jsonPath("$.title").value("Orders"))
            .andExpect(jsonPath("$.faqs.length()").value(1))
            .andExpect(jsonPath("$.faqs[0].question").value("How to order"))
            .andExpect(jsonPath("$.faqs[0].helpfulYes").value(0))
            .andExpect(jsonPath("$.faqs[0].helpfulNo").value(0));
        mvc.perform(get("/api/help/topics/ordering"))
            .andExpect(jsonPath("$.title").value("การสั่งซื้อ")).andExpect(jsonPath("$.faqs[0].answer").value("ตอบไทย"));
        mvc.perform(get("/api/help/topics/hidden")).andExpect(status().isNotFound());
        mvc.perform(get("/api/help/topics/missing")).andExpect(status().isNotFound());

        mvc.perform(get("/api/help/faqs/" + visible.getId() + "?lang=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(visible.getId().toString()))
            .andExpect(jsonPath("$.question").value("How to order"))
            .andExpect(jsonPath("$.answer").value("English answer"))
            .andExpect(jsonPath("$.topicSlug").value("ordering"));
        mvc.perform(get("/api/help/faqs/" + visible.getId() + "?lang=zh"))
            .andExpect(jsonPath("$.question").value("สั่งซื้ออย่างไร"));
        mvc.perform(get("/api/help/faqs/" + inHidden.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/help/faqs/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test void faqFeedbackIsCountedOncePerSessionAndPerUser() throws Exception {
        var orders = topic("ordering", 1, true, "การสั่งซื้อ", "Orders", null);
        var faq = faq(orders, 1, true, HelpAudience.ALL, "q", "q", null, "a", "a", null);
        var inactive = faq(orders, 2, false, HelpAudience.ALL, "q2", "q2", null, "a", "a", null);
        String url = "/api/help/faqs/" + faq.getId() + "/feedback";

        // anonymous: needs a session key and a boolean
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":true}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HELP_FEEDBACK_IDENTITY_REQUIRED"));
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"sessionKey\":\"s1\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HELP_HELPFUL_REQUIRED"));
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":true,\"sessionKey\":\"" + "k".repeat(65) + "\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HELP_FIELD_TOO_LONG"));
        mvc.perform(post("/api/help/faqs/" + inactive.getId() + "/feedback").contentType(MediaType.APPLICATION_JSON)
            .content("{\"helpful\":true,\"sessionKey\":\"s1\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/help/faqs/" + UUID.randomUUID() + "/feedback").contentType(MediaType.APPLICATION_JSON)
            .content("{\"helpful\":true,\"sessionKey\":\"s1\"}")).andExpect(status().isNotFound());

        // first vote of session s1 counts; a repeat (even with the opposite answer) does not
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":true,\"sessionKey\":\"s1\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.counted").value(true))
            .andExpect(jsonPath("$.helpfulYes").value(1)).andExpect(jsonPath("$.helpfulNo").value(0));
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":false,\"sessionKey\":\"s1\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.counted").value(false))
            .andExpect(jsonPath("$.helpfulYes").value(1)).andExpect(jsonPath("$.helpfulNo").value(0));
        // another session counts separately
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":false,\"sessionKey\":\"s2\"}"))
            .andExpect(jsonPath("$.counted").value(true))
            .andExpect(jsonPath("$.helpfulYes").value(1)).andExpect(jsonPath("$.helpfulNo").value(1));

        // signed-in user: counted once, regardless of the session key they send
        String token = tokenFor("help-voter@example.com", "0811110003");
        mvc.perform(post(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"helpful\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.counted").value(true))
            .andExpect(jsonPath("$.helpfulYes").value(2)).andExpect(jsonPath("$.helpfulNo").value(1));
        mvc.perform(post(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"helpful\":true,\"sessionKey\":\"another\"}"))
            .andExpect(jsonPath("$.counted").value(false))
            .andExpect(jsonPath("$.helpfulYes").value(2)).andExpect(jsonPath("$.helpfulNo").value(1));

        // counters are public on the FAQ and admin view; editing the FAQ never resets them
        mvc.perform(get("/api/help/faqs/" + faq.getId()))
            .andExpect(jsonPath("$.helpfulYes").value(2)).andExpect(jsonPath("$.helpfulNo").value(1));
        assertEquals(3, feedback.countByFaqId(faq.getId()));
        String admin = adminToken();
        mvc.perform(put("/api/admin/help/faqs/" + faq.getId()).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"topicId":"%s","questionTh":"คำถามใหม่","answerTh":"คำตอบใหม่"}
                  """.formatted(orders.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.helpfulYes").value(2)).andExpect(jsonPath("$.helpfulNo").value(1));
    }

    @Test void adminCanMarkFaqReviewedAndSeesFeedbackCounters() throws Exception {
        var orders = topic("ordering", 1, true, "การสั่งซื้อ", "Orders", null);
        var faq = faq(orders, 1, true, HelpAudience.ALL, "q", "q", null, "a", "a", null);
        String customer = tokenFor("help-reviewer@example.com", "0811110004");
        mvc.perform(patch("/api/admin/help/faqs/" + faq.getId() + "/reviewed").header("Authorization", "Bearer " + customer))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/help/faqs/" + faq.getId() + "/reviewed")).andExpect(status().isUnauthorized());

        String admin = adminToken();
        mvc.perform(get("/api/admin/help/faqs").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$[0].helpfulYes").value(0)).andExpect(jsonPath("$[0].helpfulNo").value(0))
            .andExpect(jsonPath("$[0].lastReviewedAt").doesNotExist());
        mvc.perform(patch("/api/admin/help/faqs/" + faq.getId() + "/reviewed").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lastReviewedAt").isNotEmpty());
        mvc.perform(get("/api/admin/help/faqs").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$[0].lastReviewedAt").isNotEmpty());
        mvc.perform(get("/api/help/faqs/" + faq.getId())).andExpect(jsonPath("$.lastReviewedAt").isNotEmpty());
        mvc.perform(patch("/api/admin/help/faqs/" + UUID.randomUUID() + "/reviewed").header("Authorization", "Bearer " + admin))
            .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertTrue(auditLogs.findAll().stream().anyMatch(log ->
            "HELP_FAQ_REVIEWED".equals(log.getAction()) && "PLATFORM_HELP_MANAGE".equals(log.getPermission())));
    }

    private HelpTopic topic(String slug, int sort, boolean active, String th, String en, String zh) {
        var topic = new HelpTopic(null);
        topic.setSlug(slug);
        topic.setIcon("Box");
        topic.setSortOrder(sort);
        topic.setActive(active);
        topic.setTitleTh(th);
        topic.setTitleEn(en);
        topic.setTitleZh(zh);
        topic.setDescriptionTh(th + " รายละเอียด");
        return topics.save(topic);
    }

    private HelpFaq faq(HelpTopic topic, int sort, boolean active, HelpAudience audience,
                        String qTh, String qEn, String qZh, String aTh, String aEn, String aZh) {
        var faq = new HelpFaq(null);
        faq.setTopic(topic);
        faq.setSortOrder(sort);
        faq.setActive(active);
        faq.setAudience(audience);
        faq.setQuestionTh(qTh);
        faq.setQuestionEn(qEn);
        faq.setQuestionZh(qZh);
        faq.setAnswerTh(aTh);
        faq.setAnswerEn(aEn);
        faq.setAnswerZh(aZh);
        return faqs.save(faq);
    }

    private HelpGuide guide(String slug, int sort, boolean active, String th, String en) {
        var guide = new HelpGuide(null);
        guide.setSlug(slug);
        guide.setSortOrder(sort);
        guide.setActive(active);
        guide.setTitleTh(th);
        guide.setTitleEn(en);
        guide.setSummaryTh(th + " สรุป");
        guide.setSummaryEn(en + " summary");
        return guides.save(guide);
    }

    private String adminToken() throws Exception {
        var admin = users.save(UserAccount.systemAccount("help-admin@example.com", passwords.encode("Password1")));
        platformRoles.save(PlatformRole.grant(admin.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
          {"identity":"help-admin@example.com","password":"Password1"}
          """)).andExpect(status().isOk()).andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    private String tokenFor(String email, String phone) throws Exception {
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
          {"firstName":"สมหญิง","lastName":"ใจดี","email":"%s","phone":"%s","password":"Password1"}
          """.formatted(email, phone))).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
    }
}
