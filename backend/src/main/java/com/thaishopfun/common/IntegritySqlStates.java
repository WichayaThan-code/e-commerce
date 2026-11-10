package com.thaishopfun.common;

import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Locale;

/** Helpers for classifying JDBC integrity failures without logging PII values. */
public final class IntegritySqlStates {
    public static final String UNIQUE = "23505";
    public static final String VALUE_TOO_LONG = "22001";
    public static final String NOT_NULL = "23502";
    public static final String FOREIGN_KEY = "23503";

    private IntegritySqlStates() {}

    public static String sqlState(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sql && sql.getSQLState() != null && !sql.getSQLState().isBlank()) {
                return sql.getSQLState();
            }
            current = current.getCause();
        }
        return "";
    }

    public static boolean isUniqueViolation(DataIntegrityViolationException exception) {
        return UNIQUE.equals(sqlState(exception));
    }

    public static boolean mentions(Throwable exception, String... tokens) {
        String haystack = safeDiagnostic(exception).toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (token != null && !token.isBlank() && haystack.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a PII-safe diagnostic fragment (constraint / table / column hints from the SQLException
     * message with quoted values stripped). Suitable for server logs only.
     */
    public static String safeDiagnostic(Throwable exception) {
        Throwable cause = exception instanceof DataIntegrityViolationException dive
            ? dive.getMostSpecificCause()
            : exception;
        String raw = cause == null || cause.getMessage() == null ? "" : cause.getMessage();
        // Strip single-quoted literals that often contain user-entered values.
        return raw.replaceAll("'[^']*'", "'***'");
    }
}
