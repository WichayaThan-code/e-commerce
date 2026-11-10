package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OAuthIdentityRepository;
import com.thaishopfun.auth.OAuthProvider;
import com.thaishopfun.auth.OAuthTokenVerifier;
import com.thaishopfun.auth.OAuthVerifiedIdentity;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthAuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired OAuthIdentityRepository identities;

    @MockitoBean OAuthTokenVerifier verifier;

    @BeforeEach void clean() {
        refreshTokens.deleteAll();
        identities.deleteAll();
        users.deleteAll();
    }

    @Test void providersReflectConfiguredIds() throws Exception {
        mvc.perform(get("/api/auth/oauth/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.google").value(true))
            .andExpect(jsonPath("$.facebook").value(false));
    }

    @Test void oauthCreatesNewUserAndSession() throws Exception {
        when(verifier.verify(eq(OAuthProvider.GOOGLE), eq("good-token")))
            .thenReturn(new OAuthVerifiedIdentity("sub-google-1", "oauth.new@example.com", true, "Somchai", "Jaidee"));

        mvc.perform(post("/api/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"idToken":"good-token"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.user.email").value("oauth.new@example.com"))
            .andExpect(jsonPath("$.user.firstName").value("Somchai"))
            .andExpect(jsonPath("$.user.emailVerified").value(true))
            .andExpect(jsonPath("$.user.role").value("CUSTOMER"));

        assertEquals(1, users.count());
        assertEquals(1, identities.count());
        assertTrue(users.findByEmailIgnoreCaseOrPhone("oauth.new@example.com", "oauth.new@example.com").get().getPhone().startsWith("oauth:"));
    }

    @Test void oauthLinksExistingEmailAndLogsIn() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"Existing","lastName":"User","email":"link.me@example.com","phone":"0812000001","password":"Password1"}
                    """))
            .andExpect(status().isCreated());
        assertEquals(1, users.count());

        when(verifier.verify(eq(OAuthProvider.GOOGLE), eq("link-token")))
            .thenReturn(new OAuthVerifiedIdentity("sub-google-link", "link.me@example.com", true, "Other", "Name"));

        var body = mvc.perform(post("/api/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"idToken":"link-token"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("link.me@example.com"))
            .andExpect(jsonPath("$.user.firstName").value("Existing"))
            .andReturn().getResponse().getContentAsString();

        assertEquals(1, users.count());
        assertEquals(1, identities.count());
        String accessToken = JsonPath.read(body, "$.accessToken");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("link.me@example.com"));
    }

    @Test void oauthReusesExistingProviderLink() throws Exception {
        when(verifier.verify(eq(OAuthProvider.GOOGLE), any()))
            .thenReturn(new OAuthVerifiedIdentity("sub-google-reuse", "reuse@example.com", true, "A", "B"));

        mvc.perform(post("/api/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"idToken":"first"}
                    """))
            .andExpect(status().isOk());
        mvc.perform(post("/api/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"idToken":"second"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("reuse@example.com"));

        assertEquals(1, users.count());
        assertEquals(1, identities.count());
    }

    @Test void oauthRejectsMissingEmailOnNewLink() throws Exception {
        when(verifier.verify(eq(OAuthProvider.GOOGLE), eq("no-email")))
            .thenReturn(new OAuthVerifiedIdentity("sub-no-email", null, false, "A", "B"));

        mvc.perform(post("/api/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"idToken":"no-email"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("OAUTH_EMAIL_REQUIRED"));
    }

    @Test void oauthRejectsDisabledProvider() throws Exception {
        mvc.perform(post("/api/auth/oauth/facebook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"idToken":"anything"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("OAUTH_PROVIDER_DISABLED"));
    }

    @Test void oauthRejectsUnknownProvider() throws Exception {
        mvc.perform(post("/api/auth/oauth/twitter")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"idToken":"anything"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PROVIDER"));
    }
}
