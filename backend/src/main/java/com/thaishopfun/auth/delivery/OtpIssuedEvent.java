package com.thaishopfun.auth.delivery;

import com.thaishopfun.auth.OtpChannel;
import com.thaishopfun.auth.OtpPurpose;

import java.util.UUID;

/**
 * Published inside the sendOtp transaction; delivered by {@link AuthNotificationListener} only after
 * commit. Carries the raw code (and optional verify-link URL), so {@link #toString()} masks secrets.
 */
public record OtpIssuedEvent(
    UUID otpId,
    OtpChannel channel,
    OtpPurpose purpose,
    String destination,
    String recipientName,
    String code,
    long ttlMinutes,
    String verifyLinkUrl
) {
    public OtpIssuedEvent(
        UUID otpId, OtpChannel channel, OtpPurpose purpose, String destination,
        String recipientName, String code, long ttlMinutes
    ) {
        this(otpId, channel, purpose, destination, recipientName, code, ttlMinutes, null);
    }

    @Override
    public String toString() {
        return "OtpIssuedEvent[otpId=" + otpId + ", channel=" + channel + ", purpose=" + purpose + "]";
    }
}
