package com.thaishopfun.translation;

import java.util.Locale;

public final class ContentLocale {
    private ContentLocale() {}

    public static String resolve(String langQuery, String acceptLanguage) {
        if (langQuery != null && !langQuery.isBlank()) {
            return normalize(langQuery);
        }
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return "th";
        }
        var parts = acceptLanguage.split(",");
        for (var part : parts) {
            var token = part.split(";")[0].trim();
            if (token.isEmpty()) continue;
            var normalized = normalize(token);
            if (!"th".equals(normalized)) {
                return normalized;
            }
        }
        return "th";
    }

    public static String normalize(String value) {
        var lower = value.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("en")) return "en";
        if (lower.startsWith("zh")) return "zh";
        return "th";
    }

    public static String targetCode(String locale) {
        return switch (locale) {
            case "en" -> "en";
            case "zh" -> "zh-CN";
            default -> "th";
        };
    }
}
