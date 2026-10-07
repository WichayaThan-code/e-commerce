package com.thaishopfun.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    Optional<ProductCategory> findByCategoryNo(Long categoryNo);
    List<ProductCategory> findByActiveTrueOrderBySortOrderAsc();
    boolean existsByNameThAndActiveTrue(String nameTh);
}
