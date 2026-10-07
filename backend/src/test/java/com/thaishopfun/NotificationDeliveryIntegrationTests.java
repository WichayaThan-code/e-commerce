package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.AuthDtos;
import com.thaishopfun.auth.AuthService;
import com.thaishopfun.auth.OtpPurpose;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.common.notification.EmailMessage;
import com.thaishopfun.common.notification.EmailSender;
import com.thaishopfun.common.notification.SmsMessage;
import com.thaishopfun.common.notification.SmsSender;
import com.thaishopfun.merchant.MerchantApplicationRepository;
import com.thaishopfun.merchant.MerchantDocumentRepository;
import com.thaishopfun.merchant.OwnerAuditLogRepository;
import com.thaishopfun.merchant.OwnerRepository;
import com.thaishopfun.merchant.ShopMemberRepository;
import com.thaishopfun.merchant.ShopReinstatementRequestRepository;
import com.thaishopfun.merchant.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** OTP / password-reset delivery: after-commit, async, right channel, nothing on unknown destinations or rollback. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationDeliveryIntegrationTests {
    private static final long WAIT_MS = 3000;

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired PlatformTransactionManager transactionManager;
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

    @MockitoBean EmailSender emailSender;
    @MockitoBean SmsSender smsSender;

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
        reset(emailSender, smsSender);
        when(emailSender.provider()).thenReturn("mock");
        when(smsSender.provider()).thenReturn("mock");
    }

    @Test void emailVerifyOtpSendsExactlyOneThaiEmailWithTheCodeAndCodeVerifies() throws Exception {
        String accessToken = JsonPath.read(register("deliver@example.com", "0861111111").andReturn().getResponse().getContentAsString(), "$.accessToken");
        var sent = mvc.perform(post("/api/auth/otp/send").header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"destination":"Deliver@Example.com","purpose":"EMAIL_VERIFY"}
                    """))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String otpId = JsonPath.read(sent, "$.otpId");
        String exposedCode = JsonPath.read(sent, "$.code");

        var captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, timeout(WAIT_MS).times(1)).send(captor.capture());
        var email = captor.getValue();
        assertEquals("deliver@example.com", email.to());
        assertEquals("สมชาย", email.toName());
        assertTrue(email.subject().contains("ยืนยันอีเมล"), email.subject());
        var codeInMail = extractCode(email.textBody());
        assertEquals(exposedCode, codeInMail);
        assertTrue(email.htmlBody().contains(codeInMail));
        assertTrue(email.textBody().contains("5 นาที"));
        verifyNoInteractions(smsSender);
        assertFalse(email.toString().contains(codeInMail), "toString must not leak the code");

        mvc.perform(post("/api/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content("""
            {"otpId":"%s","code":"%s"}
            """.formatted(otpId, codeInMail))).andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
    }

    @Test void signupOtpIsEmailedToTheDestination() throws Exception {
        mvc.perform(post("/api/auth/otp/send").contentType(MediaType.APPLICATION_JSON).content("""
            {"destination":"newbie@example.com","purpose":"SIGNUP"}
            """)).andExpect(status().isOk());
        var captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, timeout(WAIT_MS).times(1)).send(captor.capture());
        assertEquals("newbie@example.com", captor.getValue().to());
        assertTrue(captor.getValue().subject().contains("สมัครสมาชิก"));
        assertNotNull(extractCode(captor.getValue().textBody()));
    }

    @Test void unknownDestinationSendsNothing() throws Exception {
        mvc.perform(post("/api/auth/otp/send").contentType(MediaType.APPLICATION_JSON).content("""
            {"destination":"nobody@example.com","purpose":"EMAIL_VERIFY"}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.otpId").isNotEmpty());
        mvc.perform(post("/api/auth/otp/send").contentType(MediaType.APPLICATION_JSON).content("""
            {"destination":"0899990000","purpose":"PHONE_VERIFY"}
            """)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"missing@example.com"}
            """)).andExpect(status().isOk());
        verify(emailSender, after(800).never()).send(any());
        verify(smsSender, never()).send(any());
    }

    @Test void forgotPasswordEmailsResetLinkWhoseTokenResetsThePassword() throws Exception {
        register("forgot@example.com", "0862222222").andExpect(status().isCreated());
        var body = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"forgot@example.com"}
            """)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String exposedToken = JsonPath.read(body, "$.resetToken");

        var captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, timeout(WAIT_MS).times(1)).send(captor.capture());
        var email = captor.getValue();
        assertEquals("forgot@example.com", email.to());
        assertTrue(email.subject().contains("รีเซ็ตรหัสผ่าน"));
        var link = "http://localhost:5173/reset-password?token=" + exposedToken;
        assertTrue(email.textBody().contains(link), email.textBody());
        assertTrue(email.htmlBody().contains(link));

        var tokenFromMail = email.textBody().substring(email.textBody().indexOf("?token=") + 7).split("\\s")[0];
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("""
            {"token":"%s","newPassword":"Password2"}
            """.formatted(tokenFromMail))).andExpect(status().isOk());
    }

    @Test void passwordResetOtpIsEmailed() throws Exception {
        register("otpreset@example.com", "0863333333").andExpect(status().isCreated());
        mvc.perform(post("/api/auth/otp/send").contentType(MediaType.APPLICATION_JSON).content("""
            {"destination":"otpreset@example.com","purpose":"PASSWORD_RESET"}
            """)).andExpect(status().isOk());
        var captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, timeout(WAIT_MS).times(1)).send(captor.capture());
        assertTrue(captor.getValue().subject().contains("รีเซ็ตรหัสผ่าน"));
        assertNotNull(extractCode(captor.getValue().textBody()));
    }

    @Test void phoneVerifyUsesSmsSenderNotEmail() throws Exception {
        String accessToken = JsonPath.read(register("sms@example.com", "081-444-5555").andReturn().getResponse().getContentAsString(), "$.accessToken");
        var sent = mvc.perform(post("/api/auth/otp/send").header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"destination":"0814445555","purpose":"PHONE_VERIFY"}
                    """))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(sent, "$.code");
        var captor = ArgumentCaptor.forClass(SmsMessage.class);
        verify(smsSender, timeout(WAIT_MS).times(1)).send(captor.capture());
        assertEquals("0814445555", captor.getValue().to());
        assertTrue(captor.getValue().text().contains(code));
        verify(emailSender, never()).send(any());
    }

    @Test void deliveryHappensOnlyAfterCommitAndOffTheCallerThread() throws Exception {
        register("aftercommit@example.com", "0866666660").andExpect(status().isCreated());
        var callerThread = Thread.currentThread().getName();
        var senderThread = new AtomicReference<String>();
        doAnswer(invocation -> { senderThread.set(Thread.currentThread().getName()); return null; })
            .when(emailSender).send(any());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            authService.sendOtp(new AuthDtos.SendOtpRequest("aftercommit@example.com", OtpPurpose.PASSWORD_RESET), null);
            // still inside the transaction: nothing may be sent yet
            verify(emailSender, after(500).never()).send(any());
        });
        verify(emailSender, timeout(WAIT_MS).times(1)).send(any());
        assertNotNull(senderThread.get());
        assertNotEquals(callerThread, senderThread.get());
        assertTrue(senderThread.get().startsWith("notify-"), senderThread.get());
    }

    @Test void rollbackSendsNothing() throws Exception {
        register("rollback@example.com", "0867777770").andExpect(status().isCreated());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            authService.sendOtp(new AuthDtos.SendOtpRequest("rollback@example.com", OtpPurpose.PASSWORD_RESET), null);
            authService.forgotPassword(new AuthDtos.ForgotPasswordRequest("rollback@example.com"));
            status.setRollbackOnly();
        });
        verify(emailSender, after(1000).never()).send(any());
        verify(smsSender, never()).send(any());
        assertEquals(0, otpVerifications.count());
    }

    @Test void deliveryFailureDoesNotChangeTheApiResponse() throws Exception {
        register("fail@example.com", "0868888880").andExpect(status().isCreated());
        doThrow(new com.thaishopfun.common.notification.NotificationException("smtp down", null)).when(emailSender).send(any());
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"fail@example.com"}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.message").isNotEmpty());
        verify(emailSender, timeout(WAIT_MS).times(1)).send(any());
    }

    private static String extractCode(String text) {
        var matcher = java.util.regex.Pattern.compile("(\\d{6})").matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private ResultActions register(String email, String phone) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"สมชาย","lastName":"ใจดี","email":"%s","phone":"%s","password":"Password1"}
            """.formatted(email, phone)));
    }
}
