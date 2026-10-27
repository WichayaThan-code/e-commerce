package com.thaishopfun.catalog;

import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.translation.ContentLocale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Marketplace best sellers for the storefront /best-sellers page. Read-only aggregate of sold quantities; the response
 * carries product + shop display fields only (never buyer, order or customer data).
 */
@RestController
@PublicEndpoint // storefront catalog: anonymous browsing (unpublished goods / unapproved shops are filtered by the service)
@RequestMapping("/api/products/best-sellers")
public class PublicBestSellerController {
    private final ProductService products;

    public PublicBestSellerController(ProductService products) { this.products = products; }

    @GetMapping
    public CatalogDtos.PageResponse<CatalogDtos.PublicBestSellerResponse> list(
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String categorySlug,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String shopSlug,
        @RequestParam(required = false) String period,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String lang,
        @RequestParam(required = false) String locale,
        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        return products.publicBestSellers(categoryId, categorySlug, shopSlug, period, minPrice, maxPrice, sort, page, size,
            ContentLocale.resolve(locale != null ? locale : lang, acceptLanguage));
    }
}
