package com.thaishopfun.deal;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.translation.ContentLocale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PublicEndpoint("public marketplace deals: CATALOG_READ (GUEST); published products / public coupons only")
@RequestMapping("/api/deals")
public class PublicDealController {
    private final DealService deals;

    public PublicDealController(DealService deals) {
        this.deals = deals;
    }

    @GetMapping
    public DealDtos.PageResponse<?> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String tab,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String priceBand,
        @RequestParam(required = false) Integer minDiscount,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String lang,
        @RequestParam(required = false) String locale,
        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        return deals.publicDeals(
            tab, categoryId, priceBand, minDiscount, page, size,
            ContentLocale.resolve(locale != null ? locale : lang, acceptLanguage),
            RequestUser.id(jwt));
    }
}
