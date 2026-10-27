package com.thaishopfun.catalog.collection;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.catalog.CatalogDtos;
import com.thaishopfun.catalog.Product;
import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.catalog.ProductService;
import com.thaishopfun.catalog.ProductStatus;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.common.ValidationMessages;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.theme.ShopThemeMediaKind;
import com.thaishopfun.merchant.theme.ShopThemeMediaRepository;
import com.thaishopfun.merchant.theme.ShopThemeMediaService;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * V41 shop collections. Merchant side: CRUD + ordering of collections and of their products (every product must be
 * a non-deleted product of the same shop). Public side: only published collections of APPROVED shops, and only
 * PUBLISHED / non-deleted products of that shop, rendered with the same card DTO as the product list.
 */
@Service
public class ShopCollectionService {
    static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int NAME_MAX = 120;
    private static final int SLUG_MAX = 80;
    private static final int DESCRIPTION_MAX = 1000;
    private static final int PUBLIC_PAGE_MAX = 60;

    private final ShopCollectionRepository collections;
    private final ShopCollectionProductRepository items;
    private final ProductRepository products;
    private final ProductService productService;
    private final ShopThemeMediaRepository media;
    private final CatalogShopAccess access;
    private final CollectionProperties properties;
    private final EntityManager entityManager;

    public ShopCollectionService(
        ShopCollectionRepository collections, ShopCollectionProductRepository items, ProductRepository products,
        ProductService productService, ShopThemeMediaRepository media, CatalogShopAccess access,
        CollectionProperties properties, EntityManager entityManager
    ) {
        this.collections = collections;
        this.items = items;
        this.products = products;
        this.productService = productService;
        this.media = media;
        this.access = access;
        this.properties = properties;
        this.entityManager = entityManager;
    }

    // ---------------------------------------------------------------- merchant

    @Transactional(readOnly = true)
    public List<CollectionDtos.MerchantCollectionResponse> list(UUID userId, Long shopId) {
        var shop = access.merchantShop(userId, shopId);
        var rows = collections.findByShopIdOrderBySortOrderAscCreatedDateTimeAsc(shop.getUuid());
        var counts = counts(rows.stream().map(ShopCollection::getId).toList());
        return rows.stream().map(row -> toMerchant(shop, row, counts.getOrDefault(row.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public CollectionDtos.MerchantCollectionDetail get(UUID userId, Long shopId, UUID id) {
        var shop = access.merchantShop(userId, shopId);
        return detail(shop, requireCollection(shop, id));
    }

    @Transactional
    public CollectionDtos.MerchantCollectionDetail create(UUID userId, Long shopId, CollectionDtos.CollectionWriteRequest request) {
        var shop = access.merchantShop(userId, shopId);
        var total = collections.countByShopId(shop.getUuid());
        if (total >= properties.maxPerShop()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "COLLECTION_LIMIT_REACHED", ValidationMessages.text("COLLECTION_LIMIT_REACHED"));
        }
        var row = new ShopCollection(shop.getUuid(), (int) total);
        apply(shop, row, request, userId);
        return detail(shop, collections.save(row));
    }

    @Transactional
    public CollectionDtos.MerchantCollectionDetail update(UUID userId, Long shopId, UUID id, CollectionDtos.CollectionWriteRequest request) {
        var shop = access.merchantShop(userId, shopId);
        var row = requireCollection(shop, id);
        apply(shop, row, request, userId);
        return detail(shop, collections.save(row));
    }

    @Transactional
    public void delete(UUID userId, Long shopId, UUID id) {
        var shop = access.merchantShop(userId, shopId);
        var row = requireCollection(shop, id);
        items.deleteByCollection(row.getId());
        collections.delete(row);
    }

    @Transactional
    public List<CollectionDtos.MerchantCollectionResponse> reorder(UUID userId, Long shopId, CollectionDtos.OrderRequest request) {
        var shop = access.merchantShop(userId, shopId);
        var rows = collections.findByShopIdOrderBySortOrderAscCreatedDateTimeAsc(shop.getUuid());
        var ids = request == null || request.ids() == null ? List.<UUID>of() : request.ids();
        var byId = rows.stream().collect(Collectors.toMap(ShopCollection::getId, Function.identity()));
        if (ids.size() != rows.size() || new HashSet<>(ids).size() != ids.size() || !byId.keySet().containsAll(ids)) {
            throw new ValidationException(List.of(ValidationMessages.violation("ids", "COLLECTION_ORDER_INVALID")));
        }
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).moveTo(i);
        collections.saveAll(rows);
        return list(userId, shopId);
    }

    @Transactional
    public CollectionDtos.MerchantCollectionDetail addProducts(UUID userId, Long shopId, UUID id, CollectionDtos.ProductIdsRequest request) {
        var shop = access.merchantShop(userId, shopId);
        var row = requireCollection(shop, id);
        var wanted = new LinkedHashSet<>(productIds(request));
        var existing = items.findByIdCollectionIdOrderBySortOrderAsc(row.getId());
        var present = existing.stream().map(ShopCollectionProduct::getProductId).collect(Collectors.toSet());
        wanted.removeAll(present);
        if (existing.size() + wanted.size() > properties.maxProductsPerCollection()) throw productLimit();
        requireShopProducts(shopId, wanted);
        int next = existing.stream().mapToInt(ShopCollectionProduct::getSortOrder).max().orElse(-1) + 1;
        var added = new ArrayList<ShopCollectionProduct>();
        for (var productId : wanted) added.add(new ShopCollectionProduct(row.getId(), productId, next++));
        items.saveAll(added);
        row.stamp(userId);
        return detail(shop, row);
    }

    /** Replace the whole ordered product list (drag-and-drop order + removals in one call). */
    @Transactional
    public CollectionDtos.MerchantCollectionDetail replaceProducts(UUID userId, Long shopId, UUID id, CollectionDtos.ProductIdsRequest request) {
        var shop = access.merchantShop(userId, shopId);
        var row = requireCollection(shop, id);
        var ordered = productIds(request);
        if (new HashSet<>(ordered).size() != ordered.size()) {
            throw new ValidationException(List.of(ValidationMessages.violation("productIds", "COLLECTION_ORDER_INVALID")));
        }
        if (ordered.size() > properties.maxProductsPerCollection()) throw productLimit();
        var existing = items.findByIdCollectionIdOrderBySortOrderAsc(row.getId()).stream()
            .collect(Collectors.toMap(ShopCollectionProduct::getProductId, Function.identity()));
        var fresh = ordered.stream().filter(productId -> !existing.containsKey(productId)).toList();
        requireShopProducts(shopId, fresh);
        var keep = new HashSet<>(ordered);
        existing.values().stream().filter(item -> !keep.contains(item.getProductId())).forEach(items::delete);
        var save = new ArrayList<ShopCollectionProduct>();
        for (int i = 0; i < ordered.size(); i++) {
            var item = existing.get(ordered.get(i));
            if (item == null) item = new ShopCollectionProduct(row.getId(), ordered.get(i), i);
            else item.moveTo(i);
            save.add(item);
        }
        items.saveAll(save);
        row.stamp(userId);
        return detail(shop, row);
    }

    @Transactional
    public CollectionDtos.MerchantCollectionDetail removeProduct(UUID userId, Long shopId, UUID id, UUID productId) {
        var shop = access.merchantShop(userId, shopId);
        var row = requireCollection(shop, id);
        items.findById(new ShopCollectionProduct.Key(row.getId(), productId)).ifPresent(items::delete);
        row.stamp(userId);
        return detail(shop, row);
    }

    // ---------------------------------------------------------------- public

    @Transactional(readOnly = true)
    public List<CollectionDtos.PublicCollectionResponse> publicList(String slug, String locale) {
        var shop = access.publicShop(slug);
        var rows = collections.findByShopIdAndPublishedTrueOrderBySortOrderAscCreatedDateTimeAsc(shop.getUuid());
        var counts = activeCounts(shop, rows.stream().map(ShopCollection::getId).toList());
        return rows.stream().map(row -> toPublic(shop, row, counts.getOrDefault(row.getId(), 0L), locale)).toList();
    }

    @Transactional(readOnly = true)
    public CollectionDtos.PublicCollectionDetail publicGet(String slug, String collectionSlug, Integer page, Integer size, String locale) {
        var shop = access.publicShop(slug);
        var normalized = collectionSlug == null ? "" : collectionSlug.trim().toLowerCase(Locale.ROOT);
        var row = collections.findByShopIdAndSlugAndPublishedTrue(shop.getUuid(), normalized).orElseThrow(ShopCollectionService::notFound);
        int pageIndex = Math.max(0, page == null ? 0 : page);
        int pageSize = Math.min(PUBLIC_PAGE_MAX, Math.max(1, size == null ? 24 : size));
        long total = entityManager.createQuery("""
                select count(p) from ShopCollectionProduct cp, Product p
                where cp.id.collectionId = :collectionId and p.id = cp.id.productId
                  and p.shop.id = :shopId and p.status = :status and p.deletedAt is null
                """, Long.class)
            .setParameter("collectionId", row.getId())
            .setParameter("shopId", shop.getUuid())
            .setParameter("status", ProductStatus.PUBLISHED)
            .getSingleResult();
        List<Product> pageItems = total == 0 ? List.of() : entityManager.createQuery("""
                select p from ShopCollectionProduct cp, Product p
                where cp.id.collectionId = :collectionId and p.id = cp.id.productId
                  and p.shop.id = :shopId and p.status = :status and p.deletedAt is null
                order by cp.sortOrder asc, cp.createdDateTime asc
                """, Product.class)
            .setParameter("collectionId", row.getId())
            .setParameter("shopId", shop.getUuid())
            .setParameter("status", ProductStatus.PUBLISHED)
            .setFirstResult(pageIndex * pageSize)
            .setMaxResults(pageSize)
            .getResultList();
        var cards = productService.publicCards(pageItems, locale);
        return new CollectionDtos.PublicCollectionDetail(
            toPublic(shop, row, total, locale),
            new CatalogDtos.PageResponse<>(cards, pageIndex, pageSize, total)
        );
    }

    // ---------------------------------------------------------------- helpers

    private void apply(Shop shop, ShopCollection row, CollectionDtos.CollectionWriteRequest request, UUID userId) {
        var problems = new ArrayList<FieldViolation>();
        var body = request == null ? new CollectionDtos.CollectionWriteRequest(null, null, null, null, null, null) : request;
        var nameTh = cleanLine(body.nameTh());
        var nameEn = cleanLine(body.nameEn());
        if (nameTh.isEmpty() || nameTh.codePointCount(0, nameTh.length()) > NAME_MAX || hasMarkup(nameTh)) {
            problems.add(ValidationMessages.violation("nameTh", "COLLECTION_NAME_REQUIRED"));
        }
        if (nameEn.codePointCount(0, nameEn.length()) > NAME_MAX || hasMarkup(nameEn)) {
            problems.add(ValidationMessages.violation("nameEn", "COLLECTION_NAME_REQUIRED"));
        }
        var description = cleanText(body.description());
        if (description.codePointCount(0, description.length()) > DESCRIPTION_MAX || hasMarkup(description)) {
            problems.add(ValidationMessages.violation("description", "COLLECTION_DESCRIPTION_TOO_LONG"));
        }
        var rawSlug = body.slug() == null ? "" : body.slug().trim().toLowerCase(Locale.ROOT);
        boolean explicit = !rawSlug.isEmpty();
        if (explicit && (rawSlug.length() > SLUG_MAX || !SLUG.matcher(rawSlug).matches())) {
            problems.add(ValidationMessages.violation("slug", "COLLECTION_SLUG_INVALID"));
        }
        if (body.coverMediaId() != null) {
            var cover = media.findByIdAndShopIdAndDeletedAtIsNull(body.coverMediaId(), shop.getUuid()).orElse(null);
            if (cover == null || cover.getKind() != ShopThemeMediaKind.IMAGE) {
                problems.add(ValidationMessages.violation("coverMediaId", "COLLECTION_COVER_INVALID"));
            }
        }
        if (!problems.isEmpty()) throw new ValidationException(problems);

        String slug;
        if (explicit) {
            var clash = collections.findByShopIdAndSlug(shop.getUuid(), rawSlug).orElse(null);
            if (clash != null && !clash.getId().equals(row.getId())) {
                throw new AuthException(HttpStatus.CONFLICT, "COLLECTION_SLUG_TAKEN", ValidationMessages.text("COLLECTION_SLUG_TAKEN"));
            }
            slug = rawSlug;
        } else if (row.getSlug() != null) {
            slug = row.getSlug();
        } else {
            slug = uniqueSlug(shop, deriveSlug(nameEn.isEmpty() ? nameTh : nameEn));
        }
        boolean published = body.published() != null ? body.published() : row.isPublished();
        row.apply(slug, nameTh, nameEn.isEmpty() ? null : nameEn, description.isEmpty() ? null : description,
            body.coverMediaId(), published, userId);
    }

    static String deriveSlug(String name) {
        var ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        if (ascii.length() > 60) ascii = ascii.substring(0, 60).replaceAll("-+$", "");
        return ascii.isEmpty() ? "collection" : ascii;
    }

    private String uniqueSlug(Shop shop, String base) {
        var candidate = base;
        for (int n = 2; collections.findByShopIdAndSlug(shop.getUuid(), candidate).isPresent(); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }

    private static List<UUID> productIds(CollectionDtos.ProductIdsRequest request) {
        if (request == null || request.productIds() == null) return List.of();
        if (request.productIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw new ValidationException(List.of(ValidationMessages.violation("productIds", "COLLECTION_PRODUCT_INVALID")));
        }
        return request.productIds();
    }

    private void requireShopProducts(Long shopId, Collection<UUID> productIds) {
        var problems = new ArrayList<FieldViolation>();
        for (var productId : productIds) {
            if (products.findByIdAndShop_ShopIdAndDeletedAtIsNull(productId, shopId).isEmpty()) {
                problems.add(ValidationMessages.violation("productIds", "COLLECTION_PRODUCT_INVALID"));
                break;
            }
        }
        if (!problems.isEmpty()) throw new ValidationException(problems);
    }

    private ShopCollection requireCollection(Shop shop, UUID id) {
        return collections.findByIdAndShopId(id, shop.getUuid()).orElseThrow(ShopCollectionService::notFound);
    }

    private CollectionDtos.MerchantCollectionDetail detail(Shop shop, ShopCollection row) {
        var rows = items.findByIdCollectionIdOrderBySortOrderAsc(row.getId());
        var byId = new HashMap<UUID, Product>();
        if (!rows.isEmpty()) {
            entityManager.createQuery("select p from Product p where p.id in :ids and p.shop.id = :shopId", Product.class)
                .setParameter("ids", rows.stream().map(ShopCollectionProduct::getProductId).toList())
                .setParameter("shopId", shop.getUuid())
                .getResultList()
                .forEach(product -> byId.put(product.getId(), product));
        }
        var list = new ArrayList<CollectionDtos.MerchantCollectionProduct>();
        for (var item : rows) {
            var product = byId.get(item.getProductId());
            if (product == null || product.getDeletedAt() != null) continue;
            list.add(new CollectionDtos.MerchantCollectionProduct(product.getId(), product.getSlug(), product.getNameTh(),
                product.getNameEn(), product.getStatus() == null ? null : product.getStatus().name(), product.getBasePrice(), item.getSortOrder()));
        }
        return new CollectionDtos.MerchantCollectionDetail(toMerchant(shop, row, list.size()), list);
    }

    private Map<UUID, Long> counts(List<UUID> ids) {
        var out = new HashMap<UUID, Long>();
        if (ids.isEmpty()) return out;
        for (var pair : items.countByCollections(ids)) out.put((UUID) pair[0], ((Number) pair[1]).longValue());
        return out;
    }

    private Map<UUID, Long> activeCounts(Shop shop, List<UUID> ids) {
        var out = new HashMap<UUID, Long>();
        if (ids.isEmpty()) return out;
        entityManager.createQuery("""
                select cp.id.collectionId, count(p) from ShopCollectionProduct cp, Product p
                where cp.id.collectionId in :ids and p.id = cp.id.productId
                  and p.shop.id = :shopId and p.status = :status and p.deletedAt is null
                group by cp.id.collectionId
                """, Object[].class)
            .setParameter("ids", ids)
            .setParameter("shopId", shop.getUuid())
            .setParameter("status", ProductStatus.PUBLISHED)
            .getResultList()
            .forEach(pair -> out.put((UUID) pair[0], ((Number) pair[1]).longValue()));
        return out;
    }

    private String coverUrl(Shop shop, ShopCollection row) {
        if (row.getCoverMediaId() == null) return null;
        return media.findByIdAndShopIdAndDeletedAtIsNull(row.getCoverMediaId(), shop.getUuid())
            .map(found -> ShopThemeMediaService.publicUrl(shop.getSlug(), found.getId()))
            .orElse(null);
    }

    private CollectionDtos.MerchantCollectionResponse toMerchant(Shop shop, ShopCollection row, long productCount) {
        return new CollectionDtos.MerchantCollectionResponse(row.getId(), row.getSlug(), row.getNameTh(), row.getNameEn(),
            row.getDescription(), row.getCoverMediaId(), coverUrl(shop, row), row.getSortOrder(), row.isPublished(), productCount,
            row.getCreatedDateTime(), row.getModifiedDateTime());
    }

    private CollectionDtos.PublicCollectionResponse toPublic(Shop shop, ShopCollection row, long productCount, String locale) {
        var english = locale != null && !locale.toLowerCase(Locale.ROOT).startsWith("th");
        var name = english && row.getNameEn() != null && !row.getNameEn().isBlank() ? row.getNameEn() : row.getNameTh();
        return new CollectionDtos.PublicCollectionResponse(row.getSlug(), name, row.getNameTh(), row.getNameEn(),
            row.getDescription(), coverUrl(shop, row), productCount);
    }

    private static String cleanLine(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFC).replaceAll("[\\p{Cc}\\p{Cf}&&[^\\u200D]]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String cleanText(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFC).replace("\r\n", "\n")
            .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "").trim();
    }

    private static boolean hasMarkup(String value) {
        return value.indexOf('<') >= 0 || value.indexOf('>') >= 0;
    }

    private static AuthException productLimit() {
        return new AuthException(HttpStatus.BAD_REQUEST, "COLLECTION_PRODUCT_LIMIT_REACHED", ValidationMessages.text("COLLECTION_PRODUCT_LIMIT_REACHED"));
    }

    static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "COLLECTION_NOT_FOUND", "ไม่พบคอลเลกชัน");
    }
}
