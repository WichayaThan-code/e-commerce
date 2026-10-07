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

    @Bean
    @Primary
    TranslationProvider activeTranslationProvider(
        TranslationProperties properties,
        List<TranslationProvider> providers,
        NoopTranslationProvider noop
    ) {
        var wanted = properties.provider() == null ? "none" : properties.provider().trim().toLowerCase();
        if ("fake".equals(wanted)) {
            return providers.stream().filter(provider -> "fake".equals(provider.id())).findFirst().orElse(noop);
        }
        if ("google".equals(wanted)) {
            return providers.stream().filter(provider -> "google".equals(provider.id())).findFirst().orElse(noop);
        }
        if ("deepl".equals(wanted)) {
            return providers.stream().filter(provider -> "deepl".equals(provider.id())).findFirst().orElse(noop);
        }
        return noop;
    }
}
