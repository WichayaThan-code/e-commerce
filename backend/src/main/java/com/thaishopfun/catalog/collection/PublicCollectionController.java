package com.thaishopfun.catalog.collection;

import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.translation.ContentLocale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** V41 storefront collections: published collections of APPROVED shops only; products PUBLISHED and not deleted. */
@RestController
@PublicEndpoint
@RequestMapping("/api/shops/{slug}/collections")
public class PublicCollectionController {
    private final ShopCollectionService collections;

    public PublicCollectionController(ShopCollectionService collections) { this.collections = collections; }

    @GetMapping
    public List<CollectionDtos.PublicCollectionResponse> list(
        @PathVariable String slug,
        @RequestParam(required = false) String lang,
        @RequestParam(required = false) String locale,
        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        return collections.publicList(slug, ContentLocale.resolve(locale != null ? locale : lang, acceptLanguage));
    }

    @GetMapping("/{collectionSlug}")
    public CollectionDtos.PublicCollectionDetail get(
        @PathVariable String slug,
        @PathVariable String collectionSlug,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String lang,
        @RequestParam(required = false) String locale,
        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        return collections.publicGet(slug, collectionSlug, page, size, ContentLocale.resolve(locale != null ? locale : lang, acceptLanguage));
    }
}
