package com.thaishopfun.customer;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only customer insights (accepted orders + CRM) for the merchant customers page. */
public final class CustomerInsightsDtos {
    private CustomerInsightsDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomerInsight(
        String key,
        UUID shopCustomerId,
        String displayName,
        String phone,
        String email,
        boolean piiMasked,
        String channel,
        long orderCount,
        BigDecimal totalSpent,
        String currency,
        Instant firstOrderAt,
        Instant lastOrderAt,
        Instant joinedAt,
        String segment,
        String status
    ) {}

    public record InsightsStats(
        long totalCustomers,
        long newThisMonth,
        long repeatCustomers,
        long vipCustomers,
        BigDecimal totalRevenue,
        BigDecimal avgOrderValue
    ) {}

    public record InsightsPage(
        List<CustomerInsight> items,
        int page,
        int size,
        long total,
        int totalPages,
        InsightsStats stats
    ) {}

    public record ChannelCount(String channel, long count) {}

    public record InsightDetail(
        CustomerInsight insight,
        List<CustomerDtos.AddressResponse> addresses,
        List<ChannelCount> channels
    ) {}

    public record InsightOrderItem(
        UUID orderId,
        String orderNumber,
        String status,
        String paymentStatus,
        BigDecimal grandTotal,
        String currency,
        Instant placedAt
    ) {}

    public record InsightOrdersPage(
        List<InsightOrderItem> items,
        int page,
        int size,
        long total,
        int totalPages
    ) {}
}
