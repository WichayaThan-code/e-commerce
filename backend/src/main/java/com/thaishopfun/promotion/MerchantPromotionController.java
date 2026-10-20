package com.thaishopfun.promotion;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * DEPRECATED (R1): duplicate of {@link com.thaishopfun.cart.MerchantCouponController} (/api/merchant/shops/{shopId}/coupons),
 * now the primary merchant API used by the frontend. Kept only until the next cleanup; do not add features here.
 * Merchant promotions/coupons of one shop: PROMOTION_READ for reads, PROMOTION_MANAGE for every write (V27).
 */
@Deprecated(forRemoval = true)
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/promotions")
public class MerchantPromotionController {
    private final PromotionService promotions;

    public MerchantPromotionController(PromotionService promotions) { this.promotions = promotions; }

    @RequireShopPermission("PROMOTION_READ")
    @GetMapping
    public PromotionDtos.PromotionListResponse list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String type,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return promotions.list(RequestUser.id(jwt), shopId, q, status, type, page, size);
    }

    @RequireShopPermission("PROMOTION_READ")
    @GetMapping("/{promotionId}")
    public PromotionDtos.PromotionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID promotionId) {
        return promotions.get(RequestUser.id(jwt), shopId, promotionId);
    }

    @RequireShopPermission("PROMOTION_MANAGE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionDtos.PromotionResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                                  @RequestBody PromotionDtos.PromotionWriteRequest request) {
        return promotions.create(RequestUser.id(jwt), shopId, request);
    }

    @RequireShopPermission("PROMOTION_MANAGE")
    @PutMapping("/{promotionId}")
    public PromotionDtos.PromotionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID promotionId,
                                                  @RequestBody PromotionDtos.PromotionWriteRequest request) {
        return promotions.update(RequestUser.id(jwt), shopId, promotionId, request);
    }

    @RequireShopPermission("PROMOTION_MANAGE")
    @PostMapping("/{promotionId}/pause")
    public PromotionDtos.PromotionResponse pause(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID promotionId) {
        return promotions.setActive(RequestUser.id(jwt), shopId, promotionId, false);
    }

    @RequireShopPermission("PROMOTION_MANAGE")
    @PostMapping("/{promotionId}/resume")
    public PromotionDtos.PromotionResponse resume(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID promotionId) {
        return promotions.setActive(RequestUser.id(jwt), shopId, promotionId, true);
    }

    @RequireShopPermission("PROMOTION_MANAGE")
    @DeleteMapping("/{promotionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID promotionId) {
        promotions.delete(RequestUser.id(jwt), shopId, promotionId);
    }
}