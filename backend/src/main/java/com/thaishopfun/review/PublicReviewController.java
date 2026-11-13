package com.thaishopfun.review;

import com.thaishopfun.auth.authz.PublicEndpoint;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@PublicEndpoint // storefront: anyone can read published reviews (masked reviewer names only)
public class PublicReviewController {
    private final ProductReviewService reviews;

    public PublicReviewController(ProductReviewService reviews) { this.reviews = reviews; }

    @GetMapping("/api/products/{idOrSlug}/reviews")
    public ReviewDtos.ReviewPage list(@PathVariable String idOrSlug,
                                      @RequestParam(required = false) String shopSlug,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size,
                                      @RequestParam(required = false) Integer rating,
                                      @RequestParam(required = false) Boolean withPhotos) {
        return reviews.list(idOrSlug, shopSlug, page, size, rating, withPhotos);
    }

    @GetMapping("/api/review-images/{mediaId}")
    public ResponseEntity<Resource> image(@PathVariable UUID mediaId) {
        return reviews.serveImage(mediaId);
    }
}
