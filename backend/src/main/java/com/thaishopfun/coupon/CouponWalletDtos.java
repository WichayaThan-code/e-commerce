package com.thaishopfun.coupon;

import java.math.BigDecimal;
import java.time.Instant;

/** Buyer coupon wallet shapes. shopId = public shop number; type = PERCENT | FIXED_AMOUNT | FREE_SHIPPING (same as public promotions). */
public final class CouponWalletDtos {
    private CouponWalletDtos() {}

    public record CollectRequest(Long shopId, String code) {}

    public record CollectResponse(String code, Long shopId, boolean alreadyCollected, Instant collectedAt) {}

    /** status: USABLE | UPCOMING | USED (per-buyer limit reached) | EXHAUSTED (total limit reached) | EXPIRED | INACTIVE */
    public record CollectedCoupon(Long shopId, String shopName, String shopSlug, String code, String name, String type,
                                  BigDecimal value, BigDecimal minOrderAmount, BigDecimal maxDiscountAmount,
                                  Instant startsAt, Instant endsAt, Instant collectedAt, String status) {}
}
