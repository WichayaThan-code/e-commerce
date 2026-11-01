package com.thaishopfun.merchant.theme;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.common.ValidationMessages;
import com.thaishopfun.merchant.MerchantDtos;
import com.thaishopfun.merchant.MerchantStorageService;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** AG2: per-shop media library for CUSTOM storefront themes (images, MP4 videos, WOFF2 fonts). */
@Service
public class ShopThemeMediaService {
    private static final String MEDIA_KEY = "mediaId";
    /** AG3: IMAGE background of a layout block ({@code layout.blocks[].settings.backgroundMediaId}). */
    private static final String BLOCK_MEDIA_KEY = "backgroundMediaId";
    /** AG5: extra id keys of the CUSTOM chrome (hero video poster, custom fonts). */
    private static final Set<String> CHROME_MEDIA_KEYS = Set.of("posterMediaId", "headingMediaId", "bodyMediaId", "slideMediaId", "introMediaId");
    /** Hero slider (every template): {@code hero.slides[].slideMediaId}, at most {@link #MAX_HERO_SLIDES}. */
    public static final String SLIDE_KEY = "slideMediaId";
    public static final int MAX_HERO_SLIDES = 5;
    public static final String INTRO_KEY = "introMediaId";

    private final ShopThemeMediaRepository media;
    private final ShopThemeRepository themes;
    private final ShopRepository shops;
    private final AuthorizationService authorization;
    private final MerchantStorageService storage;
    private final ThemeMediaProperties limits;

    public ShopThemeMediaService(
        ShopThemeMediaRepository media,
        ShopThemeRepository themes,
        ShopRepository shops,
        AuthorizationService authorization,
        MerchantStorageService storage,
        ThemeMediaProperties limits
    ) {
        this.media = media;
        this.themes = themes;
        this.shops = shops;
        this.authorization = authorization;
        this.storage = storage;
        this.limits = limits;
    }

    public record PublicFile(Path path, String mimeType) {}

    @Transactional
    public MerchantDtos.ThemeMediaResponse upload(UUID userId, Long shopId, String kindRaw, MultipartFile file) {
        var shop = ownedShop(userId, shopId);
        var kind = ShopThemeMediaKind.from(kindRaw);
        if (kind == null) throw new AuthException(HttpStatus.BAD_REQUEST, "THEME_MEDIA_KIND_INVALID", ValidationMessages.text("THEME_MEDIA_KIND_INVALID"));
        // Hero slides: every template may upload IMAGEs (same validation + quota); VIDEO / FONT stay CUSTOM-only.
        if (kind != ShopThemeMediaKind.IMAGE && !isCustom(shop)) {
            throw new AuthException(HttpStatus.CONFLICT, "THEME_MEDIA_CUSTOM_ONLY", ValidationMessages.text("THEME_MEDIA_CUSTOM_ONLY"));
        }
        if (file == null || file.isEmpty()) throw invalid();
        var maxBytes = maxBytes(kind);
        if (file.getSize() > maxBytes) {
            throw new AuthException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "ไฟล์ต้องมีขนาดไม่เกิน " + (maxBytes / (1024 * 1024)) + " MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw invalid();
        }
        if (bytes.length > maxBytes) {
            throw new AuthException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "ไฟล์ต้องมีขนาดไม่เกิน " + (maxBytes / (1024 * 1024)) + " MB");
        }

        // Never trust the client MIME type or file name: the type comes from the leading bytes only.
        String mime;
        String extension;
        Integer width = null;
        Integer height = null;
        switch (kind) {
            case IMAGE -> {
                mime = MerchantStorageService.sniffedImageType(bytes);
                if (mime == null) throw invalid();
                extension = switch (mime) {
                    case "image/jpeg" -> ".jpg";
                    case "image/png" -> ".png";
                    default -> ".webp";
                };
                var dims = "image/webp".equals(mime) ? webpDimensions(bytes) : imageIoDimensions(bytes);
                if (dims == null && !"image/webp".equals(mime)) throw invalid();
                if (dims != null) {
                    width = dims[0];
                    height = dims[1];
                    if (width < 1 || height < 1 || width > limits.maxImageDimension() || height > limits.maxImageDimension()) throw invalid();
                }
            }
            case VIDEO -> {
                if (!isMp4(bytes)) throw invalid();
                mime = "video/mp4";
                extension = ".mp4";
            }
            default -> {
                if (!isWoff2(bytes)) throw invalid();
                mime = "font/woff2";
                extension = ".woff2";
            }
        }

        var sha256 = sha256(bytes);
        var duplicate = media.findByShopIdAndSha256AndDeletedAtIsNull(shop.getUuid(), sha256).orElse(null);
        if (duplicate != null) return toResponse(shop, duplicate);

        enforceQuota(shop.getUuid(), kind, bytes.length);

        var stored = storage.storeThemeMedia(shop.getUuid(), bytes, extension, mime);
        var row = media.save(new ShopThemeMedia(
            shop.getUuid(), kind, stored.path(), safeFilename(file.getOriginalFilename()), mime, bytes.length,
            width, height, sha256, userId
        ));
        return toResponse(shop, row);
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.ThemeMediaResponse> list(UUID userId, Long shopId, String kindRaw) {
        var shop = ownedShop(userId, shopId);
        List<ShopThemeMedia> rows;
        if (kindRaw == null || kindRaw.isBlank()) {
            rows = media.findByShopIdAndDeletedAtIsNullOrderByCreatedDateTimeDesc(shop.getUuid());
        } else {
            var kind = ShopThemeMediaKind.from(kindRaw);
            if (kind == null) throw new AuthException(HttpStatus.BAD_REQUEST, "THEME_MEDIA_KIND_INVALID", ValidationMessages.text("THEME_MEDIA_KIND_INVALID"));
            rows = media.findByShopIdAndKindAndDeletedAtIsNullOrderByCreatedDateTimeDesc(shop.getUuid(), kind);
        }
        return rows.stream().map(row -> toResponse(shop, row)).toList();
    }

    @Transactional
    public void softDelete(UUID userId, Long shopId, UUID mediaId) {
        var shop = ownedShop(userId, shopId);
        var row = media.findByIdAndShopIdAndDeletedAtIsNull(mediaId, shop.getUuid()).orElseThrow(this::notFound);
        var theme = themes.findByShopId(shop.getUuid()).orElse(null);
        if (theme != null && collectMediaIds(theme.getConfig()).contains(mediaId)) {
            throw new AuthException(HttpStatus.CONFLICT, "THEME_MEDIA_IN_USE", ValidationMessages.text("THEME_MEDIA_IN_USE"));
        }
        row.softDelete(userId);
        media.save(row);
    }

    /** Caller must already have resolved a shop the viewer may see (approved, or previewing). */
    @Transactional(readOnly = true)
    public PublicFile resolvePublicFile(Shop shop, UUID mediaId) {
        var row = media.findByIdAndShopIdAndDeletedAtIsNull(mediaId, shop.getUuid()).orElseThrow(this::notFound);
        var path = storage.openThemeMediaFile(row.getStoragePath()).orElseThrow(this::notFound);
        return new PublicFile(path, row.getMimeType());
    }

    /** Every {@code mediaId} string that parses as a UUID anywhere in the theme config (banners, hero, ...). */
    public static Set<UUID> collectMediaIds(Map<String, Object> config) {
        var out = new LinkedHashSet<UUID>();
        collect(config, out);
        return out;
    }

    private static void collect(Object node, Set<UUID> out) {
        if (node instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if ((MEDIA_KEY.equals(entry.getKey()) || BLOCK_MEDIA_KEY.equals(entry.getKey()) || CHROME_MEDIA_KEYS.contains(entry.getKey()))
                    && entry.getValue() != null) {
                    var parsed = parse(String.valueOf(entry.getValue()));
                    if (parsed != null) out.add(parsed);
                } else {
                    collect(entry.getValue(), out);
                }
            }
        } else if (node instanceof List<?> list) {
            for (var item : list) collect(item, out);
        }
    }

    /** Recursively drops every {@code mediaId} key (used for non-CUSTOM templates, which never carry media). */
    public static void stripMediaIds(Object node) {
        if (node instanceof Map<?, ?> map) {
            map.remove(MEDIA_KEY);
            for (var value : new ArrayList<>(map.values())) stripMediaIds(value);
        } else if (node instanceof List<?> list) {
            for (var item : list) stripMediaIds(item);
        }
    }

    /**
     * CUSTOM saves: every banner/hero {@code mediaId} must be a live IMAGE of this shop. Violations are field errors
     * (400) so the editor can point at the offending banner.
     */
    @SuppressWarnings("unchecked")
    public void assertMediaRefs(UUID shopUuid, Map<String, Object> config) {
        if (config == null) return;
        var problems = new ArrayList<FieldViolation>();
        if (config.get("banners") instanceof List<?> banners) {
            for (int index = 0; index < banners.size(); index++) {
                if (banners.get(index) instanceof Map<?, ?> row) {
                    checkRef(problems, shopUuid, "config.banners[" + index + "].mediaId", ((Map<String, Object>) row).get(MEDIA_KEY));
                }
            }
        }
        if (config.get("hero") instanceof Map<?, ?> hero) {
            checkRef(problems, shopUuid, "config.hero.mediaId", ((Map<String, Object>) hero).get(MEDIA_KEY));
        }
        if (config.get("layout") instanceof Map<?, ?> layout && layout.get("blocks") instanceof List<?> blocks) {
            for (int index = 0; index < blocks.size(); index++) {
                if (blocks.get(index) instanceof Map<?, ?> block && block.get("settings") instanceof Map<?, ?> settings) {
                    checkRef(problems, shopUuid, "config.layout.blocks[" + index + "].settings.backgroundMediaId",
                        settings.get(BLOCK_MEDIA_KEY));
                }
                // AG4: STEPS / FEATURE / GALLERY content images must be live IMAGEs of this shop too.
                if (blocks.get(index) instanceof Map<?, ?> block) {
                    for (var ref : ShopThemeBlockContent.mediaRefs((Map<String, Object>) block)) {
                        checkRef(problems, shopUuid, "config.layout.blocks[" + index + "]." + ref.getKey(), ref.getValue());
                    }
                }
            }
        }
        // AG5: hero background / posters / stickers (IMAGE or VIDEO), header + footer backgrounds, custom fonts (FONT).
        for (var ref : ShopThemeChrome.mediaRefs(config)) {
            checkRef(problems, shopUuid, ref.field(), ref.raw(), ref.kind());
        }
        if (!problems.isEmpty()) throw new ValidationException(problems);
    }

    private void checkRef(List<FieldViolation> problems, UUID shopUuid, String field, Object raw) {
        checkRef(problems, shopUuid, field, raw, ShopThemeMediaKind.IMAGE);
    }

    private void checkRef(List<FieldViolation> problems, UUID shopUuid, String field, Object raw, ShopThemeMediaKind expected) {
        if (raw == null) return;
        var text = String.valueOf(raw).trim();
        if (text.isEmpty()) return;
        var id = parse(text);
        if (id == null) {
            problems.add(ValidationMessages.violation(field, "THEME_MEDIA_INVALID"));
            return;
        }
        var row = media.findByIdAndShopIdAndDeletedAtIsNull(id, shopUuid).orElse(null);
        if (row == null) {
            problems.add(ValidationMessages.violation(field, "THEME_MEDIA_NOT_FOUND"));
        } else if (row.getKind() != expected) {
            problems.add(ValidationMessages.violation(field, "THEME_MEDIA_KIND_INVALID"));
        }
    }

    /**
     * Save path (every template): normalizes {@code hero.slides} to {@code [{slideMediaId}]} (order kept) and checks
     * the max of 5 and that each id is a live IMAGE of this shop. Absent / null / empty list = no slides.
     */
    @SuppressWarnings("unchecked")
    public void prepareHeroSlides(UUID shopUuid, Map<String, Object> config) {
        if (config == null || !(config.get("hero") instanceof Map<?, ?> rawHero)) return;
        var hero = (Map<String, Object>) rawHero;
        if (!hero.containsKey("slides")) return;
        var raw = hero.get("slides");
        if (raw == null) {
            hero.remove("slides");
            return;
        }
        var problems = new ArrayList<FieldViolation>();
        if (!(raw instanceof List<?> list)) {
            throw new ValidationException(List.of(ValidationMessages.violation("config.hero.slides", "THEME_HERO_SLIDES_INVALID")));
        }
        if (list.size() > MAX_HERO_SLIDES) {
            throw new ValidationException(List.of(ValidationMessages.violation("config.hero.slides", "THEME_HERO_SLIDES_TOO_MANY")));
        }
        var cleaned = new ArrayList<Map<String, Object>>();
        var seen = new LinkedHashSet<UUID>();
        for (int index = 0; index < list.size(); index++) {
            var item = list.get(index);
            Object id = item instanceof Map<?, ?> row ? row.get(SLIDE_KEY) : item;
            var field = "config.hero.slides[" + index + "]." + SLIDE_KEY;
            var text = id == null ? "" : String.valueOf(id).trim();
            if (text.isEmpty()) {
                problems.add(ValidationMessages.violation(field, "THEME_MEDIA_INVALID"));
                continue;
            }
            var before = problems.size();
            checkRef(problems, shopUuid, field, text);
            if (problems.size() == before && seen.add(UUID.fromString(text))) {
                var out = new java.util.LinkedHashMap<String, Object>();
                out.put(SLIDE_KEY, UUID.fromString(text).toString());
                cleaned.add(out);
            }
        }
        if (!problems.isEmpty()) throw new ValidationException(problems);
        if (cleaned.isEmpty()) hero.remove("slides");
        else hero.put("slides", cleaned);
    }

    /** Read path (every template): each slide gets its public {@code imageUrl}; deleted media is dropped silently. */
    @SuppressWarnings("unchecked")
    public void resolveHeroSlides(Shop shop, Map<String, Object> config) {
        if (config == null || !(config.get("hero") instanceof Map<?, ?> rawHero)) return;
        var hero = (Map<String, Object>) rawHero;
        if (!(hero.get("slides") instanceof List<?> list)) {
            hero.remove("slides");
            return;
        }
        var out = new ArrayList<Map<String, Object>>();
        for (var item : list) {
            if (out.size() >= MAX_HERO_SLIDES) break;
            if (!(item instanceof Map<?, ?> row)) continue;
            var id = row.get(SLIDE_KEY) == null ? null : parse(String.valueOf(row.get(SLIDE_KEY)));
            if (id == null) continue;
            var live = media.findByIdAndShopIdAndDeletedAtIsNull(id, shop.getUuid())
                .filter(m -> m.getKind() == ShopThemeMediaKind.IMAGE).isPresent();
            if (!live) continue;
            var slide = new java.util.LinkedHashMap<String, Object>();
            slide.put(SLIDE_KEY, id.toString());
            slide.put("imageUrl", publicUrl(shop.getSlug(), id));
            out.add(slide);
        }
        if (out.isEmpty()) hero.remove("slides");
        else hero.put("slides", out);
    }

    /** StoryBlock (tsf templates): {@code intro.introMediaId} must be a live IMAGE of this shop; blank = no image. */
    @SuppressWarnings("unchecked")
    public void prepareIntro(UUID shopUuid, Map<String, Object> config) {
        if (config == null || !(config.get("intro") instanceof Map<?, ?> raw)) return;
        var intro = (Map<String, Object>) raw;
        intro.remove("imageUrl");
        var id = intro.get(INTRO_KEY);
        var text = id == null ? "" : String.valueOf(id).trim();
        if (text.isEmpty()) {
            intro.remove(INTRO_KEY);
            return;
        }
        var problems = new ArrayList<FieldViolation>();
        checkRef(problems, shopUuid, "config.intro." + INTRO_KEY, text);
        if (!problems.isEmpty()) throw new ValidationException(problems);
        intro.put(INTRO_KEY, UUID.fromString(text).toString());
    }

    /** Read path: intro image gets its public {@code imageUrl}; deleted media is dropped silently. */
    @SuppressWarnings("unchecked")
    public void resolveIntro(Shop shop, Map<String, Object> config) {
        if (config == null || !(config.get("intro") instanceof Map<?, ?> raw)) return;
        var intro = (Map<String, Object>) raw;
        intro.remove("imageUrl");
        var id = intro.get(INTRO_KEY) == null ? null : parse(String.valueOf(intro.get(INTRO_KEY)));
        var live = id != null && media.findByIdAndShopIdAndDeletedAtIsNull(id, shop.getUuid())
            .filter(m -> m.getKind() == ShopThemeMediaKind.IMAGE).isPresent();
        if (!live) {
            intro.remove(INTRO_KEY);
            return;
        }
        intro.put(INTRO_KEY, id.toString());
        intro.put("imageUrl", publicUrl(shop.getSlug(), id));
    }

    public static String publicUrl(String slug, UUID mediaId) {
        return "/api/shops/" + slug + "/theme-media/" + mediaId;
    }

    private MerchantDtos.ThemeMediaResponse toResponse(Shop shop, ShopThemeMedia row) {
        return new MerchantDtos.ThemeMediaResponse(
            row.getId(), row.getKind().name(), row.getOriginalFilename(), row.getMimeType(), row.getSizeBytes(),
            row.getWidth(), row.getHeight(), publicUrl(shop.getSlug(), row.getId()), row.getCreatedDateTime()
        );
    }

    private void enforceQuota(UUID shopUuid, ShopThemeMediaKind kind, long incomingBytes) {
        var count = media.countByShopIdAndKindAndDeletedAtIsNull(shopUuid, kind);
        var max = switch (kind) {
            case IMAGE -> limits.maxImages();
            case VIDEO -> limits.maxVideos();
            case FONT -> limits.maxFonts();
        };
        if (count >= max || media.sumActiveSizeBytes(shopUuid) + incomingBytes > limits.maxTotalBytes()) {
            throw new AuthException(HttpStatus.CONFLICT, "THEME_MEDIA_QUOTA_EXCEEDED", ValidationMessages.text("THEME_MEDIA_QUOTA_EXCEEDED"));
        }
    }

    private long maxBytes(ShopThemeMediaKind kind) {
        return switch (kind) {
            case IMAGE -> limits.maxImageBytes();
            case VIDEO -> limits.maxVideoBytes();
            case FONT -> limits.maxFontBytes();
        };
    }

    private boolean isCustom(Shop shop) {
        return themes.findByShopId(shop.getUuid())
            .map(theme -> ShopThemeTemplate.CUSTOM.name().equals(theme.getTemplateKey()))
            .orElse(false);
    }

    private static boolean isMp4(byte[] bytes) {
        return bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p';
    }

    private static boolean isWoff2(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'w' && bytes[1] == 'O' && bytes[2] == 'F' && bytes[3] == '2';
    }

    /** Header-only read (no full decode) so a decompression bomb cannot exhaust memory. */
    private static int[] imageIoDimensions(byte[] bytes) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return null;
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Exception exception) {
            return null;
        }
    }

    /** Minimal RIFF/WEBP header parse (VP8 / VP8L / VP8X); null when it cannot be read (magic bytes still accepted). */
    private static int[] webpDimensions(byte[] b) {
        try {
            if (b.length < 30) return null;
            var chunk = new String(b, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
            switch (chunk) {
                case "VP8X" -> {
                    int w = 1 + ((b[24] & 255) | ((b[25] & 255) << 8) | ((b[26] & 255) << 16));
                    int h = 1 + ((b[27] & 255) | ((b[28] & 255) << 8) | ((b[29] & 255) << 16));
                    return new int[]{w, h};
                }
                case "VP8 " -> {
                    if ((b[23] & 255) != 0x9d || (b[24] & 255) != 0x01 || (b[25] & 255) != 0x2a) return null;
                    int w = ((b[26] & 255) | ((b[27] & 255) << 8)) & 0x3fff;
                    int h = ((b[28] & 255) | ((b[29] & 255) << 8)) & 0x3fff;
                    return new int[]{w, h};
                }
                case "VP8L" -> {
                    if ((b[20] & 255) != 0x2f) return null;
                    int bits = (b[21] & 255) | ((b[22] & 255) << 8) | ((b[23] & 255) << 16) | ((b[24] & 255) << 24);
                    return new int[]{1 + (bits & 0x3fff), 1 + ((bits >>> 14) & 0x3fff)};
                }
                default -> {
                    return null;
                }
            }
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Display-only label; storage always uses a generated UUID name. */
    private static String safeFilename(String original) {
        if (original == null) return "file";
        var name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) return "file";
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static UUID parse(String text) {
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private AuthException invalid() {
        return new AuthException(HttpStatus.BAD_REQUEST, "THEME_MEDIA_INVALID", ValidationMessages.text("THEME_MEDIA_INVALID"));
    }

    private AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "THEME_MEDIA_NOT_FOUND", ValidationMessages.text("THEME_MEDIA_NOT_FOUND"));
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
