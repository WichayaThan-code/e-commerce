package com.thaishopfun.merchant.theme;

import java.util.EnumSet;
import java.util.Set;

/** Sections that exist on the public shop page and have backing data. */
public enum ShopSectionKey {
    HERO,
    BANNERS,
    CATEGORIES,
    TRUST,
    INTRO,
    FEATURED,
    BESTSELLERS,
    NEW_ARRIVALS,
    CATALOG,
    HOURS;

    public static final Set<ShopSectionKey> ALL = EnumSet.allOf(ShopSectionKey.class);

    public static ShopSectionKey from(String raw) {
        if (raw == null) return null;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean allowsVariant() {
        return this == HERO || this == FEATURED || this == BESTSELLERS || this == NEW_ARRIVALS || this == CATALOG;
    }
}
