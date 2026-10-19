package com.thaishopfun.order;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** Timeline metadata stored as JSON text in order_status_history.metadata_json. */
public final class OrderJson {
    private static final ObjectMapper JSON = new ObjectMapper();

    private OrderJson() {}

    public static String write(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return JSON.writeValueAsString(map);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
