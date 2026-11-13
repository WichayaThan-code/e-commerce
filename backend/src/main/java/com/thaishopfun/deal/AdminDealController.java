package com.thaishopfun.deal;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/deal-campaigns")
public class AdminDealController {
    private final DealService deals;

    public AdminDealController(DealService deals) {
        this.deals = deals;
    }

    @RequirePermission("PLATFORM_SETTING_UPDATE")
    @GetMapping
    public List<DealDtos.AdminCampaignResponse> list(@RequestParam(required = false) String kind) {
        return deals.adminList(kind);
    }

    @RequirePermission("PLATFORM_SETTING_UPDATE")
    @GetMapping("/{id}")
    public DealDtos.AdminCampaignResponse get(@PathVariable UUID id) {
        return deals.adminGet(id);
    }

    @RequirePermission("PLATFORM_SETTING_UPDATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DealDtos.AdminCampaignResponse create(@RequestBody DealDtos.CampaignWriteRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return deals.create(request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_SETTING_UPDATE")
    @PutMapping("/{id}")
    public DealDtos.AdminCampaignResponse update(@PathVariable UUID id,
                                                 @RequestBody DealDtos.CampaignWriteRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return deals.update(id, request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_SETTING_UPDATE")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deals.delete(id);
    }

    @RequirePermission("PLATFORM_SETTING_UPDATE")
    @PostMapping("/{id}/products")
    public DealDtos.AdminCampaignResponse attach(@PathVariable UUID id,
                                                 @RequestBody DealDtos.CampaignProductIdsRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return deals.attachProducts(id, request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_SETTING_UPDATE")
    @DeleteMapping("/{id}/products/{productId}")
    public DealDtos.AdminCampaignResponse detach(@PathVariable UUID id,
                                                 @PathVariable UUID productId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return deals.detachProduct(id, productId, RequestUser.id(jwt));
    }
}
