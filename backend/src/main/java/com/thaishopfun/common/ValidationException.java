package com.thaishopfun.common;

import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Field-level validation failure (HTTP 400). Extends AuthException so existing callers that catch AuthException
 * (e.g. the Excel import, which reports {@link #getMessage()} per row) keep working; the message joins the
 * field messages so a row error stays readable.
 */
public class ValidationException extends AuthException {
    public static final String CODE = "VALIDATION_ERROR";
    private final List<FieldViolation> violations;

    public ValidationException(List<FieldViolation> violations) {
        this(CODE, violations);
    }

    public ValidationException(String code, List<FieldViolation> violations) {
        this(HttpStatus.BAD_REQUEST, code, violations);
    }

    public ValidationException(HttpStatus status, String code, List<FieldViolation> violations) {
        super(status, code, summary(violations), violations == null ? null : violations.stream()
            .map(violation -> new com.thaishopfun.auth.AuthDtos.ApiFieldError(violation.field(), violation.code(), violation.message()))
            .toList());
        this.violations = List.copyOf(violations);
    }

    /** When every problem has the same code (e.g. SKU_CODE_DUPLICATE_IN_FORM) that code is the top-level code; otherwise VALIDATION_ERROR. Used by the product form API (team frontend reads the top-level code). */
    public static String topCode(List<FieldViolation> violations) {
        if (violations == null || violations.isEmpty()) return CODE;
        var codes = violations.stream().map(FieldViolation::code).distinct().toList();
        return codes.size() == 1 && codes.get(0) != null ? codes.get(0) : CODE;
    }

    public List<FieldViolation> getViolations() { return violations; }

    private static String summary(List<FieldViolation> violations) {
        if (violations == null || violations.isEmpty()) return "ข้อมูลไม่ถูกต้อง";
        return violations.stream().map(FieldViolation::message).distinct().limit(5).collect(Collectors.joining(", "));
    }
}
