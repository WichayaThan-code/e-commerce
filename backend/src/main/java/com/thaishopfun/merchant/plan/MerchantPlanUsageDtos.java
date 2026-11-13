package com.thaishopfun.merchant.plan;

import com.thaishopfun.admin.AdminPlatformDtos.PlanLimits;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Merchant read model for {@code GET /api/merchant/shops/{shopId}/plan-usage}.
 * Limit kinds and metric statuses are explicit — never overload {@code null}.
 */
public final class MerchantPlanUsageDtos {
    private MerchantPlanUsageDtos() {}

    /** Display-only warning thresholds; not package enforcement cutoffs. */
    public record DisplayPolicy(double warnAtRatio, String note) {}

    public record PlanInfo(
        /** ASSIGNED | UNASSIGNED */
        String assignment,
        String code,
        String name,
        /** ACTIVE | RETIRED | UNKNOWN | NONE */
        String resolution,
        String note
    ) {}

    public record LimitInfo(
        /** FINITE | ZERO | UNLIMITED | NOT_APPLICABLE | UNKNOWN */
        String kind,
        Long value
    ) {}

    public record PeriodInfo(
        /** NONE | ROLLING_DAYS */
        String kind,
        Integer rollingDays,
        Instant start,
        Instant end
    ) {}

    public record MetricDefinition(
        String source,
        String includes,
        String excludes,
        String note
    ) {}

    public record MetricUsage(
        String code,
        /** AVAILABLE | RESTRICTED */
        String availability,
        String permissionRequired,
        boolean viewerHasPermission,
        Long used,
        LimitInfo limit,
        /** COUNT | BYTES */
        String unit,
        /** COMPLETE | PARTIAL | UNKNOWN */
        String completeness,
        PeriodInfo period,
        MetricDefinition definition,
        /**
         * OK | WARN | FULL | OVER | UNLIMITED | ZERO_QUOTA | NOT_APPLICABLE | UNKNOWN | RESTRICTED
         * Derived from raw used/limit — not from rounded display percentages.
         */
        String status,
        Long remaining,
        /** Raw used/limit when FINITE and limit &gt; 0; may be &gt; 1 when over quota. */
        Double usedRatio,
        Map<String, Long> breakdown
    ) {}

    public record EnforcementNote(String metricCode, String level, String detail) {}

    public record CatalogPlan(
        String code,
        String name,
        String description,
        BigDecimal priceMonthly,
        BigDecimal priceYearly,
        PlanLimits limits,
        int sortOrder,
        boolean recommended
    ) {}

    public record PlanUsageResponse(
        long shopId,
        String shopName,
        String shopStatus,
        PlanInfo plan,
        /** MONTHLY|YEARLY|null */
        String planBillingCycle,
        Instant planExpiresAt,
        Instant asOf,
        String timezone,
        DisplayPolicy displayPolicy,
        List<MetricUsage> metrics,
        List<EnforcementNote> enforcement,
        List<CatalogPlan> catalog
    ) {}
}
