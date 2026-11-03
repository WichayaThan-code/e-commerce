package com.thaishopfun.auth;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;

/**
 * True only when the database connection itself failed (not SQL/result-shape bugs).
 * {@link DataAccessResourceFailureException} covers {@code CannotGetJdbcConnectionException}.
 */
public final class DatabaseConnectivityFailures {
    private DatabaseConnectivityFailures() {}

    public static boolean isConnectivityFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof DatabaseUnavailableException) return true;
            if (cause instanceof DataAccessResourceFailureException) return true;
            if (cause instanceof TransientDataAccessResourceException) return true;
            if (cause instanceof QueryTimeoutException) return true;
        }
        return false;
    }
}
