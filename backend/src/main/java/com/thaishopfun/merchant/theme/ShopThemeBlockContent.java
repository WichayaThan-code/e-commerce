package com.thaishopfun.merchant.theme;

import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationMessages;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * AG4: per-type {@code content} payloads of the CUSTOM-only block types (STEPS, FEATURE, GALLERY, SOCIAL, RICH_TEXT).
 * Pure helpers (no Spring): whitelist + normalize ({@link #sanitize}), structural validation ({@link #validate}),
 * media references for the library check ({@link #mediaRefs}) and derived public URLs ({@link #resolveMedia}).
 * Product ownership and the sanitized rich-text HTML are handled by {@link ShopThemeService} / {@link #attachRichTextHtml}.
 *
 * <p>All merchant text is {@code LocalizedString} ({@code {th, en}}, {@code en} optional). Only structured data is
 * accepted: social embeds are TikTok video ids (the storefront builds the iframe URL), never embed HTML.
 */
final class ShopThemeBlockContent {
    static final Pattern TIKTOK_ID = Pattern.compile("^\\d{10,25}$");

    static final List<String> STEP_ICONS = List.of("AUTHENTIC", "FAST_SHIP", "RETURNS", "SECURE_PAY", "CUSTOM", "NUMBER");
    static final List<String> FEATURE_LAYOUTS = List.of("IMAGE_LEFT", "IMAGE_RIGHT");
    static final List<String> GALLERY_LAYOUTS = List.of("GRID", "MASONRY", "CAROUSEL");
    /** Allowlist; INSTAGRAM is structurally possible later but not accepted yet. */
    static final List<String> SOCIAL_PROVIDERS = List.of("TIKTOK");

    static final int MIN_STEPS = 2;
    static final int MAX_STEPS = 6;
    static final int MAX_BULLETS = 5;
    static final int MAX_GALLERY_ITEMS = 24;
    static final int MAX_VIDEOS = 6;
    static final int MAX_COLLECTION_CARDS = 12;
    static final int DEFAULT_COLLECTION_CARDS = 6;
    private static final Pattern COLLECTION_SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private static final int TITLE_MAX = 120;
    private static final int SUBTITLE_MAX = 240;
    private static final int STEP_TITLE_MAX = 80;
    private static final int STEP_BODY_MAX = 240;
    private static final int FEATURE_BODY_MAX = 1000;
    private static final int BULLET_MAX = 120;
    private static final int CAPTION_MAX = 160;
    private static final int CTA_LABEL_MAX = 60;
    private static final int LINK_MAX = 500;

    private static final Pattern INTERNAL_PATH = Pattern.compile("^/(?!/)[\\w\\-./?#=&%~+:@,]*$");
    private static final Pattern HTTPS = Pattern.compile("^https://[\\w\\-./?#=&%~+:@,]+$", Pattern.CASE_INSENSITIVE);

    private static final String INVALID = "THEME_BLOCK_CONTENT_INVALID";

    private ShopThemeBlockContent() {}

    // ------------------------- whitelist / normalize -------------------------

    /** Keeps only the whitelisted keys of the type; legacy types and non-map payloads become an empty content map. */
    static Map<String, Object> sanitize(ShopBlockType type, Object raw) {
        var out = new LinkedHashMap<String, Object>();
        if (type == null || !type.hasContent()) return out;
        var source = asMap(raw);
        if (source == null) source = new LinkedHashMap<>(); // missing / non-map content behaves like {} (defaults are filled below)
        switch (type) {
            case STEPS -> {
                putLocalized(out, "title", source.get("title"));
                putLocalized(out, "subtitle", source.get("subtitle"));
                if (source.containsKey("steps")) out.put("steps", sanitizeList(source.get("steps"), ShopThemeBlockContent::sanitizeStep));
                putCta(out, source.get("cta"));
            }
            case FEATURE -> {
                putText(out, "mediaId", source.get("mediaId"));
                ShopThemeImageCrop.copyCropsIfMedia(out, source, out.get("mediaId") != null);
                putLocalized(out, "title", source.get("title"));
                putLocalized(out, "body", source.get("body"));
                if (source.get("bullets") instanceof List<?> bullets) {
                    var list = new ArrayList<Object>();
                    for (var bullet : bullets) {
                        var localized = localized(bullet);
                        if (localized != null) list.add(localized);
                    }
                    out.put("bullets", list);
                } else if (source.get("bullets") != null) {
                    out.put("bullets", source.get("bullets")); // flagged by validation
                }
                putCta(out, source.get("cta"));
                putText(out, "productId", source.get("productId"));
                out.put("layout", upperOr(source.get("layout"), "IMAGE_LEFT"));
            }
            case GALLERY -> {
                putLocalized(out, "title", source.get("title"));
                if (source.containsKey("items")) out.put("items", sanitizeList(source.get("items"), ShopThemeBlockContent::sanitizeGalleryItem));
                out.put("layout", upperOr(source.get("layout"), "GRID"));
            }
            case SOCIAL -> {
                putLocalized(out, "title", source.get("title"));
                out.put("provider", upperOr(source.get("provider"), "TIKTOK"));
                if (source.get("videoIds") instanceof List<?> ids) {
                    var list = new ArrayList<Object>();
                    for (var id : ids) {
                        if (id instanceof String text) {
                            if (!text.isBlank()) list.add(text.trim());
                        } else if (id != null) {
                            list.add(id); // flagged by validation (numbers, objects, ...)
                        }
                    }
                    out.put("videoIds", list);
                } else if (source.get("videoIds") != null) {
                    out.put("videoIds", source.get("videoIds"));
                } else {
                    out.put("videoIds", new ArrayList<>());
                }
            }
            case RICH_TEXT -> {
                var markdown = localized(source.get("markdown"));
                if (markdown != null) out.put("markdown", markdown);
            }
            case COLLECTIONS -> {
                putLocalized(out, "title", source.get("title"));
                putLocalized(out, "subtitle", source.get("subtitle"));
                if (source.get("collectionSlugs") instanceof List<?> slugs) {
                    var list = new ArrayList<Object>();
                    for (var slug : slugs) {
                        if (slug instanceof String text) {
                            if (!text.isBlank()) list.add(text.trim().toLowerCase(java.util.Locale.ROOT));
                        } else if (slug != null) {
                            list.add(slug); // flagged by validation
                        }
                    }
                    out.put("collectionSlugs", list);
                } else if (source.get("collectionSlugs") != null) {
                    out.put("collectionSlugs", source.get("collectionSlugs"));
                } else {
                    out.put("collectionSlugs", new ArrayList<>());
                }
                var limit = source.get("limit");
                out.put("limit", limit == null ? DEFAULT_COLLECTION_CARDS : limit);
            }
            default -> { /* legacy types carry no content */ }
        }
        return out;
    }

    private static Object sanitizeStep(Object item) {
        var row = asMap(item);
        if (row == null) return item;
        var step = new LinkedHashMap<String, Object>();
        var icon = upperOr(row.get("icon"), "NUMBER");
        step.put("icon", icon);
        // A custom image only makes sense (and is only counted as "in use") for the CUSTOM icon.
        if ("CUSTOM".equals(icon)) {
            putText(step, "mediaId", row.get("mediaId"));
            ShopThemeImageCrop.copyCropsIfMedia(step, row, step.get("mediaId") != null);
        }
        putLocalized(step, "title", row.get("title"));
        putLocalized(step, "body", row.get("body"));
        return step;
    }

    private static Object sanitizeGalleryItem(Object item) {
        var row = asMap(item);
        if (row == null) return item;
        var media = row.get("mediaId") instanceof String text ? text.trim() : null;
        if (media == null || media.isEmpty()) return null; // an item without an image is dropped
        var out = new LinkedHashMap<String, Object>();
        out.put("mediaId", media);
        ShopThemeImageCrop.copyCropsIfMedia(out, row, true);
        putLocalized(out, "caption", row.get("caption"));
        putText(out, "link", row.get("link"));
        return out;
    }

    private static List<Object> sanitizeListResult(List<?> list, java.util.function.Function<Object, Object> mapper) {
        var out = new ArrayList<Object>();
        for (var item : list) {
            var mapped = mapper.apply(item);
            if (mapped != null) out.add(mapped);
        }
        return out;
    }

    private static Object sanitizeList(Object raw, java.util.function.Function<Object, Object> mapper) {
        return raw instanceof List<?> list ? sanitizeListResult(list, mapper) : raw; // non-list flagged by validation
    }

    private static void putCta(Map<String, Object> out, Object raw) {
        if (raw == null) return;
        var cta = asMap(raw);
        if (cta == null) {
            out.put("cta", raw); // flagged by validation
            return;
        }
        var clean = new LinkedHashMap<String, Object>();
        putLocalized(clean, "label", cta.get("label"));
        putText(clean, "link", cta.get("link"));
        if (!clean.isEmpty()) out.put("cta", clean);
    }

    /** Trimmed string kept when non-blank; non-strings are kept as-is so validation can flag them. */
    private static void putText(Map<String, Object> out, String key, Object raw) {
        if (raw == null) return;
        if (raw instanceof String text) {
            var trimmed = text.trim();
            if (!trimmed.isEmpty()) out.put(key, trimmed);
        } else {
            out.put(key, raw);
        }
    }

    private static void putLocalized(Map<String, Object> out, String key, Object raw) {
        var value = localized(raw);
        if (value != null) out.put(key, value);
    }

    /** {@code {th, en?}} with trimmed strings; {@code null} when entirely blank; odd shapes are kept for validation. */
    static Object localized(Object raw) {
        if (raw == null) return null;
        if (raw instanceof String text) {
            var trimmed = normalizeText(text);
            return trimmed.isEmpty() ? null : thOnly(trimmed);
        }
        var map = asMap(raw);
        if (map == null) return raw;
        var out = new LinkedHashMap<String, Object>();
        for (var key : List.of("th", "en")) {
            var value = map.get(key);
            if (value == null) continue;
            if (value instanceof String text) {
                var trimmed = normalizeText(text);
                if (!trimmed.isEmpty()) out.put(key, trimmed);
            } else {
                out.put(key, value);
            }
        }
        if (out.isEmpty()) return null;
        if (!out.containsKey("th") && out.get("en") instanceof String) out.put("th", "");
        return reorder(out);
    }

    private static Map<String, Object> thOnly(String text) {
        var out = new LinkedHashMap<String, Object>();
        out.put("th", text);
        return out;
    }

    private static Map<String, Object> reorder(Map<String, Object> map) {
        var out = new LinkedHashMap<String, Object>();
        if (map.containsKey("th")) out.put("th", map.get("th"));
        if (map.containsKey("en")) out.put("en", map.get("en"));
        return out;
    }

    private static String normalizeText(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static String upperOr(Object raw, String fallback) {
        if (raw instanceof String text) return text.isBlank() ? fallback : text.trim().toUpperCase();
        return raw == null ? fallback : String.valueOf(raw);
    }

    // ------------------------- validation -------------------------

    /** Structural checks of an AG4 block's content ({@code prefix} is e.g. {@code config.layout.blocks[2].content}). */
    static void validate(List<FieldViolation> problems, String prefix, ShopBlockType type, Map<String, Object> content) {
        if (type == null || !type.hasContent() || content == null) return;
        switch (type) {
            case STEPS -> validateSteps(problems, prefix, content);
            case FEATURE -> validateFeature(problems, prefix, content);
            case GALLERY -> validateGallery(problems, prefix, content);
            case SOCIAL -> validateSocial(problems, prefix, content);
            case RICH_TEXT -> validateRichText(problems, prefix, content);
            case COLLECTIONS -> validateCollections(problems, prefix, content);
            default -> { /* legacy */ }
        }
    }

    private static void validateSteps(List<FieldViolation> problems, String prefix, Map<String, Object> content) {
        checkLocalized(problems, prefix + ".title", content.get("title"), TITLE_MAX);
        checkLocalized(problems, prefix + ".subtitle", content.get("subtitle"), SUBTITLE_MAX);
        if (!(content.get("steps") instanceof List<?> steps) || steps.size() < MIN_STEPS || steps.size() > MAX_STEPS) {
            problems.add(ValidationMessages.violation(prefix + ".steps", INVALID));
        } else {
            for (int i = 0; i < steps.size(); i++) {
                var path = prefix + ".steps[" + i + "]";
                var step = asMap(steps.get(i));
                if (step == null) {
                    problems.add(ValidationMessages.violation(path, INVALID));
                    continue;
                }
                if (!(step.get("icon") instanceof String icon) || !STEP_ICONS.contains(icon)) {
                    problems.add(ValidationMessages.violation(path + ".icon", INVALID));
                }
                checkLocalized(problems, path + ".title", step.get("title"), STEP_TITLE_MAX);
                checkLocalized(problems, path + ".body", step.get("body"), STEP_BODY_MAX);
                checkMediaIdShape(problems, path + ".mediaId", step.get("mediaId"));
                if ("CUSTOM".equals(step.get("icon"))) {
                    ShopThemeImageCrop.validateHolderCrops(problems, path, step);
                }
            }
        }
        checkCta(problems, prefix + ".cta", content.get("cta"));
    }

    private static void validateFeature(List<FieldViolation> problems, String prefix, Map<String, Object> content) {
        checkMediaIdShape(problems, prefix + ".mediaId", content.get("mediaId"));
        ShopThemeImageCrop.validateHolderCrops(problems, prefix, content);
        checkLocalized(problems, prefix + ".title", content.get("title"), TITLE_MAX);
        checkLocalized(problems, prefix + ".body", content.get("body"), FEATURE_BODY_MAX);
        if (content.get("bullets") != null) {
            if (!(content.get("bullets") instanceof List<?> bullets) || bullets.size() > MAX_BULLETS) {
                problems.add(ValidationMessages.violation(prefix + ".bullets", INVALID));
            } else {
                for (int i = 0; i < bullets.size(); i++) {
                    checkLocalized(problems, prefix + ".bullets[" + i + "]", bullets.get(i), BULLET_MAX);
                }
            }
        }
        checkCta(problems, prefix + ".cta", content.get("cta"));
        if (content.get("productId") != null && productId(content) == null) {
            problems.add(ValidationMessages.violation(prefix + ".productId", "THEME_PRODUCT_INVALID"));
        }
        if (!(content.get("layout") instanceof String layout) || !FEATURE_LAYOUTS.contains(layout)) {
            problems.add(ValidationMessages.violation(prefix + ".layout", INVALID));
        }
    }

    private static void validateGallery(List<FieldViolation> problems, String prefix, Map<String, Object> content) {
        checkLocalized(problems, prefix + ".title", content.get("title"), TITLE_MAX);
        if (!(content.get("layout") instanceof String layout) || !GALLERY_LAYOUTS.contains(layout)) {
            problems.add(ValidationMessages.violation(prefix + ".layout", INVALID));
        }
        if (content.get("items") == null) return;
        if (!(content.get("items") instanceof List<?> items) || items.size() > MAX_GALLERY_ITEMS) {
            problems.add(ValidationMessages.violation(prefix + ".items", INVALID));
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            var path = prefix + ".items[" + i + "]";
            var item = asMap(items.get(i));
            if (item == null) {
                problems.add(ValidationMessages.violation(path, INVALID));
                continue;
            }
            if (!(item.get("mediaId") instanceof String media) || media.isBlank()) {
                problems.add(ValidationMessages.violation(path + ".mediaId", INVALID));
            }
            ShopThemeImageCrop.validateHolderCrops(problems, path, item);
            checkLocalized(problems, path + ".caption", item.get("caption"), CAPTION_MAX);
            checkLink(problems, path + ".link", item.get("link"));
        }
    }

    private static void validateSocial(List<FieldViolation> problems, String prefix, Map<String, Object> content) {
        checkLocalized(problems, prefix + ".title", content.get("title"), TITLE_MAX);
        if (!(content.get("provider") instanceof String provider) || !SOCIAL_PROVIDERS.contains(provider)) {
            problems.add(ValidationMessages.violation(prefix + ".provider", "THEME_SOCIAL_INVALID"));
        }
        if (!(content.get("videoIds") instanceof List<?> ids) || ids.size() > MAX_VIDEOS) {
            problems.add(ValidationMessages.violation(prefix + ".videoIds", "THEME_SOCIAL_INVALID"));
            return;
        }
        for (int i = 0; i < ids.size(); i++) {
            if (!(ids.get(i) instanceof String id) || !TIKTOK_ID.matcher(id).matches()) {
                problems.add(ValidationMessages.violation(prefix + ".videoIds[" + i + "]", "THEME_SOCIAL_INVALID"));
            }
        }
    }

    private static void validateRichText(List<FieldViolation> problems, String prefix, Map<String, Object> content) {
        var markdown = content.get("markdown");
        if (markdown == null) return;
        var map = asMap(markdown);
        if (map == null) {
            problems.add(ValidationMessages.violation(prefix + ".markdown", "THEME_RICHTEXT_INVALID"));
            return;
        }
        for (var key : List.of("th", "en")) {
            var value = map.get(key);
            if (value == null) continue;
            if (!(value instanceof String text) || ShopThemeRichText.render(text) == null) {
                problems.add(ValidationMessages.violation(prefix + ".markdown." + key, "THEME_RICHTEXT_INVALID"));
            }
        }
    }

    /** V41: only slugs + a card count; the storefront resolves them against the public (published-only) list. */
    private static void validateCollections(List<FieldViolation> problems, String prefix, Map<String, Object> content) {
        checkLocalized(problems, prefix + ".title", content.get("title"), TITLE_MAX);
        checkLocalized(problems, prefix + ".subtitle", content.get("subtitle"), SUBTITLE_MAX);
        if (!(content.get("collectionSlugs") instanceof List<?> slugs) || slugs.size() > MAX_COLLECTION_CARDS) {
            problems.add(ValidationMessages.violation(prefix + ".collectionSlugs", INVALID));
        } else {
            var seen = new java.util.HashSet<Object>();
            for (int i = 0; i < slugs.size(); i++) {
                if (!(slugs.get(i) instanceof String slug) || slug.length() > 80 || !COLLECTION_SLUG.matcher(slug).matches() || !seen.add(slug)) {
                    problems.add(ValidationMessages.violation(prefix + ".collectionSlugs[" + i + "]", INVALID));
                }
            }
        }
        if (!(content.get("limit") instanceof Integer limit) || limit < 1 || limit > MAX_COLLECTION_CARDS) {
            problems.add(ValidationMessages.violation(prefix + ".limit", INVALID));
        }
    }

    static void checkLocalized(List<FieldViolation> problems, String field, Object raw, int max) {
        if (raw == null) return;
        var map = asMap(raw);
        if (map == null) {
            problems.add(ValidationMessages.violation(field, INVALID));
            return;
        }
        for (var key : List.of("th", "en")) {
            var value = map.get(key);
            if (value == null) continue;
            if (!(value instanceof String text) || text.length() > max) {
                problems.add(ValidationMessages.violation(field + "." + key, INVALID));
            }
        }
    }

    private static void checkCta(List<FieldViolation> problems, String field, Object raw) {
        if (raw == null) return;
        var cta = asMap(raw);
        if (cta == null) {
            problems.add(ValidationMessages.violation(field, INVALID));
            return;
        }
        checkLocalized(problems, field + ".label", cta.get("label"), CTA_LABEL_MAX);
        checkLink(problems, field + ".link", cta.get("link"));
    }

    static void checkLink(List<FieldViolation> problems, String field, Object raw) {
        if (raw == null) return;
        if (!(raw instanceof String link) || link.length() > LINK_MAX
            || !(INTERNAL_PATH.matcher(link).matches() || HTTPS.matcher(link).matches())) {
            problems.add(ValidationMessages.violation(field, "THEME_BLOCK_LINK_INVALID"));
        }
    }

    private static void checkMediaIdShape(List<FieldViolation> problems, String field, Object raw) {
        if (raw == null) return;
        if (!(raw instanceof String text) || parseUuid(text) == null) {
            problems.add(ValidationMessages.violation(field, "THEME_MEDIA_INVALID"));
        }
    }

    // ------------------------- media / product -------------------------

    /** (path relative to the block, raw mediaId) for every image the content references. */
    static List<Map.Entry<String, Object>> mediaRefs(Map<String, Object> block) {
        var out = new ArrayList<Map.Entry<String, Object>>();
        var type = block == null ? null : ShopBlockType.from(block.get("type") == null ? null : String.valueOf(block.get("type")));
        var content = block == null ? null : asMap(block.get("content"));
        if (type == null || !type.hasContent() || content == null) return out;
        switch (type) {
            case STEPS -> {
                if (content.get("steps") instanceof List<?> steps) {
                    for (int i = 0; i < steps.size(); i++) {
                        var step = asMap(steps.get(i));
                        if (step != null && step.get("mediaId") != null) {
                            out.add(new AbstractMap.SimpleEntry<>("content.steps[" + i + "].mediaId", step.get("mediaId")));
                        }
                    }
                }
            }
            case FEATURE -> {
                if (content.get("mediaId") != null) out.add(new AbstractMap.SimpleEntry<>("content.mediaId", content.get("mediaId")));
            }
            case GALLERY -> {
                if (content.get("items") instanceof List<?> items) {
                    for (int i = 0; i < items.size(); i++) {
                        var item = asMap(items.get(i));
                        if (item != null && item.get("mediaId") != null) {
                            out.add(new AbstractMap.SimpleEntry<>("content.items[" + i + "].mediaId", item.get("mediaId")));
                        }
                    }
                }
            }
            default -> { /* no media */ }
        }
        return out;
    }

    /** FEATURE {@code productId} as a UUID, or {@code null} when absent / malformed. */
    static UUID productId(Map<String, Object> content) {
        return content != null && content.get("productId") instanceof String text ? parseUuid(text) : null;
    }

    /** Derive public image URLs (never stored; the whitelist drops them on the next save). */
    static void resolveMedia(String slug, Map<String, Object> block) {
        var type = ShopBlockType.from(block.get("type") == null ? null : String.valueOf(block.get("type")));
        var content = asMap(block.get("content"));
        if (type == null || !type.hasContent() || content == null) return;
        switch (type) {
            case STEPS -> {
                if (content.get("steps") instanceof List<?> steps) {
                    for (var item : steps) {
                        var step = asMap(item);
                        if (step != null) putUrl(slug, step, "mediaUrl");
                    }
                }
            }
            case FEATURE -> putUrl(slug, content, "mediaUrl");
            case GALLERY -> {
                if (content.get("items") instanceof List<?> items) {
                    for (var item : items) {
                        var row = asMap(item);
                        if (row != null) putUrl(slug, row, "url");
                    }
                }
            }
            default -> { /* no media */ }
        }
    }

    private static void putUrl(String slug, Map<String, Object> holder, String urlKey) {
        var raw = holder.get("mediaId") instanceof String text ? text : null;
        if (raw == null || raw.isBlank()) return;
        var id = parseUuid(raw);
        if (id == null) holder.remove("mediaId");
        else holder.put(urlKey, ShopThemeMediaService.publicUrl(slug, id));
    }

    /** Save path: store the jsoup-sanitized HTML next to each rich-text markdown (the client can never set it). */
    static void attachRichTextHtml(Map<String, Object> block) {
        var type = ShopBlockType.from(block.get("type") == null ? null : String.valueOf(block.get("type")));
        if (type != ShopBlockType.RICH_TEXT) return;
        var content = asMap(block.get("content"));
        var markdown = content == null ? null : asMap(content.get("markdown"));
        if (markdown == null) {
            if (content != null) content.remove("html");
            return;
        }
        var html = new LinkedHashMap<String, Object>();
        for (var key : List.of("th", "en")) {
            if (markdown.get(key) instanceof String text) {
                var rendered = ShopThemeRichText.render(text);
                if (rendered != null) html.put(key, rendered);
            }
        }
        content.put("html", html);
    }

    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}
