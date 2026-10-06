package com.thaishopfun.common.notification;

/** Helpers that keep personal data and one-time secrets out of logs. */
public final class Masking {
    private Masking() {}

    /** {@code somchai@example.com -> s***@example.com}. */
    public static String email(String value) {
        if (value == null || value.isBlank()) return "***";
        var at = value.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? value.substring(at) : "");
        return value.charAt(0) + "***" + value.substring(at);
    }

    /** {@code 0812345678 -> 081****78}. */
    public static String phone(String value) {
        if (value == null || value.length() < 6) return "***";
        return value.substring(0, 3) + "****" + value.substring(value.length() - 2);
    }

    /** Masks an OTP code or token completely except for its length / first chars. */
    public static String secret(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.length() <= 8) return "*".repeat(value.length());
        return value.substring(0, 4) + "…(" + value.length() + " chars)";
    }
}
