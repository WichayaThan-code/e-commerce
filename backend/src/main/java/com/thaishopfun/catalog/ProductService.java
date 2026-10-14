package com.thaishopfun.catalog;

import com.thaishopfun.auth.AuthException;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProductService {
    private static final int MAX_IMAGES = 9;
    static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
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
        ProductVariantService variants
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
    }

    @Transactional(readOnly = true)
    public CatalogDtos.PageResponse<CatalogDtos.MerchantProductResponse> list(UUID userId, Long shopId, String q, String status, Long categoryId, String brand, String stock, String sort, Integer page, Integer size) {
        var shop = requireSellableShop(userId, shopId);
        var result = search(shop.getId(), q, parseStatus(status), categoryId, blank(brand), parseStock(stock), parseSort(sort), page, size, false, List.of());
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, result.items().stream().map(product -> product.getId().toString()).toList());
        return new CatalogDtos.PageResponse<>(result.items().stream().map(product -> toMerchant(product, index)).toList(), result.page(), result.size(), result.total());
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
        validate(shop.getId(), request, null);
        var type = requireType(request.productType());
        var name = requireName(request.nameTh());
        var product = new Product(shop, uniqueSlug(shop.getId(), request.slug(), request.nameEn(), name, null), name, type);
        product.stamp(userId);
        apply(product, request, userId);
        // Assigned UUID ids: repository.save() would merge() a copy and later changes to `product`
        // (e.g. fillBasePrice) would never reach the database. persist() keeps this instance managed.
        entities.persist(product);
        var synced = syncSkus(product, request.skus(), userId);
        variants.sync(product, request, synced.ordered(), userId);
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
        validate(shopId, request, product);
        product.stamp(userId);
        apply(product, request, userId);
        product.setSlug(uniqueSlug(shopId, request.slug(), request.nameEn(), product.getNameTh(), product.getId()));
        var synced = syncSkus(product, request.skus(), userId);
        variants.sync(product, request, synced.ordered(), userId);
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
    public void addImportedImage(UUID userId, Long shopId, UUID productId, byte[] bytes) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        if (images.countByProduct_Id(product.getId()) >= MAX_IMAGES) throw bad("IMAGE_LIMIT", "สินค้าหนึ่งรายการมีรูปได้ไม่เกิน 9 รูป");
        var stored = storage.storeBytes(product.getId(), bytes, "PRODUCT");
        deleteFileOnRollback(stored.path());
        rememberImage(userId, product, stored);
    }

    private void rememberImage(UUID userId, Product product, MerchantStorageService.StoredFile stored) {
        if (stored.contentType() == null || !stored.contentType().startsWith("image/")) {
            storage.deleteQuietly(stored.path());
            throw bad("IMAGE_TYPE", "รองรับเฉพาะไฟล์ JPG, PNG หรือ WEBP");
        }
        var current = images.findByProduct_IdOrderBySortOrderAsc(product.getId());
        var image = new ProductImage(product, stored.path(), current.size(), current.stream().noneMatch(ProductImage::isPrimaryImage));
        image.stamp(userId);
        entities.persist(image);
        product.stamp(userId);
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
        if (!type.equals("RECEIVE") && !type.equals("ADJUST")) throw bad("INVALID_ADJUSTMENT", "ปรับสต็อกได้เฉพาะ RECEIVE หรือ ADJUST");
        if (type.equals("ADJUST") && (request.reason() == null || request.reason().isBlank())) throw bad("STOCK_REASON_REQUIRED", "การปรับยอดต้องระบุเหตุผล");
        if (type.equals("RECEIVE") && request.quantityDelta() <= 0) throw bad("INVALID_ADJUSTMENT", "รับเข้าต้องเป็นจำนวนมากกว่าศูนย์");
        if (type.equals("ADJUST") && request.quantityDelta() == 0) throw bad("INVALID_ADJUSTMENT", "จำนวนที่ปรับต้องไม่เป็นศูนย์");
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
        var before = level.getOnHand();
        var after = before + request.quantityDelta();
        if (after < 0) throw bad("INSUFFICIENT_STOCK", "สต็อกไม่พอสำหรับการปรับยอด");
        level.setOnHand(after);
        level.stamp(userId);
        var entry = new InventoryLedgerEntry(sku, type, request.quantityDelta(), before, after, blank(request.reason()), key, userId);
        entry.stamp(userId);
        ledger.save(entry);
        try {
            levels.saveAndFlush(level);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw conflict("STOCK_CONFLICT", "สต็อกถูกแก้ไขไปแล้ว โหลดข้อมูลใหม่แล้วลองอีกครั้ง");
        }
        return stockResponse(level, type);
    }

    @Transactional(readOnly = true)
    public CatalogDtos.PageResponse<CatalogDtos.PublicProductResponse> publicList(String shopSlug, String q, Long categoryId, String sort, Integer page, Integer size, String priceBands) {
        var shop = requirePublicShop(shopSlug);
        var result = search(shop.getId(), q, ProductStatus.PUBLISHED, categoryId, null, null, parseSort(sort), page, size, true, parsePriceBands(priceBands));
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, result.items().stream().map(product -> product.getId().toString()).toList());
        return new CatalogDtos.PageResponse<>(result.items().stream().map(product -> toPublic(product, index)).toList(), result.page(), result.size(), result.total());
    }

    @Transactional(readOnly = true)
    public CatalogDtos.PublicProductResponse publicGet(String shopSlug, String productSlug) {
        var shop = requirePublicShop(shopSlug);
        var product = products.findByShop_SlugAndSlugAndDeletedAtIsNull(shop.getSlug(), productSlug)
            .filter(item -> item.getStatus() == ProductStatus.PUBLISHED)
            .orElseThrow(() -> notFound("PRODUCT_NOT_FOUND", "ไม่พบสินค้า"));
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, List.of(product.getId().toString()));
        return toPublic(product, index);
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
        var existingIds = existing.stream().map(ProductSku::getId).collect(java.util.stream.Collectors.toSet());
        var codes = new HashMap<String, Integer>();
        var barcodes = new HashMap<String, Integer>();
        for (var index = 0; index < incoming.size(); index++) {
            var sku = incoming.get(index);
            var prefix = "skus." + index + ".";
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
                    add.accept(ValidationMessages.violation(prefix + "skuCode", "SKU_CODE_DUPLICATE"));
                } else if (selfId == null ? skus.existsByShop_ShopIdAndSkuCode(shopId, code) : skus.existsByShop_ShopIdAndSkuCodeAndIdNot(shopId, code, selfId)) {
                    add.accept(ValidationMessages.violation(prefix + "skuCode", "SKU_CODE_TAKEN"));
                }
            }
            var barcode = blank(sku.barcode());
            if (barcode != null && barcode.length() <= 64 && barcode.matches("[A-Za-z0-9-]+")) {
                if (barcodes.putIfAbsent(barcode.toLowerCase(Locale.ROOT), index) != null) {
                    add.accept(ValidationMessages.violation(prefix + "barcode", "BARCODE_DUPLICATE"));
                } else if (selfId == null
                    ? skus.existsByShop_ShopIdAndBarcodeIgnoreCaseAndDeletedAtIsNull(shopId, barcode)
                    : skus.existsByShop_ShopIdAndBarcodeIgnoreCaseAndDeletedAtIsNullAndIdNot(shopId, barcode, selfId)) {
                    add.accept(ValidationMessages.violation(prefix + "barcode", "BARCODE_TAKEN"));
                }
            }
        }
        if (!problems.isEmpty()) throw new ValidationException(List.copyOf(problems.values()));
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
    private SyncResult syncSkus(Product product, List<CatalogDtos.SkuWriteRequest> requests, UUID userId) {
        var opening = new ArrayList<OpeningStock>();
        var ordered = new ArrayList<ProductSku>();
        var incoming = requests == null ? List.<CatalogDtos.SkuWriteRequest>of() : requests;
        if (product.getProductType() == ProductType.SIMPLE && incoming.size() != 1) throw bad("SIMPLE_SKU_COUNT", "สินค้าแบบเดียวต้องมี 1 รหัสสินค้า");
        if (product.getProductType() == ProductType.VARIABLE && incoming.isEmpty()) throw bad("SKU_REQUIRED", "สินค้าแบบตัวเลือกต้องมีอย่างน้อย 1 รหัสสินค้า");
        var existing = skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId());
        var kept = new HashSet<UUID>();
        if (product.getProductType() == ProductType.SIMPLE && existing.size() == 1 && incoming.get(0).id() == null) {
            applySku(existing.get(0), incoming.get(0), product.getCurrency(), userId);
            kept.add(existing.get(0).getId());
            ordered.add(existing.get(0));
        } else {
            for (var request : incoming) {
                if (request.id() == null) {
                    var created = new ProductSku(product, requireSkuCode(product.getShop().getId(), request.skuCode(), null), requirePrice(request.price()));
                    created.stamp(userId);
                    applySku(created, request, product.getCurrency(), userId);
                    entities.persist(created);
                    var level = new InventoryLevel(created);
                    level.stamp(userId);
                    entities.persist(level);
                    kept.add(created.getId());
                    ordered.add(created);
                    if (request.initialStock() != null && request.initialStock() > 0) opening.add(new OpeningStock(created, request.initialStock()));
                } else {
                    var sku = existing.stream().filter(item -> item.getId().equals(request.id())).findFirst()
                        .orElseThrow(() -> notFound("SKU_NOT_FOUND", "ไม่พบรหัสสินค้า"));
                    applySku(sku, request, product.getCurrency(), userId);
                    kept.add(sku.getId());
                    ordered.add(sku);
                }
            }
        }
        var now = Instant.now();
        for (var sku : existing) {
            if (!kept.contains(sku.getId())) {
                sku.setDeletedAt(now);
                sku.setActive(false);
                sku.stamp(userId);
            }
        }
        return new SyncResult(opening, ordered);
    }

    private void applySku(ProductSku sku, CatalogDtos.SkuWriteRequest request, String currency, UUID userId) {
        sku.setSkuCode(requireSkuCode(sku.getShop().getId(), request.skuCode(), sku.getId()));
        sku.setPrice(requirePrice(request.price()));
        sku.setCostPrice(money(request.costPrice(), true));
        sku.setCompareAtPrice(money(request.compareAtPrice(), true));
        if (sku.getCompareAtPrice() != null && sku.getCompareAtPrice().compareTo(sku.getPrice()) < 0) {
            throw bad("COMPARE_PRICE_INVALID", ValidationMessages.text("COMPARE_PRICE_INVALID"));
        }
        sku.setBarcode(blank(request.barcode()));
        sku.setOptionLabel(blank(request.optionLabel()));
        sku.setOptionValues(JsonMaps.write(request.optionValues()));
        sku.setCurrency(currency);
        sku.setWeightGrams(weight(request.weightGrams()));
        sku.setActive(true);
        sku.stamp(userId);
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

    private ProductSpreadsheet.SheetRow sheetRow(Product product, ProductSku sku, InventoryLevel level) {
        var category = product.getCategory() == null ? null : product.getCategory().getNameTh();
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
            null
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
        var entityId = product.getId().toString();
        var skuRows = skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId());
        var stock = skuRows.isEmpty() ? List.<InventoryLevel>of() : levels.findBySku_IdIn(skuRows.stream().map(ProductSku::getId).toList());
        var options = variants.skuOptions(skuRows.stream().map(ProductSku::getId).toList());
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
                    level == null ? null : level.getLowStockThreshold(), level == null ? 0 : level.getVersion(),
                    sku.getImage() == null ? null : sku.getImage().getId(), options.getOrDefault(sku.getId(), List.of()));
            }).toList(),
            imageResponses(product, true),
            toMeta(index.meta(entityId, "name", "en")),
            toMeta(index.meta(entityId, "name", "zh")),
            toMeta(index.meta(entityId, "description", "en")),
            toMeta(index.meta(entityId, "description", "zh")),
            variants.groupViews(product.getId())
        );
    }

    private CatalogDtos.PublicProductResponse toPublic(Product product, ContentTranslationIndex index) {
        var entityId = product.getId().toString();
        var skuRows = skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId()).stream().filter(ProductSku::isActive).toList();
        var stock = skuRows.isEmpty() ? List.<InventoryLevel>of() : levels.findBySku_IdIn(skuRows.stream().map(ProductSku::getId).toList());
        var options = variants.skuOptions(skuRows.stream().map(ProductSku::getId).toList());
        return new CatalogDtos.PublicProductResponse(
            product.getId(), product.getSlug(), product.getNameTh(),
            index.resolve(entityId, "name", "en", product.getNameTh(), product.getNameEn()),
            index.resolve(entityId, "name", "zh", product.getNameTh(), product.getNameZh()),
            product.getDescriptionTh(),
            index.resolve(entityId, "description", "en", product.getDescriptionTh(), product.getDescriptionEn()),
            index.resolve(entityId, "description", "zh", product.getDescriptionTh(), product.getDescriptionZh()),
            product.getCategory() == null ? null : product.getCategory().getId(), product.getBrand(), product.getBasePrice(), product.getCompareAtPrice(), trim(product.getCurrency()),
            skuRows.stream().map(sku -> {
                var level = stock.stream().filter(item -> item.getSku().getId().equals(sku.getId())).findFirst().orElse(null);
                return new CatalogDtos.PublicSkuResponse(sku.getId(), sku.getSkuCode(), sku.getOptionLabel(), JsonMaps.read(sku.getOptionValues()),
                    sku.getPrice(), sku.getCompareAtPrice(), trim(sku.getCurrency()), level == null ? 0 : level.getAvailable(),
                    sku.getImage() == null ? null : sku.getImage().getId(), options.getOrDefault(sku.getId(), List.of()));
            }).toList(),
            imageResponses(product, false),
            variants.groupViews(product.getId())
        );
    }

    private static CatalogDtos.TranslationMeta toMeta(ContentTranslationIndex.TranslationFieldMeta meta) {
        return meta == null ? null : new CatalogDtos.TranslationMeta(meta.origin(), meta.status());
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.LedgerEntryResponse> ledger(UUID userId, Long shopId, UUID productId) {
        requireSellableShop(userId, shopId);
        requireProduct(shopId, productId);
        return ledger.findBySku_Product_IdOrderByCreatedDateTimeDesc(productId).stream()
            .map(entry -> new CatalogDtos.LedgerEntryResponse(
                entry.getId(), entry.getSku().getId(), entry.getSku().getSkuCode(), entry.getEntryType(), entry.getQuantityDelta(),
                entry.getOnHandBefore(), entry.getOnHandAfter(), entry.getReason(), entry.getCreatedDateTime()))
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
        return new CatalogDtos.StockResponse(level.getSku().getId(), type, level.getOnHand(), level.getAvailable(), level.getVersion());
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

    private String requireSkuCode(Long shopId, String value, UUID currentId) {
        if (value == null || value.isBlank()) throw bad("SKU_CODE_REQUIRED", "กรุณาระบุรหัสสินค้า");
        var code = value.trim();
        if (code.length() > 64) throw bad("SKU_CODE_REQUIRED", "รหัสสินค้ายาวเกินกำหนด");
        var taken = currentId == null
            ? skus.existsByShop_ShopIdAndSkuCode(shopId, code)
            : skus.existsByShop_ShopIdAndSkuCodeAndIdNot(shopId, code, currentId);
        if (taken) throw conflict("SKU_CODE_TAKEN", "รหัสสินค้านี้ซ้ำในร้าน");
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

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static AuthException missingShop() { return notFound("SHOP_NOT_FOUND", "ไม่พบร้านค้า"); }
    private static AuthException notFound(String code, String message) { return new AuthException(HttpStatus.NOT_FOUND, code, message); }
    private static AuthException bad(String code, String message) { return new AuthException(HttpStatus.BAD_REQUEST, code, message); }
    private static AuthException conflict(String code, String message) { return new AuthException(HttpStatus.CONFLICT, code, message); }

    private record SearchResult(List<Product> items, int page, int size, long total) {}
}
