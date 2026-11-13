package com.thaishopfun.admin;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import com.thaishopfun.merchant.plan.ShopPlanPurchaseDtos.PurchaseOrderView;
import com.thaishopfun.merchant.plan.ShopPlanPurchaseDtos.RejectPurchaseRequest;
import com.thaishopfun.merchant.plan.ShopPlanPurchaseService;
import com.thaishopfun.payment.PaymentFileService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/plan-purchases")
public class AdminPlanPurchaseController {
    private final ShopPlanPurchaseService purchases;
    private final PaymentFileService paymentFiles;

    public AdminPlanPurchaseController(ShopPlanPurchaseService purchases, PaymentFileService paymentFiles) {
        this.purchases = purchases;
        this.paymentFiles = paymentFiles;
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping
    public List<PurchaseOrderView> list(@RequestParam(required = false) String status) {
        return purchases.adminList(status);
    }

    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/{subscriptionId}/approve")
    public PurchaseOrderView approve(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID subscriptionId
    ) {
        return purchases.approve(subscriptionId, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/{subscriptionId}/reject")
    public PurchaseOrderView reject(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID subscriptionId,
        @RequestBody(required = false) RejectPurchaseRequest body
    ) {
        return purchases.reject(subscriptionId, body == null ? null : body.reason(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/{subscriptionId}/proof/{mediaFileId}")
    public ResponseEntity<Resource> proof(
        @PathVariable UUID subscriptionId,
        @PathVariable UUID mediaFileId
    ) {
        // Media is PRIVATE; admin with PLATFORM_SHOP_READ may inspect slips for plan reviews.
        return paymentFiles.serve(mediaFileId);
    }
}
