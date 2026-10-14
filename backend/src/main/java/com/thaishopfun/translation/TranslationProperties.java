package com.thaishopfun.translation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "app.translation")
public record TranslationProperties(
    String provider,
    String googleApiKey,
    String deeplApiKey,
    String glossary,
    int batchSize,
    int maxAttempts,
    int retryBackoffMs
) {
    public TranslationProperties {
        if (batchSize <= 0) batchSize = 40;
        if (maxAttempts <= 0) maxAttempts = 5;
        if (retryBackoffMs < 0) retryBackoffMs = 30_000;
    }

    public List<String> glossaryTerms() {
        if (glossary == null || glossary.isBlank()) {
            return List.of("ThaiShopFun", "SKU", "mg", "ml");
        }
        return Arrays.stream(glossary.split(","))
            .map(String::trim)
            .filter(term -> !term.isEmpty())
            .toList();
    }
}
