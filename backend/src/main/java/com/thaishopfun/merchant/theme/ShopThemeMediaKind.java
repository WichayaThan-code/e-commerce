package com.thaishopfun.merchant.theme;

import java.util.Locale;

public enum ShopThemeMediaKind {
    IMAGE, VIDEO, FONT;

    public static ShopThemeMediaKind from(String raw) {
        if (raw == null) return null;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
