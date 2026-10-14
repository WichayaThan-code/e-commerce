package com.thaishopfun.customer;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Duplicate keys for shop customers.
 * Phone: keep digits only (drops spaces, dashes, dots, brackets, '+'); a Thai country code 66 (from +66 / 0066 / 66)
 * becomes the domestic 0 prefix, also when the user typed "+66 0..." — so "+66 81-234-5678", "66812345678",
 * "081 234 5678" and "081-234-5678" all normalize to 0812345678. Valid result: 0 + 8 or 9 digits.
 * Email: trim + lower-case.
 */
public final class ContactNormalizer {
    private static final Pattern THAI_PHONE = Pattern.compile("^0\\d{8,9}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ContactNormalizer() {}

    /** Normalized phone, or null for blank input. Does not validate (see {@link #isValidPhone}). */
    public static String phone(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        if (digits.startsWith("0066")) digits = digits.substring(4);
        else if (digits.startsWith("66") && digits.length() >= 10) digits = digits.substring(2);
        else return digits;
        return digits.startsWith("0") ? digits : "0" + digits;
    }

    public static boolean isValidPhone(String normalized) {
        return normalized != null && THAI_PHONE.matcher(normalized).matches();
    }

    public static String email(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidEmail(String normalized) {
        return normalized != null && normalized.length() <= 255 && EMAIL.matcher(normalized).matches();
    }
}
