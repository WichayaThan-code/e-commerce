package com.thaishopfun.catalog;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchant/shops/{shopId}")
public class MerchantProductController {
    private final ProductService products;
    private final ProductImportService imports;
    private final ProductImportJobService importJobs;

    public MerchantProductController(ProductService products, ProductImportService imports, ProductImportJobService importJobs) {
        this.products = products;
        this.imports = imports;
        this.importJobs = importJobs;
    }

    @RequireShopPermission("PRODUCT_READ")
    @GetMapping("/products")
    public CatalogDtos.PageResponse<CatalogDtos.MerchantProductResponse> list(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String brand,
        @RequestParam(required = false) String stock,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size
    ) {
        return products.list(RequestUser.id(jwt), shopId, q, status, categoryId, brand, stock, sort, page, size);
    }

    @RequireShopPermission("PRODUCT_READ")
    @GetMapping("/products/brands")
    public List<CatalogDtos.BrandOption> brands(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId) {
        return products.brands(RequestUser.id(jwt), shopId);
    }

    @RequireShopPermission("PRODUCT_READ")
    @GetMapping(value = "/products/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId) {
        var body = products.exportWorkbook(RequestUser.id(jwt), shopId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("products.xlsx").build().toString())
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }

    @RequireShopPermission("PRODUCT_IMPORT")
    @PostMapping(value = "/products/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CatalogDtos.ProductImportAccepted> importFile(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @RequestPart("file") MultipartFile file
    ) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IMPORT_EMPTY", "ไม่พบรายการสินค้าในไฟล์");
        }
        return ResponseEntity.accepted().body(importJobs.submit(RequestUser.id(jwt), shopId, file.getInputStream()));
    }

    @RequireShopPermission("PRODUCT_IMPORT")
    @GetMapping("/products/import/{jobId}")
    public CatalogDtos.ProductImportJobView importStatus(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID jobId
    ) {
        return importJobs.status(RequestUser.id(jwt), shopId, jobId);
    }

    @RequireShopPermission("PRODUCT_IMPORT")
    @PostMapping("/products/batch")
    public CatalogDtos.ProductImportResult batch(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @RequestBody List<CatalogDtos.ProductWriteRequest> requests
    ) {
        return imports.importBatch(RequestUser.id(jwt), shopId, requests);
    }

    @RequireShopPermission("PRODUCT_READ")
    @GetMapping("/products/{productId}")
    public CatalogDtos.MerchantProductResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID productId) {
        return products.get(RequestUser.id(jwt), shopId, productId);
    }

    @RequireShopPermission("INVENTORY_READ")
    @GetMapping("/products/{productId}/ledger")
    public List<CatalogDtos.LedgerEntryResponse> ledger(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID productId) {
        return products.ledger(RequestUser.id(jwt), shopId, productId);
    }

    @RequireShopPermission("PRODUCT_READ")
    @GetMapping("/products/{productId}/images/{imageId}")
    public ResponseEntity<Resource> image(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID productId, @PathVariable UUID imageId) {
        var file = products.merchantImage(RequestUser.id(jwt), shopId, productId, imageId);
        var name = file.getFileName().toString().toLowerCase();
        var type = name.endsWith(".png") ? MediaType.IMAGE_PNG
            : name.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
            : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate()).body(new FileSystemResource(file));
    }

    @RequireShopPermission("PRODUCT_CREATE")
    @PostMapping("/products")
    public CatalogDtos.MerchantProductResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @RequestBody CatalogDtos.ProductWriteRequest request) {
        return products.create(RequestUser.id(jwt), shopId, request);
    }

    /** Helper for the variant form: every combination of the option groups with a suggested SKU code. Saves nothing. */
    @RequireShopPermission("PRODUCT_READ")
    @PostMapping("/products/variant-combinations")
    public List<CatalogDtos.VariantCombination> variantCombinations(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @RequestBody CatalogDtos.VariantCombinationRequest request) {
        return products.variantCombinations(RequestUser.id(jwt), shopId, request);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @PutMapping("/products/{productId}")
    public CatalogDtos.MerchantProductResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID productId, @RequestBody CatalogDtos.ProductWriteRequest request) {
        return products.update(RequestUser.id(jwt), shopId, productId, request);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @PostMapping("/products/{productId}/translations/use-auto")
    public CatalogDtos.MerchantProductResponse useAutoTranslation(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID productId,
        @RequestBody CatalogDtos.RevertAutoTranslationRequest request
    ) {
        return products.revertAutoTranslation(RequestUser.id(jwt), shopId, productId, request);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @PostMapping("/products/{productId}/publish")
    public CatalogDtos.MerchantProductResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID productId) {
        return products.publish(RequestUser.id(jwt), shopId, productId);
    }

    @RequireShopPermission("PRODUCT_ARCHIVE")
    @PostMapping("/products/{productId}/archive")
    public CatalogDtos.MerchantProductResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID productId) {
        return products.archive(RequestUser.id(jwt), shopId, productId);
    }

    @RequireShopPermission("PRODUCT_ARCHIVE")
    @PostMapping("/products/{productId}/unarchive")
    public CatalogDtos.MerchantProductResponse unarchive(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID productId) {
        return products.unarchive(RequestUser.id(jwt), shopId, productId);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @PostMapping(value = "/products/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CatalogDtos.MerchantProductResponse addImage(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID productId, @RequestPart("file") MultipartFile file) {
        return products.addImage(RequestUser.id(jwt), shopId, productId, file);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @DeleteMapping("/products/{productId}/images/{imageId}")
    public CatalogDtos.MerchantProductResponse deleteImage(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID productId, @PathVariable UUID imageId) {
        return products.deleteImage(RequestUser.id(jwt), shopId, productId, imageId);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @PutMapping("/products/{productId}/images/order")
    public CatalogDtos.MerchantProductResponse orderImages(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID productId, @RequestBody CatalogDtos.ImageOrderRequest request) {
        return products.orderImages(RequestUser.id(jwt), shopId, productId, request);
    }

    @GetMapping("/skus/check")
    @RequireShopPermission("PRODUCT_UPDATE")
    public CatalogDtos.SkuCodeCheck checkSkus(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @RequestParam(required = false) String codes,
        @RequestParam(required = false) UUID excludeProductId
    ) {
        return products.checkSkuCodes(RequestUser.id(jwt), shopId, codes, excludeProductId);
    }

    @PostMapping("/products/{productId}/stock-adjustments/bulk")
    @RequireShopPermission("INVENTORY_ADJUST")
    public List<CatalogDtos.StockResponse> adjustStockBulk(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID productId,
        @RequestBody CatalogDtos.BulkStockRequest request
    ) {
        return products.adjustStockBulk(RequestUser.id(jwt), shopId, productId, request);
    }

    @RequireShopPermission("INVENTORY_ADJUST")
    @PostMapping("/skus/{skuId}/stock-adjustments")
    public CatalogDtos.StockResponse adjustStock(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID skuId, @RequestBody CatalogDtos.StockAdjustRequest request) {
        return products.adjustStock(RequestUser.id(jwt), shopId, skuId, request);
    }
}
