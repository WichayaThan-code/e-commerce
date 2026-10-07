package com.thaishopfun.catalog;

public enum ProductStatus {
    DRAFT, PUBLISHED, ARCHIVED;

    public static ProductStatus parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ProductStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
