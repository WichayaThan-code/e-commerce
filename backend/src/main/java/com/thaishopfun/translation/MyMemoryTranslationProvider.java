package com.thaishopfun.translation;

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

/**
 * Free preview / machine translation without an API key (MyMemory public endpoint).
 * Suitable for local/dev and light production; subject to MyMemory rate limits (~1k words/day anonymous).
 * Prefer {@code google} / {@code deepl} when keys are available.
 * Selected when {@code app.translation.provider=mymemory} (or alias {@code free}).
 */
@Component
public class MyMemoryTranslationProvider implements TranslationProvider {
    private final TranslationProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public MyMemoryTranslationProvider(TranslationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return "mymemory";
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public List<String> translate(List<String> texts, String from, String to) {
        var glossary = properties.glossaryTerms();
        var target = normalizeTarget(to);
        var source = from == null || from.isBlank() ? "th" : from.trim().toLowerCase();
        var out = new ArrayList<String>(texts.size());
        for (var text : texts) {
            if (text == null || text.isBlank()) {
                out.add(text);
                continue;
            }
            var masked = TranslationTextSupport.mask(text, glossary);
            var translated = requestOne(masked.masked() == null ? "" : masked.masked(), source, target);
            out.add(TranslationTextSupport.unmask(translated, masked));
        }
        return out;
    }

    private String requestOne(String text, String from, String to) {
        try {
            var url = "https://api.mymemory.translated.net/get?q="
                + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&langpair="
                + URLEncoder.encode(from + "|" + to, StandardCharsets.UTF_8);
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("MyMemory HTTP " + response.statusCode() + ": " + response.body());
            }
            var translated = TranslationJson.myMemoryTranslatedText(response.body());
            if (translated == null || translated.isBlank()) {
                throw new IllegalStateException("MyMemory returned empty translation");
            }
            // MyMemory sometimes echoes "INVALID SOURCE LANGUAGE ..." into translatedText on bad pairs.
            if (translated.regionMatches(true, 0, "INVALID", 0, 7) || translated.regionMatches(true, 0, "MYMEMORY WARNING", 0, 16)) {
                throw new IllegalStateException("MyMemory: " + translated);
            }
            return translated;
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("MyMemory Translate failed", exception);
        }
    }

    private static String normalizeTarget(String to) {
        if (to == null) return "en";
        var value = to.trim().toLowerCase();
        if (value.startsWith("zh")) return "zh-CN";
        if (value.startsWith("en")) return "en";
        return value;
    }
}
