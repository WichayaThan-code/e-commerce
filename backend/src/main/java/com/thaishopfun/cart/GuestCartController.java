package com.thaishopfun.cart;

import com.thaishopfun.auth.authz.PublicEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Guest cart (not signed in). Scoped strictly to the X-Cart-Token header: the server stores only its SHA-256, and
 * every query filters by that hash. The first add without a token issues one (response body cartToken + header).
 * Checkout itself needs login: validate returns LOGIN_REQUIRED (INFO) for guests; call POST /api/cart/merge after login.
 */
@RestController
@PublicEndpoint("guest cart scoped to the X-Cart-Token header")
@RequestMapping("/api/guest-cart")
public class GuestCartController {
    private final CartService carts;
    private final CartAccess access;

    public GuestCartController(CartService carts, CartAccess access) {
        this.carts = carts;
        this.access = access;
    }

    @GetMapping
    public CartDtos.CartResponse get(HttpServletRequest request) {
        if (access.optionalGuestToken(request) == null) return carts.emptyGuestCart();
        return carts.get(access.requireGuest(request));
    }

    @GetMapping("/count")
    public CartDtos.CountResponse count(HttpServletRequest request) {
        if (access.optionalGuestToken(request) == null) return new CartDtos.CountResponse(0, 0, 0, 0);
        return carts.summary(access.requireGuest(request));
    }

    @PostMapping("/items")
    public CartDtos.CartResponse add(HttpServletRequest request, HttpServletResponse response, @RequestBody CartDtos.AddItemRequest body) {
        var token = access.optionalGuestToken(request);
        if (token == null) token = access.newGuestToken();
        response.setHeader(CartAccess.CART_TOKEN_HEADER, token);
        return carts.add(new CartOwner(null, token, access.hash(token)), body);
    }

    @PatchMapping("/items/{itemId}")
    public CartDtos.CartResponse update(HttpServletRequest request, @PathVariable UUID itemId, @RequestBody CartDtos.UpdateItemRequest body) {
        return carts.update(access.requireGuest(request), itemId, body);
    }

    @DeleteMapping("/items/{itemId}")
    public CartDtos.CartResponse remove(HttpServletRequest request, @PathVariable UUID itemId) {
        return carts.remove(access.requireGuest(request), itemId);
    }

    @PostMapping("/items/remove")
    public CartDtos.CartResponse removeMany(HttpServletRequest request, @RequestBody CartDtos.ItemIdsRequest body) {
        return carts.removeMany(access.requireGuest(request), body);
    }

    @PutMapping("/selection")
    public CartDtos.CartResponse select(HttpServletRequest request, @RequestBody CartDtos.SelectionRequest body) {
        return carts.select(access.requireGuest(request), body);
    }

    @DeleteMapping
    public CartDtos.CartResponse clear(HttpServletRequest request, @RequestParam(required = false) Long shopId) {
        return carts.clear(access.requireGuest(request), shopId);
    }

    @PostMapping("/shops/{shopId}/coupon")
    public CartDtos.CartResponse applyCoupon(HttpServletRequest request, @PathVariable Long shopId, @RequestBody CartDtos.ApplyCouponRequest body) {
        return carts.applyCoupon(access.requireGuest(request), shopId, body);
    }

    @DeleteMapping("/shops/{shopId}/coupon")
    public CartDtos.CartResponse removeCoupon(HttpServletRequest request, @PathVariable Long shopId) {
        return carts.removeCoupon(access.requireGuest(request), shopId);
    }

    @PutMapping("/shops/{shopId}/shipping-method")
    public CartDtos.CartResponse chooseShipping(HttpServletRequest request, @PathVariable Long shopId,
                                                @RequestBody(required = false) CartDtos.ChooseShippingRequest body) {
        return carts.chooseShipping(access.requireGuest(request), shopId, body);
    }

    @PostMapping("/accept-prices")
    public CartDtos.CartResponse acceptPrices(HttpServletRequest request) {
        return carts.acceptPrices(access.requireGuest(request));
    }

    @PostMapping("/validate")
    public CartDtos.ValidationResponse validate(HttpServletRequest request) {
        return carts.validateGuest(access.requireGuest(request));
    }
}
