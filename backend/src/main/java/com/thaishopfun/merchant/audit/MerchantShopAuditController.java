package com.thaishopfun.merchant.audit;

import com.thaishopfun.auth.authz.RequireShopPermission;
import com.thaishopfun.order.OrderQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/merchant/shops/{shopId}/security-activity")
public class MerchantShopAuditController {
    private final OrderQueryService shops;
    private final MerchantShopAuditService audits;

    public MerchantShopAuditController(OrderQueryService shops, MerchantShopAuditService audits) {
        this.shops = shops;
        this.audits = audits;
    }

    @RequireShopPermission("AUDIT_READ")
    @GetMapping
    public MerchantShopAuditDtos.Page list(
        @PathVariable Long shopId,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String outcome,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return audits.list(shops.requireShop(shopId), new MerchantShopAuditService.Filter(action, outcome, from, to), page, size);
    }

    @RequireShopPermission("AUDIT_READ")
    @GetMapping("/{id}")
    public MerchantShopAuditDtos.ActivityDetail detail(@PathVariable Long shopId, @PathVariable long id) {
        return audits.detail(shops.requireShop(shopId), id);
    }
}
