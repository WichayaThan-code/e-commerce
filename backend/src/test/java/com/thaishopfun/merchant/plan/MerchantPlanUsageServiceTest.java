package com.thaishopfun.merchant.plan;

import com.thaishopfun.merchant.plan.MerchantPlanUsageDtos.LimitInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MerchantPlanUsageServiceTest {

    @Test
    void evaluateFiniteThresholdsUseRawRatio() {
        var limit = new LimitInfo("FINITE", 100L);
        assertEquals("OK", MerchantPlanUsageService.evaluate(79, limit).status());
        assertEquals("WARN", MerchantPlanUsageService.evaluate(80, limit).status());
        assertEquals("WARN", MerchantPlanUsageService.evaluate(99, limit).status());
        assertEquals("FULL", MerchantPlanUsageService.evaluate(100, limit).status());
        var over = MerchantPlanUsageService.evaluate(125, limit);
        assertEquals("OVER", over.status());
        assertEquals(0L, over.remaining());
        assertEquals(1.25d, over.ratio(), 1e-9);
    }

    @Test
    void evaluateZeroAndUnlimitedAndMissingPlan() {
        assertEquals("ZERO_QUOTA", MerchantPlanUsageService.evaluate(0, new LimitInfo("ZERO", 0L)).status());
        assertEquals("OVER", MerchantPlanUsageService.evaluate(3, new LimitInfo("ZERO", 0L)).status());
        assertEquals("UNLIMITED", MerchantPlanUsageService.evaluate(999, new LimitInfo("UNLIMITED", null)).status());
        assertEquals("NOT_APPLICABLE", MerchantPlanUsageService.evaluate(5, new LimitInfo("NOT_APPLICABLE", null)).status());
        assertEquals("UNKNOWN", MerchantPlanUsageService.evaluate(5, new LimitInfo("UNKNOWN", null)).status());
    }

    @Test
    void remainingNeverNegativeWhenOver() {
        var e = MerchantPlanUsageService.evaluate(150, new LimitInfo("FINITE", 100L));
        assertEquals(0L, e.remaining());
        assertTrue(e.ratio() > 1.0);
    }

    @Test
    void largeByteValuesDoNotOverflowRatio() {
        long used = 1_000_000_000L;
        long limit = 10_737_418_240L; // PRO storage (~10 GiB)
        var e = MerchantPlanUsageService.evaluate(used, new LimitInfo("FINITE", limit));
        assertEquals("OK", e.status());
        assertEquals(limit - used, e.remaining());
        assertTrue(e.ratio() > 0 && e.ratio() < 0.8);
        var near = MerchantPlanUsageService.evaluate(9_000_000_000L, new LimitInfo("FINITE", limit));
        assertEquals("WARN", near.status());
        assertTrue(near.ratio() >= 0.8 && near.ratio() < 1.0);
    }
}
