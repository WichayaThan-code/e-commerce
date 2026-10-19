package com.thaishopfun.merchant.theme;

public enum ShopThemeFont {
    NOTO_SANS_THAI,
    NOTO_SERIF_THAI,
    PROMPT,
    MITR,
    IBM_PLEX_SANS_THAI;

    public static ShopThemeFont from(String raw) {
        if (raw == null) return null;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
