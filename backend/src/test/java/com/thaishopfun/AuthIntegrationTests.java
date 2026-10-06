package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordResetTokenRepository passwordResetTokens;
    @Autowired OtpVerificationRepository otpVerifications;
    @Autowired MerchantApplicationRepository merchantApplications;
    @Autowired MerchantDocumentRepository merchantDocuments;

    @BeforeEach void clean() {
        merchantDocuments.deleteAll();
        merchantApplications.deleteAll();
        otpVerifications.deleteAll();
        passwordResetTokens.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test void registerCreatesUserAndAuthenticatedSession() throws Exception {
        var result = register("somchai@example.com", "0812345678")
            .andExpect(status().isCreated())
            .andExpect(cookie().httpOnly("tsf_refresh", true))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.user.emailVerified").value(false))
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

    @Test void loginLockoutAfterRepeatedFailures() throws Exception {
        register("lock@example.com", "0844444444").andExpect(status().isCreated());
        for (int i = 0; i < 4; i++) {
            login("lock@example.com", "wrong-password").andExpect(status().isUnauthorized());
        }
        login("lock@example.com", "wrong-password")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        login("lock@example.com", "Password1")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test void forgotAndResetPasswordRevokesRefreshTokens() throws Exception {
        var registered = register("reset@example.com", "0855555555").andReturn();
        var refresh = registered.getResponse().getCookie("tsf_refresh");

        var forgot = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"reset@example.com"}
            """)).andExpect(status().isOk())
            .andExpect(jsonPath("$.resetToken").isNotEmpty())
            .andReturn();
        String token = JsonPath.read(forgot.getResponse().getContentAsString(), "$.resetToken");

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("""
            {"token":"%s","newPassword":"Password2"}
            """.formatted(token))).andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        login("reset@example.com", "Password1").andExpect(status().isUnauthorized());
        login("reset@example.com", "Password2").andExpect(status().isOk());
    }

    @Test void emailOtpVerifyAndWrongOtpLockout() throws Exception {
        var registered = register("otp@example.com", "0866666666").andReturn();
        String accessToken = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");

        var sent = mvc.perform(post("/api/auth/otp/send")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"destination":"otp@example.com","purpose":"EMAIL_VERIFY"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.otpId").isNotEmpty())
            .andExpect(jsonPath("$.code").isNotEmpty())
            .andReturn();
        String otpId = JsonPath.read(sent.getResponse().getContentAsString(), "$.otpId");
        String code = JsonPath.read(sent.getResponse().getContentAsString(), "$.code");

        mvc.perform(post("/api/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content("""
            {"otpId":"%s","code":"%s"}
            """.formatted(otpId, code)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true))
            .andExpect(jsonPath("$.purpose").value("EMAIL_VERIFY"));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(true));

        var locked = mvc.perform(post("/api/auth/otp/send")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"destination":"otp@example.com","purpose":"EMAIL_VERIFY"}
                    """))
            .andExpect(status().isOk()).andReturn();
        String otpId2 = JsonPath.read(locked.getResponse().getContentAsString(), "$.otpId");
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content("""
                {"otpId":"%s","code":"000000"}
                """.formatted(otpId2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));
        }
        mvc.perform(post("/api/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content("""
            {"otpId":"%s","code":"000000"}
            """.formatted(otpId2)))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("OTP_LOCKED"));
    }

    @Test void changePasswordRevokesRefreshTokens() throws Exception {
        var registered = register("change@example.com", "0877777777").andReturn();
        String accessToken = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
        var refresh = registered.getResponse().getCookie("tsf_refresh");

        mvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"Password1","newPassword":"Password9"}
                    """))
            .andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        login("change@example.com", "Password9").andExpect(status().isOk());
    }

    @Test void forgotPasswordDoesNotRevealMissingEmail() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"missing@example.com"}
            """)).andExpect(status().isOk())
            .andExpect(jsonPath("$.message", containsString("หากอีเมล")))
            .andExpect(jsonPath("$.resetToken", nullValue()));
    }

    private ResultActions register(String email, String phone) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"สมชาย","lastName":"ใจดี","email":"%s","phone":"%s","password":"Password1"}
            """.formatted(email, phone)));
    }

    private ResultActions login(String identity, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"identity":"%s","password":"%s"}
            """.formatted(identity, password)));
    }
}