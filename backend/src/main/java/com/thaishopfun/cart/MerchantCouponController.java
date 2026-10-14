package com.thaishopfun.cart;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Shop coupons. The permission seed has no PROMOTION_* codes yet, so (like the frontend's interim mapping
 * PROMOTION_MANAGE -> SHOP_UPDATE, /merchant/promotions -> SHOP_READ) reads use SHOP_READ and writes SHOP_UPDATE.
 */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/coupons")
public class MerchantCouponController {
    private final CouponService coupons;

    public MerchantCouponController(CouponService coupons) { this.coupons = coupons; }

    @RequireShopPermission("SHOP_READ")
    @GetMapping
    public CartDtos.PageResponse<CartDtos.CouponResponse> list(@PathVariable Long shopId,
                                                               @RequestParam(required = false) String q,
                                                               @RequestParam(required = false) String state,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size) {
        return coupons.list(shopId, q, state, page, size);
    }

    @RequireShopPermission("SHOP_READ")
    @GetMapping("/{couponId}")
    public CartDtos.CouponResponse get(@PathVariable Long shopId, @PathVariable UUID couponId) { return coupons.get(shopId, couponId); }

    @RequireShopPermission("SHOP_UPDATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartDtos.CouponResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @RequestBody CartDtos.CouponRequest request) {
        return coupons.create(RequestUser.id(jwt), shopId, request);
    }

    @RequireShopPermission("SHOP_UPDATE")
    @PutMapping("/{couponId}")
    public CartDtos.CouponResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID couponId,
                                          @RequestBody CartDtos.CouponRequest request) {
        return coupons.update(RequestUser.id(jwt), shopId, couponId, request);
    }

    @RequireShopPermission("SHOP_UPDATE")
    @PostMapping("/{couponId}/disable")
    public CartDtos.CouponResponse disable(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID couponId) {
        return coupons.setActive(RequestUser.id(jwt), shopId, couponId, false);
    }

    @RequireShopPermission("SHOP_UPDATE")
    @PostMapping("/{couponId}/enable")
    public CartDtos.CouponResponse enable(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID couponId) {
        return coupons.setActive(RequestUser.id(jwt), shopId, couponId, true);
    }
}
