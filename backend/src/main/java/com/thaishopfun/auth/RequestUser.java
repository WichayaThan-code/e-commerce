package com.thaishopfun.auth;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class RequestUser {
    private RequestUser() {}

    public static UUID id(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }
}
