package com.thaishopfun.favorite;

import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Buyer favourites. Only PUBLISHED, non-deleted products of ACTIVE shops can be added or are listed. */
@Service
public class FavoriteService {
    public record FavoriteItem(String productId, String productSlug, String shopSlug, OffsetDateTime createdAt) {}
    public record ToggleResponse(String productId, boolean favorite, boolean alreadyFavorite) {}

    private static final String LIVE = " p.status = 'PUBLISHED' and p.deleted_at is null and p.archived_at is null"
        + " and s.status = 'ACTIVE' and s.deleted_at is null ";

    private final JdbcTemplate jdbc;

    public FavoriteService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<FavoriteItem> list(UUID userId, String shopSlug) {
        var sql = "select p.id, p.slug, s.slug as shop_slug, f.created_at from user_favorites f"
            + " join products p on p.id = f.product_id join shops s on s.id = p.shop_id where f.user_id = ? and" + LIVE;
        var mapper = (org.springframework.jdbc.core.RowMapper<FavoriteItem>) (rs, i) -> new FavoriteItem(
            rs.getString("id"), rs.getString("slug"), rs.getString("shop_slug"), rs.getObject("created_at", OffsetDateTime.class));
        if (shopSlug != null && !shopSlug.isBlank()) {
            return jdbc.query(sql + " and s.slug = ? order by f.created_at desc limit 500", mapper, userId, shopSlug.trim());
        }
        return jdbc.query(sql + " order by f.created_at desc limit 500", mapper, userId);
    }

    @Transactional
    public ToggleResponse add(UUID userId, String productId) {
        var id = liveProduct(productId);
        int inserted = jdbc.update("insert into user_favorites (user_id, product_id) values (?, ?) on conflict do nothing", userId, id);
        return new ToggleResponse(id.toString(), true, inserted == 0);
    }

    @Transactional
    public void remove(UUID userId, String productId) {
        UUID id;
        try { id = UUID.fromString(productId == null ? "" : productId.trim()); } catch (IllegalArgumentException e) { return; }
        jdbc.update("delete from user_favorites where user_id = ? and product_id = ?", userId, id);
    }

    private UUID liveProduct(String productId) {
        UUID id;
        try { id = UUID.fromString(productId == null ? "" : productId.trim()); }
        catch (IllegalArgumentException e) { throw notFound(); }
        var found = jdbc.queryForObject("select count(*) from products p join shops s on s.id = p.shop_id where p.id = ? and" + LIVE, Integer.class, id);
        if (found == null || found == 0) throw notFound();
        return id;
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "\u0e44\u0e21\u0e48\u0e1e\u0e1a\u0e2a\u0e34\u0e19\u0e04\u0e49\u0e32");
    }
}
