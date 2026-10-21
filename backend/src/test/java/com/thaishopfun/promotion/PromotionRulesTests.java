package com.thaishopfun.promotion;

import com.thaishopfun.common.FieldViolation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Pure rules: status precedence and Asia/Bangkok date parsing. */
class PromotionRulesTests {
    private static final Instant NOW = Instant.parse("2026-10-19T05:00:00Z"); // 12:00 Bangkok

    @Test
    void statusPrecedenceExpiredThenPausedThenUpcomingThenActive() {
        var past = NOW.minusSeconds(3600);
        var future = NOW.plusSeconds(3600);
        assertEquals(PromotionStatus.ACTIVE, PromotionStatus.of(true, past, future, NOW));
        assertEquals(PromotionStatus.ACTIVE, PromotionStatus.of(true, past, null, NOW));
        assertEquals(PromotionStatus.ACTIVE, PromotionStatus.of(true, NOW, future, NOW), "starts exactly now");
        assertEquals(PromotionStatus.UPCOMING, PromotionStatus.of(true, future, null, NOW));
        assertEquals(PromotionStatus.PAUSED, PromotionStatus.of(false, past, future, NOW));
        assertEquals(PromotionStatus.PAUSED, PromotionStatus.of(false, future, null, NOW), "paused wins over upcoming");
        assertEquals(PromotionStatus.EXPIRED, PromotionStatus.of(false, past, past, NOW), "expired wins over paused");
        assertEquals(PromotionStatus.EXPIRED, PromotionStatus.of(true, past, NOW, NOW), "ends exactly now");
    }

    @Test
    void localDateTimesAreBangkokAndOffsetsAreRespected() {
        var violations = new ArrayList<FieldViolation>();
        assertEquals(Instant.parse("2026-11-10T17:00:00Z"), PromotionService.parseInstant("2026-11-11T00:00", "startsAt", true, violations));
        assertEquals(Instant.parse("2026-11-10T17:00:00Z"), PromotionService.parseInstant("2026-11-11T00:00:00+07:00", "startsAt", true, violations));
        assertEquals(Instant.parse("2026-11-11T00:00:00Z"), PromotionService.parseInstant("2026-11-11T00:00:00Z", "startsAt", true, violations));
        assertTrue(violations.isEmpty());
        assertNull(PromotionService.parseInstant(" ", "endsAt", false, violations));
        assertTrue(violations.isEmpty());
        assertNull(PromotionService.parseInstant(null, "startsAt", true, violations));
        assertNull(PromotionService.parseInstant("11/11/2026", "endsAt", false, violations));
        assertEquals("STARTS_AT_REQUIRED", violations.get(0).code());
        assertEquals("DATE_INVALID", violations.get(1).code());
        assertEquals("endsAt", violations.get(1).field());
    }
}