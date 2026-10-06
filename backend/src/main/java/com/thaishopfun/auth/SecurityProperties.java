package com.thaishopfun.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security")
public record SecurityProperties(
    String jwtSecret,
    long accessTokenMinutes,
    long refreshTokenDays,
    boolean secureCookies,
<<<<<<< HEAD
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
=======
    String allowedOrigin
) {}

>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
