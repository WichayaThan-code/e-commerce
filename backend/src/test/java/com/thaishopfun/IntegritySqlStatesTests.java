package com.thaishopfun;

import com.thaishopfun.common.IntegritySqlStates;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegritySqlStatesTests {
    @Test
    void extractsSqlStateAndStripsQuotedValues() {
        var ex = new DataIntegrityViolationException(
            "outer",
            new SQLException("ERROR: value too long for type character varying(50) Detail: Failing row contains ('secret-pii').", "22001"));
        assertEquals("22001", IntegritySqlStates.sqlState(ex));
        String diagnostic = IntegritySqlStates.safeDiagnostic(ex);
        assertTrue(diagnostic.contains("varying(50)"));
        assertFalse(diagnostic.contains("secret-pii"));
        assertTrue(diagnostic.contains("'***'"));
    }

    @Test
    void detectsUniqueAndMentionsTokens() {
        var ex = new DataIntegrityViolationException(
            "outer",
            new SQLException("duplicate key value violates unique constraint \"uq_merchant_applications_shop_name\"", "23505"));
        assertTrue(IntegritySqlStates.isUniqueViolation(ex));
        assertTrue(IntegritySqlStates.mentions(ex, "shop_name", "uq_merchant_applications_shop_name"));
        assertFalse(IntegritySqlStates.mentions(ex, "citizen_id_hash"));
    }
}
