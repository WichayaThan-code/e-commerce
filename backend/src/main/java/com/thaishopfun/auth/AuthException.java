package com.thaishopfun.auth;

import org.springframework.http.HttpStatus;

import java.util.List;

public class AuthException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String field;
    private final List<AuthDtos.ApiFieldError> fieldErrors;

    public AuthException(HttpStatus status, String code, String message) {
        this(status, code, message, null, null);
    }

    public AuthException(HttpStatus status, String code, String message, String field) {
        this(status, code, message, field, null);
    }

    public AuthException(HttpStatus status, String code, String message, List<AuthDtos.ApiFieldError> fieldErrors) {
        this(status, code, message, null, fieldErrors);
    }

    private AuthException(HttpStatus status, String code, String message, String field, List<AuthDtos.ApiFieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
        this.fieldErrors = fieldErrors == null || fieldErrors.isEmpty() ? null : List.copyOf(fieldErrors);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getField() { return field; }
    public List<AuthDtos.ApiFieldError> getFieldErrors() { return fieldErrors; }
}

