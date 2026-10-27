package com.thaishopfun.catalog.configurator;

import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationMessages;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * V42 configurator schema: validation / normalization of what the merchant saves, and the server-authoritative
 * quote of a buyer's selections. Pure functions (no repositories) so they are easy to test and to reuse from the
 * cart/order side later (Supitcha): re-run {@link #quote} at add-to-cart and at checkout.
 *
 * <pre>
 * {"steps":[{"id":"color","type":"SINGLE|MULTI|TEXT","label":{"th","en"},"required":true,
 *            "min":0,"max":3,                                   // MULTI
 *            "maxLength":12,"charset":"ANY|LATIN|ALNUM|THAI_LATIN","priceDelta":"50.00",   // TEXT
 *            "options":[{"id":"gold","label":{"th","en"},"priceDelta":"120.00","mediaId":uuid|null,
 *                        "available":true,"stock":null|int}]}]}
 * </pre>
 */
public final class ConfiguratorSchema {
    public static final int MAX_STEPS = 10;
    public static final int MAX_OPTIONS = 30;
    public static final int MAX_TEXT_LENGTH = 100;
    public static final int LABEL_MAX = 80;
    public static final BigDecimal PRICE_LIMIT = new BigDecimal("1000000");
    public static final List<String> TYPES = List.of("SINGLE", "MULTI", "TEXT");
    public static final List<String> CHARSETS = List.of("ANY", "LATIN", "ALNUM", "THAI_LATIN");
    static final Pattern ID = Pattern.compile("^[a-z0-9_-]{1,40}$");
    private static final Pattern LATIN = Pattern.compile("^[\\x20-\\x7E]*$");
    private static final Pattern ALNUM = Pattern.compile("^[A-Za-z0-9 ]*$");
    private static final Pattern THAI_LATIN = Pattern.compile("^[\\x20-\\x7E\\u0E00-\\u0E7F]*$");

    private ConfiguratorSchema() {}

    // ------------------------------------------------------------------ merchant schema

    /**
     * Validates and returns the normalized schema (unknown keys dropped, defaults filled, prices as 2dp strings).
     * {@code imageInShop} answers whether a media id is an IMAGE of this shop's library.
     */
    public static Map<String, Object> normalize(Object raw, BigDecimal basePrice, Predicate<UUID> imageInShop, List<FieldViolation> problems) {
        var out = new LinkedHashMap<String, Object>();
        var steps = new ArrayList<Object>();
        out.put("steps", steps);
        if (raw == null) return out;
        if (!(raw instanceof Map<?, ?> map) || !(map.get("steps") instanceof List<?> rawSteps) || rawSteps.size() > MAX_STEPS) {
            problems.add(ValidationMessages.violation("schema.steps", "CONFIGURATOR_SCHEMA_INVALID"));
            return out;
        }
        var stepIds = new HashSet<String>();
        var worst = BigDecimal.ZERO;
        for (int i = 0; i < rawSteps.size(); i++) {
            var path = "schema.steps[" + i + "]";
            if (!(rawSteps.get(i) instanceof Map<?, ?> step)) {
                problems.add(ValidationMessages.violation(path, "CONFIGURATOR_SCHEMA_INVALID"));
                continue;
            }
            var normalized = new LinkedHashMap<String, Object>();
            var id = step.get("id") instanceof String s ? s.trim() : null;
            if (id == null || !ID.matcher(id).matches() || !stepIds.add(id)) problems.add(ValidationMessages.violation(path + ".id", "CONFIGURATOR_SCHEMA_INVALID"));
            normalized.put("id", id);
            var type = step.get("type") instanceof String s ? s.trim().toUpperCase() : null;
            if (type == null || !TYPES.contains(type)) {
                problems.add(ValidationMessages.violation(path + ".type", "CONFIGURATOR_SCHEMA_INVALID"));
                continue;
            }
            normalized.put("type", type);
            normalized.put("label", label(step.get("label"), path + ".label", problems));
            boolean required = !Boolean.FALSE.equals(step.get("required"));
            normalized.put("required", required);
            if ("TEXT".equals(type)) {
                int maxLength = intOr(step.get("maxLength"), 20);
                if (maxLength < 1 || maxLength > MAX_TEXT_LENGTH) problems.add(ValidationMessages.violation(path + ".maxLength", "CONFIGURATOR_SCHEMA_INVALID"));
                var charset = step.get("charset") == null ? "ANY" : String.valueOf(step.get("charset")).trim().toUpperCase();
                if (!CHARSETS.contains(charset)) problems.add(ValidationMessages.violation(path + ".charset", "CONFIGURATOR_SCHEMA_INVALID"));
                var delta = price(step.get("priceDelta"), path + ".priceDelta", problems);
                if (step.get("options") instanceof List<?> list && !list.isEmpty()) problems.add(ValidationMessages.violation(path + ".options", "CONFIGURATOR_SCHEMA_INVALID"));
                normalized.put("maxLength", maxLength);
                normalized.put("charset", charset);
                normalized.put("priceDelta", text(delta));
                worst = worst.add(delta.min(BigDecimal.ZERO));
                steps.add(normalized);
                continue;
            }
            var options = new ArrayList<Object>();
            var negatives = new ArrayList<BigDecimal>();
            if (!(step.get("options") instanceof List<?> rawOptions) || rawOptions.isEmpty() || rawOptions.size() > MAX_OPTIONS) {
                problems.add(ValidationMessages.violation(path + ".options", "CONFIGURATOR_SCHEMA_INVALID"));
            } else {
                var optionIds = new HashSet<String>();
                for (int j = 0; j < rawOptions.size(); j++) {
                    var optionPath = path + ".options[" + j + "]";
                    if (!(rawOptions.get(j) instanceof Map<?, ?> option)) {
                        problems.add(ValidationMessages.violation(optionPath, "CONFIGURATOR_SCHEMA_INVALID"));
                        continue;
                    }
                    var optionOut = new LinkedHashMap<String, Object>();
                    var optionId = option.get("id") instanceof String s ? s.trim() : null;
                    if (optionId == null || !ID.matcher(optionId).matches() || !optionIds.add(optionId)) {
                        problems.add(ValidationMessages.violation(optionPath + ".id", "CONFIGURATOR_SCHEMA_INVALID"));
                    }
                    optionOut.put("id", optionId);
                    optionOut.put("label", label(option.get("label"), optionPath + ".label", problems));
                    var delta = price(option.get("priceDelta"), optionPath + ".priceDelta", problems);
                    optionOut.put("priceDelta", text(delta));
                    if (delta.signum() < 0) negatives.add(delta);
                    String mediaId = null;
                    if (option.get("mediaId") != null && !String.valueOf(option.get("mediaId")).isBlank()) {
                        var parsed = uuid(String.valueOf(option.get("mediaId")));
                        if (parsed == null || !imageInShop.test(parsed)) problems.add(ValidationMessages.violation(optionPath + ".mediaId", "CONFIGURATOR_MEDIA_INVALID"));
                        else mediaId = parsed.toString();
                    }
                    optionOut.put("mediaId", mediaId);
                    optionOut.put("available", !Boolean.FALSE.equals(option.get("available")));
                    Integer stock = null;
                    if (option.get("stock") != null) {
                        if (!(option.get("stock") instanceof Number number) || number.doubleValue() % 1 != 0 || number.longValue() < 0 || number.longValue() > 1_000_000) {
                            problems.add(ValidationMessages.violation(optionPath + ".stock", "CONFIGURATOR_SCHEMA_INVALID"));
                        } else {
                            stock = number.intValue();
                        }
                    }
                    optionOut.put("stock", stock);
                    options.add(optionOut);
                }
            }
            if ("MULTI".equals(type)) {
                int size = options.size();
                int min = intOr(step.get("min"), required ? 1 : 0);
                int max = intOr(step.get("max"), Math.max(1, size));
                if (min < 0 || max < 1 || min > max || max > Math.max(1, size)) {
                    problems.add(ValidationMessages.violation(path + ".max", "CONFIGURATOR_SCHEMA_INVALID"));
                }
                if (required && min < 1) min = 1;
                normalized.put("min", min);
                normalized.put("max", max);
                negatives.sort(BigDecimal::compareTo);
                for (int k = 0; k < Math.min(Math.max(max, 0), negatives.size()); k++) worst = worst.add(negatives.get(k));
            } else {
                worst = worst.add(negatives.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
            }
            normalized.put("options", options);
            steps.add(normalized);
        }
        if (problems.isEmpty() && basePrice != null && basePrice.add(worst).signum() < 0) {
            problems.add(ValidationMessages.violation("schema.steps", "CONFIGURATOR_TOTAL_NEGATIVE"));
        }
        return out;
    }

    // ------------------------------------------------------------------ quote

    public record Quote(boolean complete, List<String> missing, List<ConfiguratorDtos.QuoteLine> lines, BigDecimal total,
                        Map<String, Object> selections, String configHash) {}

    /** Server-authoritative price; invalid input -> violations on {@code selections.<stepId>}. */
    @SuppressWarnings("unchecked")
    public static Quote quote(UUID productId, int version, Map<String, Object> schema, BigDecimal basePrice, Map<String, Object> rawSelections,
                              String locale, List<FieldViolation> problems) {
        var selections = rawSelections == null ? Map.<String, Object>of() : rawSelections;
        var steps = schema.get("steps") instanceof List<?> list ? list : List.of();
        var known = new HashSet<String>();
        for (var step : steps) if (step instanceof Map<?, ?> map) known.add(String.valueOf(map.get("id")));
        for (var key : selections.keySet()) {
            if (!known.contains(key)) problems.add(ValidationMessages.violation("selections." + safeKey(key), "CONFIGURATOR_SELECTION_INVALID"));
        }
        var missing = new ArrayList<String>();
        var lines = new ArrayList<ConfiguratorDtos.QuoteLine>();
        var normalized = new LinkedHashMap<String, Object>();
        var total = basePrice == null ? BigDecimal.ZERO : basePrice;
        for (var rawStep : steps) {
            if (!(rawStep instanceof Map<?, ?> stepMap)) continue;
            var step = (Map<String, Object>) stepMap;
            var id = String.valueOf(step.get("id"));
            var field = "selections." + id;
            var type = String.valueOf(step.get("type"));
            boolean required = Boolean.TRUE.equals(step.get("required"));
            var value = selections.get(id);
            switch (type) {
                case "TEXT" -> {
                    if (value != null && !(value instanceof String)) { problems.add(ValidationMessages.violation(field, "CONFIGURATOR_TEXT_INVALID")); continue; }
                    var text = sanitizeText((String) value);
                    if (text.isEmpty()) { if (required) missing.add(id); continue; }
                    int maxLength = intOr(step.get("maxLength"), 20);
                    if (text.indexOf('<') >= 0 || text.indexOf('>') >= 0 || text.codePointCount(0, text.length()) > maxLength
                        || !charsetOk(String.valueOf(step.get("charset")), text)) {
                        problems.add(ValidationMessages.violation(field, "CONFIGURATOR_TEXT_INVALID"));
                        continue;
                    }
                    var delta = new BigDecimal(String.valueOf(step.getOrDefault("priceDelta", "0.00")));
                    normalized.put(id, text);
                    lines.add(new ConfiguratorDtos.QuoteLine(id, null, text, delta));
                    total = total.add(delta);
                }
                case "SINGLE" -> {
                    if (value == null || (value instanceof String s && s.isBlank())) { if (required) missing.add(id); continue; }
                    if (!(value instanceof String optionId)) { problems.add(ValidationMessages.violation(field, "CONFIGURATOR_SELECTION_INVALID")); continue; }
                    var option = option(step, optionId.trim());
                    if (option == null || !available(option)) { problems.add(ValidationMessages.violation(field, "CONFIGURATOR_SELECTION_INVALID")); continue; }
                    var delta = new BigDecimal(String.valueOf(option.get("priceDelta")));
                    normalized.put(id, option.get("id"));
                    lines.add(new ConfiguratorDtos.QuoteLine(id, String.valueOf(option.get("id")), labelText(option.get("label"), locale), delta));
                    total = total.add(delta);
                }
                case "MULTI" -> {
                    List<?> picked;
                    if (value == null) picked = List.of();
                    else if (value instanceof List<?> l) picked = l;
                    else if (value instanceof String s) picked = s.isBlank() ? List.of() : List.of(s);
                    else { problems.add(ValidationMessages.violation(field, "CONFIGURATOR_SELECTION_INVALID")); continue; }
                    var ids = new LinkedHashSet<String>();
                    boolean bad = false;
                    for (var item : picked) {
                        if (!(item instanceof String s) || option(step, s.trim()) == null || !available(option(step, s.trim()))) { bad = true; break; }
                        ids.add(s.trim());
                    }
                    if (bad) { problems.add(ValidationMessages.violation(field, "CONFIGURATOR_SELECTION_INVALID")); continue; }
                    int min = intOr(step.get("min"), 0);
                    int max = intOr(step.get("max"), Integer.MAX_VALUE);
                    if (ids.size() > max) { problems.add(ValidationMessages.violation(field, "CONFIGURATOR_SELECTION_COUNT")); continue; }
                    if (ids.size() < min) { if (required || !ids.isEmpty()) missing.add(id); if (ids.isEmpty()) continue; }
                    // keep the merchant's option order so the hash does not depend on click order
                    var ordered = new ArrayList<String>();
                    for (var option : (List<Map<String, Object>>) step.getOrDefault("options", List.of())) {
                        var optionId = String.valueOf(option.get("id"));
                        if (!ids.contains(optionId)) continue;
                        ordered.add(optionId);
                        var delta = new BigDecimal(String.valueOf(option.get("priceDelta")));
                        lines.add(new ConfiguratorDtos.QuoteLine(id, optionId, labelText(option.get("label"), locale), delta));
                        total = total.add(delta);
                    }
                    if (!ordered.isEmpty()) normalized.put(id, ordered);
                }
                default -> { }
            }
        }
        if (problems.isEmpty() && total.signum() < 0) problems.add(ValidationMessages.violation("selections", "CONFIGURATOR_TOTAL_NEGATIVE"));
        total = total.setScale(2, RoundingMode.HALF_UP);
        return new Quote(missing.isEmpty(), missing, lines, total, normalized, hash(productId, version, normalized));
    }

    /** NFC, control / format characters removed (ZWJ kept for emoji-free Thai safety), whitespace collapsed, trimmed. */
    public static String sanitizeText(String raw) {
        if (raw == null) return "";
        return Normalizer.normalize(raw, Normalizer.Form.NFC)
            .replaceAll("[\\p{Cc}\\p{Cf}&&[^\\u200D]]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    static boolean charsetOk(String charset, String text) {
        return switch (charset == null ? "ANY" : charset) {
            case "LATIN" -> LATIN.matcher(text).matches();
            case "ALNUM" -> ALNUM.matcher(text).matches();
            case "THAI_LATIN" -> THAI_LATIN.matcher(text).matches();
            default -> true;
        };
    }

    /** SHA-256 over productId | version | stepId=value... in schema order (MULTI ids in schema order). */
    public static String hash(UUID productId, int version, Map<String, Object> normalized) {
        var canonical = new StringBuilder().append(productId).append('|').append(version);
        normalized.forEach((key, value) -> {
            canonical.append('|').append(key).append('=');
            if (value instanceof List<?> list) canonical.append(String.join(",", list.stream().map(String::valueOf).toList()));
            else canonical.append(String.valueOf(value).replace("|", "\\|"));
        });
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static boolean available(Map<?, ?> option) {
        if (Boolean.FALSE.equals(option.get("available"))) return false;
        return !(option.get("stock") instanceof Number stock) || stock.intValue() > 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> option(Map<String, Object> step, String optionId) {
        if (!(step.get("options") instanceof List<?> options)) return null;
        for (var option : options) {
            if (option instanceof Map<?, ?> map && optionId.equals(map.get("id"))) return (Map<String, Object>) map;
        }
        return null;
    }

    static String labelText(Object label, String locale) {
        if (!(label instanceof Map<?, ?> map)) return "";
        var english = locale != null && !locale.toLowerCase().startsWith("th");
        var en = map.get("en") instanceof String s && !s.isBlank() ? s : null;
        return english && en != null ? en : String.valueOf(map.get("th") == null ? "" : map.get("th"));
    }

    private static Map<String, Object> label(Object raw, String path, List<FieldViolation> problems) {
        var out = new LinkedHashMap<String, Object>();
        String th = null, en = null;
        if (raw instanceof String s) th = s;
        else if (raw instanceof Map<?, ?> map) {
            th = map.get("th") instanceof String s ? s : null;
            en = map.get("en") instanceof String s ? s : null;
        }
        th = sanitizeText(th);
        en = sanitizeText(en);
        if (th.isEmpty() || th.length() > LABEL_MAX || th.indexOf('<') >= 0 || th.indexOf('>') >= 0) {
            problems.add(ValidationMessages.violation(path + ".th", "CONFIGURATOR_SCHEMA_INVALID"));
        }
        if (en.length() > LABEL_MAX || en.indexOf('<') >= 0 || en.indexOf('>') >= 0) {
            problems.add(ValidationMessages.violation(path + ".en", "CONFIGURATOR_SCHEMA_INVALID"));
        }
        out.put("th", th);
        out.put("en", en);
        return out;
    }

    private static BigDecimal price(Object raw, String path, List<FieldViolation> problems) {
        if (raw == null || (raw instanceof String s && s.isBlank())) return BigDecimal.ZERO.setScale(2);
        try {
            var value = raw instanceof Number number && !(raw instanceof BigDecimal) ? new BigDecimal(number.toString()) : new BigDecimal(String.valueOf(raw).trim());
            if (value.stripTrailingZeros().scale() > 2 || value.abs().compareTo(PRICE_LIMIT) > 0) {
                problems.add(ValidationMessages.violation(path, "CONFIGURATOR_PRICE_INVALID"));
                return BigDecimal.ZERO.setScale(2);
            }
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException ex) {
            problems.add(ValidationMessages.violation(path, "CONFIGURATOR_PRICE_INVALID"));
            return BigDecimal.ZERO.setScale(2);
        }
    }

    private static String text(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static int intOr(Object raw, int fallback) {
        if (raw instanceof Number number && number.doubleValue() % 1 == 0) return number.intValue();
        if (raw instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { return Integer.MIN_VALUE; }
        }
        return raw == null ? fallback : Integer.MIN_VALUE;
    }

    private static UUID uuid(String raw) {
        try { return UUID.fromString(raw.trim()); } catch (IllegalArgumentException ex) { return null; }
    }

    private static String safeKey(String key) {
        return key == null ? "" : key.replaceAll("[^A-Za-z0-9_-]", "").substring(0, Math.min(40, key.replaceAll("[^A-Za-z0-9_-]", "").length()));
    }
}
