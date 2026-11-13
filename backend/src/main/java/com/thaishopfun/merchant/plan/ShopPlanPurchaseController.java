package com.thaishopfun.merchant.plan;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import com.thaishopfun.merchant.plan.ShopPlanPurchaseDtos.*;
import com.thaishopfun.order.OrderQueryService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/merchant/shops/{shopId}/plan-purchases")
public class ShopPlanPurchaseController {
    private final OrderQueryService shops;
    private final ShopPlanPurchaseService purchases;

    public ShopPlanPurchaseController(OrderQueryService shops, ShopPlanPurchaseService purchases) {
        this.shops = shops;
        this.purchases = purchases;
    }

    @RequireShopPermission("SHOP_READ")
    @GetMapping("/catalog")
    public PurchaseCatalogResponse catalog(@PathVariable Long shopId) {
        shops.requireShop(shopId);
        return purchases.catalog();
    }

    @RequireShopPermission("SHOP_READ")
    @GetMapping
    public PurchaseHistoryResponse history(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId
    ) {
        return purchases.history(shops.requireShop(shopId));
    }

    @RequireShopPermission("SHOP_UPDATE")
    @PostMapping
    public PurchaseOrderView create(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @RequestBody(required = false) CreatePurchaseRequest body
    ) {
        return purchases.create(shops.requireShop(shopId), body, RequestUser.id(jwt));
    }

    @RequireShopPermission("SHOP_UPDATE")
    @PostMapping(path = "/{subscriptionId}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PurchaseOrderView attachProof(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID subscriptionId,
        @RequestPart("file") MultipartFile file
    ) {
        return purchases.attachProof(shops.requireShop(shopId), subscriptionId, file, RequestUser.id(jwt));
    }

    @RequireShopPermission("SHOP_UPDATE")
    @PostMapping("/{subscriptionId}/cancel")
    public PurchaseOrderView cancel(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID subscriptionId
    ) {
        return purchases.cancel(shops.requireShop(shopId), subscriptionId, RequestUser.id(jwt));
    }
}
