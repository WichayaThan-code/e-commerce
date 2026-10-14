package com.thaishopfun.translation;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

final class OptionTranslationFields {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Group>> GROUPS = new TypeReference<>() {};

    private OptionTranslationFields() {}

    record Slot(String field, String thai) {}

    static List<Slot> fromSchemaJson(String json) {
        var groups = read(json);
        var slots = new ArrayList<Slot>();
        for (var groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            var group = groups.get(groupIndex);
            slots.add(new Slot("n" + groupIndex, group.name));
            var values = group.values == null ? List.<String>of() : group.values;
            for (var valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                slots.add(new Slot("v" + groupIndex + "." + valueIndex, values.get(valueIndex)));
            }
        }
        return slots;
    }

    static String thai(String json, String field) {
        if (field == null) return null;
        for (var slot : fromSchemaJson(json)) {
            if (field.equals(slot.field())) return slot.thai();
        }
        return null;
    }

    private static List<Group> read(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return List.of();
        try {
            var groups = JSON.readValue(json, GROUPS);
            return groups == null ? List.of() : groups;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private record Group(String name, List<String> values) {}
}
