package com.thaishopfun.catalog;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.ShopStatus;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Records product views (deduped, rate limited) and serves recently-viewed / view-history signals. */
@Service
public class ProductViewService {
    static final Duration DEDUPE_WINDOW = Duration.ofMinutes(30);
    static final int IP_LIMIT = 120;
    static final Duration IP_WINDOW = Duration.ofMinutes(10);
    private static final int SESSION_MAX = 100;

    private final EntityManager entities;
    private final ProductService products;
    private final Clock clock;
    private final Map<String, ArrayDeque<Instant>> hits = new ConcurrentHashMap<>();

    public ProductViewService(EntityManager entities, ProductService products) {
        this.entities = entities;
        this.products = products;
        this.clock = Clock.systemUTC();
    }

    /** Hash of an anonymous session id (null when absent/invalid). Raw ids are never persisted or returned. */
    static String sessionHash(String sessionId) {
        if (sessionId == null) return null;
        var trimmed = sessionId.trim();
        if (trimmed.length() < 8 || trimmed.length() > SESSION_MAX || !trimmed.matches("[A-Za-z0-9_-]+")) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("view:" + trimmed).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public void record(UUID productId, UUID userId, String sessionId, String ip) {
        throttle(ip);
        var hash = sessionHash(sessionId);
        if (productId == null || (userId == null && hash == null)) return;
        var visible = entities.createQuery("select count(p) from Product p where p.id = :id and p.deletedAt is null"
                + " and p.status = :status and p.shop.status = :approved", Long.class)
            .setParameter("id", productId).setParameter("status", ProductStatus.PUBLISHED).setParameter("approved", ShopStatus.APPROVED)
            .getSingleResult();
        if (visible == 0) return;
        var now = Instant.now(clock);
        var viewer = userId != null ? "v.userId = :viewer" : "v.sessionHash = :viewer";
        var recent = entities.createQuery("select count(v) from ProductView v where v.productId = :pid and " + viewer + " and v.viewedAt >= :since", Long.class)
            .setParameter("pid", productId).setParameter("viewer", userId != null ? userId : hash)
            .setParameter("since", now.minus(DEDUPE_WINDOW)).getSingleResult();
        if (recent > 0) return;
        entities.persist(new ProductView(productId, userId, userId != null ? null : hash, now));
    }

    private void throttle(String ip) {
        var key = ip == null || ip.isBlank() ? "unknown" : ip;
        var now = Instant.now(clock);
        var queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(now.minus(IP_WINDOW))) queue.pollFirst();
            if (queue.size() >= IP_LIMIT)
                throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "VIEW_RATE_LIMITED", "Too many requests");
            queue.addLast(now);
        }
        if (hits.size() > 10_000) hits.entrySet().removeIf(e -> { synchronized (e.getValue()) { return e.getValue().isEmpty(); } });
    }

    /** Distinct recently viewed product ids, newest first (visible products only). */
    @Transactional(readOnly = true)
    public List<UUID> recentProductIds(UUID userId, String sessionId, int limit) {
        var hash = sessionHash(sessionId);
        if (userId == null && hash == null) return List.of();
        var viewer = userId != null ? "v.userId = :viewer" : "v.sessionHash = :viewer";
        var rows = entities.createQuery("select v.productId, max(v.viewedAt) from ProductView v, Product p where p.id = v.productId and " + viewer
                + " and p.deletedAt is null and p.status = :status and p.shop.status = :approved group by v.productId order by max(v.viewedAt) desc", Object[].class)
            .setParameter("viewer", userId != null ? userId : hash)
            .setParameter("status", ProductStatus.PUBLISHED).setParameter("approved", ShopStatus.APPROVED)
            .setMaxResults(limit).getResultList();
        var ids = new ArrayList<UUID>();
        for (var row : rows) ids.add((UUID) row[0]);
        return ids;
    }

    /** categoryNo -> view count over the viewer's last 200 views (personalization signal). */
    @Transactional(readOnly = true)
    public Map<Long, Long> categoryAffinity(UUID userId, String sessionId) {
        var hash = sessionHash(sessionId);
        var result = new LinkedHashMap<Long, Long>();
        if (userId == null && hash == null) return result;
        var viewer = userId != null ? "v.userId = :viewer" : "v.sessionHash = :viewer";
        var rows = entities.createQuery("select p.category.categoryNo from ProductView v, Product p where p.id = v.productId and " + viewer
                + " order by v.viewedAt desc", Long.class)
            .setParameter("viewer", userId != null ? userId : hash).setMaxResults(200).getResultList();
        for (var no : rows) if (no != null) result.merge(no, 1L, Long::sum);
        return result;
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.PublicBestSellerResponse> recentlyViewed(UUID userId, String locale, int limit) {
        var ids = recentProductIds(userId, null, limit);
        return products.rankedPublicCards(ids, Map.of(), 0, locale);
    }
}
