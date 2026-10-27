package com.thaishopfun.catalog.collection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopCollectionRepository extends JpaRepository<ShopCollection, UUID> {
    List<ShopCollection> findByShopIdOrderBySortOrderAscCreatedDateTimeAsc(UUID shopId);
    List<ShopCollection> findByShopIdAndPublishedTrueOrderBySortOrderAscCreatedDateTimeAsc(UUID shopId);
    Optional<ShopCollection> findByIdAndShopId(UUID id, UUID shopId);
    Optional<ShopCollection> findByShopIdAndSlug(UUID shopId, String slug);
    Optional<ShopCollection> findByShopIdAndSlugAndPublishedTrue(UUID shopId, String slug);
    long countByShopId(UUID shopId);
}
