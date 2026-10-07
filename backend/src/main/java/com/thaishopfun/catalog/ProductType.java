package com.thaishopfun.catalog;

public enum ProductType {
    SIMPLE, VARIABLE;

    public static ProductType parse(String value) {
        if (value == null || value.isBlank()) return SIMPLE;
        try {
            return ProductType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
