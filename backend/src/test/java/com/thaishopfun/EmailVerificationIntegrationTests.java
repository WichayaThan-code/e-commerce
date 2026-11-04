package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.auth.require-email-verification=true")
class EmailVerificationIntegrationTests {
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

    @Test void unverifiedUserCanLoginMeAndOtpButOtherApisReturn403() throws Exception {
        var registered = register("unverified@example.com", "0818000001").andExpect(status().isCreated()).andReturn();
        String access = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
        assertFalse(JsonPath.<Boolean>read(registered.getResponse().getContentAsString(), "$.user.emailVerified"));

        login("unverified@example.com", "Password1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.emailVerified").value(false));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(false));

        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"Password1","newPassword":"Password9"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        mvc.perform(post("/api/auth/otp/send").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"destination":"unverified@example.com","purpose":"EMAIL_VERIFY"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.otpId").isNotEmpty());
    }

    @Test void otpCodeAndVerifyLinkConfirmEmailAndUnlockApis() throws Exception {
        var registered = register("verify-me@example.com", "0818000002").andReturn();
        String access = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");

        var sent = mvc.perform(post("/api/auth/otp/send").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"destination":"verify-me@example.com","purpose":"EMAIL_VERIFY"}
                    """))
            .andExpect(status().isOk()).andReturn();
        String otpId = JsonPath.read(sent.getResponse().getContentAsString(), "$.otpId");
        String code = JsonPath.read(sent.getResponse().getContentAsString(), "$.code");

        mvc.perform(post("/api/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content("""
            {"otpId":"%s","code":"%s"}
            """.formatted(otpId, code)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true));

        assertTrue(users.findByEmailIgnoreCaseOrPhone("verify-me@example.com", "verify-me@example.com").orElseThrow().isEmailVerified());

        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"Password1","newPassword":"Password9"}
                    """))
            .andExpect(status().isOk());
    }

    @Test void verifyLinkWorksOnceAndRejectsReuseWrongExpiredAndSuperseded() throws Exception {
        var registered = register("link@example.com", "0818000003").andReturn();
        String access = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");

        var first = sendEmailVerify(access, "link@example.com").andExpect(status().isOk()).andReturn();
        UUID firstId = UUID.fromString(JsonPath.read(first.getResponse().getContentAsString(), "$.otpId"));
        String token1 = "link-token-one-abcdefghijklmnopqrstuv";
        setLinkToken(firstId, token1);

        mvc.perform(post("/api/auth/otp/verify-link").contentType(MediaType.APPLICATION_JSON).content("""
            {"otpId":"%s","token":"%s"}
            """.formatted(firstId, token1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true));
        assertTrue(users.findByEmailIgnoreCaseOrPhone("link@example.com", "link@example.com").orElseThrow().isEmailVerified());

        mvc.perform(post("/api/auth/otp/verify-link").contentType(MediaType.APPLICATION_JSON).content("""
            {"otpId":"%s","token":"%s"}
            """.formatted(firstId, token1)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_VERIFY_LINK"));

        // Fresh unverified user for remaining link cases
        var other = register("link2@example.com", "0818000004").andReturn();
        String access2 = JsonPath.read(other.getResponse().getContentAsString(), "$.accessToken");
        var second = sendEmailVerify(access2, "link2@example.com").andExpect(status().isOk()).andReturn();
        UUID secondId = UUID.fromString(JsonPath.read(second.getResponse().getContentAsString(), "$.otpId"));
        String token2 = "link-token-two-abcdefghijklmnopqrstuv";
        setLinkToken(secondId, token2);

        mvc.perform(post("/api/auth/otp/verify-link").contentType(MediaType.APPLICATION_JSON).content("""
            {"otpId":"%s","token":"wrong-token-abcdefghijklmnopqrstuvwx"}
            """.formatted(secondId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_VERIFY_LINK"));

        var otp = otpVerifications.findById(secondId).orElseThrow();
        otp.markExpired();
        otpVerifications.save(otp);
        mvc.perform(post("/api/auth/otp/verify-link").contentType(MediaType.APPLICATION_JSON).content("""
            {"otpId":"%s","token":"%s"}
            """.formatted(secondId, token2)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_VERIFY_LINK"));

        var thirdUser = register("link3@example.com", "0818000005").andReturn();
        String access3 = JsonPath.read(thirdUser.getResponse().getContentAsString(), "$.accessToken");
        var oldSend = sendEmailVerify(access3, "link3@example.com").andExpect(status().isOk()).andReturn();
        UUID oldId = UUID.fromString(JsonPath.read(oldSend.getResponse().getContentAsString(), "$.otpId"));
        String oldToken = "link-token-old-abcdefghijklmnopqrstuvw";
        setLinkToken(oldId, oldToken);
        Thread.sleep(1100); // past test otp-resend-cooldown-seconds
        sendEmailVerify(access3, "link3@example.com").andExpect(status().isOk()); // supersedes old pending

        mvc.perform(post("/api/auth/otp/verify-link").contentType(MediaType.APPLICATION_JSON).content("""
            {"otpId":"%s","token":"%s"}
            """.formatted(oldId, oldToken)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_VERIFY_LINK"));
        assertFalse(users.findByEmailIgnoreCaseOrPhone("link3@example.com", "link3@example.com").orElseThrow().isEmailVerified());
    }

    @Test void verifiedAndAdminUsersAreUnaffected() throws Exception {
        var registered = register("already@example.com", "0818000006").andReturn();
        String access = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
        var user = users.findByEmailIgnoreCaseOrPhone("already@example.com", "already@example.com").orElseThrow();
        user.markEmailVerified();
        users.save(user);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(true));
        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"Password1","newPassword":"Password9"}
                    """))
            .andExpect(status().isOk());
    }

    private ResultActions sendEmailVerify(String access, String email) throws Exception {
        return mvc.perform(post("/api/auth/otp/send").header("Authorization", "Bearer " + access)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"destination":"%s","purpose":"EMAIL_VERIFY"}
                """.formatted(email)));
    }

    private void setLinkToken(UUID otpId, String rawToken) {
        var otp = otpVerifications.findById(otpId).orElseThrow();
        otp.assignVerifyLinkTokenHash(sha256(rawToken));
        otpVerifications.save(otp);
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

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
