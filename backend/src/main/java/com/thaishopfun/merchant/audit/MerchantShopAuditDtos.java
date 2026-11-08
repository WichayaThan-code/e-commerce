package com.thaishopfun.merchant.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class MerchantShopAuditDtos {
    private MerchantShopAuditDtos() {}

    public record Page(
        List<ActivityRow> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        List<String> supportedActions
    ) {}

    /** Allowlisted merchant-facing activity row (no email / IP / UA / secrets). */
    public record ActivityRow(
        long id,
        Instant occurredAt,
        String actorName,
        String actorType,
        String action,
        String outcome,
        String summary,
        String reasonCode
    ) {}

    public record ActivityDetail(
        long id,
        Instant occurredAt,
        String actorName,
        String actorType,
        String action,
        String outcome,
        String summary,
        String reasonCode,
        Map<String, Object> before,
        Map<String, Object> after
    ) {}
}
