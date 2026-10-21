package com.thaishopfun.search;

import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.translation.ContentLocale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PublicEndpoint("public marketplace search: CATALOG_READ (GUEST); APPROVED shops and PUBLISHED products only")
@RequestMapping("/api/search")
public class PublicSearchController {
    private final SearchService search;

    public PublicSearchController(SearchService search) { this.search = search; }

    @GetMapping
    public SearchDtos.SearchResponse search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String brand,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String lang,
        @RequestParam(required = false) String locale,
        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        return search.search(q, brand, sort, page, size, ContentLocale.resolve(locale != null ? locale : lang, acceptLanguage));
    }
}
