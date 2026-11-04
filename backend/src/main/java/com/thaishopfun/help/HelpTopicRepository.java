package com.thaishopfun.help;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HelpTopicRepository extends JpaRepository<HelpTopic, UUID> {
    List<HelpTopic> findByActiveTrueOrderBySortOrderAsc();
    List<HelpTopic> findAllByOrderBySortOrderAscCreatedAtAsc();
    Optional<HelpTopic> findBySlug(String slug);
    boolean existsBySlug(String slug);
    @Query("select coalesce(max(t.sortOrder), 0) from HelpTopic t")
    int maxSortOrder();
}
