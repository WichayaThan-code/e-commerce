package com.thaishopfun.merchant.policy;

import java.util.Locale;
import java.util.Optional;

public enum ShopPolicyType {
    SHIPPING,
    RETURNS,
    PRIVACY,
    TERMS;

    public String path() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Accepts lowercase path segments: shipping|returns|privacy|terms. */
    public static Optional<ShopPolicyType> fromPath(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(ShopPolicyType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
