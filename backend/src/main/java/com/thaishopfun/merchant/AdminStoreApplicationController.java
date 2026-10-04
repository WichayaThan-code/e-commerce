package com.thaishopfun.merchant;

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

    @GetMapping
    public List<MerchantDtos.AdminStoreApplicationResponse> list() {
        return reviews.list();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"store-applications.xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(reviews.export());
    }

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

    @PostMapping("/bulk/approve")
    public MerchantDtos.BulkShopResponse approveMany(@Valid @RequestBody MerchantDtos.BulkShopRequest request) {
        return reviews.approveMany(request.shopIds());
    }

    @PostMapping("/bulk/reject")
    public MerchantDtos.BulkShopResponse rejectMany(@Valid @RequestBody MerchantDtos.BulkShopRequest request) {
        return reviews.rejectMany(request.shopIds());
    }

    @PostMapping("/bulk/suspend")
    public MerchantDtos.BulkShopResponse suspendMany(@Valid @RequestBody MerchantDtos.BulkShopRequest request) {
        return reviews.suspendMany(request.shopIds());
    }

    @PostMapping("/bulk/reactivate")
    public MerchantDtos.BulkShopResponse reactivateMany(@Valid @RequestBody MerchantDtos.BulkShopRequest request) {
        return reviews.reactivateMany(request.shopIds());
    }

    @PostMapping("/{shopId}/approve")
    public MerchantDtos.AdminStoreApplicationResponse approve(@PathVariable Long shopId) {
        return reviews.approve(shopId);
    }

    @PostMapping("/{shopId}/reject")
    public MerchantDtos.AdminStoreApplicationResponse reject(@PathVariable Long shopId) {
        return reviews.reject(shopId);
    }

    @PostMapping("/{shopId}/suspend")
    public MerchantDtos.AdminStoreApplicationResponse suspend(@PathVariable Long shopId) {
        return reviews.suspend(shopId);
    }

    @PostMapping("/{shopId}/reactivate")
    public MerchantDtos.AdminStoreApplicationResponse reactivate(@PathVariable Long shopId) {
        return reviews.reactivate(shopId);
    }
}
