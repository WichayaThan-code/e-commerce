package com.thaishopfun.catalog.configurator;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.catalog.Product;
import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.catalog.ProductStatus;
import com.thaishopfun.catalog.collection.CatalogShopAccess;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.common.ValidationMessages;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.theme.ShopThemeMediaKind;
import com.thaishopfun.merchant.theme.ShopThemeMediaRepository;
import com.thaishopfun.merchant.theme.ShopThemeMediaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V42 product configurator (ตัวออกแบบสินค้าเอง). Whole feature is behind {@link ConfiguratorProperties#enabled()}
 * (404 FEATURE_DISABLED while off) and only for CUSTOM-template shops. The quote is the server-authoritative price;
 * cart / order integration is NOT here (Supitcha's part, see AB2 handoff note).
 */
@Service
public class ProductConfiguratorService {
    private final ProductConfiguratorRepository configurators;
    private final ProductRepository products;
    private final ShopThemeMediaRepository media;
    private final CatalogShopAccess access;
    private final ConfiguratorProperties properties;

    public ProductConfiguratorService(ProductConfiguratorRepository configurators, ProductRepository products, ShopThemeMediaRepository media,
                                      CatalogShopAccess access, ConfiguratorProperties properties) {
        this.configurators = configurators;
        this.products = products;
        this.media = media;
        this.access = access;
        this.properties = properties;
    }

    public boolean enabled() { return properties.enabled(); }

    @Transactional(readOnly = true)
    public ConfiguratorDtos.MerchantConfiguratorResponse merchantGet(UUID userId, Long shopId, UUID productId) {
        requireFlag();
        var shop = access.merchantShop(userId, shopId);
        var product = merchantProduct(shopId, productId);
        requireCustom(shop);
        return configurators.findById(product.getId())
            .map(ProductConfiguratorService::toMerchant)
            .orElseGet(() -> new ConfiguratorDtos.MerchantConfiguratorResponse(product.getId(), false, Map.of("steps", List.of()), 0, null));
    }

    @Transactional
    public ConfiguratorDtos.MerchantConfiguratorResponse merchantSave(UUID userId, Long shopId, UUID productId, ConfiguratorDtos.ConfiguratorWriteRequest request) {
        requireFlag();
        var shop = access.merchantShop(userId, shopId);
        var product = merchantProduct(shopId, productId);
        requireCustom(shop);
        var body = request == null ? new ConfiguratorDtos.ConfiguratorWriteRequest(false, null, 0) : request;
        var row = configurators.findById(product.getId()).orElseGet(() -> new ProductConfigurator(product.getId()));
        int expected = body.version() == null ? 0 : body.version();
        if (expected != row.getVersion()) {
            throw new AuthException(HttpStatus.CONFLICT, "CONFIGURATOR_VERSION_CONFLICT", "ตัวออกแบบสินค้าถูกแก้ไขจากที่อื่นแล้ว กรุณาโหลดใหม่ก่อนบันทึก");
        }
        var problems = new ArrayList<FieldViolation>();
        var schema = ConfiguratorSchema.normalize(body.schema(), product.getBasePrice(), id -> isShopImage(shop, id), problems);
        boolean enabled = Boolean.TRUE.equals(body.enabled());
        if (enabled && ((List<?>) schema.get("steps")).isEmpty() && problems.isEmpty()) {
            problems.add(ValidationMessages.violation("schema.steps", "CONFIGURATOR_SCHEMA_INVALID"));
        }
        if (!problems.isEmpty()) throw new ValidationException(problems);
        row.apply(enabled, schema, userId);
        return toMerchant(configurators.save(row));
    }

    @Transactional(readOnly = true)
    public ConfiguratorDtos.PublicConfiguratorResponse publicGet(String slug, UUID productId, String locale) {
        var context = publicContext(slug, productId);
        var steps = new ArrayList<Map<String, Object>>();
        for (var raw : (List<?>) context.row.getSchema().getOrDefault("steps", List.of())) {
            if (!(raw instanceof Map<?, ?> step)) continue;
            var out = new LinkedHashMap<String, Object>();
            step.forEach((key, value) -> {
                if (!"options".equals(key)) out.put(String.valueOf(key), value);
            });
            if (step.get("options") instanceof List<?> options) {
                var list = new ArrayList<Map<String, Object>>();
                for (var item : options) {
                    if (!(item instanceof Map<?, ?> option)) continue;
                    var view = new LinkedHashMap<String, Object>();
                    view.put("id", option.get("id"));
                    view.put("label", option.get("label"));
                    view.put("priceDelta", option.get("priceDelta"));
                    var mediaId = option.get("mediaId") == null ? null : String.valueOf(option.get("mediaId"));
                    view.put("mediaUrl", mediaId == null ? null : mediaUrl(context.shop, mediaId));
                    view.put("available", ConfiguratorSchema.available(option)); // exact stock is not exposed
                    list.add(view);
                }
                out.put("options", list);
            }
            steps.add(out);
        }
        return new ConfiguratorDtos.PublicConfiguratorResponse(context.product.getId(), context.row.getVersion(),
            context.product.getBasePrice(), context.product.getCurrency(), steps);
    }

    @Transactional(readOnly = true)
    public ConfiguratorDtos.QuoteResponse quote(String slug, UUID productId, ConfiguratorDtos.QuoteRequest request, String locale) {
        var context = publicContext(slug, productId);
        var body = request == null ? new ConfiguratorDtos.QuoteRequest(Map.of(), null) : request;
        if (body.version() != null && body.version() != context.row.getVersion()) {
            throw new AuthException(HttpStatus.CONFLICT, "CONFIGURATOR_VERSION_STALE", ValidationMessages.text("CONFIGURATOR_VERSION_STALE"));
        }
        var problems = new ArrayList<FieldViolation>();
        var base = context.product.getBasePrice() == null ? BigDecimal.ZERO : context.product.getBasePrice();
        var quote = ConfiguratorSchema.quote(context.product.getId(), context.row.getVersion(), context.row.getSchema(), base,
            body.selections(), locale, problems);
        if (!problems.isEmpty()) throw new ValidationException(problems);
        return new ConfiguratorDtos.QuoteResponse(quote.complete(), quote.missing(), base, quote.lines(), quote.total(),
            context.product.getCurrency(), context.row.getVersion(), quote.configHash(), quote.selections());
    }

    /** V43 cart / checkout re-quote result: snapshot to store on the line, its hash and the configured price delta. */
    public record CartQuote(Map<String, Object> snapshot, String configHash, BigDecimal configDelta) {}

    /**
     * V43: server re-quote for a cart line (add-to-cart, checkout validation, accept prices). Same rules as the public quote
     * (flag on, CUSTOM shop, PUBLISHED product, enabled configurator); additionally the selection must be complete.
     * Labels are stored in Thai (+ English when present) so the cart / order pages never need the live schema.
     * Not @Transactional on purpose: runs inside the caller's cart transaction, and a rejected quote must not mark it rollback-only.
     */
    public CartQuote cartQuote(Product product, Map<String, Object> selections, Integer version) {
        var shop = product.getShop();
        var quote = quote(shop.getSlug(), product.getId(), new ConfiguratorDtos.QuoteRequest(selections, version), "th");
        if (!quote.complete()) {
            var problems = new ArrayList<FieldViolation>();
            for (var stepId : quote.missing()) problems.add(ValidationMessages.violation("configuration.selections." + stepId, "CONFIGURATOR_SELECTION_REQUIRED"));
            throw new ValidationException(problems);
        }
        var row = configurators.findById(product.getId()).orElseThrow(ProductConfiguratorService::notFound);
        var stepLabels = new LinkedHashMap<String, Object>();
        var optionLabels = new LinkedHashMap<String, Object>();
        for (var raw : (List<?>) row.getSchema().getOrDefault("steps", List.of())) {
            if (!(raw instanceof Map<?, ?> step)) continue;
            stepLabels.put(String.valueOf(step.get("id")), step.get("label"));
            if (step.get("options") instanceof List<?> options) {
                for (var item : options) {
                    if (item instanceof Map<?, ?> option) optionLabels.put(step.get("id") + "/" + option.get("id"), option.get("label"));
                }
            }
        }
        var base = quote.basePrice() == null ? BigDecimal.ZERO : quote.basePrice();
        var delta = quote.total().subtract(base).setScale(2, java.math.RoundingMode.HALF_UP);
        var lines = new ArrayList<Map<String, Object>>();
        for (var line : quote.lines()) {
            var view = new LinkedHashMap<String, Object>();
            view.put("stepId", line.stepId());
            view.put("stepLabel", stepLabels.get(line.stepId()));
            view.put("optionId", line.optionId());
            view.put("label", line.optionId() == null ? Map.of("th", line.label()) : optionLabels.get(line.stepId() + "/" + line.optionId()));
            if (line.optionId() == null) view.put("text", line.label());
            view.put("priceDelta", line.priceDelta().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            lines.add(view);
        }
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("version", quote.version());
        snapshot.put("selections", quote.selections());
        snapshot.put("lines", lines);
        snapshot.put("configDelta", delta.toPlainString());
        snapshot.put("currency", quote.currency());
        snapshot.put("configHash", quote.configHash());
        return new CartQuote(snapshot, quote.configHash(), delta);
    }

    private record PublicContext(Shop shop, Product product, ProductConfigurator row) {}

    private PublicContext publicContext(String slug, UUID productId) {
        requireFlag();
        var shop = access.publicShop(slug);
        if (!access.isCustom(shop)) throw notFound();
        var product = products.findByIdAndShop_ShopIdAndDeletedAtIsNull(productId, shop.getId())
            .filter(found -> found.getStatus() == ProductStatus.PUBLISHED)
            .orElseThrow(ProductConfiguratorService::notFound);
        var row = configurators.findById(product.getId()).filter(ProductConfigurator::isEnabled).orElseThrow(ProductConfiguratorService::notFound);
        return new PublicContext(shop, product, row);
    }

    private Product merchantProduct(Long shopId, UUID productId) {
        return products.findByIdAndShop_ShopIdAndDeletedAtIsNull(productId, shopId)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "ไม่พบสินค้า"));
    }

    private boolean isShopImage(Shop shop, UUID mediaId) {
        return media.findByIdAndShopIdAndDeletedAtIsNull(mediaId, shop.getUuid())
            .map(found -> found.getKind() == ShopThemeMediaKind.IMAGE)
            .orElse(false);
    }

    private String mediaUrl(Shop shop, String mediaId) {
        try {
            var id = UUID.fromString(mediaId);
            return isShopImage(shop, id) ? ShopThemeMediaService.publicUrl(shop.getSlug(), id) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void requireFlag() {
        if (!properties.enabled()) throw new AuthException(HttpStatus.NOT_FOUND, "FEATURE_DISABLED", "ฟีเจอร์นี้ยังไม่เปิดใช้งาน");
    }

    private void requireCustom(Shop shop) {
        if (!access.isCustom(shop)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "CONFIGURATOR_CUSTOM_ONLY", "ตัวออกแบบสินค้าใช้ได้เฉพาะร้านที่ใช้ธีมแบบกำหนดเอง (CUSTOM)");
        }
    }

    private static ConfiguratorDtos.MerchantConfiguratorResponse toMerchant(ProductConfigurator row) {
        return new ConfiguratorDtos.MerchantConfiguratorResponse(row.getProductId(), row.isEnabled(), row.getSchema(), row.getVersion(), row.getModifiedDateTime());
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "CONFIGURATOR_NOT_FOUND", "ไม่พบตัวออกแบบสินค้านี้");
    }
}
