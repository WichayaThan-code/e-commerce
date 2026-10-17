package com.thaishopfun.translation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

final class TranslationTextSupport {
    private static final Pattern INTERPOLATION = Pattern.compile("\\{\\{[^}]+}}");
    private static final Pattern RICH_TAG = Pattern.compile("</?\\d+>");

    private TranslationTextSupport() {}

    static String hashThai(String text) {
        if (text == null) return null;
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    /** Thai prose is translated. Size and capacity codes such as S, XL, 40, and 128GB are kept as written. */
    static boolean translatable(String value) {
        var text = blankToNull(value);
        if (text == null) return false;
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (character >= '\u0E00' && character <= '\u0E7F') return true;
        }
        return false;
    }

    static MaskedText mask(String text, List<String> glossary) {
        if (text == null || text.isBlank()) {
            return new MaskedText(text, List.of(), List.of());
        }
        var glossaryTokens = new ArrayList<String>();
        var working = text;
        int glossaryIndex = 0;
        for (var term : glossary) {
            if (term == null || term.isBlank()) continue;
            while (working.contains(term)) {
                var token = "{{G" + glossaryIndex + "}}";
                glossaryTokens.add(term);
                working = working.replace(term, token);
                glossaryIndex++;
            }
        }
        var segments = new ArrayList<Segment>();
        working = shield(working, INTERPOLATION, segments);
        working = shield(working, RICH_TAG, segments);
        return new MaskedText(working, glossaryTokens, segments);
    }

    private static String shield(String input, Pattern pattern, List<Segment> segments) {
        var matcher = pattern.matcher(input);
        var buffer = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            buffer.append(input, last, matcher.start());
            var token = "{{P" + segments.size() + "}}";
            segments.add(new Segment(token, matcher.group()));
            buffer.append(token);
            last = matcher.end();
        }
        buffer.append(input.substring(last));
        return buffer.toString();
    }

    static String unmask(String translated, MaskedText masked) {
        if (translated == null) return null;
        var result = translated;
        for (var segment : masked.segments()) {
            result = result.replace(segment.token(), segment.original());
        }
        for (int i = masked.glossaryTokens().size() - 1; i >= 0; i--) {
            result = result.replace("{{G" + i + "}}", masked.glossaryTokens().get(i));
        }
        return result;
    }

    record MaskedText(String masked, List<String> glossaryTokens, List<Segment> segments) {}

    record Segment(String token, String original) {}
}
