package com.thaishopfun.common;

/**
 * One field-level validation problem. {@code field} uses bracket indexes
 * (e.g. {@code nameTh}, {@code skus[0].skuCode}, {@code optionGroups[0].values[1].value}, {@code optionSchema[0].name});
 * {@code code} is a stable machine code; {@code message} is Thai text.
 */
public record FieldViolation(String field, String code, String message) {}
