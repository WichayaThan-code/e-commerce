package com.thaishopfun.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByShop_ShopIdAndDeletedAtIsNullOrderByCreatedDateTimeDesc(Long shopId);
    Optional<Product> findByIdAndShop_ShopIdAndDeletedAtIsNull(UUID id, Long shopId);
    Optional<Product> findByShop_SlugAndSlugAndDeletedAtIsNull(String shopSlug, String productSlug);
    boolean existsByShop_ShopIdAndSlug(Long shopId, String slug);
    boolean existsByShop_ShopIdAndSlugAndIdNot(Long shopId, String slug, UUID id);
}
