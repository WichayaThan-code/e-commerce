package com.thaishopfun.catalog.collection;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** V41 limits for shop collections (picked up by {@code @ConfigurationPropertiesScan}). */
@ConfigurationProperties("app.merchant.collections")
public record CollectionProperties(
    @DefaultValue("50") int maxPerShop,
    @DefaultValue("500") int maxProductsPerCollection
) {}
