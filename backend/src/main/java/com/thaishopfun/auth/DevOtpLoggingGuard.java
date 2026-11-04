package com.thaishopfun.auth;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

/**
 * Fail-fast if {@code app.auth.dev-log-otp} is enabled under a prod profile;
 * otherwise WARN once at startup when the flag is on.
 */
@Component
public class DevOtpLoggingGuard {
    private static final Logger log = LoggerFactory.getLogger(DevOtpLoggingGuard.class);

    private final AuthProperties auth;
    private final Environment environment;

    public DevOtpLoggingGuard(AuthProperties auth, Environment environment) {
        this.auth = auth;
        this.environment = environment;
    }

    @PostConstruct
    public void check() {
        if (!auth.devLogOtp()) return;
        if (isProdProfile()) {
            throw new IllegalStateException(
                "app.auth.dev-log-otp=true is forbidden when a prod profile is active");
        }
        log.warn("DEV OTP LOGGING ENABLED - do not use in production");
    }

    private boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles())
            .map(p -> p.toLowerCase(Locale.ROOT))
            .anyMatch(p -> p.equals("prod") || p.equals("production"));
    }
}
