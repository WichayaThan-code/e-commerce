package com.thaishopfun.review;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.order.OrderStatus;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Buyer product reviews. Reads orders/order items (owned by the order module) read-only to check eligibility:
 * only the buyer of a DELIVERED / COMPLETED order may review each order item, once.
 */
@Service
public class ProductReviewService {
    static final int COMMENT_MAX = 2000;
    private final ProductReviewRepository reviews;
    private final EntityManager entities;

    public ProductReviewService(ProductReviewRepository reviews, EntityManager entities) {
        this.reviews = reviews;
        this.entities = entities;
    }

    @Transactional
    public ReviewDtos.ReviewView create(UUID buyerId, ReviewDtos.CreateReviewRequest request) {
        if (buyerId == null) throw new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "กรุณาเข้าสู่ระบบ");
        if (request == null || request.orderItemId() == null) throw bad("ORDER_ITEM_REQUIRED", "กรุณาระบุรายการสินค้าที่ต้องการรีวิว");
        var rating = request.rating();
        if (rating == null || rating < 1 || rating > 5) throw bad("INVALID_RATING", "คะแนนต้องอยู่ระหว่าง 1 ถึง 5");
        var comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        if (comment != null && comment.length() > COMMENT_MAX) throw bad("COMMENT_TOO_LONG", "ความคิดเห็นยาวเกินไป");
        var rows = entities.createQuery("select oi.id, oi.productId, o.id, o.shopId, o.buyerUserId, o.status"
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
        var review = reviews.saveAndFlush(new ProductReview((UUID) row[3], (UUID) row[1], (UUID) row[2], (UUID) row[0], buyerId, rating, comment));
        return new ReviewDtos.ReviewView(review.getId(), review.getRating(), review.getBody(), displayName(buyerId), review.getCreatedDateTime());
    }

    /** Public, PUBLISHED reviews of one PUBLISHED product (UUID, or product slug + optional shop slug), newest first. */
    @Transactional(readOnly = true)
    public ReviewDtos.ReviewPage list(String idOrSlug, String shopSlug, Integer page, Integer size) {
        var productId = resolveProduct(idOrSlug, shopSlug);
        var pageNumber = page == null || page < 0 ? 0 : page;
        var pageSize = size == null || size < 1 ? 10 : Math.min(size, 50);
        var found = entities.createQuery("select r from ProductReview r where r.productId = :p and r.status = :s and r.deletedAt is null"
                + " order by r.createdDateTime desc, r.id asc", ProductReview.class)
            .setParameter("p", productId).setParameter("s", ProductReview.PUBLISHED)
            .setFirstResult(pageNumber * pageSize).setMaxResults(pageSize).getResultList();
        var names = displayNames(found.stream().map(ProductReview::getBuyerUserId).toList());
        var items = found.stream().map(r -> new ReviewDtos.ReviewView(r.getId(), r.getRating(), r.getBody(),
            names.getOrDefault(r.getBuyerUserId(), "ผู้ซื้อ"), r.getCreatedDateTime())).toList();
        var agg = aggregates(List.of(productId)).getOrDefault(productId, new ReviewDtos.Aggregate(null, 0));
        return new ReviewDtos.ReviewPage(items, pageNumber, pageSize, agg.reviewCount(), agg.ratingAvg(), agg.reviewCount());
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
}
