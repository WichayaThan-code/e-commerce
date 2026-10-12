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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        var skuCreated = 0;
        for (var group : groupRows(rows)) {
            var outcome = importGroup(products, userId, shopId, group, catalog);
            if (outcome.created()) {
                created++;
                skuCreated += outcome.skuCreated();
            }
            errors.addAll(outcome.errors());
        }
        return new CatalogDtos.ProductImportResult(created, errors.size(), errors, skuCreated);
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
        var skuCreated = 0;
        for (var index = 0; index < requests.size(); index++) {
            try {
                products.create(userId, shopId, requests.get(index));
                created++;
                var skus = requests.get(index).skus();
                skuCreated += skus == null || skus.isEmpty() ? 1 : skus.size();
            } catch (AuthException exception) {
                errors.add(new CatalogDtos.ProductImportError(index + 1, exception.getMessage()));
            }
        }
        return new CatalogDtos.ProductImportResult(created, errors.size(), errors, skuCreated);
    }

    record RowOutcome(boolean created, CatalogDtos.ProductImportError error) {}

    record GroupOutcome(boolean created, int skuCreated, int rowCount, List<CatalogDtos.ProductImportError> errors) {}

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

    static List<List<ProductSpreadsheet.SheetRow>> groupRows(List<ProductSpreadsheet.SheetRow> rows) {
        var groups = new LinkedHashMap<String, List<ProductSpreadsheet.SheetRow>>();
        for (var row : rows) {
            var key = blank(row.productKey()) ? "row:" + row.row() : "key:" + row.productKey().trim();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        return List.copyOf(groups.values());
    }

    static GroupOutcome importGroup(ProductService products, UUID userId, Long shopId, List<ProductSpreadsheet.SheetRow> rows, List<ProductCategory> catalog) {
        if (rows.size() == 1 && blank(rows.get(0).productKey())) {
            var outcome = importRow(products, userId, shopId, rows.get(0), catalog);
            var errors = outcome.error() == null ? List.<CatalogDtos.ProductImportError>of() : List.of(outcome.error());
            return new GroupOutcome(outcome.created(), outcome.created() ? 1 : 0, 1, errors);
        }
        var blockers = new ArrayList<CatalogDtos.ProductImportError>();
        var warnings = new ArrayList<CatalogDtos.ProductImportError>();
        CatalogDtos.ProductWriteRequest request;
        try {
            request = toGroupRequest(rows, catalog, blockers, warnings);
        } catch (IllegalArgumentException exception) {
            blockers.add(issue(rows, null, "IMPORT_INVALID", exception.getMessage()));
            request = null;
        }
        if (request == null || !blockers.isEmpty()) {
            blockers.addAll(warnings);
            return new GroupOutcome(false, 0, rows.size(), blockers);
        }
        List<List<ProductSpreadsheet.EmbeddedImage>> pictures;
        try {
            pictures = picturesFor(rows);
        } catch (RowFailure exception) {
            blockers.add(new CatalogDtos.ProductImportError(exception.row, coded(exception.code, exception.getMessage())));
            blockers.addAll(warnings);
            return new GroupOutcome(false, 0, rows.size(), blockers);
        }
        var unique = new ArrayList<ProductSpreadsheet.EmbeddedImage>();
        var firstImage = new int[rows.size()];
        Arrays.fill(firstImage, -1);
        for (var index = 0; index < pictures.size(); index++) {
            var images = pictures.get(index);
            if (images.isEmpty()) continue;
            for (var image : images) rememberImage(unique, image);
            firstImage[index] = sameImage(unique, images.get(0));
        }
        if (unique.size() > ProductSpreadsheet.MAX_IMAGES) {
            blockers.add(issue(rows, null, "IMAGE_LIMIT", "รูปได้ไม่เกิน 9 รูปต่อสินค้า"));
            blockers.addAll(warnings);
            return new GroupOutcome(false, 0, rows.size(), blockers);
        }
        CatalogDtos.MerchantProductResponse saved;
        try {
            saved = products.create(userId, shopId, request);
        } catch (AuthException exception) {
            blockers.addAll(fromAuth(rows, exception));
            blockers.addAll(warnings);
            return new GroupOutcome(false, 0, rows.size(), blockers);
        }
        try {
            if (!unique.isEmpty()) {
                var imageIds = new ArrayList<UUID>();
                for (var image : unique) imageIds.add(products.addImportedImage(userId, shopId, saved.id(), image.data()));
                products.update(userId, shopId, saved.id(), withImages(request, saved, imageIds, firstImage));
            }
        } catch (AuthException | IllegalArgumentException exception) {
            warnings.add(0, new CatalogDtos.ProductImportError(rows.get(0).row(), "บันทึกสินค้าแล้ว แต่ใส่รูปไม่สำเร็จ: " + exception.getMessage()));
        }
        return new GroupOutcome(true, request.skus().size(), rows.size(), warnings);
    }

    private static CatalogDtos.ProductWriteRequest toGroupRequest(
        List<ProductSpreadsheet.SheetRow> rows,
        List<ProductCategory> catalog,
        List<CatalogDtos.ProductImportError> blockers,
        List<CatalogDtos.ProductImportError> warnings
    ) {
        var head = rows.get(0);
        if (blank(head.nameTh())) blockers.add(issue(rows, head.row(), "NAME_REQUIRED", "กรุณาระบุชื่อสินค้า"));
        for (var row : rows) {
            if (blank(row.skuCode())) blockers.add(issue(rows, row.row(), "SKU_CODE_REQUIRED", "กรุณาระบุรหัสสินค้า"));
            if (row.price() == null || row.price().signum() < 0) blockers.add(issue(rows, row.row(), "PRICE_INVALID", "กรุณาระบุราคาที่ไม่ติดลบ"));
            if (row.price() != null && row.compareAtPrice() != null && row.compareAtPrice().compareTo(row.price()) <= 0) {
                blockers.add(issue(rows, row.row(), "COMPARE_PRICE_INVALID", "ราคาเปรียบเทียบต้องมากกว่าราคาขาย"));
            }
            if (row != head) warnIfDifferent(head, row, warnings);
        }
        var variable = variable(rows);
        var schema = variable ? schema(rows, blockers) : List.<OptionSlot>of();
        if (!blockers.isEmpty()) return null;
        var skus = new ArrayList<CatalogDtos.SkuWriteRequest>();
        for (var row : rows) {
            var values = variable ? optionValues(schema, row) : Map.<String, String>of();
            var label = !blank(row.optionLabel()) ? row.optionLabel().trim() : values.isEmpty() ? null : String.join(" / ", values.values());
            skus.add(new CatalogDtos.SkuWriteRequest(
                null, row.skuCode().trim(), blank(row.barcode()) ? null : row.barcode().trim(), label, values,
                row.price(), null, row.compareAtPrice(), row.weightGrams(), row.stock(), null, null
            ));
        }
        return new CatalogDtos.ProductWriteRequest(
            head.nameTh().trim(), head.nameEn(), head.nameZh(),
            head.descriptionTh(), head.descriptionEn(), head.descriptionZh(),
            categoryId(head, catalog), null, variable ? "VARIABLE" : "SIMPLE", head.brand(), head.price(), head.compareAtPrice(), "THB", head.weightGrams(),
            skus, variable ? schema.stream().map(slot -> new CatalogDtos.OptionGroup(slot.name, slot.values)).toList() : null, null
        );
    }

    private static boolean variable(List<ProductSpreadsheet.SheetRow> rows) {
        for (var row : rows) {
            if (!blank(row.productType())) return "VARIABLE".equalsIgnoreCase(row.productType().trim());
        }
        return rows.size() > 1 || rows.stream().anyMatch(ProductImportService::hasOption);
    }

    private static boolean hasOption(ProductSpreadsheet.SheetRow row) {
        return !blank(row.option1Name()) || !blank(row.option2Name()) || !blank(row.option3Name());
    }

    private static List<OptionSlot> schema(List<ProductSpreadsheet.SheetRow> rows, List<CatalogDtos.ProductImportError> blockers) {
        var names = new String[3];
        var values = new ArrayList<LinkedHashSet<String>>();
        for (var index = 0; index < 3; index++) values.add(new LinkedHashSet<>());
        for (var row : rows) {
            var pairs = new String[][] {
                {row.option1Name(), row.option1Value()},
                {row.option2Name(), row.option2Value()},
                {row.option3Name(), row.option3Value()}
            };
            for (var index = 0; index < 3; index++) {
                var name = blank(pairs[index][0]) ? null : pairs[index][0].trim();
                var value = blank(pairs[index][1]) ? null : pairs[index][1].trim();
                if (name != null) {
                    if (names[index] == null) names[index] = name;
                    else if (!names[index].equals(name)) blockers.add(issue(List.of(row), row.row(), "OPTION_SCHEMA_INVALID", "ชื่อกลุ่มตัวเลือกไม่ตรงกับแถวแรกของกลุ่ม"));
                }
                if (value != null && (name != null || names[index] != null)) values.get(index).add(value);
            }
        }
        var groups = new ArrayList<OptionSlot>();
        for (var index = 0; index < 3; index++) {
            if (names[index] != null) groups.add(new OptionSlot(index, names[index], List.copyOf(values.get(index))));
        }
        return groups;
    }

    private static Map<String, String> optionValues(List<OptionSlot> schema, ProductSpreadsheet.SheetRow row) {
        var slots = new String[] {row.option1Value(), row.option2Value(), row.option3Value()};
        var values = new LinkedHashMap<String, String>();
        for (var slot : schema) {
            if (!blank(slots[slot.index])) values.put(slot.name, slots[slot.index].trim());
        }
        return values;
    }

    private record OptionSlot(int index, String name, List<String> values) {}

    private static void warnIfDifferent(ProductSpreadsheet.SheetRow head, ProductSpreadsheet.SheetRow row, List<CatalogDtos.ProductImportError> warnings) {
        var fields = new ArrayList<String>();
        differ(fields, "nameTh", head.nameTh(), row.nameTh());
        differ(fields, "nameEn", head.nameEn(), row.nameEn());
        differ(fields, "nameZh", head.nameZh(), row.nameZh());
        differ(fields, "descriptionTh", head.descriptionTh(), row.descriptionTh());
        differ(fields, "descriptionEn", head.descriptionEn(), row.descriptionEn());
        differ(fields, "descriptionZh", head.descriptionZh(), row.descriptionZh());
        differ(fields, "category", head.category(), row.category());
        differ(fields, "brand", head.brand(), row.brand());
        if (!fields.isEmpty()) {
            warnings.add(new CatalogDtos.ProductImportError(row.row(), coded("PRODUCT_FIELD_MISMATCH", "ข้อมูลสินค้าไม่ตรงกับแถวแรกของกลุ่ม: " + String.join(", ", fields))));
        }
    }

    private static void differ(List<String> fields, String name, String head, String value) {
        if (blank(value)) return;
        var left = head == null ? "" : head.trim();
        if (!left.equals(value.trim())) fields.add(name);
    }

    private static Long categoryId(ProductSpreadsheet.SheetRow row, List<ProductCategory> catalog) {
        if (blank(row.category())) return null;
        var name = row.category().trim();
        return catalog.stream()
            .filter(category -> name.equalsIgnoreCase(category.getNameTh())
                || name.equalsIgnoreCase(category.getNameEn())
                || name.equalsIgnoreCase(category.getNameZh()))
            .map(ProductCategory::getId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("ไม่พบหมวดสินค้า " + name));
    }

    private static List<List<ProductSpreadsheet.EmbeddedImage>> picturesFor(List<ProductSpreadsheet.SheetRow> rows) {
        var pictures = new ArrayList<List<ProductSpreadsheet.EmbeddedImage>>();
        for (var row : rows) {
            try {
                pictures.add(imagesFor(row));
            } catch (IllegalArgumentException exception) {
                throw new RowFailure(row.row(), exception.getMessage().contains("9") ? "IMAGE_LIMIT" : "IMAGE_INVALID", exception.getMessage());
            }
        }
        return pictures;
    }

    private static void rememberImage(List<ProductSpreadsheet.EmbeddedImage> unique, ProductSpreadsheet.EmbeddedImage image) {
        if (sameImage(unique, image) < 0) unique.add(image);
    }

    private static int sameImage(List<ProductSpreadsheet.EmbeddedImage> images, ProductSpreadsheet.EmbeddedImage image) {
        for (var index = 0; index < images.size(); index++) {
            var candidate = images.get(index);
            if (candidate.contentType().equals(image.contentType()) && Arrays.equals(candidate.data(), image.data())) return index;
        }
        return -1;
    }

    private static CatalogDtos.ProductWriteRequest withImages(
        CatalogDtos.ProductWriteRequest request,
        CatalogDtos.MerchantProductResponse saved,
        List<UUID> imageIds,
        int[] firstImage
    ) {
        var skus = new ArrayList<CatalogDtos.SkuWriteRequest>();
        for (var index = 0; index < request.skus().size(); index++) {
            var sku = request.skus().get(index);
            var match = saved.skus().stream().filter(item -> item.skuCode().equals(sku.skuCode())).findFirst().orElse(null);
            if (match == null) throw new IllegalArgumentException("ไม่พบรหัสสินค้า " + sku.skuCode());
            var imageId = firstImage[index] < 0 ? null : imageIds.get(firstImage[index]);
            skus.add(new CatalogDtos.SkuWriteRequest(
                match.id(), sku.skuCode(), sku.barcode(), sku.optionLabel(), sku.optionValues(),
                sku.price(), null, sku.compareAtPrice(), sku.weightGrams(), null, imageId, sku.active()
            ));
        }
        return new CatalogDtos.ProductWriteRequest(
            request.nameTh(), request.nameEn(), request.nameZh(),
            request.descriptionTh(), request.descriptionEn(), request.descriptionZh(),
            request.categoryId(), request.slug(), request.productType(), request.brand(),
            request.basePrice(), request.compareAtPrice(), request.currency(), request.weightGrams(),
            skus, request.optionSchema(), null
        );
    }

    private static List<CatalogDtos.ProductImportError> fromAuth(List<ProductSpreadsheet.SheetRow> rows, AuthException exception) {
        var issues = exception.getFieldErrors();
        if (issues == null || issues.isEmpty()) return List.of(issue(rows, null, exception.getCode(), exception.getMessage()));
        var errors = new ArrayList<CatalogDtos.ProductImportError>();
        for (var field : issues) errors.add(issue(rows, rowForField(field.field(), rows), field.code(), field.message()));
        return errors;
    }

    private static Integer rowForField(String field, List<ProductSpreadsheet.SheetRow> rows) {
        if (field == null) return null;
        Matcher matcher = Pattern.compile("skus\\[(\\d+)]").matcher(field);
        if (!matcher.find()) return null;
        var index = Integer.parseInt(matcher.group(1));
        if (index < 0 || index >= rows.size()) return null;
        return rows.get(index).row();
    }

    private static CatalogDtos.ProductImportError issue(List<ProductSpreadsheet.SheetRow> rows, Integer row, String code, String message) {
        var excelRow = row == null ? rows.get(0).row() : row;
        var text = coded(code, message);
        if (row == null && rows.size() > 1) {
            text = "แถว " + rows.get(0).row() + "-" + rows.get(rows.size() - 1).row() + ": " + text;
        }
        return new CatalogDtos.ProductImportError(excelRow, text);
    }

    private static String coded(String code, String message) {
        if (code == null || code.isBlank()) return message;
        return code + ": " + message;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class RowFailure extends RuntimeException {
        private final int row;
        private final String code;

        private RowFailure(int row, String code, String message) {
            super(message);
            this.row = row;
            this.code = code;
        }
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
            row.price(), null, row.compareAtPrice(), row.weightGrams(), row.stock(), null, null
        );
        return new CatalogDtos.ProductWriteRequest(
            row.nameTh().trim(), row.nameEn(), row.nameZh(),
            row.descriptionTh(), row.descriptionEn(), row.descriptionZh(),
            categoryId, null, "SIMPLE", row.brand(), row.price(), row.compareAtPrice(), "THB", row.weightGrams(),
            List.of(sku), null, null
        );
    }
}