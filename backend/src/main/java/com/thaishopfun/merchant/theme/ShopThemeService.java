package com.thaishopfun.merchant.theme;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.common.ValidationMessages;
import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.catalog.ProductStatus;
import com.thaishopfun.merchant.MerchantDtos;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ShopThemeService {
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern INTERNAL_PATH = Pattern.compile("^/[\\w\\-./?#=&%]*$");
    private static final Pattern HTTPS = Pattern.compile("^https://[\\w\\-./?#=&%]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS = Pattern.compile("(?i)url\\s*\\(|expression|;|}|<");

    private static final Set<String> TOP_KEYS = Set.of(
        "colors", "font", "style", "hero", "banners", "trust", "sections",
        "gradients", "productCard", "sectionSettings", "layout",
        // AG5: CUSTOM-only chrome (stripped for every other template by ShopThemeChrome.stripForNonCustom)
        "header", "footer", "fontCustom",
        // Brand options for every template: separate body font + logo position / text alignment.
        "fontBody", "brand"
    );
    private static final List<String> BRAND_ALIGNS = List.of("LEFT", "CENTER");
    private static final Set<String> COLOR_KEYS = Set.of(
        "primary", "secondary", "background", "text", "accent",
        "surface", "muted", "ctaBackground", "ctaText", "border",
        "navBackground", "navText", "cardBackground"
    );
    private static final Set<String> OPTIONAL_COLORS = Set.of(
        "accent", "surface", "muted", "ctaBackground", "ctaText", "border",
        "navBackground", "navText", "cardBackground"
    );
    private static final Set<String> STYLE_KEYS = Set.of(
        "radius", "cardStyle", "density", "buttonShape", "shadow", "headingTextShadow",
        "productSectionBackground"
    );
    private static final Set<String> GRADIENT_SLOT_KEYS = Set.of("hero", "page", "section");
    private static final Set<String> GRADIENT_KEYS = Set.of("angle", "stops");
    private static final Set<String> GRADIENT_STOP_KEYS = Set.of("color", "at");
    /** AG1 keys that old templates never knew about; stripped silently for non-CUSTOM saves. */
    private static final List<String> AG1_TOP_KEYS = List.of("gradients", "productCard", "sectionSettings", "layout");
    private static final List<String> AG1_COLOR_KEYS = List.of("surface", "muted", "ctaBackground", "ctaText", "border");
    private static final List<String> AG1_STYLE_KEYS = List.of("shadow", "headingTextShadow");
    private static final Set<String> PRODUCT_CARD_KEYS = Set.of(
        "imageRatio", "ctaStyle", "showPrice", "edgeToEdge", "titleAlign"
    );
    private static final Set<String> SECTION_SETTING_KEYS = Set.of("background", "align", "columns", "paddingY");
    private static final Set<String> PRODUCT_SECTIONS = Set.of("FEATURED", "BESTSELLERS", "NEW_ARRIVALS", "CATALOG");
    private static final List<String> SHADOWS = List.of("NONE", "SOFT", "MEDIUM", "STRONG");
    private static final List<String> IMAGE_RATIOS = List.of("SQUARE", "PORTRAIT", "LANDSCAPE");
    private static final List<String> CTA_STYLES = List.of("PILL", "ROUNDED", "SQUARE", "NONE");
    private static final List<String> TITLE_ALIGNS = List.of("LEFT", "CENTER");
    private static final List<String> SECTION_BACKGROUNDS = List.of("NONE", "SURFACE", "PRIMARY", "GRADIENT");
    private static final List<String> SECTION_ALIGNS = List.of("LEFT", "CENTER");
    private static final List<String> PADDING_Y = List.of("COMPACT", "NORMAL", "LARGE");
    private static final List<Integer> SECTION_COLUMNS = List.of(2, 3, 4, 5);
    private static final List<String> CARD_RADII = List.of("NONE", "ROUNDED");
    private static final List<String> CARD_GAPS = List.of("TIGHT", "SPACED");

    private final ShopThemeRepository themes;
    private final ShopRepository shops;
    private final AuthorizationService authorization;

    private final ShopThemeMediaService themeMedia;
    private final ProductRepository products;

    public ShopThemeService(
        ShopThemeRepository themes,
        ShopRepository shops,
        AuthorizationService authorization,
        ShopThemeMediaService themeMedia,
        ProductRepository products
    ) {
        this.themes = themes;
        this.shops = shops;
        this.authorization = authorization;
        this.themeMedia = themeMedia;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public MerchantDtos.ShopThemeResponse get(UUID userId, Long shopId) {
        var shop = ownedShop(userId, shopId);
        return responseFor(shop);
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.ShopThemePresetResponse> presets() {
        return List.of(
            new MerchantDtos.ShopThemePresetResponse(ShopThemeTemplate.GENERAL.name(), ThemePresets.generalConfig()),
            new MerchantDtos.ShopThemePresetResponse(ShopThemeTemplate.FASHION.name(), ThemePresets.fashionConfig()),
            new MerchantDtos.ShopThemePresetResponse(ShopThemeTemplate.FOOD_BEVERAGE.name(), ThemePresets.foodBeverageConfig()),
            new MerchantDtos.ShopThemePresetResponse(ShopThemeTemplate.CUSTOM.name(), ThemePresets.customConfig())
        );
    }

    @Transactional
    public MerchantDtos.ShopThemeResponse save(UUID userId, Long shopId, MerchantDtos.ShopThemeUpdateRequest request) {
        var shop = ownedShop(userId, shopId);
        var template = ShopThemeTemplate.from(request.templateKey());
        if (template == null) {
            throw new ValidationException(List.of(ValidationMessages.violation("templateKey", "THEME_TEMPLATE_INVALID")));
        }
        // AG1.1: CUSTOM gets the AG1 token whitelist + validation; GENERAL/FASHION/FOOD_BEVERAGE behave exactly as
        // pre-AG1 except that AG1 token keys are silently dropped (never validated, never a 400).
        var custom = template == ShopThemeTemplate.CUSTOM;
        var copy = ThemePresets.deepCopy(request.config());
        var config = custom ? stripUnknown(copy) : stripAg1Tokens(copy, template);
        if (custom) {
            var customHero = asMap(config.get("hero"));
            if (customHero != null) for (var key : ThemePresets.GENERAL_HERO_KEYS) customHero.remove(key);
        }
        if (config != null) {
            // AG2: only CUSTOM themes may reference the media library; other templates silently drop mediaId.
            if (custom) normalizeMediaIds(config);
            else ShopThemeMediaService.stripMediaIds(config);
            ThemePresets.ensureStyle(config, template);
            ThemePresets.ensureMallExtras(config, template);
        }
        validateConfig(config, custom);
        if (ThemePresets.allowsExtraColors(template)) validateGeneralExtraColors(config);
        // StoryBlock (non-CUSTOM tsf templates): plain-text copy + optional shop-media image; CUSTOM never carries it.
        if (!custom && config != null) {
            validateIntro(config);
            validateTsfCopy(config);
            themeMedia.prepareIntro(shop.getUuid(), config);
        }
        // Hero slider (all templates): max 5 live IMAGEs from the shop media library.
        themeMedia.prepareHeroSlides(shop.getUuid(), config);
        if (custom) {
            themeMedia.assertMediaRefs(shop.getUuid(), config);
            assertBlockProducts(shop, config);
            // AG4: store the jsoup-sanitized HTML next to each RICH_TEXT markdown source.
            ShopThemeBlocks.finalizeContent(config);
            dropImageUrlWhereMediaId(config);
            // AG3: persist blocks (synthesized from sections when the client sent none) and keep sections in sync.
            ShopThemeBlocks.ensureOnSave(config);
        }
        var existing = themes.findByShopId(shop.getUuid()).orElse(null);
        if (existing == null) {
            themes.save(new ShopTheme(shop, template, config, userId));
        } else {
            existing.apply(template, config, userId);
            themes.save(existing);
        }
        return responseFor(shop);
    }

    @Transactional(readOnly = true)
    public MerchantDtos.ShopThemeResponse publicTheme(Shop shop) {
        return responseFor(shop);
    }

    private MerchantDtos.ShopThemeResponse responseFor(Shop shop) {
        var theme = themes.findByShopId(shop.getUuid()).orElse(null);
        if (theme == null) {
            return new MerchantDtos.ShopThemeResponse(ShopThemeTemplate.GENERAL.name(), ThemePresets.generalConfig());
        }
        var template = ShopThemeTemplate.from(theme.getTemplateKey());
        // Unknown keys still fall back to GENERAL for gap-fill; CUSTOM must not.
        var resolved = template == null ? ShopThemeTemplate.GENERAL : template;
        var config = ThemePresets.deepCopy(theme.getConfig());
        ThemePresets.ensureStyle(config, resolved);
        ThemePresets.ensureMallExtras(config, resolved);
        if (resolved == ShopThemeTemplate.CUSTOM) {
            resolveBannerMedia(shop.getSlug(), config);
            // AG3: CUSTOM themes saved before blocks existed are migrated on read (sections + sectionSettings → blocks).
            ShopThemeBlocks.ensureOnRead(config);
            ShopThemeBlocks.resolveMedia(shop.getSlug(), config);
            resolveFeatureProducts(shop, config);
            ShopThemeChrome.resolveMedia(shop.getSlug(), config);
            config.remove("intro");
        } else {
            ShopThemeChrome.stripForNonCustom(config, resolved == ShopThemeTemplate.GENERAL);
            ShopThemeMediaService.stripMediaIds(config);
            config.remove("layout");
            themeMedia.resolveIntro(shop, config);
        }
        themeMedia.resolveHeroSlides(shop, config);
        return new MerchantDtos.ShopThemeResponse(theme.getTemplateKey(), config);
    }

    /**
     * AG4: a FEATURE block may spotlight a product, but only a PUBLISHED, non-deleted product of this very shop.
     * Anything else (other shop, draft, archived, unknown id) is a 400 THEME_PRODUCT_INVALID on the block's productId.
     */
    private void assertBlockProducts(Shop shop, Map<String, Object> config) {
        var layout = asMap(config.get("layout"));
        if (layout == null || !(layout.get("blocks") instanceof List<?> blocks)) return;
        var problems = new ArrayList<FieldViolation>();
        for (int index = 0; index < blocks.size(); index++) {
            var block = asMap(blocks.get(index));
            if (block == null || ShopBlockType.from(string(block.get("type"))) != ShopBlockType.FEATURE) continue;
            var content = asMap(block.get("content"));
            if (content == null || content.get("productId") == null) continue;
            var id = ShopThemeBlockContent.productId(content);
            var published = id != null && products.findByIdAndShop_ShopIdAndDeletedAtIsNull(id, shop.getId())
                .filter(product -> product.getStatus() == ProductStatus.PUBLISHED)
                .isPresent();
            if (!published) {
                problems.add(ValidationMessages.violation("config.layout.blocks[" + index + "].content.productId", "THEME_PRODUCT_INVALID"));
            }
        }
        if (!problems.isEmpty()) throw new ValidationException(problems);
    }

    /** Read path: FEATURE blocks get a derived {@code productSlug} while the product is still published (never stored). */
    private void resolveFeatureProducts(Shop shop, Map<String, Object> config) {
        var layout = asMap(config.get("layout"));
        if (layout == null || !(layout.get("blocks") instanceof List<?> blocks)) return;
        for (var item : blocks) {
            var block = asMap(item);
            if (block == null || ShopBlockType.from(string(block.get("type"))) != ShopBlockType.FEATURE) continue;
            var content = asMap(block.get("content"));
            var id = ShopThemeBlockContent.productId(content);
            if (id == null) continue;
            products.findByIdAndShop_ShopIdAndDeletedAtIsNull(id, shop.getId())
                .filter(product -> product.getStatus() == ProductStatus.PUBLISHED)
                .ifPresent(product -> content.put("productSlug", product.getSlug()));
        }
    }

    /** Blank / non-string mediaId values are treated as absent so the banner falls back to imageUrl. */
    private static void normalizeMediaIds(Map<String, Object> config) {
        for (var row : bannerRows(config)) {
            if (!row.containsKey("mediaId")) continue;
            var value = string(row.get("mediaId"));
            if (value == null || value.isBlank()) row.remove("mediaId");
            else row.put("mediaId", value.trim());
        }
        var hero = asMap(config.get("hero"));
        if (hero != null && hero.containsKey("mediaId")) {
            var value = string(hero.get("mediaId"));
            if (value == null || value.isBlank()) hero.remove("mediaId");
            else hero.put("mediaId", value.trim());
        }
    }

    /** The stored banner keeps only mediaId; imageUrl is derived from it on read so it can never go stale. */
    private static void dropImageUrlWhereMediaId(Map<String, Object> config) {
        for (var row : bannerRows(config)) {
            if (row.containsKey("mediaId")) row.remove("imageUrl");
        }
    }

    /** Read path: banner mediaId → public {@code imageUrl} (mediaId stays in the payload for the editor). */
    private static void resolveBannerMedia(String slug, Map<String, Object> config) {
        for (var row : bannerRows(config)) {
            var raw = string(row.get("mediaId"));
            if (raw == null || raw.isBlank()) continue;
            try {
                row.put("imageUrl", ShopThemeMediaService.publicUrl(slug, UUID.fromString(raw.trim())));
            } catch (IllegalArgumentException ignored) {
                row.remove("mediaId");
            }
        }
    }

    private static List<Map<String, Object>> bannerRows(Map<String, Object> config) {
        var rows = new ArrayList<Map<String, Object>>();
        if (config != null && config.get("banners") instanceof List<?> banners) {
            for (var item : banners) {
                var row = asMap(item);
                if (row != null) rows.add(row);
            }
        }
        return rows;
    }

    /** Non-CUSTOM: remove only AG1 token keys; everything else is left exactly as pre-AG1. */
    private Map<String, Object> stripAg1Tokens(Map<String, Object> config, ShopThemeTemplate template) {
        if (config == null) return null;
        for (var key : AG1_TOP_KEYS) config.remove(key);
        var colors = asMap(config.get("colors"));
        if (colors != null) {
            for (var key : AG1_COLOR_KEYS) colors.remove(key);
            // Spec palette roles (borderStrong / sale / focus) belong to the themes-spec templates (GENERAL, FASHION); others drop them silently.
            if (!ThemePresets.allowsExtraColors(template)) {
                for (var key : ThemePresets.GENERAL_EXTRA_COLOR_KEYS) colors.remove(key);
            } else {
                for (var key : ThemePresets.GENERAL_EXTRA_COLOR_KEYS) {
                    var value = colors.get(key);
                    if (value == null || string(value) == null || string(value).isBlank()) colors.remove(key);
                }
            }
        }
        var style = asMap(config.get("style"));
        if (style != null) for (var key : AG1_STYLE_KEYS) style.remove(key);
        // GENERAL keeps its plain-text hero copy (eyebrow + secondary CTA); other templates drop those keys.
        var heroIn = asMap(config.get("hero"));
        if (heroIn != null && template != ShopThemeTemplate.GENERAL) {
            for (var key : ThemePresets.GENERAL_HERO_KEYS) heroIn.remove(key);
        }
        // AG5: header / footer / fontCustom / hero extras are dropped; MEDIA_FULL falls back to FULL except for GENERAL (themes port).
        ShopThemeChrome.stripForNonCustom(config, template == ShopThemeTemplate.GENERAL);
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stripUnknown(Map<String, Object> config) {
        if (config == null) return new LinkedHashMap<>();
        var out = new LinkedHashMap<String, Object>();
        for (var entry : config.entrySet()) {
            if (!TOP_KEYS.contains(entry.getKey())) continue;
            out.put(entry.getKey(), entry.getValue());
        }
        var colors = asMap(out.get("colors"));
        if (colors != null) out.put("colors", keepKeys(colors, COLOR_KEYS));
        var style = asMap(out.get("style"));
        if (style != null) out.put("style", keepKeys(style, STYLE_KEYS));
        var gradients = asMap(out.get("gradients"));
        if (gradients != null) {
            var cleaned = new LinkedHashMap<String, Object>();
            for (var slot : GRADIENT_SLOT_KEYS) {
                if (!gradients.containsKey(slot)) continue;
                var row = asMap(gradients.get(slot));
                if (row == null) {
                    cleaned.put(slot, gradients.get(slot));
                    continue;
                }
                var slotOut = keepKeys(row, GRADIENT_KEYS);
                if (slotOut.get("stops") instanceof List<?> stops) {
                    var stopsOut = new ArrayList<Object>();
                    for (var stop : stops) {
                        var stopRow = asMap(stop);
                        stopsOut.add(stopRow == null ? stop : keepKeys(stopRow, GRADIENT_STOP_KEYS));
                    }
                    slotOut.put("stops", stopsOut);
                }
                cleaned.put(slot, slotOut);
            }
            out.put("gradients", cleaned);
        }
        var productCard = asMap(out.get("productCard"));
        if (productCard != null) out.put("productCard", keepKeys(productCard, PRODUCT_CARD_KEYS));
        var sectionSettings = asMap(out.get("sectionSettings"));
        if (sectionSettings != null) {
            var cleaned = new LinkedHashMap<String, Object>();
            for (var key : ShopSectionKey.ALL) {
                var name = key.name();
                if (!sectionSettings.containsKey(name)) continue;
                var row = asMap(sectionSettings.get(name));
                if (row != null) cleaned.put(name, keepKeys(row, SECTION_SETTING_KEYS));
            }
            out.put("sectionSettings", cleaned);
        }
        ShopThemeBlocks.sanitize(out);
        ShopThemeChrome.sanitize(out);
        return out;
    }

    private static Map<String, Object> keepKeys(Map<String, Object> source, Set<String> allowed) {
        var out = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            if (allowed.contains(entry.getKey())) out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }

    /** GENERAL spec palette roles are optional, but when present they must be #RRGGBB (same code as the base colors). */
    private void validateGeneralExtraColors(Map<String, Object> config) {
        var colors = config == null ? null : asMap(config.get("colors"));
        if (colors == null) return;
        var problems = new ArrayList<FieldViolation>();
        for (var key : ThemePresets.GENERAL_EXTRA_COLOR_KEYS) {
            if (!colors.containsKey(key)) continue;
            var value = string(colors.get(key));
            if (value == null || !HEX.matcher(value).matches()) {
                problems.add(ValidationMessages.violation("config.colors." + key, "THEME_COLOR_INVALID"));
            }
        }
        var hero = config == null ? null : asMap(config.get("hero"));
        if (hero != null) {
            var eyebrow = hero.get("eyebrow");
            if (eyebrow != null && !(eyebrow instanceof String)) {
                problems.add(ValidationMessages.violation("config.hero.eyebrow", "THEME_HERO_TEXT_INVALID"));
            }
            checkLen(problems, "config.hero.eyebrow", string(eyebrow), 60, "THEME_HERO_EYEBROW_TOO_LONG");
            plainText(problems, "config.hero.eyebrow", string(eyebrow));
            plainText(problems, "config.hero.title", string(hero.get("title")));
            plainText(problems, "config.hero.subtitle", string(hero.get("subtitle")));
            plainText(problems, "config.hero.ctaLabel", string(hero.get("ctaLabel")));
            var label = hero.get("ctaSecondaryLabel");
            var link = hero.get("ctaSecondaryLink");
            if ((label != null && !(label instanceof String)) || (link != null && !(link instanceof String))) {
                problems.add(ValidationMessages.violation("config.hero.ctaSecondaryLabel", "THEME_HERO_TEXT_INVALID"));
            } else {
                checkLen(problems, "config.hero.ctaSecondaryLabel", string(label), 120, "THEME_HERO_CTA_TOO_LONG");
                plainText(problems, "config.hero.ctaSecondaryLabel", string(label));
                var href = string(link);
                if (href != null && !href.isBlank() && !INTERNAL_PATH.matcher(href).matches() && !HTTPS.matcher(href).matches()) {
                    problems.add(ValidationMessages.violation("config.hero.ctaSecondaryLink", "THEME_HERO_LINK_INVALID"));
                }
            }
        }
        if (!problems.isEmpty()) throw new ValidationException(problems);
    }

    private static final Set<String> INTRO_KEYS = Set.of("eyebrow", "title", "body", "ctaLabel", "ctaLink", "introMediaId", "imageUrl", "reverse");
    private static final Pattern HTTP_OR_HTTPS = Pattern.compile("^https?://[\\w\\-./?#=&%]+$", Pattern.CASE_INSENSITIVE);

    /** StoryBlock copy: plain text with length limits; CTA link is an internal path or http(s); reverse is a boolean. */
    private void validateIntro(Map<String, Object> config) {
        if (!config.containsKey("intro")) return;
        var raw = config.get("intro");
        if (raw == null) {
            config.remove("intro");
            return;
        }
        var problems = new ArrayList<FieldViolation>();
        var intro = asMap(raw);
        if (intro == null) {
            throw new ValidationException(List.of(ValidationMessages.violation("config.intro", "THEME_INTRO_TEXT_INVALID")));
        }
        intro.keySet().removeIf(key -> !INTRO_KEYS.contains(key));
        var limits = new LinkedHashMap<String, Integer>();
        limits.put("eyebrow", 60);
        limits.put("title", 120);
        limits.put("body", 1000);
        limits.put("ctaLabel", 60);
        limits.put("ctaLink", 500);
        for (var entry : limits.entrySet()) {
            var field = "config.intro." + entry.getKey();
            var value = intro.get(entry.getKey());
            if (value == null) continue;
            if (!(value instanceof String text)) {
                problems.add(ValidationMessages.violation(field, "THEME_INTRO_TEXT_INVALID"));
                continue;
            }
            if (text.length() > entry.getValue() || text.indexOf('<') >= 0 || text.indexOf('>') >= 0) {
                problems.add(ValidationMessages.violation(field, "THEME_INTRO_TEXT_INVALID"));
            }
        }
        var href = intro.get("ctaLink") instanceof String text ? text.trim() : null;
        if (href != null && !href.isEmpty() && !INTERNAL_PATH.matcher(href).matches() && !HTTP_OR_HTTPS.matcher(href).matches()) {
            problems.add(ValidationMessages.violation("config.intro.ctaLink", "THEME_INTRO_LINK_INVALID"));
        }
        var reverse = intro.get("reverse");
        if (reverse != null && !(reverse instanceof Boolean)) {
            problems.add(ValidationMessages.violation("config.intro.reverse", "THEME_INTRO_TEXT_INVALID"));
        }
        if (!problems.isEmpty()) throw new ValidationException(problems);
    }

    private static boolean badText(Object value, int max) {
        return !(value instanceof String text) || text.length() > max || text.indexOf('<') >= 0 || text.indexOf('>') >= 0;
    }

    /**
     * Themes port copy (non-CUSTOM): GENERAL MEDIA_FULL hero badges (max 3 x 24 chars, decorative stickers),
     * trust sub-lines (80 chars) and the announcement bar (max 2 x 120 chars). Plain text only.
     */
    @SuppressWarnings("unchecked")
    private void validateTsfCopy(Map<String, Object> config) {
        var problems = new ArrayList<FieldViolation>();
        var hero = asMap(config.get("hero"));
        if (hero != null && hero.containsKey("badges")) {
            var raw = hero.get("badges");
            if (raw == null) hero.remove("badges");
            else if (!(raw instanceof List<?> badges) || badges.size() > 3) {
                problems.add(ValidationMessages.violation("config.hero.badges", "THEME_HERO_BADGES_INVALID"));
            } else {
                for (int i = 0; i < badges.size(); i++) {
                    if (badText(badges.get(i), 24)) problems.add(ValidationMessages.violation("config.hero.badges[" + i + "]", "THEME_HERO_BADGES_INVALID"));
                }
            }
        }
        var trust = asMap(config.get("trust"));
        if (trust != null && trust.get("items") instanceof List<?> items) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) instanceof Map<?, ?> row && ((Map<String, Object>) row).containsKey("sub")) {
                    var sub = ((Map<String, Object>) row).get("sub");
                    if (sub == null) ((Map<String, Object>) row).remove("sub");
                    else if (badText(sub, 80)) problems.add(ValidationMessages.violation("config.trust.items[" + i + "].sub", "THEME_TRUST_SUB_INVALID"));
                }
            }
        }
        if (config.containsKey("announcement")) {
            var raw = config.get("announcement");
            var announcement = asMap(raw);
            if (raw == null) config.remove("announcement");
            else if (announcement == null) problems.add(ValidationMessages.violation("config.announcement", "THEME_ANNOUNCEMENT_INVALID"));
            else {
                announcement.keySet().removeIf(key -> !"items".equals(key));
                var list = announcement.get("items");
                if (list != null) {
                    if (!(list instanceof List<?> rows) || rows.size() > 2) {
                        problems.add(ValidationMessages.violation("config.announcement.items", "THEME_ANNOUNCEMENT_INVALID"));
                    } else {
                        for (int i = 0; i < rows.size(); i++) {
                            if (badText(rows.get(i), 120)) problems.add(ValidationMessages.violation("config.announcement.items[" + i + "]", "THEME_ANNOUNCEMENT_INVALID"));
                        }
                    }
                }
            }
        }
        if (!problems.isEmpty()) throw new ValidationException(problems);
    }

    /** GENERAL hero copy is plain text: no markup characters at all. */
    private static void plainText(List<FieldViolation> problems, String field, String value) {
        if (value != null && (value.indexOf('<') >= 0 || value.indexOf('>') >= 0)) {
            problems.add(ValidationMessages.violation(field, "THEME_HERO_TEXT_INVALID"));
        }
    }

    private void validateBrand(Map<String, Object> config, List<FieldViolation> problems) {
        var raw = config.get("brand");
        if (raw == null) { config.remove("brand"); return; }
        var brand = asMap(raw);
        if (brand == null) { problems.add(ValidationMessages.violation("config.brand", "THEME_TOKEN_INVALID")); return; }
        var out = new java.util.LinkedHashMap<String, Object>();
        for (var key : List.of("logoPosition", "textAlign")) {
            var value = brand.get(key);
            if (value == null) continue;
            var text = value instanceof String s ? s.trim().toUpperCase() : null;
            if (text == null || !BRAND_ALIGNS.contains(text)) problems.add(ValidationMessages.violation("config.brand." + key, "THEME_TOKEN_INVALID"));
            else out.put(key, text);
        }
        if (out.isEmpty()) config.remove("brand"); else config.put("brand", out);
    }

    @SuppressWarnings("unchecked")
    private void validateConfig(Map<String, Object> config, boolean custom) {
        var problems = new ArrayList<FieldViolation>();
        if (config == null) {
            throw new ValidationException(List.of(ValidationMessages.violation("config", "THEME_CONFIG_REQUIRED")));
        }

        var colors = asMap(config.get("colors"));
        if (colors == null) {
            problems.add(ValidationMessages.violation("config.colors", "THEME_COLORS_REQUIRED"));
        } else {
            for (var key : List.of("primary", "secondary", "background", "text")) {
                var value = string(colors.get(key));
                if (value == null || !HEX.matcher(value).matches()) {
                    problems.add(ValidationMessages.violation("config.colors." + key, "THEME_COLOR_INVALID"));
                }
            }
            if (custom) {
                // TSF-only colours — drop quietly on CUSTOM before optional hex checks.
                colors.remove("navBackground");
                colors.remove("navText");
                colors.remove("cardBackground");
                for (var key : OPTIONAL_COLORS) {
                    if (!colors.containsKey(key) || colors.get(key) == null || string(colors.get(key)).isBlank()) continue;
                    requireHex(problems, "config.colors." + key, colors.get(key));
                }
            } else {
                if (colors.get("accent") != null && !String.valueOf(colors.get("accent")).isBlank()) {
                    // Accent is editable for every template now, so it must be a hex everywhere.
                    requireHex(problems, "config.colors.accent", colors.get("accent"));
                }
                for (var key : List.of("navBackground", "navText", "cardBackground")) {
                    if (!colors.containsKey(key) || colors.get(key) == null || string(colors.get(key)).isBlank()) {
                        colors.remove(key);
                        continue;
                    }
                    requireHex(problems, "config.colors." + key, colors.get(key));
                }
            }
        }

        var font = ShopThemeFont.from(string(config.get("font")));
        if (font == null) problems.add(ValidationMessages.violation("config.font", "THEME_FONT_INVALID"));
        if (config.containsKey("fontBody")) {
            var rawBody = config.get("fontBody");
            if (rawBody == null || (rawBody instanceof String blank && blank.isBlank())) {
                config.remove("fontBody");
            } else {
                var bodyFont = rawBody instanceof String text ? ShopThemeFont.from(text) : null;
                if (bodyFont == null) problems.add(ValidationMessages.violation("config.fontBody", "THEME_FONT_INVALID"));
                else config.put("fontBody", bodyFont.name());
            }
        }
        if (config.containsKey("brand")) validateBrand(config, problems);

        var style = asMap(config.get("style"));
        if (style != null) {
            var radius = style.get("radius");
            int radiusValue = -1;
            if (radius instanceof Number number) radiusValue = number.intValue();
            else if (radius instanceof String text) {
                try { radiusValue = Integer.parseInt(text.trim()); } catch (NumberFormatException ignored) { /* fall through */ }
            }
            if (radiusValue < 0 || radiusValue > 32) {
                problems.add(ValidationMessages.violation("config.style.radius", "THEME_STYLE_RADIUS_INVALID"));
            }
            var cardStyle = string(style.get("cardStyle"));
            if (cardStyle == null || !List.of("FLAT", "BORDERED", "SHADOW").contains(cardStyle.trim().toUpperCase())) {
                problems.add(ValidationMessages.violation("config.style.cardStyle", "THEME_STYLE_CARD_INVALID"));
            }
            if (style.containsKey("cardRadius") && style.get("cardRadius") != null && !string(style.get("cardRadius")).isBlank()) {
                var cardRadius = string(style.get("cardRadius"));
                if (cardRadius == null || !CARD_RADII.contains(cardRadius.trim().toUpperCase())) {
                    problems.add(ValidationMessages.violation("config.style.cardRadius", "THEME_TOKEN_INVALID"));
                }
            }
            if (style.containsKey("cardGap") && style.get("cardGap") != null && !string(style.get("cardGap")).isBlank()) {
                var cardGap = string(style.get("cardGap"));
                if (cardGap == null || !CARD_GAPS.contains(cardGap.trim().toUpperCase())) {
                    problems.add(ValidationMessages.violation("config.style.cardGap", "THEME_TOKEN_INVALID"));
                }
            }
            var density = string(style.get("density"));
            if (density == null || !List.of("COMPACT", "COMFORTABLE").contains(density.trim().toUpperCase())) {
                problems.add(ValidationMessages.violation("config.style.density", "THEME_STYLE_DENSITY_INVALID"));
            }
            var buttonShape = string(style.get("buttonShape"));
            if (buttonShape == null || !List.of("ROUNDED", "PILL", "SQUARE").contains(buttonShape.trim().toUpperCase())) {
                problems.add(ValidationMessages.violation("config.style.buttonShape", "THEME_STYLE_BUTTON_INVALID"));
            }
            if (custom && style.containsKey("shadow") && style.get("shadow") != null && !string(style.get("shadow")).isBlank()) {
                var shadow = string(style.get("shadow"));
                if (shadow == null || !SHADOWS.contains(shadow.trim().toUpperCase())) {
                    problems.add(ValidationMessages.violation("config.style.shadow", "THEME_TOKEN_INVALID"));
                }
            }
            if (custom && style.containsKey("headingTextShadow") && style.get("headingTextShadow") != null
                && !(style.get("headingTextShadow") instanceof Boolean)) {
                problems.add(ValidationMessages.violation("config.style.headingTextShadow", "THEME_TOKEN_INVALID"));
            }
            if (custom) {
                style.remove("productSectionBackground");
            } else if (style.containsKey("productSectionBackground")) {
                var band = string(style.get("productSectionBackground"));
                if (band == null || band.isBlank()) {
                    style.remove("productSectionBackground");
                } else if (!List.of("NONE", "SURFACE").contains(band.trim().toUpperCase())) {
                    problems.add(ValidationMessages.violation("config.style.productSectionBackground", "THEME_TOKEN_INVALID"));
                } else {
                    style.put("productSectionBackground", band.trim().toUpperCase());
                }
            }
        }

        var hero = asMap(config.get("hero"));
        if (hero == null) {
            problems.add(ValidationMessages.violation("config.hero", "THEME_HERO_REQUIRED"));
        } else {
            var layout = ShopSectionVariant.from(string(hero.get("layout")));
            if (layout == null || !layout.allowedFor(ShopSectionKey.HERO)) {
                problems.add(ValidationMessages.violation("config.hero.layout", "THEME_HERO_LAYOUT_INVALID"));
            }
            checkLen(problems, "config.hero.title", string(hero.get("title")), 120, "THEME_HERO_TITLE_TOO_LONG");
            checkLen(problems, "config.hero.subtitle", string(hero.get("subtitle")), 240, "THEME_HERO_SUBTITLE_TOO_LONG");
            checkLen(problems, "config.hero.ctaLabel", string(hero.get("ctaLabel")), 120, "THEME_HERO_CTA_TOO_LONG");
            var link = string(hero.get("ctaLink"));
            if (link != null && !link.isBlank() && !INTERNAL_PATH.matcher(link).matches() && !HTTPS.matcher(link).matches()) {
                problems.add(ValidationMessages.violation("config.hero.ctaLink", "THEME_HERO_LINK_INVALID"));
            }
        }

        var sectionsRaw = config.get("sections");
        if (!(sectionsRaw instanceof List<?> sections) || sections.isEmpty()) {
            problems.add(ValidationMessages.violation("config.sections", "THEME_SECTIONS_REQUIRED"));
        } else {
            var seen = EnumSet.noneOf(ShopSectionKey.class);
            for (int index = 0; index < sections.size(); index++) {
                var prefix = "config.sections[" + index + "]";
                if (!(sections.get(index) instanceof Map<?, ?> row)) {
                    problems.add(ValidationMessages.violation(prefix, "THEME_SECTION_INVALID"));
                    continue;
                }
                var map = (Map<String, Object>) row;
                var key = ShopSectionKey.from(string(map.get("key")));
                if (key == null) {
                    problems.add(ValidationMessages.violation(prefix + ".key", "THEME_SECTION_KEY_INVALID"));
                    continue;
                }
                if (!seen.add(key)) {
                    problems.add(ValidationMessages.violation(prefix + ".key", "THEME_SECTION_KEY_DUPLICATE"));
                }
                var enabled = map.get("enabled");
                if (!(enabled instanceof Boolean)) {
                    problems.add(ValidationMessages.violation(prefix + ".enabled", "THEME_SECTION_ENABLED_INVALID"));
                }
                var variantRaw = string(map.get("variant"));
                if (key.allowsVariant()) {
                    var variant = ShopSectionVariant.from(variantRaw);
                    if (variant == null || !variant.allowedFor(key)) {
                        problems.add(ValidationMessages.violation(prefix + ".variant", "THEME_SECTION_VARIANT_INVALID"));
                    }
                } else if (variantRaw != null && !variantRaw.isBlank()) {
                    problems.add(ValidationMessages.violation(prefix + ".variant", "THEME_SECTION_VARIANT_INVALID"));
                }
                validateProductCardLook(problems, prefix, key, map);
            }
            for (var required : ShopSectionKey.ALL) {
                if (!seen.contains(required)) {
                    problems.add(ValidationMessages.violation("config.sections", "THEME_SECTIONS_INCOMPLETE"));
                    break;
                }
            }
        }

        var bannersRaw = config.get("banners");
        if (bannersRaw != null) {
            if (!(bannersRaw instanceof List<?> banners)) {
                problems.add(ValidationMessages.violation("config.banners", "THEME_BANNERS_INVALID"));
            } else if (banners.size() > 8) {
                problems.add(ValidationMessages.violation("config.banners", "THEME_BANNERS_TOO_MANY"));
            } else {
                for (int index = 0; index < banners.size(); index++) {
                    var prefix = "config.banners[" + index + "]";
                    if (!(banners.get(index) instanceof Map<?, ?> row)) {
                        problems.add(ValidationMessages.violation(prefix, "THEME_BANNER_INVALID"));
                        continue;
                    }
                    var map = (Map<String, Object>) row;
                    var imageUrl = string(map.get("imageUrl"));
                    var mediaId = custom ? string(map.get("mediaId")) : null;
                    if (mediaId != null && !mediaId.isBlank()) {
                        // AG2: validated against the media library after structural checks (assertMediaRefs).
                    } else if (imageUrl == null || imageUrl.isBlank()) {
                        problems.add(ValidationMessages.violation(prefix + ".imageUrl", "THEME_BANNER_IMAGE_REQUIRED"));
                    } else if (!INTERNAL_PATH.matcher(imageUrl).matches() && !HTTPS.matcher(imageUrl).matches()) {
                        problems.add(ValidationMessages.violation(prefix + ".imageUrl", "THEME_BANNER_IMAGE_INVALID"));
                    }
                    checkLen(problems, prefix + ".title", string(map.get("title")), 80, "THEME_BANNER_TITLE_TOO_LONG");
                    var link = string(map.get("link"));
                    if (link != null && !link.isBlank() && !INTERNAL_PATH.matcher(link).matches() && !HTTPS.matcher(link).matches()) {
                        problems.add(ValidationMessages.violation(prefix + ".link", "THEME_BANNER_LINK_INVALID"));
                    }
                }
            }
        }

        var trust = asMap(config.get("trust"));
        if (trust != null) {
            if (trust.get("enabled") != null && !(trust.get("enabled") instanceof Boolean)) {
                problems.add(ValidationMessages.violation("config.trust.enabled", "THEME_TRUST_ENABLED_INVALID"));
            }
            var itemsRaw = trust.get("items");
            if (itemsRaw != null) {
                if (!(itemsRaw instanceof List<?> items) || items.isEmpty() || items.size() > 8) {
                    problems.add(ValidationMessages.violation("config.trust.items", "THEME_TRUST_ITEMS_INVALID"));
                } else {
                    for (int index = 0; index < items.size(); index++) {
                        var prefix = "config.trust.items[" + index + "]";
                        if (!(items.get(index) instanceof Map<?, ?> row)) {
                            problems.add(ValidationMessages.violation(prefix, "THEME_TRUST_ITEM_INVALID"));
                            continue;
                        }
                        var map = (Map<String, Object>) row;
                        checkLen(problems, prefix + ".label", string(map.get("label")), 60, "THEME_TRUST_LABEL_TOO_LONG");
                        var icon = string(map.get("icon"));
                        if (icon != null && !icon.isBlank()
                            && !List.of("AUTHENTIC", "FAST_SHIP", "RETURNS", "SECURE_PAY", "CUSTOM").contains(icon.trim().toUpperCase())) {
                            problems.add(ValidationMessages.violation(prefix + ".icon", "THEME_TRUST_ICON_INVALID"));
                        }
                    }
                }
            }
        }

        if (custom) {
            validateGradients(problems, asMap(config.get("gradients")));
            validateProductCard(problems, asMap(config.get("productCard")));
            validateSectionSettings(problems, asMap(config.get("sectionSettings")));
            validateBlocks(problems, config);
            // AG5: MEDIA_FULL hero extras, header, footer, fontCustom.
            ShopThemeChrome.validate(problems, config, this::validateGradientRow);
        }

        if (!problems.isEmpty()) throw new ValidationException(problems);
    }

    @SuppressWarnings("unchecked")
    private void validateGradients(List<FieldViolation> problems, Map<String, Object> gradients) {
        if (gradients == null) return;
        for (var slot : GRADIENT_SLOT_KEYS) {
            if (!gradients.containsKey(slot) || gradients.get(slot) == null) continue;
            validateGradientRow(problems, "config.gradients." + slot, gradients.get(slot));
        }
    }

    /** Shared by AG1 gradient slots and AG3 block gradients: structured angle + 2–4 hex stops, never CSS strings. */
    @SuppressWarnings("unchecked")
    private void validateGradientRow(List<FieldViolation> problems, String prefix, Object rawRow) {
        {
            var row = asMap(rawRow);
            if (row == null) {
                problems.add(ValidationMessages.violation(prefix, "THEME_GRADIENT_INVALID"));
                return;
            }
            int angle = -1;
            var angleRaw = row.get("angle");
            if (angleRaw instanceof Number number) angle = number.intValue();
            else if (angleRaw instanceof String text) {
                try { angle = Integer.parseInt(text.trim()); } catch (NumberFormatException ignored) { /* fall through */ }
            }
            if (angle < 0 || angle > 360) {
                problems.add(ValidationMessages.violation(prefix + ".angle", "THEME_GRADIENT_INVALID"));
            }
            if (!(row.get("stops") instanceof List<?> stops) || stops.size() < 2 || stops.size() > 4) {
                problems.add(ValidationMessages.violation(prefix + ".stops", "THEME_GRADIENT_INVALID"));
                return;
            }
            for (int i = 0; i < stops.size(); i++) {
                var stopPrefix = prefix + ".stops[" + i + "]";
                if (!(stops.get(i) instanceof Map<?, ?> stopRow)) {
                    problems.add(ValidationMessages.violation(stopPrefix, "THEME_GRADIENT_INVALID"));
                    continue;
                }
                var stop = (Map<String, Object>) stopRow;
                requireHex(problems, stopPrefix + ".color", stop.get("color"));
                int at = -1;
                var atRaw = stop.get("at");
                if (atRaw instanceof Number number) at = number.intValue();
                else if (atRaw instanceof String text) {
                    try { at = Integer.parseInt(text.trim()); } catch (NumberFormatException ignored) { /* fall through */ }
                }
                if (at < 0 || at > 100) {
                    problems.add(ValidationMessages.violation(stopPrefix + ".at", "THEME_GRADIENT_INVALID"));
                }
            }
        }
    }

    /** AG3: {@code layout.blocks} (CUSTOM only). Structure → THEME_BLOCKS_INVALID, tokens → THEME_TOKEN_INVALID. */
    private void validateBlocks(List<FieldViolation> problems, Map<String, Object> config) {
        var layout = asMap(config.get("layout"));
        if (layout == null || !layout.containsKey("blocks")) return;
        var base = "config.layout.blocks";
        if (!(layout.get("blocks") instanceof List<?> blocks)) {
            problems.add(ValidationMessages.violation(base, "THEME_BLOCKS_INVALID"));
            return;
        }
        if (blocks.size() > ShopThemeBlocks.MAX_BLOCKS) {
            problems.add(ValidationMessages.violation(base, "THEME_BLOCKS_INVALID"));
        }
        var ids = new java.util.HashSet<String>();
        var singletons = EnumSet.noneOf(ShopBlockType.class);
        int limit = Math.min(blocks.size(), ShopThemeBlocks.MAX_BLOCKS);
        for (int index = 0; index < limit; index++) {
            var prefix = base + "[" + index + "]";
            var block = asMap(blocks.get(index));
            if (block == null) {
                problems.add(ValidationMessages.violation(prefix, "THEME_BLOCKS_INVALID"));
                continue;
            }
            var id = block.get("id") instanceof String text ? text : null;
            if (id == null || !ShopThemeBlocks.ID.matcher(id).matches()) {
                problems.add(ValidationMessages.violation(prefix + ".id", "THEME_BLOCKS_INVALID"));
            } else if (!ids.add(id)) {
                problems.add(ValidationMessages.violation(prefix + ".id", "THEME_BLOCKS_INVALID"));
            }
            // AG4: the block type is a ShopBlockType; only the legacy ten map to a ShopSectionKey (variants / columns).
            var blockType = ShopBlockType.from(string(block.get("type")));
            var type = blockType == null ? null : blockType.sectionKey();
            if (blockType == null) {
                problems.add(ValidationMessages.violation(prefix + ".type", "THEME_BLOCKS_INVALID"));
            } else if (ShopThemeBlocks.SINGLETON_TYPES.contains(blockType) && !singletons.add(blockType)) {
                problems.add(ValidationMessages.violation(prefix + ".type", "THEME_BLOCKS_INVALID"));
            }
            if (!(block.get("enabled") instanceof Boolean)) {
                problems.add(ValidationMessages.violation(prefix + ".enabled", "THEME_BLOCKS_INVALID"));
            }
            var variantRaw = string(block.get("variant"));
            if (blockType != null) {
                if (type != null && type.allowsVariant()) {
                    if (variantRaw != null) {
                        var variant = ShopSectionVariant.from(variantRaw);
                        if (variant == null || !variant.allowedFor(type)) {
                            problems.add(ValidationMessages.violation(prefix + ".variant", "THEME_BLOCKS_INVALID"));
                        }
                    }
                } else if (variantRaw != null) {
                    problems.add(ValidationMessages.violation(prefix + ".variant", "THEME_BLOCKS_INVALID"));
                }
            }
            if (block.get("content") != null && !(block.get("content") instanceof Map<?, ?>)) {
                problems.add(ValidationMessages.violation(prefix + ".content", "THEME_BLOCKS_INVALID"));
            } else if (blockType != null && blockType.hasContent()) {
                ShopThemeBlockContent.validate(problems, prefix + ".content", blockType, asMap(block.get("content")));
            }

            var settings = asMap(block.get("settings"));
            var sp = prefix + ".settings";
            if (settings == null) {
                problems.add(ValidationMessages.violation(sp, "THEME_BLOCKS_INVALID"));
                continue;
            }
            var background = string(settings.get("background"));
            if (background == null || !ShopThemeBlocks.BACKGROUNDS.contains(background)) {
                problems.add(ValidationMessages.violation(sp + ".background", "THEME_TOKEN_INVALID"));
            } else if ("IMAGE".equals(background)
                && (settings.get(ShopThemeBlocks.MEDIA_KEY) == null || string(settings.get(ShopThemeBlocks.MEDIA_KEY)).isBlank())) {
                problems.add(ValidationMessages.violation(sp + "." + ShopThemeBlocks.MEDIA_KEY, "THEME_BLOCKS_INVALID"));
            }
            var align = string(settings.get("align"));
            if (align == null || !ShopThemeBlocks.ALIGNS.contains(align)) {
                problems.add(ValidationMessages.violation(sp + ".align", "THEME_TOKEN_INVALID"));
            }
            var padding = string(settings.get("paddingY"));
            if (padding == null || !ShopThemeBlocks.PADDINGS.contains(padding)) {
                problems.add(ValidationMessages.violation(sp + ".paddingY", "THEME_TOKEN_INVALID"));
            }
            if (!(settings.get("fullWidth") instanceof Boolean)) {
                problems.add(ValidationMessages.violation(sp + ".fullWidth", "THEME_TOKEN_INVALID"));
            }
            if (settings.containsKey("overlay")) {
                var overlay = ShopThemeBlocks.intValue(settings.get("overlay"));
                if (overlay == null || overlay < 0 || overlay > 80) {
                    problems.add(ValidationMessages.violation(sp + ".overlay", "THEME_TOKEN_INVALID"));
                }
            }
            if (settings.containsKey("columns")) {
                var columns = ShopThemeBlocks.intValue(settings.get("columns"));
                if (type == null || !ShopThemeBlocks.PRODUCT_TYPES.contains(type)
                    || columns == null || !SECTION_COLUMNS.contains(columns)) {
                    problems.add(ValidationMessages.violation(sp + ".columns", "THEME_TOKEN_INVALID"));
                }
            }
            if (settings.containsKey("cardRadius") && settings.get("cardRadius") != null && !string(settings.get("cardRadius")).isBlank()) {
                var value = string(settings.get("cardRadius"));
                if (type == null || !ShopThemeBlocks.PRODUCT_TYPES.contains(type)
                    || value == null || !CARD_RADII.contains(value.trim().toUpperCase())) {
                    problems.add(ValidationMessages.violation(sp + ".cardRadius", "THEME_TOKEN_INVALID"));
                }
            }
            if (settings.containsKey("cardGap") && settings.get("cardGap") != null && !string(settings.get("cardGap")).isBlank()) {
                var value = string(settings.get("cardGap"));
                if (type == null || !ShopThemeBlocks.PRODUCT_TYPES.contains(type)
                    || value == null || !CARD_GAPS.contains(value.trim().toUpperCase())) {
                    problems.add(ValidationMessages.violation(sp + ".cardGap", "THEME_TOKEN_INVALID"));
                }
            }
            if (settings.containsKey("gradient")) {
                validateGradientRow(problems, sp + ".gradient", settings.get("gradient"));
            }
        }
    }

    private void validateProductCardLook(
        List<FieldViolation> problems,
        String prefix,
        ShopSectionKey key,
        Map<String, Object> map
    ) {
        var product = PRODUCT_SECTIONS.contains(key.name());
        for (var entry : List.of(
            Map.entry("cardRadius", CARD_RADII),
            Map.entry("cardGap", CARD_GAPS)
        )) {
            var field = entry.getKey();
            var allowed = entry.getValue();
            var raw = string(map.get(field));
            if (raw == null || raw.isBlank()) continue;
            if (!product || !allowed.contains(raw.trim().toUpperCase())) {
                problems.add(ValidationMessages.violation(prefix + "." + field, "THEME_TOKEN_INVALID"));
            }
        }
    }

    private void validateProductCard(List<FieldViolation> problems, Map<String, Object> card) {
        if (card == null) return;
        if (card.containsKey("imageRatio") && card.get("imageRatio") != null && !string(card.get("imageRatio")).isBlank()) {
            var value = string(card.get("imageRatio"));
            if (value == null || !IMAGE_RATIOS.contains(value.trim().toUpperCase())) {
                problems.add(ValidationMessages.violation("config.productCard.imageRatio", "THEME_TOKEN_INVALID"));
            }
        }
        if (card.containsKey("ctaStyle") && card.get("ctaStyle") != null && !string(card.get("ctaStyle")).isBlank()) {
            var value = string(card.get("ctaStyle"));
            if (value == null || !CTA_STYLES.contains(value.trim().toUpperCase())) {
                problems.add(ValidationMessages.violation("config.productCard.ctaStyle", "THEME_TOKEN_INVALID"));
            }
        }
        if (card.containsKey("titleAlign") && card.get("titleAlign") != null && !string(card.get("titleAlign")).isBlank()) {
            var value = string(card.get("titleAlign"));
            if (value == null || !TITLE_ALIGNS.contains(value.trim().toUpperCase())) {
                problems.add(ValidationMessages.violation("config.productCard.titleAlign", "THEME_TOKEN_INVALID"));
            }
        }
        for (var boolKey : List.of("showPrice", "edgeToEdge")) {
            if (card.containsKey(boolKey) && card.get(boolKey) != null && !(card.get(boolKey) instanceof Boolean)) {
                problems.add(ValidationMessages.violation("config.productCard." + boolKey, "THEME_TOKEN_INVALID"));
            }
        }
    }

    private void validateSectionSettings(List<FieldViolation> problems, Map<String, Object> settings) {
        if (settings == null) return;
        for (var entry : settings.entrySet()) {
            var key = ShopSectionKey.from(entry.getKey());
            var prefix = "config.sectionSettings." + entry.getKey();
            if (key == null) continue; // unknown section keys are stripped, never a 400
            var row = asMap(entry.getValue());
            if (row == null) {
                problems.add(ValidationMessages.violation(prefix, "THEME_TOKEN_INVALID"));
                continue;
            }
            if (row.containsKey("background") && row.get("background") != null && !string(row.get("background")).isBlank()) {
                var value = string(row.get("background"));
                if (value == null || !SECTION_BACKGROUNDS.contains(value.trim().toUpperCase())) {
                    problems.add(ValidationMessages.violation(prefix + ".background", "THEME_TOKEN_INVALID"));
                }
            }
            if (row.containsKey("align") && row.get("align") != null && !string(row.get("align")).isBlank()) {
                var value = string(row.get("align"));
                if (value == null || !SECTION_ALIGNS.contains(value.trim().toUpperCase())) {
                    problems.add(ValidationMessages.violation(prefix + ".align", "THEME_TOKEN_INVALID"));
                }
            }
            if (row.containsKey("paddingY") && row.get("paddingY") != null && !string(row.get("paddingY")).isBlank()) {
                var value = string(row.get("paddingY"));
                if (value == null || !PADDING_Y.contains(value.trim().toUpperCase())) {
                    problems.add(ValidationMessages.violation(prefix + ".paddingY", "THEME_TOKEN_INVALID"));
                }
            }
            if (row.containsKey("columns") && row.get("columns") != null) {
                if (!PRODUCT_SECTIONS.contains(key.name())) {
                    problems.add(ValidationMessages.violation(prefix + ".columns", "THEME_TOKEN_INVALID"));
                } else {
                    int columns = -1;
                    var raw = row.get("columns");
                    if (raw instanceof Number number) columns = number.intValue();
                    else if (raw instanceof String text) {
                        try { columns = Integer.parseInt(text.trim()); } catch (NumberFormatException ignored) { /* fall through */ }
                    }
                    if (!SECTION_COLUMNS.contains(columns)) {
                        problems.add(ValidationMessages.violation(prefix + ".columns", "THEME_TOKEN_INVALID"));
                    }
                }
            }
        }
    }

    private void requireHex(List<FieldViolation> problems, String field, Object value) {
        var text = string(value);
        if (text == null || !HEX.matcher(text).matches()) {
            problems.add(ValidationMessages.violation(field, "THEME_COLOR_INVALID"));
            return;
        }
        rejectDangerous(problems, field, text);
    }

    private void rejectDangerous(List<FieldViolation> problems, String field, String value) {
        if (value != null && DANGEROUS.matcher(value).find()) {
            problems.add(ValidationMessages.violation(field, "THEME_TOKEN_INVALID"));
        }
    }

    private void checkLen(List<FieldViolation> problems, String field, String value, int max, String code) {
        if (value != null && value.length() > max) problems.add(ValidationMessages.violation(field, code));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return null;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Shop ownedShop(UUID userId, Long shopId) {
        if (shopId != null && authorization.isSuperAdmin(userId)) {
            return shops.findByShopId(shopId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
        }
        var owned = shops.findMemberShops(userId);
        if (owned.isEmpty()) {
            throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ยังไม่มีร้านค้าที่สมัครไว้");
        }
        if (shopId == null) return owned.get(0);
        return owned.stream().filter(shop -> shop.getId().equals(shopId)).findFirst()
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
    }
}
