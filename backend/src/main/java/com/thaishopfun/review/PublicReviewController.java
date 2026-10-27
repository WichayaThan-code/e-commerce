package com.thaishopfun.review;

import com.thaishopfun.auth.authz.PublicEndpoint;
import org.springframework.web.bind.annotation.*;

@RestController
@PublicEndpoint // storefront: anyone can read published reviews (masked reviewer names only)
@RequestMapping("/api/products/{idOrSlug}/reviews")
public class PublicReviewController {
    private final ProductReviewService reviews;

    public PublicReviewController(ProductReviewService reviews) { this.reviews = reviews; }

    @GetMapping
    public ReviewDtos.ReviewPage list(@PathVariable String idOrSlug,
                                      @RequestParam(required = false) String shopSlug,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size) {
        return reviews.list(idOrSlug, shopSlug, page, size);
    }
}
