package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * POST /api/checkout — turns the signed-in buyer's selected cart lines into one PENDING_PAYMENT order per shop.
 * Header Idempotency-Key is required: the same key returns the same orders (200, replayed=true) instead of new ones.
 */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {
    private final OrderCheckoutService checkout;

    public CheckoutController(OrderCheckoutService checkout) {
        this.checkout = checkout;
    }

    @RequirePermission("CHECKOUT_CREATE")
    @PostMapping
    public ResponseEntity<OrderDtos.CheckoutResponse> checkout(@AuthenticationPrincipal Jwt jwt,
                                                               @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                               @RequestBody(required = false) OrderDtos.CheckoutRequest request) {
        var buyer = RequestUser.id(jwt);
        OrderCheckoutService.requireKey(key);
        try {
            var result = checkout.checkout(buyer, key, request);
            return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
        } catch (DataIntegrityViolationException exception) {
            // two requests with the same key raced past the replay check: uq_orders_idempotency_key let one win
            var replay = checkout.replay(buyer, key);
            if (replay != null) return ResponseEntity.ok(replay);
            throw new AuthException(HttpStatus.CONFLICT, "CHECKOUT_CONFLICT", "มีการสั่งซื้อซ้อนกัน กรุณาลองใหม่");
        }
    }
}
