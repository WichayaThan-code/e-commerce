package com.thaishopfun.catalog.collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShopCollectionProductRepository extends JpaRepository<ShopCollectionProduct, ShopCollectionProduct.Key> {
    List<ShopCollectionProduct> findByIdCollectionIdOrderBySortOrderAsc(UUID collectionId);
    long countByIdCollectionId(UUID collectionId);

    @Query("select cp.id.collectionId, count(cp) from ShopCollectionProduct cp where cp.id.collectionId in :ids group by cp.id.collectionId")
    List<Object[]> countByCollections(@Param("ids") Collection<UUID> ids);

    @Modifying
    @Query("delete from ShopCollectionProduct cp where cp.id.collectionId = :collectionId")
    void deleteByCollection(@Param("collectionId") UUID collectionId);
}
