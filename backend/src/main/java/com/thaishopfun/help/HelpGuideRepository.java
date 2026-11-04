package com.thaishopfun.help;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HelpGuideRepository extends JpaRepository<HelpGuide, UUID> {
    List<HelpGuide> findByActiveTrueOrderBySortOrderAsc();
    List<HelpGuide> findAllByOrderBySortOrderAscCreatedAtAsc();
    Optional<HelpGuide> findBySlug(String slug);
    @Query("select coalesce(max(g.sortOrder), 0) from HelpGuide g")
    int maxSortOrder();
}
