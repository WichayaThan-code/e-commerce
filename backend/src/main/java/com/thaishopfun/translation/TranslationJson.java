package com.thaishopfun.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TranslationJson {
    private static final Pattern GOOGLE = Pattern.compile("\"translatedText\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern DEEPL = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private TranslationJson() {}

    static List<String> googleTranslatedTexts(String body) {
        return extract(body, GOOGLE);
    }

    static List<String> deeplTexts(String body) {
        return extract(body, DEEPL);
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

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\r", "\r").replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
