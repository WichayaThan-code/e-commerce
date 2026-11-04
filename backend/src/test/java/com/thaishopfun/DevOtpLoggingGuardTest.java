package com.thaishopfun;

import com.thaishopfun.auth.AuthProperties;
import com.thaishopfun.auth.DevOtpLoggingGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevOtpLoggingGuardTest {

    @Test
    void prodProfileWithDevLogOtpFailsFast() {
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        var guard = new DevOtpLoggingGuard(new AuthProperties(true, null), env);
        var ex = assertThrows(IllegalStateException.class, guard::check);
        assertTrue(ex.getMessage().contains("dev-log-otp"));
    }

    @Test
    void productionProfileAliasAlsoFailsFast() {
        var env = new MockEnvironment();
        env.setActiveProfiles("production");
        assertThrows(IllegalStateException.class,
            () -> new DevOtpLoggingGuard(new AuthProperties(true, null), env).check());
    }

    @Test
    void flagOffAllowsProdProfile() {
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertDoesNotThrow(() -> new DevOtpLoggingGuard(new AuthProperties(false, null), env).check());
    }

    @Test
    void flagOnNonProdStarts() {
        var env = new MockEnvironment();
        env.setActiveProfiles("local");
        assertDoesNotThrow(() -> new DevOtpLoggingGuard(new AuthProperties(true, null), env).check());
    }
}
