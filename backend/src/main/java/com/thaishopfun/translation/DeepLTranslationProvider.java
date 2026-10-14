package com.thaishopfun.translation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "app.translation.provider", havingValue = "deepl")
public class DeepLTranslationProvider implements TranslationProvider {
    private final TranslationProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public DeepLTranslationProvider(TranslationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return "deepl";
    }

    @Override
    public boolean configured() {
        return properties.deeplApiKey() != null && !properties.deeplApiKey().isBlank();
    }

    @Override
    public List<String> translate(List<String> texts, String from, String to) {
        var key = properties.deeplApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("DEEPL_API_KEY is not configured");
        }
        try {
            var glossary = properties.glossaryTerms();
            var maskedEntries = texts.stream().map(text -> TranslationTextSupport.mask(text, glossary)).toList();
            var target = to.startsWith("zh") ? "ZH" : to.toUpperCase();
            var body = maskedEntries.stream()
                .map(masked -> "text=" + URLEncoder.encode(masked.masked() == null ? "" : masked.masked(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
            body += "&source_lang=TH&target_lang=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
            var endpoint = key.endsWith(":fx")
                ? "https://api-free.deepl.com/v2/translate"
                : "https://api.deepl.com/v2/translate";
            var request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "DeepL-Auth-Key " + key)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("DeepL HTTP " + response.statusCode() + ": " + response.body());
            }
            var translated = TranslationJson.deeplTexts(response.body());
            var restored = new ArrayList<String>();
            for (int i = 0; i < texts.size(); i++) {
                var original = texts.get(i);
                if (original == null || original.isBlank()) {
                    restored.add(original);
                    continue;
                }
                var text = translated.size() > i ? translated.get(i) : null;
                restored.add(TranslationTextSupport.unmask(text, maskedEntries.get(i)));
            }
            return restored;
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("DeepL failed", exception);
        }
    }
}
