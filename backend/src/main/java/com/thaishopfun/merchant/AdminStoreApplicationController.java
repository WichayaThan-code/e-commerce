package com.thaishopfun.merchant;

import com.thaishopfun.admin.AdminPlatformDtos;
import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/store-applications")
public class AdminStoreApplicationController {
    private final StoreReviewService reviews;

    public AdminStoreApplicationController(StoreReviewService reviews) {
        this.reviews = reviews;
    }

    @RequirePermission("PLATFORM_APPLICATION_READ")
    @GetMapping
    public List<MerchantDtos.AdminStoreApplicationResponse> list() {
        return reviews.list();
    }

    @RequirePermission("PLATFORM_APPLICATION_READ")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"store-applications.xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(reviews.export());
    }

    @RequirePermission("PLATFORM_APPLICATION_READ")
    @GetMapping("/{shopId}/documents/{documentId}")
    public ResponseEntity<Resource> document(@PathVariable Long shopId, @PathVariable UUID documentId) {
        var filename = reviews.documentFilename(shopId, documentId);
        var encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        var lower = filename.toLowerCase();
        var type = lower.endsWith(".png") ? MediaType.IMAGE_PNG
            : lower.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
            : lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? MediaType.IMAGE_JPEG
            : lower.endsWith(".pdf") ? MediaType.APPLICATION_PDF
            : MediaType.APPLICATION_OCTET_STREAM;
        var inline = type.getType().equals("image");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? "inline" : "attachment") + "; filename=\"document\"; filename*=UTF-8''" + encoded)
            .contentType(type)
            .body(new FileSystemResource(reviews.documentFile(shopId, documentId)));
    }

    @RequirePermission("PLATFORM_APPLICATION_REVIEW")
    @PostMapping("/bulk/approve")
    public MerchantDtos.BulkShopResponse approveMany(@Valid @RequestBody MerchantDtos.BulkShopRequest request, @AuthenticationPrincipal Jwt jwt) {
        return reviews.approveMany(request.shopIds(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_APPLICATION_REVIEW")
    @PostMapping("/bulk/reject")
    public MerchantDtos.BulkShopResponse rejectMany(@Valid @RequestBody MerchantDtos.BulkShopRequest request, @AuthenticationPrincipal Jwt jwt) {
        return reviews.rejectMany(request.shopIds(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/bulk/suspend")
    public MerchantDtos.BulkShopResponse suspendMany(@Valid @RequestBody MerchantDtos.BulkShopRequest request, @AuthenticationPrincipal Jwt jwt) {
        return reviews.suspendMany(request.shopIds(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_SHOP_RESTORE")
    @PostMapping("/bulk/reactivate")
    public MerchantDtos.BulkShopResponse reactivateMany(@Valid @RequestBody MerchantDtos.BulkShopRequest request, @AuthenticationPrincipal Jwt jwt) {
        return reviews.reactivateMany(request.shopIds(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_APPLICATION_REVIEW")
    @PostMapping("/{shopId}/approve")
    public MerchantDtos.AdminStoreApplicationResponse approve(@PathVariable Long shopId, @AuthenticationPrincipal Jwt jwt) {
        return reviews.approve(shopId, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_APPLICATION_REVIEW")
    @PostMapping("/{shopId}/reject")
    public MerchantDtos.AdminStoreApplicationResponse reject(@PathVariable Long shopId, @AuthenticationPrincipal Jwt jwt) {
        return reviews.reject(shopId, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/{shopId}/suspend")
    public MerchantDtos.AdminStoreApplicationResponse suspend(@PathVariable Long shopId, @AuthenticationPrincipal Jwt jwt,
                                                              @Valid @RequestBody(required = false) AdminPlatformDtos.OptionalReasonRequest body) {
        return reviews.suspend(shopId, RequestUser.id(jwt), body == null ? null : body.reason());
    }

    @RequirePermission("PLATFORM_SHOP_RESTORE")
    @PostMapping("/{shopId}/reactivate")
    public MerchantDtos.AdminStoreApplicationResponse reactivate(@PathVariable Long shopId, @AuthenticationPrincipal Jwt jwt,
                                                                 @Valid @RequestBody(required = false) AdminPlatformDtos.OptionalReasonRequest body) {
        return reviews.reactivate(shopId, RequestUser.id(jwt), body == null ? null : body.reason());
    }
}
