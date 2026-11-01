package com.thaishopfun.merchant.theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ThemePresets {
    private ThemePresets() {}

    /** GENERAL extra palette roles (themes spec): saved/validated for GENERAL only, stripped for FASHION/FOOD_BEVERAGE. */
    public static final String GENERAL_HERO_EYEBROW = "ส่งตรงจากชุมชนทั่วไทย";
    /** GENERAL-only plain-text hero copy keys (other non-CUSTOM templates drop them on save). */
    public static final List<String> GENERAL_HERO_KEYS = List.of("eyebrow", "ctaSecondaryLabel", "ctaSecondaryLink", "badges");
    public static final List<String> GENERAL_EXTRA_COLOR_KEYS = List.of("borderStrong", "sale", "focus");
    /** Templates rendered by the themes-spec storefront tree; they keep (and validate) the extra palette roles. */
    public static final Set<ShopThemeTemplate> EXTRA_COLOR_TEMPLATES = Set.of(ShopThemeTemplate.GENERAL, ShopThemeTemplate.FASHION, ShopThemeTemplate.FOOD_BEVERAGE);

    public static boolean allowsExtraColors(ShopThemeTemplate template) {
        return template != null && EXTRA_COLOR_TEMPLATES.contains(template);
    }

    /** GENERAL follows the ThaiShopFun Themes spec: IBM Plex Sans Thai, spec palette, Hero→Trust→Categories→Featured→Banners→Best sellers→Intro→Catalog. */
    public static Map<String, Object> generalConfig() {
        var config = config(
            "#0a5fd6", "#08245d", "#ffffff", "#132b50",
            "#f28c28",
            ShopThemeFont.IBM_PLEX_SANS_THAI,
            "FULL", "", "", "", "",
            style(12, "BORDERED", "COMFORTABLE", "ROUNDED"),
            defaultBanners(),
            defaultTrust(),
            List.of(
                section("HERO", true, "FULL"),
                section("TRUST", true, null),
                section("CATEGORIES", true, null),
                section("FEATURED", true, "GRID"),
                section("BANNERS", true, null),
                section("BESTSELLERS", true, "GRID"),
                section("INTRO", true, null),
                section("CATALOG", true, "GRID"),
                section("NEW_ARRIVALS", false, "GRID"),
                section("HOURS", false, null)
            )
        );
        @SuppressWarnings("unchecked")
        var colors = (Map<String, Object>) config.get("colors");
        colors.put("borderStrong", "#7d8ba0");
        colors.put("sale", "#c4271b");
        colors.put("focus", "#0a5fd6");
        // Themes spec hero copy (Thai-first). Links stay blank -> storefront falls back to this shop's products / categories.
        @SuppressWarnings("unchecked")
        var hero = (Map<String, Object>) config.get("hero");
        hero.put("eyebrow", GENERAL_HERO_EYEBROW);
        hero.put("title", "ของดีทั่วไทย ส่งตรงถึงบ้านคุณ");
        hero.put("subtitle", "สินค้าคุณภาพจากผู้ผลิตท้องถิ่น แพ็กอย่างดี ส่งไวภายใน 24 ชั่วโมง");
        hero.put("ctaLabel", "ช้อปเลย");
        hero.put("ctaSecondaryLabel", "ดูหมวดหมู่");
        hero.put("ctaSecondaryLink", "");
        return config;
    }

    /** Pre-spec GENERAL baseline; CUSTOM keeps cloning this so CUSTOM defaults stay exactly as before. */
    private static Map<String, Object> legacyGeneralConfig() {
        return config(
            "#087bf5", "#08245d", "#ffffff", "#132b50",
            null,
            ShopThemeFont.NOTO_SANS_THAI,
            "FULL", "", "", "", "",
            style(12, "BORDERED", "COMFORTABLE", "ROUNDED"),
            defaultBanners(),
            defaultTrust(),
            List.of(
                section("HERO", true, "FULL"),
                section("BANNERS", true, null),
                section("CATEGORIES", true, null),
                section("TRUST", true, null),
                section("FEATURED", true, "GRID"),
                section("BESTSELLERS", true, "GRID"),
                section("NEW_ARRIVALS", true, "GRID"),
                section("CATALOG", true, "GRID"),
                section("INTRO", false, null),
                section("HOURS", false, null)
            )
        );
    }

    public static Map<String, Object> fashionConfig() {
        // Themes spec "fashion" (tokens.json): monochrome editorial, lime accent, Prompt headings, square shapes + PILL buttons.
        var config = config(
            "#111111", "#f1efea", "#ffffff", "#111111",
            "#e3ff3d",
            ShopThemeFont.PROMPT,
            "FULL", "", "", "", "",
            style(0, "FLAT", "COMFORTABLE", "PILL"),
            defaultBanners(),
            defaultTrust(),
            List.of(
                section("HERO", true, "FULL"),
                section("NEW_ARRIVALS", true, "LARGE"),
                section("BANNERS", true, null),
                section("CATEGORIES", true, null),
                section("BESTSELLERS", true, "LARGE"),
                section("INTRO", true, null),
                section("TRUST", true, null),
                section("FEATURED", false, "LARGE"),
                section("CATALOG", false, "LARGE"),
                section("HOURS", false, null)
            )
        );
        @SuppressWarnings("unchecked")
        var colors = (Map<String, Object>) config.get("colors");
        colors.put("borderStrong", "#767676");
        colors.put("sale", "#c8102e");
        colors.put("focus", "#111111");
        return config;
    }

    public static Map<String, Object> foodBeverageConfig() {
        // Themes spec "food" (tokens.json): warm terracotta + herb green on cream, Mitr headings, round shapes, STRIP hero.
        var config = config(
            "#a63a17", "#2e4a2b", "#fff8ef", "#2b1a10",
            "#f2b544",
            ShopThemeFont.MITR,
            "STRIP", "", "", "", "",
            style(20, "SHADOW", "COMFORTABLE", "PILL"),
            defaultBanners(),
            defaultTrust(),
            List.of(
                section("HERO", true, "STRIP"),
                section("CATEGORIES", true, null),
                section("FEATURED", true, "MENU"),
                section("BESTSELLERS", true, "MENU"),
                section("BANNERS", true, null),
                section("HOURS", true, null),
                section("INTRO", true, null),
                section("TRUST", false, null),
                section("NEW_ARRIVALS", false, "MENU"),
                section("CATALOG", false, "MENU")
            )
        );
        @SuppressWarnings("unchecked")
        var colors = (Map<String, Object>) config.get("colors");
        colors.put("borderStrong", "#8f735f");
        colors.put("sale", "#a63a17");
        colors.put("focus", "#2e4a2b");
        return config;
    }

    /** CUSTOM baseline is a deep clone of the pre-spec GENERAL preset (never share the same map instance). */
    public static Map<String, Object> customConfig() {
        return deepCopy(legacyGeneralConfig());
    }

    public static Map<String, Object> forTemplate(ShopThemeTemplate template) {
        return switch (template == null ? ShopThemeTemplate.GENERAL : template) {
            case GENERAL -> generalConfig();
            case FASHION -> fashionConfig();
            case FOOD_BEVERAGE -> foodBeverageConfig();
            case CUSTOM -> customConfig();
        };
    }

    @SuppressWarnings("unchecked")
    public static void ensureStyle(Map<String, Object> config, ShopThemeTemplate template) {
        if (config == null) return;
        var preset = forTemplate(template);
        var presetStyle = asMap(preset.get("style"));
        if (presetStyle == null) return;
        var style = asMap(config.get("style"));
        if (style == null) {
            config.put("style", deepCopy(presetStyle));
            return;
        }
        for (var key : List.of("radius", "cardStyle", "density", "buttonShape", "shadow", "headingTextShadow")) {
            if (!presetStyle.containsKey(key)) continue;
            if (!style.containsKey(key) || style.get(key) == null || String.valueOf(style.get(key)).isBlank()) {
                style.put(key, presetStyle.get(key));
            }
        }
        config.put("style", style);
    }

    /** Fill banners / trust / missing sections from the template preset. */
    @SuppressWarnings("unchecked")
    public static void ensureMallExtras(Map<String, Object> config, ShopThemeTemplate template) {
        if (config == null) return;
        var preset = forTemplate(template);
        if (!(config.get("banners") instanceof List<?>)) {
            config.put("banners", deepCopyList((List<?>) preset.get("banners")));
        }
        if (!(config.get("trust") instanceof Map<?, ?>)) {
            config.put("trust", deepCopy(asMap(preset.get("trust"))));
        } else {
            var trust = asMap(config.get("trust"));
            var presetTrust = asMap(preset.get("trust"));
            if (trust != null && presetTrust != null) {
                if (!(trust.get("items") instanceof List<?>)) {
                    trust.put("items", deepCopyList((List<?>) presetTrust.get("items")));
                }
                config.put("trust", trust);
            }
        }
        ensureSections(config, template);
    }

    @SuppressWarnings("unchecked")
    private static void ensureSections(Map<String, Object> config, ShopThemeTemplate template) {
        var presetSections = (List<Map<String, Object>>) (List<?>) forTemplate(template).get("sections");
        if (presetSections == null) return;
        var raw = config.get("sections");
        var existing = new ArrayList<Map<String, Object>>();
        if (raw instanceof List<?> list) {
            for (var item : list) {
                if (item instanceof Map<?, ?> map) existing.add((Map<String, Object>) map);
            }
        }
        var seen = new java.util.HashSet<String>();
        for (var row : existing) {
            var key = String.valueOf(row.get("key"));
            seen.add(key);
        }
        for (var preset : presetSections) {
            var key = String.valueOf(preset.get("key"));
            if (!seen.contains(key)) {
                existing.add(deepCopy(preset));
                seen.add(key);
            }
        }
        config.put("sections", existing);
    }

    public static Map<String, Object> deepCopy(Map<String, Object> source) {
        return copyMap(source);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> deepCopyList(List<?> source) {
        var copy = new ArrayList<Object>();
        if (source == null) return copy;
        for (var item : source) {
            if (item instanceof Map<?, ?> nested) copy.add(copyMap((Map<String, Object>) nested));
            else copy.add(item);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMap(Map<String, Object> source) {
        var copy = new LinkedHashMap<String, Object>();
        if (source == null) return copy;
        for (var entry : source.entrySet()) {
            var value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                copy.put(entry.getKey(), copyMap((Map<String, Object>) nested));
            } else if (value instanceof List<?> list) {
                copy.put(entry.getKey(), deepCopyList(list));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    private static Map<String, Object> config(
        String primary, String secondary, String background, String text,
        String accent,
        ShopThemeFont font, String heroLayout, String title, String subtitle, String ctaLabel, String ctaLink,
        Map<String, Object> style,
        List<Map<String, Object>> banners,
        Map<String, Object> trust,
        List<Map<String, Object>> sections
    ) {
        var colors = new LinkedHashMap<String, Object>();
        colors.put("primary", primary);
        colors.put("secondary", secondary);
        colors.put("background", background);
        colors.put("text", text);
        if (accent != null && !accent.isBlank()) colors.put("accent", accent);
        var hero = new LinkedHashMap<String, Object>();
        hero.put("layout", heroLayout);
        hero.put("title", title);
        hero.put("subtitle", subtitle);
        hero.put("ctaLabel", ctaLabel);
        hero.put("ctaLink", ctaLink);
        var config = new LinkedHashMap<String, Object>();
        config.put("colors", colors);
        config.put("font", font.name());
        config.put("style", style);
        config.put("hero", hero);
        config.put("banners", banners);
        config.put("trust", trust);
        config.put("sections", sections);
        return config;
    }

    private static Map<String, Object> style(int radius, String cardStyle, String density, String buttonShape) {
        var row = new LinkedHashMap<String, Object>();
        row.put("radius", radius);
        row.put("cardStyle", cardStyle);
        row.put("density", density);
        row.put("buttonShape", buttonShape);
        return row;
    }

    /** Empty by default — section hides until the merchant adds banner image URLs. */
    private static List<Map<String, Object>> defaultBanners() {
        return new ArrayList<>();
    }

    private static Map<String, Object> defaultTrust() {
        var trust = new LinkedHashMap<String, Object>();
        trust.put("enabled", true);
        trust.put("items", List.of(
            trustItem("AUTHENTIC", "ของแท้ 100%"),
            trustItem("FAST_SHIP", "ส่งไว"),
            trustItem("RETURNS", "คืนสินค้าได้"),
            trustItem("SECURE_PAY", "ชำระปลอดภัย")
        ));
        return trust;
    }

    private static Map<String, Object> trustItem(String icon, String label) {
        var row = new LinkedHashMap<String, Object>();
        row.put("icon", icon);
        row.put("label", label);
        row.put("enabled", true);
        return row;
    }

    private static Map<String, Object> section(String key, boolean enabled, String variant) {
        var row = new LinkedHashMap<String, Object>();
        row.put("key", key);
        row.put("enabled", enabled);
        row.put("variant", variant);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return null;
    }
}
