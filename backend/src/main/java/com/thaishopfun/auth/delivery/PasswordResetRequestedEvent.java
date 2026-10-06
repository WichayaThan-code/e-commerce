package com.thaishopfun.auth.delivery;

import java.util.UUID;

/**
 * Published inside the forgot-password transaction; delivered after commit. Carries the raw reset
 * token, so {@link #toString()} masks it.
 */
public record PasswordResetRequestedEvent(
    UUID userId,
    String email,
    String recipientName,
    String token,
    long ttlMinutes
) {
    @Override
    public String toString() {
        return "PasswordResetRequestedEvent[userId=" + userId + "]";
    }
}
