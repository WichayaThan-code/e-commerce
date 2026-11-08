package com.thaishopfun.merchant.team;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Invite TTL and resend throttle. Defaults favour short-lived single-use links without a separate billing quota.
 */
@ConfigurationProperties(prefix = "app.shop-team")
public record ShopTeamProperties(
    /** Days until a PENDING invite expires (default 7). */
    int inviteTtlDays,
    /** Minimum seconds between resend/regenerate for the same PENDING invite (default 60). */
    int resendCooldownSeconds,
    /** Max PENDING creates per shop per rolling hour (default 20). */
    int inviteCreatePerHour,
    /** Path appended to app.notification.frontend-url for the accept page. */
    String invitePath
) {
    public ShopTeamProperties {
        if (inviteTtlDays <= 0) inviteTtlDays = 7;
        if (resendCooldownSeconds <= 0) resendCooldownSeconds = 60;
        if (inviteCreatePerHour <= 0) inviteCreatePerHour = 20;
        if (invitePath == null || invitePath.isBlank()) invitePath = "/invite";
    }
}
