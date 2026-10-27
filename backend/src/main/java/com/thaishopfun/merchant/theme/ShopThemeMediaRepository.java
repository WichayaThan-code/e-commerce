package com.thaishopfun.merchant.theme;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopThemeMediaRepository extends JpaRepository<ShopThemeMedia, UUID> {
    List<ShopThemeMedia> findByShopIdAndDeletedAtIsNullOrderByCreatedDateTimeDesc(UUID shopId);

    List<ShopThemeMedia> findByShopIdAndKindAndDeletedAtIsNullOrderByCreatedDateTimeDesc(UUID shopId, ShopThemeMediaKind kind);

    Optional<ShopThemeMedia> findByIdAndShopIdAndDeletedAtIsNull(UUID id, UUID shopId);

    Optional<ShopThemeMedia> findByShopIdAndSha256AndDeletedAtIsNull(UUID shopId, String sha256);

    long countByShopIdAndKindAndDeletedAtIsNull(UUID shopId, ShopThemeMediaKind kind);

    @Query("select coalesce(sum(m.sizeBytes), 0) from ShopThemeMedia m where m.shopId = :shopId and m.deletedAt is null")
    long sumActiveSizeBytes(@Param("shopId") UUID shopId);
}
