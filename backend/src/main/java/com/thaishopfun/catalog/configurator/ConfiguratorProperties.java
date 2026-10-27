package com.thaishopfun.catalog.configurator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * V42 feature flag {@code app.features.configurator.enabled} (default false). While false every configurator
 * endpoint answers 404 FEATURE_DISABLED and nothing else in the app changes.
 */
@ConfigurationProperties("app.features.configurator")
public record ConfiguratorProperties(@DefaultValue("false") boolean enabled) {}
