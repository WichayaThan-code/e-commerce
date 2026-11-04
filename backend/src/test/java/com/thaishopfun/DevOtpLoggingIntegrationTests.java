package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class DevOtpLoggingIntegrationTests {

    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = "app.auth.dev-log-otp=false")
    static class FlagOff {
        @Autowired MockMvc mvc;
        @Autowired UserAccountRepository users;
        @Autowired RefreshTokenRepository refreshTokens;
        @Autowired OtpVerificationRepository otpVerifications;

        @BeforeEach
        void clean() {
            otpVerifications.deleteAll();
            refreshTokens.deleteAll();
            users.deleteAll();
        }

        @Test
        void sendOtpDoesNotLogPlainDevOtpLine(CapturedOutput output) throws Exception {
            var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"สมชาย","lastName":"ใจดี","email":"dev-otp-off@example.com","phone":"0816000401","password":"Password1"}
                """)).andExpect(status().isCreated()).andReturn();
            String token = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");

            mvc.perform(post("/api/auth/otp/send").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"destination":"dev-otp-off@example.com","purpose":"EMAIL_VERIFY"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpId").isNotEmpty());

            assertFalse(output.getOut().contains("[DEV-OTP]"), output.getOut());
            assertFalse(output.getErr().contains("[DEV-OTP]"), output.getErr());
            assertTrue(output.getOut().contains("OTP issued") || output.getErr().contains("OTP issued"));
        }
    }

    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = "app.auth.dev-log-otp=true")
    static class FlagOn {
        @Autowired MockMvc mvc;
        @Autowired UserAccountRepository users;
        @Autowired RefreshTokenRepository refreshTokens;
        @Autowired OtpVerificationRepository otpVerifications;

        @BeforeEach
        void clean() {
            otpVerifications.deleteAll();
            refreshTokens.deleteAll();
            users.deleteAll();
        }

        @Test
        void sendOtpLogsPlainDevOtpLine(CapturedOutput output) throws Exception {
            var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"สมชาย","lastName":"ใจดี","email":"dev-otp-on@example.com","phone":"0816000402","password":"Password1"}
                """)).andExpect(status().isCreated()).andReturn();
            String token = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");

            var sent = mvc.perform(post("/api/auth/otp/send").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"destination":"dev-otp-on@example.com","purpose":"EMAIL_VERIFY"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpId").isNotEmpty())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andReturn();
            String code = JsonPath.read(sent.getResponse().getContentAsString(), "$.code");
            String otpId = JsonPath.read(sent.getResponse().getContentAsString(), "$.otpId");

            var logs = output.getOut() + output.getErr();
            assertTrue(logs.contains("[DEV-OTP]"), logs);
            assertTrue(logs.contains("code=" + code), logs);
            assertTrue(logs.contains("purpose=EMAIL_VERIFY"), logs);
            assertTrue(logs.contains("channel=EMAIL"), logs);
            assertTrue(logs.contains("destination=dev-otp-on@example.com"), logs);
            assertTrue(logs.contains("id=" + otpId), logs);
            assertTrue(logs.contains("expiresAt="), logs);
            assertTrue(logs.contains("OTP issued"), logs);
            assertTrue(logs.contains("DEV OTP LOGGING ENABLED"), logs);
        }
    }
}
