package com.thaishopfun.catalog;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.MerchantStorageService;
import com.thaishopfun.merchant.ProductCategory;
import com.thaishopfun.merchant.ProductCategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ProductImportService {
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    private static final ExecutorService IMAGE_FETCHES = Executors.newFixedThreadPool(4, runnable -> {
        var thread = new Thread(runnable, "product-import-image");
        thread.setDaemon(true);
        return thread;
    });
    private final ProductService products;
    private final ProductCategoryRepository categories;

    public ProductImportService(ProductService products, ProductCategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    public CatalogDtos.ProductImportResult importWorkbook(UUID userId, Long shopId, InputStream input) {
        List<ProductSpreadsheet.SheetRow> rows;
        try {
            rows = ProductSpreadsheet.read(input);
        } catch (IllegalArgumentException exception) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IMPORT_INVALID", exception.getMessage());
        }
        if (rows.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IMPORT_EMPTY", "ไม่พบรายการสินค้าในไฟล์");
        }
        var catalog = categories.findByActiveTrueOrderBySortOrderAsc();
        var errors = new ArrayList<CatalogDtos.ProductImportError>();
        var created = 0;
        for (var row : rows) {
            var outcome = importRow(products, userId, shopId, row, catalog);
            if (outcome.created()) created++;
            if (outcome.error() != null) errors.add(outcome.error());
        }
        return new CatalogDtos.ProductImportResult(created, errors.size(), errors);
    }

    public CatalogDtos.ProductImportResult importBatch(UUID userId, Long shopId, List<CatalogDtos.ProductWriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IMPORT_EMPTY", "ไม่พบรายการสินค้า");
        }
        if (requests.size() > ProductSpreadsheet.MAX_ROWS) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IMPORT_INVALID", "นำเข้าได้ไม่เกิน " + ProductSpreadsheet.MAX_ROWS + " รายการต่อครั้ง");
        }
        var errors = new ArrayList<CatalogDtos.ProductImportError>();
        var created = 0;
        for (var index = 0; index < requests.size(); index++) {
            try {
                products.create(userId, shopId, requests.get(index));
                created++;
            } catch (AuthException exception) {
                errors.add(new CatalogDtos.ProductImportError(index + 1, exception.getMessage()));
            }
        }
        return new CatalogDtos.ProductImportResult(created, errors.size(), errors);
    }

    record RowOutcome(boolean created, CatalogDtos.ProductImportError error) {}

    static RowOutcome importRow(ProductService products, UUID userId, Long shopId, ProductSpreadsheet.SheetRow row, List<ProductCategory> catalog) {
        try {
            var pictures = imagesFor(row);
            var saved = products.create(userId, shopId, toRequest(row, catalog));
            try {
                for (var picture : pictures) products.addImportedImage(userId, shopId, saved.id(), picture.data());
            } catch (AuthException exception) {
                return new RowOutcome(true, new CatalogDtos.ProductImportError(row.row(), "บันทึกสินค้าแล้ว แต่ใส่รูปไม่สำเร็จ: " + exception.getMessage()));
            }
            return new RowOutcome(true, null);
        } catch (AuthException | IllegalArgumentException exception) {
            return new RowOutcome(false, new CatalogDtos.ProductImportError(row.row(), exception.getMessage()));
        }
    }

    static List<ProductSpreadsheet.EmbeddedImage> imagesFor(ProductSpreadsheet.SheetRow row) {
        if (row.imageError() != null) throw new IllegalArgumentException(row.imageError());
        var pictures = new ArrayList<>(row.pictures());
        var urls = new ArrayList<String>();
        var raw = row.image();
        var rejected = false;
        if (raw != null && !raw.isBlank()) {
            for (var token : raw.split("[\\n|;,]+")) {
                var value = token.trim();
                if (value.isEmpty() || value.toUpperCase(Locale.ROOT).contains("DISPIMG")) continue;
                if (value.startsWith("http://") || value.startsWith("https://")) {
                    urls.add(value);
                    continue;
                }
                rejected = true;
            }
        }
        if (rejected && pictures.isEmpty() && urls.isEmpty()) {
            throw new IllegalArgumentException("คอลัมน์ image ใส่ลิงก์รูป http(s) หรือวางรูป JPG, PNG, WEBP ในแถวสินค้า");
        }
        pictures.addAll(downloadAll(urls));
        if (pictures.size() > ProductSpreadsheet.MAX_IMAGES) {
            throw new IllegalArgumentException("รูปได้ไม่เกิน 9 รูปต่อสินค้า");
        }
        return pictures;
    }

    private static List<ProductSpreadsheet.EmbeddedImage> downloadAll(List<String> urls) {
        if (urls.isEmpty()) return List.of();
        var futures = new ArrayList<CompletableFuture<ProductSpreadsheet.EmbeddedImage>>(urls.size());
        for (var url : urls) futures.add(CompletableFuture.supplyAsync(() -> download(url), IMAGE_FETCHES));
        var images = new ArrayList<ProductSpreadsheet.EmbeddedImage>(urls.size());
        for (var future : futures) {
            try {
                images.add(future.join());
            } catch (CompletionException exception) {
                var cause = exception.getCause();
                if (cause instanceof IllegalArgumentException illegal) throw illegal;
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IllegalArgumentException("ดาวน์โหลดรูปไม่สำเร็จ", cause);
            }
        }
        return images;
    }

    private static ProductSpreadsheet.EmbeddedImage download(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("ลิงก์รูปไม่ถูกต้อง");
        }
        var scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")) || uri.getUserInfo() != null || uri.getHost() == null) {
            throw new IllegalArgumentException("ลิงก์รูปต้องขึ้นต้นด้วย http หรือ https");
        }
        try {
            for (var address : InetAddress.getAllByName(uri.getHost())) {
                if (blocked(address)) throw new IllegalArgumentException("ลิงก์รูปต้องเป็นที่อยู่สาธารณะ");
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("ไม่พบเซิร์ฟเวอร์ของลิงก์รูป");
        }
        try {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("User-Agent", "ThaiShopFun").GET().build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("ดาวน์โหลดรูปไม่สำเร็จ");
            }
            try (var input = response.body()) {
                var data = input.readNBytes(MAX_IMAGE_BYTES + 1);
                if (data.length > MAX_IMAGE_BYTES) throw new IllegalArgumentException("รูปต้องมีขนาดไม่เกิน 5 MB");
                var contentType = MerchantStorageService.sniffedImageType(data);
                if (contentType == null) throw new IllegalArgumentException("รองรับเฉพาะรูป JPG, PNG หรือ WEBP");
                return new ProductSpreadsheet.EmbeddedImage(contentType, data);
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("ดาวน์โหลดรูปไม่สำเร็จ");
        } catch (IOException exception) {
            throw new IllegalArgumentException("ดาวน์โหลดรูปไม่สำเร็จ");
        }
    }

    private static boolean blocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address v6) {
            var first = v6.getAddress()[0] & 255;
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }

    static CatalogDtos.ProductWriteRequest toRequest(ProductSpreadsheet.SheetRow row, List<ProductCategory> catalog) {
        if (row.nameTh() == null || row.nameTh().isBlank()) {
            throw new IllegalArgumentException("กรุณาระบุชื่อสินค้า");
        }
        if (row.skuCode() == null || row.skuCode().isBlank()) {
            throw new IllegalArgumentException("กรุณาระบุรหัสสินค้า");
        }
        if (row.price() == null) {
            throw new IllegalArgumentException("กรุณาระบุราคา");
        }
        Long categoryId = null;
        if (row.category() != null && !row.category().isBlank()) {
            var name = row.category().trim();
            categoryId = catalog.stream()
                .filter(category -> name.equalsIgnoreCase(category.getNameTh())
                    || name.equalsIgnoreCase(category.getNameEn())
                    || name.equalsIgnoreCase(category.getNameZh()))
                .map(ProductCategory::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบหมวดสินค้า " + name));
        }
        var sku = new CatalogDtos.SkuWriteRequest(
            null, row.skuCode().trim(), row.barcode(), row.optionLabel(), Map.of(),
            row.price(), null, row.compareAtPrice(), row.weightGrams(), row.stock()
        );
        return new CatalogDtos.ProductWriteRequest(
            row.nameTh().trim(), row.nameEn(), row.nameZh(),
            row.descriptionTh(), row.descriptionEn(), row.descriptionZh(),
            categoryId, null, "SIMPLE", row.brand(), row.price(), row.compareAtPrice(), "THB", row.weightGrams(),
            List.of(sku)
        );
    }
}