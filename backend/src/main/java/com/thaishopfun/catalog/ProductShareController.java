package com.thaishopfun.catalog;

import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.common.notification.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Share landing page for Facebook / LINE / X link previews: tiny HTML with Open Graph tags (all values escaped) that
 * sends people on to the SPA product page, plus the Shopee-style 1200x630 preview card it points og:image at.
 * Only published products of public shops (same rules as the catalog API).
 */
@RestController
@PublicEndpoint("share preview: OG meta + generated preview card of a published product, redirects to the storefront")
public class ProductShareController {
    private static final Logger log = LoggerFactory.getLogger(ProductShareController.class);
    private static final int MAX_DESCRIPTION = 200;
    static final String OG_IMAGE_FILE = "og-image.png";
    private final ProductService products;
    private final NotificationProperties notification;
    private final ProductOgImageService ogImages;

    public ProductShareController(ProductService products, NotificationProperties notification, ProductOgImageService ogImages) {
        this.products = products;
        this.notification = notification;
        this.ogImages = ogImages;
    }

    @GetMapping(value = "/share/p/{shopSlug}/{productSlug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> share(@PathVariable String shopSlug, @PathVariable String productSlug) {
        var encodedShop = UriUtils.encodePathSegment(shopSlug, StandardCharsets.UTF_8);
        var encodedProduct = UriUtils.encodePathSegment(productSlug, StandardCharsets.UTF_8);
        var sharePath = "/share/p/" + encodedShop + "/" + encodedProduct;
        var shareUrl = frontend() + sharePath;
        var target = frontend() + "/shop/" + encodedShop + "/product/" + encodedProduct;
        String title = "ThaiShopFun";
        String description = "";
        String card = null;
        String rawImage = null;
        int status = 200;
        try {
            var product = products.publicGet(shopSlug, productSlug, "th");
            title = firstNonBlank(product.nameTh(), product.nameEn(), title);
            description = shorten(firstNonBlank(product.descriptionTh(), product.descriptionEn(), ""));
            card = absolute(sharePath + "/" + OG_IMAGE_FILE);
            if (product.images() != null && !product.images().isEmpty()) {
                var picked = product.images().stream().filter(CatalogDtos.ImageResponse::primary).findFirst().orElse(product.images().get(0));
                rawImage = absolute(picked.url());
            }
        } catch (RuntimeException notFound) {
            status = 404;
            target = frontend() + "/shop/" + encodedShop;
            shareUrl = target;
        }
        var html = new StringBuilder(1536)
            .append("<!doctype html><html lang=\"th\"><head><meta charset=\"utf-8\">")
            .append("<title>").append(esc(title)).append("</title>")
            .append("<meta name=\"description\" content=\"").append(esc(description)).append("\">")
            .append("<meta property=\"og:type\" content=\"product\">")
            .append("<meta property=\"og:site_name\" content=\"ThaiShopFun\">")
            .append("<meta property=\"og:locale\" content=\"th_TH\">")
            .append("<meta property=\"og:title\" content=\"").append(esc(title)).append("\">")
            .append("<meta property=\"og:description\" content=\"").append(esc(description)).append("\">")
            .append("<meta property=\"og:url\" content=\"").append(esc(shareUrl)).append("\">");
        // 1st og:image = generated preview card (photo + title + price + rating + logo); 2nd = raw product photo as fallback
        var primary = card != null ? card : rawImage;
        if (card != null) {
            appendImage(html, card, title, true);
        }
        if (rawImage != null) {
            appendImage(html, rawImage, title, false);
        }
        if (primary != null) {
            html.append("<meta name=\"twitter:card\" content=\"summary_large_image\">")
                .append("<meta name=\"twitter:title\" content=\"").append(esc(title)).append("\">")
                .append("<meta name=\"twitter:description\" content=\"").append(esc(description)).append("\">")
                .append("<meta name=\"twitter:image\" content=\"").append(esc(primary)).append("\">")
                .append("<meta name=\"twitter:image:alt\" content=\"").append(esc(title)).append("\">");
        }
        html.append("<link rel=\"canonical\" href=\"").append(esc(shareUrl)).append("\">")
            .append("</head><body><script>window.location.replace(").append(jsString(target)).append(");</script>")
            .append("<noscript><meta http-equiv=\"refresh\" content=\"0;url=").append(esc(target)).append("\"></noscript>")
            .append("<p><a href=\"").append(esc(target)).append("\">").append(esc(title)).append("</a></p></body></html>");
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
            .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
            .body(html.toString());
    }

    /** Shopee-style 1200x630 PNG preview card (GET and HEAD; HEAD is allowed for public GET endpoints). */
    @GetMapping(value = "/share/p/{shopSlug}/{productSlug}/" + OG_IMAGE_FILE, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> ogImage(@PathVariable String shopSlug, @PathVariable String productSlug) {
        try {
            return ogImages.render(shopSlug, productSlug)
                .map(png -> ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(15)).cachePublic())
                    .contentType(MediaType.IMAGE_PNG)
                    .contentLength(png.length)
                    .body(png))
                .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            log.warn("OG card render failed for {}/{}", shopSlug, productSlug, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private static void appendImage(StringBuilder html, String url, String title, boolean card) {
        html.append("<meta property=\"og:image\" content=\"").append(esc(url)).append("\">");
        if (url.startsWith("https://")) {
            html.append("<meta property=\"og:image:secure_url\" content=\"").append(esc(url)).append("\">");
        }
        if (card) {
            html.append("<meta property=\"og:image:type\" content=\"image/png\">")
                .append("<meta property=\"og:image:width\" content=\"").append(ProductOgImageRenderer.WIDTH).append("\">")
                .append("<meta property=\"og:image:height\" content=\"").append(ProductOgImageRenderer.HEIGHT).append("\">");
        }
        html.append("<meta property=\"og:image:alt\" content=\"").append(esc(title)).append("\">");
    }

    private String frontend() {
        var value = notification == null ? null : notification.frontendUrl();
        if (value == null || value.isBlank()) value = "http://localhost:5173";
        return value.replaceAll("/+$", "");
    }

    /** HTTPS frontend (Vercel proxies /api and /share to Railway) when configured, else this backend's own origin. */
    private String absolute(String url) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        var front = frontend();
        var base = front.startsWith("https://")
            ? front
            : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString().replaceAll("/+$", "");
        return base + (url.startsWith("/") ? url : "/" + url);
    }

    private static String shorten(String text) {
        var t = text.replaceAll("\\s+", " ").trim();
        return t.length() > MAX_DESCRIPTION ? t.substring(0, MAX_DESCRIPTION - 1) + "\u2026" : t;
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return fallback;
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private static String jsString(String value) {
        if (value == null) return "\"\"";
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("<", "\\u003c")
            .replace(">", "\\u003e")
            .replace("&", "\\u0026")
            + "\"";
    }
}
