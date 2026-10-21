package com.thaishopfun.payment;

import com.thaishopfun.auth.authz.RequirePermission;
import com.thaishopfun.order.OrderQueryService;
import org.springframework.web.bind.annotation.*;

/** GET /api/checkout/payment-methods?shopId= — what the buyer may choose at checkout for that shop (BANK_TRANSFER / COD + fee). */
@RestController
@RequestMapping("/api/checkout/payment-methods")
public class CheckoutPaymentMethodController {
    private final PaymentMethodService methods;
    private final OrderQueryService queries;

    public CheckoutPaymentMethodController(PaymentMethodService methods, OrderQueryService queries) {
        this.methods = methods;
        this.queries = queries;
    }

    @RequirePermission("CHECKOUT_CREATE")
    @GetMapping
    public PaymentDtos.PaymentMethodsView list(@RequestParam Long shopId) {
        return methods.view(shopId, queries.requireShop(shopId).getUuid());
    }
}
