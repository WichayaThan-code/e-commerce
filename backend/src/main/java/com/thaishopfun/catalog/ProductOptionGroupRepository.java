package com.thaishopfun.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductOptionGroupRepository extends JpaRepository<ProductOptionGroup, UUID> {
    List<ProductOptionGroup> findByProduct_IdOrderBySortOrderAsc(UUID productId);
}
