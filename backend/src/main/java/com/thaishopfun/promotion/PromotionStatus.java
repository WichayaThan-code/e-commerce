package com.thaishopfun.promotion;

import java.time.Instant;

/**
 * Status shown to merchants, computed (never stored) from is_active and the start/end instants.
 * Precedence: EXPIRED (ended) &gt; PAUSED (is_active = false) &gt; UPCOMING (not started) &gt; ACTIVE.
 * Instants are absolute; the API parses/prints merchant-entered local times in Asia/Bangkok.
 */
public enum PromotionStatus {
    UPCOMING, ACTIVE, PAUSED, EXPIRED;

    public static PromotionStatus of(boolean active, Instant startsAt, Instant endsAt, Instant now) {
        if (endsAt != null && !now.isBefore(endsAt)) return EXPIRED;
        if (!active) return PAUSED;
        if (startsAt != null && now.isBefore(startsAt)) return UPCOMING;
        return ACTIVE;
    }
}
