package com.thaishopfun.catalog;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.translation.ContentLocale;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Public recommendations + view tracking. A bearer token is optional (used for personalization only). */
@RestController
@PublicEndpoint
@RequestMapping("/api/products")
public class PublicRecommendationController {
    public static final String SESSION_HEADER = "X-View-Session";
    private final RecommendationService recommendations;
    private final ProductViewService views;

    public PublicRecommendationController(RecommendationService recommendations, ProductViewService views) {
        this.recommendations = recommendations;
        this.views = views;
    }

    public record ViewRequest(UUID productId) {}

    @GetMapping("/recommendations")
    public CatalogDtos.PageResponse<CatalogDtos.PublicBestSellerResponse> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) BigDecimal minRating,
        @RequestParam(required = false) String brands,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String lang,
        @RequestParam(required = false) String locale,
        @RequestHeader(value = SESSION_HEADER, required = false) String session,
        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        return recommendations.recommend(categoryId, minPrice, maxPrice, minRating, brands, sort, page, size,
            RequestUser.id(jwt), session, ContentLocale.resolve(locale != null ? locale : lang, acceptLanguage));
    }

    @PostMapping("/views")
    public ResponseEntity<Void> record(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) ViewRequest body,
                                       @RequestHeader(value = SESSION_HEADER, required = false) String session, HttpServletRequest request) {
        views.record(body == null ? null : body.productId(), RequestUser.id(jwt), session, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/recently-viewed")
    public List<CatalogDtos.PublicBestSellerResponse> recentlyViewed(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String lang,
        @RequestParam(required = false) String locale,
        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        if (jwt == null) throw new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sign in to see recently viewed products");
        int limit = size == null ? 12 : Math.max(1, Math.min(20, size));
        return views.recentlyViewed(RequestUser.id(jwt), ContentLocale.resolve(locale != null ? locale : lang, acceptLanguage), limit);
    }
}
