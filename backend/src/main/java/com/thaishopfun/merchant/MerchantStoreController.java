package com.thaishopfun.merchant;

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

    @GetMapping("/shops")
    public List<MerchantDtos.MerchantShopSummary> shops(@AuthenticationPrincipal Jwt jwt) {
        return shopSettings.list(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/reinstatement")
    public MerchantDtos.ReinstatementResponse reinstatement(@AuthenticationPrincipal Jwt jwt, @RequestParam Long shopId) {
        return reinstatements.current(UUID.fromString(jwt.getSubject()), shopId);
    }

    @PostMapping("/reinstatement")
    public MerchantDtos.ReinstatementResponse submitReinstatement(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam Long shopId,
        @Valid @RequestBody MerchantDtos.ReinstatementRequestBody request
    ) {
        return reinstatements.submit(UUID.fromString(jwt.getSubject()), shopId, request.message());
    }

    @GetMapping("/owners")
    public List<MerchantDtos.MerchantOwnerResponse> owners(@AuthenticationPrincipal Jwt jwt) {
        return ownerAccounts.list(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/owners/{id}")
    public MerchantDtos.MerchantOwnerResponse updateOwner(@AuthenticationPrincipal Jwt jwt, @org.springframework.web.bind.annotation.PathVariable UUID id, @Valid @RequestBody MerchantDtos.OwnerRequest request) {
        return ownerAccounts.update(UUID.fromString(jwt.getSubject()), id, request);
    }

    @PostMapping("/shops")
    public MerchantDtos.ApplicationResponse openAnotherShop(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID ownerId) {
        return applications.openAnotherShop(UUID.fromString(jwt.getSubject()), ownerId);
    }

    @GetMapping("/store-access")
    public MerchantDtos.StoreAccessResponse access(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long shopId) {
        return reviews.access(UUID.fromString(jwt.getSubject()), shopId);
    }

    @GetMapping("/settings")
    public MerchantDtos.MerchantSettingsResponse readSettings(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long shopId) {
        return shopSettings.get(UUID.fromString(jwt.getSubject()), shopId);
    }

    @PutMapping("/settings")
    public MerchantDtos.MerchantSettingsResponse updateSettings(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long shopId,
        @Valid @RequestBody MerchantDtos.MerchantSettingsUpdateRequest request
    ) {
        return shopSettings.update(UUID.fromString(jwt.getSubject()), shopId, request);
    }

    @PutMapping("/settings/hours")
    public MerchantDtos.MerchantSettingsResponse updateHours(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long shopId,
        @Valid @RequestBody MerchantDtos.OpeningHoursRequest request
    ) {
        return shopSettings.updateHours(UUID.fromString(jwt.getSubject()), shopId, request);
    }

    @PostMapping(value = "/settings/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MerchantDtos.MerchantSettingsResponse uploadLogo(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long shopId,
        @RequestPart("file") MultipartFile file
    ) {
        return shopSettings.uploadLogo(UUID.fromString(jwt.getSubject()), shopId, file);
    }

    @PostMapping(value = "/settings/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MerchantDtos.MerchantSettingsResponse uploadCover(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long shopId,
        @RequestPart("file") MultipartFile file
    ) {
        return shopSettings.uploadCover(UUID.fromString(jwt.getSubject()), shopId, file);
    }
}
