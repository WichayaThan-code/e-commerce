package com.thaishopfun.catalog;

import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.review.ProductReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the Shopee-style share card (PNG) for a published product: reads the product, its primary image straight
 * from storage (no HTTP round trip), the review aggregate and shop name, then hands them to the renderer.
 * Rendered cards are kept in memory for a few minutes because crawlers (Facebook, LINE, X) re-fetch them in bursts.
 */
@Service
public class ProductOgImageService {
    private static final Logger log = LoggerFactory.getLogger(ProductOgImageService.class);
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final int CACHE_MAX = 200;
    private static final long MAX_SOURCE_BYTES = 20L * 1024 * 1024;

    private final ProductService products;
    private final ProductReviewService reviews;
    private final ShopRepository shops;
    private final ProductOgImageRenderer renderer;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(byte[] png, long at) {}

    public ProductOgImageService(ProductService products, ProductReviewService reviews, ShopRepository shops,
                                 ProductOgImageRenderer renderer) {
        this.products = products;
        this.reviews = reviews;
        this.shops = shops;
        this.renderer = renderer;
    }

    /** Empty when the product / shop is not publicly visible (same rules as the storefront catalog). */
    public Optional<byte[]> render(String shopSlug, String productSlug) {
        var key = shopSlug + "/" + productSlug;
        long now = System.currentTimeMillis();
        var hit = cache.get(key);
        if (hit != null && now - hit.at() < CACHE_TTL_MS) return Optional.of(hit.png());

        CatalogDtos.PublicProductResponse product;
        try {
            product = products.publicGet(shopSlug, productSlug, "th");
        } catch (RuntimeException notFound) {
            return Optional.empty();
        }
        var png = renderer.render(card(shopSlug, product));
        if (cache.size() >= CACHE_MAX) cache.entrySet().removeIf(e -> now - e.getValue().at() >= CACHE_TTL_MS);
        if (cache.size() >= CACHE_MAX) cache.clear();
        cache.put(key, new Cached(png, now));
        return Optional.of(png);
    }

    ProductOgImageRenderer.Card card(String shopSlug, CatalogDtos.PublicProductResponse product) {
        var title = firstNonBlank(product.nameTh(), product.nameEn(), "ThaiShopFun");
        var prices = priceRange(product);
        var compareAt = product.compareAtPrice();
        if (compareAt == null && product.skus() != null) {
            compareAt = product.skus().stream().map(CatalogDtos.PublicSkuResponse::compareAtPrice)
                .filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
        }
        BigDecimal ratingAvg = null;
        long reviewCount = 0;
        try {
            var aggregate = reviews.aggregates(List.of(product.id())).get(product.id());
            if (aggregate != null) {
                ratingAvg = aggregate.ratingAvg();
                reviewCount = aggregate.reviewCount();
            }
        } catch (RuntimeException e) {
            log.debug("OG card: review aggregate unavailable for {}/{}", shopSlug, product.slug(), e);
        }
        String shopName = null;
        try {
            shopName = shops.findBySlug(shopSlug).map(Shop::getName).orElse(null);
        } catch (RuntimeException e) {
            log.debug("OG card: shop name unavailable for {}", shopSlug, e);
        }
        return new ProductOgImageRenderer.Card(title, shopName, prices[0], prices[1], compareAt, ratingAvg, reviewCount,
            product.soldCount(), primaryImage(shopSlug, product));
    }

    private static BigDecimal[] priceRange(CatalogDtos.PublicProductResponse product) {
        BigDecimal min = null;
        BigDecimal max = null;
        if (product.skus() != null) {
            for (var sku : product.skus()) {
                if (sku.price() == null) continue;
                if (min == null || sku.price().compareTo(min) < 0) min = sku.price();
                if (max == null || sku.price().compareTo(max) > 0) max = sku.price();
            }
        }
        if (min == null) {
            min = product.basePrice();
            max = product.basePrice();
        }
        return new BigDecimal[] {min, max};
    }

    private BufferedImage primaryImage(String shopSlug, CatalogDtos.PublicProductResponse product) {
        if (product.images() == null || product.images().isEmpty()) return null;
        var picked = product.images().stream().filter(CatalogDtos.ImageResponse::primary).findFirst()
            .orElse(product.images().get(0));
        try {
            var file = products.publicImage(shopSlug, product.id(), picked.id());
            if (Files.size(file) > MAX_SOURCE_BYTES) return null;
            return ImageIO.read(file.toFile()); // jpg / png built in, webp via TwelveMonkeys
        } catch (Exception e) {
            log.warn("OG card: cannot read primary image of {}/{}: {}", shopSlug, product.slug(), e.toString());
            return null;
        }
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return fallback;
    }
}
