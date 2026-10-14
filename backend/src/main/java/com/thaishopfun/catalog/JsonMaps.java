package com.thaishopfun.catalog;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonMaps {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<CatalogDtos.OptionGroup>> GROUPS = new TypeReference<>() {};
    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP = new TypeReference<>() {};

    private JsonMaps() {}

    static String write(Map<String, String> values) {
        try {
            return JSON.writeValueAsString(values == null ? Map.of() : values);
        } catch (RuntimeException exception) {
            return "{}";
        }
    }

    static Map<String, String> read(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return new LinkedHashMap<>();
        try {
            var values = JSON.readValue(json, STRING_MAP);
            return values == null ? new LinkedHashMap<>() : values;
        } catch (RuntimeException exception) {
            return new LinkedHashMap<>();
        }
    }

    static String writeGroups(List<CatalogDtos.OptionGroup> groups) {
        try {
            return JSON.writeValueAsString(groups == null ? List.of() : groups);
        } catch (RuntimeException exception) {
            return "[]";
        }
    }

    static List<CatalogDtos.OptionGroup> readGroups(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return List.of();
        try {
            var groups = JSON.readValue(json, GROUPS);
            return groups == null ? List.of() : List.copyOf(groups);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }
}
