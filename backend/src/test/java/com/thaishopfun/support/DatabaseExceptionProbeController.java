package com.thaishopfun.support;

import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

/**
 * Test-only probes imported by {@link com.thaishopfun.DatabaseUnavailableAuthTests}.
 * Not component-scanned in production.
 */
@RestController
@RequestMapping("/api/admin/_db-probe")
public class DatabaseExceptionProbeController {
    @RequirePermission("PLATFORM_SYSTEM_READ")
    @GetMapping("/empty-result")
    public void emptyResult() {
        throw new EmptyResultDataAccessException(1);
    }

    @RequirePermission("PLATFORM_SYSTEM_READ")
    @GetMapping("/bad-sql")
    public void badSql() {
        throw new BadSqlGrammarException("select", "select 1", new SQLException("bad grammar"));
    }

    @RequirePermission("PLATFORM_SYSTEM_READ")
    @GetMapping("/jdbc-down")
    public void jdbcDown() {
        throw new CannotGetJdbcConnectionException("connection refused", new SQLException("connection refused"));
    }

    @RequirePermission("PLATFORM_SYSTEM_READ")
    @GetMapping("/integrity")
    public void integrity() {
        throw new DataIntegrityViolationException("integrity", new SQLException("generic integrity", "99999"));
    }

    @RequirePermission("PLATFORM_SYSTEM_READ")
    @GetMapping("/integrity-unique")
    public void integrityUnique() {
        throw new DataIntegrityViolationException(
            "unique",
            new SQLException("duplicate key value violates unique constraint \"uq_demo\"", "23505"));
    }

    @RequirePermission("PLATFORM_SYSTEM_READ")
    @GetMapping("/integrity-too-long")
    public void integrityTooLong() {
        throw new DataIntegrityViolationException(
            "too long",
            new SQLException("value too long for type character varying(50)", "22001"));
    }
}
