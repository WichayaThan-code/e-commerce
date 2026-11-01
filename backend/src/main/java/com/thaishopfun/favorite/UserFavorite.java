package com.thaishopfun.favorite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Mapping for user_favorites (V45); FavoriteService uses JdbcTemplate, this keeps the schema known to JPA/tests. */
@Entity
@Table(name = "user_favorites")
@IdClass(UserFavorite.Key.class)
public class UserFavorite {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp with time zone default current_timestamp")
    private OffsetDateTime createdAt;

    protected UserFavorite() {
    }

    public record Key(UUID userId, UUID productId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
