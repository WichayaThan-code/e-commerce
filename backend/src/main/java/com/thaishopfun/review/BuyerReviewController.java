package com.thaishopfun.review;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Signed-in buyer writes a review for one of their own delivered order items (implicit BUYER role, BUYER_ORDER_READ). */
@RestController
@RequestMapping("/api/me/reviews")
public class BuyerReviewController {
    private final ProductReviewService reviews;

    public BuyerReviewController(ProductReviewService reviews) { this.reviews = reviews; }

    @RequirePermission("BUYER_ORDER_READ")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewDtos.ReviewView create(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) ReviewDtos.CreateReviewRequest request) {
        return reviews.create(RequestUser.id(jwt), request);
    }
}
