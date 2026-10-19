package com.thaishopfun.promotion;

import com.thaishopfun.auth.authz.PublicEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PublicEndpoint("public storefront promotions: CATALOG_READ (GUEST); APPROVED shops, active + in-date + shown on storefront only")
@RequestMapping("/api/shops/{slug}/promotions")
public class PublicPromotionController {
    private final PromotionService promotions;

    public PublicPromotionController(PromotionService promotions) { this.promotions = promotions; }

    @GetMapping
    public List<PromotionDtos.PublicPromotionResponse> list(@PathVariable String slug) {
        return promotions.publicList(slug);
    }
}