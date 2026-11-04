package com.thaishopfun.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Auth-side toggles under {@code app.auth.*}.
 * {@code devLogOtp} must stay false in production — see {@link DevOtpLoggingGuard}.
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(
    /** When true, logs the plaintext OTP at WARN as {@code [DEV-OTP]} (local/dev only). */
    boolean devLogOtp,
    /**
     * When true (default), authenticated callers with {@code email_verified_at == null} get 403
     * {@code EMAIL_NOT_VERIFIED} on non-exempt APIs. Integration tests can disable this flag.
     */
    Boolean requireEmailVerification
) {
    public boolean emailVerificationRequired() {
        return requireEmailVerification == null || requireEmailVerification;
    }
}
