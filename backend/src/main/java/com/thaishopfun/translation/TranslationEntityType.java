package com.thaishopfun.translation;

public enum TranslationEntityType {
    PRODUCT,
    PRODUCT_OPTION,
    PRODUCT_CATEGORY,
    SHOP;

    public String wire() {
        return name();
    }

    public static TranslationEntityType parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
