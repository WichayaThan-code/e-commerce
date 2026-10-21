package com.thaishopfun.catalog;

import com.thaishopfun.auth.AuthDtos;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.common.ValidationMessages;
import com.thaishopfun.merchant.MerchantStorageService;
import com.thaishopfun.merchant.ProductCategoryRepository;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.ShopStatus;
import com.thaishopfun.translation.ContentTranslationBridge;
import com.thaishopfun.translation.ContentTranslationIndex;
import com.thaishopfun.translation.TranslationEntityType;
import com.thaishopfun.translation.TranslationService;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {
    private static final int MAX_IMAGES = 9;
    static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_SKUS = 100;
    private final ShopRepository shops;
    private final ProductRepository products;
    private final ProductSkuRepository skus;
    private final ProductImageRepository images;
    private final InventoryLevelRepository levels;
    private final InventoryLedgerRepository ledger;
    private final ProductCategoryRepository categories;
    private final MerchantStorageService storage;
    private final EntityManager entities;
    private final ContentTranslationBridge translationBridge;
    private final TranslationService translations;
    private final AuthorizationService authorization;
    private final Validator validator;
    private final ProductVariantService variants;
    private final UserAccountRepository accounts;

    public ProductService(
        ShopRepository shops,
        ProductRepository products,
        ProductSkuRepository skus,
        ProductImageRepository images,
        InventoryLevelRepository levels,
        InventoryLedgerRepository ledger,
        ProductCategoryRepository categories,
        MerchantStorageService storage,
        EntityManager entities,
        ContentTranslationBridge translationBridge,
        TranslationService translations,
        AuthorizationService authorization,
        Validator validator,
        ProductVariantService variants,
        UserAccountRepository accounts
    ) {
        this.shops = shops;
        this.products = products;
        this.skus = skus;
        this.images = images;
        this.levels = levels;
        this.ledger = ledger;
        this.categories = categories;
        this.storage = storage;
        this.entities = entities;
        this.translationBridge = translationBridge;
        this.translations = translations;
        this.authorization = authorization;
        this.validator = validator;
        this.variants = variants;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public CatalogDtos.PageResponse<CatalogDtos.MerchantProductResponse> list(UUID userId, Long shopId, String q, String status, Long categoryId, String brand, String stock, String sort, Integer page, Integer size) {
        var shop = requireSellableShop(userId, shopId);
        var result = search(shop.getId(), q, parseStatus(status), categoryId, blank(brand), parseStock(stock), parseSort(sort), page, size, false, List.of());
        var ids = result.items().stream().map(Product::getId).toList();
        var rollup = rollup(ids);
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, ids.stream().map(UUID::toString).toList());
        return new CatalogDtos.PageResponse<>(result.items().stream().map(product -> toMerchant(product, index, rollup.get(product.getId()))).toList(), result.page(), result.size(), result.total());
    }

    /** Distinct brand names used by the shop's (non-deleted) products, case-insensitive like the brand filter. */
    @Transactional(readOnly = true)
    public List<CatalogDtos.BrandOption> brands(UUID userId, Long shopId) {
        var shop = requireSellableShop(userId, shopId);
        return entities.createQuery(
                "select min(trim(p.brand)), count(p) from Product p where p.shop.shopId = :shopId and p.deletedAt is null"
                    + " and p.brand is not null and trim(p.brand) <> '' group by lower(trim(p.brand)) order by lower(trim(p.brand))", Object[].class)
            .setParameter("shopId", shop.getId())
            .getResultList().stream()
            .map(row -> new CatalogDtos.BrandOption((String) row[0], ((Number) row[1]).longValue()))
            .toList();
    }

    @Transactional(readOnly = true)
    public byte[] importTemplate(UUID userId, Long shopId) {
        requireSellableShop(userId, shopId);
        return ProductSpreadsheet.template();
    }

    @Transactional(readOnly = true)
    public byte[] exportWorkbook(UUID userId, Long shopId) {
        var shop = requireSellableShop(userId, shopId);
        var items = products.findByShop_ShopIdAndDeletedAtIsNullOrderByCreatedDateTimeDesc(shop.getId());
        var rows = new ArrayList<ProductSpreadsheet.SheetRow>();
        for (var product : items) {
            var skuRows = skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId());
            var stock = skuRows.isEmpty() ? List.<InventoryLevel>of() : levels.findBySku_IdIn(skuRows.stream().map(ProductSku::getId).toList());
            if (skuRows.isEmpty()) {
                rows.add(sheetRow(product, null, null));
                continue;
            }
            for (var sku : skuRows) {
                var level = stock.stream().filter(item -> item.getSku().getId().equals(sku.getId())).findFirst().orElse(null);
                rows.add(sheetRow(product, sku, level));
            }
        }
        if (rows.isEmpty()) return ProductSpreadsheet.template();
        return ProductSpreadsheet.write(rows);
    }

    @Transactional(readOnly = true)
    public CatalogDtos.MerchantProductResponse get(UUID userId, Long shopId, UUID productId) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, List.of(product.getId().toString()));
        return toMerchant(product, index);
    }

    @Transactional
    public CatalogDtos.MerchantProductResponse create(UUID userId, Long shopId, CatalogDtos.ProductWriteRequest request) {
        var shop = requireSellableShop(userId, shopId);
        request = normalizeBarcodes(request);
        validate(shop.getId(), request, null);
        var type = requireType(request.productType());
        var name = requireName(request.nameTh());
        var product = new Product(shop, uniqueSlug(shop.getId(), request.slug(), request.nameEn(), name, null), name, type);
        product.stamp(userId);
        apply(product, request, userId);
        // Assigned UUID ids: repository.save() would merge() a copy and later changes to `product`
        // (e.g. fillBasePrice) would never reach the database. persist() keeps this instance managed.
        entities.persist(product);
        var synced = syncSkus(product, request, userId);
        variants.sync(product, request, synced.ordered(), userId);
        storeOptionSchema(product);
        fillBasePrice(product);
        applyOpeningStock(userId, shop.getId(), synced.opening());
        translationBridge.onProductSaved(product, request);
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, List.of(product.getId().toString()));
        return toMerchant(product, index);
    }

    @Transactional
    public CatalogDtos.MerchantProductResponse update(UUID userId, Long shopId, UUID productId, CatalogDtos.ProductWriteRequest request) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        request = normalizeBarcodes(request);
        validate(shopId, request, product);
        product.stamp(userId);
        apply(product, request, userId);
        product.setSlug(uniqueSlug(shopId, request.slug(), request.nameEn(), product.getNameTh(), product.getId()));
        var synced = syncSkus(product, request, userId);
        variants.sync(product, request, synced.ordered(), userId);
        storeOptionSchema(product);
        fillBasePrice(product);
        applyOpeningStock(userId, shopId, synced.opening());
        translationBridge.onProductSaved(product, request);
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, List.of(product.getId().toString()));
        return toMerchant(product, index);
    }

    /** All combinations of the given option groups with suggested SKU codes; nothing is saved. */
    @Transactional(readOnly = true)
    public List<CatalogDtos.VariantCombination> variantCombinations(UUID userId, Long shopId, CatalogDtos.VariantCombinationRequest request) {
        requireSellableShop(userId, shopId);
        return ProductVariantService.combinations(request);
    }

    @Transactional
    public CatalogDtos.MerchantProductResponse revertAutoTranslation(
        UUID userId,
        Long shopId,
        UUID productId,
        CatalogDtos.RevertAutoTranslationRequest request
    ) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        if (request == null || request.field() == null || request.locale() == null) {
            throw bad("TRANSLATION_INVALID", "ต้องระบุ field และ locale");
        }
        var thai = switch (request.field()) {
            case "name" -> product.getNameTh();
            case "description" -> product.getDescriptionTh();
            default -> throw bad("TRANSLATION_INVALID", "field ไม่รองรับ");
        };
        translations.revertToMachine(TranslationEntityType.PRODUCT, product.getId().toString(), request.field(), request.locale(), thai);
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, List.of(product.getId().toString()));
        return toMerchant(product, index);
    }

    @Transactional
    public CatalogDtos.MerchantProductResponse retryTranslation(
        UUID userId,
        Long shopId,
        UUID productId,
        CatalogDtos.RevertAutoTranslationRequest request
    ) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        if (request == null || request.field() == null || request.locale() == null) {
            throw bad("TRANSLATION_INVALID", "ต้องระบุ field และ locale");
        }
        var field = request.field().trim();
        var optionField = field.matches("n\\d+") || field.matches("v\\d+\\.\\d+");
        if (!optionField && !"name".equals(field) && !"description".equals(field)) {
            throw bad("TRANSLATION_INVALID", "field ไม่รองรับ");
        }
        var entity = optionField ? TranslationEntityType.PRODUCT_OPTION : TranslationEntityType.PRODUCT;
        try {
            translations.retry(entity, product.getId().toString(), field, request.locale().trim());
        } catch (TranslationService.RetryRejected rejected) {
            throw bad(rejected.code(), rejected.getMessage());
        }
        return merchantView(product);
    }

    @Transactional
    public CatalogDtos.MerchantProductResponse publish(UUID userId, Long shopId, UUID productId) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        if (product.getStatus() == ProductStatus.ARCHIVED) throw conflict("PRODUCT_ARCHIVED", "สินค้าที่เก็บถาวรต้องนำกลับมาก่อนเผยแพร่");
        var missing = new ArrayList<FieldViolation>();
        if (images.countByProduct_Id(product.getId()) < 1) missing.add(ValidationMessages.violation("images", "PUBLISH_IMAGE_REQUIRED"));
        if (product.getBasePrice() == null) missing.add(ValidationMessages.violation("basePrice", "PUBLISH_PRICE_REQUIRED"));
        if (skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId()).stream().noneMatch(ProductSku::isActive)) {
            missing.add(ValidationMessages.violation("skus", "PUBLISH_SKU_REQUIRED"));
        }
        if (!missing.isEmpty()) throw new ValidationException("PUBLISH_INCOMPLETE", missing);
        product.stamp(userId);
        product.setStatus(ProductStatus.PUBLISHED);
        product.setPublishedAt(Instant.now());
        product.setArchivedAt(null);
        return merchantView(product);
    }

    @Transactional
    public CatalogDtos.MerchantProductResponse archive(UUID userId, Long shopId, UUID productId) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        if (product.getStatus() == ProductStatus.ARCHIVED) return merchantView(product);
        product.stamp(userId);
        product.setStatus(ProductStatus.ARCHIVED);
        product.setArchivedAt(Instant.now());
        return merchantView(product);
    }

    @Transactional
    public CatalogDtos.MerchantProductResponse unarchive(UUID userId, Long shopId, UUID productId) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        if (product.getStatus() != ProductStatus.ARCHIVED) throw conflict("PRODUCT_NOT_ARCHIVED", "สินค้านนี้ไม่ได้อยู่ในสถานะเก็บถาวร");
        product.stamp(userId);
        product.setStatus(ProductStatus.DRAFT);
        product.setArchivedAt(null);
        return merchantView(product);
    }

    @Transactional
    public CatalogDtos.MerchantProductResponse addImage(UUID userId, Long shopId, UUID productId, MultipartFile file) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        if (images.countByProduct_Id(product.getId()) >= MAX_IMAGES) throw bad("IMAGE_LIMIT", "สินค้าหนึ่งรายการมีรูปได้ไม่เกิน 9 รูป");
        var name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp"))) {
            throw bad("IMAGE_TYPE", "รองรับเฉพาะไฟล์ JPG, PNG หรือ WEBP");
        }
        var stored = storage.store(product.getId(), file, "PRODUCT");
        deleteFileOnRollback(stored.path());
        rememberImage(userId, product, stored);
        return merchantView(product);
    }

    @Transactional
    public UUID addImportedImage(UUID userId, Long shopId, UUID productId, byte[] bytes) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        if (images.countByProduct_Id(product.getId()) >= MAX_IMAGES) throw bad("IMAGE_LIMIT", "สินค้าหนึ่งรายการมีรูปได้ไม่เกิน 9 รูป");
        var stored = storage.storeBytes(product.getId(), bytes, "PRODUCT");
        deleteFileOnRollback(stored.path());
        return rememberImage(userId, product, stored).getId();
    }

    private ProductImage rememberImage(UUID userId, Product product, MerchantStorageService.StoredFile stored) {
        if (stored.contentType() == null || !stored.contentType().startsWith("image/")) {
            storage.deleteQuietly(stored.path());
            throw bad("IMAGE_TYPE", "รองรับเฉพาะไฟล์ JPG, PNG หรือ WEBP");
        }
        var current = images.findByProduct_IdOrderBySortOrderAsc(product.getId());
        var image = new ProductImage(product, stored.path(), current.size(), current.stream().noneMatch(ProductImage::isPrimaryImage));
        image.stamp(userId);
        entities.persist(image);
        product.stamp(userId);
        return image;
    }

    /** A stored file whose DB row never commits must not stay on disk. */
    private void deleteFileOnRollback(String path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.deleteQuietly(path);
            }
        });
    }

    /** The file of a deleted image row is removed only once the delete has committed. */
    private void deleteFileAfterCommit(String path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.deleteQuietly(path);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.deleteQuietly(path);
            }
        });
    }

    @Transactional
    public CatalogDtos.MerchantProductResponse deleteImage(UUID userId, Long shopId, UUID productId, UUID imageId) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        var image = images.findByIdAndProduct_Id(imageId, product.getId()).orElseThrow(() -> notFound("IMAGE_NOT_FOUND", "ไม่พบรูปสินค้า"));
        if (product.getStatus() == ProductStatus.PUBLISHED && images.countByProduct_Id(product.getId()) <= 1) {
            throw new ValidationException("PUBLISHED_IMAGE_REQUIRED", List.of(new FieldViolation("images", "PUBLISHED_IMAGE_REQUIRED",
                "สินค้าที่เผยแพร่อยู่ต้องมีรูปอย่างน้อย 1 รูป กรุณาอัปโหลดรูปใหม่ก่อนลบรูปนี้ หรือเก็บสินค้าถาวรก่อน")));
        }
        var wasPrimary = image.isPrimaryImage();
        deleteFileAfterCommit(image.getStoragePath());
        product.stamp(userId);
        images.delete(image);
        images.flush();
        var remaining = images.findByProduct_IdOrderBySortOrderAsc(product.getId());
        for (var index = 0; index < remaining.size(); index++) remaining.get(index).setSortOrder(index);
        if (wasPrimary && !remaining.isEmpty()) remaining.get(0).setPrimaryImage(true);
        return merchantView(product);
    }

    @Transactional
    public CatalogDtos.MerchantProductResponse orderImages(UUID userId, Long shopId, UUID productId, CatalogDtos.ImageOrderRequest request) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        var current = images.findByProduct_IdOrderBySortOrderAsc(product.getId());
        var ordered = request == null ? null : request.imageIds();
        if (ordered == null || ordered.size() != current.size() || !new HashSet<>(ordered).equals(current.stream().map(ProductImage::getId).collect(java.util.stream.Collectors.toSet()))) {
            throw bad("IMAGE_ORDER", "ต้องส่งรหัสรูปทั้งหมดของสินค้านี้");
        }
        if (request.primaryImageId() == null || ordered.stream().noneMatch(id -> id.equals(request.primaryImageId()))) {
            throw bad("IMAGE_ORDER", "ต้องระบุรูปหลักจากรูปของสินค้านี้");
        }
        current.forEach(image -> image.setPrimaryImage(false));
        images.flush();
        for (var index = 0; index < ordered.size(); index++) {
            var id = ordered.get(index);
            var image = current.stream().filter(item -> item.getId().equals(id)).findFirst().orElseThrow();
            image.setSortOrder(index);
            image.setPrimaryImage(id.equals(request.primaryImageId()));
            image.stamp(userId);
        }
        return merchantView(product);
    }

    @Transactional
    public CatalogDtos.StockResponse adjustStock(UUID userId, Long shopId, UUID skuId, CatalogDtos.StockAdjustRequest request) {
        requireSellableShop(userId, shopId);
        var sku = skus.findByIdAndShop_ShopIdAndDeletedAtIsNull(skuId, shopId).orElseThrow(() -> notFound("SKU_NOT_FOUND", "ไม่พบรหัสสินค้า"));
        var type = request == null || request.entryType() == null ? "" : request.entryType().trim().toUpperCase(Locale.ROOT);
        if (!type.equals("RECEIVE") && !type.equals("ISSUE") && !type.equals("ADJUST") && !type.equals("RESERVE") && !type.equals("RELEASE")) {
            throw bad("INVALID_ADJUSTMENT", "ปรับสต็อกได้เฉพาะ RECEIVE, ISSUE, ADJUST, RESERVE หรือ RELEASE");
        }
        if ((type.equals("ISSUE") || type.equals("ADJUST") || type.equals("RESERVE") || type.equals("RELEASE")) && (request.reason() == null || request.reason().isBlank())) {
            throw bad("STOCK_REASON_REQUIRED", "กรุณาระบุเหตุผล");
        }
        if (type.equals("RECEIVE") && request.quantityDelta() <= 0) throw bad("INVALID_ADJUSTMENT", "รับเข้าต้องเป็นจำนวนมากกว่าศูนย์");
        if (type.equals("ISSUE") && request.quantityDelta() <= 0) throw bad("INVALID_ADJUSTMENT", "เบิกออกต้องเป็นจำนวนมากกว่าศูนย์");
        if (type.equals("ADJUST") && request.quantityDelta() == 0) throw bad("INVALID_ADJUSTMENT", "จำนวนที่ปรับต้องไม่เป็นศูนย์");
        if ((type.equals("RESERVE") || type.equals("RELEASE")) && request.quantityDelta() <= 0) throw bad("INVALID_ADJUSTMENT", "จำนวนต้องมากกว่าศูนย์");
        var key = request.idempotencyKey() == null || request.idempotencyKey().isBlank() ? null : request.idempotencyKey().trim();
        if (key != null) {
            var prior = ledger.findByIdempotencyKey(key);
            if (prior.isPresent()) {
                if (!prior.get().getSku().getId().equals(sku.getId())) throw conflict("IDEMPOTENCY_CONFLICT", "รหัสกันซ้ำนี้ถูกใช้กับสินค้าอื่นแล้ว");
                return stockResponse(levels.findBySku_Id(sku.getId()).orElseThrow(), type);
            }
        }
        var level = levels.findBySku_Id(sku.getId()).orElseThrow(() -> notFound("SKU_NOT_FOUND", "ไม่พบรหัสสินค้า"));
        if (level.getVersion() != request.version()) throw conflict("STOCK_CONFLICT", "สต็อกถูกแก้ไขไปแล้ว โหลดข้อมูลใหม่แล้วลองอีกครั้ง");
        var onHandBefore = level.getOnHand();
        var reservedBefore = level.getReservedQty();
        var onHandAfter = onHandBefore;
        var reservedAfter = reservedBefore;
        var delta = request.quantityDelta();
        switch (type) {
            case "RECEIVE" -> onHandAfter = onHandBefore + delta;
            case "ISSUE" -> {
                onHandAfter = onHandBefore - delta;
                if (onHandAfter < reservedBefore) throw bad("INSUFFICIENT_STOCK", "สต็อกไม่พอสำหรับการเบิกออก");
                delta = -delta;
            }
            case "ADJUST" -> {
                onHandAfter = onHandBefore + delta;
                if (onHandAfter < 0 || onHandAfter < reservedBefore) throw bad("INSUFFICIENT_STOCK", "สต็อกไม่พอสำหรับการปรับยอด");
            }
            case "RESERVE" -> {
                if (delta > level.getAvailable()) throw bad("INSUFFICIENT_AVAILABLE", "สต็อกพร้อมขายไม่พอสำหรับการจอง");
                reservedAfter = reservedBefore + delta;
            }
            case "RELEASE" -> {
                if (delta > reservedBefore) throw bad("RELEASE_EXCEEDS_RESERVED", "จำนวนคืนสต็อกมากกว่ายอดที่จองไว้");
                reservedAfter = reservedBefore - delta;
                delta = -delta;
            }
            default -> throw bad("INVALID_ADJUSTMENT", "ปรับสต็อกได้เฉพาะ RECEIVE, ISSUE, ADJUST, RESERVE หรือ RELEASE");
        }
        level.setOnHand(onHandAfter);
        level.setReservedQty(reservedAfter);
        level.stamp(userId);
        var entry = new InventoryLedgerEntry(sku, type, delta, onHandBefore, onHandAfter, reservedBefore, reservedAfter, blank(request.reason()), key, userId);
        entry.stamp(userId);
        ledger.save(entry);
        try {
            levels.saveAndFlush(level);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw conflict("STOCK_CONFLICT", "สต็อกถูกแก้ไขไปแล้ว โหลดข้อมูลใหม่แล้วลองอีกครั้ง");
        }
        return stockResponse(level, type);
    }

    @Transactional
    public List<CatalogDtos.StockResponse> adjustStockBulk(UUID userId, Long shopId, UUID productId, CatalogDtos.BulkStockRequest request) {
        requireSellableShop(userId, shopId);
        requireProduct(shopId, productId);
        var items = request == null || request.items() == null ? List.<CatalogDtos.BulkStockItem>of() : request.items();
        if (items.isEmpty()) throw bad("INVALID_ADJUSTMENT", "ไม่มีรายการปรับสต็อก");
        var prefix = request.idempotencyKey() == null || request.idempotencyKey().isBlank() ? null : request.idempotencyKey().trim();
        var results = new ArrayList<CatalogDtos.StockResponse>();
        for (var item : items) {
            if (item == null || item.skuId() == null) throw bad("SKU_NOT_FOUND", "ไม่พบรหัสสินค้า");
            var sku = skus.findByIdAndShop_ShopIdAndDeletedAtIsNull(item.skuId(), shopId).orElseThrow(() -> notFound("SKU_NOT_FOUND", "ไม่พบรหัสสินค้า"));
            if (!sku.getProduct().getId().equals(productId)) throw notFound("SKU_NOT_FOUND", "ไม่พบรหัสสินค้า");
            var key = prefix == null ? null : prefix + ":" + item.skuId();
            results.add(adjustStock(userId, shopId, item.skuId(), new CatalogDtos.StockAdjustRequest(
                item.entryType(), item.quantityDelta(), item.reason(), item.version(), key
            )));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public CatalogDtos.SkuCodeCheck checkSkuCodes(UUID userId, Long shopId, String codes, UUID excludeProductId) {
        requireSellableShop(userId, shopId);
        var requested = new LinkedHashSet<String>();
        if (codes != null) {
            for (var part : codes.split(",")) {
                var code = part.trim();
                if (!code.isEmpty()) requested.add(code);
            }
        }
        if (requested.isEmpty()) return new CatalogDtos.SkuCodeCheck(List.of());
        var taken = new ArrayList<String>();
        for (var sku : skus.findByShop_ShopIdAndSkuCodeInAndDeletedAtIsNull(shopId, requested)) {
            if (excludeProductId != null && sku.getProduct().getId().equals(excludeProductId)) continue;
            if (!taken.contains(sku.getSkuCode())) taken.add(sku.getSkuCode());
        }
        return new CatalogDtos.SkuCodeCheck(taken);
    }

    @Transactional(readOnly = true)
    public CatalogDtos.PageResponse<CatalogDtos.PublicProductResponse> publicList(String shopSlug, String q, Long categoryId, String sort, Integer page, Integer size, String priceBands, String locale) {
        var shop = requirePublicShop(shopSlug);
        var result = search(shop.getId(), q, ProductStatus.PUBLISHED, categoryId, null, null, parseSort(sort), page, size, true, parsePriceBands(priceBands));
        var ids = result.items().stream().map(product -> product.getId().toString()).toList();
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, ids);
        var options = translations.loadIndex(TranslationEntityType.PRODUCT_OPTION, ids);
        return new CatalogDtos.PageResponse<>(result.items().stream().map(product -> toPublic(product, index, options, locale)).toList(), result.page(), result.size(), result.total());
    }

    /** Public sort keys accepted by the marketplace search (no stock/updated ordering for guests). */
    private static final Set<String> PUBLIC_SEARCH_SORTS = Set.of("newest", "price_asc", "price_desc", "name");
    private static final int SEARCH_TERM_MAX = 100;

    /**
     * Marketplace-wide product search: PUBLISHED, not deleted, in APPROVED shops only. Matches the same fields as the
     * in-shop search (name th/en/zh, slug, SKU code, barcode) plus brand; LIKE wildcards in the term are literal.
     * The per-shop search ({@link #publicList}) is intentionally untouched.
     */
    @Transactional(readOnly = true)
    public CatalogDtos.PageResponse<CatalogDtos.PublicSearchProductResponse> publicSearch(String q, String brand, String sort, Integer page, Integer size, String locale) {
        var term = searchTerm(q);
        var brandKey = brand == null || brand.isBlank() ? null : brand.trim().toLowerCase(Locale.ROOT);
        var pageNumber = page == null || page < 0 ? 0 : page;
        var pageSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        if (term == null && brandKey == null) return new CatalogDtos.PageResponse<>(List.of(), pageNumber, pageSize, 0);
        var mapped = parseSort(sort);
        var order = orderClause(PUBLIC_SEARCH_SORTS.contains(mapped) ? mapped : "newest");
        var where = new StringBuilder(" where p.deletedAt is null and p.status = :status and p.shop.status = :approved");
        if (brandKey != null) where.append(" and lower(trim(p.brand)) = :brand");
        if (term != null) {
            where.append(" and (lower(p.nameTh) like :q escape '!' or lower(coalesce(p.nameEn, '')) like :q escape '!'")
                .append(" or lower(coalesce(p.nameZh, '')) like :q escape '!' or lower(p.slug) like :q escape '!'")
                .append(" or lower(coalesce(p.brand, '')) like :q escape '!'")
                .append(" or exists (select s from ProductSku s where s.product = p and s.deletedAt is null")
                .append(" and (lower(s.skuCode) like :q escape '!' or lower(coalesce(s.barcode, '')) like :q escape '!')))");
        }
        var query = entities.createQuery("select p from Product p" + where + order, Product.class);
        var count = entities.createQuery("select count(p) from Product p" + where, Long.class);
        for (var target : List.<jakarta.persistence.Query>of(query, count)) {
            target.setParameter("status", ProductStatus.PUBLISHED);
            target.setParameter("approved", ShopStatus.APPROVED);
            if (brandKey != null) target.setParameter("brand", brandKey);
            if (term != null) target.setParameter("q", "%" + likeEscape(term) + "%");
        }
        var total = count.getSingleResult();
        var items = query.setFirstResult(pageNumber * pageSize).setMaxResults(pageSize).getResultList();
        var ids = items.stream().map(product -> product.getId().toString()).toList();
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, ids);
        var options = translations.loadIndex(TranslationEntityType.PRODUCT_OPTION, ids);
        var hits = items.stream().map(product -> {
            var shop = product.getShop();
            return new CatalogDtos.PublicSearchProductResponse(
                toPublic(product, index, options, locale),
                new CatalogDtos.PublicSearchShopRef(shop.getSlug(), shop.getName(), shop.getNameEn())
            );
        }).toList();
        return new CatalogDtos.PageResponse<>(hits, pageNumber, pageSize, total);
    }

    /** Distinct brands (case/space-insensitive) of PUBLISHED products in APPROVED shops whose name contains the term. */
    @Transactional(readOnly = true)
    public List<CatalogDtos.PublicBrandResponse> publicBrandSearch(String q, int limit) {
        var term = searchTerm(q);
        if (term == null) return List.of();
        var rows = entities.createQuery(
                "select min(trim(p.brand)), count(p) from Product p"
                    + " where p.deletedAt is null and p.status = :status and p.shop.status = :approved"
                    + " and p.brand is not null and trim(p.brand) <> '' and lower(p.brand) like :q escape '!'"
                    + " group by lower(trim(p.brand)) order by count(p) desc, lower(trim(p.brand)) asc",
                Object[].class)
            .setParameter("status", ProductStatus.PUBLISHED)
            .setParameter("approved", ShopStatus.APPROVED)
            .setParameter("q", "%" + likeEscape(term) + "%")
            .setMaxResults(Math.max(1, Math.min(limit, 50)))
            .getResultList();
        return rows.stream().map(row -> new CatalogDtos.PublicBrandResponse(String.valueOf(row[0]), ((Number) row[1]).longValue())).toList();
    }

    private static String searchTerm(String q) {
        if (q == null || q.isBlank()) return null;
        var trimmed = q.trim();
        if (trimmed.length() > SEARCH_TERM_MAX) trimmed = trimmed.substring(0, SEARCH_TERM_MAX);
        return trimmed.toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public CatalogDtos.PublicProductResponse publicGet(String shopSlug, String productSlug, String locale) {
        var shop = requirePublicShop(shopSlug);
        var product = products.findByShop_SlugAndSlugAndDeletedAtIsNull(shop.getSlug(), productSlug)
            .filter(item -> item.getStatus() == ProductStatus.PUBLISHED)
            .orElseThrow(() -> notFound("PRODUCT_NOT_FOUND", "ไม่พบสินค้า"));
        var ids = List.of(product.getId().toString());
        return toPublic(product, translations.loadIndex(TranslationEntityType.PRODUCT, ids), translations.loadIndex(TranslationEntityType.PRODUCT_OPTION, ids), locale);
    }

    @Transactional(readOnly = true)
    public Path publicImage(String shopSlug, UUID productId, UUID imageId) {
        var shop = requirePublicShop(shopSlug);
        var product = products.findByIdAndShop_ShopIdAndDeletedAtIsNull(productId, shop.getId())
            .filter(item -> item.getStatus() == ProductStatus.PUBLISHED)
            .orElseThrow(() -> notFound("PRODUCT_NOT_FOUND", "ไม่พบสินค้า"));
        var image = images.findByIdAndProduct_Id(imageId, product.getId()).orElseThrow(() -> notFound("IMAGE_NOT_FOUND", "ไม่พบรูปสินค้า"));
        return storage.openPublicImage(image.getStoragePath()).orElseThrow(() -> notFound("IMAGE_NOT_FOUND", "ไม่พบรูปสินค้า"));
    }

    private void apply(Product product, CatalogDtos.ProductWriteRequest request, UUID userId) {
        var type = requireType(request == null ? null : request.productType());
        product.setNameTh(requireName(request == null ? null : request.nameTh()));
        product.setNameEn(blank(request.nameEn()));
        product.setNameZh(blank(request.nameZh()));
        product.setDescriptionTh(blank(request.descriptionTh()));
        product.setDescriptionEn(blank(request.descriptionEn()));
        product.setDescriptionZh(blank(request.descriptionZh()));
        product.setProductType(type);
        product.setBrand(blank(request.brand()));
        product.setBasePrice(money(request.basePrice(), true));
        product.setCompareAtPrice(money(request.compareAtPrice(), true));
        product.setCurrency(currency(request.currency()));
        product.setWeightGrams(weight(request.weightGrams()));
        product.setCategory(category(request.categoryId()));
        product.stamp(userId);
        if (product.getCompareAtPrice() != null && product.getBasePrice() != null && product.getCompareAtPrice().compareTo(product.getBasePrice()) < 0) {
            throw bad("COMPARE_PRICE_INVALID", ValidationMessages.text("COMPARE_PRICE_INVALID"));
        }
    }

    /**
     * Every create/update check in one pass, before anything is written: Bean Validation on the request DTO
     * (annotations in CatalogDtos) plus the business rules that need the database (category exists and is active,
     * SKU code / barcode unique per shop, original price not below the sale price, SKU count per product type).
     * Throws one 400 ValidationException listing every field problem.
     */
    private void validate(Long shopId, CatalogDtos.ProductWriteRequest request, Product current) {
        if (request == null) throw new ValidationException(List.of(ValidationMessages.violation("nameTh", "NAME_REQUIRED")));
        var problems = new java.util.LinkedHashMap<String, FieldViolation>();
        java.util.function.Consumer<FieldViolation> add = violation -> problems.putIfAbsent(violation.field() + "|" + violation.code(), violation);
        validator.validate(request).stream()
            .sorted(java.util.Comparator.comparing(violation -> violation.getPropertyPath().toString()))
            .forEach(violation -> add.accept(ValidationMessages.fromConstraint(violation.getPropertyPath().toString(), violation.getMessage())));
        var type = ProductType.parse(request.productType());
        if (type == null) add.accept(ValidationMessages.violation("productType", "PRODUCT_TYPE_INVALID"));
        if (request.slug() != null && !request.slug().isBlank() && normalizeSlug(request.slug()) == null) add.accept(ValidationMessages.violation("slug", "SLUG_INVALID"));
        if (request.currency() != null && !request.currency().isBlank() && !request.currency().trim().matches("[A-Za-z]{3}")) {
            add.accept(ValidationMessages.violation("currency", "CURRENCY_INVALID"));
        }
        if (request.categoryId() != null) {
            var category = categories.findByCategoryNo(request.categoryId()).orElse(null);
            var unchanged = category != null && current != null && current.getCategory() != null && current.getCategory().getId().equals(category.getId());
            if (category == null) add.accept(ValidationMessages.violation("categoryId", "UNKNOWN_CATEGORY"));
            else if (!category.isActive() && !unchanged) add.accept(ValidationMessages.violation("categoryId", "CATEGORY_INACTIVE"));
        }
        var incoming = request.skus() == null ? List.<CatalogDtos.SkuWriteRequest>of() : request.skus();
        // without a base price the product price becomes the lowest SKU price (fillBasePrice)
        var effectiveBase = positive(request.basePrice()) ? request.basePrice() : incoming.stream()
            .filter(java.util.Objects::nonNull).map(CatalogDtos.SkuWriteRequest::price).filter(ProductService::positive)
            .min(BigDecimal::compareTo).orElse(null);
        if (effectiveBase != null && positive(request.compareAtPrice()) && request.compareAtPrice().compareTo(effectiveBase) < 0) {
            add.accept(ValidationMessages.violation("compareAtPrice", "COMPARE_PRICE_INVALID"));
        }
        if (type == ProductType.SIMPLE && incoming.size() != 1) add.accept(ValidationMessages.violation("skus", "SIMPLE_SKU_COUNT"));
        if (type == ProductType.VARIABLE && incoming.isEmpty()) add.accept(ValidationMessages.violation("skus", "SKU_REQUIRED"));
        variants.validate(request, type, current, add);
        var existing = current == null ? List.<ProductSku>of() : skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(current.getId());
        if (current != null && current.getStatus() == ProductStatus.PUBLISHED && !anyActiveSku(incoming, existing)) {
            add.accept(ValidationMessages.violation("skus", "PUBLISHED_SKU_REQUIRED"));
        }
        var existingIds = existing.stream().map(ProductSku::getId).collect(java.util.stream.Collectors.toSet());
        var codes = new HashMap<String, Integer>();
        var barcodes = new HashMap<String, Integer>();
        for (var index = 0; index < incoming.size(); index++) {
            var sku = incoming.get(index);
            var prefix = "skus[" + index + "].";
            if (sku == null) {
                add.accept(ValidationMessages.violation(prefix + "skuCode", "SKU_CODE_REQUIRED"));
                continue;
            }
            var selfId = sku.id();
            if (selfId != null && !existingIds.contains(selfId)) add.accept(ValidationMessages.violation(prefix + "id", "SKU_NOT_FOUND"));
            // a SIMPLE product saved without the SKU id keeps editing its single existing SKU row (see syncSkus)
            if (selfId == null && type == ProductType.SIMPLE && existing.size() == 1 && incoming.size() == 1) selfId = existing.get(0).getId();
            if (positive(sku.price()) && positive(sku.compareAtPrice()) && sku.compareAtPrice().compareTo(sku.price()) < 0) {
                add.accept(ValidationMessages.violation(prefix + "compareAtPrice", "COMPARE_PRICE_INVALID"));
            }
            var code = sku.skuCode() == null ? "" : sku.skuCode().trim();
            if (!code.isEmpty() && code.length() <= 64) {
                if (codes.putIfAbsent(code, index) != null) {
                    add.accept(ValidationMessages.violation(prefix + "skuCode", "SKU_CODE_DUPLICATE_IN_FORM"));
                } else if (codeTaken(shopId, code, current == null ? null : current.getId())) {
                    add.accept(new FieldViolation(prefix + "skuCode", "SKU_CODE_TAKEN", "รหัสสินค้า " + code + " ซ้ำในร้าน"));
                }
            }
            var barcode = normalizeBarcode(sku.barcode());
            if (barcode != null && barcode.length() <= 64 && barcode.matches("[A-Z0-9-]+")) {
                if (barcodes.putIfAbsent(barcode.toLowerCase(Locale.ROOT), index) != null) {
                    add.accept(ValidationMessages.violation(prefix + "barcode", "BARCODE_DUPLICATE"));
                } else if (barcodeTaken(shopId, barcode, current == null ? null : current.getId())) {
                    add.accept(ValidationMessages.violation(prefix + "barcode", "BARCODE_TAKEN"));
                }
            }
        }
        if (!problems.isEmpty()) {
            var list = List.copyOf(problems.values());
            var code = ValidationException.topCode(list);
            // only "code already used in the shop" -> 409 (team frontend contract); anything else -> 400
            throw new ValidationException("SKU_CODE_TAKEN".equals(code) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST, code, list);
        }
    }

    private static String normalizeBarcode(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static CatalogDtos.ProductWriteRequest normalizeBarcodes(CatalogDtos.ProductWriteRequest request) {
        if (request == null || request.skus() == null) return request;
        var changed = false;
        var next = new ArrayList<CatalogDtos.SkuWriteRequest>();
        for (var sku : request.skus()) {
            if (sku == null) {
                next.add(null);
                continue;
            }
            var barcode = normalizeBarcode(sku.barcode());
            if (barcode == null ? sku.barcode() != null : !barcode.equals(sku.barcode())) changed = true;
            next.add(new CatalogDtos.SkuWriteRequest(sku.id(), sku.skuCode(), barcode, sku.optionLabel(), sku.optionValues(),
                sku.price(), sku.costPrice(), sku.compareAtPrice(), sku.weightGrams(), sku.initialStock(), sku.imageId(), sku.active(), sku.safetyStock()));
        }
        if (!changed) return request;
        return new CatalogDtos.ProductWriteRequest(request.nameTh(), request.nameEn(), request.nameZh(),
            request.descriptionTh(), request.descriptionEn(), request.descriptionZh(), request.categoryId(), request.slug(),
            request.productType(), request.brand(), request.basePrice(), request.compareAtPrice(), request.currency(),
            request.weightGrams(), next, request.optionGroups(), request.optionSchema(), request.confirmRemoveStocked());
    }

    /** A published product must keep at least one SKU on sale. A null active flag keeps the stored value; a new SKU is on sale. */
    private static boolean anyActiveSku(List<CatalogDtos.SkuWriteRequest> incoming, List<ProductSku> existing) {
        for (var sku : incoming) {
            if (sku == null) continue;
            if (sku.active() != null) {
                if (sku.active()) return true;
                continue;
            }
            if (sku.id() == null) return true;
            var stored = existing.stream().filter(item -> item.getId().equals(sku.id())).findFirst().orElse(null);
            if (stored == null || stored.isActive()) return true;
        }
        return false;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /** Newly created SKU and the opening stock requested for it. */
    private record OpeningStock(ProductSku sku, int quantity) {}

    /** Opening stock of the new SKUs and the saved SKU row for each request SKU, in request order. */
    private record SyncResult(List<OpeningStock> opening, List<ProductSku> ordered) {}

    /**
     * New SKUs get an inventory level and their opening stock through the ledger; SKUs left out of the request are
     * soft-deleted (deleted_at, inactive) so their stock ledger and order history stay intact. Stock of kept SKUs is
     * never changed here (use the stock adjustment endpoint).
     */
    private SyncResult syncSkus(Product product, CatalogDtos.ProductWriteRequest request, UUID userId) {
        var opening = new ArrayList<OpeningStock>();
        var ordered = new ArrayList<ProductSku>();
        var incoming = request == null || request.skus() == null ? List.<CatalogDtos.SkuWriteRequest>of() : request.skus();
        if (product.getProductType() == ProductType.SIMPLE && incoming.size() != 1) throw bad("SIMPLE_SKU_COUNT", "สินค้าแบบเดียวต้องมี 1 รหัสสินค้า");
        if (product.getProductType() == ProductType.VARIABLE && incoming.isEmpty()) throw bad("SKU_REQUIRED", "สินค้าแบบตัวเลือกต้องมีอย่างน้อย 1 รหัสสินค้า");
        var existing = skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId());
        var kept = new HashSet<UUID>();
        var reuseSimple = product.getProductType() == ProductType.SIMPLE && existing.size() == 1 && incoming.get(0).id() == null;
        if (reuseSimple) kept.add(existing.get(0).getId());
        else incoming.stream().filter(java.util.Objects::nonNull).map(CatalogDtos.SkuWriteRequest::id).filter(java.util.Objects::nonNull).forEach(kept::add);
        // SKUs left out of the request are soft-deleted (ledger/order history stay). Stocked ones need confirmRemoveStocked.
        var dropping = existing.stream().filter(sku -> !kept.contains(sku.getId())).toList();
        releaseRemovedStock(dropping, request != null && Boolean.TRUE.equals(request.confirmRemoveStocked()), userId, product.getShop().getId());
        var now = Instant.now();
        for (var sku : dropping) {
            sku.setDeletedAt(now);
            sku.setActive(false);
            sku.stamp(userId);
        }
        if (!dropping.isEmpty()) entities.flush();
        if (!reuseSimple) parkChangingCodes(existing, incoming);
        if (reuseSimple) {
            applySku(existing.get(0), incoming.get(0), product.getCurrency(), userId, 0);
            applySafetyStock(existing.get(0), incoming.get(0).safetyStock(), userId);
            ordered.add(existing.get(0));
        } else {
            for (var index = 0; index < incoming.size(); index++) {
                var skuRequest = incoming.get(index);
                if (skuRequest.id() == null) {
                    var created = new ProductSku(product, requireSkuCode(product.getShop().getId(), skuRequest.skuCode(), null, index), requirePrice(skuRequest.price()));
                    created.stamp(userId);
                    applySku(created, skuRequest, product.getCurrency(), userId, index);
                    entities.persist(created);
                    var level = new InventoryLevel(created);
                    if (skuRequest.safetyStock() != null) level.setSafetyStock(skuRequest.safetyStock());
                    level.stamp(userId);
                    entities.persist(level);
                    ordered.add(created);
                    if (skuRequest.initialStock() != null && skuRequest.initialStock() > 0) opening.add(new OpeningStock(created, skuRequest.initialStock()));
                } else {
                    var sku = existing.stream().filter(item -> item.getId().equals(skuRequest.id())).findFirst()
                        .orElseThrow(() -> notFound("SKU_NOT_FOUND", "ไม่พบรหัสสินค้า"));
                    applySku(sku, skuRequest, product.getCurrency(), userId, index);
                    applySafetyStock(sku, skuRequest.safetyStock(), userId);
                    ordered.add(sku);
                }
            }
        }
        return new SyncResult(opening, ordered);
    }

    /** products.option_schema (team frontend reads it) mirrors the option group tables after every save. */
    private void storeOptionSchema(Product product) {
        if (product.getProductType() != ProductType.VARIABLE) {
            product.setOptionSchema("[]");
            return;
        }
        var groups = variants.groupViews(product.getId()).stream()
            .map(group -> new CatalogDtos.OptionGroup(group.name(), group.values().stream().map(CatalogDtos.OptionValueResponse::value).toList()))
            .toList();
        product.setOptionSchema(JsonMaps.writeGroups(groups));
    }

    private List<CatalogDtos.OptionGroup> normalizeSchema(List<CatalogDtos.OptionGroup> raw, ProductType type, List<AuthDtos.ApiFieldError> issues) {
        if (type != ProductType.VARIABLE) return List.of();
        var groups = new ArrayList<CatalogDtos.OptionGroup>();
        var names = new HashSet<String>();
        if (raw != null) {
            for (var group : raw) {
                var name = group == null || group.name() == null ? "" : group.name().trim();
                var values = new ArrayList<String>();
                var seen = new HashSet<String>();
                if (group != null && group.values() != null) {
                    for (var value : group.values()) {
                        if (value == null || value.isBlank()) continue;
                        var label = value.trim();
                        if (seen.add(label)) values.add(label);
                    }
                }
                if (name.isBlank() || values.isEmpty() || !names.add(name)) continue;
                groups.add(new CatalogDtos.OptionGroup(name, List.copyOf(values)));
            }
        }
        if (groups.isEmpty() || groups.size() > 3 || (raw != null && groups.size() != raw.size())) {
            issues.add(field("optionSchema", "OPTION_SCHEMA_INVALID", "สินค้าแบบตัวเลือกต้องมีกลุ่มตัวเลือก 1 ถึง 3 กลุ่ม"));
        }
        return groups;
    }

    private static boolean combinationMatches(List<CatalogDtos.OptionGroup> schema, Map<String, String> values) {
        if (schema.isEmpty()) return false;
        if (values.size() != schema.size()) return false;
        for (var group : schema) {
            var value = values.get(group.name());
            if (value == null || !group.values().contains(value)) return false;
        }
        return true;
    }

    private static String combinationKey(List<CatalogDtos.OptionGroup> schema, Map<String, String> values) {
        var key = new StringBuilder();
        for (var group : schema) {
            if (!key.isEmpty()) key.append('\n');
            key.append(group.name()).append('=').append(values.get(group.name()));
        }
        return key.toString();
    }

    /** Barcode used by a live SKU of another product (this product's own SKUs are checked within the form). */
    private boolean barcodeTaken(Long shopId, String barcode, UUID productId) {
        return skus.findByShop_ShopIdAndBarcodeIgnoreCaseAndDeletedAtIsNull(shopId, barcode).stream()
            .anyMatch(sku -> productId == null || !sku.getProduct().getId().equals(productId));
    }

    private boolean codeTaken(Long shopId, String code, UUID productId) {
        return skus.findByShop_ShopIdAndSkuCodeInAndDeletedAtIsNull(shopId, List.of(code)).stream()
            .anyMatch(sku -> productId == null || !sku.getProduct().getId().equals(productId));
    }

    private void parkChangingCodes(List<ProductSku> existing, List<CatalogDtos.SkuWriteRequest> incoming) {
        var changed = false;
        for (var skuRequest : incoming) {
            if (skuRequest.id() == null || skuRequest.skuCode() == null) continue;
            var sku = existing.stream().filter(item -> item.getId().equals(skuRequest.id())).findFirst().orElse(null);
            if (sku == null || sku.getSkuCode().equals(skuRequest.skuCode().trim())) continue;
            sku.setSkuCode(temporaryCode(sku.getId()));
            changed = true;
        }
        if (changed) skus.flush();
    }

    private static String temporaryCode(UUID id) {
        return ("~" + id.toString().replace("-", "")).substring(0, 33);
    }

    private void releaseRemovedStock(List<ProductSku> dropping, boolean confirm, UUID userId, Long shopId) {
        if (dropping.isEmpty()) return;
        var stock = levels.findBySku_IdIn(dropping.stream().map(ProductSku::getId).toList());
        if (stock.stream().anyMatch(level -> level.getReservedQty() > 0)) {
            throw new AuthException(HttpStatus.CONFLICT, "SKU_HAS_STOCK", "มีรหัสสินค้าที่มียอดจอง", "skus");
        }
        if (!confirm && stock.stream().anyMatch(level -> level.getOnHand() > 0)) {
            throw new AuthException(HttpStatus.CONFLICT, "SKU_HAS_STOCK", "มีรหัสสินค้าที่ยังมีสต็อกหรือยอดจอง", "skus");
        }
        for (var level : stock) {
            if (level.getOnHand() <= 0) continue;
            adjustStock(userId, shopId, level.getSku().getId(), new CatalogDtos.StockAdjustRequest(
                "ADJUST", -level.getOnHand(), "VARIANT_REMOVED", level.getVersion(), "variant-removed-" + level.getSku().getId()
            ));
        }
    }

    private void applySku(ProductSku sku, CatalogDtos.SkuWriteRequest request, String currency, UUID userId, int index) {
        sku.setSkuCode(requireSkuCode(sku.getShop().getId(), request.skuCode(), sku.getId(), index));
        sku.setPrice(requirePrice(request.price()));
        sku.setCostPrice(money(request.costPrice(), true));
        sku.setCompareAtPrice(money(request.compareAtPrice(), true));
        if (sku.getCompareAtPrice() != null && sku.getCompareAtPrice().compareTo(sku.getPrice()) < 0) {
            throw bad("COMPARE_PRICE_INVALID", ValidationMessages.text("COMPARE_PRICE_INVALID"));
        }
        sku.setBarcode(normalizeBarcode(request.barcode()));
        sku.setOptionLabel(blank(request.optionLabel()));
        sku.setOptionValues(JsonMaps.write(request.optionValues()));
        sku.setCurrency(currency);
        sku.setWeightGrams(weight(request.weightGrams()));
        if (request.active() != null) sku.setActive(request.active());
        if (request.imageId() == null) sku.setImage(null);
        else sku.setImage(images.findByIdAndProduct_Id(request.imageId(), sku.getProduct().getId()).orElse(null));
        sku.stamp(userId);
    }

    /** null keeps the stored safety stock. New levels are created at 0 before this runs. */
    private void applySafetyStock(ProductSku sku, Integer requested, UUID userId) {
        if (requested == null) return;
        var level = levels.findBySku_Id(sku.getId()).orElse(null);
        if (level == null || level.getSafetyStock() == requested) return;
        level.setSafetyStock(requested);
        level.stamp(userId);
    }

    /** Opening stock of new SKUs goes through the ledger (RECEIVE) inside the same transaction as the product. */
    private void applyOpeningStock(UUID userId, Long shopId, List<OpeningStock> opening) {
        if (opening.isEmpty()) return;
        entities.flush();
        for (var item : opening) {
            adjustStock(userId, shopId, item.sku().getId(), new CatalogDtos.StockAdjustRequest(
                "RECEIVE", item.quantity(), "สต็อกเริ่มต้น", 0, "opening-" + item.sku().getId()
            ));
        }
    }

    private static String writeSchema(List<CatalogDtos.OptionGroup> groups) {
        return JsonMaps.writeGroups(groups);
    }

    private static List<CatalogDtos.OptionGroup> readSchema(String raw) {
        return JsonMaps.readGroups(raw);
    }

    private static AuthDtos.ApiFieldError field(String name, String code, String message) {
        return new AuthDtos.ApiFieldError(name, code, message);
    }

    private static AuthException fieldErrors(List<AuthDtos.ApiFieldError> issues) {
        var takenOnly = issues.stream().allMatch(issue -> "SKU_CODE_TAKEN".equals(issue.code()));
        var status = takenOnly ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        var first = issues.get(0);
        return new AuthException(status, first.code(), first.message(), issues);
    }

    private ProductSpreadsheet.SheetRow sheetRow(Product product, ProductSku sku, InventoryLevel level) {
        var category = product.getCategory() == null ? null : product.getCategory().getNameTh();
        var values = sku == null ? Map.<String, String>of() : JsonMaps.read(sku.getOptionValues());
        var schema = readSchema(product.getOptionSchema());
        var names = new String[3];
        var selected = new String[3];
        for (var index = 0; index < Math.min(3, schema.size()); index++) {
            names[index] = schema.get(index).name();
            selected[index] = values.get(names[index]);
        }
        var variable = product.getProductType() == ProductType.VARIABLE;
        return new ProductSpreadsheet.SheetRow(
            0,
            product.getNameTh(),
            product.getNameEn(),
            product.getNameZh(),
            product.getDescriptionTh(),
            product.getDescriptionEn(),
            product.getDescriptionZh(),
            category,
            product.getBrand(),
            sku == null ? null : sku.getSkuCode(),
            sku == null ? null : sku.getBarcode(),
            sku == null ? null : sku.getOptionLabel(),
            sku == null ? product.getBasePrice() : sku.getPrice(),
            sku == null ? product.getCompareAtPrice() : sku.getCompareAtPrice(),
            sku == null ? product.getWeightGrams() : sku.getWeightGrams(),
            level == null ? 0 : level.getOnHand(),
            null,
            List.of(),
            null,
            variable ? product.getId().toString() : null,
            product.getProductType().name(),
            names[0], selected[0], names[1], selected[1], names[2], selected[2]
        );
    }

    private void fillBasePrice(Product product) {
        if (product.getBasePrice() != null) return;
        var prices = skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId()).stream()
            .map(ProductSku::getPrice)
            .toList();
        if (!prices.isEmpty()) product.setBasePrice(prices.stream().min(BigDecimal::compareTo).orElseThrow());
        if (product.getCompareAtPrice() != null && product.getBasePrice() != null && product.getCompareAtPrice().compareTo(product.getBasePrice()) < 0) {
            throw bad("COMPARE_PRICE_INVALID", ValidationMessages.text("COMPARE_PRICE_INVALID"));
        }
    }

    private SearchResult search(Long shopId, String q, ProductStatus status, Long categoryId, String brand, String stock, String sort, Integer page, Integer size, boolean publishedOnly, List<String> priceBands) {
        var pageNumber = page == null || page < 0 ? 0 : page;
        var pageSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        var term = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        var where = new StringBuilder(" where p.shop.shopId = :shopId and p.deletedAt is null");
        if (publishedOnly || status != null) where.append(" and p.status = :status");
        else where.append(" and p.status <> :archived");
        if (categoryId != null) where.append(" and p.category.categoryNo = :categoryId");
        if (brand != null) where.append(" and lower(trim(p.brand)) = :brand");
        if (term != null) {
            where.append(" and (lower(p.nameTh) like :q escape '!' or lower(coalesce(p.nameEn, '')) like :q escape '!'")
                .append(" or lower(coalesce(p.nameZh, '')) like :q escape '!' or lower(p.slug) like :q escape '!'")
                .append(" or exists (select s from ProductSku s where s.product = p and s.deletedAt is null")
                .append(" and (lower(s.skuCode) like :q escape '!' or lower(coalesce(s.barcode, '')) like :q escape '!')))");
        }
        where.append(stockClause(stock));
        where.append(priceClause(priceBands));
        var query = entities.createQuery("select p from Product p" + where + orderClause(sort), Product.class);
        var count = entities.createQuery("select count(p) from Product p" + where, Long.class);
        for (var target : List.<jakarta.persistence.Query>of(query, count)) {
            target.setParameter("shopId", shopId);
            if (publishedOnly || status != null) target.setParameter("status", publishedOnly ? ProductStatus.PUBLISHED : status);
            else target.setParameter("archived", ProductStatus.ARCHIVED);
            if (categoryId != null) target.setParameter("categoryId", categoryId);
            if (brand != null) target.setParameter("brand", brand.toLowerCase(Locale.ROOT));
            if (term != null) target.setParameter("q", "%" + likeEscape(term) + "%");
        }
        var total = count.getSingleResult();
        var items = query.setFirstResult(pageNumber * pageSize).setMaxResults(pageSize).getResultList();
        return new SearchResult(items, pageNumber, pageSize, total);
    }

    private static String likeEscape(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private CatalogDtos.MerchantProductResponse merchantView(Product product) {
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, List.of(product.getId().toString()));
        return toMerchant(product, index);
    }

    private CatalogDtos.MerchantProductResponse toMerchant(Product product, ContentTranslationIndex index) {
        return toMerchant(product, index, null);
    }

    private CatalogDtos.MerchantProductResponse toMerchant(Product product, ContentTranslationIndex index, SkuRollup rollup) {
        var entityId = product.getId().toString();
        var skuRows = skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId());
        var stock = skuRows.isEmpty() ? List.<InventoryLevel>of() : levels.findBySku_IdIn(skuRows.stream().map(ProductSku::getId).toList());
        var options = variants.skuOptions(skuRows.stream().map(ProductSku::getId).toList());
        var totals = rollup == null ? rollup(skuRows, stock) : rollup;
        return new CatalogDtos.MerchantProductResponse(
            product.getId(), product.getShop().getId(), product.getSlug(), product.getNameTh(),
            index.merchantValue(entityId, "name", "en", product.getNameEn(), product.getNameTh()),
            index.merchantValue(entityId, "name", "zh", product.getNameZh(), product.getNameTh()),
            product.getDescriptionTh(),
            index.merchantValue(entityId, "description", "en", product.getDescriptionEn(), product.getDescriptionTh()),
            index.merchantValue(entityId, "description", "zh", product.getDescriptionZh(), product.getDescriptionTh()),
            product.getStatus(), product.getProductType(),
            product.getCategory() == null ? null : product.getCategory().getId(), product.getBrand(), product.getBasePrice(), product.getCompareAtPrice(),
            trim(product.getCurrency()), product.getWeightGrams(), product.getPublishedAt(), product.getArchivedAt(), product.getModifiedDateTime(),
            skuRows.stream().map(sku -> {
                var level = stock.stream().filter(item -> item.getSku().getId().equals(sku.getId())).findFirst().orElse(null);
                return new CatalogDtos.MerchantSkuResponse(sku.getId(), sku.getSkuCode(), sku.getBarcode(), sku.getOptionLabel(), JsonMaps.read(sku.getOptionValues()),
                    sku.getPrice(), sku.getCostPrice(), sku.getCompareAtPrice(), trim(sku.getCurrency()), sku.getWeightGrams(), sku.isActive(),
                    level == null ? 0 : level.getOnHand(), level == null ? 0 : level.getReservedQty(), level == null ? 0 : level.getAvailable(),
                    level == null ? null : level.getLowStockThreshold(), level == null ? 0 : level.getSafetyStock(), level == null ? 0 : level.getVersion(),
                    sku.getImage() == null ? null : sku.getImage().getId(), options.getOrDefault(sku.getId(), List.of()));
            }).toList(),
            imageResponses(product, true),
            toMeta(index.meta(entityId, "name", "en")),
            toMeta(index.meta(entityId, "name", "zh")),
            toMeta(index.meta(entityId, "description", "en")),
            toMeta(index.meta(entityId, "description", "zh")),
            variants.groupViews(product.getId()),
            readSchema(product.getOptionSchema()),
            totals.count(), totals.stock(), totals.minPrice(), totals.maxPrice(), totals.lowStock()
        );
    }

    private static UUID uuidOf(byte[] bytes) {
        var buffer = java.nio.ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private Map<UUID, SkuRollup> rollup(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var totals = new HashMap<UUID, SkuRollup>();
        for (var row : skus.summarizeActiveSkus(ids)) {
            var id = row[0] instanceof UUID uuid ? uuid
                : row[0] instanceof byte[] bytes && bytes.length == 16 ? uuidOf(bytes)
                : UUID.fromString(String.valueOf(row[0]));
            totals.put(id, new SkuRollup(number(row[1]), number(row[2]), decimal(row[3]), decimal(row[4]), number(row[5])));
        }
        return totals;
    }

    private static SkuRollup rollup(List<ProductSku> skuRows, List<InventoryLevel> stock) {
        var count = 0;
        var total = 0;
        var low = 0;
        BigDecimal min = null;
        BigDecimal max = null;
        for (var sku : skuRows) {
            if (!sku.isActive()) continue;
            count++;
            var level = stock.stream().filter(item -> item.getSku().getId().equals(sku.getId())).findFirst().orElse(null);
            var available = level == null ? 0 : level.getAvailable();
            total += available;
            var threshold = level == null ? null : level.getLowStockThreshold();
            if (available > 0 && threshold != null && available <= threshold) low++;
            if (sku.getPrice() == null) continue;
            if (min == null || sku.getPrice().compareTo(min) < 0) min = sku.getPrice();
            if (max == null || sku.getPrice().compareTo(max) > 0) max = sku.getPrice();
        }
        return new SkuRollup(count, total, min, max, low);
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal amount) return amount;
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : null;
    }

    private record SkuRollup(int count, int stock, BigDecimal minPrice, BigDecimal maxPrice, int lowStock) {}

    private CatalogDtos.PublicProductResponse toPublic(Product product, ContentTranslationIndex index, ContentTranslationIndex optionTranslations, String locale) {
        var entityId = product.getId().toString();
        var skuRows = skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId()).stream().filter(ProductSku::isActive).toList();
        var stock = skuRows.isEmpty() ? List.<InventoryLevel>of() : levels.findBySku_IdIn(skuRows.stream().map(ProductSku::getId).toList());
        var options = variants.skuOptions(skuRows.stream().map(ProductSku::getId).toList());
        var schema = readSchema(product.getOptionSchema());
        var localized = localizeGroups(entityId, schema, optionTranslations, locale);
        var used = optionKeys(skuRows);
        var sourceGroups = filterOptionGroups(schema, used);
        var localizedGroups = filterAlignedOptions(schema, localized, used);
        return new CatalogDtos.PublicProductResponse(
            product.getId(), product.getSlug(), product.getNameTh(),
            shown(index.resolve(entityId, "name", "en", product.getNameTh(), product.getNameEn()), product.getNameTh()),
            shown(index.resolve(entityId, "name", "zh", product.getNameTh(), product.getNameZh()), product.getNameTh()),
            product.getDescriptionTh(),
            shown(index.resolve(entityId, "description", "en", product.getDescriptionTh(), product.getDescriptionEn()), product.getDescriptionTh()),
            shown(index.resolve(entityId, "description", "zh", product.getDescriptionTh(), product.getDescriptionZh()), product.getDescriptionTh()),
            product.getCategory() == null ? null : product.getCategory().getId(), product.getBrand(), product.getBasePrice(), product.getCompareAtPrice(), trim(product.getCurrency()),
            skuRows.stream().map(sku -> {
                var level = stock.stream().filter(item -> item.getSku().getId().equals(sku.getId())).findFirst().orElse(null);
                var values = localizeOptionValues(sourceGroups, localizedGroups, JsonMaps.read(sku.getOptionValues()));
                var label = values.isEmpty() ? sku.getOptionLabel() : String.join(" / ", values.values());
                return new CatalogDtos.PublicSkuResponse(sku.getId(), sku.getSkuCode(), label, values,
                    sku.getPrice(), sku.getCompareAtPrice(), trim(sku.getCurrency()), level == null ? 0 : level.getAvailable(),
                    sku.getImage() == null ? null : sku.getImage().getId(), options.getOrDefault(sku.getId(), List.of()));
            }).toList(),
            imageResponses(product, false),
            filterGroupViews(variants.groupViews(product.getId()), used),
            localizedGroups
        );
    }

    private static CatalogDtos.TranslationMeta toMeta(ContentTranslationIndex.TranslationFieldMeta meta) {
        return meta == null ? null : new CatalogDtos.TranslationMeta(meta.origin(), meta.status());
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.LedgerEntryResponse> ledger(UUID userId, Long shopId, UUID productId) {
        requireSellableShop(userId, shopId);
        requireProduct(shopId, productId);
        var rows = ledger.findBySku_Product_IdOrderByCreatedDateTimeDesc(productId);
        var actorIds = new LinkedHashSet<UUID>();
        for (var entry : rows) {
            var actorId = entry.getActorId() != null ? entry.getActorId() : entry.getCreatedBy();
            if (actorId != null) actorIds.add(actorId);
        }
        var names = new LinkedHashMap<UUID, String>();
        if (!actorIds.isEmpty()) {
            for (var user : accounts.findAllById(actorIds)) names.put(user.getId(), actorLabel(user));
        }
        return rows.stream()
            .map(entry -> {
                var actorId = entry.getActorId() != null ? entry.getActorId() : entry.getCreatedBy();
                return new CatalogDtos.LedgerEntryResponse(
                    entry.getId(), entry.getSku().getId(), entry.getSku().getSkuCode(), entry.getEntryType(), entry.getQuantityDelta(),
                    entry.getOnHandBefore(), entry.getOnHandAfter(), entry.getReason(), entry.getCreatedDateTime(),
                    actorId == null ? null : names.get(actorId),
                    entry.getReservedQtyBefore() == null ? 0 : entry.getReservedQtyBefore(),
                    entry.getReservedQtyAfter() == null ? 0 : entry.getReservedQtyAfter());
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public Path merchantImage(UUID userId, Long shopId, UUID productId, UUID imageId) {
        requireSellableShop(userId, shopId);
        requireProduct(shopId, productId);
        var image = images.findByIdAndProduct_Id(imageId, productId).orElseThrow(() -> notFound("IMAGE_NOT_FOUND", "ไม่พบรูปสินค้า"));
        return storage.openPublicImage(image.getStoragePath()).orElseThrow(() -> notFound("IMAGE_NOT_FOUND", "ไม่พบรูปสินค้า"));
    }

    private List<CatalogDtos.ImageResponse> imageResponses(Product product, boolean merchant) {
        return images.findByProduct_IdOrderBySortOrderAsc(product.getId()).stream()
            .map(image -> new CatalogDtos.ImageResponse(image.getId(), merchant ? merchantImageUrl(product, image.getId()) : imageUrl(product, image.getId()), image.getSortOrder(), image.isPrimaryImage(), image.getAltText()))
            .toList();
    }

    private static String merchantImageUrl(Product product, UUID imageId) {
        return "/api/merchant/shops/" + product.getShop().getId() + "/products/" + product.getId() + "/images/" + imageId;
    }

    private static String imageUrl(Product product, UUID imageId) {
        return "/api/shops/" + product.getShop().getSlug() + "/products/" + product.getId() + "/images/" + imageId;
    }

    private static CatalogDtos.StockResponse stockResponse(InventoryLevel level, String type) {
        return new CatalogDtos.StockResponse(level.getSku().getId(), type, level.getOnHand(), level.getAvailable(), level.getSafetyStock(), level.getVersion());
    }

    public void requireImportAccess(UUID userId, Long shopId) {
        requireSellableShop(userId, shopId);
    }

    private Shop requireSellableShop(UUID userId, Long shopId) {
        var shop = shops.findByShopId(shopId).orElseThrow(ProductService::missingShop);
        // Who may touch the shop is decided by the DB permission matrix (@RequireShopPermission already ran);
        // here we only make sure the caller is an active member of THIS shop (or super admin), so a service
        // call can never leak another shop's data. Non-members get the same 404 as a missing shop.
        if (userId == null || !canAccessShop(userId, shop)) throw missingShop();
        if (shop.getStatus() != ShopStatus.APPROVED) throw new AuthException(HttpStatus.FORBIDDEN, "SHOP_NOT_APPROVED", "ร้านนี้ยังเปิดขายไม่ได้");
        return shop;
    }

    private boolean canAccessShop(UUID userId, Shop shop) {
        var grants = authorization.grants(userId);
        return grants.superAdmin() || grants.shopRoles().containsKey(shop.getId());
    }

    private Shop requirePublicShop(String slug) {
        var shop = shops.findBySlug(slug).orElseThrow(ProductService::missingShop);
        if (shop.getStatus() != ShopStatus.APPROVED) throw missingShop();
        return shop;
    }

    private Product requireProduct(Long shopId, UUID productId) {
        return products.findByIdAndShop_ShopIdAndDeletedAtIsNull(productId, shopId).orElseThrow(() -> notFound("PRODUCT_NOT_FOUND", "ไม่พบสินค้า"));
    }

    private String uniqueSlug(Long shopId, String requested, String nameEn, String nameTh, UUID currentId) {
        String base;
        if (requested != null && !requested.isBlank()) {
            base = normalizeSlug(requested);
            if (base == null) throw bad("SLUG_INVALID", "ชื่อลิงก์ใช้ได้เฉพาะตัวอักษรภาษาอังกฤษ ตัวเลข และขีด");
        } else {
            base = generatedSlug(nameEn, nameTh);
            if (base == null) base = "item";
        }
        var candidate = base;
        for (var suffix = 2; slugTaken(shopId, candidate, currentId); suffix++) {
            var ending = "-" + suffix;
            var room = Math.max(1, 150 - ending.length());
            var stem = base.length() > room ? base.substring(0, room).replaceAll("-+$", "") : base;
            candidate = stem + ending;
        }
        return candidate;
    }

    private boolean slugTaken(Long shopId, String slug, UUID currentId) {
        return currentId == null
            ? products.existsByShop_ShopIdAndSlug(shopId, slug)
            : products.existsByShop_ShopIdAndSlugAndIdNot(shopId, slug, currentId);
    }

    private static String generatedSlug(String nameEn, String nameTh) {
        var english = normalizeSlug(nameEn);
        if (english != null) return english;
        return normalizeSlug(nameTh);
    }

    private static String normalizeSlug(String value) {
        if (value == null || value.isBlank()) return null;
        var slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 150) slug = slug.substring(0, 150).replaceAll("-+$", "");
        return slug.length() >= 2 ? slug : null;
    }

    private String requireSkuCode(Long shopId, String value, UUID currentId, int index) {
        if (value == null || value.isBlank()) throw bad("SKU_CODE_REQUIRED", "กรุณาระบุรหัสสินค้า");
        var code = value.trim();
        if (code.length() > 64) throw bad("SKU_CODE_REQUIRED", "รหัสสินค้ายาวเกินกำหนด");
        var taken = currentId == null
            ? skus.existsByShop_ShopIdAndSkuCodeAndDeletedAtIsNull(shopId, code)
            : skus.existsByShop_ShopIdAndSkuCodeAndDeletedAtIsNullAndIdNot(shopId, code, currentId);
        if (taken) {
            var message = "รหัสสินค้า " + code + " ซ้ำในร้าน";
            throw new AuthException(HttpStatus.CONFLICT, "SKU_CODE_TAKEN", message, List.of(field("skus[" + index + "].skuCode", "SKU_CODE_TAKEN", message)));
        }
        return code;
    }

    private com.thaishopfun.merchant.ProductCategory category(Long categoryId) {
        if (categoryId == null) return null;
        return categories.findByCategoryNo(categoryId).orElseThrow(() -> bad("UNKNOWN_CATEGORY", "หมวดสินค้าไม่ถูกต้อง"));
    }

    private static ProductType requireType(String value) {
        var type = ProductType.parse(value);
        if (type == null) throw bad("PRODUCT_TYPE_INVALID", "ประเภทสินค้าไม่ถูกต้อง");
        return type;
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) throw bad("NAME_REQUIRED", "กรุณาระบุชื่อสินค้า");
        var name = value.trim();
        if (name.length() > 255) throw bad("NAME_REQUIRED", "ชื่อสินค้ายาวเกินกำหนด");
        return name;
    }

    private static BigDecimal requirePrice(BigDecimal value) {
        var price = money(value, false);
        if (price == null) throw bad("PRICE_INVALID", "กรุณาระบุราคาที่ไม่ติดลบ");
        return price;
    }

    private static BigDecimal money(BigDecimal value, boolean optional) {
        if (value == null) {
            if (optional) return null;
            throw bad("PRICE_INVALID", "กรุณาระบุราคาที่ไม่ติดลบ");
        }
        if (value.signum() < 0) throw bad("PRICE_INVALID", "ราคาต้องไม่ติดลบ");
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static Integer weight(Integer value) {
        if (value == null) return null;
        if (value < 0) throw bad("WEIGHT_INVALID", "น้ำหนักต้องไม่ติดลบ");
        return value;
    }

    private static String currency(String value) {
        if (value == null || value.isBlank()) return "THB";
        var code = value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) throw bad("CURRENCY_INVALID", "สกุลเงินต้องมี 3 ตัวอักษร");
        return code;
    }

    private static ProductStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        var status = ProductStatus.parse(value);
        if (status == null) throw bad("INVALID_STATUS", "สถานะสินค้าไม่ถูกต้อง");
        return status;
    }

    private static String parseStock(String value) {
        if (value == null || value.isBlank()) return null;
        var stock = value.trim().toLowerCase(Locale.ROOT);
        if (!stock.equals("in") && !stock.equals("low") && !stock.equals("out")) throw bad("INVALID_STOCK", "ตัวกรองสต็อกไม่ถูกต้อง");
        return stock;
    }

    /**
     * Sort whitelist. Accepts the UI keys (newest, oldest, updated, name, name_desc, price_asc, price_desc, stock)
     * and field,direction pairs for createdAt, updatedAt, name, price (e.g. "updatedAt,desc"). Anything else is 400.
     */
    private static String parseSort(String value) {
        if (value == null || value.isBlank()) return "newest";
        var sort = value.trim().toLowerCase(Locale.ROOT).replace(":", ",");
        var mapped = switch (sort) {
            case "newest", "createdat,desc", "createdat" -> "newest";
            case "oldest", "createdat,asc" -> "oldest";
            case "updated", "updatedat,desc", "updatedat" -> "updated";
            case "updated_asc", "updatedat,asc" -> "updated_asc";
            case "name", "name,asc" -> "name";
            case "name_desc", "name,desc" -> "name_desc";
            case "price_asc", "price,asc", "price" -> "price_asc";
            case "price_desc", "price,desc" -> "price_desc";
            case "stock", "stock,desc" -> "stock";
            default -> null;
        };
        if (mapped == null) throw bad("INVALID_SORT", "การเรียงลำดับไม่ถูกต้อง");
        return mapped;
    }

    private static String stockClause(String stock) {
        if (stock == null) return "";
        var active = "select l from InventoryLevel l where l.sku.product = p and l.sku.deletedAt is null and l.sku.active = true";
        return switch (stock) {
            case "out" -> " and not exists (" + active + " and l.available > 0)";
            case "low" -> " and exists (" + active + " and l.available > 0 and l.lowStockThreshold is not null and l.available <= l.lowStockThreshold)";
            default -> " and exists (" + active + " and l.available > 0 and (l.lowStockThreshold is null or l.available > l.lowStockThreshold))";
        };
    }

    private static List<String> parsePriceBands(String value) {
        if (value == null || value.isBlank()) return List.of();
        var allowed = java.util.Set.of("under100", "b100_300", "b301_500", "b501_1000", "over1000");
        return java.util.Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(part -> !part.isEmpty() && allowed.contains(part))
            .distinct()
            .toList();
    }

    private static String priceClause(List<String> bands) {
        if (bands == null || bands.isEmpty()) return "";
        var parts = new java.util.ArrayList<String>();
        for (var band : bands) {
            switch (band) {
                case "under100" -> parts.add("(p.basePrice is not null and p.basePrice < 100)");
                case "b100_300" -> parts.add("(p.basePrice is not null and p.basePrice >= 100 and p.basePrice <= 300)");
                case "b301_500" -> parts.add("(p.basePrice is not null and p.basePrice >= 301 and p.basePrice <= 500)");
                case "b501_1000" -> parts.add("(p.basePrice is not null and p.basePrice >= 501 and p.basePrice <= 1000)");
                case "over1000" -> parts.add("(p.basePrice is not null and p.basePrice > 1000)");
                default -> { }
            }
        }
        if (parts.isEmpty()) return "";
        return " and (" + String.join(" or ", parts) + ")";
    }

    /** Every order ends with p.id so paging is stable when the main key ties. */
    private static String orderClause(String sort) {
        return switch (sort) {
            case "oldest" -> " order by p.createdDateTime asc, p.id asc";
            case "updated" -> " order by p.modifiedDateTime desc, p.id asc";
            case "updated_asc" -> " order by p.modifiedDateTime asc, p.id asc";
            case "name" -> " order by lower(p.nameTh) asc, p.id asc";
            case "name_desc" -> " order by lower(p.nameTh) desc, p.id asc";
            case "price_asc" -> " order by p.basePrice asc nulls last, p.createdDateTime desc, p.id asc";
            case "price_desc" -> " order by p.basePrice desc nulls last, p.createdDateTime desc, p.id asc";
            case "stock" -> " order by (select coalesce(sum(l.available), 0) from InventoryLevel l where l.sku.product = p and l.sku.deletedAt is null) desc, p.createdDateTime desc, p.id asc";
            default -> " order by p.createdDateTime desc, p.id asc";
        };
    }

    private static Set<String> optionKeys(List<ProductSku> skus) {
        var used = new HashSet<String>();
        for (var sku : skus) {
            JsonMaps.read(sku.getOptionValues()).forEach((name, value) -> {
                if (name != null && value != null) used.add(name.trim().toLowerCase(Locale.ROOT) + "\n" + value.trim().toLowerCase(Locale.ROOT));
            });
        }
        return used;
    }

    private static List<CatalogDtos.OptionGroup> filterOptionGroups(List<CatalogDtos.OptionGroup> groups, Set<String> used) {
        var out = new ArrayList<CatalogDtos.OptionGroup>();
        for (var group : groups) {
            if (group == null || group.name() == null || group.values() == null) continue;
            var values = group.values().stream()
                .filter(value -> value != null && used.contains(group.name().trim().toLowerCase(Locale.ROOT) + "\n" + value.trim().toLowerCase(Locale.ROOT)))
                .toList();
            if (!values.isEmpty()) out.add(new CatalogDtos.OptionGroup(group.name(), values));
        }
        return out;
    }

    /** Drops values with no active SKU while keeping source and translated lists aligned. */
    private static List<CatalogDtos.OptionGroup> filterAlignedOptions(List<CatalogDtos.OptionGroup> source, List<CatalogDtos.OptionGroup> localized, Set<String> used) {
        if (source.size() != localized.size()) return filterOptionGroups(localized, used);
        var out = new ArrayList<CatalogDtos.OptionGroup>();
        for (var groupIndex = 0; groupIndex < source.size(); groupIndex++) {
            var group = source.get(groupIndex);
            var translated = localized.get(groupIndex);
            if (group == null || group.name() == null || group.values() == null || translated == null) continue;
            var values = new ArrayList<String>();
            var localValues = translated.values() == null ? List.<String>of() : translated.values();
            for (var valueIndex = 0; valueIndex < group.values().size(); valueIndex++) {
                var value = group.values().get(valueIndex);
                if (value == null || !used.contains(group.name().trim().toLowerCase(Locale.ROOT) + "\n" + value.trim().toLowerCase(Locale.ROOT))) continue;
                if (valueIndex < localValues.size()) values.add(localValues.get(valueIndex));
            }
            if (!values.isEmpty()) out.add(new CatalogDtos.OptionGroup(translated.name(), List.copyOf(values)));
        }
        return out;
    }

    private static List<CatalogDtos.OptionGroupResponse> filterGroupViews(List<CatalogDtos.OptionGroupResponse> groups, Set<String> used) {
        var out = new ArrayList<CatalogDtos.OptionGroupResponse>();
        for (var group : groups) {
            if (group == null || group.name() == null || group.values() == null) continue;
            var values = group.values().stream()
                .filter(value -> value != null && value.value() != null && used.contains(group.name().trim().toLowerCase(Locale.ROOT) + "\n" + value.value().trim().toLowerCase(Locale.ROOT)))
                .toList();
            if (!values.isEmpty()) out.add(new CatalogDtos.OptionGroupResponse(group.id(), group.name(), group.sortOrder(), values));
        }
        return out;
    }

    private static List<CatalogDtos.OptionGroup> localizeGroups(String entityId, List<CatalogDtos.OptionGroup> groups, ContentTranslationIndex options, String locale) {
        if (groups.isEmpty() || locale == null || "th".equals(locale)) return groups;
        var localized = new ArrayList<CatalogDtos.OptionGroup>();
        for (var groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            var group = groups.get(groupIndex);
            var values = group.values() == null ? List.<String>of() : group.values();
            var translatedValues = new ArrayList<String>();
            for (var valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                translatedValues.add(shown(options.resolve(entityId, "v" + groupIndex + "." + valueIndex, locale, values.get(valueIndex), null), values.get(valueIndex)));
            }
            localized.add(new CatalogDtos.OptionGroup(
                shown(options.resolve(entityId, "n" + groupIndex, locale, group.name(), null), group.name()),
                translatedValues
            ));
        }
        return localized;
    }

    private static Map<String, String> localizeOptionValues(List<CatalogDtos.OptionGroup> source, List<CatalogDtos.OptionGroup> localized, Map<String, String> values) {
        if (values == null || values.isEmpty() || source.size() != localized.size()) return values == null ? Map.of() : values;
        var translated = new LinkedHashMap<String, String>();
        for (var entry : values.entrySet()) {
            var groupIndex = -1;
            for (var index = 0; index < source.size(); index++) {
                if (source.get(index).name() != null && source.get(index).name().equals(entry.getKey())) {
                    groupIndex = index;
                    break;
                }
            }
            var name = groupIndex >= 0 ? localized.get(groupIndex).name() : entry.getKey();
            var value = entry.getValue();
            if (groupIndex >= 0 && source.get(groupIndex).values() != null) {
                var valueIndex = source.get(groupIndex).values().indexOf(entry.getValue());
                if (valueIndex >= 0 && valueIndex < localized.get(groupIndex).values().size()) {
                    value = localized.get(groupIndex).values().get(valueIndex);
                }
            }
            translated.put(name, value);
        }
        return translated;
    }

    private static String shown(String resolved, String thai) {
        if (resolved != null && !resolved.isBlank()) return resolved;
        return thai == null ? "" : thai;
    }

    private static String actorLabel(UserAccount user) {
        var first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        var last = user.getLastName() == null ? "" : user.getLastName().trim();
        var name = (first + " " + last).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static AuthException missingShop() { return notFound("SHOP_NOT_FOUND", "ไม่พบร้านค้า"); }
    private static AuthException notFound(String code, String message) { return new AuthException(HttpStatus.NOT_FOUND, code, message); }
    private static AuthException bad(String code, String message) { return new AuthException(HttpStatus.BAD_REQUEST, code, message); }
    private static AuthException conflict(String code, String message) { return new AuthException(HttpStatus.CONFLICT, code, message); }

    private record SearchResult(List<Product> items, int page, int size, long total) {}
}
