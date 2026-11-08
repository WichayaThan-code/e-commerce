package com.thaishopfun.merchant.plan;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import com.thaishopfun.order.OrderQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/shops/{shopId}/plan-usage")
public class MerchantPlanUsageController {
    private final OrderQueryService shops;
    private final MerchantPlanUsageService usage;

    public MerchantPlanUsageController(OrderQueryService shops, MerchantPlanUsageService usage) {
        this.shops = shops;
        this.usage = usage;
    }

    /** Read-only snapshot of plan + usage for the active shop. SHOP_READ gates membership; metrics may still be RESTRICTED. */
    @RequireShopPermission("SHOP_READ")
    @GetMapping
    public MerchantPlanUsageDtos.PlanUsageResponse get(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId
    ) {
        return usage.snapshot(shops.requireShop(shopId), RequestUser.id(jwt));
    }
}
