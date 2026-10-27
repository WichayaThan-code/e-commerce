package com.thaishopfun.catalog.configurator;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** V42 merchant configurator editor (flag app.features.configurator.enabled; CUSTOM shops only). */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/products/{productId}/configurator")
public class MerchantConfiguratorController {
    private final ProductConfiguratorService configurators;

    public MerchantConfiguratorController(ProductConfiguratorService configurators) { this.configurators = configurators; }

    @RequireShopPermission("PRODUCT_READ")
    @GetMapping
    public ConfiguratorDtos.MerchantConfiguratorResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID productId) {
        return configurators.merchantGet(RequestUser.id(jwt), shopId, productId);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @PutMapping
    public ConfiguratorDtos.MerchantConfiguratorResponse save(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID productId,
                                                              @RequestBody ConfiguratorDtos.ConfiguratorWriteRequest request) {
        return configurators.merchantSave(RequestUser.id(jwt), shopId, productId, request);
    }
}
