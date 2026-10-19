package com.thaishopfun.merchant.theme;

public enum ShopSectionVariant {
    FULL,
    STRIP,
    GRID,
    LARGE,
    MENU;

    public static ShopSectionVariant from(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean allowedFor(ShopSectionKey key) {
        return switch (key) {
            case HERO -> this == FULL || this == STRIP;
            case FEATURED, BESTSELLERS, NEW_ARRIVALS, CATALOG -> this == GRID || this == LARGE || this == MENU;
            case BANNERS, CATEGORIES, TRUST, INTRO, HOURS -> false;
        };
    }
}
