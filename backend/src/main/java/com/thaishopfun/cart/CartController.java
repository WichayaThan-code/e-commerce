package com.thaishopfun.cart;

import com.thaishopfun.auth.authz.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Signed-in buyer's cart. Account-level rules: every active account holds CART_READ / CART_UPDATE / CHECKOUT_CREATE
 * through the implicit BUYER role. The service only ever touches carts whose buyer_user_id is the caller.
 * {shopId} = public shop number.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService carts;
    private final CartAccess access;

    public CartController(CartService carts, CartAccess access) {
        this.carts = carts;
        this.access = access;
    }

    @RequirePermission("CART_READ")
    @GetMapping
    public CartDtos.CartResponse get() { return carts.get(access.requireBuyer()); }

    @RequirePermission("CART_READ")
    @GetMapping("/count")
    public CartDtos.CountResponse count() { return carts.summary(access.requireBuyer()); }

    @RequirePermission("CART_UPDATE")
    @PostMapping("/items")
    public CartDtos.CartResponse add(@RequestBody CartDtos.AddItemRequest request) { return carts.add(access.requireBuyer(), request); }

    @RequirePermission("CART_UPDATE")
    @PatchMapping("/items/{itemId}")
    public CartDtos.CartResponse update(@PathVariable UUID itemId, @RequestBody CartDtos.UpdateItemRequest request) {
        return carts.update(access.requireBuyer(), itemId, request);
    }

    @RequirePermission("CART_UPDATE")
    @DeleteMapping("/items/{itemId}")
    public CartDtos.CartResponse remove(@PathVariable UUID itemId) { return carts.remove(access.requireBuyer(), itemId); }

    @RequirePermission("CART_UPDATE")
    @PostMapping("/items/remove")
    public CartDtos.CartResponse removeMany(@RequestBody CartDtos.ItemIdsRequest request) { return carts.removeMany(access.requireBuyer(), request); }

    @RequirePermission("CART_UPDATE")
    @PutMapping("/selection")
    public CartDtos.CartResponse select(@RequestBody CartDtos.SelectionRequest request) { return carts.select(access.requireBuyer(), request); }

    @RequirePermission("CART_UPDATE")
    @DeleteMapping
    public CartDtos.CartResponse clear(@RequestParam(required = false) Long shopId) { return carts.clear(access.requireBuyer(), shopId); }

    @RequirePermission("CART_UPDATE")
    @PostMapping("/shops/{shopId}/coupon")
    public CartDtos.CartResponse applyCoupon(@PathVariable Long shopId, @RequestBody CartDtos.ApplyCouponRequest request) {
        return carts.applyCoupon(access.requireBuyer(), shopId, request);
    }

    @RequirePermission("CART_UPDATE")
    @DeleteMapping("/shops/{shopId}/coupon")
    public CartDtos.CartResponse removeCoupon(@PathVariable Long shopId) { return carts.removeCoupon(access.requireBuyer(), shopId); }

    @RequirePermission("CART_UPDATE")
    @PutMapping("/shops/{shopId}/shipping-method")
    public CartDtos.CartResponse chooseShipping(@PathVariable Long shopId, @RequestBody(required = false) CartDtos.ChooseShippingRequest request) {
        return carts.chooseShipping(access.requireBuyer(), shopId, request);
    }

    @RequirePermission("CART_UPDATE")
    @PostMapping("/accept-prices")
    public CartDtos.CartResponse acceptPrices() { return carts.acceptPrices(access.requireBuyer()); }

    @RequirePermission("CHECKOUT_CREATE")
    @PostMapping("/validate")
    public CartDtos.ValidationResponse validate() { return carts.validate(access.requireBuyer()); }

    /** After login: move the guest cart (X-Cart-Token) into this buyer's cart. Safe to call twice. */
    @RequirePermission("CART_UPDATE")
    @PostMapping("/merge")
    public CartDtos.CartResponse merge(HttpServletRequest request) {
        return carts.merge(access.requireBuyer(), access.optionalGuestToken(request));
    }
}
