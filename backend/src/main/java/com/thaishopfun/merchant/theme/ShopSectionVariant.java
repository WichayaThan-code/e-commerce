package com.thaishopfun.merchant.theme;

public enum ShopSectionVariant {
    FULL,
    STRIP,
    GRID,
    LARGE,
    MENU,
    /** AG5: full-bleed image / video / gradient hero with stickers. Allowed for HERO on CUSTOM themes only. */
    MEDIA_FULL,
    /** Hero: copy on one side, media on the other (stacks on mobile). All templates. */
    SPLIT,
    /** Hero: big centred poster typography over the media (GENERAL badges as stickers). All templates. */
    POSTER;

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
            case HERO -> this == FULL || this == STRIP || this == MEDIA_FULL || this == SPLIT || this == POSTER;
            case FEATURED, BESTSELLERS, NEW_ARRIVALS, CATALOG -> this == GRID || this == LARGE || this == MENU;
            case BANNERS, CATEGORIES, TRUST, INTRO, HOURS -> false;
        };
    }
}
