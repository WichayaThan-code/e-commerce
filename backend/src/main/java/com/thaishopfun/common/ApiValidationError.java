package com.thaishopfun.common;

import java.util.List;

/**
 * 400 body for validation failures. Same {@code code}/{@code message} shape as every other API error
 * (AuthDtos.ApiError) plus the list of field-level problems.
 */
public record ApiValidationError(String code, String message, List<FieldViolation> fieldErrors) {}
