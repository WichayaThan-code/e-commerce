package com.thaishopfun.catalog;

import java.util.LinkedHashMap;
import java.util.Map;

final class JsonMaps {
    private JsonMaps() {}

    static String write(Map<String, String> values) {
        if (values == null || values.isEmpty()) return "{}";
        var json = new StringBuilder("{");
        var first = true;
        for (var entry : values.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append('"').append(escape(entry.getKey())).append("\":\"")
                .append(escape(entry.getValue() == null ? "" : entry.getValue())).append('"');
        }
        return json.append('}').toString();
    }

    static Map<String, String> read(String json) {
        var values = new LinkedHashMap<String, String>();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return values;
        var body = json.trim();
        if (body.startsWith("{")) body = body.substring(1);
        if (body.endsWith("}")) body = body.substring(0, body.length() - 1);
        if (body.isBlank()) return values;
        for (var part : body.split(",")) {
            var split = part.split(":", 2);
            if (split.length < 2) continue;
            values.put(unquote(split[0]), unquote(split[1]));
        }
        return values;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unquote(String value) {
        var text = value.trim();
        if (text.startsWith("\"")) text = text.substring(1);
        if (text.endsWith("\"")) text = text.substring(0, text.length() - 1);
        return text.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
