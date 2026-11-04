package com.thaishopfun.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogCategoryRepository extends JpaRepository<BlogCategory, UUID> {
    List<BlogCategory> findByActiveTrueOrderBySortOrderAsc();
    List<BlogCategory> findAllByOrderBySortOrderAscSlugAsc();
    Optional<BlogCategory> findBySlug(String slug);

    @Query("select coalesce(max(c.sortOrder), 0) from BlogCategory c")
    int maxSortOrder();
}
