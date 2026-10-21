package com.thaishopfun.coupon;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Signed-in buyer's coupon wallet. Uses the buyer cart permissions (implicit BUYER role): collecting is part of
 * building one's own cart, so no new permission code is needed (CART_UPDATE to collect, CART_READ to list).
 */
@RestController
@RequestMapping("/api/me/coupons")
public class CouponWalletController {
    private final CouponWalletService wallet;

    public CouponWalletController(CouponWalletService wallet) { this.wallet = wallet; }

    /** ?shopId= (public shop number) narrows to one shop. */
    @RequirePermission("CART_READ")
    @GetMapping
    public List<CouponWalletDtos.CollectedCoupon> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long shopId) {
        return wallet.list(RequestUser.id(jwt), shopId);
    }

    /** body {shopId, code}; 201 = newly collected, 200 = already in the wallet. */
    @RequirePermission("CART_UPDATE")
    @PostMapping
    public ResponseEntity<CouponWalletDtos.CollectResponse> collect(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) CouponWalletDtos.CollectRequest request) {
        var result = wallet.collect(RequestUser.id(jwt), request);
        return ResponseEntity.status(result.alreadyCollected() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }
}
