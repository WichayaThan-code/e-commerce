package com.thaishopfun.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security")
public record SecurityProperties(
    String jwtSecret,
    long accessTokenMinutes,
    long refreshTokenDays,
    boolean secureCookies,
    String allowedOrigin
) {}

