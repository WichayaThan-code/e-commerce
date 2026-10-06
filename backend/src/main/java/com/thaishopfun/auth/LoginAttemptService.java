package com.thaishopfun.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory lockout for wrong passwords. Schema has no failed_attempts/locked_until on users;
 * OTP lockout uses otp_verifications.attempts/max_attempts instead.
 */
@Service
public class LoginAttemptService {
    private record State(int failures, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final SecurityProperties properties;

    public LoginAttemptService(SecurityProperties properties) {
        this.properties = properties;
    }

    public void assertNotLocked(String key) {
        var state = states.get(key);
        if (state == null || state.lockedUntil() == null) return;
        if (state.lockedUntil().isAfter(Instant.now())) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED",
                "บัญชีถูกล็อกชั่วคราวเนื่องจากรหัสผ่านผิดหลายครั้ง กรุณาลองใหม่ภายหลัง");
        }
        states.remove(key, state);
    }

    public void recordFailure(String key) {
        var max = properties.maxLoginAttempts();
        var lockout = Duration.ofMinutes(properties.loginLockoutMinutes());
        states.compute(key, (k, prev) -> {
            var failures = (prev == null || (prev.lockedUntil() != null && prev.lockedUntil().isBefore(Instant.now())))
                ? 1
                : prev.failures() + 1;
            Instant lockedUntil = failures >= max ? Instant.now().plus(lockout) : null;
            return new State(failures, lockedUntil);
        });
        var state = states.get(key);
        if (state != null && state.lockedUntil() != null && state.lockedUntil().isAfter(Instant.now())) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED",
                "บัญชีถูกล็อกชั่วคราวเนื่องจากรหัสผ่านผิดหลายครั้ง กรุณาลองใหม่ภายหลัง");
        }
    }

    public void recordSuccess(String key) {
        states.remove(key);
    }
}