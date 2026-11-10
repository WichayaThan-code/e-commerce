package com.thaishopfun.auth;

public enum OAuthProvider {
    GOOGLE,
    FACEBOOK;

    public static OAuthProvider fromPath(String value) {
        if (value == null || value.isBlank()) {
            throw new AuthException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PROVIDER", "ผู้ให้บริการเข้าสู่ระบบไม่ถูกต้อง");
        }
        try {
            return OAuthProvider.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new AuthException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PROVIDER", "ผู้ให้บริการเข้าสู่ระบบไม่ถูกต้อง");
        }
    }
}
