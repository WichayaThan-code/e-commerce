package com.thaishopfun.deal;

/** Public /deals query tab keys. */
public enum DealTab {
    TODAY,
    WEEKLY,
    FLASH,
    UNDER199,
    BOGO,
    COUPONS,
    SPECIAL;

    public static DealTab fromParam(String raw) {
        if (raw == null || raw.isBlank()) return TODAY;
        return switch (raw.trim().toLowerCase()) {
            case "today", "ดีลวันนี้" -> TODAY;
            case "weekly", "ดีลทุกสัปดาห์" -> WEEKLY;
            case "flash", "flashsale", "flash-sale" -> FLASH;
            case "under199", "under_199", "199" -> UNDER199;
            case "bogo", "buy1get1" -> BOGO;
            case "coupons", "coupon" -> COUPONS;
            case "special", "specialprice", "special-price" -> SPECIAL;
            default -> throw new IllegalArgumentException(raw);
        };
    }

    public DealCampaignKind campaignKind() {
        return switch (this) {
            case TODAY -> DealCampaignKind.TODAY;
            case WEEKLY -> DealCampaignKind.WEEKLY;
            case FLASH -> DealCampaignKind.FLASH;
            case BOGO -> DealCampaignKind.BOGO;
            default -> null;
        };
    }
}
