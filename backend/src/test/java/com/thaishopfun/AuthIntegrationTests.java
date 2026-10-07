package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.LoginAttemptService;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
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
    @Autowired ShopReinstatementRequestRepository reinstatements;
    @Autowired ShopMemberRepository shopMembers;
    @Autowired ShopRepository shops;
    @Autowired OwnerAuditLogRepository ownerAudits;
    @Autowired OwnerRepository owners;
    @Autowired LoginAttemptService loginAttempts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clean() {
        reinstatements.deleteAll();
        shopMembers.deleteAll();
        shops.deleteAll();
        merchantDocuments.deleteAll();
        merchantApplications.deleteAll();
        ownerAudits.deleteAll();
        owners.deleteAll();
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

    @Test void lockoutCounterIsSharedBetweenEmailAndPhoneIdentifiers() throws Exception {
        register("shared-lock@example.com", "0811112222").andExpect(status().isCreated());
        login("shared-lock@example.com", "wrong-password").andExpect(status().isUnauthorized());
        login("0811112222", "wrong-password").andExpect(status().isUnauthorized());
        login("SHARED-LOCK@example.com", "wrong-password").andExpect(status().isUnauthorized());
        login("081-111-2222", "wrong-password").andExpect(status().isUnauthorized());
        login("shared-lock@example.com", "wrong-password")
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        login("0811112222", "Password1")
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test void unknownIdentifierFailuresAreBoundedAndStillReturnInvalidCredentials() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("ghost@example.com", "wrong-password")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        login("ghost@example.com", "wrong-password").andExpect(status().isTooManyRequests());
        org.junit.jupiter.api.Assertions.assertTrue(loginAttempts.size() <= 50_000);
    }

    @Test void malformedOrMistypedJsonReturns400ValidationError() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"firstName\": "))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("not json"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/auth/otp/send").contentType(MediaType.APPLICATION_JSON).content("""
            {"destination":"a@example.com","purpose":"NOT_A_PURPOSE"}
            """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content("""
            {"otpId":"not-a-uuid","code":"123456"}
            """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test void changePasswordInvalidatesExistingAccessTokens() throws Exception {
        var registered = register("pv@example.com", "0812223333").andReturn();
        String oldToken = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + oldToken)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"currentPassword":"Password1","newPassword":"Password9"}
                    """)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken)).andExpect(status().isUnauthorized());
        var relogin = login("pv@example.com", "Password9").andExpect(status().isOk()).andReturn();
        String newToken = JsonPath.read(relogin.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newToken)).andExpect(status().isOk());
    }

    @Test void resetPasswordInvalidatesExistingAccessTokens() throws Exception {
        var registered = register("pv-reset@example.com", "0813334444").andReturn();
        String oldToken = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
        var forgot = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"pv-reset@example.com"}
            """)).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(forgot.getResponse().getContentAsString(), "$.resetToken");
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("""
            {"token":"%s","newPassword":"Password2"}
            """.formatted(token))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken)).andExpect(status().isUnauthorized());
    }

    @Test void refreshTokenReuseRevokesTheWholeFamily() throws Exception {
        var registered = register("reuse@example.com", "0814445555").andReturn();
        var original = registered.getResponse().getCookie("tsf_refresh");
        var rotated = mvc.perform(post("/api/auth/refresh").cookie(original))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("tsf_refresh");
        // replaying the already-rotated token is treated as theft ...
        mvc.perform(post("/api/auth/refresh").cookie(original)).andExpect(status().isUnauthorized());
        // ... so the legitimately rotated token is revoked too
        mvc.perform(post("/api/auth/refresh").cookie(rotated)).andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertTrue(refreshTokens.findAll().stream().allMatch(t -> t.isRevoked()));
    }

    @Test void refreshRejectsSuspendedOrDeletedUsers() throws Exception {
        var registered = register("suspended@example.com", "0815556666").andReturn();
        var refresh = registered.getResponse().getCookie("tsf_refresh");
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE email = ?", "suspended@example.com");
        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());

        var other = register("deleted@example.com", "0816667777").andReturn();
        var refresh2 = other.getResponse().getCookie("tsf_refresh");
        String access2 = JsonPath.read(other.getResponse().getContentAsString(), "$.accessToken");
        jdbc.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE email = ?", "deleted@example.com");
        mvc.perform(post("/api/auth/refresh").cookie(refresh2)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access2)).andExpect(status().isUnauthorized());
    }

    @Test void verifyOtpForUnknownDestinationReturnsGenericResponseAndSendsNothing() throws Exception {
        mvc.perform(post("/api/auth/otp/send").contentType(MediaType.APPLICATION_JSON).content("""
            {"destination":"nobody@example.com","purpose":"EMAIL_VERIFY"}
            """)).andExpect(status().isOk())
            .andExpect(jsonPath("$.otpId").isNotEmpty())
            .andExpect(jsonPath("$.channel").value("EMAIL"));
        mvc.perform(post("/api/auth/otp/send").contentType(MediaType.APPLICATION_JSON).content("""
            {"destination":"0899990000","purpose":"PHONE_VERIFY"}
            """)).andExpect(status().isOk())
            .andExpect(jsonPath("$.otpId").isNotEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(0, otpVerifications.count());
    }

    @Test void unauthenticatedRequestsGet401AndErrorDispatchIsPermitted() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
        mvc.perform(get("/error").with(r -> { r.setDispatcherType(jakarta.servlet.DispatcherType.ERROR); return r; }))
            .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(401, result.getResponse().getStatus()));
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