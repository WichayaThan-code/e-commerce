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
        super(HttpStatus.BAD_REQUEST, code, summary(violations));
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() { return violations; }

    private static String summary(List<FieldViolation> violations) {
        if (violations == null || violations.isEmpty()) return "ข้อมูลไม่ถูกต้อง";
        return violations.stream().map(FieldViolation::message).distinct().limit(5).collect(Collectors.joining(", "));
    }
}
