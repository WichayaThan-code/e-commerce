package com.thaishopfun.merchant.theme;

public enum ShopThemeTemplate {
    GENERAL,
    FASHION,
    FOOD_BEVERAGE;

    public static ShopThemeTemplate from(String raw) {
        if (raw == null) return null;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
