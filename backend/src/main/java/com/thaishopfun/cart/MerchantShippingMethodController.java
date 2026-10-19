package com.thaishopfun.cart;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Shop shipping rules used by the cart (no SHIPPING_SETTING_* code in the seed: SHOP_READ / SHOP_UPDATE). */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/shipping-methods")
public class MerchantShippingMethodController {
    private final ShippingMethodService methods;

    public MerchantShippingMethodController(ShippingMethodService methods) { this.methods = methods; }

    @RequireShopPermission("SHOP_READ")
    @GetMapping
    public List<CartDtos.ShippingMethodResponse> list(@PathVariable Long shopId) { return methods.list(shopId); }

    @RequireShopPermission("SHOP_UPDATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartDtos.ShippingMethodResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                                  @RequestBody CartDtos.ShippingMethodRequest request) {
        return methods.create(RequestUser.id(jwt), shopId, request);
    }

    @RequireShopPermission("SHOP_UPDATE")
    @PutMapping("/{methodId}")
    public CartDtos.ShippingMethodResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID methodId,
                                                  @RequestBody CartDtos.ShippingMethodRequest request) {
        return methods.update(RequestUser.id(jwt), shopId, methodId, request);
    }

    @RequireShopPermission("SHOP_UPDATE")
    @PostMapping("/{methodId}/disable")
    public CartDtos.ShippingMethodResponse disable(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID methodId) {
        return methods.setActive(RequestUser.id(jwt), shopId, methodId, false);
    }

    @RequireShopPermission("SHOP_UPDATE")
    @PostMapping("/{methodId}/enable")
    public CartDtos.ShippingMethodResponse enable(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID methodId) {
        return methods.setActive(RequestUser.id(jwt), shopId, methodId, true);
    }
}
