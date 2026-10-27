package com.thaishopfun.merchant.theme;

/**
 * AG4: every type a CUSTOM {@code layout.blocks[]} entry may have. The first ten are exactly the legacy
 * {@link ShopSectionKey}s (kept in sync with the {@code sections} list); the rest are block-only content sections that
 * never appear in {@code config.sections}, so GENERAL / FASHION / FOOD_BEVERAGE templates are unaffected.
 */
public enum ShopBlockType {
    HERO, BANNERS, CATEGORIES, TRUST, INTRO, FEATURED, BESTSELLERS, NEW_ARRIVALS, CATALOG, HOURS,
    STEPS, FEATURE, GALLERY, SOCIAL, RICH_TEXT,
    /** V41: cards of the shop's published collections (content: title, subtitle, collectionSlugs[], limit). */
    COLLECTIONS;

    public static ShopBlockType from(String raw) {
        if (raw == null) return null;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** The legacy section this block type maps to, or {@code null} for block-only content types. */
    public ShopSectionKey sectionKey() {
        return ShopSectionKey.from(name());
    }

    /** AG4 content types (carry a {@code content} payload and are never part of {@code sections}). */
    public boolean hasContent() {
        return sectionKey() == null;
    }

    public boolean isSingleton() {
        return this == HERO || this == HOURS;
    }
}
