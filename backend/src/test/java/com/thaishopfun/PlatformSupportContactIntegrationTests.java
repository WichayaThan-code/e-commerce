package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.audit.PlatformAuditLogRepository;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.platform.PlatformSetting;
import com.thaishopfun.platform.PlatformSettingRepository;
import com.thaishopfun.platform.SupportContactDtos.DayHours;
import com.thaishopfun.platform.SupportContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformSupportContactIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired PlatformSettingRepository settings;
    @Autowired PlatformAuditLogRepository auditLogs;
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

    @BeforeEach
    void clean() {
        auditLogs.deleteAll();
        settings.deleteAll();
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
        seedDefault();
    }

    @Test
    void publicGetReturnsOnlyEnabledChannelsWithStructuredHours() throws Exception {
        mvc.perform(get("/api/platform/contact"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.chat.hours.length()").value(7))
            .andExpect(jsonPath("$.chat.hours[0].day").value(0))
            .andExpect(jsonPath("$.chat.hours[0].open").value(true))
            .andExpect(jsonPath("$.chat.hours[0].start").value("08:00"))
            .andExpect(jsonPath("$.chat.hours[0].end").value("22:00"))
            .andExpect(jsonPath("$.phone.hours[5].open").value(false))
            .andExpect(jsonPath("$.line.id").value("@thaishopfun"))
            .andExpect(jsonPath("$.email.address").value("support@thaishopfun.com"))
            .andExpect(jsonPath("$.phone.number").value("02-123-4567"))
            .andExpect(jsonPath("$.chat.enabled").doesNotExist());

        var row = settings.findBySettingKey(SupportContactService.KEY).orElseThrow();
        Map<String, Object> value = new LinkedHashMap<>(row.getSettingValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> chat = new LinkedHashMap<>((Map<String, Object>) value.get("chat"));
        chat.put("enabled", false);
        value.put("chat", chat);
        row.replaceValue(value, null);
        settings.save(row);

        mvc.perform(get("/api/platform/contact"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.chat").doesNotExist())
            .andExpect(jsonPath("$.line.id").value("@thaishopfun"));
    }

    @Test
    void adminGetAndPutUpdateContactAndWriteAudit() throws Exception {
        String admin = adminToken();
        mvc.perform(get("/api/admin/settings/contact").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email.address").value("support@thaishopfun.com"))
            .andExpect(jsonPath("$.chat.enabled").value(true))
            .andExpect(jsonPath("$.chat.hours.length()").value(7));

        String body = """
            {
              "chat": {"enabled": true, "url": "https://chat.example.com/support", "hours": %s},
              "line": {"enabled": true, "id": "@thaishopfun", "url": null},
              "email": {"enabled": true, "address": "help@thaishopfun.com"},
              "phone": {"enabled": false, "number": "02-123-4567", "hours": %s}
            }
            """.formatted(hoursJson(SupportContactService.defaultChatHours()), hoursJson(SupportContactService.defaultPhoneHours()));
        mvc.perform(put("/api/admin/settings/contact").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email.address").value("help@thaishopfun.com"))
            .andExpect(jsonPath("$.chat.url").value("https://chat.example.com/support"))
            .andExpect(jsonPath("$.phone.enabled").value(false))
            .andExpect(jsonPath("$.chat.hours[6].end").value("22:00"));

        mvc.perform(get("/api/platform/contact"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email.address").value("help@thaishopfun.com"))
            .andExpect(jsonPath("$.phone").doesNotExist())
            .andExpect(jsonPath("$.chat.url").value("https://chat.example.com/support"));

        assertTrue(auditLogs.findAll().stream().anyMatch(row ->
            "PLATFORM_SETTING_UPDATE".equals(row.getAction()) && "platform_settings".equals(row.getResourceType())));
    }

    @Test
    void adminEndpointsRejectWithoutPermission() throws Exception {
        String customer = tokenFor("contact-customer@example.com", "0811110091");
        mvc.perform(get("/api/admin/settings/contact").header("Authorization", "Bearer " + customer))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/settings/contact").header("Authorization", "Bearer " + customer)
                .contentType(MediaType.APPLICATION_JSON).content(validBody()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/settings/contact")).andExpect(status().isUnauthorized());
    }

    @Test
    void putRejectsInvalidFieldsAndBadHoursWith400() throws Exception {
        String admin = adminToken();
        List<DayHours> badChat = new ArrayList<>(SupportContactService.defaultChatHours());
        badChat.set(0, new DayHours(0, true, "18:00", "08:00"));
        String bad = """
            {
              "chat": {"enabled": true, "url": "http://insecure.example.com", "hours": %s},
              "line": {"enabled": true, "id": "not a line", "url": null},
              "email": {"enabled": true, "address": "not-an-email"},
              "phone": {"enabled": true, "number": "123", "hours": %s}
            }
            """.formatted(hoursJson(badChat), hoursJson(SupportContactService.defaultPhoneHours()));
        long before = auditLogs.findAll().stream().filter(row -> "PLATFORM_SETTING_UPDATE".equals(row.getAction())).count();
        mvc.perform(put("/api/admin/settings/contact").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("chat.url")))
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email.address")))
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("phone.number")))
            .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("chat.hours[0].end")))
            .andExpect(jsonPath("$.fieldErrors[*].code", hasItem("TIME_RANGE_INVALID")));

        long after = auditLogs.findAll().stream().filter(row -> "PLATFORM_SETTING_UPDATE".equals(row.getAction())).count();
        assertEquals(before, after);
    }

    private void seedDefault() {
        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("enabled", true);
        chat.put("url", null);
        chat.put("hours", toMaps(SupportContactService.defaultChatHours()));
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("enabled", true);
        line.put("id", "@thaishopfun");
        line.put("url", null);
        Map<String, Object> email = new LinkedHashMap<>();
        email.put("enabled", true);
        email.put("address", "support@thaishopfun.com");
        Map<String, Object> phone = new LinkedHashMap<>();
        phone.put("enabled", true);
        phone.put("number", "02-123-4567");
        phone.put("hours", toMaps(SupportContactService.defaultPhoneHours()));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("chat", chat);
        value.put("line", line);
        value.put("email", email);
        value.put("phone", phone);
        settings.save(new PlatformSetting(SupportContactService.KEY, value, true, "test support contact"));
    }

    private static List<Map<String, Object>> toMaps(List<DayHours> hours) {
        List<Map<String, Object>> out = new ArrayList<>(7);
        for (DayHours day : hours) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", day.day());
            row.put("open", day.open());
            row.put("start", day.start());
            row.put("end", day.end());
            out.add(row);
        }
        return out;
    }

    private static String hoursJson(List<DayHours> hours) {
        return hours.stream()
            .map(day -> "{\"day\":%d,\"open\":%s,\"start\":\"%s\",\"end\":\"%s\"}"
                .formatted(day.day(), day.open(), day.start(), day.end()))
            .collect(Collectors.joining(",", "[", "]"));
    }

    private String validBody() {
        return """
            {
              "chat": {"enabled": true, "url": null, "hours": %s},
              "line": {"enabled": true, "id": "@thaishopfun", "url": null},
              "email": {"enabled": true, "address": "support@thaishopfun.com"},
              "phone": {"enabled": true, "number": "02-123-4567", "hours": %s}
            }
            """.formatted(hoursJson(SupportContactService.defaultChatHours()), hoursJson(SupportContactService.defaultPhoneHours()));
    }

    private String adminToken() throws Exception {
        var admin = users.save(UserAccount.systemAccount("support-admin@example.com", passwords.encode("Password1")));
        platformRoles.save(PlatformRole.grant(admin.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
          {"identity":"support-admin@example.com","password":"Password1"}
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
