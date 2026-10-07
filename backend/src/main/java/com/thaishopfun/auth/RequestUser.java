package com.thaishopfun.auth;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class RequestUser {
    private RequestUser() {}

    public static UUID id(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }

    public static boolean admin(Jwt jwt) {
        if (jwt == null) return false;
        var roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.stream().anyMatch(role -> "ADMIN".equals(role) || "ROLE_ADMIN".equals(role));
    }
}
