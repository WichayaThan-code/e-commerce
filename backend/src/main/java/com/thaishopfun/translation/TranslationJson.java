package com.thaishopfun.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TranslationJson {
    private static final Pattern GOOGLE = Pattern.compile("\"translatedText\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern DEEPL = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    /** MyMemory: first "translatedText" under responseData. */
    private static final Pattern MY_MEMORY = Pattern.compile("\"translatedText\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private TranslationJson() {}

    static List<String> googleTranslatedTexts(String body) {
        return extract(body, GOOGLE);
    }

    static List<String> deeplTexts(String body) {
        return extract(body, DEEPL);
    }

    static String myMemoryTranslatedText(String body) {
        var values = extract(body, MY_MEMORY);
        return values.isEmpty() ? null : values.get(0);
    }

    private static List<String> extract(String body, Pattern pattern) {
        var values = new ArrayList<String>();
        if (body == null) return values;
        Matcher matcher = pattern.matcher(body);
        while (matcher.find()) {
            values.add(unescape(matcher.group(1)));
        }
        return values;
    }

    /** Decode JSON string escapes, including {@code \\uXXXX} (needed for MyMemory Chinese text). */
    private static String unescape(String value) {
        if (value == null || value.isEmpty()) return value;
        var out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (character != '\\' || index + 1 >= value.length()) {
                out.append(character);
                continue;
            }
            var next = value.charAt(++index);
            switch (next) {
                case 'u' -> {
                    if (index + 4 < value.length()) {
                        var hex = value.substring(index + 1, index + 5);
                        try {
                            out.append((char) Integer.parseInt(hex, 16));
                            index += 4;
                            break;
                        } catch (NumberFormatException ignored) {
                            // fall through — keep literal
                        }
                    }
                    out.append('\\').append('u');
                }
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                default -> out.append('\\').append(next);
            }
        }
        return out.toString();
    }
}
