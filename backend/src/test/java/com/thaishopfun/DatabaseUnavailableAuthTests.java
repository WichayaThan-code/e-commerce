package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.support.DatabaseExceptionProbeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Connectivity failures → 503 DATABASE_UNAVAILABLE; other DataAccessException kinds keep prior behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DatabaseExceptionProbeController.class)
class DatabaseUnavailableAuthTests {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformRoleRepository platformRoles;
    @MockitoSpyBean UserAccountRepository users;

    @Test
    void jwtPvLookupCannotGetJdbcConnectionReturns503() throws Exception {
        String token = adminToken();
        doThrow(new CannotGetJdbcConnectionException("simulated", new SQLException("simulated")))
            .when(users).findActivePermissionVersion(any(UUID.class));
        try {
            String body = mvc.perform(get("/api/admin/system/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("ฐานข้อมูลใช้งานไม่ได้"))
                .andReturn().getResponse().getContentAsString();
            assertNoSecrets(body);
        } finally {
            reset(users);
        }
    }

    @Test
    void controllerCannotGetJdbcConnectionReturns503() throws Exception {
        String body = mvc.perform(get("/api/admin/_db-probe/jdbc-down").header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("ฐานข้อมูลใช้งานไม่ได้"))
            .andReturn().getResponse().getContentAsString();
        assertNoSecrets(body);
    }

    @Test
    void emptyResultAndBadSqlGrammarAreNotMappedTo503() throws Exception {
        String token = adminToken();
        String emptyBody = mvc.perform(get("/api/admin/_db-probe/empty-result").header("Authorization", "Bearer " + token))
            .andExpect(status().isInternalServerError())
            .andReturn().getResponse().getContentAsString();
        assertFalse(emptyBody.contains("DATABASE_UNAVAILABLE"));
        assertNoSecrets(emptyBody);

        String badSqlBody = mvc.perform(get("/api/admin/_db-probe/bad-sql").header("Authorization", "Bearer " + token))
            .andExpect(status().isInternalServerError())
            .andReturn().getResponse().getContentAsString();
        assertFalse(badSqlBody.contains("DATABASE_UNAVAILABLE"));
        assertNoSecrets(badSqlBody);
    }

    @Test
    void dataIntegrityWithoutSqlStateReturnsGenericBadRequest() throws Exception {
        String body = mvc.perform(get("/api/admin/_db-probe/integrity").header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DATA_INTEGRITY"))
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("ซ้ำ"));
        assertNoSecrets(body);
    }

    @Test
    void uniqueViolationReturns409Conflict() throws Exception {
        String body = mvc.perform(get("/api/admin/_db-probe/integrity-unique").header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andReturn().getResponse().getContentAsString();
        assertNoSecrets(body);
    }

    @Test
    void valueTooLongReturns400() throws Exception {
        String body = mvc.perform(get("/api/admin/_db-probe/integrity-too-long").header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALUE_TOO_LONG"))
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        assertFalse(body.toLowerCase().contains("varying"));
        assertNoSecrets(body);
    }

    private String adminToken() throws Exception {
        var stamp = String.valueOf(System.nanoTime());
        var email = "db-map-" + stamp + "@example.com";
        var phone = "08" + stamp.substring(Math.max(0, stamp.length() - 8));
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"ผู้","lastName":"ดูแล","email":"%s","phone":"%s","password":"Password1"}
            """.formatted(email, phone))).andExpect(status().isCreated()).andReturn();
        UUID userId = UUID.fromString(JsonPath.read(registered.getResponse().getContentAsString(), "$.user.id"));
        platformRoles.save(PlatformRole.grant(userId, PlatformRole.PLATFORM_ADMIN, null, "test"));
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"identity":"%s","password":"Password1"}
            """.formatted(email))).andExpect(status().isOk()).andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    private static void assertNoSecrets(String body) {
        String lower = body.toLowerCase();
        assertFalse(lower.contains("password"));
        assertFalse(lower.contains("jdbc:"));
        assertFalse(lower.contains("\"host\""));
        assertFalse(lower.contains("connection refused"));
        assertFalse(lower.contains("bad grammar"));
        assertFalse(lower.contains("duplicate key"));
        assertFalse(body.contains("stackTrace"));
    }
}
