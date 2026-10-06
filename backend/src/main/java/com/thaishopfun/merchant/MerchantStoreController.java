package com.thaishopfun.merchant;

import com.thaishopfun.auth.authz.FilterByShopPermission;
import com.thaishopfun.auth.authz.RequirePermission;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchant")
public class MerchantStoreController {
    /** Permission that decides which shops appear in "my shops" lists (and store-access). */
    static final String SHOP_LIST_PERMISSION = "SHOP_READ";

    private final StoreReviewService reviews;
    private final MerchantShopSettingsService shopSettings;
    private final MerchantApplicationService applications;
    private final ShopReinstatementService reinstatements;
    private final OwnerAccountService ownerAccounts;

    public MerchantStoreController(StoreReviewService reviews, MerchantShopSettingsService shopSettings, MerchantApplicationService applications, ShopReinstatementService reinstatements, OwnerAccountService ownerAccounts) {
        this.reviews = reviews;
        this.shopSettings = shopSettings;
        this.applications = applications;
        this.reinstatements = reinstatements;
        this.ownerAccounts = ownerAccounts;
    }

    @FilterByShopPermission(SHOP_LIST_PERMISSION)
    @GetMapping("/shops")
    public List<MerchantDtos.MerchantShopSummary> shops(@AuthenticationPrincipal Jwt jwt) {
        return shopSettings.list(UUID.fromString(jwt.getSubject()), SHOP_LIST_PERMISSION);
    }

    @RequireShopPermission("SHOP_READ")
    @GetMapping("/reinstatement")
    public MerchantDtos.ReinstatementResponse reinstatement(@AuthenticationPrincipal Jwt jwt, @RequestParam Long shopId) {
        return reinstatements.current(UUID.fromString(jwt.getSubject()), shopId);
    }

    @RequireShopPermission("SHOP_UPDATE")
    @PostMapping("/reinstatement")
    public MerchantDtos.ReinstatementResponse submitReinstatement(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam Long shopId,
        @Valid @RequestBody MerchantDtos.ReinstatementRequestBody request
    ) {
        return reinstatements.submit(UUID.fromString(jwt.getSubject()), shopId, request.message());
    }

    @RequirePermission("SHOP_APPLICATION_READ")
    @GetMapping("/owners")
    public List<MerchantDtos.MerchantOwnerResponse> owners(@AuthenticationPrincipal Jwt jwt) {
        return ownerAccounts.list(UUID.fromString(jwt.getSubject()));
    }

    @RequirePermission("SHOP_APPLICATION_UPDATE")
    @PutMapping("/owners/{id}")
    public MerchantDtos.MerchantOwnerResponse updateOwner(@AuthenticationPrincipal Jwt jwt, @org.springframework.web.bind.annotation.PathVariable UUID id, @Valid @RequestBody MerchantDtos.OwnerRequest request) {
        return ownerAccounts.update(UUID.fromString(jwt.getSubject()), id, request);
    }

    @RequirePermission("SHOP_APPLICATION_CREATE")
    @PostMapping("/shops")
    public MerchantDtos.ApplicationResponse openAnotherShop(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID ownerId) {
        return applications.openAnotherShop(UUID.fromString(jwt.getSubject()), ownerId);
    }

    @FilterByShopPermission(SHOP_LIST_PERMISSION)
    @GetMapping("/store-access")
    public MerchantDtos.StoreAccessResponse access(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long shopId) {
        return reviews.access(UUID.fromString(jwt.getSubject()), shopId, SHOP_LIST_PERMISSION);
    }

    @RequireShopPermission(value = "SHOP_READ", defaultShop = true)
    @GetMapping("/settings")
    public MerchantDtos.MerchantSettingsResponse readSettings(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long shopId) {
        return shopSettings.get(UUID.fromString(jwt.getSubject()), shopId);
    }

    @RequireShopPermission(value = "SHOP_UPDATE", defaultShop = true)
    @PutMapping("/settings")
    public MerchantDtos.MerchantSettingsResponse updateSettings(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long shopId,
        @Valid @RequestBody MerchantDtos.MerchantSettingsUpdateRequest request
    ) {
        return shopSettings.update(UUID.fromString(jwt.getSubject()), shopId, request);
    }

    @RequireShopPermission(value = "SHOP_UPDATE", defaultShop = true)
    @PutMapping("/settings/hours")
    public MerchantDtos.MerchantSettingsResponse updateHours(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long shopId,
        @Valid @RequestBody MerchantDtos.OpeningHoursRequest request
    ) {
        return shopSettings.updateHours(UUID.fromString(jwt.getSubject()), shopId, request);
    }

    @RequireShopPermission(value = "SHOP_UPDATE", defaultShop = true)
    @PostMapping(value = "/settings/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MerchantDtos.MerchantSettingsResponse uploadLogo(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long shopId,
        @RequestPart("file") MultipartFile file
    ) {
        return shopSettings.uploadLogo(UUID.fromString(jwt.getSubject()), shopId, file);
    }

    @RequireShopPermission(value = "SHOP_UPDATE", defaultShop = true)
    @PostMapping(value = "/settings/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MerchantDtos.MerchantSettingsResponse uploadCover(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long shopId,
        @RequestPart("file") MultipartFile file
    ) {
        return shopSettings.uploadCover(UUID.fromString(jwt.getSubject()), shopId, file);
    }
}
