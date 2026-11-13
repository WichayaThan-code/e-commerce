package com.thaishopfun.catalog;

import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.common.notification.NotificationProperties;
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
 * Share landing page for Facebook / LINE link previews: tiny HTML with Open Graph tags (all values escaped) that
 * sends people on to the SPA product page. Only published products of public shops (same rules as the catalog API).
 */
@RestController
@PublicEndpoint("share preview: OG meta of a published product, redirects to the storefront")
public class ProductShareController {
    private static final int MAX_DESCRIPTION = 200;
    private final ProductService products;
    private final NotificationProperties notification;

    public ProductShareController(ProductService products, NotificationProperties notification) {
        this.products = products;
        this.notification = notification;
    }

    @GetMapping(value = "/share/p/{shopSlug}/{productSlug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> share(@PathVariable String shopSlug, @PathVariable String productSlug) {
        var target = frontend() + "/shop/" + UriUtils.encodePathSegment(shopSlug, StandardCharsets.UTF_8)
            + "/product/" + UriUtils.encodePathSegment(productSlug, StandardCharsets.UTF_8);
        String title = "ThaiShopFun";
        String description = "";
        String image = null;
        int status = 200;
        try {
            var product = products.publicGet(shopSlug, productSlug, "th");
            title = firstNonBlank(product.nameTh(), product.nameEn(), title);
            description = shorten(firstNonBlank(
                ProductDescriptionHtml.plainText(product.descriptionTh()),
                ProductDescriptionHtml.plainText(product.descriptionEn()),
                ""));
            if (product.images() != null && !product.images().isEmpty()) {
                var picked = product.images().stream().filter(CatalogDtos.ImageResponse::primary).findFirst().orElse(product.images().get(0));
                image = absolute(picked.url());
            }
        } catch (RuntimeException notFound) {
            status = 404;
            target = frontend() + "/shop/" + UriUtils.encodePathSegment(shopSlug, StandardCharsets.UTF_8);
        }
        var html = new StringBuilder(1024)
            .append("<!doctype html><html lang=\"th\"><head><meta charset=\"utf-8\">")
            .append("<title>").append(esc(title)).append("</title>")
            .append("<meta name=\"description\" content=\"").append(esc(description)).append("\">")
            .append("<meta property=\"og:type\" content=\"product\">")
            .append("<meta property=\"og:site_name\" content=\"ThaiShopFun\">")
            .append("<meta property=\"og:title\" content=\"").append(esc(title)).append("\">")
            .append("<meta property=\"og:description\" content=\"").append(esc(description)).append("\">")
            .append("<meta property=\"og:url\" content=\"").append(esc(target)).append("\">");
        if (image != null) html.append("<meta property=\"og:image\" content=\"").append(esc(image)).append("\">")
            .append("<meta name=\"twitter:card\" content=\"summary_large_image\">");
        html.append("<meta http-equiv=\"refresh\" content=\"0;url=").append(esc(target)).append("\">")
            .append("<link rel=\"canonical\" href=\"").append(esc(target)).append("\">")
            .append("</head><body><p><a href=\"").append(esc(target)).append("\">").append(esc(title)).append("</a></p></body></html>");
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
            .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
            .body(html.toString());
    }

    private String frontend() {
        var value = notification == null ? null : notification.frontendUrl();
        if (value == null || value.isBlank()) value = "http://localhost:5173";
        return value.replaceAll("/+$", "");
    }

    private static String absolute(String url) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        var base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString().replaceAll("/+$", "");
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
}