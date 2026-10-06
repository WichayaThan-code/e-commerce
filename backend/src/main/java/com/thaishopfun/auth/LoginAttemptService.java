package com.thaishopfun.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory lockout for wrong passwords (schema has no failed_attempts/locked_until on users;
 * OTP lockout uses otp_verifications.attempts/max_attempts instead).
 *
 * Counters are keyed by the resolved user id, so email and phone logins of the same account share
 * one counter. Identifiers that do not resolve to an account are counted under their normalized
 * identifier so unknown accounts lock exactly like real ones (no enumeration signal).
 *
 * Memory is bounded: entries expire (lock end, or lockout window after the last failure) and are
 * purged opportunistically; above {@link #MAX_ENTRIES} unknown-identifier entries are dropped first.
 */
@Service
public class LoginAttemptService {
    static final int MAX_ENTRIES = 50_000;
    private static final long CLEANUP_INTERVAL_MILLIS = 60_000;

    private record State(int failures, Instant lockedUntil, Instant expiresAt) {
        boolean expired(Instant now) { return !expiresAt.isAfter(now); }
        boolean locked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }
    }

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());
    private final SecurityProperties properties;

    public LoginAttemptService(SecurityProperties properties) {
        this.properties = properties;
    }

    public static String userKey(UUID userId) { return "u:" + userId; }

    public static String identifierKey(String identifier) {
        return "i:" + (identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT));
    }

    public void assertNotLocked(String key) {
        var now = Instant.now();
        var state = states.get(key);
        if (state == null) return;
        if (state.locked(now)) throw locked();
        if (state.expired(now)) states.remove(key, state);
    }

    public void recordFailure(String key) {
        var now = Instant.now();
        var max = Math.max(1, properties.maxLoginAttempts());
        var window = Duration.ofMinutes(properties.loginLockoutMinutes());
        var state = states.compute(key, (k, prev) -> {
            var failures = (prev == null || prev.expired(now)) ? 1 : prev.failures() + 1;
            var lockedUntil = failures >= max ? now.plus(window) : null;
            return new State(failures, lockedUntil, now.plus(window));
        });
        maybeCleanup();
        if (state.locked(now)) throw locked();
    }

    public void recordSuccess(String key) {
        states.remove(key);
    }

    public void clearUser(UUID userId) {
        states.remove(userKey(userId));
    }

    public int size() { return states.size(); }

    private void maybeCleanup() {
        var nowMillis = System.currentTimeMillis();
        var last = lastCleanup.get();
        var due = nowMillis - last >= CLEANUP_INTERVAL_MILLIS;
        if (!due && states.size() <= MAX_ENTRIES) return;
        if (due && !lastCleanup.compareAndSet(last, nowMillis)) return;
        var now = Instant.now();
        states.entrySet().removeIf(entry -> entry.getValue().expired(now));
        if (states.size() > MAX_ENTRIES) {
            states.entrySet().removeIf(entry -> entry.getKey().startsWith("i:") && !entry.getValue().locked(now));
        }
    }

    private AuthException locked() {
        return new AuthException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED",
            "บัญชีถูกล็อกชั่วคราวเนื่องจากรหัสผ่านผิดหลายครั้ง กรุณาลองใหม่ภายหลัง");
    }
}
