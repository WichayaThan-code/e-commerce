package com.thaishopfun.merchant.notification;

import java.util.List;
import java.util.Map;

public final class MerchantNotificationPreferenceDtos {
    private MerchantNotificationPreferenceDtos() {}

    public record ChannelInfo(boolean supported, boolean editable) {}

    public record CategoryInfo(
        String code,
        boolean mandatory,
        boolean defaultInApp,
        boolean inAppEnabled,
        boolean editable,
        String viewerPermission,
        List<String> eventTypes,
        String policyReason,
        boolean viewerHasPermission
    ) {}

    public record PreferencesResponse(
        long shopId,
        String shopName,
        long version,
        Map<String, ChannelInfo> channels,
        List<CategoryInfo> categories
    ) {}

    public record CategoryPatch(String code, Boolean inAppEnabled) {}

    public record SaveRequest(Long version, List<CategoryPatch> categories) {}
}
