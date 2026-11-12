package com.thaishopfun.translation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@Configuration
@EnableAsync
@EnableScheduling
public class TranslationConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TranslationConfig.class);

    @Bean
    @Primary
    TranslationProvider activeTranslationProvider(
        TranslationProperties properties,
        List<TranslationProvider> providers,
        NoopTranslationProvider noop
    ) {
        var wanted = properties.provider() == null ? "none" : properties.provider().trim().toLowerCase();
        TranslationProvider selected = noop;
        if ("fake".equals(wanted)) {
            selected = providers.stream().filter(provider -> "fake".equals(provider.id())).findFirst().orElse(noop);
        } else if ("mymemory".equals(wanted) || "free".equals(wanted)) {
            selected = providers.stream().filter(provider -> "mymemory".equals(provider.id())).findFirst().orElse(noop);
        } else if ("google".equals(wanted)) {
            selected = providers.stream().filter(provider -> "google".equals(provider.id())).findFirst().orElse(noop);
        } else if ("deepl".equals(wanted)) {
            selected = providers.stream().filter(provider -> "deepl".equals(provider.id())).findFirst().orElse(noop);
        }
        log.info("Translation provider active: {} (configured={}, wanted={})",
            selected.id(), selected.configured(), wanted);
        return selected;
    }
}
