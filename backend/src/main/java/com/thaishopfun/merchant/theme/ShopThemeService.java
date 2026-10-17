package com.thaishopfun.merchant.theme;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.common.ValidationMessages;
import com.thaishopfun.merchant.MerchantDtos;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ShopThemeService {
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern INTERNAL_PATH = Pattern.compile("^/[\\w\\-./?#=&%]*$");
    private static final Pattern HTTPS = Pattern.compile("^https://[\\w\\-./?#=&%]+$", Pattern.CASE_INSENSITIVE);

    private final ShopThemeRepository themes;
    private final ShopRepository shops;
    private final AuthorizationService authorization;

    public ShopThemeService(ShopThemeRepository themes, ShopRepository shops, AuthorizationService authorization) {
        this.themes = themes;
        this.shops = shops;
        this.authorization = authorization;
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
            new MerchantDtos.ShopThemePresetResponse(ShopThemeTemplate.FOOD_BEVERAGE.name(), ThemePresets.foodBeverageConfig())
        );
    }

    @Transactional
    public MerchantDtos.ShopThemeResponse save(UUID userId, Long shopId, MerchantDtos.ShopThemeUpdateRequest request) {
        var shop = ownedShop(userId, shopId);
        var template = ShopThemeTemplate.from(request.templateKey());
        if (template == null) {
            throw new ValidationException(List.of(ValidationMessages.violation("templateKey", "THEME_TEMPLATE_INVALID")));
        }
        var config = ThemePresets.deepCopy(request.config());
        ThemePresets.ensureStyle(config, template);
        ThemePresets.ensureMallExtras(config, template);
        validateConfig(config);
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
        var resolved = template == null ? ShopThemeTemplate.GENERAL : template;
        var config = ThemePresets.deepCopy(theme.getConfig());
        ThemePresets.ensureStyle(config, resolved);
        ThemePresets.ensureMallExtras(config, resolved);
        return new MerchantDtos.ShopThemeResponse(theme.getTemplateKey(), config);
    }

    @SuppressWarnings("unchecked")
    private void validateConfig(Map<String, Object> config) {
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
        }

        var font = ShopThemeFont.from(string(config.get("font")));
        if (font == null) problems.add(ValidationMessages.violation("config.font", "THEME_FONT_INVALID"));

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
            var density = string(style.get("density"));
            if (density == null || !List.of("COMPACT", "COMFORTABLE").contains(density.trim().toUpperCase())) {
                problems.add(ValidationMessages.violation("config.style.density", "THEME_STYLE_DENSITY_INVALID"));
            }
            var buttonShape = string(style.get("buttonShape"));
            if (buttonShape == null || !List.of("ROUNDED", "PILL", "SQUARE").contains(buttonShape.trim().toUpperCase())) {
                problems.add(ValidationMessages.violation("config.style.buttonShape", "THEME_STYLE_BUTTON_INVALID"));
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
                    if (imageUrl == null || imageUrl.isBlank()) {
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

        if (!problems.isEmpty()) throw new ValidationException(problems);
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
