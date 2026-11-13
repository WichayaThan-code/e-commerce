package com.thaishopfun.merchant.theme;

import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationMessages;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Non-destructive image focal point + zoom stored next to theme media references.
 * Shape: {@code { x: 0–100, y: 0–100, zoom: 1–3 }} (percent focus + scale). Absent = centre / zoom 1.
 */
final class ShopThemeImageCrop {
    static final String CROP_KEY = "crop";
    static final String MOBILE_CROP_KEY = "mobileCrop";
    static final String INVALID = "THEME_IMAGE_CROP_INVALID";

    static final double X_MIN = 0;
    static final double X_MAX = 100;
    static final double Y_MIN = 0;
    static final double Y_MAX = 100;
    static final double ZOOM_MIN = 1;
    static final double ZOOM_MAX = 3;

    private ShopThemeImageCrop() {}

    /**
     * Whitelist + clamp. Missing / null removes the key. Non-map / non-numeric shapes are left as-is so
     * {@link #validateCrop} can return {@link #INVALID}.
     */
    static void putCrop(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source == null || !source.containsKey(key)) return;
        var raw = source.get(key);
        if (raw == null) {
            target.remove(key);
            return;
        }
        var map = asMap(raw);
        if (map == null) {
            target.put(key, raw);
            return;
        }
        target.put(key, sanitizeCrop(map));
    }

    /** Clamp numeric crop fields to range and round to 2 decimals. Non-numbers stay so validate can reject. */
    static Map<String, Object> sanitizeCrop(Map<String, Object> source) {
        var out = new LinkedHashMap<String, Object>();
        putNumber(out, source, "x", X_MIN, X_MAX, 50);
        putNumber(out, source, "y", Y_MIN, Y_MAX, 50);
        putNumber(out, source, "zoom", ZOOM_MIN, ZOOM_MAX, 1);
        return out;
    }

    /** Structural check when the key is present. Path is the full field path (e.g. {@code config.banners[0].crop.zoom}). */
    static void validateCrop(List<FieldViolation> problems, String path, Object raw) {
        if (raw == null) {
            problems.add(ValidationMessages.violation(path, INVALID));
            return;
        }
        var map = asMap(raw);
        if (map == null) {
            problems.add(ValidationMessages.violation(path, INVALID));
            return;
        }
        checkAxis(problems, path + ".x", map.get("x"), X_MIN, X_MAX);
        checkAxis(problems, path + ".y", map.get("y"), Y_MIN, Y_MAX);
        checkAxis(problems, path + ".zoom", map.get("zoom"), ZOOM_MIN, ZOOM_MAX);
    }

    /** Validate {@code crop} / {@code mobileCrop} when present on a media holder. */
    static void validateHolderCrops(List<FieldViolation> problems, String prefix, Map<String, Object> holder) {
        if (holder == null) return;
        if (holder.containsKey(CROP_KEY)) validateCrop(problems, prefix + "." + CROP_KEY, holder.get(CROP_KEY));
        if (holder.containsKey(MOBILE_CROP_KEY)) {
            validateCrop(problems, prefix + "." + MOBILE_CROP_KEY, holder.get(MOBILE_CROP_KEY));
        }
    }

    /** Copy sanitized crops from source only when the holder still has media; otherwise drop them. */
    static void copyCropsIfMedia(Map<String, Object> target, Map<String, Object> source, boolean hasMedia) {
        if (!hasMedia) return;
        putCrop(target, source, CROP_KEY);
        putCrop(target, source, MOBILE_CROP_KEY);
    }

    private static void putNumber(
        Map<String, Object> out, Map<String, Object> source, String key, double min, double max, double fallback
    ) {
        if (!source.containsKey(key) || source.get(key) == null) {
            out.put(key, round2(fallback));
            return;
        }
        var number = asDouble(source.get(key));
        if (number == null) {
            out.put(key, source.get(key)); // flagged by validate
            return;
        }
        out.put(key, round2(clamp(number, min, max)));
    }

    private static void checkAxis(List<FieldViolation> problems, String field, Object raw, double min, double max) {
        var number = asDouble(raw);
        if (number == null || number < min || number > max) {
            problems.add(ValidationMessages.violation(field, INVALID));
        }
    }

    private static Double asDouble(Object raw) {
        if (raw instanceof Number number) return number.doubleValue();
        if (raw instanceof String text) {
            var trimmed = text.trim();
            if (trimmed.isEmpty()) return null;
            try {
                return Double.parseDouble(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}
