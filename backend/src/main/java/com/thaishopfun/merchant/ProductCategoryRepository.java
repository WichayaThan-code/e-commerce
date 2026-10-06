package com.thaishopfun.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    List<ProductCategory> findByActiveTrueOrderBySortOrderAsc();
    boolean existsByNameThAndActiveTrue(String nameTh);
}
