package com.thaishopfun.catalog;

import com.thaishopfun.auth.AuthException;
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
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProductService {
    private static final int MAX_IMAGES = 9;
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
        TranslationService translations
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
    }

    @Transactional(readOnly = true)
    public CatalogDtos.PageResponse<CatalogDtos.MerchantProductResponse> list(UUID userId, Long shopId, String q, String status, Long categoryId, String stock, String sort, Integer page, Integer size) {
        var shop = requireSellableShop(userId, shopId);
        var result = search(shop.getId(), q, parseStatus(status), categoryId, parseStock(stock), parseSort(sort), page, size, false, List.of());
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, result.items().stream().map(product -> product.getId().toString()).toList());
        return new CatalogDtos.PageResponse<>(result.items().stream().map(product -> toMerchant(product, index)).toList(), result.page(), result.size(), result.total());
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
        var type = requireType(request.productType());
        var name = requireName(request.nameTh());
        var product = new Product(shop, uniqueSlug(shop.getId(), request.slug(), request.nameEn(), name, null), name, type);
        product.stamp(userId);
        apply(product, request, userId);
        products.save(product);
        syncSkus(product, request.skus(), userId);
        fillBasePrice(product);
        applyOpeningStock(userId, shop.getId(), product, request.skus());
        translationBridge.onProductSaved(product, request);
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, List.of(product.getId().toString()));
        return toMerchant(product, index);
    }

    @Transactional
    public CatalogDtos.MerchantProductResponse update(UUID userId, Long shopId, UUID productId, CatalogDtos.ProductWriteRequest request) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        product.stamp(userId);
        apply(product, request, userId);
        product.setSlug(uniqueSlug(shopId, request.slug(), request.nameEn(), product.getNameTh(), product.getId()));
        syncSkus(product, request.skus(), userId);
        fillBasePrice(product);
        translationBridge.onProductSaved(product, request);
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, List.of(product.getId().toString()));
        return toMerchant(product, index);
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
        if (images.countByProduct_Id(product.getId()) < 1 || product.getBasePrice() == null) {
            throw bad("PUBLISH_INCOMPLETE", "เผยแพร่ได้เมื่อมีรูปอย่างน้อย 1 รูปและมีราคา");
        }
        if (skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId()).stream().noneMatch(ProductSku::isActive)) {
            throw bad("PUBLISH_INCOMPLETE", "เผยแพร่ได้เมื่อมีรหัสสินค้าที่เปิดขาย");
        }
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
        rememberImage(userId, product, stored);
        return merchantView(product);
    }

    @Transactional
    public void addImportedImage(UUID userId, Long shopId, UUID productId, byte[] bytes) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        if (images.countByProduct_Id(product.getId()) >= MAX_IMAGES) throw bad("IMAGE_LIMIT", "สินค้าหนึ่งรายการมีรูปได้ไม่เกิน 9 รูป");
        rememberImage(userId, product, storage.storeBytes(product.getId(), bytes, "PRODUCT"));
    }

    private void rememberImage(UUID userId, Product product, MerchantStorageService.StoredFile stored) {
        if (stored.contentType() == null || !stored.contentType().startsWith("image/")) {
            storage.deleteQuietly(stored.path());
            throw bad("IMAGE_TYPE", "รองรับเฉพาะไฟล์ JPG, PNG หรือ WEBP");
        }
        var current = images.findByProduct_IdOrderBySortOrderAsc(product.getId());
        var image = new ProductImage(product, stored.path(), current.size(), current.stream().noneMatch(ProductImage::isPrimaryImage));
        image.stamp(userId);
        images.save(image);
    }

    @Transactional
    public CatalogDtos.MerchantProductResponse deleteImage(UUID userId, Long shopId, UUID productId, UUID imageId) {
        requireSellableShop(userId, shopId);
        var product = requireProduct(shopId, productId);
        var image = images.findByIdAndProduct_Id(imageId, product.getId()).orElseThrow(() -> notFound("IMAGE_NOT_FOUND", "ไม่พบรูปสินค้า"));
        var wasPrimary = image.isPrimaryImage();
        storage.deleteQuietly(image.getStoragePath());
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
        var result = search(shop.getId(), q, ProductStatus.PUBLISHED, categoryId, null, parseSort(sort), page, size, true, parsePriceBands(priceBands));
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
        if (product.getCompareAtPrice() != null && product.getBasePrice() != null && product.getCompareAtPrice().compareTo(product.getBasePrice()) <= 0) {
            throw bad("COMPARE_PRICE_INVALID", "ราคาเปรียบเทียบต้องมากกว่าราคาขาย");
        }
    }

    private void syncSkus(Product product, List<CatalogDtos.SkuWriteRequest> requests, UUID userId) {
        var incoming = requests == null ? List.<CatalogDtos.SkuWriteRequest>of() : requests;
        if (product.getProductType() == ProductType.SIMPLE && incoming.size() != 1) throw bad("SIMPLE_SKU_COUNT", "สินค้าแบบเดียวต้องมี 1 รหัสสินค้า");
        if (product.getProductType() == ProductType.VARIABLE && incoming.isEmpty()) throw bad("SKU_REQUIRED", "สินค้าแบบตัวเลือกต้องมีอย่างน้อย 1 รหัสสินค้า");
        var existing = skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId());
        var kept = new HashSet<UUID>();
        if (product.getProductType() == ProductType.SIMPLE && existing.size() == 1 && incoming.get(0).id() == null) {
            applySku(existing.get(0), incoming.get(0), product.getCurrency(), userId);
            kept.add(existing.get(0).getId());
        } else {
            for (var request : incoming) {
                if (request.id() == null) {
                    var created = new ProductSku(product, requireSkuCode(product.getShop().getId(), request.skuCode(), null), requirePrice(request.price()));
                    created.stamp(userId);
                    applySku(created, request, product.getCurrency(), userId);
                    skus.save(created);
                    var level = new InventoryLevel(created);
                    level.stamp(userId);
                    levels.save(level);
                    kept.add(created.getId());
                } else {
                    var sku = existing.stream().filter(item -> item.getId().equals(request.id())).findFirst()
                        .orElseThrow(() -> notFound("SKU_NOT_FOUND", "ไม่พบรหัสสินค้า"));
                    applySku(sku, request, product.getCurrency(), userId);
                    kept.add(sku.getId());
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
    }

    private void applySku(ProductSku sku, CatalogDtos.SkuWriteRequest request, String currency, UUID userId) {
        sku.setSkuCode(requireSkuCode(sku.getShop().getId(), request.skuCode(), sku.getId()));
        sku.setPrice(requirePrice(request.price()));
        sku.setCostPrice(money(request.costPrice(), true));
        sku.setCompareAtPrice(money(request.compareAtPrice(), true));
        if (sku.getCompareAtPrice() != null && sku.getCompareAtPrice().compareTo(sku.getPrice()) <= 0) {
            throw bad("COMPARE_PRICE_INVALID", "ราคาเปรียบเทียบต้องมากกว่าราคาขาย");
        }
        sku.setBarcode(blank(request.barcode()));
        sku.setOptionLabel(blank(request.optionLabel()));
        sku.setOptionValues(JsonMaps.write(request.optionValues()));
        sku.setCurrency(currency);
        sku.setWeightGrams(weight(request.weightGrams()));
        sku.setActive(true);
        sku.stamp(userId);
    }

    private void applyOpeningStock(UUID userId, Long shopId, Product product, List<CatalogDtos.SkuWriteRequest> requests) {
        if (requests == null) return;
        var saved = skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId());
        for (var request : requests) {
            if (request.initialStock() == null || request.initialStock() <= 0 || request.skuCode() == null) continue;
            var code = request.skuCode().trim();
            var sku = saved.stream().filter(item -> item.getSkuCode().equals(code)).findFirst().orElse(null);
            if (sku == null) continue;
            adjustStock(userId, shopId, sku.getId(), new CatalogDtos.StockAdjustRequest(
                "RECEIVE", request.initialStock(), "นำเข้า", 0, "import-" + sku.getId()
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
        if (product.getCompareAtPrice() != null && product.getBasePrice() != null && product.getCompareAtPrice().compareTo(product.getBasePrice()) <= 0) {
            throw bad("COMPARE_PRICE_INVALID", "ราคาเปรียบเทียบต้องมากกว่าราคาขาย");
        }
    }

    private SearchResult search(Long shopId, String q, ProductStatus status, Long categoryId, String stock, String sort, Integer page, Integer size, boolean publishedOnly, List<String> priceBands) {
        var pageNumber = page == null || page < 0 ? 0 : page;
        var pageSize = size == null || size < 1 ? 20 : Math.min(size, 50);
        var where = new StringBuilder(" where p.shop.shopId = :shopId and p.deletedAt is null");
        if (publishedOnly || status != null) where.append(" and p.status = :status");
        if (categoryId != null) where.append(" and p.category.id = :categoryId");
        if (q != null && !q.isBlank()) where.append(" and (lower(p.nameTh) like :q or lower(coalesce(p.nameEn, '')) like :q or lower(coalesce(p.nameZh, '')) like :q or lower(p.slug) like :q or exists (select s from ProductSku s where s.product = p and s.deletedAt is null and lower(s.skuCode) like :q))");
        where.append(stockClause(stock));
        where.append(priceClause(priceBands));
        var query = entities.createQuery("select p from Product p" + where + orderClause(sort), Product.class);
        var count = entities.createQuery("select count(p) from Product p" + where, Long.class);
        bind(query, count, shopId, q, publishedOnly ? ProductStatus.PUBLISHED : status, categoryId);
        var total = count.getSingleResult();
        var items = query.setFirstResult(pageNumber * pageSize).setMaxResults(pageSize).getResultList();
        return new SearchResult(items, pageNumber, pageSize, total);
    }

    private void bind(jakarta.persistence.Query query, jakarta.persistence.Query count, Long shopId, String q, ProductStatus status, Long categoryId) {
        for (var target : List.of(query, count)) {
            target.setParameter("shopId", shopId);
            if (status != null) target.setParameter("status", status);
            if (categoryId != null) target.setParameter("categoryId", categoryId);
            if (q != null && !q.isBlank()) target.setParameter("q", "%" + q.trim().toLowerCase(Locale.ROOT) + "%");
        }
    }

    private CatalogDtos.MerchantProductResponse merchantView(Product product) {
        var index = translations.loadIndex(TranslationEntityType.PRODUCT, List.of(product.getId().toString()));
        return toMerchant(product, index);
    }

    private CatalogDtos.MerchantProductResponse toMerchant(Product product, ContentTranslationIndex index) {
        var entityId = product.getId().toString();
        var skuRows = skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId());
        var stock = skuRows.isEmpty() ? List.<InventoryLevel>of() : levels.findBySku_IdIn(skuRows.stream().map(ProductSku::getId).toList());
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
                    level == null ? null : level.getLowStockThreshold(), level == null ? 0 : level.getVersion());
            }).toList(),
            imageResponses(product, true),
            toMeta(index.meta(entityId, "name", "en")),
            toMeta(index.meta(entityId, "name", "zh")),
            toMeta(index.meta(entityId, "description", "en")),
            toMeta(index.meta(entityId, "description", "zh"))
        );
    }

    private CatalogDtos.PublicProductResponse toPublic(Product product, ContentTranslationIndex index) {
        var entityId = product.getId().toString();
        var skuRows = skus.findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(product.getId()).stream().filter(ProductSku::isActive).toList();
        var stock = skuRows.isEmpty() ? List.<InventoryLevel>of() : levels.findBySku_IdIn(skuRows.stream().map(ProductSku::getId).toList());
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
                    sku.getPrice(), sku.getCompareAtPrice(), trim(sku.getCurrency()), level == null ? 0 : level.getAvailable());
            }).toList(),
            imageResponses(product, false)
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
        if (shop.getUser() == null || shop.getUser().getId() == null || !shop.getUser().getId().equals(userId)) throw missingShop();
        if (shop.getStatus() != ShopStatus.APPROVED) throw new AuthException(HttpStatus.FORBIDDEN, "SHOP_NOT_APPROVED", "ร้านนี้ยังเปิดขายไม่ได้");
        return shop;
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

    private static String parseSort(String value) {
        if (value == null || value.isBlank()) return "newest";
        var sort = value.trim().toLowerCase(Locale.ROOT);
        if (!sort.equals("newest") && !sort.equals("name") && !sort.equals("price_asc") && !sort.equals("price_desc") && !sort.equals("stock")) {
            throw bad("INVALID_SORT", "การเรียงลำดับไม่ถูกต้อง");
        }
        return sort;
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

    private static String orderClause(String sort) {
        return switch (sort) {
            case "name" -> " order by p.nameTh asc";
            case "price_asc" -> " order by p.basePrice asc nulls last, p.createdDateTime desc";
            case "price_desc" -> " order by p.basePrice desc nulls last, p.createdDateTime desc";
            case "stock" -> " order by (select coalesce(sum(l.available), 0) from InventoryLevel l where l.sku.product = p) desc, p.createdDateTime desc";
            default -> " order by p.createdDateTime desc";
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
