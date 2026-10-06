package com.thaishopfun.merchant;

import java.util.Locale;

final class ShopSlugs {
    private ShopSlugs() {}

    static String base(String englishName, String shopName, Long shopId) {
        var fromEnglish = latin(englishName);
        if (fromEnglish != null) return fromEnglish;
        var fromName = latin(shopName);
        if (fromName != null) return fromName;
        return "shop-" + shopId;
    }

    private static String latin(String value) {
        if (value == null) return null;
        var slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 60) slug = slug.substring(0, 60).replaceAll("-+$", "");
        return slug.length() >= 2 ? slug : null;
    }
}
