package com.thaishopfun.translation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ContentTranslationIndex {
    private final Map<String, ContentTranslation> byKey = new HashMap<>();

    public static ContentTranslationIndex of(List<ContentTranslation> rows) {
        var index = new ContentTranslationIndex();
        for (var row : rows) {
            index.byKey.put(key(row.getEntityId(), row.getFieldName(), row.getLocale()), row);
        }
        return index;
    }

    public ContentTranslation get(String entityId, String fieldName, String locale) {
        return byKey.get(key(entityId, fieldName, locale));
    }

    public String resolve(String entityId, String fieldName, String locale, String thai, String legacy) {
        var row = get(entityId, fieldName, locale);
        if (row != null && row.getOrigin() == TranslationOrigin.HUMAN && row.getTranslatedText() != null && !row.getTranslatedText().isBlank()) {
            return row.getTranslatedText().trim();
        }
        if (row != null && row.getOrigin() == TranslationOrigin.MACHINE && row.getStatus() == TranslationStatus.DONE && row.getTranslatedText() != null && !row.getTranslatedText().isBlank()) {
            return row.getTranslatedText().trim();
        }
        if (legacy != null && !legacy.isBlank()) {
            return legacy.trim();
        }
        if (thai != null && !thai.isBlank()) {
            return thai.trim();
        }
        return "";
    }

    public TranslationFieldMeta meta(String entityId, String fieldName, String locale) {
        var row = get(entityId, fieldName, locale);
        if (row == null) return null;
        return new TranslationFieldMeta(row.getOrigin().name(), row.getStatus().name());
    }

    public record TranslationFieldMeta(String origin, String status) {}

    public String merchantValue(String entityId, String fieldName, String locale, String columnValue, String thai) {
        var row = get(entityId, fieldName, locale);
        if (row != null && row.getOrigin() == TranslationOrigin.HUMAN && row.getTranslatedText() != null && !row.getTranslatedText().isBlank()) {
            return row.getTranslatedText().trim();
        }
        if (columnValue != null && !columnValue.isBlank()) {
            return columnValue.trim();
        }
        if (row != null && row.getOrigin() == TranslationOrigin.MACHINE && row.getStatus() == TranslationStatus.DONE && row.getTranslatedText() != null && !row.getTranslatedText().isBlank()) {
            return row.getTranslatedText().trim();
        }
        return columnValue == null ? null : columnValue.trim();
    }

    private static String key(String entityId, String fieldName, String locale) {
        return entityId + "|" + fieldName + "|" + locale;
    }

    public record CatalogDtosTranslationMeta(String origin, String status) {}
}
