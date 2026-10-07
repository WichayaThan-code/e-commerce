package com.thaishopfun.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
    List<ProductImage> findByProduct_IdOrderBySortOrderAsc(UUID productId);
    Optional<ProductImage> findByIdAndProduct_Id(UUID id, UUID productId);
    long countByProduct_Id(UUID productId);
}
