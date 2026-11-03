package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.admin.AdminSystemStatusService;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSystemStatusTests {
    private static final String BASE = "/api/admin/system/status";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformRoleRepository platformRoles;

    @BeforeEach
    void cleanTokens() {
        // leave catalog; only ensure login users are unique per run via fixed emails recreated
    }

    @Test
    void adminWithPermissionGets200AndCoreComponentsUp() throws Exception {
        String body = mvc.perform(get(BASE).header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overall").value(anyOf(is("UP"), is("DEGRADED"))))
            .andExpect(jsonPath("$.checkedAt").isString())
            .andExpect(jsonPath("$.app.version").isString())
            .andExpect(jsonPath("$.app.startedAt").isString())
            .andExpect(jsonPath("$.app.uptimeSeconds").isNumber())
            .andExpect(jsonPath("$.app.javaVersion").isString())
            .andExpect(jsonPath("$.app.profile").value("test"))
            .andExpect(jsonPath("$.components[?(@.key=='database')].status").value(hasItem("UP")))
            .andExpect(jsonPath("$.components[?(@.key=='migrations')].status").value(hasItem("UP")))
            .andExpect(jsonPath("$.components[?(@.key=='storage')].status").value(hasItem("UP")))
            .andExpect(jsonPath("$.components[?(@.key=='mail')].status").value(hasItem(anyOf(is("UP"), is("UNKNOWN")))))
            .andReturn().getResponse().getContentAsString();

        String lower = body.toLowerCase();
        assertFalse(lower.contains("password"));
        assertFalse(lower.contains("jdbc:"));
        assertFalse(lower.contains("\"host\""));
        assertFalse(body.contains("STORAGE_ROOT"));
        assertFalse(body.contains("target/test-uploads"));

        List<String> keys = JsonPath.read(body, "$.components[*].key");
        assertEquals(List.of("database", "migrations", "mail", "storage"), keys);
        List<Number> latencyValues = JsonPath.read(body, "$.components[?(@.key=='database')].latencyMs");
        assertFalse(latencyValues.isEmpty());
        assertTrue(latencyValues.get(0).longValue() >= 0);
    }

    @Test
    void missingPermissionIs403AndAnonymousIs401() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());

        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"ผู้","lastName":"ซื้อ","email":"sys-buyer@example.com","phone":"0815500099","password":"Password1"}
            """)).andExpect(status().isCreated()).andReturn();
        String buyerToken = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get(BASE).header("Authorization", "Bearer " + buyerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void overallRules() {
        var up = new AdminSystemStatusService.ComponentStatus("database", "UP", 1L, "H2 2.x", null, null, null, null);
        var mig = new AdminSystemStatusService.ComponentStatus("migrations", "UP", null, "n/a", 0, 0, null, null);
        var mail = AdminSystemStatusService.ComponentStatus.of("mail", "UNKNOWN", "not configured");
        var storage = new AdminSystemStatusService.ComponentStatus("storage", "UP", null, "writable", null, null, 1L, 2L);
        assertEquals("UP", AdminSystemStatusService.overall(List.of(up, mig, mail, storage)));

        var dbDown = new AdminSystemStatusService.ComponentStatus("database", "DOWN", 2000L, "timeout", null, null, null, null);
        assertEquals("DOWN", AdminSystemStatusService.overall(List.of(dbDown, mig, mail, storage)));

        var storageDown = new AdminSystemStatusService.ComponentStatus("storage", "DOWN", null, "not writable", null, null, null, null);
        assertEquals("DEGRADED", AdminSystemStatusService.overall(List.of(up, mig, mail, storageDown)));
    }

    private String adminToken() throws Exception {
        var email = "sys-admin-" + System.nanoTime() + "@example.com";
        var approver = users.save(UserAccount.systemAccount(email, passwords.encode("Password1")));
        platformRoles.save(PlatformRole.grant(approver.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"identity":"%s","password":"Password1"}
            """.formatted(email))).andExpect(status().isOk()).andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }
}
