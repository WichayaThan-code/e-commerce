package com.thaishopfun.common;

/**
 * One field-level validation problem. {@code field} uses the same dotted path the frontend form uses
 * (e.g. {@code nameTh}, {@code skus.0.price}); {@code code} is a stable machine code; {@code message} is Thai text.
 */
public record FieldViolation(String field, String code, String message) {}
