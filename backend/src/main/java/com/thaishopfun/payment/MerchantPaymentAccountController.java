package com.thaishopfun.payment;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.auth.authz.RequireShopPermission;
import com.thaishopfun.order.OrderQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The shop's receiving accounts (provider BANK_TRANSFER | PROMPTPAY). No PAYMENT_ACCOUNT_* code in the seed:
 * read = SHOP_READ (account numbers masked unless the member also has SHOP_UPDATE), change = SHOP_UPDATE.
 */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/payment-accounts")
public class MerchantPaymentAccountController {
    private final PaymentAccountService accounts;
    private final OrderQueryService queries;
    private final AuthorizationService authz;

    public MerchantPaymentAccountController(PaymentAccountService accounts, OrderQueryService queries, AuthorizationService authz) {
        this.accounts = accounts;
        this.queries = queries;
        this.authz = authz;
    }

    @RequireShopPermission("SHOP_READ")
    @GetMapping
    public List<PaymentDtos.PaymentAccountView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId) {
        var shop = queries.requireShop(shopId);
        var userId = RequestUser.id(jwt);
        // Existing shops: seed BANK_TRANSFER once from registration if settings are still empty.
        accounts.ensureBankFromRegistration(shop.getUuid(), shop.getApplication(), shop.getOwner(), userId);
        boolean full = authz.checkShop(userId, shopId, "SHOP_UPDATE") == AuthorizationService.ShopDecision.GRANTED;
        return accounts.list(shop.getUuid(), !full);
    }

    @RequireShopPermission("SHOP_UPDATE")
    @PutMapping("/{provider}")
    public PaymentDtos.PaymentAccountView upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable String provider,
                                                 @RequestBody(required = false) PaymentDtos.PaymentAccountRequest request) {
        return accounts.upsert(queries.requireShop(shopId).getUuid(), provider, request, RequestUser.id(jwt));
    }

    @RequireShopPermission("SHOP_UPDATE")
    @DeleteMapping("/{provider}")
    public PaymentDtos.PaymentAccountView deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable String provider) {
        return accounts.deactivate(queries.requireShop(shopId).getUuid(), provider, RequestUser.id(jwt));
    }
}
