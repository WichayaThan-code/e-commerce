package com.thaishopfun.catalog.configurator;

import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.translation.ContentLocale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** V42 storefront configurator: read the steps and get a server-side quote (no cart write here). */
@RestController
@PublicEndpoint
@RequestMapping("/api/shops/{slug}/products/{productId}/configurator")
public class PublicConfiguratorController {
    private final ProductConfiguratorService configurators;

    public PublicConfiguratorController(ProductConfiguratorService configurators) { this.configurators = configurators; }

    @GetMapping
    public ConfiguratorDtos.PublicConfiguratorResponse get(
        @PathVariable String slug, @PathVariable UUID productId,
        @RequestParam(required = false) String lang, @RequestParam(required = false) String locale,
        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        return configurators.publicGet(slug, productId, ContentLocale.resolve(locale != null ? locale : lang, acceptLanguage));
    }

    @PostMapping("/quote")
    public ConfiguratorDtos.QuoteResponse quote(
        @PathVariable String slug, @PathVariable UUID productId,
        @RequestBody(required = false) ConfiguratorDtos.QuoteRequest request,
        @RequestParam(required = false) String lang, @RequestParam(required = false) String locale,
        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        return configurators.quote(slug, productId, request, ContentLocale.resolve(locale != null ? locale : lang, acceptLanguage));
    }
}
