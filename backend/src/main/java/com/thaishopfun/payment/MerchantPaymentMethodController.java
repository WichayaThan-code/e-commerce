package com.thaishopfun.payment;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import com.thaishopfun.order.OrderQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Payment methods the shop offers (V29): BANK_TRANSFER is always on; Cash on Delivery is switched here with an optional
 * fee and max order amount. Same codes as the receiving accounts: read = SHOP_READ, change = SHOP_UPDATE.
 */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/payment-methods")
public class MerchantPaymentMethodController {
    private final PaymentMethodService methods;
    private final OrderQueryService queries;

    public MerchantPaymentMethodController(PaymentMethodService methods, OrderQueryService queries) {
        this.methods = methods;
        this.queries = queries;
    }

    @RequireShopPermission("SHOP_READ")
    @GetMapping
    public PaymentDtos.PaymentMethodsView list(@PathVariable Long shopId) {
        return methods.view(shopId, queries.requireShop(shopId).getUuid());
    }

    /** Body {enabled, fee?, maxOrderAmount?}. Disabling affects new checkouts only; existing COD orders continue. */
    @RequireShopPermission("SHOP_UPDATE")
    @PutMapping("/cod")
    public PaymentDtos.PaymentMethodsView updateCod(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                                    @RequestBody(required = false) PaymentDtos.CodSettingsRequest request) {
        return methods.updateCod(shopId, queries.requireShop(shopId).getUuid(), request, RequestUser.id(jwt));
    }
}
