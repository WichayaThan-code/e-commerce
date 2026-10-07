package com.thaishopfun.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security")
public record SecurityProperties(
    String jwtSecret,
    long accessTokenMinutes,
    long refreshTokenDays,
    boolean secureCookies,
    String allowedOrigin,
    long otpTtlMinutes,
    int otpMaxAttempts,
    long otpResendCooldownSeconds,
    int otpMaxPerHour,
    int maxLoginAttempts,
    long loginLockoutMinutes,
    long passwordResetTtlMinutes,
    boolean otpExposeCode
) {}