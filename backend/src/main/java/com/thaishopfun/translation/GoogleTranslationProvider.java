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

@Component
@ConditionalOnProperty(name = "app.translation.provider", havingValue = "google")
public class GoogleTranslationProvider implements TranslationProvider {
    private final TranslationProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GoogleTranslationProvider(TranslationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return "google";
    }

    @Override
    public List<String> translate(List<String> texts, String from, String to) {
        var key = properties.googleApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("GOOGLE_TRANSLATE_API_KEY is not configured");
        }
        try {
            var glossary = properties.glossaryTerms();
            var maskedEntries = texts.stream().map(text -> TranslationTextSupport.mask(text, glossary)).toList();
            var query = new StringBuilder("https://translation.googleapis.com/language/translate/v2?key=")
                .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append("&source=").append(URLEncoder.encode(from, StandardCharsets.UTF_8))
                .append("&target=").append(URLEncoder.encode(to, StandardCharsets.UTF_8));
            for (var masked : maskedEntries) {
                query.append("&q=").append(URLEncoder.encode(masked.masked() == null ? "" : masked.masked(), StandardCharsets.UTF_8));
            }
            var request = HttpRequest.newBuilder(URI.create(query.toString())).timeout(Duration.ofSeconds(30)).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Google Translate HTTP " + response.statusCode() + ": " + response.body());
            }
            var translated = TranslationJson.googleTranslatedTexts(response.body());
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
            throw new IllegalStateException("Google Translate failed", exception);
        }
    }
}
