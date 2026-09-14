package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired MerchantApplicationRepository merchantApplications;
    @Autowired MerchantDocumentRepository merchantDocuments;

    @BeforeEach void clean() {
        merchantDocuments.deleteAll();
        merchantApplications.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test void registerCreatesUserAndAuthenticatedSession() throws Exception {
        var result = register("somchai@example.com", "0812345678")
            .andExpect(status().isCreated())
            .andExpect(cookie().httpOnly("tsf_refresh", true))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
            .andReturn();
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("somchai@example.com"));
    }

    @Test void loginSupportsEmailAndNormalizedPhone() throws Exception {
        register("buyer@example.com", "081-234-5678").andExpect(status().isCreated());
        login("buyer@example.com", "Password1").andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value("buyer@example.com"));
        login("081 234 5678", "Password1").andExpect(status().isOk()).andExpect(jsonPath("$.user.phone").value("0812345678"));
    }

    @Test void refreshRotatesTokenAndLogoutRevokesIt() throws Exception {
        var registered = register("rotate@example.com", "0899999999").andReturn();
        var refresh = registered.getResponse().getCookie("tsf_refresh");
        var refreshed = mvc.perform(post("/api/auth/refresh").cookie(refresh))
            .andExpect(status().isOk()).andExpect(cookie().exists("tsf_refresh")).andReturn();
        var rotated = refreshed.getResponse().getCookie("tsf_refresh");

        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").cookie(rotated)).andExpect(status().isNoContent()).andExpect(cookie().maxAge("tsf_refresh", 0));
        mvc.perform(post("/api/auth/refresh").cookie(rotated)).andExpect(status().isUnauthorized());
    }

    @Test void duplicateAndBadCredentialsReturnSafeErrors() throws Exception {
        register("duplicate@example.com", "0822222222").andExpect(status().isCreated());
        register("duplicate@example.com", "0833333333")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
        login("duplicate@example.com", "wrong-password")
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message", containsString("ไม่ถูกต้อง")));
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String phone) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"สมชาย","lastName":"ใจดี","email":"%s","phone":"%s","password":"Password1"}
            """.formatted(email, phone)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String identity, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"identity":"%s","password":"%s"}
            """.formatted(identity, password)));
    }
}
