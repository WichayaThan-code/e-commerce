package com.thaishopfun.catalog.configurator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** V42 configurator request / response shapes. */
public final class ConfiguratorDtos {
    private ConfiguratorDtos() {}

    /** {@code version} = the version the editor loaded (0 when none exists yet); a mismatch is a 409. */
    public record ConfiguratorWriteRequest(Boolean enabled, Map<String, Object> schema, Integer version) {}

    public record MerchantConfiguratorResponse(UUID productId, boolean enabled, Map<String, Object> schema, int version, Instant modifiedDateTime) {}

    public record PublicConfiguratorResponse(UUID productId, int version, BigDecimal basePrice, String currency, List<Map<String, Object>> steps) {}

    /** selections: stepId -> optionId (SINGLE) | [optionIds] (MULTI) | text (TEXT). */
    public record QuoteRequest(Map<String, Object> selections, Integer version) {}

    public record QuoteLine(String stepId, String optionId, String label, BigDecimal priceDelta) {}

    public record QuoteResponse(
        boolean complete, List<String> missing, BigDecimal basePrice, List<QuoteLine> lines, BigDecimal total, String currency,
        int version, String configHash, Map<String, Object> selections
    ) {}
}
