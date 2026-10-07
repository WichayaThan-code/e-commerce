package com.thaishopfun.catalog;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.thaishopfun.translation.ContentLocale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.thaishopfun.auth.authz.PublicEndpoint;

import java.time.Duration;
import java.util.UUID;

@RestController
@PublicEndpoint("public storefront products: CATALOG_READ (GUEST)")
@RequestMapping("/api/shops/{slug}/products")
public class PublicProductController {
    private final ProductService products;

    public PublicProductController(ProductService products) { this.products = products; }

    @GetMapping
    public CatalogDtos.PageResponse<CatalogDtos.PublicProductResponse> list(
        @PathVariable String slug,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String priceBands,
        @RequestParam(required = false) String lang,
        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        return products.publicList(slug, q, categoryId, sort, page, size, priceBands);
    }

    @GetMapping("/{productSlug}")
    public CatalogDtos.PublicProductResponse get(
        @PathVariable String slug,
        @PathVariable String productSlug,
        @RequestParam(required = false) String lang,
        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        ContentLocale.resolve(lang, acceptLanguage);
        return products.publicGet(slug, productSlug);
    }

    @GetMapping("/{productId}/images/{imageId}")
    public ResponseEntity<Resource> image(@PathVariable String slug, @PathVariable UUID productId, @PathVariable UUID imageId) {
        var file = products.publicImage(slug, productId, imageId);
        var name = file.getFileName().toString().toLowerCase();
        var type = name.endsWith(".png") ? MediaType.IMAGE_PNG
            : name.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
            : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
            .contentType(type)
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
            .body(new FileSystemResource(file));
    }
}
