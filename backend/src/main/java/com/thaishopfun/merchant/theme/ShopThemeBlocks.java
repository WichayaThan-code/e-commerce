package com.thaishopfun.merchant.theme;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * AG3: CUSTOM-only repeatable {@code layout.blocks}. Pure helpers (no Spring) for whitelisting, legacy migration
 * ({@code sections} + {@code sectionSettings} → blocks) and keeping the legacy {@code sections} list in sync.
 * Validation lives in {@link ShopThemeService} so it can reuse the shared hex / gradient checks.
 */
final class ShopThemeBlocks {
    static final int MAX_BLOCKS = 30;
    static final Pattern ID = Pattern.compile("^[a-z0-9-]{1,40}$");
    /** HERO and HOURS may appear once; AG4 content types (STEPS, FEATURE, ...) and the product sections repeat freely. */
    static final Set<ShopBlockType> SINGLETON_TYPES = Set.of(ShopBlockType.HERO, ShopBlockType.HOURS);
    static final Set<ShopSectionKey> PRODUCT_TYPES = Set.of(
        ShopSectionKey.FEATURED, ShopSectionKey.BESTSELLERS, ShopSectionKey.NEW_ARRIVALS, ShopSectionKey.CATALOG
    );

    static final List<String> BACKGROUNDS = List.of("NONE", "SURFACE", "PRIMARY", "GRADIENT", "IMAGE");
    static final List<String> ALIGNS = List.of("LEFT", "CENTER");
    static final List<String> PADDINGS = List.of("COMPACT", "NORMAL", "LARGE");
    static final String MEDIA_KEY = "backgroundMediaId";

    private static final Set<String> BLOCK_KEYS = Set.of("id", "type", "enabled", "variant", "settings", "content");
    private static final Set<String> SETTINGS_KEYS = Set.of(
        "background", "gradient", MEDIA_KEY, "overlay", "align", "columns", "paddingY", "fullWidth"
    );
    private static final Set<String> GRADIENT_KEYS = Set.of("angle", "stops");
    private static final Set<String> GRADIENT_STOP_KEYS = Set.of("color", "at");

    private ShopThemeBlocks() {}

    // ───────────────────────── whitelist ─────────────────────────

    /** CUSTOM: keep only {@code layout.blocks} and whitelisted block fields; fills defaults for omitted settings. */
    static void sanitize(Map<String, Object> config) {
        if (config == null) return;
        var layout = asMap(config.get("layout"));
        if (layout == null || layout.get("blocks") == null) {
            config.remove("layout");
            return;
        }
        var out = new LinkedHashMap<String, Object>();
        if (layout.get("blocks") instanceof List<?> list) {
            var blocks = new ArrayList<Object>();
            for (var item : list) {
                var map = asMap(item);
                blocks.add(map == null ? item : sanitizeBlock(map));
            }
            out.put("blocks", blocks);
        } else {
            out.put("blocks", layout.get("blocks")); // flagged by validation
        }
        config.put("layout", out);
    }

    private static Map<String, Object> sanitizeBlock(Map<String, Object> block) {
        var out = keep(block, BLOCK_KEYS);
        upper(out, "type");
        upper(out, "variant");
        if (out.get("variant") == null || (out.get("variant") instanceof String text && text.isBlank())) out.remove("variant");
        if (out.get("settings") == null || asMap(out.get("settings")) != null) {
            var source = asMap(out.get("settings"));
            var settings = source == null ? new LinkedHashMap<String, Object>() : keep(source, SETTINGS_KEYS);
            upper(settings, "background");
            upper(settings, "align");
            upper(settings, "paddingY");
            coerceInt(settings, "columns");
            coerceInt(settings, "overlay");
            if (settings.get("columns") == null) settings.remove("columns");
            if (settings.get("overlay") == null) settings.remove("overlay");
            var gradient = asMap(settings.get("gradient"));
            if (gradient != null) {
                var cleaned = keep(gradient, GRADIENT_KEYS);
                if (cleaned.get("stops") instanceof List<?> stops) {
                    var stopsOut = new ArrayList<Object>();
                    for (var stop : stops) {
                        var row = asMap(stop);
                        stopsOut.add(row == null ? stop : keep(row, GRADIENT_STOP_KEYS));
                    }
                    cleaned.put("stops", stopsOut);
                }
                settings.put("gradient", cleaned);
            } else if (settings.get("gradient") == null) {
                settings.remove("gradient");
            }
            if (settings.get(MEDIA_KEY) instanceof String text) {
                if (text.isBlank()) settings.remove(MEDIA_KEY);
                else settings.put(MEDIA_KEY, text.trim());
            } else if (settings.get(MEDIA_KEY) == null) {
                settings.remove(MEDIA_KEY);
            }
            settings.putIfAbsent("background", "NONE");
            settings.putIfAbsent("align", "LEFT");
            settings.putIfAbsent("paddingY", "NORMAL");
            settings.putIfAbsent("fullWidth", Boolean.FALSE);
            if (!"IMAGE".equals(settings.get("background"))) settings.remove(MEDIA_KEY);
            out.put("settings", settings);
        }
        // AG4: legacy types never carry content; STEPS/FEATURE/GALLERY/SOCIAL/RICH_TEXT keep a per-type whitelist.
        out.put("content", ShopThemeBlockContent.sanitize(ShopBlockType.from(string(out.get("type"))), block.get("content")));
        return out;
    }

    /** Save path (after validation): attach the jsoup-sanitized HTML of every RICH_TEXT block. */
    static void finalizeContent(Map<String, Object> config) {
        var layout = config == null ? null : asMap(config.get("layout"));
        if (layout == null || !(layout.get("blocks") instanceof List<?> blocks)) return;
        for (var item : blocks) {
            var block = asMap(item);
            if (block != null) ShopThemeBlockContent.attachRichTextHtml(block);
        }
    }

    // ───────────────────────── presence / migration ─────────────────────────

    static boolean hasBlocks(Map<String, Object> config) {
        var layout = config == null ? null : asMap(config.get("layout"));
        return layout != null && layout.get("blocks") instanceof List<?> list && !list.isEmpty();
    }

    /** Save path: synthesize blocks when missing, otherwise keep the legacy {@code sections} list in sync. */
    static void ensureOnSave(Map<String, Object> config) {
        if (hasBlocks(config)) syncSections(config);
        else putSynthesized(config);
    }

    /** Read path: CUSTOM themes saved before AG3 get blocks built from {@code sections} + {@code sectionSettings}. */
    static void ensureOnRead(Map<String, Object> config) {
        if (!hasBlocks(config)) putSynthesized(config);
    }

    private static void putSynthesized(Map<String, Object> config) {
        var layout = new LinkedHashMap<String, Object>();
        layout.put("blocks", synthesize(config));
        config.put("layout", layout);
    }

    /** Build blocks from the legacy sections, preserving order, enabled flag, variant and per-section settings. */
    @SuppressWarnings("unchecked")
    static List<Object> synthesize(Map<String, Object> config) {
        var blocks = new ArrayList<Object>();
        var used = new HashSet<String>();
        var settingsByKey = asMap(config.get("sectionSettings"));
        var hero = asMap(config.get("hero"));
        if (!(config.get("sections") instanceof List<?> sections)) return blocks;
        for (var item : sections) {
            var row = asMap(item);
            if (row == null) continue;
            var key = ShopSectionKey.from(string(row.get("key")));
            if (key == null) continue;

            var block = new LinkedHashMap<String, Object>();
            block.put("id", uniqueId(legacyId(key), used));
            block.put("type", key.name());
            block.put("enabled", Boolean.TRUE.equals(row.get("enabled")));
            if (key.allowsVariant()) {
                var variant = ShopSectionVariant.from(string(row.get("variant")));
                if ((variant == null || !variant.allowedFor(key)) && key == ShopSectionKey.HERO && hero != null) {
                    variant = ShopSectionVariant.from(string(hero.get("layout")));
                }
                if (variant == null || !variant.allowedFor(key)) variant = defaultVariant(key);
                block.put("variant", variant.name());
            }

            var legacy = settingsByKey == null ? null : asMap(settingsByKey.get(key.name()));
            var settings = new LinkedHashMap<String, Object>();
            var background = upperString(legacy == null ? null : legacy.get("background"));
            settings.put("background", background != null && List.of("NONE", "SURFACE", "PRIMARY", "GRADIENT").contains(background) ? background : "NONE");
            var align = upperString(legacy == null ? null : legacy.get("align"));
            settings.put("align", align != null && ALIGNS.contains(align) ? align : "LEFT");
            var padding = upperString(legacy == null ? null : legacy.get("paddingY"));
            settings.put("paddingY", padding != null && PADDINGS.contains(padding) ? padding : "NORMAL");
            if (PRODUCT_TYPES.contains(key) && legacy != null) {
                var columns = intValue(legacy.get("columns"));
                if (columns != null && columns >= 2 && columns <= 5) settings.put("columns", columns);
            }
            settings.put("fullWidth", Boolean.FALSE);
            block.put("settings", settings);
            block.put("content", new LinkedHashMap<String, Object>());
            blocks.add(block);
        }
        return blocks;
    }

    // ───────────────────────── sections sync ─────────────────────────

    /**
     * Regenerate the legacy {@code sections} list from the blocks: one row per type in first-seen order (enabled flag
     * and variant of the first block of that type), then any remaining type appended as disabled.
     */
    @SuppressWarnings("unchecked")
    static void syncSections(Map<String, Object> config) {
        var layout = asMap(config.get("layout"));
        if (layout == null || !(layout.get("blocks") instanceof List<?> blocks)) return;
        var previous = new LinkedHashMap<ShopSectionKey, Map<String, Object>>();
        if (config.get("sections") instanceof List<?> list) {
            for (var item : list) {
                var row = asMap(item);
                var key = row == null ? null : ShopSectionKey.from(string(row.get("key")));
                if (key != null) previous.putIfAbsent(key, row);
            }
        }
        var sections = new ArrayList<Object>();
        var seen = new HashSet<ShopSectionKey>();
        for (var item : blocks) {
            var block = asMap(item);
            var key = block == null ? null : ShopSectionKey.from(string(block.get("type")));
            if (key == null || !seen.add(key)) continue;
            sections.add(sectionRow(key, Boolean.TRUE.equals(block.get("enabled")), variantFor(key, block, previous.get(key))));
        }
        for (var key : ShopSectionKey.values()) {
            if (seen.contains(key)) continue;
            sections.add(sectionRow(key, false, variantFor(key, null, previous.get(key))));
        }
        config.put("sections", sections);

        var hero = asMap(config.get("hero"));
        if (hero != null) {
            for (var item : sections) {
                var row = (Map<String, Object>) item;
                if ("HERO".equals(row.get("key")) && row.get("variant") != null) hero.put("layout", row.get("variant"));
            }
        }
    }

    private static String variantFor(ShopSectionKey key, Map<String, Object> block, Map<String, Object> previous) {
        if (!key.allowsVariant()) return null;
        var variant = ShopSectionVariant.from(block == null ? null : string(block.get("variant")));
        if (variant == null || !variant.allowedFor(key)) {
            variant = ShopSectionVariant.from(previous == null ? null : string(previous.get("variant")));
        }
        if (variant == null || !variant.allowedFor(key)) variant = defaultVariant(key);
        return variant.name();
    }

    private static Map<String, Object> sectionRow(ShopSectionKey key, boolean enabled, String variant) {
        var row = new LinkedHashMap<String, Object>();
        row.put("key", key.name());
        row.put("enabled", enabled);
        row.put("variant", variant);
        return row;
    }

    // ───────────────────────── read-side media ─────────────────────────

    /** Derive the public {@code backgroundUrl} for IMAGE blocks (never stored; the whitelist drops it on save). */
    static void resolveMedia(String slug, Map<String, Object> config) {
        var layout = config == null ? null : asMap(config.get("layout"));
        if (layout == null || !(layout.get("blocks") instanceof List<?> blocks)) return;
        for (var item : blocks) {
            var block = asMap(item);
            if (block != null) ShopThemeBlockContent.resolveMedia(slug, block);
            var settings = block == null ? null : asMap(block.get("settings"));
            if (settings == null) continue;
            var raw = string(settings.get(MEDIA_KEY));
            if (raw == null || raw.isBlank()) continue;
            try {
                settings.put("backgroundUrl", ShopThemeMediaService.publicUrl(slug, UUID.fromString(raw.trim())));
            } catch (IllegalArgumentException ignored) {
                settings.remove(MEDIA_KEY);
            }
        }
    }

    // ───────────────────────── small utils ─────────────────────────

    static ShopSectionVariant defaultVariant(ShopSectionKey key) {
        return key == ShopSectionKey.HERO ? ShopSectionVariant.FULL : ShopSectionVariant.GRID;
    }

    private static String legacyId(ShopSectionKey key) {
        if (key == ShopSectionKey.HERO) return "legacy-hero";
        if (key == ShopSectionKey.FEATURED) return "legacy-featured";
        return "sec-" + key.name().toLowerCase().replace('_', '-');
    }

    private static String uniqueId(String base, Set<String> used) {
        var id = base;
        int n = 2;
        while (!used.add(id)) id = base + "-" + n++;
        return id;
    }

    private static Map<String, Object> keep(Map<String, Object> source, Set<String> allowed) {
        var out = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            if (allowed.contains(entry.getKey())) out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static void upper(Map<String, Object> map, String key) {
        if (map.get(key) instanceof String text) map.put(key, text.trim().toUpperCase());
    }

    private static void coerceInt(Map<String, Object> map, String key) {
        if (map.get(key) instanceof String text) {
            try {
                map.put(key, Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) { /* left as string → validation error */ }
        }
    }

    static Integer intValue(Object raw) {
        if (raw instanceof Integer i) return i;
        if (raw instanceof Number number && number.doubleValue() == Math.floor(number.doubleValue())) return number.intValue();
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) { /* fall through */ }
        }
        return null;
    }

    private static String upperString(Object raw) {
        return raw == null ? null : String.valueOf(raw).trim().toUpperCase();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
