package com.thaishopfun.merchant.theme;

import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationMessages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * AG5: CUSTOM-only storefront "chrome" — the MEDIA_FULL hero extras ({@code config.hero.*}), {@code config.header},
 * {@code config.footer} and {@code config.fontCustom}. Pure helpers (no Spring): whitelist / normalize
 * ({@link #sanitize}), non-CUSTOM stripping ({@link #stripForNonCustom}), structural validation ({@link #validate}),
 * media references for the library check ({@link #mediaRefs}) and derived public URLs ({@link #resolveMedia}).
 *
 * <p>Only structured data is accepted: colors are {@code #RRGGBB}, links are internal paths or https, backgrounds are
 * enum tokens plus structured gradients, fonts are references into the shop's own FONT media. Merchants never supply CSS.
 */
final class ShopThemeChrome {
    static final List<String> HERO_BACKGROUND_TYPES = List.of("IMAGE", "VIDEO", "GRADIENT");
    static final List<String> TEXT_ALIGNS = List.of("LEFT", "CENTER");
    static final List<String> HEADLINE_SIZES = List.of("L", "XL");
    static final List<String> HEADER_LAYOUTS = List.of("LOGO_LEFT", "LOGO_CENTER");

    static final int MAX_STICKERS = 6;
    static final int MAX_MENU_ITEMS = 8;
    static final int MAX_FOOTER_COLUMNS = 4;
    static final int MAX_COLUMN_LINKS = 8;

    private static final Set<String> HERO_EXTRA_KEYS = Set.of(
        "background", "overlay", "heightVh", "textAlign", "headlineSize", "stickers", "ctaSecondary"
    );
    private static final Set<String> HERO_BACKGROUND_KEYS = Set.of("type", "mediaId", "posterMediaId");
    private static final Set<String> STICKER_KEYS = Set.of("mediaId", "x", "y", "size", "rotate", "hideOnMobile");
    private static final Set<String> CTA_KEYS = Set.of("label", "link");
    private static final Set<String> HEADER_KEYS = Set.of(
        "layout", "background", "gradient", "backgroundMediaId", "overlay", "sticky", "menu",
        "showSearch", "showSocialIcons", "announcementBar"
    );
    private static final Set<String> FOOTER_KEYS = Set.of(
        "columns", "about", "showContacts", "showSocial", "background", "gradient", "backgroundMediaId", "overlay", "copyright"
    );
    private static final Set<String> MENU_KEYS = Set.of("label", "link");
    private static final Set<String> COLUMN_KEYS = Set.of("title", "links");
    private static final Set<String> ANNOUNCEMENT_KEYS = Set.of("text", "link", "backgroundColor", "textColor");
    private static final Set<String> FONT_CUSTOM_KEYS = Set.of("headingMediaId", "bodyMediaId", "fallback");
    private static final Set<String> GRADIENT_KEYS = Set.of("angle", "stops");
    private static final Set<String> GRADIENT_STOP_KEYS = Set.of("color", "at");

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern INTERNAL_PATH = Pattern.compile("^/(?!/)[\\w\\-./?#=&%~+:@,]*$");
    private static final Pattern HTTPS = Pattern.compile("^https://[\\w\\-./?#=&%~+:@,]+$", Pattern.CASE_INSENSITIVE);

    private static final String TOKEN = "THEME_TOKEN_INVALID";
    private static final String CHROME = "THEME_CHROME_INVALID";
    private static final String CONTENT = "THEME_BLOCK_CONTENT_INVALID";
    private static final String MEDIA = "THEME_MEDIA_INVALID";

    private static final int HERO_CTA_MAX = 120;
    private static final int MENU_LABEL_MAX = 40;
    private static final int COLUMN_TITLE_MAX = 60;
    private static final int ANNOUNCEMENT_MAX = 200;
    private static final int ABOUT_MAX = 500;
    private static final int COPYRIGHT_MAX = 200;

    /** Reused AG3 gradient validation (angle + 2–4 hex stops), supplied by {@link ShopThemeService}. */
    @FunctionalInterface
    interface GradientCheck {
        void check(List<FieldViolation> problems, String prefix, Object raw);
    }

    /** A media id the chrome references, with the library kind it must have. */
    record MediaRef(String field, Object raw, ShopThemeMediaKind kind) {}

    private ShopThemeChrome() {}

    // ───────────────────────── non-CUSTOM ─────────────────────────

    /** GENERAL / FASHION / FOOD_BEVERAGE: drop header / footer / fontCustom / hero extras; MEDIA_FULL silently becomes FULL. */
    static void stripForNonCustom(Map<String, Object> config) {
        stripForNonCustom(config, false);
    }

    /**
     * Themes port: GENERAL may keep the MEDIA_FULL hero layout (centred poster, no CUSTOM extras such as stickers /
     * background media); FASHION / FOOD_BEVERAGE still fall back to FULL.
     */
    static void stripForNonCustom(Map<String, Object> config, boolean keepMediaFull) {
        if (config == null) return;
        config.remove("header");
        config.remove("footer");
        config.remove("fontCustom");
        var hero = asMap(config.get("hero"));
        if (hero != null) {
            for (var key : HERO_EXTRA_KEYS) hero.remove(key);
            if (!keepMediaFull && isMediaFull(hero.get("layout"))) hero.put("layout", ShopSectionVariant.FULL.name());
        }
        if (config.get("sections") instanceof List<?> sections) {
            for (var item : sections) {
                var row = asMap(item);
                if (!keepMediaFull && row != null && isMediaFull(row.get("variant"))) row.put("variant", ShopSectionVariant.FULL.name());
            }
        }
    }

    private static boolean isMediaFull(Object raw) {
        return raw != null && ShopSectionVariant.MEDIA_FULL == ShopSectionVariant.from(String.valueOf(raw));
    }

    // ───────────────────────── CUSTOM whitelist / normalize ─────────────────────────

    /** CUSTOM: whitelist and normalize every AG5 key; odd shapes are kept as-is so {@link #validate} can flag them. */
    static void sanitize(Map<String, Object> config) {
        if (config == null) return;
        var hero = asMap(config.get("hero"));
        if (hero != null) sanitizeHero(hero);

        sanitizeSection(config, "header", ShopThemeChrome::sanitizeHeader);
        sanitizeSection(config, "footer", ShopThemeChrome::sanitizeFooter);

        if (config.containsKey("fontCustom")) {
            var font = asMap(config.get("fontCustom"));
            if (font == null && config.get("fontCustom") == null) {
                config.remove("fontCustom");
            } else if (font != null) {
                var cleaned = sanitizeFontCustom(font, config.get("font"));
                if (cleaned == null) config.remove("fontCustom");
                else config.put("fontCustom", cleaned);
            }
        }
    }

    private static void sanitizeSection(
        Map<String, Object> config, String key, java.util.function.Function<Map<String, Object>, Map<String, Object>> cleaner
    ) {
        if (!config.containsKey(key)) return;
        var raw = config.get(key);
        if (raw == null) {
            config.remove(key);
            return;
        }
        var map = asMap(raw);
        if (map != null) config.put(key, cleaner.apply(map));
    }

    private static void sanitizeHero(Map<String, Object> hero) {
        if (hero.containsKey("background")) {
            var raw = hero.get("background");
            var bg = asMap(raw);
            if (raw == null) {
                hero.remove("background");
            } else if (bg != null) {
                var out = keep(bg, HERO_BACKGROUND_KEYS);
                upper(out, "type");
                textId(out, "mediaId");
                textId(out, "posterMediaId");
                var type = out.get("type");
                if (!"IMAGE".equals(type) && !"VIDEO".equals(type)) {
                    out.remove("mediaId");
                    out.remove("posterMediaId");
                } else if (!"VIDEO".equals(type)) {
                    out.remove("posterMediaId");
                }
                hero.put("background", out);
            }
        }
        for (var key : List.of("overlay", "heightVh")) {
            if (!hero.containsKey(key)) continue;
            coerceInt(hero, key);
            if (hero.get(key) == null) hero.remove(key);
        }
        upper(hero, "textAlign");
        upper(hero, "headlineSize");
        if (hero.containsKey("textAlign") && hero.get("textAlign") == null) hero.remove("textAlign");
        if (hero.containsKey("headlineSize") && hero.get("headlineSize") == null) hero.remove("headlineSize");

        if (hero.containsKey("stickers")) {
            var raw = hero.get("stickers");
            if (raw == null) {
                hero.remove("stickers");
            } else if (raw instanceof List<?> list) {
                var stickers = new ArrayList<Object>();
                for (var item : list) {
                    var map = asMap(item);
                    stickers.add(map == null ? item : sanitizeSticker(map));
                }
                hero.put("stickers", stickers);
            }
        }

        if (hero.containsKey("ctaSecondary")) {
            var raw = hero.get("ctaSecondary");
            var cta = asMap(raw);
            if (raw == null) {
                hero.remove("ctaSecondary");
            } else if (cta != null) {
                var out = keep(cta, CTA_KEYS);
                trimText(out, "label");
                trimText(out, "link");
                if (out.isEmpty()) hero.remove("ctaSecondary");
                else hero.put("ctaSecondary", out);
            }
        }
    }

    private static Map<String, Object> sanitizeSticker(Map<String, Object> source) {
        var out = keep(source, STICKER_KEYS);
        textId(out, "mediaId");
        for (var entry : Map.of("x", 50, "y", 50, "size", 120, "rotate", 0).entrySet()) {
            coerceInt(out, entry.getKey());
            if (out.get(entry.getKey()) == null) out.put(entry.getKey(), entry.getValue());
        }
        if (!(out.get("hideOnMobile") instanceof Boolean) && out.get("hideOnMobile") == null) out.put("hideOnMobile", Boolean.FALSE);
        return out;
    }

    private static Map<String, Object> sanitizeHeader(Map<String, Object> source) {
        var out = keep(source, HEADER_KEYS);
        upper(out, "layout");
        defaultIfNull(out, "layout", "LOGO_LEFT");
        sanitizeBackground(out);
        for (var key : List.of("sticky", "showSearch", "showSocialIcons")) defaultIfNull(out, key, Boolean.FALSE);
        out.put("menu", sanitizeLinkList(out.get("menu"), true));

        if (out.containsKey("announcementBar")) {
            var raw = out.get("announcementBar");
            var bar = asMap(raw);
            if (raw == null) {
                out.remove("announcementBar");
            } else if (bar != null) {
                var clean = keep(bar, ANNOUNCEMENT_KEYS);
                var text = ShopThemeBlockContent.localized(clean.get("text"));
                if (text == null) {
                    out.remove("announcementBar");
                } else {
                    clean.put("text", text);
                    trimText(clean, "link");
                    trimText(clean, "backgroundColor");
                    trimText(clean, "textColor");
                    defaultIfNull(clean, "backgroundColor", "#111111");
                    defaultIfNull(clean, "textColor", "#ffffff");
                    out.put("announcementBar", clean);
                }
            }
        }
        return out;
    }

    private static Map<String, Object> sanitizeFooter(Map<String, Object> source) {
        var out = keep(source, FOOTER_KEYS);
        sanitizeBackground(out);
        for (var key : List.of("showContacts", "showSocial")) defaultIfNull(out, key, Boolean.FALSE);

        var columnsRaw = out.get("columns");
        if (columnsRaw == null) {
            out.put("columns", new ArrayList<>());
        } else if (columnsRaw instanceof List<?> list) {
            var columns = new ArrayList<Object>();
            for (var item : list) {
                var map = asMap(item);
                if (map == null) {
                    columns.add(item);
                    continue;
                }
                var column = keep(map, COLUMN_KEYS);
                var title = ShopThemeBlockContent.localized(column.get("title"));
                if (title == null) column.remove("title");
                else column.put("title", title);
                column.put("links", sanitizeLinkList(column.get("links"), false));
                if (column.get("title") == null && column.get("links") instanceof List<?> links && links.isEmpty()) continue;
                columns.add(column);
            }
            out.put("columns", columns);
        }

        if (out.containsKey("about")) {
            var about = ShopThemeBlockContent.localized(out.get("about"));
            if (about == null) out.remove("about");
            else out.put("about", about);
        }
        if (out.get("copyright") instanceof String text) {
            var trimmed = text.trim();
            if (trimmed.isEmpty()) out.remove("copyright");
            else out.put("copyright", trimmed);
        } else if (out.get("copyright") == null) {
            out.remove("copyright");
        }
        return out;
    }

    /** Header menu / footer column links: {@code {label: LocalizedString, link}}; fully blank rows are dropped. */
    private static Object sanitizeLinkList(Object raw, boolean menu) {
        if (raw == null) return new ArrayList<>();
        if (!(raw instanceof List<?> list)) return raw; // flagged by validation
        var out = new ArrayList<Object>();
        for (var item : list) {
            var map = asMap(item);
            if (map == null) {
                out.add(item);
                continue;
            }
            var row = keep(map, MENU_KEYS);
            var label = ShopThemeBlockContent.localized(row.get("label"));
            if (label == null) row.remove("label");
            else row.put("label", label);
            trimText(row, "link");
            if (row.get("label") == null && row.get("link") == null) continue;
            out.add(row);
        }
        return out;
    }

    /** Header / footer background tokens (same shape as {@code layout.blocks[].settings}). */
    private static void sanitizeBackground(Map<String, Object> out) {
        upper(out, "background");
        defaultIfNull(out, "background", "NONE");
        if (out.get("gradient") == null) {
            out.remove("gradient");
        } else if (asMap(out.get("gradient")) != null) {
            var gradient = keep(asMap(out.get("gradient")), GRADIENT_KEYS);
            coerceInt(gradient, "angle");
            if (gradient.get("stops") instanceof List<?> stops) {
                var cleaned = new ArrayList<Object>();
                for (var stop : stops) {
                    var row = asMap(stop);
                    if (row == null) {
                        cleaned.add(stop);
                        continue;
                    }
                    var kept = keep(row, GRADIENT_STOP_KEYS);
                    coerceInt(kept, "at");
                    cleaned.add(kept);
                }
                gradient.put("stops", cleaned);
            }
            out.put("gradient", gradient);
        }
        textId(out, "backgroundMediaId");
        if (!"IMAGE".equals(out.get("background"))) out.remove("backgroundMediaId");
        if (out.containsKey("overlay")) {
            coerceInt(out, "overlay");
            if (out.get("overlay") == null) out.remove("overlay");
        }
    }

    private static Map<String, Object> sanitizeFontCustom(Map<String, Object> source, Object font) {
        var out = keep(source, FONT_CUSTOM_KEYS);
        textId(out, "headingMediaId");
        textId(out, "bodyMediaId");
        if (out.get("headingMediaId") == null && out.get("bodyMediaId") == null) return null; // nothing custom → legacy font only
        upper(out, "fallback");
        if (out.get("fallback") == null) {
            var base = font == null ? null : ShopThemeFont.from(String.valueOf(font));
            out.put("fallback", (base == null ? ShopThemeFont.NOTO_SANS_THAI : base).name());
        }
        return out;
    }

    // ───────────────────────── validation ─────────────────────────

    /** Structural validation of every AG5 key (CUSTOM only; media existence / kind is checked in the media service). */
    static void validate(List<FieldViolation> problems, Map<String, Object> config, GradientCheck gradients) {
        if (config == null) return;
        var hero = asMap(config.get("hero"));
        if (hero != null) validateHero(problems, hero);
        if (config.containsKey("header")) validateHeader(problems, config.get("header"), gradients);
        if (config.containsKey("footer")) validateFooter(problems, config.get("footer"), gradients);
        if (config.containsKey("fontCustom")) validateFontCustom(problems, config.get("fontCustom"));
    }

    private static void validateHero(List<FieldViolation> problems, Map<String, Object> hero) {
        var base = "config.hero";
        if (hero.containsKey("background")) {
            var bg = asMap(hero.get("background"));
            if (bg == null) {
                problems.add(ValidationMessages.violation(base + ".background", TOKEN));
            } else {
                var type = bg.get("type") instanceof String text ? text : null;
                if (type == null || !HERO_BACKGROUND_TYPES.contains(type)) {
                    problems.add(ValidationMessages.violation(base + ".background.type", TOKEN));
                } else if (!"GRADIENT".equals(type)) {
                    requireMediaShape(problems, base + ".background.mediaId", bg.get("mediaId"));
                    if ("VIDEO".equals(type)) optionalMediaShape(problems, base + ".background.posterMediaId", bg.get("posterMediaId"));
                }
            }
        }
        checkRange(problems, hero, "overlay", base + ".overlay", 0, 80);
        checkRange(problems, hero, "heightVh", base + ".heightVh", 50, 100);
        checkEnum(problems, hero, "textAlign", base + ".textAlign", TEXT_ALIGNS);
        checkEnum(problems, hero, "headlineSize", base + ".headlineSize", HEADLINE_SIZES);

        if (hero.containsKey("stickers")) {
            if (!(hero.get("stickers") instanceof List<?> stickers) || stickers.size() > MAX_STICKERS) {
                problems.add(ValidationMessages.violation(base + ".stickers", TOKEN));
            } else {
                for (int i = 0; i < stickers.size(); i++) {
                    var path = base + ".stickers[" + i + "]";
                    var sticker = asMap(stickers.get(i));
                    if (sticker == null) {
                        problems.add(ValidationMessages.violation(path, TOKEN));
                        continue;
                    }
                    requireMediaShape(problems, path + ".mediaId", sticker.get("mediaId"));
                    checkRange(problems, sticker, "x", path + ".x", 0, 100);
                    checkRange(problems, sticker, "y", path + ".y", 0, 100);
                    checkRange(problems, sticker, "size", path + ".size", 40, 240);
                    checkRange(problems, sticker, "rotate", path + ".rotate", -30, 30);
                    if (sticker.get("hideOnMobile") != null && !(sticker.get("hideOnMobile") instanceof Boolean)) {
                        problems.add(ValidationMessages.violation(path + ".hideOnMobile", TOKEN));
                    }
                }
            }
        }

        if (hero.containsKey("ctaSecondary")) {
            var cta = asMap(hero.get("ctaSecondary"));
            if (cta == null) {
                problems.add(ValidationMessages.violation(base + ".ctaSecondary", TOKEN));
            } else {
                if (cta.get("label") != null && (!(cta.get("label") instanceof String label) || label.length() > HERO_CTA_MAX)) {
                    problems.add(ValidationMessages.violation(base + ".ctaSecondary.label", "THEME_HERO_CTA_TOO_LONG"));
                }
                if (cta.get("link") != null && !safeLink(cta.get("link"))) {
                    problems.add(ValidationMessages.violation(base + ".ctaSecondary.link", "THEME_HERO_LINK_INVALID"));
                }
            }
        }
    }

    private static void validateHeader(List<FieldViolation> problems, Object raw, GradientCheck gradients) {
        var base = "config.header";
        var header = asMap(raw);
        if (header == null) {
            problems.add(ValidationMessages.violation(base, CHROME));
            return;
        }
        checkEnum(problems, header, "layout", base + ".layout", HEADER_LAYOUTS);
        validateBackground(problems, header, base, gradients);
        for (var key : List.of("sticky", "showSearch", "showSocialIcons")) checkBoolean(problems, header, key, base + "." + key);

        if (header.get("menu") != null) {
            if (!(header.get("menu") instanceof List<?> menu) || menu.size() > MAX_MENU_ITEMS) {
                problems.add(ValidationMessages.violation(base + ".menu", CHROME));
            } else {
                validateLinks(problems, base + ".menu", menu, MENU_LABEL_MAX);
            }
        }

        if (header.containsKey("announcementBar")) {
            var bar = asMap(header.get("announcementBar"));
            var path = base + ".announcementBar";
            if (bar == null) {
                problems.add(ValidationMessages.violation(path, CHROME));
            } else {
                if (bar.get("text") == null) problems.add(ValidationMessages.violation(path + ".text", CHROME));
                else ShopThemeBlockContent.checkLocalized(problems, path + ".text", bar.get("text"), ANNOUNCEMENT_MAX);
                ShopThemeBlockContent.checkLink(problems, path + ".link", bar.get("link"));
                for (var key : List.of("backgroundColor", "textColor")) {
                    if (!(bar.get(key) instanceof String color) || !HEX.matcher(color).matches()) {
                        problems.add(ValidationMessages.violation(path + "." + key, "THEME_COLOR_INVALID"));
                    }
                }
            }
        }
    }

    private static void validateFooter(List<FieldViolation> problems, Object raw, GradientCheck gradients) {
        var base = "config.footer";
        var footer = asMap(raw);
        if (footer == null) {
            problems.add(ValidationMessages.violation(base, CHROME));
            return;
        }
        validateBackground(problems, footer, base, gradients);
        for (var key : List.of("showContacts", "showSocial")) checkBoolean(problems, footer, key, base + "." + key);

        if (footer.get("columns") != null) {
            if (!(footer.get("columns") instanceof List<?> columns) || columns.size() > MAX_FOOTER_COLUMNS) {
                problems.add(ValidationMessages.violation(base + ".columns", CHROME));
            } else {
                for (int i = 0; i < columns.size(); i++) {
                    var path = base + ".columns[" + i + "]";
                    var column = asMap(columns.get(i));
                    if (column == null) {
                        problems.add(ValidationMessages.violation(path, CHROME));
                        continue;
                    }
                    ShopThemeBlockContent.checkLocalized(problems, path + ".title", column.get("title"), COLUMN_TITLE_MAX);
                    if (column.get("links") != null) {
                        if (!(column.get("links") instanceof List<?> links) || links.size() > MAX_COLUMN_LINKS) {
                            problems.add(ValidationMessages.violation(path + ".links", CHROME));
                        } else {
                            validateLinks(problems, path + ".links", links, MENU_LABEL_MAX);
                        }
                    }
                }
            }
        }
        if (footer.get("about") != null) ShopThemeBlockContent.checkLocalized(problems, base + ".about", footer.get("about"), ABOUT_MAX);
        if (footer.get("copyright") != null
            && (!(footer.get("copyright") instanceof String text) || text.length() > COPYRIGHT_MAX)) {
            problems.add(ValidationMessages.violation(base + ".copyright", CONTENT));
        }
    }

    private static void validateLinks(List<FieldViolation> problems, String prefix, List<?> rows, int labelMax) {
        for (int i = 0; i < rows.size(); i++) {
            var path = prefix + "[" + i + "]";
            var row = asMap(rows.get(i));
            if (row == null) {
                problems.add(ValidationMessages.violation(path, CHROME));
                continue;
            }
            if (row.get("label") == null) problems.add(ValidationMessages.violation(path + ".label", CHROME));
            else ShopThemeBlockContent.checkLocalized(problems, path + ".label", row.get("label"), labelMax);
            if (row.get("link") == null) problems.add(ValidationMessages.violation(path + ".link", "THEME_BLOCK_LINK_INVALID"));
            else ShopThemeBlockContent.checkLink(problems, path + ".link", row.get("link"));
        }
    }

    private static void validateBackground(List<FieldViolation> problems, Map<String, Object> holder, String base, GradientCheck gradients) {
        var background = holder.get("background") instanceof String text ? text : null;
        if (background == null || !ShopThemeBlocks.BACKGROUNDS.contains(background)) {
            problems.add(ValidationMessages.violation(base + ".background", TOKEN));
        } else if ("IMAGE".equals(background)) {
            requireMediaShape(problems, base + ".backgroundMediaId", holder.get("backgroundMediaId"));
        }
        if (holder.get("gradient") != null) gradients.check(problems, base + ".gradient", holder.get("gradient"));
        checkRange(problems, holder, "overlay", base + ".overlay", 0, 80);
    }

    private static void validateFontCustom(List<FieldViolation> problems, Object raw) {
        var base = "config.fontCustom";
        var font = asMap(raw);
        if (font == null) {
            problems.add(ValidationMessages.violation(base, "THEME_FONT_CUSTOM_INVALID"));
            return;
        }
        optionalMediaShape(problems, base + ".headingMediaId", font.get("headingMediaId"));
        optionalMediaShape(problems, base + ".bodyMediaId", font.get("bodyMediaId"));
        if (!(font.get("fallback") instanceof String fallback) || ShopThemeFont.from(fallback) == null) {
            problems.add(ValidationMessages.violation(base + ".fallback", "THEME_FONT_INVALID"));
        }
    }

    // ───────────────────────── media ─────────────────────────

    /** Every media id the AG5 keys reference, with the kind it must have in the library. */
    static List<MediaRef> mediaRefs(Map<String, Object> config) {
        var out = new ArrayList<MediaRef>();
        if (config == null) return out;
        var hero = asMap(config.get("hero"));
        if (hero != null) {
            var bg = asMap(hero.get("background"));
            if (bg != null) {
                var type = bg.get("type");
                if ("IMAGE".equals(type) && bg.get("mediaId") != null) {
                    out.add(new MediaRef("config.hero.background.mediaId", bg.get("mediaId"), ShopThemeMediaKind.IMAGE));
                } else if ("VIDEO".equals(type)) {
                    if (bg.get("mediaId") != null) {
                        out.add(new MediaRef("config.hero.background.mediaId", bg.get("mediaId"), ShopThemeMediaKind.VIDEO));
                    }
                    if (bg.get("posterMediaId") != null) {
                        out.add(new MediaRef("config.hero.background.posterMediaId", bg.get("posterMediaId"), ShopThemeMediaKind.IMAGE));
                    }
                }
            }
            if (hero.get("stickers") instanceof List<?> stickers) {
                for (int i = 0; i < stickers.size(); i++) {
                    var sticker = asMap(stickers.get(i));
                    if (sticker != null && sticker.get("mediaId") != null) {
                        out.add(new MediaRef("config.hero.stickers[" + i + "].mediaId", sticker.get("mediaId"), ShopThemeMediaKind.IMAGE));
                    }
                }
            }
        }
        for (var key : List.of("header", "footer")) {
            var section = asMap(config.get(key));
            if (section != null && section.get("backgroundMediaId") != null) {
                out.add(new MediaRef("config." + key + ".backgroundMediaId", section.get("backgroundMediaId"), ShopThemeMediaKind.IMAGE));
            }
        }
        var font = asMap(config.get("fontCustom"));
        if (font != null) {
            for (var key : List.of("headingMediaId", "bodyMediaId")) {
                if (font.get(key) != null) out.add(new MediaRef("config.fontCustom." + key, font.get(key), ShopThemeMediaKind.FONT));
            }
        }
        return out;
    }

    /** Read path: derive public URLs (never stored; the whitelist drops them on the next save). */
    static void resolveMedia(String slug, Map<String, Object> config) {
        if (config == null) return;
        var hero = asMap(config.get("hero"));
        if (hero != null) {
            var bg = asMap(hero.get("background"));
            if (bg != null) {
                putUrl(slug, bg, "mediaId", "url");
                putUrl(slug, bg, "posterMediaId", "posterUrl");
            }
            if (hero.get("stickers") instanceof List<?> stickers) {
                for (var item : stickers) {
                    var sticker = asMap(item);
                    if (sticker != null) putUrl(slug, sticker, "mediaId", "url");
                }
            }
        }
        for (var key : List.of("header", "footer")) {
            var section = asMap(config.get(key));
            if (section != null) putUrl(slug, section, "backgroundMediaId", "backgroundUrl");
        }
        var font = asMap(config.get("fontCustom"));
        if (font != null) {
            putUrl(slug, font, "headingMediaId", "headingUrl");
            putUrl(slug, font, "bodyMediaId", "bodyUrl");
        }
    }

    private static void putUrl(String slug, Map<String, Object> holder, String idKey, String urlKey) {
        var raw = holder.get(idKey) instanceof String text ? text : null;
        if (raw == null || raw.isBlank()) return;
        var id = parseUuid(raw);
        if (id == null) holder.remove(idKey);
        else holder.put(urlKey, ShopThemeMediaService.publicUrl(slug, id));
    }

    // ───────────────────────── small helpers ─────────────────────────

    private static boolean safeLink(Object raw) {
        return raw instanceof String link && link.length() <= 500
            && (INTERNAL_PATH.matcher(link).matches() || HTTPS.matcher(link).matches());
    }

    private static void requireMediaShape(List<FieldViolation> problems, String field, Object raw) {
        if (!(raw instanceof String text) || parseUuid(text) == null) problems.add(ValidationMessages.violation(field, MEDIA));
    }

    private static void optionalMediaShape(List<FieldViolation> problems, String field, Object raw) {
        if (raw != null) requireMediaShape(problems, field, raw);
    }

    private static void checkRange(List<FieldViolation> problems, Map<String, Object> holder, String key, String field, int min, int max) {
        if (!holder.containsKey(key) || holder.get(key) == null) return;
        var value = ShopThemeBlocks.intValue(holder.get(key));
        if (value == null || value < min || value > max) problems.add(ValidationMessages.violation(field, TOKEN));
    }

    private static void checkEnum(List<FieldViolation> problems, Map<String, Object> holder, String key, String field, List<String> allowed) {
        if (!holder.containsKey(key) || holder.get(key) == null) return;
        if (!(holder.get(key) instanceof String text) || !allowed.contains(text)) {
            problems.add(ValidationMessages.violation(field, TOKEN));
        }
    }

    private static void checkBoolean(List<FieldViolation> problems, Map<String, Object> holder, String key, String field) {
        if (holder.get(key) != null && !(holder.get(key) instanceof Boolean)) problems.add(ValidationMessages.violation(field, TOKEN));
    }

    private static Map<String, Object> keep(Map<String, Object> source, Set<String> allowed) {
        var out = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            if (allowed.contains(entry.getKey())) out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static void upper(Map<String, Object> map, String key) {
        if (map.get(key) instanceof String text) {
            var trimmed = text.trim();
            if (trimmed.isEmpty()) map.remove(key);
            else map.put(key, trimmed.toUpperCase());
        }
    }

    /** Trimmed string kept when non-blank (blank / null removed); non-strings are kept so validation can flag them. */
    private static void trimText(Map<String, Object> map, String key) {
        var value = map.get(key);
        if (value == null) {
            map.remove(key);
        } else if (value instanceof String text) {
            var trimmed = text.trim();
            if (trimmed.isEmpty()) map.remove(key);
            else map.put(key, trimmed);
        }
    }

    private static void textId(Map<String, Object> map, String key) {
        trimText(map, key);
    }

    private static void coerceInt(Map<String, Object> map, String key) {
        var value = map.get(key);
        if (value instanceof String text) {
            if (text.isBlank()) {
                map.remove(key);
                return;
            }
            var parsed = ShopThemeBlocks.intValue(text);
            if (parsed != null) map.put(key, parsed);
        } else if (value instanceof Number number && number.doubleValue() == Math.floor(number.doubleValue()) && !(value instanceof Integer)) {
            map.put(key, number.intValue());
        }
    }

    private static void defaultIfNull(Map<String, Object> map, String key, Object value) {
        if (map.get(key) == null) map.put(key, value);
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
