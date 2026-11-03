package com.thaishopfun.admin;

/**
 * Contact masking for platform-admin API responses (store list / shop owner refs).
 * Phone: {@code 0812345678} / {@code +66812345678} → {@code 08x-xxx-5678} (last 4 digits only).
 * Email: {@code somchai@example.com} → {@code s***@example.com}.
 */
public final class AdminContactMask {
    private AdminContactMask() {}

    /** Digits only; {@code 66…} / {@code +66…} → leading {@code 0}. */
    public static String normalizePhoneDigits(String value) {
        if (value == null) return "";
        var digits = value.replaceAll("\\D", "");
        if (digits.startsWith("66") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }

    public static String phone(String value) {
        if (value == null || value.isBlank()) return value;
        var digits = normalizePhoneDigits(value);
        if (digits.length() < 4) return "****";
        var last4 = digits.substring(digits.length() - 4);
        if (digits.length() >= 10) return digits.substring(0, 2) + "x-xxx-" + last4;
        if (digits.length() >= 7) return "xxx-xxx-" + last4;
        return "••••" + last4;
    }

    public static String email(String value) {
        if (value == null || value.isBlank()) return value;
        var at = value.indexOf('@');
        if (at <= 0) return "***";
        var local = value.substring(0, at);
        var domain = value.substring(at);
        if (local.length() == 1) return "*" + domain;
        return local.charAt(0) + "***" + domain;
    }
}
