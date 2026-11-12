package com.thaishopfun.review;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.MerchantStorageService;
import com.thaishopfun.order.MediaFile;
import com.thaishopfun.order.MediaFileRepository;
import com.thaishopfun.order.OrderStatus;
import jakarta.persistence.EntityManager;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;

import static org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED;

/**
 * Buyer product reviews. Reads orders/order items (owned by the order module) read-only to check eligibility:
 * only the buyer of a DELIVERED / COMPLETED order may review each order item, once.
 * Optional review photos (max 5) use public {@code REVIEW_IMAGE} media files.
 */
@Service
public class ProductReviewService {
    static final int COMMENT_MAX = 2000;
    static final int MAX_IMAGES = 5;
    static final String IMAGE_PURPOSE = "REVIEW_IMAGE";
    static final String IMAGE_FOLDER = "review-images";
    static final String IMAGE_URL_PREFIX = "/api/review-images/";

    private final ProductReviewRepository reviews;
    private final MediaFileRepository media;
    private final MerchantStorageService storage;
    private final EntityManager entities;

    public ProductReviewService(ProductReviewRepository reviews, MediaFileRepository media,
                                MerchantStorageService storage, EntityManager entities) {
        this.reviews = reviews;
        this.media = media;
        this.storage = storage;
        this.entities = entities;
    }

    @Transactional
    public ReviewDtos.ReviewImageUpload uploadImage(UUID buyerId, MultipartFile file) {
        if (buyerId == null) throw new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "กรุณาเข้าสู่ระบบ");
        if (file == null || file.isEmpty()) throw bad("FILE_REQUIRED", "กรุณาเลือกรูป (JPG, PNG หรือ WEBP)");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw bad("INVALID_FILE", "ไม่สามารถอ่านไฟล์ได้");
        }
        var stored = storage.storePlatformImage(IMAGE_FOLDER, bytes, IMAGE_PURPOSE);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) storage.deleteQuietly(stored.path());
                }
            });
        }
        var name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
            ? stored.storedName() : file.getOriginalFilename();
        if (name.length() > 255) name = name.substring(name.length() - 255);
        var row = new MediaFile(null, buyerId, IMAGE_PURPOSE, stored.path(), name, stored.contentType(), stored.size(), stored.sha256(), null);
        row.makePublic();
        row.stamp(buyerId);
        media.save(row);
        return new ReviewDtos.ReviewImageUpload(row.getId(), IMAGE_URL_PREFIX + row.getId());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> serveImage(UUID mediaId) {
        var file = media.findById(mediaId)
            .filter(m -> IMAGE_PURPOSE.equals(m.getPurpose()) && "PUBLIC".equals(m.getVisibility()))
            .orElseThrow(ProductReviewService::imageNotFound);
        var path = storage.openPublicImage(file.getStorageKey()).orElseThrow(ProductReviewService::imageNotFound);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.getContentType()))
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
            .body(new FileSystemResource(path));
    }

    @Transactional
    public ReviewDtos.ReviewView create(UUID buyerId, ReviewDtos.CreateReviewRequest request) {
        if (buyerId == null) throw new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "กรุณาเข้าสู่ระบบ");
        if (request == null || request.orderItemId() == null) throw bad("ORDER_ITEM_REQUIRED", "กรุณาระบุรายการสินค้าที่ต้องการรีวิว");
        var rating = request.rating();
        if (rating == null || rating < 1 || rating > 5) throw bad("INVALID_RATING", "คะแนนต้องอยู่ระหว่าง 1 ถึง 5");
        var comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        if (comment != null && comment.length() > COMMENT_MAX) throw bad("COMMENT_TOO_LONG", "ความคิดเห็นยาวเกินไป");
        var imageIds = normalizeImageIds(request.imageIds());
        var rows = entities.createQuery(
                "select oi.id, oi.productId, o.id, o.shopId, o.buyerUserId, o.status, oi.optionLabelSnapshot"
                    + " from OrderItem oi join oi.order o where oi.id = :id", Object[].class)
            .setParameter("id", request.orderItemId()).getResultList();
        // Someone else's item looks the same as a missing one (no order enumeration).
        if (rows.isEmpty() || !buyerId.equals(rows.get(0)[4])) throw new AuthException(HttpStatus.NOT_FOUND, "ORDER_ITEM_NOT_FOUND", "ไม่พบรายการสั่งซื้อ");
        var row = rows.get(0);
        var status = (OrderStatus) row[5];
        if (status != OrderStatus.DELIVERED && status != OrderStatus.COMPLETED)
            throw new AuthException(HttpStatus.CONFLICT, "REVIEW_NOT_ALLOWED", "รีวิวได้เมื่อได้รับสินค้าแล้วเท่านั้น");
        if (reviews.existsByOrderItemId(request.orderItemId()))
            throw new AuthException(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS", "คุณรีวิวสินค้ารายการนี้แล้ว");
        var optionLabel = (String) row[6];
        var review = reviews.saveAndFlush(new ProductReview(
            (UUID) row[3], (UUID) row[1], (UUID) row[2], (UUID) row[0], buyerId, rating, comment, optionLabel));
        var images = attachImages(review.getId(), buyerId, imageIds);
        return toView(review, displayName(buyerId), images);
    }

    /** Order-item ids this buyer already reviewed for one of their orders (empty if not the buyer / unknown). */
    @Transactional(readOnly = true)
    public ReviewDtos.ReviewStatusResponse statusForOrder(UUID buyerId, UUID orderId) {
        if (buyerId == null) throw new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "กรุณาเข้าสู่ระบบ");
        if (orderId == null) throw bad("ORDER_REQUIRED", "กรุณาระบุคำสั่งซื้อ");
        var owner = entities.createQuery(
                "select o.buyerUserId from Order o where o.id = :id", UUID.class)
            .setParameter("id", orderId).setMaxResults(1).getResultList();
        if (owner.isEmpty() || !buyerId.equals(owner.get(0))) {
            throw new AuthException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "ไม่พบคำสั่งซื้อ");
        }
        return new ReviewDtos.ReviewStatusResponse(orderId, reviews.findReviewedOrderItemIds(orderId, buyerId));
    }

    /**
     * Public, PUBLISHED reviews of one PUBLISHED product (UUID, or product slug + optional shop slug), newest first.
     * Optional filters: {@code rating} 1–5, {@code withPhotos=true} for reviews that have at least one image.
     */
    @Transactional(readOnly = true)
    public ReviewDtos.ReviewPage list(String idOrSlug, String shopSlug, Integer page, Integer size,
                                      Integer rating, Boolean withPhotos) {
        var productId = resolveProduct(idOrSlug, shopSlug);
        var pageNumber = page == null || page < 0 ? 0 : page;
        var pageSize = size == null || size < 1 ? 10 : Math.min(size, 50);
        if (rating != null && (rating < 1 || rating > 5)) throw bad("INVALID_RATING", "คะแนนต้องอยู่ระหว่าง 1 ถึง 5");
        var wantPhotos = Boolean.TRUE.equals(withPhotos);

        var where = new StringBuilder(" where r.productId = :p and r.status = :s and r.deletedAt is null");
        if (rating != null) where.append(" and r.rating = :rating");
        if (wantPhotos) {
            where.append(" and exists (select 1 from ProductReviewImage i where i.reviewId = r.id)");
        }

        var countJpql = "select count(r) from ProductReview r" + where;
        var countQuery = entities.createQuery(countJpql, Long.class)
            .setParameter("p", productId).setParameter("s", ProductReview.PUBLISHED);
        if (rating != null) countQuery.setParameter("rating", rating.shortValue());
        var filteredTotal = countQuery.getSingleResult();

        var listJpql = "select r from ProductReview r" + where + " order by r.createdDateTime desc, r.id asc";
        var listQuery = entities.createQuery(listJpql, ProductReview.class)
            .setParameter("p", productId).setParameter("s", ProductReview.PUBLISHED)
            .setFirstResult(pageNumber * pageSize).setMaxResults(pageSize);
        if (rating != null) listQuery.setParameter("rating", rating.shortValue());
        var found = listQuery.getResultList();

        var names = displayNames(found.stream().map(ProductReview::getBuyerUserId).toList());
        var imagesByReview = imagesByReviewIds(found.stream().map(ProductReview::getId).toList());
        var items = found.stream()
            .map(r -> toView(r, names.getOrDefault(r.getBuyerUserId(), "ผู้ซื้อ"),
                imagesByReview.getOrDefault(r.getId(), List.of())))
            .toList();
        var agg = aggregates(List.of(productId)).getOrDefault(productId, new ReviewDtos.Aggregate(null, 0));
        return new ReviewDtos.ReviewPage(items, pageNumber, pageSize, filteredTotal, agg.ratingAvg(),
            agg.reviewCount(), filterCounts(productId));
    }

    /** PUBLISHED review avg (1 decimal) + count per product; products without reviews are absent. */
    @Transactional(readOnly = true)
    public Map<UUID, ReviewDtos.Aggregate> aggregates(Collection<UUID> productIds) {
        var result = new HashMap<UUID, ReviewDtos.Aggregate>();
        if (productIds == null || productIds.isEmpty()) return result;
        for (var row : entities.createQuery("select r.productId, avg(r.rating), count(r) from ProductReview r"
                + " where r.productId in :ids and r.status = :s and r.deletedAt is null group by r.productId", Object[].class)
            .setParameter("ids", productIds).setParameter("s", ProductReview.PUBLISHED).getResultList()) {
            var avg = BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(1, RoundingMode.HALF_UP);
            result.put((UUID) row[0], new ReviewDtos.Aggregate(avg, ((Number) row[2]).longValue()));
        }
        return result;
    }

    private ReviewDtos.ReviewFilterCounts filterCounts(UUID productId) {
        long all = 0, withPhotos = 0;
        long[] stars = new long[6];
        for (var row : entities.createQuery(
                "select r.rating, count(r) from ProductReview r"
                    + " where r.productId = :p and r.status = :s and r.deletedAt is null group by r.rating",
                Object[].class)
            .setParameter("p", productId).setParameter("s", ProductReview.PUBLISHED).getResultList()) {
            int star = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            if (star >= 1 && star <= 5) stars[star] = count;
            all += count;
        }
        withPhotos = entities.createQuery(
                "select count(r) from ProductReview r where r.productId = :p and r.status = :s and r.deletedAt is null"
                    + " and exists (select 1 from ProductReviewImage i where i.reviewId = r.id)", Long.class)
            .setParameter("p", productId).setParameter("s", ProductReview.PUBLISHED)
            .getSingleResult();
        return new ReviewDtos.ReviewFilterCounts(all, withPhotos, stars[1], stars[2], stars[3], stars[4], stars[5]);
    }

    private List<ReviewDtos.ReviewImageView> attachImages(UUID reviewId, UUID buyerId, List<UUID> imageIds) {
        if (imageIds.isEmpty()) return List.of();
        var files = media.findAllById(imageIds);
        if (files.size() != imageIds.size()) throw bad("IMAGE_NOT_FOUND", "ไม่พบรูปที่แนบ");
        var byId = new HashMap<UUID, MediaFile>();
        for (var file : files) byId.put(file.getId(), file);
        var alreadyUsed = new HashSet<>(entities.createQuery(
                "select i.mediaId from ProductReviewImage i where i.mediaId in :ids", UUID.class)
            .setParameter("ids", imageIds).getResultList());
        var attached = new ArrayList<ReviewDtos.ReviewImageView>(imageIds.size());
        for (int i = 0; i < imageIds.size(); i++) {
            var mediaId = imageIds.get(i);
            var file = byId.get(mediaId);
            if (file == null
                || !IMAGE_PURPOSE.equals(file.getPurpose())
                || !"PUBLIC".equals(file.getVisibility())
                || !buyerId.equals(file.getUploaderId())) {
                throw bad("IMAGE_NOT_FOUND", "ไม่พบรูปที่แนบ");
            }
            if (alreadyUsed.contains(mediaId)) throw bad("IMAGE_ALREADY_USED", "รูปนี้ถูกใช้กับรีวิวอื่นแล้ว");
            entities.persist(new ProductReviewImage(reviewId, mediaId, i));
            attached.add(new ReviewDtos.ReviewImageView(mediaId, IMAGE_URL_PREFIX + mediaId));
        }
        entities.flush();
        return attached;
    }

    private Map<UUID, List<ReviewDtos.ReviewImageView>> imagesByReviewIds(List<UUID> reviewIds) {
        var result = new HashMap<UUID, List<ReviewDtos.ReviewImageView>>();
        if (reviewIds.isEmpty()) return result;
        for (var image : entities.createQuery(
                "select i from ProductReviewImage i where i.reviewId in :ids order by i.sortOrder asc, i.id asc",
                ProductReviewImage.class)
            .setParameter("ids", reviewIds).getResultList()) {
            result.computeIfAbsent(image.getReviewId(), ignored -> new ArrayList<>())
                .add(new ReviewDtos.ReviewImageView(image.getMediaId(), IMAGE_URL_PREFIX + image.getMediaId()));
        }
        return result;
    }

    private static List<UUID> normalizeImageIds(List<UUID> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        var seen = new LinkedHashSet<UUID>();
        for (var id : raw) {
            if (id != null) seen.add(id);
        }
        if (seen.size() > MAX_IMAGES) throw bad("TOO_MANY_IMAGES", "แนบรูปได้สูงสุด 5 ภาพ");
        return List.copyOf(seen);
    }

    private static ReviewDtos.ReviewView toView(ProductReview review, String reviewerName,
                                               List<ReviewDtos.ReviewImageView> images) {
        return new ReviewDtos.ReviewView(
            review.getId(),
            review.getRating(),
            review.getBody(),
            reviewerName,
            review.getCreatedDateTime(),
            review.getOptionLabel(),
            images == null ? List.of() : images
        );
    }

    private UUID resolveProduct(String idOrSlug, String shopSlug) {
        var key = idOrSlug == null ? "" : idOrSlug.trim();
        UUID id = null;
        try { id = UUID.fromString(key); } catch (IllegalArgumentException ignored) { }
        var jpql = new StringBuilder("select p.id from Product p where p.deletedAt is null and p.status = com.thaishopfun.catalog.ProductStatus.PUBLISHED");
        jpql.append(id != null ? " and p.id = :key" : " and lower(p.slug) = :key");
        var shop = shopSlug == null || shopSlug.isBlank() ? null : shopSlug.trim().toLowerCase(Locale.ROOT);
        if (shop != null) jpql.append(" and lower(p.shop.slug) = :shop");
        var query = entities.createQuery(jpql.toString(), UUID.class).setParameter("key", id != null ? id : key.toLowerCase(Locale.ROOT));
        if (shop != null) query.setParameter("shop", shop);
        var ids = query.setMaxResults(2).getResultList();
        if (ids.isEmpty()) throw new AuthException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "ไม่พบสินค้า");
        if (ids.size() > 1) throw bad("PRODUCT_AMBIGUOUS", "กรุณาระบุ shopSlug");
        return ids.get(0);
    }

    private String displayName(UUID userId) { return displayNames(List.of(userId)).getOrDefault(userId, "ผู้ซื้อ"); }

    private Map<UUID, String> displayNames(List<UUID> userIds) {
        var result = new HashMap<UUID, String>();
        if (userIds.isEmpty()) return result;
        for (var row : entities.createQuery("select u.id, u.firstName, u.lastName from UserAccount u where u.id in :ids", Object[].class)
            .setParameter("ids", new HashSet<>(userIds)).getResultList())
            result.put((UUID) row[0], mask((String) row[1], (String) row[2]));
        return result;
    }

    /** "สมชาย ใจดี" -> "ส*** ใ." ; never exposes the full name, email or phone. */
    static String mask(String first, String last) {
        if (first == null || first.isBlank()) return "ผู้ซื้อ";
        var f = first.trim();
        var name = f.substring(0, f.offsetByCodePoints(0, 1)) + "***";
        if (last != null && !last.isBlank()) { var l = last.trim(); name += " " + l.substring(0, l.offsetByCodePoints(0, 1)) + "."; }
        return name;
    }

    private static AuthException bad(String code, String message) { return new AuthException(HttpStatus.BAD_REQUEST, code, message); }

    private static AuthException imageNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "ไม่พบรูป");
    }
}
