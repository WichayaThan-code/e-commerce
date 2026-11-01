package com.thaishopfun.catalog;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.ShopStatus;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Public marketplace recommendations: published products from approved shops ranked by sales (same "sold" rule as
 * best sellers), published review rating and, when the viewer has view history, category affinity.
 */
@Service
public class RecommendationService {
    static final int CANDIDATE_CAP = 2000;
    static final Set<String> SORTS = Set.of("featured", "best", "price_asc", "price_desc", "rating", "newest");

    private final EntityManager entities;
    private final ProductService products;
    private final ProductViewService views;

    public RecommendationService(EntityManager entities, ProductService products, ProductViewService views) {
        this.entities = entities;
        this.products = products;
        this.views = views;
    }

    private record Candidate(UUID id, Long categoryNo, BigDecimal price, java.time.Instant created, long sold, double rating, long reviews, double score) {}

    @Transactional(readOnly = true)
    public CatalogDtos.PageResponse<CatalogDtos.PublicBestSellerResponse> recommend(Long categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                                                                                    BigDecimal minRating, String brands, String sort,
                                                                                    Integer page, Integer size, UUID userId, String sessionId, String locale) {
        var key = sort == null || sort.isBlank() ? "featured" : sort.trim().toLowerCase(Locale.ROOT);
        if (!SORTS.contains(key)) throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_SORT", "Unsupported sort");
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > 100) throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Invalid page");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_PRICE_RANGE", "minPrice must be <= maxPrice");
        var brandList = brands == null ? List.<String>of() : Arrays.stream(brands.split(","))
            .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty() && s.length() <= 120).distinct().limit(20).toList();

        var where = new StringBuilder(" from Product p where p.deletedAt is null and p.status = :status and p.shop.status = :approved");
        if (categoryId != null) where.append(" and p.category.categoryNo = :categoryId");
        if (minPrice != null) where.append(" and p.basePrice >= :minPrice");
        if (maxPrice != null) where.append(" and p.basePrice <= :maxPrice");
        if (!brandList.isEmpty()) where.append(" and lower(p.brand) in :brands");
        var query = entities.createQuery("select p.id, p.category.categoryNo, p.basePrice, p.createdDateTime" + where
            + " order by p.createdDateTime desc, p.id asc", Object[].class);
        query.setParameter("status", ProductStatus.PUBLISHED).setParameter("approved", ShopStatus.APPROVED);
        if (categoryId != null) query.setParameter("categoryId", categoryId);
        if (minPrice != null) query.setParameter("minPrice", minPrice);
        if (maxPrice != null) query.setParameter("maxPrice", maxPrice);
        if (!brandList.isEmpty()) query.setParameter("brands", brandList);
        var rows = query.setMaxResults(CANDIDATE_CAP).getResultList();
        var ids = rows.stream().map(r -> (UUID) r[0]).toList();

        var sold = soldCounts(ids);
        var ratings = ratings(ids);
        var affinity = views.categoryAffinity(userId, sessionId);
        long affinityTotal = affinity.values().stream().mapToLong(Long::longValue).sum();

        var candidates = new ArrayList<Candidate>();
        for (var r : rows) {
            var id = (UUID) r[0];
            var rating = ratings.getOrDefault(id, new double[] {0, 0});
            if (minRating != null && (rating[1] == 0 || rating[0] < minRating.doubleValue())) continue;
            long s = sold.getOrDefault(id, 0L);
            double score = Math.log1p(s) + (rating[0] / 5.0) * 1.5 * Math.min(1.0, rating[1] / 3.0);
            var cat = (Long) r[1];
            if (affinityTotal > 0 && cat != null) score += 2.0 * affinity.getOrDefault(cat, 0L) / (double) affinityTotal;
            candidates.add(new Candidate(id, cat, (BigDecimal) r[2], (java.time.Instant) r[3], s, rating[0], (long) rating[1], score));
        }
        Comparator<Candidate> newest = Comparator.comparing(Candidate::created, Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<Candidate> order = switch (key) {
            case "best" -> Comparator.comparingLong(Candidate::sold).reversed().thenComparing(Comparator.comparingDouble(Candidate::score).reversed());
            case "price_asc" -> Comparator.comparing(Candidate::price, Comparator.nullsLast(Comparator.naturalOrder()));
            case "price_desc" -> Comparator.comparing(Candidate::price, Comparator.nullsLast(Comparator.reverseOrder()));
            case "rating" -> Comparator.comparingDouble(Candidate::rating).reversed().thenComparing(Comparator.comparingLong(Candidate::reviews).reversed());
            case "newest" -> newest;
            default -> Comparator.comparingDouble(Candidate::score).reversed();
        };
        candidates.sort(order.thenComparing(newest).thenComparing(Candidate::id));
        int from = (int) Math.min(candidates.size(), (long) pageNumber * pageSize);
        var slice = candidates.subList(from, Math.min(candidates.size(), from + pageSize));
        var soldMap = new HashMap<UUID, Long>();
        for (var c : slice) soldMap.put(c.id(), c.sold());
        var items = products.rankedPublicCards(slice.stream().map(Candidate::id).toList(), soldMap, from, locale);
        return new CatalogDtos.PageResponse<>(items, pageNumber, pageSize, candidates.size());
    }

    private Map<UUID, Long> soldCounts(List<UUID> ids) {
        var result = new HashMap<UUID, Long>();
        if (ids.isEmpty()) return result;
        for (int i = 0; i < ids.size(); i += 500) {
            var chunk = ids.subList(i, Math.min(ids.size(), i + 500));
            for (var row : entities.createQuery("select oi.productId, sum(oi.quantity) from OrderItem oi join oi.order o where oi.productId in :ids and "
                    + ProductService.SOLD_ORDER_FILTER + " group by oi.productId", Object[].class).setParameter("ids", chunk).getResultList())
                result.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    private Map<UUID, double[]> ratings(List<UUID> ids) {
        var result = new HashMap<UUID, double[]>();
        for (int i = 0; i < ids.size(); i += 500) {
            var chunk = ids.subList(i, Math.min(ids.size(), i + 500));
            for (var row : entities.createQuery("select r.productId, avg(r.rating), count(r) from ProductReview r where r.productId in :ids"
                    + " and r.status = 'PUBLISHED' and r.deletedAt is null group by r.productId", Object[].class).setParameter("ids", chunk).getResultList())
                result.put((UUID) row[0], new double[] {((Number) row[1]).doubleValue(), ((Number) row[2]).doubleValue()});
        }
        return result;
    }
}
