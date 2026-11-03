package com.thaishopfun.auth;

import org.springframework.security.core.AuthenticationException;

/** Raised when auth must hit the database but the connection is down (map to HTTP 503). */
public class DatabaseUnavailableException extends AuthenticationException {
    public DatabaseUnavailableException(Throwable cause) {
        super("Database unavailable", cause);
    }
}
