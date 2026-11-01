package com.thaishopfun.order;

import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.web.bind.annotation.*;

/** A1: read-only dashboard KPIs for one shop. {shopId} = public shop number; needs ORDER_READ on that shop. */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/dashboard")
public class MerchantDashboardController {
    private final OrderQueryService queries;
    private final MerchantDashboardService dashboard;

    public MerchantDashboardController(OrderQueryService queries, MerchantDashboardService dashboard) {
        this.queries = queries;
        this.dashboard = dashboard;
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping("/summary")
    public MerchantDashboardService.Summary summary(@PathVariable Long shopId, @RequestParam(required = false) String date,
                                                   @RequestParam(required = false) String range,
                                                   @RequestParam(required = false) String from,
                                                   @RequestParam(required = false) String to) {
        return dashboard.summary(queries.requireShop(shopId).getUuid(), date, range, from, to);
    }
    /** A12: sales series + payment-method split + top 5 products/customers for the same period/paid rule as summary. */
    @RequireShopPermission("ORDER_READ")
    @GetMapping("/insights")
    public MerchantDashboardService.Insights insights(@PathVariable Long shopId, @RequestParam(required = false) String date,
                                                     @RequestParam(required = false) String range,
                                                     @RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to) {
        return dashboard.insights(shopId, queries.requireShop(shopId).getUuid(), date, range, from, to);
    }
}
